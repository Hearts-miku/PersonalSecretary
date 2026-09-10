# 代码审计报告 — PersonalSecretary / WorkLogResume

**审计对象**：`main` @ [`2589cdd`](https://github.com/Hearts-miku/PersonalSecretary/commit/2589cdd) `feat: upgrade security and data management`
**更新日期**：2026-09-10（第 9 轮）
**性质**：只读审计

> 本分支**只存放审计文档，不含源码**。代码链接指向 `2589cdd`，行号长期有效。
> 每轮全量重写；发现编号跨轮次稳定，已解决项移入 [07-已修复与回归记录.md](07-已修复与回归记录.md)。

---

## 产品前提（影响严重级判定）

**应用尚未发布，线上没有存量用户。** 「升级会损坏存量数据」类的发现不作为缺陷记录。
若发布计划有变，[02-P1](02-P1-功能与安全.md) 里的判定需要重新评估。

---

## 文档索引

| 文件 | 内容 |
|---|---|
| [01-P0-阻断项.md](01-P0-阻断项.md) | **0 项** |
| [02-P1-功能与安全.md](02-P1-功能与安全.md) | 1 项（P0-6 回归） |
| [03-P2-健壮性与体验.md](03-P2-健壮性与体验.md) | 13 项 |
| [04-P3-清理项.md](04-P3-清理项.md) | D-1 残留、构建配置 |
| [05-架构观察.md](05-架构观察.md) | 结构性问题 |
| [06-修复顺序建议.md](06-修复顺序建议.md) | 优先级与批次划分 |
| [07-已修复与回归记录.md](07-已修复与回归记录.md) | 已解决 **64 项**；含四次回归事件 |

---

## 当前状态总览

| 严重级 | 未决 | 本轮变化 |
|---|---|---|
| P0 | **0** | 保持 |
| P1 | **1** | 原有 3 项全部解决；P0-6 回归 |
| P2 | **13** | 解决 7 项；新增 2 项 |
| P3 | — | D-1 留下 2 处残留 |

### 本轮是迄今推进最大的一轮

一次完成了此前计划中的 6 个批次：

| 批次 | 内容 | 结果 |
|---|---|---|
| 1 | Gradle wrapper | ✅ 已提交，干净克隆可构建 |
| 2 | [P1-17](07-已修复与回归记录.md) 占位域名 | ✅ 默认值改空 + 三项配置各自提前失败 + 占位符仅作 `placeholder` |
| 3 | [D-1](07-已修复与回归记录.md) 单 provider 收敛 | ✅ `GeminiRepository` → `AiRepository`，两条路径删除 |
| 4 | [P1-16](07-已修复与回归记录.md) 标签缺口 | ✅ 查询词与日志内容均消毒并包标签，另加 18000 字预算 |
| 7 | [P1-4](07-已修复与回归记录.md) 真加密 | ✅ Android Keystore + AES-256-GCM，`v2:` 前缀向下兼容 |
| 5/8 | 清理与 P2 | ✅ N-15、N-16、N-17、P2-4、P2-19、P2-22 |

**D-1 执行得很准确。** 审计曾特别标注「不要连带删掉仍然有用的保护」——核对结果：
OpenAI 的 `finish_reason == "length"` 截断检测（[AiRepository.kt:131](https://github.com/Hearts-miku/PersonalSecretary/blob/2589cdd/app/src/main/java/com/example/data/ai/AiRepository.kt#L131)）
与 o1/o3 的 `temperature` 保护（[:108](https://github.com/Hearts-miku/PersonalSecretary/blob/2589cdd/app/src/main/java/com/example/data/ai/AiRepository.kt#L108)）
都完整保留。全项目搜索 `gemini` / `anthropic` 仅剩 `metadata.json` 一处。

### 唯一需要留意的方向性问题

[P0-6 回归](02-P1-功能与安全.md)：数据库回到 `fallbackToDestructiveMigration()`，
且 `exportSchema` 从 `true` 改回 `false`。

未发布阶段用破坏性迁移是合理的取舍；但**关掉 schema 导出有累积成本**——schema 历史只能
在版本存在的当下捕获，v7 现在没有对应的 json，将来写 7→8 迁移只能手工重建。

---

## 审计覆盖

逐行阅读：构建配置（含新增的 wrapper）、数据层（含重写的 `CryptoManager`）、
AI 层（重命名后的 `AiRepository`）、全部 UI 层、测试。
未覆盖：`Color.kt`、图片资源、`.idea/`、`gradle-wrapper.jar`（二进制）。

## 未执行的验证

**本审计从未编译或运行过代码。** wrapper 现已提交，下一轮起可以实际构建验证。
以下需实机复现：

- [N-19](03-P2-健壮性与体验.md) Keystore 异常路径 —— 依赖设备行为，静态分析只能确认代码路径存在
- [P2-4](07-已修复与回归记录.md) 日历跨月的修法已静态确认正确，但未运行验证
- 本轮两处未使用 import 已确认正文零引用，判定为警告而非错误
