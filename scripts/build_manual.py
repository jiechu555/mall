# -*- coding: utf-8 -*-
"""生成 mall 复现手册 PDF（docx -> LibreOffice PDF）
全部命令与输出为 2026-10-03 实机复现记录。"""
import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

from docx import Document
from docx.shared import Pt, RGBColor, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

doc = Document()
for sec in doc.sections:
    sec.left_margin = Inches(0.7)
    sec.right_margin = Inches(0.7)

style = doc.styles["Normal"]
style.font.name = "Microsoft YaHei"
style._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
style.font.size = Pt(10.5)


def heading(text, size=15, color="1A2636", space_before=14):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(space_before)
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run(text)
    r.bold = True
    r.font.size = Pt(size)
    r.font.color.rgb = RGBColor.from_string(color)
    r.font.name = "Microsoft YaHei"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    p.paragraph_format.keep_with_next = True
    return p


def body(text, size=10.5, color="2C3E50"):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.font.color.rgb = RGBColor.from_string(color)
    r.font.name = "Microsoft YaHei"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    return p


def set_cell_bg(cell, hexcolor):
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), hexcolor)
    cell._tc.get_or_add_tcPr().append(shd)


def term_block(lines, title=None):
    table = doc.add_table(rows=1, cols=1)
    table.autofit = True
    tr = table.rows[0]._tr
    trPr = tr.get_or_add_trPr()
    cantSplit = OxmlElement("w:cantSplit")
    trPr.append(cantSplit)
    cell = table.rows[0].cells[0]
    set_cell_bg(cell, "1E1E1E")
    first = True
    if title:
        p = cell.paragraphs[0]
        r = p.add_run(title)
        r.font.name = "Consolas"
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor.from_string("6A9955")
        r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
        first = False
    for line in lines:
        text, color = (line, "D4D4D4") if isinstance(line, str) else line
        if first:
            p = cell.paragraphs[0]
            first = False
        else:
            p = cell.add_paragraph()
        p.paragraph_format.space_after = Pt(0)
        r = p.add_run(text)
        r.font.name = "Consolas"
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor.from_string(color)
        r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return table


GREEN = "6A9955"


def tip_block(title, text):
    """知识点卡片：浅蓝底，穿插在步骤之间帮助理解"""
    table = doc.add_table(rows=1, cols=1)
    table.autofit = True
    tr = table.rows[0]._tr
    trPr = tr.get_or_add_trPr()
    cantSplit = OxmlElement("w:cantSplit")
    trPr.append(cantSplit)
    cell = table.rows[0].cells[0]
    set_cell_bg(cell, "EEF4FB")
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("💡 知识点 · " + title)
    r.bold = True
    r.font.size = Pt(9.5)
    r.font.color.rgb = RGBColor.from_string("0B57D0")
    r.font.name = "Microsoft YaHei"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    p2 = cell.add_paragraph()
    p2.paragraph_format.space_after = Pt(0)
    r2 = p2.add_run(text)
    r2.font.size = Pt(9.5)
    r2.font.color.rgb = RGBColor.from_string("2C3E50")
    r2.font.name = "Microsoft YaHei"
    r2._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return table
YELLOW = "DCDCAA"
BLUE = "569CD6"
GRAY = "9AA4B2"
RED = "F48771"

# ============ 封面 ============
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("mall 本地复现手册")
r.bold = True
r.font.size = Pt(24)
r.font.color.rgb = RGBColor.from_string("1A2636")
r.font.name = "Microsoft YaHei"
r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("五套中间件 · 双应用启动 · 秒杀全流程 · 2026-10-03 实机记录")
r.font.size = Pt(11)
r.font.color.rgb = RGBColor.from_string("5F6B7A")
r.font.name = "Microsoft YaHei"
r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")

body("")
body("适用环境：Windows + JDK 17 + Maven 3.9.9（D:\\dev-tools）+ Docker Desktop。全部命令与输出为 2026-10-03 实机复现记录，逐条可复制。")
body("跟随本手册走完 = 你独立跑通了秒杀子系统（简历项目一）。出错先查文末《常见故障速查表》。", color="0B57D0")

