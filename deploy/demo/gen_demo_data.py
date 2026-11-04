#!/usr/bin/env python3
"""生成演示数据 SQL：一家约 60 人的软件公司，近三个月的内部培训记录。

用法：
    python deploy/demo/gen_demo_data.py -o demo-data.sql            # 以今天为基准
    python deploy/demo/gen_demo_data.py --today 2026-11-04 -o x.sql  # 指定基准日期（便于复现）

一般不直接调用，而是用同目录的 load-demo-data.ps1 一步导入本地 Docker Compose 的 MySQL。

生成的 SQL 会【清空】课程、报名和所有非管理员账号，再写入演示数据；管理员账号保留。
只用于本地开发和演示环境，不要对有真实数据的库执行。

设计要点：
- 所有时间都相对"今天"推算：约 16 门已结束、2 门进行中、10 门未开始，任何一天导入看起来都是合理的；
- 数据满足业务规则（技术设计 §5.2 / §5.3）：报名、取消发生在开课前，完成发生在开课后，
  所有时间不晚于导入时刻，已报名人数不超过容量，enrolled_count 由 SQL 按报名记录重新计算；
- 员工账号的密码统一为 Demo12345（BCrypt 哈希写死在下面）；
- 随机数种子固定，同一基准日期生成的结果完全相同。
"""

from __future__ import annotations

import argparse
import random
import sys
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta, timezone

CST = timezone(timedelta(hours=8))  # 业务时区固定为北京时间（技术设计 §5.4），不依赖本机时区库
SEED = 20261104
DEMO_PASSWORD = "Demo12345"
# BCrypt(Demo12345)，由后端注册接口生成
PASSWORD_HASH = "$2a$10$watoga0Ug/YaRlowoIoabOLVBZwqZX4gYFwW1c8hKzGSLK9Vvbxae"

ALL = "*"

# ---------------------------------------------------------------- 员工
# (用户名, 姓名, 部门, 标签)
# 标签：mgr 部门负责人 / newmgr 新晋主管 / itv 面试官 / new 本月新入职 / left 已离职（账号停用）/ nodept 自助注册未填部门
EMPLOYEES: list[tuple[str, str, str, set[str]]] = [
    # 研发部
    ("chenzhiyuan", "陈志远", "研发部", {"mgr", "itv"}),
    ("linhaoran", "林浩然", "研发部", {"newmgr", "itv"}),
    ("wangwei", "王伟", "研发部", set()),
    ("liuyang", "刘洋", "研发部", set()),
    ("zhaosiyuan", "赵思远", "研发部", {"itv"}),
    ("huangjunjie", "黄俊杰", "研发部", {"newmgr", "itv"}),
    ("zhouzihao", "周子豪", "研发部", set()),
    ("wuyutong", "吴雨桐", "研发部", {"itv"}),
    ("xumingxuan", "徐明轩", "研发部", set()),
    ("sunjiayi", "孙嘉怡", "研发部", set()),
    ("maxiao", "马骁", "研发部", set()),
    ("zhuwenbo", "朱文博", "研发部", set()),
    ("huxiaofeng", "胡晓峰", "研发部", {"itv"}),
    ("guojing", "郭婧", "研发部", set()),
    ("hejun", "何俊", "研发部", set()),
    ("gaotianyu", "高天宇", "研发部", set()),
    ("luoyiming", "罗一鸣", "研发部", {"new"}),
    ("zhengkai", "郑凯", "研发部", {"new"}),
    # 测试部
    ("liangjingyi", "梁静怡", "测试部", {"mgr", "itv"}),
    ("xieting", "谢婷", "测试部", {"newmgr"}),
    ("songzhenyu", "宋振宇", "测试部", set()),
    ("tangxiaowen", "唐晓雯", "测试部", set()),
    ("handong", "韩冬", "测试部", {"left"}),
    ("fengyue", "冯悦", "测试部", set()),
    # 运维部
    ("dengchaoqun", "邓超群", "运维部", {"mgr", "itv"}),
    ("caoyuhang", "曹宇航", "运维部", {"newmgr"}),
    ("penglei", "彭磊", "运维部", set()),
    ("zengzimo", "曾子默", "运维部", {"new"}),
    # 产品部
    ("xunuo", "许诺", "产品部", {"mgr", "itv"}),
    ("shenjiaqi", "沈佳琪", "产品部", {"newmgr"}),
    ("jiangmengyao", "蒋梦瑶", "产品部", set()),
    ("duhongliang", "杜宏亮", "产品部", set()),
    ("yeshuran", "叶舒然", "产品部", set()),
    ("qinlang", "秦朗", "产品部", {"new"}),
    # 设计部
    ("suxiaofan", "苏小凡", "设计部", {"mgr"}),
    ("chengxue", "程雪", "设计部", {"newmgr"}),
    ("weishuhan", "魏书涵", "设计部", set()),
    # 市场部
    ("lvxiaomeng", "吕晓萌", "市场部", {"mgr", "itv"}),
    ("dingyi", "丁一", "市场部", {"newmgr"}),
    ("jiangxueying", "姜雪莹", "市场部", set()),
    ("fanwenjing", "范文静", "市场部", set()),
    ("yule", "余乐", "市场部", {"new"}),
    # 销售部（两个王伟：后入职的用户名带后缀）
    ("panhongwei", "潘宏伟", "销售部", {"mgr", "itv"}),
    ("yuanzhiqiang", "袁志强", "销售部", {"newmgr"}),
    ("wangwei2", "王伟", "销售部", set()),
    ("tianye", "田野", "销售部", set()),
    ("dongjie", "董洁", "销售部", set()),
    ("wanghaitao", "汪海涛", "销售部", {"newmgr"}),
    ("renjia", "任佳", "销售部", set()),
    ("zhongyi", "钟毅", "销售部", {"left"}),
    # 客户成功部
    ("yaosiqi", "姚思琪", "客户成功部", {"mgr", "itv"}),
    ("luxiaodong", "卢晓东", "客户成功部", {"newmgr"}),
    ("fuya", "傅雅", "客户成功部", {"nodept"}),
    ("qianhao", "钱浩", "客户成功部", {"new"}),
    # 人力资源部
    ("zhoumin", "周敏", "人力资源部", {"mgr", "itv"}),
    ("lujia", "陆佳", "人力资源部", {"newmgr"}),
    ("cuiqian", "崔倩", "人力资源部", set()),
    # 财务部
    ("jialixin", "贾立新", "财务部", {"mgr"}),
    ("xiayu", "夏雨", "财务部", {"newmgr"}),
    ("fanglin", "方琳", "财务部", set()),
]

