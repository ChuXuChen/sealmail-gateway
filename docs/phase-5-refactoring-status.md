# SealMail Gateway 阶段 5 重构状态

本文档记录史诗大重构执行到阶段 5 后的当前状态、已完成边界、验证结果和后续操作建议。

当前停止点：阶段 5 已完成，尚未进入阶段 6。下一步只有在明确确认后，才应开始阶段 6「密码算法 Profile 化」。

## 1. 当前阶段状态

已完成阶段：

- 阶段 0：治理与重构基线。
- 阶段 1：核心行为锁定。
- 阶段 2：模块依赖边界治理。
- 阶段 3：强类型邮件处理上下文。
- 阶段 4：Spring Integration 编排替换旧 Pipeline 语义。
- 阶段 5：统一错误处理与隔离决策。

未开始阶段：

- 阶段 6：密码算法 Profile 化。
- 阶段 7：配置与数据职责治理。
- 阶段 8：审计与可观测性。
- 阶段 9：前端与管理 API 收敛。
- 阶段 10：旧实现删除与硬化。

## 2. 阶段 5 完成内容

阶段 5 只处理统一错误流、错误分类、隔离决策和 dead-letter 行为，未改前端，未落地密码 profile 选择策略。数据库只新增前进式 migration，用于让异常邮件记录持久化原始邮件内容。

主要迁移：

- 新增 `MailProcessingException`，使用既有 `MailProcessingErrorType` 表达 route、auth、DLP、crypto、relay、quarantine 等错误分类。
- 新增 `MailErrorDecisionHandler`，集中处理 Spring Integration error message。
- `DeadLetterHandler` 保留 dead-letter 可观测计数和最近错误列表，并委托 `MailErrorDecisionHandler` 做状态、审计和隔离决策。
- `MailPipelineFlow` 显式接入 `errorChannel`，route、step、relay、quarantine 异常都会发送 `ErrorMessage`。
- 新增 `MailProcessingTracker`，只负责 Spring Integration handler 生命周期中的处理轨迹记录和终态写入。
- 业务步骤直接返回 `Message<byte[]>`，隔离决策写入 `mailProcessingContext`，异常抛出 `MailProcessingException` 后进入统一错误流。
- DLP、auth、decrypt、verify、sign、encrypt、dkimSign、relay、quarantine step 的异常改为抛出分类异常，不再自行吞异常或伪造普通 failure result。
- Quarantine 持久化异常进入 dead-letter，不再重新投递到 quarantine channel，避免错误循环。
- 错误流生成的隔离上下文 detail 包含 `processingId`、`errorType` 和错误详情。
- `exception_mail` 通过 `V11__add_exception_mail_raw_content.sql` 增加 `raw_content`，使异常隔离记录与 DLP 隔离记录一样保留原始邮件内容。

## 3. 当前兼容边界

旧 Pipeline 生产抽象已清除：

- 删除 `MailPipelineStep`。
- 删除 `PipelineResult`。
- 删除 `PipelineStepTracker`。

当前保留的邮件编排表面：

- `MailPipelineFlow` 作为 Spring Integration flow 定义。
- `MailProcessingTracker` 作为处理轨迹记录组件，不承载流程分支语义。
- `MailProcessingMessages` 作为构造 message/context 的小工具。

当前约束：

- 新增邮件处理异常必须抛出 `MailProcessingException` 或让 flow 包装后发送到 `errorChannel`。
- 主邮件流不得重新把异常转换成成功 payload 或普通 relay payload。
- Quarantine 持久化失败不得重新进入 quarantine channel。
- 生产代码不得恢复 `MailPipelineStep`、`PipelineResult` 或 `PipelineStepTracker`。
- 密码算法 profile 选择、业务配置数据化、前端/API 收敛不属于阶段 5。

## 3.1 阶段 5.5 Pipeline 小清理

阶段 5 通过后追加了一个窄范围小清理，用于移除确定无引用或会绕过统一错误流的旧 pipeline 表面：

