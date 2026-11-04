#Requires -Version 5.1
<#
.SYNOPSIS
    构建镜像并把 TCMS 发布到生产 K8s（技术设计 §9.5；一次性准备见 docs/部署手册.md）。

.DESCRIPTION
    1. 首次运行时生成 deploy/k8s/overlays/prod/secret.env（随机密码）
    2. 构建前后端镜像（linux/amd64），标签默认为 Git commit 号
    3. 交付镜像：
       方式 A（指定 -Registry）：推送到镜像仓库，集群用 registry-pull 密钥拉取
       方式 B（不指定 -Registry）：docker save → scp → 服务器 ctr import
    4. 本地用 kustomize 渲染清单 → scp 到服务器 → kubectl apply → 等待滚动完成 → 删除服务器上的渲染文件
    5. 通过公网地址做冒烟检查

    本文件需保存为 UTF-8 with BOM，Windows PowerShell 5.1 才能正确读取中文。

.EXAMPLE
    .\deploy\deploy.ps1 -Registry crpi-xxxx.cn-beijing.personal.cr.aliyuncs.com/tcms
    方式 A：推送到阿里云 ACR 后发布

.EXAMPLE
    .\deploy\deploy.ps1
    方式 B：不使用镜像仓库，直接把镜像导入服务器

.EXAMPLE
    .\deploy\deploy.ps1 -DryRun
    只渲染清单并列出将要发布的资源，不构建、不连接服务器

.EXAMPLE
    .\deploy\deploy.ps1 -Registry crpi-xxxx.cn-beijing.personal.cr.aliyuncs.com/tcms -Tag 1a2b3c4 -SkipBuild
    用仓库里已有的旧标签重新发布（回滚）
