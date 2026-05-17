# SealMail Gateway DLP 功能扩充设计文档

## 1. 文档目标

本文档定义 SealMail Gateway 的 DLP 功能扩充方向、架构边界、数据模型、接口和分阶段落地计划。目标不是把 DLP 做成完整商用套件，而是把当前“正则规则命中后隔离”的能力扩展为企业基础可交付的邮件数据防泄漏能力。

本文档与以下文档配套使用：

- `docs/enterprise-feature-expansion-plan.md`
- `docs/post-epic-engineering-principles.md`
- `docs/security-local-material.md`
- `docs/frontend-revision-plan.md`

## 2. 当前基线

从当前代码看，DLP 已具备以下基础：

- 规则配置：`dlp_pattern` 支持名称、说明、正则、动作、严重级别、优先级和启用状态。
- 作用范围：`dlp_selection` 支持全局、发件域、收件域，并可绑定全部规则或指定规则。
- 扫描 SPI：`DlpContentScanner` 提供扫描器扩展点。
- 扫描编排：`DlpService` 聚合多个 scanner，并按动作优先级得到最终动作。
- 邮件链路：`DlpStep` 已接入 inbound/outbound Spring Integration flow。
- 动作模型：`WARN`、`MUST_ENCRYPT`、`QUARANTINE`、`BLOCK` 已存在。
- 隔离处置：`DlpQuarantineController`、`ReleaseQuarantineUseCase`、`RejectQuarantineUseCase` 已支持查询、放行、加密放行、拒绝、释放修复和批量操作。
- 前端页面：已有 DLP 规则、DLP 生效范围和 DLP 隔离邮件页面。
- 审计基础：DLP 命中、隔离和释放已有审计接入。

当前主要短板：

- 内容抽取偏粗糙，正文、HTML、附件、编码和压缩包处理能力不足。
- 命中结果缺少结构化证据，隔离详情主要是字符串摘要。
- 规则模型只覆盖正则，缺少字典、内置分类、组合条件、计数阈值和例外条件。
- 策略模型粒度不足，缺少方向、用户/地址、附件类型、文件大小、时间窗口、规则组等维度。
- 扫描失败、大附件、慢规则和异常 MIME 的资源保护还不够完整。
- 前端缺少规则测试、命中解释、证据查看、策略仿真和运营统计。

## 3. 产品目标

DLP 扩充后应覆盖三类企业使用场景：

1. 敏感内容识别：识别正文、HTML 和常见附件中的身份证号、手机号、银行卡号、密钥、口令、内部项目代号、客户资料、财务信息等。
2. 投递策略控制：根据命中风险执行告警、强制 S/MIME 加密、进入 DLP 隔离队列或直接阻断。
3. 安全运营闭环：管理员能看到命中原因、脱敏证据、处置历史、规则来源和可解释结果，并能持续调优规则。

目标能力：

- 可配置的规则库、规则组和策略集。
- 可扩展的内容抽取和扫描器 SPI。
- 结构化命中事件和脱敏证据。
- 有资源上限的附件扫描。
- 隔离工作台具备复核、放行、拒绝和审计追溯能力。
- 规则测试工具支持管理员在保存前验证误报和漏报。
- 所有行为进入统一审计、错误分类和处理轨迹。

非目标：

- 不做 AI/ML 自适应 DLP。
- 不做终端侧文件管控。
- 不做完整邮件归档系统。
- 不做跨租户集中策略控制台。
- 不做 Office 文档 DRM。
- 不在 DLP 命中证据中保存完整敏感正文。

## 4. 设计原则