- 删除旧 `SMIMEProcessor` 组件，避免阶段 6 同时维护两套 S/MIME 编排语义。
- 删除 `MailPipelineFlow.relayFlow(MessageChannel)` 旧重载，保留带 `errorChannel` 和 `quarantineChannel` 的 relay flow。
- 删除 `MailPipelineStep` / `PipelineResult` / `PipelineStepTracker` 旧生产抽象。
- 删除 `PipelineStepTracker.wrap` / `wrapMessage` 未使用包装 API所在的旧 tracker。
- 删除 `MailPipelineStep.isEnabled` 默认方法所在的旧 step 接口。
- 删除 `PipelineResult.successWithHeader` 和 `PipelineResult.SUCCESS` 未使用 API所在的旧 result 模型。
- 新增 `quarantineReleaseChannel` 和专用 Spring Integration release flow。
- `QuarantineMailReleaseRelayImpl` 收敛为端口适配器，只负责把 DLP 隔离邮件封装成 `Message<byte[]>` 投递到 `quarantineReleaseChannel`。
- DLP 隔离释放路径不再在适配器内手写 route、sign、encrypt、dkimSign、relay 或 exception-mail 记录。
- release flow 负责 `route -> sign/encrypt/dkimSign/relay` 或 inbound direct relay，并在释放过程中再次触发隔离决策时进入 `errorChannel`、标记处理失败且向调用方传播失败。
- DLP 隔离队列分页查询只返回 `QUARANTINED` 状态邮件；已放行或已拒绝邮件不再混入待处理列表。

旧 `PipelineResult.failure("Pipeline step returned no result")` fallback 已随 `PipelineStepTracker` 删除。原生 handler 返回 `null` 时由 `MailProcessingTracker` 转换为 `MailProcessingException(PIPELINE)`。

## 4. 新增或补强测试

代表性测试：

- `MailPipelineFlowCharacterizationTest`
- `DeadLetterHandlerTest`
- `DlpStepTest`
- `MailProcessingTrackerTest`

覆盖重点：

- outbound step 异常进入 `errorChannel`，并短路后续签名、加密和 relay。
- inbound route 异常进入 `errorChannel`，并短路后续认证、解密、验签和 DLP。
- 错误决策器将错误分类写入隔离 detail。
- 错误决策器更新 `MailProcessing` 终态。
- Quarantine 持久化失败保留为 dead-letter，不再重入 quarantine channel。
- DLP scanner 异常抛出 `MailProcessingException(DLP)`，由统一错误流处理。
- Exception mail 持久化保留 raw content。
- DLP 隔离释放通过 `quarantineReleaseChannel` 进入 release flow，适配器不持有 pipeline step。
- DLP 隔离释放失败时不会把原隔离记录标记为 `RELEASED`。
- DLP 隔离列表只展示待处理的 `QUARANTINED` 记录，并支持在该状态内按 reason 过滤。
- `MailPipelineFlow` 源码不再包含 `PipelineResult`、`PipelineStepTracker`、`MailPipelineStep` 或 `executeMessageWithTracking`。

## 5. 当前验证结果

已通过的阶段 5 定向验证命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am -Dtest=MailPipelineFlowCharacterizationTest,DeadLetterHandlerTest,MailProcessingTrackerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl sealmail-backend/sealmail-infra -am -Dtest=DeadLetterHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl sealmail-backend/sealmail-infra -am -Dtest=QuarantineMailReleaseRelayImplTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl sealmail-backend/sealmail-infra -am -Dtest=MailPipelineFlowCharacterizationTest,QuarantineMailReleaseRelayImplTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl sealmail-backend/sealmail-app -am -Dtest=QueryQuarantineUseCaseTest,ReleaseQuarantineUseCaseTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl sealmail-backend/sealmail-infra -am -Dtest=MailPipelineFlowCharacterizationTest,DeadLetterHandlerTest,MailProcessingTrackerTest,QuarantineMailReleaseRelayImplTest,DlpStepTest,EncryptStepTest,RelayStepTest,DecryptStepTest,VerifyStepTest,MailAuthenticationStepTest,QuarantineStepTest -Dsurefire.failIfNoSpecifiedTests=false test
```

阶段最终验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
```

最近一次结果：

- `sealmail-domain`：9 tests，全部通过。
- `sealmail-infra`：111 tests，全部通过。

已知非失败警告：

- Mockito dynamic agent warning。
- 现有 BouncyCastle deprecated API 编译提示。
- 现有 Surefire/native output warning。
- 现有 DLP MIME content extractor 预期 warning。
- 现有 crypto mismatch 预期错误日志。
- 错误流测试中故意触发的 dead-letter error 日志。

## 6. 后续操作建议

进入阶段 6 前建议确认：

1. 阶段 5 作为独立提交或一组可回滚提交落盘。
2. 不将配置数据化、前端/API 收敛或旧 Pipeline 删除混入阶段 6 的首个提交。
3. 阶段 6 应从 `CryptoProfile` 到具体 S/MIME suite、签名算法、加密算法的映射开始。
4. 阶段 6 不应改变阶段 5 的统一错误流入口；crypto profile 选择失败应继续抛出分类错误并进入 `errorChannel`。
