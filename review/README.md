# 代码审计报告 — PersonalSecretary / WorkLogResume

**审计对象**：`main` @ [`fa9c62c`](https://github.com/Hearts-miku/PersonalSecretary/commit/fa9c62c) `feat(profile): add AI content refinement and manual edit support`
**更新日期**：2026-08-13（第 5 轮）
**性质**：只读审计

> 本分支**只存放审计文档，不含源码**。文档中的代码链接指向 `fa9c62c` 这个提交，
> 行号因此长期有效，不会随 main 后续变动而漂移。
>
> 每轮**全量重写**，始终描述当前代码的真实状态。发现编号（`P1-4`、`N-5` …）跨轮次
> 保持稳定；已解决项移入 [07-已修复与回归记录.md](07-已修复与回归记录.md)。

---

## 文档索引

| 文件 | 内容 |
|---|---|
| [01-P0-阻断项.md](01-P0-阻断项.md) | **0 项**。首次全部清零 |
| [02-P1-功能与安全.md](02-P1-功能与安全.md) | 6 项。凭据处理、升级路径、提示词隔离 |
| [03-P2-健壮性与体验.md](03-P2-健壮性与体验.md) | 21 项 |
| [04-P3-清理项.md](04-P3-清理项.md) | 死代码、误提交文件、构建配置 |
| [05-架构观察.md](05-架构观察.md) | 结构性问题 |
| [06-修复顺序建议.md](06-修复顺序建议.md) | 优先级与批次划分 |
| [07-已修复与回归记录.md](07-已修复与回归记录.md) | 已解决 **36 项**；含两次回归事件的成因 |

---

## 当前状态总览

| 严重级 | 未决 | 本轮变化 |
|---|---|---|
| P0 | **0** | 两个编译阻断已解决；P0-6 破坏性迁移已换成真实 Migration |
| P1 | **6** | 解决 2 项；新引入 2 项；1 项回归 |
| P2 | **21** | 解决 2 项；新引入 7 项 |
| P3 | — | 新增 1 个误提交文件 |

### 里程碑

**P0 首次清零。** 上一轮遗留的两个编译阻断（`CryptoManager` 缺失、`AndroidManifest`
引用已删除的主题）都已修复，`fallbackToDestructiveMigration` 也换成了真实的
`MIGRATION_5_6`——首轮审计中最要紧的那批问题至此全部收口。

### 但升级路径成了新的风险集中区

本轮引入的两件事叠加在一起，构成当前最需要关注的地方：

1. [`CryptoManager`](02-P1-功能与安全.md) 用 Base64 冒充加密，且**会静默损坏存量用户的 API Key**
2. [迁移从未被执行验证过](02-P1-功能与安全.md)，而 `fallbackToDestructiveMigration` 已移除——
   schema 一旦不匹配就是硬崩溃，不再是静默清库

两者都只在「老用户升级」这条路径上触发，而这条路径**至今没有被构建或运行验证过**
（`app/schemas/6.json` 缺失即为佐证）。

### 新功能的质量

本轮新增「AI 提炼导入内容」「工作/项目经历手动编辑」「清空所有数据」三项功能。
手动编辑复用了 P1-14 已验证的 ViewModel 状态模式，导入对话框重构为「选目标 + 选方式」，
清空数据有二次确认且保留 API Key——这些都做得不错。

问题集中在两点：**新的 AI 调用绕开了项目自己的提示词隔离约定**（用了 `<imported_data>`
标签，而 sanitizer 只认 `<user_raw_content>`），以及**三个 `cancelEditing*` 函数写了却没接**，
导致编辑态没有取消出口。

---

## 审计覆盖

逐行阅读：

- 构建：`build.gradle.kts`（root + app）、`settings.gradle.kts`、`gradle.properties`、`libs.versions.toml`、`.env.example`、`AndroidManifest.xml`、`proguard-rules.pro`、`res/xml/*`
- 数据层：`AppDatabase.kt`、`Daos.kt`、`Entities.kt`、`CryptoManager.kt`、`WorkLogRepository.kt`、`MarkdownFileManager.kt`、`DiffUtils.kt`、`AutoSummaryScheduler.kt`
- AI 层：`GeminiRepository.kt`、`AISafetyManager.kt`
- UI 层：`MainActivity.kt`、`MainScreen.kt`、`Screen.kt`、6 个 Screen、`MarkdownText.kt`、`CustomCalendarView.kt`、`Theme.kt`、`Type.kt`
- 测试：`app/src/test/*`

未覆盖：`Color.kt`（仅色值常量）、图片资源、`.idea/`。

## 未执行的验证

**本审计从未编译或运行过代码**（仓库无 Gradle wrapper）。以下结论尤其需要实机复现：

- **[N-5](02-P1-功能与安全.md) 存量 Key 被 Base64 解码损坏** —— 是否触发取决于具体 key 是否恰好构成合法 Base64，需用真实 key 验证
- **[P1-15](02-P1-功能与安全.md) 迁移正确性** —— CREATE TABLE 语句已与实体逐列比对一致，但需在真实 v5 数据库上执行一次
- **[P2-4](03-P2-健壮性与体验.md) 日历跨月**、**[N-8](03-P2-健壮性与体验.md) 进度标志竞态** —— 均依赖运行时时序
