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

# ============ 步骤 7 ============
heading("步骤 7 · 停止应用（还原环境）")
term_block([
    ("$ powershell \"Get-Process java | Stop-Process -Force\"", YELLOW),
    ("$ netstat -ano | grep -E ':(8080|8085)\\s.*LISTEN' || echo 端口已释放", YELLOW),
    ("端口已释放", "D4D4D4"),
], "终端")
body("Docker 中间件不必停——下次复现直接从步骤 3 开始。彻底不用时退出 Docker Desktop 即可。")

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
            for rr in pp.runs:
                rr.font.color.rgb = RGBColor.from_string("FFFFFF")

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
