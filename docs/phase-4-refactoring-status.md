# SealMail Gateway 阶段 4 重构状态

本文档记录史诗大重构执行到阶段 4 后的当前状态、已完成边界、验证结果和后续操作建议。

当前停止点：阶段 4 已完成，尚未进入阶段 5。下一步只有在明确确认后，才应开始阶段 5「统一错误处理与隔离决策」。

## 1. 当前阶段状态

已完成阶段：

- 阶段 0：治理与重构基线。
- 阶段 1：核心行为锁定。
- 阶段 2：模块依赖边界治理。
- 阶段 3：强类型邮件处理上下文。
- 阶段 4：Spring Integration 编排替换旧 Pipeline 语义。

未开始阶段：

- 阶段 5：统一错误处理与隔离决策。
- 阶段 6：密码算法 Profile 化。
- 阶段 7：配置与数据职责治理。
- 阶段 8：审计与可观测性。
- 阶段 9：前端与管理 API 收敛。
- 阶段 10：旧实现删除与硬化。

## 2. 阶段 4 完成内容

阶段 4 只处理 Spring Integration 编排语义，未修改数据库 migration，未改前端，未引入统一错误分类，未落地密码 profile 选择策略。

主要迁移：

- `MailPipelineFlow` 不再把 `PipelineResult` 作为 Spring Integration payload 或路由条件。
- inbound 主链路改为 `route -> auth -> decrypt -> verify -> dlp -> relay/quarantine` 的消息流。
- outbound 主链路改为 `route -> dlp -> sign -> encrypt -> dkimSign -> relay/quarantine` 的消息流。
- relay flow 先执行 relay handler，再通过 context router 分流成功完成或隔离。
- quarantine flow 直接消费 `Message<byte[]>` 和 `mailProcessingContext`，不再兼容 `PipelineResult` payload。
- `QuarantineMailReleaseRelayImpl` 的隔离释放路径改为通过 message 型 step 执行入口串联 sign/encrypt/dkimSign/relay。
- `PipelineStepTracker` 新增 `executeMessageWithTracking`，把旧 step 返回的 `PipelineResult` 消化为 `Message<byte[]>` 和更新后的 `mailProcessingContext`。
- `PipelineStepTracker` 删除全局静态步骤去重集合，不再用内存静态状态控制处理状态。

## 3. 当前兼容边界

仍保留短期兼容层：

- `MailPipelineStep`
- `PipelineResult`
- `PipelineStepTracker.executeWithTracking`

保留原因：

- 现有各 step 单元测试仍直接验证 step 的 `PipelineResult` 输出。
- 各 step 内部仍以 `PipelineResult` 表达局部成功、失败、隔离和事件返回。
- 阶段 4 的目标是先让主邮件流不再依赖 `PipelineResult` 编排；彻底删除旧 step 接口和 `PipelineResult` 属于阶段 10 或后续拆分提交。

当前约束：

- 主邮件流不得新增 `PipelineResult::success` 路由。
- 主邮件流不得把 `PipelineResult` 放入 relay 或 quarantine channel。
- 新增邮件处理入口应调用 message 型执行入口或原生 Spring Integration handler，不得重新依赖旧 Pipeline payload 语义。

## 4. 新增或补强测试

代表性测试：

- `MailPipelineFlowCharacterizationTest`
- `PipelineStepTrackerTest`
- `QuarantineMailReleaseRelayImplTest`

覆盖重点：

- outbound happy path 仍按 DLP、签名、加密、DKIM、relay 顺序执行。
- outbound DLP 隔离分支输出 `byte[]` payload 和 `mailProcessingContext`，不再输出 `PipelineResult` payload。
- routing 隔离分支短路后续 step，并通过 context 进入 quarantine channel。
- inbound happy path 仍按认证、解密、验签、DLP、relay 顺序执行。
- inbound 认证隔离分支输出 `byte[]` payload 和 `mailProcessingContext`。
- inbound 解密隔离分支会短路验签和 DLP。
- 隔离释放路径使用 message 型 step 执行入口恢复后续处理。
- `MailPipelineFlow` 源码不再包含 `PipelineResult::success` 或 `<PipelineResult` 路由。

## 5. 当前验证结果

已通过的阶段 4 定向验证命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am -Dtest=MailPipelineFlowCharacterizationTest,PipelineStepTrackerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl sealmail-backend/sealmail-infra -am -Dtest=QuarantineMailReleaseRelayImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

阶段最终验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
```

最近一次结果：

- `sealmail-domain`：9 tests，全部通过。
- `sealmail-infra`：107 tests，全部通过。

已知非失败警告：

- Mockito dynamic agent warning。
- 现有 BouncyCastle deprecated API 编译提示。
- 现有 Surefire/native output warning。
- 现有 DLP MIME content extractor 预期 warning。
- 现有 crypto mismatch 预期错误日志。

## 6. 后续操作建议

进入阶段 5 前建议确认：

1. 阶段 4 作为独立提交或一组可回滚提交落盘。
2. 不将密码算法 profile、配置数据化、前端/API 收敛混入阶段 5 的首个提交。
3. 阶段 5 应从统一错误流开始，将 step 异常、route 异常、relay 异常和 quarantine 决策纳入 Spring Integration `errorChannel` 或专用错误通道。
4. 阶段 5 可以继续保留 `PipelineResult` 兼容层，但不得让主 flow 回退到 `PipelineResult` payload 路由。
