# 导入演示数据（数据内容见 gen_demo_data.py 顶部说明）
# 会清空课程、报名和所有非管理员账号；管理员账号保留。员工密码统一为 Demo12345。
#
#   .\deploy\demo\load-demo-data.ps1                    # 本地 Docker Compose（导入前确认）
#   .\deploy\demo\load-demo-data.ps1 -Force             # 本地，不确认
#   .\deploy\demo\load-demo-data.ps1 -Target k8s        # 服务器上的 K8s：先自动备份一次，再导入
#   .\deploy\demo\load-demo-data.ps1 -Target k8s -Server root@1.2.3.4
#
# K8s 模式通过 SSH 在服务器上执行 kubectl，前提和 deploy.ps1 相同（部署手册 §1.1 配好免密登录）。
param(
    [ValidateSet('local', 'k8s')]
    [string]$Target = 'local',
    # SSH 目标；也可用环境变量 TCMS_SERVER 设置（与 deploy.ps1 一致）
    [string]$Server = $(if ($env:TCMS_SERVER) { $env:TCMS_SERVER } else { 'root@101.96.237.10' }),
    [string]$Namespace = 'tcms',
    # 只对本地生效；K8s 模式始终要求输入服务器地址确认
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$sqlName = 'tcms-demo-data.sql'
$sql = Join-Path ([IO.Path]::GetTempPath()) $sqlName

function Invoke-Checked {
    param([Parameter(Mandatory)][string]$FilePath, [string[]]$ArgumentList = @(), [string]$Message = '命令执行失败')
    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) { throw "$Message（$FilePath 退出码 $LASTEXITCODE）" }
}

# ---------- 确认 ----------
if ($Target -eq 'k8s') {
    $serverHost = $Server -replace '^.*@', ''
    Write-Host "目标：$Server 上 K8s 命名空间 $Namespace 中的 MySQL" -ForegroundColor Yellow
    Write-Host '将清空课程、报名和所有非管理员账号，然后写入演示数据（导入前会自动备份一次）。' -ForegroundColor Yellow
    Write-Host '演示员工的密码都是 Demo12345，环境若已交给真实用户使用，请不要执行。' -ForegroundColor Yellow
    $answer = Read-Host "确认请输入服务器地址 $serverHost"
    if ($answer -ne $serverHost) { Write-Host '已取消'; exit 1 }
} elseif (-not $Force) {
    $answer = Read-Host '将清空本地库中的课程、报名和非管理员账号，然后写入演示数据。继续？(y/N)'
    if ($answer -ne 'y') { Write-Host '已取消'; exit 1 }
}

Invoke-Checked python @((Join-Path $PSScriptRoot 'gen_demo_data.py'), '-o', $sql) -Message '生成演示数据失败'

# 都是先把 SQL 文件拷过去再在目标机器上重定向导入，避免经 PowerShell 管道传输时中文被转码
if ($Target -eq 'local') {
    $compose = Join-Path $PSScriptRoot '..\docker-compose.yml'
    Invoke-Checked docker @('compose', '-f', $compose, 'cp', $sql, "mysql:/tmp/$sqlName") -Message '拷贝 SQL 到 MySQL 容器失败（容器是否已启动？）'
    Invoke-Checked docker @('compose', '-f', $compose, 'exec', '-T', 'mysql', 'sh', '-c',
        "MYSQL_PWD=`"`$MYSQL_PASSWORD`" mysql -u`"`$MYSQL_USER`" --default-character-set=utf8mb4 `"`$MYSQL_DATABASE`" < /tmp/$sqlName; rc=`$?; rm -f /tmp/$sqlName; exit `$rc") `
        -Message '导入演示数据失败，事务已回滚'
} else {
    Write-Host ''
    Write-Host '==> 导入前备份（CronJob mysql-backup 手动触发一次）' -ForegroundColor Cyan
    $backup = @"
set -e
job=mysql-backup-before-demo-`$(date +%Y%m%d%H%M%S)
kubectl -n $Namespace create job --from=cronjob/mysql-backup `$job
kubectl -n $Namespace wait --for=condition=complete --timeout=300s job/`$job
kubectl -n $Namespace logs job/`$job | tail -n 3
"@ -replace "`r", ''
    Invoke-Checked ssh @($Server, $backup) -Message '备份失败，已中止导入（可执行 kubectl -n tcms describe job 查看原因）'

    Write-Host ''
    Write-Host '==> 上传并导入演示数据' -ForegroundColor Cyan
    Invoke-Checked scp @('-q', $sql, "${Server}:/tmp/$sqlName") -Message '上传 SQL 失败'
    $import = @"
kubectl -n $Namespace exec -i mysql-0 -- bash -c 'MYSQL_PWD="`$MYSQL_PASSWORD" mysql -u"`$MYSQL_USER" --default-character-set=utf8mb4 "`$MYSQL_DATABASE"' < /tmp/$sqlName
rc=`$?
rm -f /tmp/$sqlName
exit `$rc
"@ -replace "`r", ''
    Invoke-Checked ssh @($Server, $import) -Message '导入演示数据失败，事务已回滚'
}
Remove-Item $sql

Write-Host ''
Write-Host '演示数据已导入：员工账号如 chenzhiyuan / Demo12345，管理员账号不变。' -ForegroundColor Green
if ($Target -eq 'k8s') {
    Write-Host '如需撤销，按部署手册 §5.3 恢复名为 tcms-<时间>.sql.gz 的最新一份备份（即刚才导入前的那份）。'
}