- 先提升确定性能力，再考虑高级分类。正则、字典、校验算法、附件抽取和策略编排优先。
- 扫描器可以失败，但邮件主链路不能被无限拖慢。所有扫描必须有大小、时间、深度和异常边界。
- 命中证据必须脱敏，默认只保存最小必要上下文。
- DLP 策略是业务策略，不写死在 pipeline step、YAML 或 scanner 中。
- pipeline 只做编排，扫描算法、内容抽取、策略选择、证据存储通过端口和服务解耦。
- 运行时配置进入 PostgreSQL，只通过新增 Flyway migration 演进 schema。
- 兼容现有规则和隔离队列，避免一次性破坏现有页面和 API。

## 5. 目标架构

建议把 DLP 拆成六个明确子域：

```text
邮件链路
  -> 内容抽取 Content Extraction
  -> 策略解析 Policy Resolution
  -> 检测引擎 Detection Engine
  -> 处置决策 Disposition Decision
  -> 事件与证据 Event Evidence
  -> 隔离与运营 Quarantine Operations
```

推荐后端职责边界：

- `domain`
  - 定义 DLP 领域模型：规则、规则组、策略、命中、证据、扫描事件、最终动作。
  - 定义端口：规则配置端口、扫描事件端口、内容抽取端口、扫描器 SPI。
- `app`
  - 管理 DLP 规则、策略、规则组、测试规则、查询事件、查询证据。
  - 编排权限、事务、DTO 映射和审计。
- `infra`
  - 实现 JPA 存储、MIME/附件解析、PDF/文本抽取、正则/字典扫描器。
  - 实现扫描资源限制、缓存和指标采集。
- `web`
  - 暴露 DLP 管理 API、规则测试 API、事件查询 API。
- `boot`
  - 装配 scanner、抽取器、默认配置和定时清理任务。

## 6. 能力扩充方案

### 6.1 内容抽取

当前 `MimeContentExtractor` 只抽取基础主题和正文，并对 multipart 做简单递归。扩充后建议引入结构化抽取结果：

```text
DlpContentBundle
  messageId
  subjectPart
  bodyParts[]
  attachmentParts[]
  headers[]
  extractionWarnings[]
  totalBytes
  truncated
```

每个 part 应包含：

- `partId`：稳定定位，例如 `body:plain:0`、`attachment:2`。
- `kind`：SUBJECT、BODY_TEXT、BODY_HTML、HEADER、ATTACHMENT_TEXT、ATTACHMENT_PDF、ATTACHMENT_ARCHIVE_ENTRY。
- `fileName`：附件文件名，正文为空。
- `contentType`：MIME 类型。
- `charset`：解析得到的字符集。
- `text`：提取后的文本，可能截断。
- `rawSizeBytes`：原始大小。
- `textSizeChars`：提取文本长度。
- `truncated`：是否因限制被截断。
- `extractable`：是否成功提取。
- `warningCode`：例如 `UNSUPPORTED_TYPE`、`TOO_LARGE`、`CORRUPT_ATTACHMENT`。

第一批支持范围：

- 主题、From、To、Cc、Reply-To 等头部中的可见文本。
- `text/plain` 正文。
- `text/html` 转纯文本。
- PDF 文本抽取。
- `.txt`、`.csv`、`.json`、`.xml`、`.log` 等文本附件。
- `.zip` 内文本文件，限制解压层级和总展开大小。

暂缓范围：

- Office 文档深度解析可以作为第二批能力。
- 图片 OCR 不纳入企业基础版。
- 加密压缩包只记录无法扫描，不尝试破解。

### 6.2 检测引擎

当前检测以配置正则为主。扩充后建议保留 `DlpContentScanner` SPI，但把输入从 `subject/body/rawContent` 升级为 `DlpScanRequest`：

```text
DlpScanRequest
  envelope
  direction
  contentBundle
  effectivePolicy
  scanLimits
  processingId
```

扫描器类型：

- 正则扫描器：兼容现有 `dlp_pattern.regex`。
- 字典扫描器：支持关键词集合、大小写敏感、整词匹配、中文分词简化匹配。
- 校验型扫描器：身份证、银行卡、统一社会信用代码等带校验位的数据。
- 密钥材料扫描器：PEM 私钥、API token、JWT、常见云密钥格式。
- 组合规则扫描器：多个条件同时满足才触发，例如“客户 + 金额 + 附件”。