# ============ 学习地图 ============
heading("学习地图 · 这本手册教你什么", size=13, space_before=10)
body("mall 是三个项目里工程链最全的一个——企业级 Java 栈的核心概念在这里各就各位：", size=9.5)
tbl = doc.add_table(rows=8, cols=2)
tbl.style = "Table Grid"
rows = [
    ("概念", "在哪学（步骤）"),
    ("Docker 容器与端口映射", "步骤 1"),
    ("SQL 幂等写法与密码哈希", "步骤 2"),
    ("Maven 多模块构建与 fat jar", "步骤 3"),
    ("JWT 无状态鉴权 + 配置隔离（profile）", "步骤 4"),
    ("会员端 / 管理端双体系", "步骤 5"),
    ("缓存（Redis）+ 脚本原子性（Lua）+ 消息队列（MQ）", "步骤 6 + 原理图解"),
    ("压测思维：并发 / QPS / 分位数延迟", "历史实测数据"),
]
for i, (a, b) in enumerate(rows):
    c0, c1 = tbl.rows[i].cells
    c0.text, c1.text = a, b
    for c in (c0, c1):
        for pp in c.paragraphs:
            pp.paragraph_format.keep_with_next = (i == 0)
            for rr in pp.runs:
                rr.font.size = Pt(8.5)
                rr.font.name = "Microsoft YaHei"
                rr._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    if i == 0:
        set_cell_bg(c0, "1A2636"); set_cell_bg(c1, "1A2636")
        for pp in c0.paragraphs + c1.paragraphs:
            for rr in pp.runs:
                rr.font.color.rgb = RGBColor.from_string("FFFFFF")
doc.add_paragraph().paragraph_format.space_after = Pt(2)

# ============ 目录速览 ============
heading("项目目录速览 · 每个模块是干什么的", size=13, space_before=10)
term_block([
    ("mall/  （Maven 多模块工程）", BLUE),
    ("├─ mall-admin/    ← 后台管理应用（端口 8080）：商品/订单/用户管理 + 登录", "D4D4D4"),
    ("├─ mall-portal/   ← 商城前台应用（端口 8085）：会员下单 + 秒杀子系统★", "D4D4D4"),
    ("│   └─ seckill/", GRAY),
    ("│       ├─ controller/FlashSaleController   ← 三个接口：list/order/result", "D4D4D4"),
    ("│       ├─ service/FlashSaleServiceImpl     ← 限流→核销→Lua→MQ 的编排", "D4D4D4"),
    ("│       ├─ component/FlashSaleOrderReceiver ← MQ 消费端（幂等落库）", "D4D4D4"),
    ("│       └─ resources/luascript/seckill_deduct.lua ← 原子预扣脚本（精读主角）", "D4D4D4"),
    ("├─ mall-common/   ← 工具与通用响应；mall-mbg/ ← MyBatis 数据库映射生成", "D4D4D4"),
    ("├─ mall-security/ ← JWT 鉴权组件（admin/portal 共用框架、各配各的密钥）", "D4D4D4"),
    ("├─ config/application-local.yml ← 本地私密配置（AI key，不入库）", "D4D4D4"),
    ("└─ document/     ← sql/（建库脚本）docker/（中间件编排）seckill-benchmark/（压测）", "D4D4D4"),
], title="目录树")

# ============ 步骤 1 ============
heading("步骤 1 · 启动 Docker Desktop 与五套中间件")
body("开始菜单启动 Docker Desktop（托盘图标变绿约 1 分钟）。五套中间件容器配置了自启，Docker 起来后直接验证：")
term_block([
    ("$ docker ps --format '{{.Names}}\\t{{.Status}}'", YELLOW),
    ("elasticsearch   Up 33 seconds", "D4D4D4"),
    ("mongo           Up 33 seconds", "D4D4D4"),
    ("rabbitmq        Up 33 seconds", "D4D4D4"),
    ("redis           Up 33 seconds", "D4D4D4"),
    ("mysql           Up 33 seconds", "D4D4D4"),
], "终端")
body("若有容器没起来：docker start mysql redis rabbitmq elasticsearch mongo。四件套健康抽查：")
term_block([
    ("$ docker exec mysql mysql -uroot -proot -e 'SELECT 1'", YELLOW),
    ("1", "D4D4D4"),
    ("$ docker exec redis redis-cli ping", YELLOW),
    ("PONG", "D4D4D4"),
    ("$ curl -s localhost:9200 | grep tagline", YELLOW),
    ('  "tagline" : "You Know, for Search"', "D4D4D4"),
], "终端")

tip_block("Docker 容器与端口映射（步骤 1 在干什么）",
    "镜像是『安装包』（只读模板），容器是『跑起来的实例』。MySQL 装在容器里 = 数据库不污染宿主机、版本精确可控（mysql:5.7 到哪都一样）。端口映射 -p 3306:3306 是把容器内的门牌接到宿主机——你连 localhost:3306 实际进的是容器。数据放卷（volume）持久化在宿主盘，容器删了数据还在。想一想：为什么五套中间件各一个容器，而不是全装一台虚拟机？→ 隔离+按需重启+启动秒级，出了问题爆炸半径小。")

