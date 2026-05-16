# SealMail Gateway 阶段 3 重构状态

本文档记录史诗大重构执行到阶段 3 后的当前状态、已完成边界、验证结果和后续操作建议。

当前停止点：阶段 3 已完成，尚未进入阶段 4。下一步只有在明确确认后，才应开始阶段 4「Spring Integration 编排替换旧 Pipeline 语义」。

## 1. 当前阶段状态

已完成阶段：

- 阶段 0：治理与重构基线。
- 阶段 1：核心行为锁定。
- 阶段 2：模块依赖边界治理。
- 阶段 3：强类型邮件处理上下文。

未开始阶段：

- 阶段 4：Spring Integration 编排替换旧 Pipeline 语义。
- 阶段 5：统一错误处理与隔离决策。
- 阶段 6：密码算法 Profile 化。
- 阶段 7：配置与数据职责治理。
- 阶段 8：审计与可观测性。
- 阶段 9：前端与管理 API 收敛。
- 阶段 10：旧实现删除与硬化。

## 2. 阶段 3 完成内容

阶段 3 只处理强类型邮件处理上下文，未恢复或重建顶层 `sealmail-app`，未删除旧 Pipeline，未替换 Spring Integration 主链路语义，未重构统一错误流，未修改数据库 migration，未改前端。

新增 domain 强类型模型：

- `MailProcessingContext`
- `MailProcessingDecision`
- `MailProcessingErrorType`
- `MailRecordDisposition`
- `CryptoProfile`
- `CertificateSelection`
- `RelayProfile`
- `DlpDecision`
- `AuditTrace`

新增 infra 边界组件：

- `MailProcessingHeaders`：只保留 `CONTEXT = "mailProcessingContext"`。

已删除中间层：

- `MailProcessingHeaderAdapter`：不再保留。生产代码直接读取 `mailProcessingContext` 中的 `MailProcessingContext`。

主要迁移：

- 邮件入口 `OutboundMailGateway`、`SealMailSmtpServer` 和隔离释放入口直接创建初始 context。
- `RoutingService` 只读取 context 中的 envelope，并只输出更新后的 `mailProcessingContext`。
- `DlpStep`、`DecryptStep`、`VerifyStep`、`SignStep`、`EncryptStep`、`DkimSignStep`、`RelayStep`、`QuarantineStep`、`PipelineStepTracker` 直接读取 context 获取邮件处理状态。
- S/MIME 加密状态、relay profile、隔离原因、处理 ID、证书选择、原始邮件内容和隔离释放 ID 均归入 `MailProcessingContext`。
- `MailRecordDisposition` 从 infra 移入 domain。
- `DeadLetterHandler` 通过 context 读取原始 payload，并用失败 context 进入隔离流。

## 3. 已废弃内容

阶段 3 明确废弃旧业务 header，不保留 fallback：

- `mailEnvelope`
- `processingId`
- `mailDirection`
- `signingEnabled`
- `encryptionEnabled`
- `mustEncrypt`
- `preferredAlgorithm`
- `senderCertificate`
- `recipientCertificate`
- `recipientCertificates`
- `relayHost` / `relayPort` / `relayUseTls` / `relayUsername` / `relayPassword` / `relayTimeout` / `relayEnvelopeFrom`
- `quarantineRequired` / `quarantineReason` / `quarantineDetail`
- `mailRecordDisposition`
- `submissionType`
- `remoteAddress`
- `originalMessage` / `originalMailContent`

当前唯一允许承载邮件处理业务状态的 Spring Integration header 是 `mailProcessingContext`。

仍保留但不属于本阶段替换范围：

- `MailPipelineFlow`
- `MailPipelineStep`
- `PipelineResult`
- `PipelineStepTracker`

原因：

- 阶段 3 的目标是统一业务状态载体，不改变旧 flow 的分支编排语义。
- 删除旧 Pipeline 和替换 `PipelineResult` 属于阶段 4。
- 统一错误流和 dead-letter 决策属于阶段 5。
- 密码 profile 的算法选择落地属于阶段 6；本阶段的 `CryptoProfile` 只作为上下文元数据。

## 4. 新增或补强测试

新增或补强的代表性测试：

- `RoutingServiceTest`
- `DeadLetterHandlerTest`
- `MailPipelineFlowCharacterizationTest`
- `EncryptStepTest`
- `RelayStepTest`
- `QuarantineMailReleaseRelayImplTest`

覆盖重点：

- 不存在旧业务 header fallback。
- 不存在 `MailProcessingHeaderAdapter` 中间层。
- 路由和各 pipeline step 只通过 context 获取业务状态。
- 阶段 1 characterization tests 继续通过。

## 5. 当前验证结果

已通过的阶段 3 验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
```

最近一次结果：

- `sealmail-domain`：9 tests，全部通过。
- `sealmail-infra`：109 tests，全部通过。

已知非失败警告：

- Mockito dynamic agent warning。
- Surefire/native output warning。
- 现有 DLP MIME content extractor 预期 warning。
- 现有 crypto mismatch 预期错误日志。

## 6. 后续操作建议

进入阶段 4 前建议先确认：

1. 阶段 3 作为独立提交或一组可回滚提交落盘。
2. 不修改历史 Flyway migration。
3. 不将前端/API 收敛、统一错误流或密码 profile 选择混入阶段 4 的首个提交。
4. 以阶段 1 characterization tests 为保护，逐步把旧 `PipelineResult` 主链路替换为 Spring Integration handler/router/transformer 语义。

阶段 4 的推荐入口：

- 先迁 outbound happy path。
- 再迁 outbound quarantine path。
- 再迁 inbound happy path。
- 再迁 inbound quarantine path。
- 最后迁隔离释放路径。