TECH = ["研发部", "测试部", "运维部"]
BIZ = ["销售部", "客户成功部", "市场部"]

LOC_HALL = "总部 A 座 3F 大会议室"
LOC_301 = "总部 A 座 3F 301 会议室"
LOC_302 = "总部 A 座 3F 302 会议室"
LOC_501 = "总部 A 座 5F 501 培训教室"
LOC_B2 = "总部 B 座 2F 多功能厅"
LOC_ONLINE = "线上 · 腾讯会议 {}（会议链接见企业微信群公告）"

# ---------------------------------------------------------------- 课程
# when 的写法：
#   ("day", 周偏移, 星期几(0=周一), "开始", "结束")          单日课程，周偏移相对本周（负数为过去）
#   ("days", 周偏移, 起始星期, "开始", 结束星期, "结束")     同一周内连续多天
#   ("camp", 起始周偏移, 起始星期, "开始", 结束周偏移, 结束星期, "结束")
#   ("around_today", "开始", "结束")                        上一个工作日开始、下一个工作日结束（保证导入时正在进行）
# audience：部门名、标签或 ALL；fill：占容量（或候选人数）的比例；cancel：取消人数占报名人数的比例；
# complete：已结束课程中被标记完成的比例；admin_share：完成记录中由管理员确认的比例
COURSES: list[dict] = [
    # ---------- 已结束 ----------
    dict(
        title="2026 年度信息安全意识培训",
        instructor="邓超群",
        location=LOC_HALL,
        when=("day", -11, 2, "14:00", "16:00"),
        capacity=None, audience=[ALL], fill=0.78, cancel=0.03, complete=0.96, admin_share=0.8,
        desc="""公司年度必修课，全员参加。

内容：
1. 近一年行业内典型安全事件复盘（钓鱼邮件、弱口令、代码仓库泄露密钥）
2. 公司《信息安全管理制度》要点：账号、终端、数据分级与外发审批
3. 现场演示：如何识别仿冒登录页和可疑附件
4. 课后小测（10 道选择题），成绩计入年度合规记录

请自带笔记本电脑，现场需要登录企业微信完成小测。""",
    ),
    dict(
        title="Git 工作流与 Code Review 规范",
        instructor="林浩然",
        location=LOC_501,
        when=("day", -10, 3, "14:00", "17:00"),
        capacity=30, audience=TECH, fill=0.83, cancel=0.08, complete=0.88, admin_share=0.3,
        desc="""统一研发团队的分支模型和评审标准，减少合并冲突和返工。

大纲：
- 主干开发 vs. Git Flow：我们为什么选择短分支 + PR
- 提交信息规范、rebase 与 merge 的取舍
- Code Review 检查清单：正确性、可读性、测试、安全
- 实操：在演示仓库里完成一次完整的 PR 流程

适合人群：研发、测试同学；需提前在电脑上装好 Git 2.40 以上版本。""",
    ),
    dict(
        title="MySQL 索引原理与慢查询优化",
        instructor="陈志远",
        location=LOC_501,
        when=("day", -9, 1, "14:00", "17:00"),
        capacity=30, audience=TECH, fill=0.9, cancel=0.1, complete=0.85, admin_share=0.2,
        desc="""从 B+ 树讲起，结合线上真实慢 SQL 案例，讲清楚索引怎么建、为什么失效。

大纲：
1. InnoDB 索引结构：聚簇索引、二级索引、回表与覆盖索引
2. 联合索引最左前缀、索引下推、常见失效场景
3. 读懂 EXPLAIN：type、key、rows、Extra 各字段的含义
4. 案例复盘：订单列表接口从 3.2s 优化到 80ms

课前请回顾一下自己负责模块里最慢的 3 条 SQL，现场可以一起分析。""",
    ),
    dict(
        title="高效沟通与跨部门协作",
        instructor="刘芳（外部讲师）",
        location=LOC_B2,
        when=("day", -9, 4, "09:30", "17:00"),
        capacity=40, audience=[ALL], fill=0.95, cancel=0.12, complete=0.9, admin_share=0.85,
        desc="""全天工作坊，以案例演练为主。

上午：沟通的底层逻辑
- 结构化表达：先结论、后依据
- 向上沟通与向下沟通的不同侧重
- 冲突场景下的非暴力沟通

下午：跨部门协作
- 需求方与执行方的预期对齐
- 用 RACI 厘清职责
- 分组演练：一次延期项目的协调会

午餐由公司统一安排，请在报名后到行政处登记饮食禁忌。""",
    ),
    dict(
        title="大客户销售方法论",
        instructor="王建国（外部讲师）",
        location=LOC_302,
        when=("day", -8, 2, "09:30", "17:30"),
        capacity=25, audience=BIZ, fill=0.88, cancel=0.05, complete=0.82, admin_share=0.6,
        desc="""面向销售与客户成功团队的全天实战课程。

主要内容：
- 大客户采购决策链分析：识别决策者、影响者、使用者
- 顾问式销售四步法：调研、诊断、方案、成交
- 招投标项目的节奏把控与常见风险
- 角色扮演：模拟一次面向集团客户 CIO 的方案汇报

讲师有 15 年 ToB 软件销售管理经验，课后提供配套的客户拜访模板。""",
    ),
    dict(
        title="Docker 与 Kubernetes 入门",
        instructor="邓超群",
        location=LOC_501,
        when=("day", -7, 3, "14:00", "17:30"),
        capacity=30, audience=TECH, fill=0.93, cancel=0.1, complete=0.78, admin_share=0.25,
        desc="""动手为主的入门课，结束后能独立把一个 Spring Boot 服务容器化并部署到测试集群。

大纲：
1. 镜像、容器、仓库的概念；Dockerfile 多阶段构建
2. docker compose 编排本地开发环境
3. Kubernetes 核心对象：Pod、Deployment、Service、ConfigMap、Secret
4. 健康检查、资源限制与滚动发布
5. 实操：部署到测试集群并完成一次版本回滚

请提前安装 Docker Desktop，并向运维部申请测试集群的 kubeconfig。""",
    ),
    dict(
        title="如何写好产品需求文档（PRD）",
        instructor="许诺",
        location=LOC_301,
        when=("day", -6, 1, "14:00", "16:30"),
        capacity=25, audience=["产品部", "设计部", "研发部", "测试部"], fill=0.8, cancel=0.1, complete=0.75, admin_share=0.3,
        desc="""一份好的 PRD 能让评审会少开一半。本次分享产品部沉淀下来的写作规范。

内容：
- PRD 的读者是谁：研发、测试、设计分别关心什么
- 必备章节：背景与目标、用户故事、流程图、字段说明、异常与边界、验收标准
- 常见问题：只写正常流程、指标不可衡量、范围蔓延
- 点评环节：现场匿名点评 3 份历史 PRD

欢迎研发、测试同学一起来，听听"读者"视角的反馈。""",
    ),
    dict(
        title="财务报销制度与差旅标准解读",
        instructor="贾立新",
        location=LOC_ONLINE.format("328-416-905"),
        when=("day", -6, 3, "16:00", "17:00"),
        capacity=None, audience=[ALL], fill=0.55, cancel=0.04, complete=0.7, admin_share=0.9,
        desc="""新版《费用报销管理办法》自下月 1 日起执行，本次线上说明会解读主要变化。

重点：
1. 差旅住宿标准按城市分级调整
2. 电子发票查重规则与常见退单原因
3. 招待费事前审批流程
4. 报销系统操作演示与答疑

经常出差的销售、客户成功同学建议参加。会议有录屏，会后发到企业微信群。""",
    ),
    dict(
        title="接口自动化测试实践（Pytest + Playwright）",
        instructor="梁静怡",
        location=LOC_501,
        when=("day", -5, 2, "14:00", "17:00"),
        capacity=20, audience=["测试部", "研发部"], fill=0.85, cancel=0.12, complete=0.8, admin_share=0.2,
        desc="""介绍测试部目前的自动化框架，并带大家写出第一批用例。

大纲：
- 分层测试策略：单元 / 接口 / UI 各自覆盖什么
- Pytest fixture 管理测试数据与登录态
- Playwright 做 UI 冒烟：选择器、等待、截图
- 接入 CI：失败用例的定位与重跑

需要提前准备 Python 3.11+ 环境，仓库地址会在开课前一天发到群里。""",
    ),
    dict(
        title="Figma 组件库与设计规范",
        instructor="苏小凡",
        location=LOC_302,
        when=("day", -4, 1, "14:00", "16:00"),
        capacity=20, audience=["设计部", "产品部", "研发部"], fill=0.7, cancel=0.07, complete=0.86, admin_share=0.4,
        desc="""设计部新版组件库已上线，本次介绍使用方式和交付约定。

内容：
- 组件库结构：基础样式、通用组件、业务组件
- 设计稿标注与切图规范，如何在 Figma 里直接查看间距和色值
- 设计走查流程：研发自查清单
- 答疑：常见的还原度问题

前端同学优先报名。""",
    ),
    dict(
        title="新任管理者领导力训练营",
        instructor="刘芳（外部讲师）",
        location=LOC_302,
        when=("days", -4, 3, "09:00", 4, "17:30"),
        capacity=15, audience=["newmgr"], fill=0.8, cancel=0.0, complete=1.0, admin_share=1.0,
        desc="""面向近一年内晋升的主管，两天封闭式培训，需全程参加。

第一天：角色转变
- 从优秀个人贡献者到管理者：时间分配与精力管理
- 目标拆解与任务委派
- 一对一沟通（1:1）的方法

第二天：团队管理
- 绩效反馈：如何给出具体、可执行的反馈
- 处理团队冲突与低绩效
- 结业汇报：每人提交一份团队 90 天计划

由人力资源部统一提名，请被提名的同学按时报名。""",
    ),
    dict(
        title="客户投诉处理与服务沟通技巧",
        instructor="姚思琪",
        location=LOC_301,
        when=("day", -3, 2, "14:00", "16:30"),
        capacity=20, audience=["客户成功部", "销售部"], fill=0.75, cancel=0.1, complete=0.8, admin_share=0.5,
        desc="""结合客户成功部近半年的工单数据，分享投诉处理的标准流程。

内容：
1. 投诉分级与响应时限（P1 / P2 / P3）
2. 情绪安抚的话术模板与禁忌用语
3. 升级机制：什么时候拉研发、什么时候拉管理层
4. 案例复盘：一次续约风险客户的挽回过程

课后会分享工单回复模板库的使用权限。""",
    ),
    dict(
        title="Spring Boot 应用性能调优实战",
        instructor="陈志远",
        location=LOC_501,
        when=("day", -2, 1, "14:00", "17:30"),
        capacity=16, audience=["研发部", "运维部"], fill=1.0, cancel=0.15, complete=0.72, admin_share=0.15,
        desc="""报名很火，名额有限，报名后请务必参加，如有变动提前取消把名额让给其他同学。

大纲：
- 性能问题的定位思路：先指标、再火焰图、最后看代码
- JVM 内存与 GC：常见参数与 OOM 排查
- 连接池（HikariCP）、线程池的容量规划
- 用 Arthas 在线诊断：trace、watch、profiler
- 案例：报表导出接口 CPU 打满的排查过程

需要提前在电脑上准备 JDK 21 和 IntelliJ IDEA。""",
    ),
    dict(
        title="数据分析入门：用 SQL 看懂业务数据",
        instructor="胡晓峰",
        location=LOC_501,
        when=("day", -2, 3, "14:00", "17:00"),
        capacity=30, audience=["产品部", "市场部", "销售部", "客户成功部"], fill=0.73, cancel=0.1, complete=0.68, admin_share=0.3,
        desc="""零基础友好，面向需要自己取数的非技术同学。

内容：
1. 业务数据仓库里有哪些表，字段怎么看
2. SELECT / WHERE / GROUP BY / JOIN 四个够用的语法
3. 常见指标的写法：日活、转化率、复购率
4. 在 BI 平台上保存查询并做成看板

现场会开通只读账号，请带电脑。""",
    ),
    dict(
        title="消防安全知识与应急疏散演练",
        instructor="赵立军（园区消防中心）",
        location="总部 A 座 1F 大厅（演练集合点：东侧广场）",
        when=("day", -1, 2, "15:00", "16:30"),
        capacity=None, audience=[ALL], fill=0.62, cancel=0.02, complete=0.97, admin_share=1.0,
        desc="""园区年度消防演练，由园区消防中心派员讲解。

流程：
15:00 - 15:40 消防知识讲解：初期火灾扑救、灭火器与消火栓使用
15:40 - 16:00 灭火器实操（每人一次）
16:00 - 16:30 全楼疏散演练，听到警报后按就近楼梯撤离至东侧广场

演练期间电梯停用，请提前安排好手头工作。""",
    ),
    dict(
        title="劳动合同与员工关系常见问题",
        instructor="周敏",
        location=LOC_ONLINE.format("715-208-334"),
        when=("day", -1, 4, "15:00", "16:00"),
        capacity=None, audience=["mgr", "newmgr", "人力资源部"], fill=0.85, cancel=0.05, complete=0.6, admin_share=0.5,
        desc="""面向各部门负责人和主管的线上分享。

内容：
- 试用期考核与转正的流程要求
- 加班、调休与年假的规定
- 员工主动离职与协商解除的注意事项
- 典型劳动争议案例

会后可在企业微信"HR 服务台"继续提问。""",
    ),
    # ---------- 进行中 ----------
    dict(
        title="新员工入职培训（本月第一期）",
        instructor="周敏",
        location=LOC_302,
        when=("around_today", "09:00", "17:30"),
        capacity=15, audience=["new"], fill=1.0, cancel=0.0, complete=0.0, admin_share=1.0,
        desc="""本月新入职同学集中培训，由人力资源部组织，各部门负责人分别讲解。

日程：
- 第一天：公司发展历程与组织架构、企业文化、规章制度、办公系统开通
- 第二天：产品线介绍、业务流程、信息安全与保密要求
- 第三天：部门导师见面、试用期目标设定、结业座谈

培训期间请佩戴工牌，每天 09:00 签到。""",
    ),
    dict(
        title="Go 语言后端开发训练营（第 3 期）",
        instructor="黄俊杰",
        location=LOC_501 + " / 腾讯会议同步直播",
        when=("camp", -2, 1, "19:00", 1, 3, "21:00"),
        capacity=30, audience=TECH, fill=0.7, cancel=0.1, complete=0.0, admin_share=0.0,
        desc="""为期四周的业余训练营，每周二、周四晚 19:00 - 21:00，共 8 次课，结营时提交一个完整的小项目。

课程安排：
第 1 周：语法基础、模块与依赖管理
第 2 周：并发模型（goroutine、channel、context）
第 3 周：用 Gin + GORM 写 REST 服务，单元测试
第 4 周：性能分析（pprof）、项目答辩

每次课后有作业，在代码仓库提交 PR，助教会在 48 小时内点评。""",
    ),
    # ---------- 未开始 ----------
    dict(
        title="Vue 3 组合式 API 与 TypeScript 实践",
        instructor="吴雨桐",
        location=LOC_501,
        when=("day", 1, 1, "14:00", "17:00"),
        capacity=25, audience=["研发部", "设计部", "测试部"], fill=0.84, cancel=0.08,
        desc="""前端团队技术分享，适合有一定 Vue 2 或 React 基础的同学。

大纲：
- 选项式 API 迁移到 <script setup> 的思路
- ref / reactive / computed / watch 的正确用法与常见坑
- 组合式函数（composables）的封装与复用
- Pinia 状态管理与类型推导
- 实操：把一个列表页从 Vue 2 改写为 Vue 3 + TS

请提前安装 Node.js 22 和 pnpm。""",
    ),
    dict(
        title="2027 年度预算编制说明会",
        instructor="贾立新",
        location=LOC_ONLINE.format("902-551-387"),
        when=("day", 1, 3, "15:00", "16:30"),
        capacity=None, audience=["mgr"], fill=0.8, cancel=0.0,
        desc="""各部门负责人必须参加。

议程：
1. 2026 年预算执行情况回顾
2. 2027 年预算编制原则与时间节点
3. 预算模板填写说明：人力成本、项目费用、资本性支出
4. 答疑

预算初稿提交截止时间以财务部邮件通知为准。""",
    ),
    dict(
        title="演讲与汇报技巧",
        instructor="刘芳（外部讲师）",
        location=LOC_B2,
        when=("day", 2, 2, "09:30", "17:00"),
        capacity=30, audience=[ALL], fill=0.7, cancel=0.1,
        desc="""全天课程，年底述职前的针对性训练。

内容：
- 汇报的结构：金字塔原理与 SCQA
- 数据可视化：一页 PPT 只讲一件事
- 现场表达：声音、肢体、节奏控制
- 演练：每人 3 分钟模拟汇报，讲师逐一点评

请提前准备一段 3 分钟的工作汇报内容。""",
    ),
    dict(
        title="Redis 缓存设计与常见问题",
        instructor="赵思远",
        location=LOC_501,
        when=("day", 2, 3, "14:00", "17:00"),
        capacity=20, audience=["研发部", "运维部"], fill=1.0, cancel=0.1,
        desc="""内部技术分享，名额已满可关注后续加场。

大纲：
1. 数据结构选型：String、Hash、ZSet 在业务里的典型用法
2. 缓存更新策略：旁路缓存、延迟双删
3. 穿透、击穿、雪崩的成因与解决方案
4. 分布式锁的正确姿势
5. 线上案例：一次热点 Key 导致的故障复盘""",
    ),
    dict(
        title="结构化面试与面试官认证",
        instructor="周敏",
        location=LOC_301,
        when=("day", 3, 1, "14:00", "17:00"),
        capacity=20, audience=["itv", "newmgr"], fill=0.75, cancel=0.05,
        desc="""明年起所有面试官需持证上岗，本次培训结束后进行认证考核。

内容：
- 胜任力模型与岗位画像
- 行为面试法（STAR）提问与追问
- 评分标准统一与常见评估偏差
- 合规要求：面试中不能问的问题
- 模拟面试与认证考核

已经通过认证的同学无需重复报名。""",
    ),
    dict(
        title="可观测性实践：日志、指标与链路追踪",
        instructor="曹宇航",
        location=LOC_501,
        when=("day", 3, 3, "14:00", "17:30"),
        capacity=30, audience=TECH, fill=0.6, cancel=0.08,
        desc="""运维部正在推进统一监控平台，本次介绍接入方式。

大纲：
- 三大支柱：Logs、Metrics、Traces 各解决什么问题
- 日志规范：结构化日志与 TraceId 透传
- Prometheus 指标设计与 Grafana 看板
- OpenTelemetry 接入 Spring Boot 服务
- 告警规则怎么定才不会"狼来了"

课后各团队需在两周内完成核心服务接入。""",
    ),
    dict(
        title="年终述职 PPT 制作技巧",
        instructor="魏书涵",
        location=LOC_ONLINE.format("436-870-129"),
        when=("day", 4, 2, "19:00", "20:30"),
        capacity=50, audience=[ALL], fill=0.5, cancel=0.06,
        desc="""线上晚间分享，设计部同学教你把述职 PPT 做得清楚又好看。

内容：
- 版式：对齐、留白、字号层级
- 图表选择：什么时候用柱状图、折线图、表格
- 公司述职模板使用说明
- 现场改稿：征集 3 份大家的旧 PPT 进行点评

会议录屏会上传到知识库。""",
    ),
    dict(
        title="大模型应用开发入门：RAG 与 Agent",
        instructor="李卓（外部讲师）",
        location=LOC_B2,
        when=("day", 5, 3, "13:30", "17:30"),
        capacity=40, audience=["研发部", "产品部", "测试部"], fill=0.72, cancel=0.05,
        desc="""近期报名最多的课程之一，产品同学也欢迎参加。

大纲：
1. 大模型能力边界与提示词设计
2. RAG：文档切分、向量检索、召回评估
3. Agent 与工具调用：让模型调用内部接口
4. 安全与合规：数据脱敏、输出审核
5. 演示：基于公司知识库搭建一个问答助手

讲师为某云厂商解决方案架构师。""",
    ),
    dict(
        title="合同风险与商务条款审查",
        instructor="孙婉清（外部律师）",
        location=LOC_302,
        when=("day", 5, 4, "14:00", "16:30"),
        capacity=25, audience=["销售部", "客户成功部", "财务部"], fill=0.48, cancel=0.0,
        desc="""面向销售、客户成功和财务同学。

内容：
- 软件销售合同的常见风险条款：验收、违约责任、知识产权
- 付款条件与回款保障
- 保密协议（NDA）与数据处理条款
- 案例：一次因验收条款不清导致的回款纠纷

可提前把工作中遇到的合同问题发给人力资源部陆佳，讲师会在课上集中解答。""",
    ),
    dict(
        title="急救知识与 AED 使用培训",
        instructor="杨静（红十字会培训师）",
        location=LOC_B2,
        when=("day", 6, 1, "14:00", "16:30"),
        capacity=30, audience=[ALL], fill=0.3, cancel=0.0,
        desc="""行政部联合红十字会组织，完成培训可获得红十字会颁发的救护员证书。

内容：
- 心肺复苏（CPR）理论与模拟人实操
- AED 使用方法（公司 A 座 1F 和 B 座 2F 各配备一台）
- 气道异物梗阻（海姆立克法）
- 常见外伤的止血与包扎

实操环节需要跪地操作，建议穿着便于活动的衣服。""",
    ),
]