# ============ 步骤 2 ============
heading("步骤 2 · 数据库与演示数据检查")
body("mall 库随容器持久化在 D 盘（首次安装时已导入 document/sql/mall.sql + seckill.sql，重装系统才需要重做）。秒杀演示数据用下面一段 SQL 激活（时间无关，任何时刻照手册跑都能命中）：")
term_block([
    ("$ docker exec -i mysql mysql -uroot -proot mall <<'SQL'", YELLOW),
    ("-- 活动窗口改到当前日期；插入 00:00~23:59 全天演示场次（id=8）", GRAY),
    ("UPDATE sms_flash_promotion SET start_date=DATE_SUB(CURDATE(),INTERVAL 2 DAY),", GREEN),
    ("       end_date=DATE_ADD(CURDATE(),INTERVAL 30 DAY) WHERE id=14;", GREEN),
    ("INSERT INTO sms_flash_promotion_session (id,name,start_time,end_time,status,create_time)", GREEN),
    ("VALUES (8,'demo全天场','00:00:00','23:59:59',1,NOW())", GREEN),
    ("ON DUPLICATE KEY UPDATE start_time='00:00:00', end_time='23:59:59', status=1;", GREEN),
    ("-- 演示商品：活动14/场次8/华为P20(id 26)，秒杀价 599、库存 100、每人限 1 件", GRAY),
    ("DELETE FROM sms_flash_promotion_product_relation WHERE flash_promotion_id=14 AND flash_promotion_session_id<>8;", GREEN),
    ("INSERT INTO sms_flash_promotion_product_relation", GREEN),
    ("  (id,flash_promotion_id,flash_promotion_session_id,product_id,flash_promotion_price,flash_promotion_count,flash_promotion_limit,sort)", GREEN),
    ("VALUES (200,14,8,26,599.00,100,1,0);", GREEN),
    ("-- 演示会员 test 的密码重置为 macro123（复用 admin 的 bcrypt 哈希）", GRAY),
    ("UPDATE ums_member m JOIN ums_admin a ON a.username='admin' SET m.password=a.password WHERE m.username='test';", GREEN),
    ("SQL", YELLOW),
], "终端")
body("这段 SQL 是「演示脚手架」：真实业务里令牌由运营侧批量签发、库存由场次开始前的预热任务写入，二开版保留了服务层接口（issueTokens / warmUp），演示阶段用 redis-cli 等价注入——见步骤 6。")

tip_block("可重跑 SQL 与 bcrypt（步骤 2 的两个细节）",
    "演示 SQL 设计成重复执行结果一致：DELETE 再 INSERT、INSERT ... ON DUPLICATE KEY UPDATE——这叫幂等，运维脚本的铁律。会员密码存的是 bcrypt 哈希：单向（不可逆推原文）、带盐（同密码每次哈希结果都不同）——所以『找回密码』只能重置不能告诉你原密码。本手册直接复用 admin 的哈希，等于给 test 账号设了同款密码。想一想：为什么不用 MD5？→ 无盐且太快，彩虹表+暴力破解秒破。")

# ============ 步骤 3 ============
heading("步骤 3 · 打包两个应用（约 33 秒）")
body("注意 -Ddocker.skip=true：pom 里的 docker-maven-plugin 默认指向原作者办公室的远程 Docker（192.168.3.101:2375），不跳过必报错（故障表 F1）。")
term_block([
    ("$ cd /c/Users/12808/Documents/code/mall", YELLOW),
    ("$ export PATH=\"/d/dev-tools/apache-maven-3.9.9/bin:$PATH\"", YELLOW),
    ("$ mvn -q -pl mall-admin,mall-portal -am clean package -DskipTests -Ddocker.skip=true", YELLOW),
    ("$ stat -c '%y %n' mall-admin/target/*.jar mall-portal/target/*.jar | cut -c1-19,36-", GRAY),
    ("2026-10-03 16:57:15 .../mall-admin-1.0-SNAPSHOT.jar      # 92 MB", "D4D4D4"),
    ("2026-10-03 16:57:24 .../mall-portal-1.0-SNAPSHOT.jar     # 139 MB", "D4D4D4"),
], "终端")