扫描结果应从单个 `DlpViolation` 扩展为更完整的结构：

```text
DlpMatch
  ruleId
  ruleName
  ruleType
  severity
  action
  confidence
  partId
  partKind
  fileName
  matchCount
  evidence[]
```

动作优先级保持：

```text
BLOCK > QUARANTINE > MUST_ENCRYPT > WARN
```

当动作相同，按严重级别、规则优先级、规则名称排序，以保证结果稳定。

### 6.3 规则库

建议把当前 `dlp_pattern` 演进为更通用的规则模型，保持旧字段兼容：

- `id`
- `name`
- `description`
- `type`：REGEX、KEYWORD、DICTIONARY、BUILTIN、COMPOSITE
- `pattern`：正则或关键字表达式。
- `dictionary_id`：字典规则引用。
- `builtin_code`：内置规则编码，例如 `CN_ID_CARD`、`BANK_CARD_LUHN`。
- `severity`
- `default_action`
- `priority`
- `enabled`
- `content_kinds`：适用 subject/body/header/attachment。
- `file_name_pattern`：可选附件名过滤。
- `min_match_count`
- `max_evidence_count`
- `masking_strategy`
- `created_at`
- `updated_at`
- `version`

规则组用于把规则从策略中解耦：

```text
dlp_rule_group
  id
  name
  description
  enabled

dlp_rule_group_item
  group_id
  rule_id
  override_action
  override_severity
  priority
```

规则库建议内置但可关闭：

- 身份证号。
- 手机号。
- 银行卡号。
- 邮箱地址。
- 内网 IP、公网 IP。
- PEM 私钥。
- 密码关键字。
- 常见 token 形态。
- 企业自定义关键词字典。

内置规则不应通过 migration 写入大量生产数据。更稳妥的方式是应用启动时提供只读模板，管理员选择“导入为本地规则”后落库。

### 6.4 策略模型

当前 `dlp_selection` 只有作用范围和规则选择。扩充后建议引入策略集：

```text
dlp_policy
  id
  name
  description
  enabled
  priority
  mode              -- MONITOR, ENFORCE
  direction         -- INBOUND, OUTBOUND, BOTH
  sender_domain
  recipient_domain
  sender_pattern
  recipient_pattern
  min_severity
  default_action
  stop_on_first_block
  created_at
  updated_at

dlp_policy_rule_group
  policy_id
  rule_group_id
  priority
```

策略匹配顺序：

1. 只选择 `enabled=true` 的策略。
2. 按方向过滤。
3. 按发件域、收件域、发件地址、收件地址过滤。
4. 按附件条件过滤。
5. 按 `priority ASC` 排序。
6. 多个策略命中时合并规则组，动作取最高优先级。

策略模式：

- `MONITOR`：只记录命中和证据，最终动作不超过 WARN。
- `ENFORCE`：按规则动作执行。

这样可以支持企业先观察误报，再逐步切换到强制处置。

### 6.5 命中证据

命中证据是扩充的关键点。建议新增结构化事件表，而不是继续把规则摘要写进 `detail` 字符串。

```text
dlp_scan_event
  id
  processing_id
  message_id
  direction
  sender_email
  recipients
  remote_address
  final_action
  max_severity
  policy_ids
  rule_ids
  match_count
  scan_duration_ms
  content_truncated
  extraction_warnings
  quarantine_id
  created_at

dlp_scan_evidence
  id
  event_id
  rule_id
  rule_name
  severity
  action
  part_id
  part_kind
  file_name
  content_type
  masked_snippet
  match_hash
  start_offset
  end_offset
  created_at
```

证据保存规则：