# ---------------------------------------------------------------- 生成逻辑


@dataclass
class User:
    username: str
    real_name: str
    department: str | None
    real_dept: str
    tags: set[str]
    created_at: datetime = None  # type: ignore[assignment]
    left_at: datetime | None = None


@dataclass
class Course:
    spec: dict
    start: datetime
    end: datetime
    created_at: datetime = None  # type: ignore[assignment]
    updated_at: datetime = None  # type: ignore[assignment]
    rows: list[dict] = field(default_factory=list)


def at(d: date, hhmm: str) -> datetime:
    h, m = map(int, hhmm.split(":"))
    return datetime.combine(d, time(h, m))


def prev_workday(d: date) -> date:
    d -= timedelta(days=1)
    while d.weekday() >= 5:
        d -= timedelta(days=1)
    return d


def next_workday(d: date) -> date:
    d += timedelta(days=1)
    while d.weekday() >= 5:
        d += timedelta(days=1)
    return d


class Gen:
    def __init__(self, today: date, now: datetime):
        self.rng = random.Random(SEED)
        self.today = today
        self.now = now
        self.monday = today - timedelta(days=today.weekday())

    def week_day(self, week: int, weekday: int) -> date:
        return self.monday + timedelta(weeks=week, days=weekday)

    def rand_between(self, lo: datetime, hi: datetime, work_hours: bool = True) -> datetime:
        """lo 与 hi 之间的随机时刻；尽量落在工作日 09:00–20:00，找不到就退回纯随机。"""
        if hi <= lo:
            return lo
        span = (hi - lo).total_seconds()
        for _ in range(40):
            t = lo + timedelta(seconds=self.rng.uniform(0, span))
            if not work_hours or (t.weekday() < 5 and 9 <= t.hour < 20):
                return t.replace(microsecond=0)
        return (lo + timedelta(seconds=self.rng.uniform(0, span))).replace(microsecond=0)

    def schedule(self, when: tuple) -> tuple[datetime, datetime]:
        kind = when[0]
        if kind == "day":
            _, w, wd, s, e = when
            d = self.week_day(w, wd)
            return at(d, s), at(d, e)
        if kind == "days":
            _, w, wd1, s, wd2, e = when
            return at(self.week_day(w, wd1), s), at(self.week_day(w, wd2), e)
        if kind == "camp":
            _, w1, wd1, s, w2, wd2, e = when
            return at(self.week_day(w1, wd1), s), at(self.week_day(w2, wd2), e)
        if kind == "around_today":
            _, s, e = when
            return at(prev_workday(self.today), s), at(next_workday(self.today), e)
        raise ValueError(when)

    def build(self) -> tuple[list[User], list[Course]]:
        courses = [Course(spec, *self.schedule(spec["when"])) for spec in COURSES]
        titles = [c.spec["title"] for c in courses]
        assert len(set(titles)) == len(titles), "课程标题必须唯一"

        # 课程发布时间：开课前 1–3 周，不晚于现在
        for c in courses:
            hi = min(c.start - timedelta(days=6), self.now - timedelta(hours=2))
            lo = min(c.start - timedelta(days=21), hi - timedelta(days=2))
            c.created_at = self.rand_between(lo, hi)
            c.updated_at = c.created_at
            if self.rng.random() < 0.25:  # 部分课程发布后改过一次（地点、描述等）
                c.updated_at = self.rand_between(c.created_at, min(c.start, self.now))

        users = self.build_users(courses)
        for c in courses:
            self.enroll(c, users)
        return users, courses

    def build_users(self, courses: list[Course]) -> list[User]:
        # 系统上线时管理员批量导入老员工；本月新人在入职当天由 HR 建号
        launch = at(self.week_day(-14, 0), "10:12")
        onboarding = next(c for c in courses if "new" in c.spec["audience"])
        first_course = min(c.created_at for c in courses)
        assert launch < first_course
        users: list[User] = []
        t = launch
        for username, name, dept, tags in EMPLOYEES:
            u = User(username, name, None if "nodept" in tags else dept, dept, tags)
            if "new" in tags:
                join_day = onboarding.start.date() - timedelta(days=self.rng.randint(1, 6))
                while join_day.weekday() >= 5:
                    join_day -= timedelta(days=1)
                u.created_at = at(join_day, "09:30") + timedelta(minutes=self.rng.randint(0, 50))
            else:
                t += timedelta(seconds=self.rng.randint(3, 25))
                u.created_at = t
            if "left" in tags:
                u.left_at = at(self.week_day(-3, 4), "17:40") + timedelta(minutes=self.rng.randint(0, 15))
            users.append(u)
        return users

    def eligible(self, u: User, c: Course) -> bool:
        aud = c.spec["audience"]
        # 自助注册没填部门的员工，按实际所在部门判断是否属于目标人群
        if ALL not in aud and u.real_dept not in aud and not (u.tags & set(aud)):
            return False
        if c.spec["instructor"] == u.real_name:  # 内部讲师不报自己的课
            return False
        if u.created_at >= min(c.start, self.now) - timedelta(hours=1):  # 建号前的课程不可能参加
            return False
        if u.left_at is not None and c.start >= u.left_at:  # 离职后的课程
            return False
        return True

    def enroll(self, c: Course, users: list[User]) -> None:
        s = c.spec
        pool = [u for u in users if self.eligible(u, c)]
        self.rng.shuffle(pool)
        cap = s["capacity"]
        base = cap if cap is not None else len(pool)
        n_active = min(round(base * s["fill"]), len(pool))
        if cap is not None:
            n_active = min(n_active, cap)
        n_cancel = min(round(n_active * s["cancel"]) + (1 if s["cancel"] >= 0.1 else 0), len(pool) - n_active)
        active, cancelled = pool[:n_active], pool[n_active:n_active + n_cancel]

        open_until = min(c.start, self.now) - timedelta(minutes=5)

        def enroll_time(u: User) -> datetime:
            lo = max(c.created_at, u.created_at) + timedelta(minutes=3)
            # 报名大多集中在发布后几天，少数临近开课才报
            if self.rng.random() < 0.7:
                hi = min(lo + timedelta(days=4), open_until)
            else:
                hi = open_until
            return self.rand_between(lo, max(hi, lo + timedelta(minutes=1)))

        ended = c.end <= self.now
        admin_batch = None
        if ended:
            admin_batch = at(next_workday(c.end.date()), "10:20") + timedelta(minutes=self.rng.randint(0, 40))
            admin_batch = min(admin_batch, self.now - timedelta(minutes=30))

        for u in active:
            enrolled_at = enroll_time(u)
            row = dict(username=u.username, status="ENROLLED", enrolled_at=enrolled_at,
                       cancelled_at=None, completed_at=None, completed_by=None,
                       created_at=enrolled_at, updated_at=enrolled_at)
            if self.rng.random() < 0.06:  # 曾经取消过又重新报名：记录复用，创建时间更早
                row["created_at"] = self.rand_between(c.created_at, enrolled_at)
            if ended and self.rng.random() < s["complete"]:
                if self.rng.random() < s["admin_share"]:
                    admin_batch += timedelta(seconds=self.rng.randint(4, 20))
                    row.update(completed_at=admin_batch, completed_by="ADMIN")
                else:
                    lo = max(c.end - timedelta(minutes=20), c.start)
                    hi = min(c.end + timedelta(days=3), self.now - timedelta(minutes=10))
                    row.update(completed_at=self.rand_between(lo, hi), completed_by="SELF")
                row["status"] = "COMPLETED"
                row["updated_at"] = row["completed_at"]
            c.rows.append(row)

        for u in cancelled:
            enrolled_at = enroll_time(u)
            cancelled_at = self.rand_between(enrolled_at + timedelta(minutes=10),
                                             max(open_until, enrolled_at + timedelta(minutes=11)))
            c.rows.append(dict(username=u.username, status="CANCELLED", enrolled_at=enrolled_at,
                               cancelled_at=cancelled_at, completed_at=None, completed_by=None,
                               created_at=enrolled_at, updated_at=cancelled_at))

        self.check(c)

    def check(self, c: Course) -> None:
        title = c.spec["title"]
        assert c.end > c.start, title
        assert c.created_at <= c.updated_at <= self.now, title
        seats = sum(r["status"] != "CANCELLED" for r in c.rows)
        if c.spec["capacity"] is not None:
            assert seats <= c.spec["capacity"], title
        for r in c.rows:
            assert r["enrolled_at"] < c.start, (title, r)
            assert r["created_at"] <= r["enrolled_at"] <= r["updated_at"] <= self.now, (title, r)
            if r["cancelled_at"]:
                assert r["enrolled_at"] < r["cancelled_at"] < c.start, (title, r)
            if r["completed_at"]:
                assert c.start <= r["completed_at"] <= self.now, (title, r)


