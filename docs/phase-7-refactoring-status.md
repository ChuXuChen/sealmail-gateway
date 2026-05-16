# SealMail Gateway 阶段 7 重构状态

本文档记录按照 `docs/epic-refactoring-plan.md` 中阶段 7「配置与数据职责治理」进行严格审视后的当前状态、已完成边界和仍需处理的问题。

当前判断：阶段 7 已基本完成主干功能，但严格验收尚未通过。主要阻塞是已提交 migration 被直接修改；其余问题属于配置治理和管理能力补强。

## 1. 阶段 7 验收目标

阶段 7 目标是：

- YAML 只管理部署级配置。
- PostgreSQL 管理运行时业务配置。
- 管理 API 可修改运行时配置。
- 关键配置变更进入审计。
- 不修改历史 migration。

阶段范围包括：

- 域名策略。
- 证书绑定。
- DLP 规则。
- 邮件认证策略。
- Relay 策略。
- 隔离策略。
- 用户、角色、权限。
- Secret 引用。

## 2. 当前已完成内容

当前实现已经覆盖阶段 7 的主要功能面：

- `application.yml` 基本收敛为部署级配置，JWT 改为 `secret-ref`，SMTP TLS 密码改为 secret ref，Relay 只保留 fallback。
- Relay 策略已进入数据库，`RelayPolicyService` 负责读写策略，邮件发送路径实际读取数据库策略。
- 隔离策略已进入数据库，`releaseRequiresEncryption` 影响隔离释放，`maxRetentionDays` 接入定时清理，`notificationEnabled` 接入隔离创建通知入口。
- 邮件认证策略已通过数据库和 CRUD 管理，DKIM 私钥使用 path 或 secret ref，不再通过运行时 API 写入 PEM。
- 证书绑定新增领域模型、仓储、API、前端入口，并被证书选择路径优先使用。
- DLP 规则、域名策略、用户账号等已有数据库和管理入口。
- Mail auth、Relay、隔离策略、证书绑定等关键配置变更均有审计事件。

已验证命令：

```bash
mvn -pl sealmail-backend/sealmail-web,sealmail-backend/sealmail-infra -am test
npm run build
```

最近一次结果：

- 后端 Maven reactor build 成功。
- 前端 TypeScript 与 Vite build 成功。

## 3. 阻塞验收的问题

### 3.1 已提交 migration 被直接修改

严重级别：阻塞。

阶段 7 验收标准明确要求“不修改历史 migration”。当前 `V12__runtime_policy_tables_and_secret_refs.sql` 已经存在于提交 `4f8095b RefactoringV7`，但工作区又直接修改了该文件：

- 删除了原先清空并 drop `dkim_private_key_pem` 的语句。
- 新增了 `certificate_binding` 表和索引。

风险：

- 如果任何环境已经执行过旧版 V12，Flyway 会产生 checksum mismatch。
- 新环境和旧环境的 schema 演进路径不一致。
- 后续排查会无法判断 V12 的真实历史语义。

建议处理：

1. 不继续修改已提交的 V12。
2. 保留 V12 已发布版本。
3. 用新的 V13 migration 承载证书绑定表、索引，以及 DKIM PEM 后续清理或保留策略。
4. 如果旧 V12 的破坏性逻辑尚未进入任何共享环境，可以在提交前重写历史；否则必须走新增 migration。

### 3.2 DKIM PEM 旧列仍存在于历史 schema

严重级别：中。

当前代码已经不再通过运行时 API 使用 `dkim_private_key_pem`，但历史 migration 仍会在 fresh database 上创建该列：

- `V1__create_core_schema.sql`
- `V8__align_schema_with_current_entities.sql`

当前 V12 改为保留旧 PEM 值，并提示运维后续迁移。这避免了直接丢失私钥，是正确方向；但严格看“避免明文存储入口扩散”，数据库中仍存在旧明文字段入口。

建议处理：

1. 明确过渡策略：保留列但应用层完全不读不写，还是新增 migration 将旧 PEM 迁移到外部 secret 后 drop 列。
2. 如果要 drop，应使用 V13 或更高版本 migration，不回改 V1/V8/V12。
3. 增加一次 schema 或 repository 级测试，确保应用层 DTO、Entity、Service 不再暴露 PEM 字段。

## 4. 仍需补强的问题

### 4.1 隔离通知目前只是日志适配器

严重级别：低到中。

`notificationEnabled` 已经被 `QuarantineStep` 消费，但当前实现是 `LoggingQuarantineNotificationAdapter`，只记录日志，不发送真实通知。

如果阶段 7 只要求“策略配置进入数据库并能影响流程”，当前可以接受。如果要求“隔离通知业务闭环”，则仍需实现实际通知通道。

建议处理：

1. 明确通知策略的阶段边界。
2. 若阶段 7 要闭环，新增邮件、Webhook 或系统通知适配器。
3. 保留日志适配器作为 fallback 或开发环境实现。

### 4.2 用户、角色、权限管理能力不完整

严重级别：中。

用户账号已在数据库中，并有查询、启用、禁用、解锁、自助改密等能力。但当前用户管理 API 仍缺少完整 CRUD：

- 没有创建用户接口。
- 没有修改用户角色接口。
- 没有修改 managed domains 或权限范围接口。
- 角色和权限仍主要是字符串集合和代码检查，没有独立角色/权限模型。

如果阶段 7 的“用户、角色、权限”只要求从 YAML seed 迁入数据库，则当前基本够用；如果要求运行时治理完整闭环，则仍不完整。

建议处理：

1. 增加用户创建、重置密码、角色变更、managed domains 变更 API。
2. 对角色/权限做最小模型化，至少统一枚举和校验边界。
3. 为角色和权限变更增加审计事件。

### 4.3 Secret 引用策略需要统一边界

严重级别：低到中。

当前已经引入 `SecretReferenceResolver`，支持 `env:` 和 `file:`。JWT、SMTP TLS 密码、Relay 密码、DKIM 私钥等主要敏感项已改为 secret ref 或环境变量。

仍需确认的边界：

- 数据库密码仍是 Spring datasource 标准配置，虽然默认值已为空，但不是 secret ref 体系。
- `KeyStoreService` 仍存在旧式 `sealmail.security.keystore.password` / `SEALMAIL_KEYSTORE_PASSWORD` 入口，虽然不在 `application.yml` 当前主配置中。
- 需要明确哪些部署级 secret 允许直接走环境变量，哪些必须走统一 secret ref。

建议处理：

1. 写入 `docs/security-local-material.md` 或专门 secret policy，定义部署级 secret 与运行时业务 secret 的边界。
2. 清理或改造 `KeyStoreService` 的旧密码属性入口。
3. 增加配置扫描测试，防止 `application.yml` 再出现 plaintext password 默认值。

## 5. 当前阶段结论

按功能实现判断，阶段 7 主干已经接近完成，约 85%-90%。

按 epic 验收严格判断，阶段 7 当前仍未完成。原因是“已提交 migration 被直接修改”违反硬性验收标准；该问题解决前，不应标记阶段 7 完成。

建议下一步优先级：

1. 处理 V12 历史 migration 问题，将新增 schema 演进拆到 V13。
2. 明确 DKIM PEM 旧列的过渡和最终清理策略。
3. 决定隔离通知是否在阶段 7 闭环。
4. 补齐用户、角色、权限的运行时管理边界，或在文档中明确该部分延后。