- 不保存完整原文。
- `masked_snippet` 默认限制 128 到 256 字符。
- `match_hash` 使用带系统盐的 HMAC 或不可逆摘要，用于去重和统计，不能还原原文。
- 对身份证、银行卡、手机号、密钥等使用专用脱敏策略。
- 每条规则每个 part 默认最多保存 3 条证据。
- 每封邮件默认最多保存 50 条证据。
- 原始邮件仍只在隔离队列中保存，并受隔离保留策略控制。

### 6.6 处置决策

最终动作与现有模型兼容：

- `WARN`：投递继续，记录 DLP 事件和审计。
- `MUST_ENCRYPT`：更新 `MailProcessingDecision.mustEncrypt`，后续加密 step 强制执行。
- `QUARANTINE`：进入 DLP 隔离队列，允许管理员复核后放行或加密放行。
- `BLOCK`：进入异常邮件或阻断队列，不应默认显示为可放行的 DLP 隔离项。

需要明确 `BLOCK` 与 `QUARANTINE` 的产品语义：

- `QUARANTINE` 是人工复核队列，默认可以放行。
- `BLOCK` 是高风险阻断，默认不可直接放行，只有管理员带强制理由才能重新投递，或者继续放入 `exception_mail`。

当前 `DlpStep` 对 `BLOCK` 使用 `MailRecordDisposition.EXCEPTION`，对 `QUARANTINE` 使用 `DLP_QUARANTINE`。建议保持该行为，并在 UI 中明确区分“DLP 隔离”和“DLP 阻断”。

### 6.7 隔离工作台

现有隔离工作台应增强为“复核 + 解释 + 处置”页面：

列表字段：

- 主题、发件人、收件人、方向。
- 最终动作。
- 最高严重级别。
- 命中规则数。
- 命中附件数。
- 策略名称。
- 状态。
- 创建时间。
- 释放可用性。

详情抽屉：

- 邮件基础信息。
- 命中摘要。
- 脱敏证据列表。
- 附件命中分布。
- 策略和规则来源。
- 抽取警告。
- 处理时间线。
- 审计关联。

处置动作：

- 直接放行。
- 加密后放行。
- 拒绝。
- 标记误报并建议关闭或调低规则。
- 复制脱敏证据。
- 查看同类命中事件。

需要避免在 UI 中展示完整敏感内容。即使管理员有权限，也默认只展示脱敏片段。

### 6.8 规则测试与策略仿真

新增规则测试 API：

```text
POST /api/v1/dlp/test
  subject
  body
  headers
  attachments[]
  sender
  recipients
  direction
  ruleIds?
  policyIds?
```

返回：

- 最终动作。
- 命中规则。
- 脱敏证据。
- 命中 part。
- 策略匹配过程。
- 抽取警告。
- 扫描耗时。

用途：

- 管理员保存规则前验证。
- 调整策略时评估误报。
- 排查为什么一封邮件被隔离。

测试接口必须限制上传大小，并且不落库原始测试内容，除非后续明确加入“测试样例库”。

### 6.9 运营统计

Dashboard 和 DLP 页面建议展示：

- 今日 DLP 命中数。
- 待复核隔离数。
- 按动作分布。
- 按规则 Top N。
- 按发件域/收件域 Top N。
- 平均扫描耗时和 P95 扫描耗时。
- 抽取失败/截断次数。
- 误报标记次数。

统计数据优先来自 `dlp_scan_event`，避免扫描隔离原文。

## 7. 邮件链路集成

目标链路保持现有结构：

```text
inbound:
route -> mail-auth -> decrypt -> verify -> dlp -> relay/quarantine

outbound:
route -> dlp -> sign -> encrypt -> dkim-sign -> relay/quarantine

quarantineRelease:
route -> optional sign/encrypt/dkim-sign -> relay
```

建议调整：