tip_block("Maven 多模块与 fat jar（步骤 3 的构建体系）",
    "mall 是多模块工程：mall-common（工具）、mall-mbg（数据库映射）、mall-security（鉴权）、mall-admin/portal（应用）。-pl 选要打包的应用，-am 自动先构建它依赖的模块。fat jar（139MB）= 应用代码 + BOOT-INF 里嵌套的全部依赖 + 内嵌 Tomcat → java -jar 一条命令启动，不用部署应用服务器。-DskipTests 跳过测试加速（正式交付绝不许跳）。想一想：为什么依赖模块改了行代码，admin 也要重新打包？→ jar 里嵌的是依赖的副本，不是引用。")

# ============ 步骤 4 ============
heading("步骤 4 · 启动 mall-admin，登录拿 JWT（约 20 秒）")
term_block([
    ("$ java -jar mall-admin/target/mall-admin-1.0-SNAPSHOT.jar --spring.profiles.active=dev", YELLOW),
    ("2026-10-03T17:08:17 INFO ... TomcatWebServer : Tomcat started on port 8080", "D4D4D4"),
    ("2026-10-03T17:08:17 INFO ... MallAdminApplication : Started MallAdminApplication in 19.447 seconds", "D4D4D4"),
], "终端")
body("看到 Started 字样即成功（此命令占住终端，新开一个 Git Bash 窗口继续）。登录与鉴权验证：")
term_block([
    ("$ curl -s -X POST http://localhost:8080/admin/login -H 'Content-Type: application/json' \\", YELLOW),
    ("    -d '{\"username\":\"admin\",\"password\":\"macro123\"}' | head -c 130", YELLOW),
    ('{"code":200,"message":"操作成功","data":{"tokenHead":"Bearer ","token":"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWI...', "D4D4D4"),
    ("$ # 取 token 拼 tokenHead 后放进 Authorization 头查商品列表：", GRAY),
    ("$ curl -s 'http://localhost:8080/product/list?pageNum=1&pageSize=2' -H \"Authorization: $TOKEN\"", YELLOW),
    ('{"code":200,...,"list":[{"id":26,...,"name":"华为 HUAWEI P20 ","pic":"http://macro-oss...', "D4D4D4"),
], "终端")
body("商品 26 号「华为 HUAWEI P20」正是步骤 6 秒杀的演示商品。浏览器打开 http://localhost:8080/swagger-ui/index.html 可见接口文档（下图为实机截图；knife4j 的 /doc.html 不在安全白名单里，打开只会看到一坨 401 JSON，见 F7）：")
pic = doc.add_picture(r"C:\Users\12808\Documents\code\mall\document\manual-assets\swagger-ui.png", width=Inches(5.8))
doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER

tip_block("JWT 三段式与 profile 隔离（步骤 4 的鉴权原理）",
    "JWT = 头.载荷.签名 三段 Base64。载荷存用户名+过期时间（可解码看，别放敏感信息）；签名用服务端密钥对前两段计算——改一个字符签名就对不上，服务端验签即可信，不用存 session（无状态，水平扩容随便加机器）。tokenHead『Bearer 』是约定前缀。profile（dev/local/prod）让同一份代码不同环境读不同配置——local 装密钥不入 git。想一想：JWT 怎么『注销』？→ 难题：本身无法撤销，只能短过期+黑名单，如实答反而加分。")

# ============ 步骤 5 ============
heading("步骤 5 · 启动 mall-portal，会员登录（约 21 秒）")
body("portal 加 local profile 是为了读本地私密配置（智谱 AI key，不入库）：")
term_block([
    ("$ java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT.jar --spring.profiles.active=dev,local", YELLOW),
    ("2026-10-03T17:10:46 INFO ... MallPortalApplication : Started MallPortalApplication in 21.063 seconds", "D4D4D4"),
    ("", "D4D4D4"),
    ("$ # 会员登录（test / macro123，密码在步骤 2 已重置）", GRAY),
    ("$ curl -s -X POST 'http://localhost:8085/sso/login?username=test&password=macro123'", YELLOW),
    ('{"code":200,...,"data":{"token":"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."}}   # 155 字符', "D4D4D4"),
], "终端")

tip_block("双端双密钥（步骤 5 的体系设计）",
    "mall-admin（后台管理，给运营）和 mall-portal（商城前台，给买家）是两个独立应用：独立端口、独立数据库表域（ums_admin vs ums_member）、独立 JWT 密钥（mall-admin-secret vs mall-portal-secret）。为什么分开？管理员 token 拿不到 portal 接口、买家 token 进不了后台——爆炸半径隔离 + 部署伸缩独立。这是微服务拆分的最小雏形。想一想：两边能用同一个用户表吗？→ 能但耦合，权限模型完全不同迟早分家。")