# ---------------------------------------------------------------- 输出 SQL


def q(v) -> str:
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    s = str(v).replace("\\", "\\\\").replace("'", "''").replace("\n", "\\n")
    return "'" + s + "'"


def render(users: list[User], courses: list[Course], today: date, now: datetime) -> str:
    out: list[str] = []
    w = out.append
    n_rows = sum(len(c.rows) for c in courses)
    w(f"-- TCMS 演示数据（由 deploy/demo/gen_demo_data.py 生成，基准时间 {now:%Y-%m-%d %H:%M}）")
    w(f"-- {len(users)} 名员工、{len(courses)} 门课程、{n_rows} 条报名记录；员工密码统一为 {DEMO_PASSWORD}")
    w("-- 警告：会清空课程、报名和所有非管理员账号，仅用于本地开发和演示环境！")
    w("SET NAMES utf8mb4;")
    w("START TRANSACTION;")
    w("")
    w("DELETE FROM enrollment;")
    w("DELETE FROM course;")
    w("DELETE FROM sys_user WHERE role <> 'ADMIN';")
    w("")
    w("-- 课程创建人、管理员确认完成都记在最早的管理员名下；没有管理员时下面的 INSERT 会因 NOT NULL 失败并回滚")
    w("SET @admin = (SELECT MIN(id) FROM sys_user WHERE role = 'ADMIN');")
    launch = min(u.created_at for u in users)
    w(f"UPDATE sys_user SET created_at = LEAST(created_at, {q(launch - timedelta(days=1))}),")
    w(f"                    updated_at = LEAST(updated_at, {q(launch - timedelta(days=1))}) WHERE id = @admin;")
    w("")

    w("INSERT INTO sys_user (username, password_hash, real_name, department, role, enabled, created_at, updated_at) VALUES")
    vals = []
    for u in users:
        updated = u.left_at or u.created_at
        vals.append(f"  ({q(u.username)}, {q(PASSWORD_HASH)}, {q(u.real_name)}, {q(u.department)}, 'EMPLOYEE', "
                    f"{q(u.left_at is None)}, {q(u.created_at)}, {q(updated)})")
    w(",\n".join(vals) + ";")
    w("")

    w("INSERT INTO course (title, description, instructor, location, start_time, end_time, capacity, enrolled_count, created_by, created_at, updated_at) VALUES")
    vals = []
    for c in sorted(courses, key=lambda c: c.created_at):
        s = c.spec
        vals.append(f"  ({q(s['title'])}, {q(s['desc'])}, {q(s['instructor'])}, {q(s['location'])}, "
                    f"{q(c.start)}, {q(c.end)}, {q(s['capacity'])}, 0, @admin, {q(c.created_at)}, {q(c.updated_at)})")
    w(",\n".join(vals) + ";")
    w("")

    w("INSERT INTO enrollment (course_id, user_id, status, enrolled_at, cancelled_at, completed_at, completed_by, created_at, updated_at)")
    w("SELECT c.id, u.id, v.status, v.enrolled_at, v.cancelled_at, v.completed_at,")
    w("       CASE v.completed_by WHEN 'SELF' THEN u.id WHEN 'ADMIN' THEN @admin END, v.created_at, v.updated_at")
    w("FROM (VALUES")
    vals = []
    rows = sorted(((c, r) for c in courses for r in c.rows), key=lambda cr: cr[1]["created_at"])
    for c, r in rows:
        vals.append(f"  ROW({q(c.spec['title'])}, {q(r['username'])}, {q(r['status'])}, {q(r['enrolled_at'])}, "
                    f"{q(r['cancelled_at'])}, {q(r['completed_at'])}, {q(r['completed_by'])}, "
                    f"{q(r['created_at'])}, {q(r['updated_at'])})")
    w(",\n".join(vals))
    w(") AS v (title, username, status, enrolled_at, cancelled_at, completed_at, completed_by, created_at, updated_at)")
    w("JOIN course c ON c.title = v.title")
    w("JOIN sys_user u ON u.username = v.username")
    w("ORDER BY v.created_at;")
    w("")
    w("-- 冗余计数与报名记录保持一致（ENROLLED + COMPLETED）")
    w("UPDATE course c SET enrolled_count = (")
    w("  SELECT COUNT(*) FROM enrollment e WHERE e.course_id = c.id AND e.status IN ('ENROLLED', 'COMPLETED'));")
    w("")
    w("COMMIT;")
    w("")
    w("SELECT (SELECT COUNT(*) FROM sys_user) AS users, (SELECT COUNT(*) FROM course) AS courses,")
    w("       (SELECT COUNT(*) FROM enrollment) AS enrollments;")
    return "\n".join(out) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser(description="生成 TCMS 演示数据 SQL")
    ap.add_argument("--today", help="基准日期 YYYY-MM-DD，默认今天（北京时间）")
    ap.add_argument("-o", "--output", help="输出文件，默认标准输出")
    args = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    now = datetime.now(CST).replace(tzinfo=None, microsecond=0)
    if args.today:
        today = date.fromisoformat(args.today)
        now = at(today, "12:00")  # 指定日期时以当天中午为"现在"
    else:
        today = now.date()

    users, courses = Gen(today, now).build()
    sql = render(users, courses, today, now)
    if args.output:
        with open(args.output, "w", encoding="utf-8", newline="\n") as f:
            f.write(sql)
    else:
        sys.stdout.write(sql)

    n = sum(len(c.rows) for c in courses)
    phases = {"已结束": 0, "进行中": 0, "未开始": 0}
    for c in courses:
        phases["已结束" if c.end <= now else "进行中" if c.start <= now else "未开始"] += 1
    print(f"已生成：{len(users)} 名员工、{len(courses)} 门课程（{phases}）、{n} 条报名记录", file=sys.stderr)


if __name__ == "__main__":
    main()