1. `DlpStep` 不再直接拼接字符串证据，而是调用 `DlpEvaluationService.evaluate(...)`。
2. `DlpEvaluationService` 完成内容抽取、策略解析、扫描、事件保存、审计摘要生成。
3. `DlpStep` 只根据 `DlpEvaluationResult.finalAction` 更新 `MailProcessingContext`。
4. `QuarantineStep` 保存隔离邮件时关联 `dlp_scan_event.quarantine_id`。
5. 释放隔离邮件时默认不重新执行 DLP，避免同一策略导致无法释放；但可以在“策略已变更”时提供手动复扫按钮。

## 8. 数据库演进建议

不要修改已有 migration。建议新增 migration，分批演进：

第一批：

- `dlp_scan_event`
- `dlp_scan_evidence`
- `dlp_pattern` 增加兼容字段：`type`、`content_kinds`、`min_match_count`、`masking_strategy`
- `dlp_quarantine_mail` 增加 `dlp_event_id`

第二批：

- `dlp_rule_group`
- `dlp_rule_group_item`
- `dlp_policy`
- `dlp_policy_rule_group`

第三批：

- `dlp_dictionary`
- `dlp_dictionary_entry`
- 统计索引和保留策略字段。

关键索引：

- `dlp_scan_event(processing_id)`
- `dlp_scan_event(message_id)`
- `dlp_scan_event(created_at DESC)`
- `dlp_scan_event(final_action, max_severity)`
- `dlp_scan_evidence(event_id)`
- `dlp_scan_evidence(rule_id)`
- `dlp_quarantine_mail(dlp_event_id)`

## 9. API 扩充建议

规则：

- `GET /api/v1/dlp/rules`
- `POST /api/v1/dlp/rules`
- `PUT /api/v1/dlp/rules/{id}`
- `DELETE /api/v1/dlp/rules/{id}`
- 旧 `/patterns` 保留一段时间，内部映射到规则模型。

规则组：

- `GET /api/v1/dlp/rule-groups`
- `POST /api/v1/dlp/rule-groups`
- `PUT /api/v1/dlp/rule-groups/{id}`
- `POST /api/v1/dlp/rule-groups/{id}/rules`

策略：

- `GET /api/v1/dlp/policies`
- `POST /api/v1/dlp/policies`
- `PUT /api/v1/dlp/policies/{id}`
- `POST /api/v1/dlp/policies/{id}/enable`
- `POST /api/v1/dlp/policies/{id}/disable`

测试：

- `POST /api/v1/dlp/test`
- `POST /api/v1/dlp/policies/{id}/simulate`

事件：

- `GET /api/v1/dlp/events`
- `GET /api/v1/dlp/events/{id}`
- `GET /api/v1/dlp/events/{id}/evidence`

隔离增强：

- `GET /api/v1/dlp/quarantine/{id}/evidence`
- `POST /api/v1/dlp/quarantine/{id}/false-positive`
- `POST /api/v1/dlp/quarantine/{id}/rescan`

## 10. 前端扩充建议

保留现有三个入口，并升级为四类视图：

1. 规则库
   - 规则列表。
   - 规则类型。
   - 严重级别和默认动作。
   - 适用内容类型。
   - 内置规则导入。
   - 单规则测试。

2. 策略集
   - 策略优先级。
   - 方向和域名条件。
   - 规则组绑定。
   - 监控/强制模式。
   - 策略仿真。

3. 命中事件
   - 按时间、动作、严重级别、规则、域名筛选。
   - 查看脱敏证据。
   - 关联邮件处理轨迹和审计。

4. 隔离复核
   - 复用现有 DLP 隔离邮件页面。
   - 增加证据、时间线、策略来源、抽取警告和误报反馈。

UI 约束：

- 不展示完整原文。
- 脱敏证据默认折叠。
- 高风险动作使用清晰标签，但不使用夸张视觉。
- 批量操作必须二次确认。
- 规则测试输入不自动保存。

## 11. 安全与合规控制

必须落实：