# ============ 步骤 6 ============
heading("步骤 6 · 秒杀全流程（三层防刷完整可见）")
body("同步链路只碰 Redis/MQ 不落库；消费端异步落库。逐段执行：")
term_block([
    ("# ① 预热库存 + 注入令牌（注意令牌值要带 JSON 引号——Java 侧是 Jackson 序列化，见 F6）", GRAY),
    ("$ SECTOKEN=$(py -3 -c \"import uuid; print(uuid.uuid4())\")", YELLOW),
    ('$ docker exec redis redis-cli SET "mall:sms:flashSaleStock:14:8:26" 100 EX 86400', YELLOW),
    ('$ docker exec redis redis-cli SADD "mall:sms:flashSaleToken:14:8" "\\"$SECTOKEN\\""', YELLOW),
    ("OK / (integer) 1", "D4D4D4"),
], "终端")
term_block([
    ("# ② 当前场次与实时余量", GRAY),
    ('$ curl -s http://localhost:8085/seckill/list -H "Authorization: Bearer $MTOKEN"', YELLOW),
    ('{"code":200,"message":"操作成功","data":{"promotionId":14,"sessionId":8,...', "D4D4D4"),
    ('  "products":[{"productId":26,"productName":"华为 HUAWEI P20 ","flashPromotionPrice":599.0,', "D4D4D4"),
    ('    "flashPromotionCount":100,"flashPromotionLimit":1,"remainingStock":100}]}}', "D4D4D4"),
], "终端")
term_block([
    ("# ③ 同步下单（限流→令牌 SREM 核销→Lua 原子预扣→发 MQ）", GRAY),
    ('$ curl -s -X POST "http://localhost:8085/seckill/order?promotionId=14&sessionId=8', YELLOW),
    ('    &productId=26&memberReceiveAddressId=1&token=$SECTOKEN" -H "Authorization: Bearer $MTOKEN"', YELLOW),
    ('{"code":200,"message":"受理中，请凭受理号轮询结果","data":"05b0b9b1-008f-422b-8688-c3f9069f505e"}', "D4D4D4"),
    ("", "D4D4D4"),
    ("# ④ 3 秒后轮询结果（MQ 消费端已生成正式订单）", GRAY),
    ('$ curl -s "http://localhost:8085/seckill/result?ticket=05b0b9b1-..." -H "Authorization: Bearer $MTOKEN"', YELLOW),
    ('{"code":200,"message":"操作成功","data":{"status":"SUCCESS","orderSn":"202610030100000002"}}', "D4D4D4"),
], "终端")
term_block([
    ("# ⑤ 防刷验证 a：重放同一令牌（SREM 已核销 → 拒绝）", GRAY),
    ("$ curl -s -X POST ...token=$SECTOKEN...", YELLOW),
    ('{"code":500,"message":"秒杀令牌无效"}', RED),
    ("# ⑥ 防刷验证 b：换新令牌再买（Lua 限购 bought+1 > perLimit → 拒绝）", GRAY),
    ("$ curl -s -X POST ...token=$NEW_TOKEN...", YELLOW),
    ('{"code":500,"message":"超出限购数量"}', RED),
    ("# ⑦ 终态核对：Redis 余量 99 / 已购 Hash member=1→1 件 / DB 受理单 status=1", GRAY),
    ("$ docker exec redis redis-cli GET mall:sms:flashSaleStock:14:8:26", YELLOW),
    ("99", "D4D4D4"),
    ("$ docker exec mysql mysql -uroot -proot mall -e 'SELECT id,status,order_sn,flash_promotion_price FROM sms_flash_promotion_order;'", YELLOW),
    ("8    1    202610030100000002    599.00", "D4D4D4"),
], "终端")

tip_block("Redis + Lua + MQ 三件套分工（步骤 6 的世界模型）",
    "Redis：内存库，单线程处理命令 → 十万级 QPS，扛得住瞬时洪峰；但单条命令不够用（扣库存是查-判-改三步），Lua 脚本把三步打包成一个原子单元——期间不接受任何其他命令。MQ：消息队列，发出去就返回（异步），消费端按自己的节奏慢慢落库——洪峰在 MQ 里排队，像水库蓄洪。三者分工：Redis 管快、Lua 管对、MQ 管稳。想一想：为什么不用 MySQL 行锁 SELECT FOR UPDATE？→ 能，但每请求一个事务连接，500 并发数据库连接池就爆了。")

