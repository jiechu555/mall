# AI 客服检索质量评测报告

> 评测集 `mall-portal/src/test/resources/ai/eval-set.json`，25 问覆盖商品型号词（10）、FAQ 政策问法（10）、白话口语（5）三类。检索取 top5，命中条件 = 期望类型+标题关键词匹配。测试类 `AiKbEvaluationTest`（@Tag("es")，本地跑）。

## 环境

| 项 | 值 |
|---|---|
| ES | 7.17.3 + ik_max_word（title^2 + content）+ dense_vector 1024 维 |
| 嵌入 | 智谱 embedding-2（1024 维，实测与 mapping 一致） |
| LLM | 智谱 glm-4.5-air（temperature 0.2，实测单问 1.8~3.5s） |
| 知识库 | 36 篇（20 上架商品卡片 + 16 条 FAQ 种子），全部带向量 |

## 结果：BM25 基线 vs 混合检索（2026-10-01 实测）

| 指标 | 纯 BM25 基线 | **混合检索**（BM25+kNN+RRF k=60） |
|---|---|---|
| **召回@5（全体）** | 84.0%（21/25） | **100%（25/25）** ✅ |
| 召回@5（商品型号词） | 10/10 = 100% | 10/10 = 100% |
| 召回@5（FAQ 政策问法） | 8/10 = 80% | 10/10 = 100% |
| 召回@5（白话口语） | 3/5 = 60% | 5/5 = 100% |
| 检索延迟 P95 | **7ms**（中位 6ms） | **243ms**（中位 219ms） |

延迟解读（25 问实测，AiKbEvaluationTest 输出）：混合检索的 ~210ms 差值几乎全部是**智谱 embedding API 网络往返**（每次查询把问题实时向量化）；纯 ES 侧 BM25+kNN+RRF 合计个位数毫秒（36 文档单分片）。优化路径明确：高频问题 embedding 缓存 / 本地小模型向量化，可把 P95 压回 50ms 内——当前为 25 问小库演示值，生产规模需重新压测。

基线 4 个 MISS（怎么退货 / 买了能退吗 / 东西什么时候到 / 不想要了怎么办）全部是**语义等价但字面无关**的查询，混合模式下全部被向量路召回——RRF 融合两路共识后无一回归（基线原有命中未因融合丢失）。

## 端到端问答实测（真实 LLM）

| 问题（白话） | 检索引用 | LLM 回答 |
|---|---|---|
| 买了能退吗 | 退换货政策/流程 FAQ ×2 | "支持，签收后 7 天内可无理由退货（商品需完好），质量问题 15 天内可换货。" |
| 东西什么时候到 | 多久发货等 FAQ（知识库无到货时效数据） | "现货商品一般 48 小时内发货，大促期间可能延长至 72 小时。到货时效请查看订单详情页。"（部分覆盖引导生效） |

Prompt 引导迭代（A/B 实测）：初版"若资料部分相关请回答覆盖部分"对"发货≠送达"类问题仍拒答；改为具体举例引导（"资料可能只覆盖某一方面，如只讲发货时效而未讲到货时效"）后正确回答——具体示例比抽象指令对推理模型更有效。

单问端到端延迟 1.8~3.5s（受智谱 API 网络支配）；LLM 异常/超时自动降级 BM25 摘要 + `degraded:true`（有测试覆盖）。

## 复现

```bash
# 前置：本机 ES 7.17.3+ik 运行中；混合模式需智谱 key
# 纯 BM25 基线（ai.enabled 默认 false）
mvn -pl mall-portal -DskipTests=false -Dtest=AiKbEvaluationTest test
# 混合检索（系统属性注入，测试 JVM 工作目录在 mall-portal/ 读不到仓库根 config/）
mvn -pl mall-portal -DskipTests=false -Dtest=AiKbEvaluationTest \
    -Dai.enabled=true -Dai.api-key=<智谱key> test
# 输出在 surefire 报告的 system-out 段；断言阈值 92%（混合）/ 60%（基线，见测试类注释）
```

运行态启用（portal）：仓库根 `config/application-local.yml`（不入库）设 `ai.enabled/api-key`，启动加 `--spring.profiles.active=dev,local`。

## 与 08 讲指标表对照

| 08 讲指标 | 本报告实测 | 备注 |
|---|---|---|
| 检索质量：召回@5 ≥ 80% | **84% → 100%**（基线 → 混合） | 基线即达标，混合全量命中 |
| 检索延迟 P95 < 50ms | BM25 路 7ms；混合 243ms | 差值=embedding API 网络往返，见上节解读 |
| /ai/chat P95 < 5s | 实测 1.8~3.5s（抽样） | glm-4.5-air，含思维链 |
| 降级行为 | ✅ 已实现并有测试 | BM25 摘要 + degraded:true |