- 命中证据脱敏。
- 原始邮件只在隔离表中保存，受保留策略清理。
- 规则测试内容默认不落库。
- 日志中不输出匹配原文。
- 审计详情只记录规则名、动作、严重级别、数量和事件 ID。
- 附件抽取失败不能泄露堆栈到前端。
- DLP 配置变更必须审计。
- 放行、拒绝、误报标记必须记录操作人和理由。

建议增加 hardening test：

- 禁止 `DlpViolation.matchedContent` 或证据原文直接进入普通日志。
- 禁止 `dlp_scan_evidence` 保存超过上限的片段。
- 禁止 DLP 测试 API 未鉴权访问。
- 禁止非管理员修改 DLP 规则和策略。

## 12. 性能与稳定性

默认限制建议：

- 单封邮件 DLP 总扫描时间：5 秒，可配置。
- 单附件扫描大小：10 MB，可配置。
- 单封邮件总附件扫描大小：30 MB，可配置。
- ZIP 解压层级：2 层。
- ZIP 展开总大小：50 MB。
- 单封邮件最大证据数：50。
- 单规则最大证据数：3。
- 正则执行需要防止灾难性回溯，保存规则时做风险提示，扫描时使用超时保护。

失败策略：

- 抽取失败：记录 warning，继续扫描其他 part。
- 单个 scanner 失败：记录扫描错误，默认按配置决定 `WARN` 或 `BLOCK`。生产安全默认可偏向 `QUARANTINE`。
- 总扫描超时：记录 `SCAN_TIMEOUT`，按策略进入隔离或告警。
- 数据库事件保存失败：不应静默投递高风险邮件，应进入统一错误流或异常隔离。

## 13. 分阶段路线

### 阶段 0：基线保护

目标：在扩展前保护现有行为。

交付：

- 补充当前 DLP 正则、选择范围、动作优先级、隔离/阻断路径的 characterization test。
- 明确 `BLOCK` 与 `QUARANTINE` 的 UI 和后端语义。
- 增加扫描失败路径测试。

验收：

- 现有 DLP 页面和 API 不破坏。
- 当前规则仍能命中并进入原有动作。

### 阶段 1：内容抽取与结构化事件

目标：先让 DLP 有稳定输入和可解释输出。

交付：

- 新增 `DlpContentBundle` 和 part 模型。
- 支持 HTML 转文本、文本附件和 PDF 文本抽取。
- 新增 `dlp_scan_event`、`dlp_scan_evidence`。
- DLP 命中写入结构化事件和脱敏证据。
- 隔离项关联 DLP 事件。

验收：

- 正文、HTML、文本附件和 PDF 至少各有一条测试样例。
- UI 或 API 能查看脱敏证据。
- 大附件被截断或跳过时有 warning。

### 阶段 2：规则库升级

目标：把规则从单一正则扩展为规则库。

交付：

- 规则类型：REGEX、KEYWORD、BUILTIN。
- 内置身份证、银行卡、密钥材料等规则。
- 规则保存校验和测试接口。
- 旧 `patterns` API 兼容。

验收：

- 管理员能创建正则、关键字和内置规则。
- 测试接口能返回命中解释。
- 旧规则迁移后行为一致。

### 阶段 3：策略集和规则组

目标：支持企业按方向、域名和规则组治理 DLP。

交付：

- `dlp_policy`、`dlp_rule_group`。
- 策略优先级、方向、域名条件和 MONITOR/ENFORCE 模式。
- 前端策略页面。
- 策略仿真。

验收：

- 可以配置“出站到外部域命中客户资料时隔离”。
- 可以配置“入站只监控不阻断”。
- 多策略命中时结果稳定。

### 阶段 4：隔离工作台增强

目标：让 DLP 命中可复核、可解释、可追溯。

交付：

- 隔离详情展示证据、规则、策略和时间线。
- 支持误报标记。
- 支持按严重级别、规则、动作筛选。
- Dashboard 增加 DLP 风险统计。