# ============ 步骤 7 ============
heading("步骤 7 · 停止应用（还原环境）")
term_block([
    ("$ powershell \"Get-Process java | Stop-Process -Force\"", YELLOW),
    ("$ netstat -ano | grep -E ':(8080|8085)\\s.*LISTEN' || echo 端口已释放", YELLOW),
    ("端口已释放", "D4D4D4"),
], "终端")
body("Docker 中间件不必停——下次复现直接从步骤 3 开始。彻底不用时退出 Docker Desktop 即可。")

tip_block("优雅停机（步骤 7 为什么这么停）",
    "直接关窗口=进程被强杀：正在处理的请求断在半路（下单扣了 Redis 没发 MQ 就死，靠对账救回）。Stop-Process 虽是强杀，但本地开发可接受；生产用 kill -15（SIGTERM）给进程善后时间：停止接新请求→处理完存量→释放连接→退出。联想：TinyWebServer 手册里 SIGTERM 优雅退出是同一原理的 C++ 版。想一想：为什么 mall 的对账任务能容忍强杀？→ 因为设计了补偿链路——兜底思维贯穿始终。")

# ============ 原理图解 ============
heading("原理图解 · 秒杀为什么这么设计（双链路）", size=14)
term_block([
    ("【同步受理链】POST /seckill/order —— 每请求 O(1)，只碰内存态存储，不落库", BLUE),
    ("  ① 限流：memberId 计数器（Redis INCR + 过期）", "D4D4D4"),
    ("  ② 令牌核销：SREM 原子——删掉才算数，重放直接拒", "D4D4D4"),
    ("  ③ Lua 原子预扣：[限购判断 + 库存 DECRBY + 已购 HINCRBY] 一个脚本", "D4D4D4"),
    ("     Redis 单线程执行 Lua 期间不被打断 = 天然临界区，无锁胜有锁", GRAY),
    ("  ④ 发 RabbitMQ 消息（TTL+死信→延迟取消）→ 立刻返回受理号 ticket", "D4D4D4"),
    ("", "D4D4D4"),
    ("【异步落库链】MQ 消费端 —— 慢操作全部挪出主链路", BLUE),
    ("  幂等受理单 insert（唯一键：活动+场次+商品+会员 四列）", "D4D4D4"),
    ("  → 原子 SQL 锁库存 → order_type=1 正式订单 → 延迟任务到期核销", "D4D4D4"),
    ("  → 对账任务兜底：守恒校验 / 差异回补 / 卡死受理单清扫", "D4D4D4"),
], title="链路图")
body("三个「为什么」（面试必问）：", size=10)
body("① 为什么同步链不落库？数据库连接和事务是稀缺资源，秒杀瞬时流量打 MySQL 必雪崩。Redis 单线程内存操作扛得住，MySQL 只承受消费端的匀速写入——削峰填谷。", size=9.5)
body("② 为什么用 Lua？扣库存要「查-判-改」三步，三条命令间可能插入别人的命令（竞态）。Lua 脚本在 Redis 里原子执行，三步变一步——这就是步骤 6 里 100 件永远不少卖的保证。", size=9.5)
body("③ 消息重复投递怎么办？消费端唯一键四列 = 天然幂等键：重复消息 insert 撞唯一键直接丢弃，配合两段事务，至多一次生效。", size=9.5)

# ============ 代码精读 ============
heading("代码精读 · seckill_deduct.lua（18 行扛住 1000 并发的脚本）", size=14)
term_block([
    ("-- KEYS[1] 库存key   KEYS[2] 已购Hash key", GRAY),
    ("-- ARGV[1] memberId  ARGV[2] quantity  ARGV[3] perLimit", GRAY),
    ("-- 返回：1 预扣成功；0 售罄；-1 超出限购；-2 活动未预热", GRAY),
    ("local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or 0)", "D4D4D4"),
    ("if bought + tonumber(ARGV[2]) > tonumber(ARGV[3]) then return -1 end", RED),
    ("local stock = tonumber(redis.call('GET', KEYS[1]) or -1)", "D4D4D4"),
    ("if stock < 0 then return -2 end", RED),
    ("if stock < tonumber(ARGV[2]) then return 0 end", RED),
    ("redis.call('DECRBY', KEYS[1], ARGV[2])", GREEN),
    ("redis.call('HINCRBY', KEYS[2], ARGV[1], ARGV[2])", GREEN),
    ("return 1", GREEN),
], title="mall-portal/.../luascript/seckill_deduct.lua")
body("逐行读：先查已购（Hash 里 memberId 已买几件）——超限购立刻返回 -1，库存都没碰；再取库存——没预热返回 -2（提示运营漏了 warmUp，而不是静默失败）；库存不够返回 0（售罄）；三道闸全过才执行写操作：DECRBY 扣库存 + HINCRBY 记已购，两步在同一个脚本里 = 原子。注意设计细节：①检查顺序是「先限购后库存」——限购是业务规则优先级更高且不消耗资源；②返回码用数字不用字符串——Lua 返回给 Java 走 long，省一次类型转换；③tonumber 包住所有入参——Java 序列化器可能把数字写成字符串，统一归一。这就是步骤 6 里「100 件永远不少卖、每人永远买不了第二件」的全部秘密。", size=9.5)