#>
[CmdletBinding()]
param(
    # SSH 目标；也可用环境变量 TCMS_SERVER 设置
    [string]$Server = $(if ($env:TCMS_SERVER) { $env:TCMS_SERVER } else { 'root@101.96.237.10' }),
    # 镜像仓库前缀（方式 A），为空则直接导入镜像（方式 B）；也可用环境变量 TCMS_REGISTRY 设置
    [string]$Registry = $env:TCMS_REGISTRY,
    # 镜像标签，默认取 Git commit 号
    [string]$Tag,
    # 不重新构建/推送，直接用已有标签发布（回滚用）
    [switch]$SkipBuild,
    # 只渲染清单并列出资源，不构建、不连接服务器
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 1.0

$Root       = Split-Path -Parent $PSScriptRoot
$Overlay    = Join-Path $Root 'deploy\k8s\overlays\prod'
$SecretFile = Join-Path $Overlay 'secret.env'
$ReleaseDir = Join-Path $Root 'deploy\k8s\.release'
$Namespace  = 'tcms'
$NodePort   = 30080
$ServerHost = $Server -replace '^.*@', ''
$PublicUrl  = "http://${ServerHost}:$NodePort"
$Utf8NoBom  = New-Object System.Text.UTF8Encoding $false

function Write-Step([string]$Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Native {
    param([Parameter(Mandatory)][string]$FilePath, [string[]]$ArgumentList = @())
    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "命令失败（退出码 $LASTEXITCODE）：$FilePath $($ArgumentList -join ' ')"
    }
}

function Assert-Command([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "找不到命令 $Name：$Hint"
    }
}

function New-RandomString([int]$Length) {
    # 去掉易混淆字符（0/O、1/l/I），只含字母数字，放进 env 文件和 shell 都无需转义
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
    $bytes = New-Object byte[] $Length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

function Initialize-SecretFile {
    if (Test-Path $SecretFile) { return }
    Write-Step '首次发布：生成 secret.env（随机密码）'
    do { $adminPassword = New-RandomString 16 } until ($adminPassword -cmatch '[A-Za-z]' -and $adminPassword -match '[0-9]')
    $lines = @(
        '# 由 deploy.ps1 自动生成。已被 .gitignore 忽略，切勿提交；请另行备份（丢失后数据库密码将无法对上）。',
        "MYSQL_ROOT_PASSWORD=$(New-RandomString 24)",
        "DB_PASSWORD=$(New-RandomString 24)",
        "JWT_SECRET=$(New-RandomString 48)",
        'ADMIN_USERNAME=admin',
        "ADMIN_PASSWORD=$adminPassword"
    )
    # 用 LF 换行：CRLF 会让值末尾带上 \r
    [System.IO.File]::WriteAllText($SecretFile, (($lines -join "`n") + "`n"), $Utf8NoBom)
    Write-Host "管理员账号：admin    初始密码：$adminPassword" -ForegroundColor Yellow
    Write-Host "已保存在 $SecretFile ，请把这个文件备份到安全的地方。" -ForegroundColor Yellow
}

function Get-DefaultTag {
    $sha = $null
    $ok = $false
    try {
        $sha = & git -C $Root rev-parse --short HEAD 2>$null
        $ok = ($LASTEXITCODE -eq 0) -and $sha
    } catch { $ok = $false }
    if (-not $ok) {
        # 还没有任何提交
        return 'build-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    }
    $tag = "$sha".Trim()
    $dirty = $null
    try { $dirty = & git -C $Root status --porcelain 2>$null } catch { $dirty = 'unknown' }
    if ($dirty) {
        # 有未提交改动时保证标签唯一，否则 IfNotPresent 会沿用节点上同标签的旧镜像
        $tag = "$tag-dirty-" + (Get-Date -Format 'yyyyMMddHHmmss')
    }
    return $tag
}

function Show-RenderedSummary([string]$Path) {
    $docs = ([System.IO.File]::ReadAllText($Path)) -split "(?m)^---\s*$"
    foreach ($doc in $docs) {
        $kind = [regex]::Match($doc, '(?m)^kind:\s*(\S+)').Groups[1].Value
        $name = [regex]::Match($doc, '(?m)^  name:\s*(\S+)').Groups[1].Value
        if ($kind) { Write-Host ("  {0,-24} {1}" -f $kind, $name) }
    }
    Write-Host '  镜像：'
    [regex]::Matches([System.IO.File]::ReadAllText($Path), '(?m)^\s*image:\s*(\S+)') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique |
        ForEach-Object { Write-Host "    $_" }
}

# ---------------------------------------------------------------- 前置检查
Assert-Command kubectl '用于本地渲染 kustomize 清单（Docker Desktop 自带 kubectl）'
if (-not $DryRun) {
    Assert-Command ssh '需要 Windows 自带的 OpenSSH 客户端'
    Assert-Command scp '需要 Windows 自带的 OpenSSH 客户端'
    if (-not $SkipBuild) {
        Assert-Command docker '需要 Docker Desktop'
        $dockerOk = $false
        try { & docker info --format '{{.ServerVersion}}' *> $null; $dockerOk = ($LASTEXITCODE -eq 0) } catch { $dockerOk = $false }
        if (-not $dockerOk) { throw 'Docker 未运行：请先启动 Docker Desktop' }
    }
}

Initialize-SecretFile
if (-not $Tag) { $Tag = Get-DefaultTag }
$Registry = if ($Registry) { $Registry.Trim().TrimEnd('/') } else { '' }
$ModeText = if ($Registry) { "方式 A：镜像仓库 $Registry" } else { '方式 B：直接导入（不使用镜像仓库）' }

Write-Host "目标服务器：$Server"
Write-Host "镜像标签：  $Tag"
Write-Host "交付方式：  $ModeText"

$Components = @('backend', 'frontend')
$LocalImage = @{}
$ImageName  = @{}
foreach ($c in $Components) {
    $LocalImage[$c] = "tcms-${c}:$Tag"
    $ImageName[$c]  = if ($Registry) { "$Registry/tcms-$c" } else { "tcms-$c" }
}

# ---------------------------------------------------------------- 1. 构建镜像
if (-not $SkipBuild -and -not $DryRun) {
    Write-Step "构建镜像（linux/amd64，标签 $Tag）"
    foreach ($c in $Components) {
        Invoke-Native docker @('build', '--platform', 'linux/amd64', '--provenance=false', '-t', $LocalImage[$c], (Join-Path $Root $c))
    }
}

# ---------------------------------------------------------------- 2. 交付镜像
if (-not $SkipBuild -and -not $DryRun) {
    if ($Registry) {
        Write-Step "推送镜像到 $Registry"
        foreach ($c in $Components) {
            $remoteRef = "$($ImageName[$c]):$Tag"
            Invoke-Native docker @('tag', $LocalImage[$c], $remoteRef)
            Invoke-Native docker @('push', $remoteRef)
        }
    } else {
        Write-Step '打包镜像并导入服务器（上传几百 MB，耗时取决于上行带宽）'
        $tarName = "tcms-images-$Tag.tar"
        $tarPath = Join-Path $env:TEMP $tarName
        try {
            Invoke-Native docker (@('save', '--platform', 'linux/amd64', '-o', $tarPath) + ($Components | ForEach-Object { $LocalImage[$_] }))
            Invoke-Native scp @('-C', $tarPath, "${Server}:/tmp/$tarName")
            Invoke-Native ssh @($Server, "ctr -n k8s.io images import /tmp/$tarName; rc=`$?; rm -f /tmp/$tarName; exit `$rc")
        } finally {
            Remove-Item $tarPath -ErrorAction SilentlyContinue
        }
    }
}

# ---------------------------------------------------------------- 3. 渲染清单
Write-Step '渲染 K8s 清单（kustomize）'
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$kustomization = @"
# 由 deploy/deploy.ps1 生成，请勿手改（已被 .gitignore 忽略）
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../overlays/prod
images:
  - name: tcms-backend
    newName: $($ImageName['backend'])
    newTag: "$Tag"
  - name: tcms-frontend
    newName: $($ImageName['frontend'])
    newTag: "$Tag"
"@
if ($Registry) {
    $kustomization += @"

patches:
  - target:
      kind: Deployment
    patch: |-
      - op: add
        path: /spec/template/spec/imagePullSecrets
        value:
          - name: registry-pull
"@
}
[System.IO.File]::WriteAllText((Join-Path $ReleaseDir 'kustomization.yaml'), ($kustomization -replace "`r`n", "`n"), $Utf8NoBom)

# 渲染结果含明文密钥：只放在本机临时目录，用完即删
$rendered = Join-Path $env:TEMP "tcms-release-$Tag.yaml"
try {
    Invoke-Native kubectl @('kustomize', $ReleaseDir, '-o', $rendered)

    if ($DryRun) {
        Write-Step '预览：将要发布的资源（未连接服务器）'
        Show-RenderedSummary $rendered
        return
    }

    # ------------------------------------------------------------ 4. 发布
    Write-Step "发布到 $Server"
    $remoteFile = 'tcms-release.yaml'   # 放在远端用户主目录（/root 权限为 700）
    Invoke-Native scp @('-q', $rendered, "${Server}:$remoteFile")
} finally {
    Remove-Item $rendered -ErrorAction SilentlyContinue
}

# 远端命令只用 ASCII：Windows PowerShell 5.1 传给原生命令的参数不是 UTF-8
$remoteSteps = @(
    'set -e',
    "trap 'rm -f ~/$remoteFile' EXIT"
)
if ($Registry) {
    $remoteSteps += "kubectl -n $Namespace get secret registry-pull >/dev/null 2>&1 || { echo 'ERROR: secret registry-pull not found in namespace $Namespace, see docs/deployment manual section 1.3'; exit 1; }"
}
$remoteSteps += @(
    "kubectl apply -f ~/$remoteFile",
    "kubectl -n $Namespace rollout status statefulset/mysql --timeout=600s",
    "kubectl -n $Namespace rollout status deployment/backend --timeout=600s",
    "kubectl -n $Namespace rollout status deployment/frontend --timeout=300s",
    "kubectl -n $Namespace get pods -o wide"
)
Invoke-Native ssh @($Server, ($remoteSteps -join '; '))

# ---------------------------------------------------------------- 5. 冒烟检查
Write-Step "冒烟检查 $PublicUrl"
$healthy = $true
try {
    $resp = Invoke-WebRequest -Uri "$PublicUrl/" -UseBasicParsing -TimeoutSec 15
    Write-Host "  前端页面        HTTP $($resp.StatusCode)"
} catch {
    $healthy = $false
    Write-Warning "访问 $PublicUrl 失败：请确认火山引擎安全组已放行 TCP $NodePort。$($_.Exception.Message)"
}
if ($healthy) {
    # 未登录访问需要认证的接口，后端应返回 401 统一错误体，说明 nginx -> backend 链路和认证都正常
    $status = 0
    try {
        Invoke-WebRequest -Uri "$PublicUrl/api/auth/me" -UseBasicParsing -TimeoutSec 15 | Out-Null
        $status = 200
    } catch {
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    }
    if ($status -eq 401) {
        Write-Host '  前端 -> 后端    正常（未登录返回预期的 401）'
    } else {
        $healthy = $false
        Write-Warning "后端链路异常（HTTP $status）。在服务器上查看：kubectl -n $Namespace logs deploy/backend --tail=100"
    }
}

Write-Step $(if ($healthy) { '发布完成' } else { '发布完成，但冒烟检查未通过（见上方警告）' })
Write-Host "访问地址：$PublicUrl"
Write-Host "镜像标签：$Tag"
Write-Host "回滚命令：ssh $Server `"kubectl -n $Namespace rollout undo deployment/backend`""