验收：

- 管理员能在一个页面完成复核和处置。
- 每次处置都能关联审计。
- 误报样例能用于后续规则调优。

### 阶段 5：硬化与运营

目标：达到企业基础可交付。

交付：

- 扫描资源限制配置。
- 证据保留策略。
- 定时清理扫描事件和证据。
- 性能指标和慢规则日志。
- 故障排查文档。

验收：

- 大附件、损坏附件和慢规则不会拖垮主链路。
- 敏感原文不会进入日志、审计详情和证据表。
- 后端和前端验收命令通过。

## 14. 测试计划

后端测试：

- `domain`：规则动作优先级、证据脱敏、策略匹配、最终决策。
- `infra`：MIME 抽取、HTML 转文本、PDF 抽取、ZIP 限制、正则超时。
- `app`：规则管理、策略管理、测试接口、权限检查。
- `web`：DLP API 鉴权、请求校验、错误响应。
- pipeline：inbound/outbound DLP 命中后的 WARN、MUST_ENCRYPT、QUARANTINE、BLOCK。
- 隔离释放：释放不重复触发原策略阻断，除非显式复扫。

前端测试和验证：

- 规则创建、编辑、删除。
- 策略创建、启停、优先级展示。
- 规则测试结果展示。
- 隔离证据详情展示。
- 批量放行/拒绝确认。
- `npm run lint` 和 `npm run build`。

推荐验收命令：

```bash
cd sealmail-backend
mvn -pl sealmail-domain -am test
mvn -pl sealmail-infra -am test
mvn -pl sealmail-app -am test
mvn -pl sealmail-web -am test

cd ../sealmail-frontend
npm run lint
npm run build
```

## 15. 风险与控制

误报风险：

- 用 MONITOR 模式先观察。
- 提供规则测试和策略仿真。
- 支持误报标记和规则 Top N 统计。

性能风险：

- 限制附件大小、解压深度和扫描时间。
- 慢规则记录指标。
- 对高成本扫描器做开关。

泄密风险：

- 只保存脱敏证据。
- 禁止日志输出匹配原文。
- 原文保留仍走隔离保留策略。

兼容风险：

- 保留旧 `dlp_pattern` 和 `/patterns` API 一段时间。
- 新策略引擎默认生成等价策略，确保旧规则行为一致。
- 每阶段有 migration 和回滚说明。

运维风险：

- 提供扫描失败统计。
- 提供抽取 warning。
- 对常见附件解析失败给出可读原因。

## 16. 建议优先级

最小企业可交付优先级：

1. 结构化 DLP 事件和脱敏证据。
2. HTML、文本附件、PDF 文本抽取。
3. 规则测试 API 和 UI。
4. 内置规则和关键字规则。
5. 策略集和 MONITOR/ENFORCE。
6. 隔离详情证据展示。
7. 资源限制、保留策略和 hardening test。

不建议优先做：

- OCR。
- 机器学习分类。
- Office DRM。
- 跨租户策略中心。
- 复杂审批流。

这些能力成本高、误报治理复杂，且不影响企业基础可交付的核心闭环。

## 17. 完成定义

DLP 扩充完成后应满足：

- 管理员可以配置规则、规则组和策略。
- 邮件正文、HTML、文本附件和 PDF 能被扫描。
- 命中结果有结构化事件和脱敏证据。
- WARN、MUST_ENCRYPT、QUARANTINE、BLOCK 行为稳定可测。
- 隔离工作台能解释命中原因并完成处置。
- 规则测试和策略仿真可用于上线前调优。
- 大附件、异常附件和扫描失败不会拖垮邮件主链路。
- 敏感原文不会进入普通日志、审计详情或 DLP 证据表。
- 所有新增配置和处置动作都有审计。
- 后端相关测试、前端 lint/build 通过。