# ============ 历史实测 ============
heading("历史实测数据（简历上每个数字的出处）", size=14)
tbl = doc.add_table(rows=6, cols=2)
tbl.style = "Table Grid"
rows = [
    ("实测项（全部仓库/报告可查）", "结果"),
    ("JUnit 1000 并发抢 100 件库存", "成功恰好 100、超卖 0（seckill 测试类）"),
    ("JMeter 5000 请求 / 500 并发", "下单接口 P99 = 55ms；售罄 4900 全被拦"),
    ("阶梯压测峰值", "1441 QPS——同机部署受限值（负载机/被测/中间件共 CPU），先证明拐点存在"),
    ("RabbitMQ 停 30s 故障演练", "同步链降级不雪崩，恢复后自动续消费"),
    ("源码级排障两例", "MySQL 容器崩溃（bind mount 遮盖 /etc/mysql）；「401」实为 NPE 经错误页伪装"),
]
for i, (a, b) in enumerate(rows):
    c0, c1 = tbl.rows[i].cells
    c0.text, c1.text = a, b
    for c in (c0, c1):
        for pp in c.paragraphs:
            for rr in pp.runs:
                rr.font.size = Pt(9)
                rr.font.name = "Microsoft YaHei"
                rr._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    if i == 0:
        set_cell_bg(c0, "1A2636"); set_cell_bg(c1, "1A2636")
        for pp in c0.paragraphs + c1.paragraphs:
            pp.paragraph_format.keep_with_next = True
            for rr in pp.runs:
                rr.font.color.rgb = RGBColor.from_string("FFFFFF")
doc.add_paragraph().paragraph_format.space_after = Pt(2)

# ============ 面试五问 ============
heading("面试五问（面试官视角，答题要点）", size=14)
for q, a in [
    ("Q1 哪部分是你写的？", "mall 基座是开源的（如实说，面试官认识它）；我的增量 = 秒杀子系统 + 全部测试压测 + 两例排障，fork 上 18 个 commit 逐个可查。"),
    ("Q2 超卖怎么防的？", "三层：Lua 原子预扣挡并发窗口；DB 唯一键四列是硬约束兜底；对账任务守恒校验自动回补——每层防的是不同失效模式。"),
    ("Q3 消息丢了/重复了怎么办？", "重复→唯一键幂等；丢失→对账任务比对 Redis 已扣与 DB 落单差异自动补；卡死→清扫任务关单回库存。"),
    ("Q4 1441 QPS 不高啊？", "同机受限值，负载机/被测/五套中间件共享 CPU——先承认，再讲拐点分析方法，最后说独立负载机复测是下一步。"),
    ("Q5 为什么 Redis Lua 无回滚也要用？", "脚本内先做类型/参数校验再写，失败路径不产生半写状态；真出现不一致靠对账兜底——把「无回滚」当设计输入而非缺陷。"),
]:
    body(q, color="1A2636", size=10)
    body("要点：" + a, size=9.5, color="5F6B7A")

# ============ 故障表 ============
heading("常见故障速查表")
tbl = doc.add_table(rows=8, cols=2)
tbl.style = "Table Grid"
rows = [
    ("编号", "症状 → 原因 → 解法"),
    ("F1", "打包报 docker access object 192.168.3.101:2375 failed → pom 的 docker 插件指向上游远程 Docker → 加 -Ddocker.skip=true"),
    ("F2", "打包时 jar 替换失败/文件被占 → 应用还在运行，Windows 锁 jar → 先 Stop-Process java 再 clean package"),
    ("F3", "应用起不来报 Port 8080/8085 was already in use → 残留 java 进程 → taskkill 或换 --server.port=8081"),
    ("F4", "接口返回 HTTP 200 但 body 是 code:401「暂未登录」→ mall 的错误处理约定，看 body.code 不看 HTTP 状态码 → 检查 Authorization 头"),
    ("F5", "调 /admin/product/list 报 401 但 token 明明正确 → 实际是 404 伪装（mall 错误页转发），admin 接口没有 /admin 前缀 → 用 /product/list"),
    ("F6", "redis-cli 注入令牌后下单报「秒杀令牌无效」→ Java 用 Jackson 序列化，集合元素存的是带引号 JSON 串 → SADD key \"\\\"uuid\\\"\"（双层引号）"),
    ("F7", "浏览器开 /doc.html 只看到一坨 401 JSON → knife4j 路径不在安全白名单（ignored.urls 只有 /swagger-ui/ 与 /v3/api-docs/*）→ 用 http://localhost:8080/swagger-ui/index.html"),
]
for i, (a, b) in enumerate(rows):
    tr1 = tbl.rows[i]._tr
    trPr1 = tr1.get_or_add_trPr()
    cs1 = OxmlElement("w:cantSplit")
    trPr1.append(cs1)
    c0, c1 = tbl.rows[i].cells
    c0.text, c1.text = a, b
    for c in (c0, c1):
        for pp in c.paragraphs:
            for rr in pp.runs:
                rr.font.size = Pt(9)
                rr.font.name = "Microsoft YaHei"
                rr._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    if i == 0:
        set_cell_bg(c0, "1A2636"); set_cell_bg(c1, "1A2636")
        for pp in c0.paragraphs + c1.paragraphs:
            pp.paragraph_format.keep_with_next = True
            for rr in pp.runs:
                rr.font.color.rgb = RGBColor.from_string("FFFFFF")

# ============ 术语表 ============
heading("术语表 · 面试口语必备", size=13, space_before=10)
tbl = doc.add_table(rows=11, cols=2)
tbl.style = "Table Grid"
rows = [
    ("术语", "一句话解释"),
    ("QPS / TPS", "Queries Per Second 每秒请求数 / 每秒事务数（一个事务可能含多个请求）"),
    ("P50 / P99 延迟", "50%/99% 的请求快于此耗时；P99 看长尾——平均数会骗人，长尾不会"),
    ("超卖 Oversell", "并发下库存扣成负数：查-判-改三步间被插入其他请求的经典竞态"),
    ("幂等 Idempotent", "同一操作执行多次效果等同一次（MQ 重复投递场景的救命特性）"),
    ("削峰填谷", "瞬时洪峰先进队列缓存，消费端按稳定速率处理——流量的水库"),
    ("死信队列 DLQ", "Dead Letter Queue：过期/消费失败的消息去向（延迟关单的实现基础）"),
    ("Fat Jar", "含全部依赖与内嵌容器的可执行 jar，java -jar 直接跑"),
    ("JWT", "JSON Web Token：签名自证的用户凭证，服务端无状态、天然支持横向扩容"),
    ("BCrypt", "带盐慢哈希算法：单向不可逆、算力成本高，存密码的标准选择"),
    ("Base 镜像 / 容器", "镜像=只读模板，容器=运行实例；容器删了数据要靠卷 volume 存活"),
]
for i, (a, b) in enumerate(rows):
    c0, c1 = tbl.rows[i].cells
    c0.text, c1.text = a, b
    for c in (c0, c1):
        for pp in c.paragraphs:
            pp.paragraph_format.keep_with_next = (i == 0)
            for rr in pp.runs:
                rr.font.size = Pt(9)
                rr.font.name = "Microsoft YaHei"
                rr._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    if i == 0:
        set_cell_bg(c0, "1A2636"); set_cell_bg(c1, "1A2636")
        for pp in c0.paragraphs + c1.paragraphs:
            for rr in pp.runs:
                rr.font.color.rgb = RGBColor.from_string("FFFFFF")
doc.add_paragraph().paragraph_format.space_after = Pt(2)

# ============ 自测题 ============
heading("复现自测题（答出来说明你真懂了）", size=12, space_before=8)
for q in [
    "① 秒杀的同步链路（/seckill/order）碰了哪些存储？哪些操作是它故意不做的？",
    "② 三层防刷的顺序是什么？为什么令牌核销放在 Lua 预扣之前？",
    "③ Redis 令牌注入时为什么要带双层引号？（提示：Java 侧的序列化器）",
]:
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(q)
    r.font.size = Pt(9.5)
    r.font.color.rgb = RGBColor.from_string("2C3E50")
    r.font.name = "Microsoft YaHei"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(2)
r = p.add_run("答案都在步骤 6 的注释和 F6 里——能脱稿讲清楚，面试这一关你就稳了。")
r.font.size = Pt(9.5)
r.font.color.rgb = RGBColor.from_string("0B57D0")
r.font.name = "Microsoft YaHei"
r._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")

doc.save(r"C:\Users\12808\Documents\复现手册\mall复现手册.docx")
print("docx saved")
