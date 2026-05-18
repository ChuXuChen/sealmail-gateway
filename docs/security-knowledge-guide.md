# SealMail Gateway 网络安全知识体系说明

本文面向第一次接触邮件安全网关的人，解释 SealMail Gateway 配置背后的网络安全知识。它不是单纯的操作手册；操作步骤请看 `docs/configuration-guide.md`。本文重点回答：

- 为什么要这样配。
- 每个开关对应什么安全假设。
- 系统如何把 SMTP、TLS、PKI、S/MIME、DKIM、SPF、DMARC、DLP、审计和隔离组合成一个可运行的邮件安全边界。
- 配置错误通常会破坏哪条安全性质。

文中会适当使用形式化记号。形式化记号不是为了把系统变成数学论文，而是为了明确“什么条件下系统应该允许、加密、签名、隔离或拒绝邮件”。

## 1. 项目安全目标与边界

SealMail Gateway 是邮件安全网关。它处在邮件流量路径上，接收 SMTP 邮件，解析信封和内容，根据域名策略、证书、邮件认证和 DLP 策略作出处理，再交给 Postfix 或 Relay 继续投递。

项目涉及的主要安全目标是：

| 目标 | 含义 | 典型机制 |
| --- | --- | --- |
| 机密性 | 未授权方不能读邮件内容 | S/MIME 加密、TLS/TLCP、keystore、secret 引用 |
| 完整性 | 邮件内容和关键头部不应被静默篡改 | S/MIME 签名、DKIM、证书校验 |
| 身份鉴别 | 判断发件域、证书主体、管理员身份是否可信 | X.509、SPF、DKIM、DMARC、JWT、RBAC |
| 防伪造 | 降低伪造发件域和伪造邮件来源的风险 | SPF、DKIM、DMARC、Authentication-Results |
| 防泄漏 | 敏感数据不能被随意外发 | DLP、EDM、文档指纹、UBA、隔离 |
| 可追溯 | 关键安全操作可以事后追查 | 审计日志、处理步骤、隔离记录 |
| 最小暴露 | 私钥、密码、token 不进入仓库和普通配置 | `env:`、`file:`、secret manager、仓库外 keystore |

需要先明确系统边界。SealMail Gateway 不是完整邮件系统，它不负责：

- 作为公网 MX 的所有反垃圾策略。
- 长期邮件归档。
- 终端侧文件管控。
- 替代 DNS 权威服务器。
- 替代 CA 的全流程合规治理。

它真正控制的是“邮件经过本网关时”的安全处理。

## 2. 邮件网关链路

### 2.1 标准链路

项目默认使用 Postfix 做 SMTP 边界，SealMail 做内容过滤和安全处理：

```text
外部/内部 SMTP
  -> Postfix :25/:465/:587
  -> content_filter
  -> SealMail SMTP :10025
  -> SealMail 安全处理流水线
  -> Postfix after-filter :10026 或 outbound :10027
  -> 本地投递、远程 MX 或 smart host
```

这条链路里的关键安全边界是：

| 边界 | 安全意义 |
| --- | --- |
| Internet -> Postfix | 面对不可信网络，需要 SMTP/TLS、反滥用和连接级限制 |
| Postfix -> SealMail `10025` | 内部内容过滤链路，应只允许可信内网访问 |
| SealMail -> Postfix `10026/10027` | 处理后的回注链路，应避免再次进入同一个 content filter 造成循环 |
| SealMail -> 数据库 | 运行时策略、隔离、审计和证书元数据的持久化边界 |
| SealMail -> keystore/secret | 私钥、JWT secret、SMTP 密码等高敏材料边界 |

### 2.2 国密 Edge 链路

国密 Edge 是独立 SMTP gateway，用于 TLCP 或国密 TLS 1.3 伙伴流量：

```text
国密入站:
Partner -> Kona Edge :2525/:2465 -> Postfix internal smtpd :2530
  -> SealMail :10025 -> Postfix :10026/:10027

国密出站:
Postfix transport_maps -> Kona Edge :2526 -> Partner GM gateway
```

它的设计边界很重要：

- 标准 SMTP/TLS 不走 Edge，仍由 Postfix 处理。
- Edge 只处理约定端口和约定伙伴域。
- Edge 不本地排队、不生成退信、不改写正文。
- Edge 只通过受控 trace header 告诉后端原始客户端信息。
- `edge.admin.*` 默认绑定 `127.0.0.1`，不应暴露到公网。

如果把 Edge 管理口或内部回注端口暴露给不可信网络，攻击者可能绕过 Postfix 的边界策略，或者伪造“原始客户端 IP”相关判断。

## 3. 邮件基础概念

### 3.1 SMTP 信封与邮件头

邮件有两套“地址”：

| 名称 | 来源 | 示例 | 安全用途 |
| --- | --- | --- | --- |
| SMTP envelope sender | `MAIL FROM` | `bounce@example.com` | SPF 通常检查它所属域 |
| SMTP envelope recipients | `RCPT TO` | `alice@example.net` | 路由、投递、DLP 范围 |
| Header From | 邮件头 `From:` | `CEO <ceo@example.com>` | 用户看到的发件人，DMARC 对齐核心 |
| Header To/Cc | 邮件头 `To:`/`Cc:` | `alice@example.net` | 用户展示、DLP 抽取 |

安全判断不能只看用户看见的 `From:`，也不能只看 SMTP envelope。攻击者可以构造：

```text
MAIL FROM:<attacker.invalid>
From: CEO <ceo@company.example>
```

这就是 SPF、DKIM、DMARC 需要协同的原因。

### 3.2 入站、出站、本地域、远程域

设：

- `S(m)`：邮件 `m` 的 envelope sender 域。
- `R(m)`：邮件 `m` 的 envelope recipient 域集合。
- `Local`：系统配置的本地域集合。

可粗略定义：

```text
outbound(m) := S(m) in Local and exists r in R(m): r not in Local
inbound(m)  := S(m) not in Local and exists r in R(m): r in Local
internal(m) := S(m) in Local and all r in R(m): r in Local
external(m) := S(m) not in Local and all r in R(m): r not in Local
```

实际系统还会结合域名配置、Relay 策略、Postfix 入口和运行时策略。上述定义的用途是帮助理解：域名配置不是 DNS 说明文字，而是安全决策输入。

## 4. SMTP、TLS 与可信来源

### 4.1 SMTP 是传输协议，不等于安全协议

SMTP 负责提交、转发和投递邮件。默认 SMTP 本身不保证：

- 对端身份可信。
- 传输内容保密。
- 邮件体未被篡改。
- Header From 没被伪造。

所以邮件系统通常叠加以下机制：

- TLS 或 STARTTLS：保护链路传输。
- SPF：检查发信 IP 是否被 envelope sender 域授权。
- DKIM：用发件域私钥签名部分头部和正文哈希。
- DMARC：要求 SPF 或 DKIM 与 Header From 域对齐，并给出失败策略。
- S/MIME：对邮件内容做端到端或网关到网关的签名和加密。

### 4.2 STARTTLS 与隐式 TLS

| 模式 | 工作方式 | 常见端口 |
| --- | --- | --- |
| 明文 SMTP | 连接后直接 SMTP 命令 | 25 |
| STARTTLS | 先明文 EHLO，再升级 TLS | 25/587，项目 GM Edge 可用 2525 |
| 隐式 TLS | 连接开始就是 TLS | 465，项目 GM Edge 可用 2465 |

STARTTLS 的风险是降级攻击：如果路径上攻击者移除 STARTTLS 能力，双方可能退回明文。因此对强安全伙伴域，不能只说“支持 TLS”，而应配置强制 TLS 或国密 Edge 分流。

### 4.3 可信代理头

Postfix 或 Edge 可能在内网链路上添加：

```text
X-Original-Client-IP: 203.0.113.10
X-SealMail-Edge-Protocol: TLCP
```

这类 header 只有在“直接连接 SealMail 的上一跳本身可信”时才能使用。形式化地：

```text
trusted_header_usable(m, peer_ip) :=
  peer_ip in trusted_relay_cidrs
  and header_name in configured_original_ip_headers
  and mail_auth_policy.trusted_proxy_mode permits trusted headers
```

如果任何公网主机都能直连 `10025`，攻击者就能伪造这些 header，让 SPF、审计和风控看到假的来源 IP。因此 `10025` 应该是内网端口或被防火墙限制。

## 5. 密码学基础

### 5.1 四类基本原语

| 原语 | 解决什么问题 | 项目例子 |
| --- | --- | --- |
| 对称加密 | 同一把密钥加密和解密，速度快 | AES、SM4 加密邮件内容 |
| 非对称加密 | 公钥加密或验签，私钥解密或签名 | RSA、SM2 保护内容密钥或签名 |
| 哈希 | 把任意长度数据映射成固定摘要 | SHA-256、SM3、DLP 哈希 |
| 数字签名 | 私钥签名，公钥验证，证明完整性和私钥持有 | S/MIME 签名、DKIM 签名、证书签发 |

邮件加密通常不会直接用 RSA 或 SM2 加密整封邮件，因为非对称加密慢且有长度限制。典型混合加密是：

```text
随机生成内容密钥 K
用 K 对邮件内容 C 做对称加密: Enc_K(C)
对每个收件人 r，用 r 的证书公钥加密 K: Enc_Pub(r)(K)
把 Enc_K(C) 和每个 Enc_Pub(r)(K) 放进 CMS/S/MIME 结构
```

### 5.2 标准算法族与国密算法族

项目把加密 Profile 分成：

| Profile | 典型算法 | 项目语义 |
| --- | --- | --- |
| `STANDARD` | RSA、AES、SHA-256 | 国际通用 S/MIME 套件 |
| `GM` | SM2、SM3、SM4 | 国密套件 |
| `AUTO` | 自动选择 | 根据证书和策略选择具体 Profile |

`SmimeAlgorithmSuites` 内置的 S/MIME 套件包括：

| 套件 | Profile | 内容加密 | 签名算法 |
| --- | --- | --- | --- |
| `STANDARD_AES_256_GCM` | STANDARD | AES-256-GCM | SHA256withRSA |
| `STANDARD_AES_128_GCM` | STANDARD | AES-128-GCM | SHA256withRSA |
| `STANDARD_AES_256_CBC` | STANDARD | AES-256-CBC | SHA256withRSA |
| `STANDARD_AES_128_CBC` | STANDARD | AES-128-CBC | SHA256withRSA |
| `GM_SM4_CBC` | GM | SM4-CBC | SM3withSM2 |
| `GM_SM4_GCM` | GM | SM4-GCM | SM3withSM2 |

注意：

- GCM 是认证加密模式，能同时保护机密性和密文完整性。
- CBC 需要正确 IV、填充和外层完整性保护；S/MIME/CMS 会提供结构化封装，但配置者仍应优先理解套件差异。
- GM 与 STANDARD 不是简单“强弱”关系，而是合规和互操作边界。伙伴系统只支持国密时，STANDARD 套件无法互通。

## 6. X.509、CA、证书与 keystore

### 6.1 证书是什么

X.509 证书可以理解为：

```text
Cert := {
  subject,
  subjectPublicKey,
  issuer,
  serialNumber,
  notBefore,
  notAfter,
  keyUsage,
  extendedKeyUsage,
  subjectAlternativeName,
  signatureByIssuer
}
```

证书把“身份”和“公钥”绑定起来。签名者通常是 CA。验证证书时至少要检查：

```text
valid_time(cert, t) := cert.notBefore <= t <= cert.notAfter
not_revoked(cert) := cert not in CRL and OCSP(cert) != revoked
trusted(cert) := admin has marked cert trusted or cert chains to trusted authority
purpose_ok(cert, usage) := keyUsage/EKU permits usage
identity_ok(cert, identity) := subject/SAN matches mailbox/domain/person
```

项目里的证书校验强调：

- 检查有效期。
- 开启 CRL 和 OCSP 时检查吊销状态。
- 导入未知证书默认不应自动视为可信，需要管理员显式信任。
- 系统可作为内部信任权威管理私有 CA 和业务证书。

### 6.2 CA 层级

常见层级是：

```text
Root CA
  -> Intermediate CA
    -> End-Entity Certificate
```

Root CA 私钥是最高价值材料，泄漏后可以伪造整个体系。生产环境通常应：

- Root CA 离线保存。
- Intermediate CA 用于日常签发。
- End-Entity 证书绑定具体邮箱、域名或服务。
- CRL Distribution Point 使用外部可访问 URL。

项目启动时可以自动创建 RSA 和 SM2 双栈内部 CA 及示例证书。这适合开发环境；生产环境应显式决定是否关闭自动初始化。

### 6.3 私钥与 keystore

私钥是证明身份和解密内容的核心材料。项目中私钥可能出现在：

- S/MIME 证书私钥。
- DKIM 私钥。
- Edge TLS keystore。
- JWT secret。
- Relay SMTP 密码。

安全原则：

```text
secret in git repository = security incident
```

推荐引用方式：

```text
env:VARIABLE_NAME
file:/run/secrets/name
VARIABLE_NAME
```

`SEALMAIL_KEYSTORE_PATH` 指向仓库外 PKCS12 keystore。如果不配置，进程内 keystore 适合短期开发，但重启后私钥不会持久保留。

## 7. S/MIME：邮件内容级加密与签名

### 7.1 S/MIME 解决的问题

S/MIME 是基于 CMS 和 X.509 的邮件内容安全机制。

| 能力 | 保护对象 | 证明什么 |
| --- | --- | --- |
| 签名 | 邮件内容及签名覆盖的 MIME 结构 | 内容未被改动，签名私钥持有人参与发送 |
| 加密 | 邮件内容 | 只有拥有对应私钥的收件人可解密 |

S/MIME 与 SMTP TLS 的区别：

| 机制 | 保护范围 | 中间节点能否看到内容 |
| --- | --- | --- |
| SMTP TLS | 两个 SMTP 节点之间的链路 | 下一跳 SMTP 节点可看到明文 |
| S/MIME | 邮件对象本身 | 未持有私钥的中间节点不能解密 |

网关型 S/MIME 要特别理解：如果网关负责加解密，那么网关就是一个受信任安全边界。它能看到明文并执行 DLP、审计、重加密等操作。

### 7.2 加密 Profile 选择

设：

- `P_req`：域名配置要求的 Profile，取值 `AUTO | GM | STANDARD`。
- `Certs(r)`：收件人 `r` 的可用证书集合。
- `profile(c)`：证书 `c` 对应的 Profile。

项目中的多收件人加密可以理解为：

```text
supports(r, p) := exists c in Certs(r): profile(c) = p and trusted(c)

plan(p, R) succeeds iff forall r in R: supports(r, p)

if P_req = GM:
  require plan(GM, R)
else if P_req = STANDARD:
  require plan(STANDARD, R)
else if P_req = AUTO:
  prefer plan(GM, R)
  else plan(STANDARD, R)
  else fail
```

关键点：一封多收件人邮件通常需要所有收件人共享同一种加密 Profile。若 A 只有 GM 证书，B 只有 STANDARD 证书，系统不能用同一个 S/MIME 加密包同时满足两者，除非拆分邮件或引入更复杂的多套件封装策略。

### 7.3 强制加密、允许加密、不加密

域名策略里的加密开关可以形式化为：

```text
MANDATORY:
  if encryption_plan succeeds -> encrypt
  else -> quarantine/fail according to policy

ALLOW:
  if encryption_plan succeeds -> encrypt
  else -> continue without encryption unless DLP says MUST_ENCRYPT

NO_ENCRYPTION:
  do not encrypt unless another stronger policy requires it
```

DLP 的 `MUST_ENCRYPT` 会提高要求：

```text
requires_encryption(m) :=
  domain_policy(m).encryption = MANDATORY
  or dlp_decision(m).action = MUST_ENCRYPT
  or other runtime policy requires encryption
```

因此“域名允许加密”不等于“永远可明文投递”。如果 DLP 命中高风险规则，邮件可能必须加密或进入隔离。

## 8. 邮件认证：SPF、DKIM、DMARC

邮件认证解决的不是内容保密，而是“这封邮件是否像它声称的那样来自某个域”。

### 8.1 SPF

SPF 由发件域在 DNS TXT 中声明哪些 IP 可以代表该域发邮件：

```text
example.com TXT "v=spf1 mx ip4:192.0.2.10 include:mail.example.net ~all"
```

验证输入通常是：

```text
SPF(ip, envelope_from_domain)
```

它不直接验证 Header From。攻击者可以让 SPF 通过另一个域，同时在 Header From 写成受害域，所以 SPF 必须和 DMARC 对齐一起看。

SPF 常见结果：

| 结果 | 含义 |
| --- | --- |
| `pass` | 来源 IP 被授权 |
| `fail` | 明确未授权，通常来自 `-all` |
| `softfail` | 可能未授权，通常来自 `~all` |
| `neutral` | 域不表达判断，通常来自 `?all` |
| `none` | 没有 SPF 记录或缺少可判断域 |
| `permerror` | SPF 记录语法或查询超限等永久错误 |
| `temperror` | DNS 临时错误 |

项目支持“跳过内网中继 SPF”。原因是 SealMail 常看到的是 Postfix 或 Edge 的内网 IP，而不是公网发信 IP。若没有可信原始 IP，就对 `127.0.0.1` 做 SPF，结果没有安全意义。

### 8.2 DKIM

DKIM 是发件域对邮件部分头部和正文哈希做签名。DNS 发布公钥：

```text
selector._domainkey.example.com TXT "v=DKIM1; k=rsa; p=..."
```

邮件里携带：

```text
DKIM-Signature: v=1; a=rsa-sha256; d=example.com; s=selector; h=from:to:subject:date; bh=...; b=...
```

核心验证：

```text
body_hash_ok := hash(canonicalize_body(m)) = bh
signature_ok := Verify(public_key(d, s), canonicalize_headers(m), b)
dkim_pass := body_hash_ok and signature_ok
```

需要关注：

- `d=` 是签名域，不一定等于 Header From 域。
- `s=` 是 selector，用于轮换密钥。
- `h=` 指明哪些头部参与签名。`from` 不应省略。
- Canonicalization 会定义空白、换行、头部折叠如何归一化。

项目当前 DKIM 实现聚焦 RSA-SHA256。国密 S/MIME 与国密 TLS 不自动意味着 DKIM 也是国密算法；这是两个不同协议层。

### 8.3 DMARC

DMARC 把 SPF 和 DKIM 结果与 Header From 域对齐起来，并读取发件域 DNS 中的策略：

```text
_dmarc.example.com TXT "v=DMARC1; p=quarantine; adkim=r; aspf=r; pct=100; rua=mailto:dmarc@example.com"
```

设：

- `FromDomain(m)`：Header From 域。
- `SPFDomain(m)`：SPF 认证使用的 envelope domain。
- `DKIMDomains(m)`：通过 DKIM 的签名域集合。
- `aligned(a, b, mode)`：域对齐。`s` 要求完全相等，`r` 允许组织域对齐。

DMARC 通过条件：

```text
dmarc_pass(m) :=
  (spf_pass(m) and aligned(SPFDomain(m), FromDomain(m), aspf))
  or
  (exists d in DKIMDomains(m): aligned(d, FromDomain(m), adkim))
```

失败时是否隔离取决于两个策略：

```text
remote_dmarc_policy in {none, quarantine, reject}
local_failure_action in {LOG_ONLY, APPLY_POLICY, FORCE_ALLOW}

quarantine_by_mail_auth :=
  not dmarc_pass
  and remote_dmarc_policy in {quarantine, reject}
  and local_failure_action = APPLY_POLICY
```

刚上线建议：

- DMARC `p=none` 观察。
- 本地失败处理先 `LOG_ONLY`。
- 收集确认后再切 `quarantine` 或 `reject`。

### 8.4 Authentication-Results

网关会写入类似：

```text
Authentication-Results: sealmail-gateway; spf=pass ...; dkim=pass ...; dmarc=pass ...
```

这个头用于把认证结果传递给后续系统和管理员。它本身也可能被外部伪造，所以写入时应移除或覆盖相同 `authserv-id` 的旧值，只信任由本网关生成的记录。

## 9. DLP：数据防泄漏

DLP 的目标不是判断邮件“真假”，而是判断邮件是否携带不该外发的敏感数据。

### 9.1 DLP 抽象流程

```text
邮件
  -> 内容抽取
  -> 策略解析
  -> 检测引擎
  -> 风险增强
  -> 处置动作
  -> 证据、隔离、审计
```

项目里的 DLP 资源包括：

- Pattern 规则：正则、关键词、内置模板。
- EDM 数据集：精确数据匹配，落库保存规范化哈希，不保存明文敏感值。
- Fingerprint 指纹库：把文档文本切片并哈希，用于检测相似内容或片段泄漏。
- Rule Group：把多个规则组合。
- Policy：定义方向、模式、规则组。
- Selection：按全局、域名、发件人、收件人等范围绑定策略或规则。
- UBA：根据发件人行为基线提高风险。

### 9.2 动作优先级

DLP 动作可以按偏序理解：

```text
WARN < MUST_ENCRYPT < QUARANTINE < BLOCK
```

多个规则命中时取最强动作：

```text
final_action(matches) := max(action(match_i))
```

项目中 `BLOCK` 和 `QUARANTINE` 最终都会进入强阻断/隔离路径；区别主要在业务语义和后续处置。

### 9.3 正则、关键词和内置模板

正则适合结构化模式，例如手机号、身份证号、API token。风险是：

- 写得太宽会误报。
- 写得太复杂可能造成性能问题。
- 不带校验位的规则容易命中随机数字。

严谨规则通常包含：

```text
candidate := regex(text)
valid := candidate matches syntax and checksum(candidate) ok
evidence := masked(candidate)
```

证据应脱敏保存，例如只保存前后少量字符或哈希，不能把完整身份证号、银行卡号、私钥片段写入审计。

### 9.4 EDM 精确匹配

EDM 适合“已知敏感清单”，例如客户身份证号、员工号、合同编号。基本思想：

```text
normalize(value) -> canonical
hash(canonical) -> digest
store(digest)

scan text:
  extract candidates
  normalize(candidate)
  if hash(candidate) in dataset_hashes:
    match
```

优势：

- 数据库不需要保存明文敏感值。
- 命中准确度高。

限制：

- 只能匹配已知值。
- 规范化必须一致，例如空格、大小写、分隔符处理。
- 如果候选提取不完整，可能漏报。

### 9.5 文档指纹

文档指纹适合检测“敏感文档片段”泄漏。粗略过程：

```text
document_text
  -> normalize
  -> sliding chunks
  -> hash each chunk
  -> store chunk hashes

mail_text
  -> normalize
  -> sliding chunks
  -> hash
  -> compare with library
```

指纹不是加密，不应反推出原文；但如果文本空间很小，哈希仍可能被字典攻击。因此指纹库和证据同样是敏感资产。

### 9.6 UBA 风险增强

UBA 不直接代替 DLP 规则，而是在已命中基础上调整风险。例如：

- 首次发送到外部域。
- 非常规时间。
- 附件异常大。
- 近期 DLP 命中频繁。

形式化地：

```text
base_action := final_action(dlp_matches)
risk_score := uba(sender, recipients, time, attachment_size, history)
recommended_action := upgrade(base_action, risk_score)
```

这可以把 `WARN` 提升为 `MUST_ENCRYPT` 或 `QUARANTINE`，但不应在没有内容命中的情况下单独阻断邮件，否则误伤会很高。

## 10. 隔离、放行与审计

### 10.1 隔离的意义

隔离不是投递失败日志，而是安全决策队列。邮件进入隔离通常意味着：

- 策略不允许自动投递。
- 需要管理员复核。
- 需要补充加密后释放。
- 需要拒绝并留痕。

隔离原因包括：

- 证书缺失，强制加密无法满足。
- 邮件认证失败且本地策略要求执行对方 DMARC。
- DLP 命中隔离或阻断规则。
- 其他策略违规。

### 10.2 放行不等于绕过安全

如果隔离策略启用 `releaseRequiresEncryption`，则放行也必须满足加密要求：

```text
can_release(q) :=
  q.status = QUARANTINED
  and raw_content_available(q)
  and (
    not releaseRequiresEncryption
    or encryption_plan(q.mail) succeeds
  )
```

这样可以避免“DLP 要求加密，但管理员一键明文放行”的策略绕过。

### 10.3 审计

审计要记录“谁在什么时间对什么对象做了什么”。项目的事件包括：

- 证书导入、信任、吊销。
- 邮件收到、签名、加密、解密、验证、隔离、投递。
- DLP 命中、隔离、释放、拒绝。
- 邮件认证策略变更。
- 运行时策略变更。

审计的安全要求：

- 内容最小化，避免把完整敏感正文写入审计。
- 保留 correlation id 或 processing id，方便串起一次处理。
- 记录操作者身份和角色。
- 记录策略变更字段，便于事后解释行为变化。

## 11. Relay、路由与投递策略

Relay 策略控制 SealMail 处理后如何外发到 smart host：

```text
enabled
host
port
username
passwordSecretRef
timeoutMs
envelopeFrom
allowUnconfiguredExternalRecipientDomains
```

安全含义：

- `enabled=false` 表示不使用全局 smart host。
- `passwordSecretRef` 只保存 secret 引用，不保存明文。
- `envelopeFrom` 会影响退信路径和 SPF 认证语义。
- `allowUnconfiguredExternalRecipientDomains=false` 可避免系统被当作开放中继。

开放中继风险很严重。形式化地，系统不应满足：

```text
untrusted_sender can submit mail to arbitrary external_recipient
```

因此出站邮件通常要求：

```text
sender_domain in enabled_local_domains
and recipient policy permits route
and relay policy permits target
and DLP/mail-auth/crypto decisions do not block
```

## 12. 身份认证、JWT 与 RBAC

管理 API 使用登录接口换取 Bearer token。关键概念：

| 机制 | 作用 |
| --- | --- |
| BCrypt | 存储用户密码哈希，避免保存明文密码 |
| JWT | 登录后携带身份和权限声明 |
| `SEALMAIL_JWT_SECRET` | HMAC 签名密钥，保护 token 不被伪造 |
| RBAC | 用角色控制管理能力 |

JWT 的安全性质依赖 secret：

```text
token_valid :=
  signature(token, jwt_secret) ok
  and now < exp
  and subject/user still valid according to server policy
```

如果 `SEALMAIL_JWT_SECRET` 太短、泄漏或提交到仓库，攻击者可能伪造管理员 token。生产环境应使用高熵随机 secret，并通过 secret manager 或文件挂载注入。

常见角色包括：

- `SUPER_ADMIN`
- `ADMIN`
- `PKI_ADMIN`
- `DOMAIN_ADMIN`
- `DOMAIN_MANAGER`
- `AUDITOR`
- `USER`

前端权限只能改善交互体验，不能作为唯一安全边界；后端 use case 必须继续校验权限。

## 13. 配置来源与优先级

项目配置可以分成四层：

| 层级 | 例子 | 适合内容 |
| --- | --- | --- |
| 启动环境变量 | `SEALMAIL_DB_URL`、`SEALMAIL_JWT_SECRET` | 部署环境差异、secret 引用 |
| 后端 YAML 默认值 | `application.yml` | 安全默认值、端口、超时 |
| 数据库运行时策略 | Relay、DLP、域名、DKIM/SPF/DMARC | 管理员可动态调整的业务安全策略 |
| 系统级网关配置 | Postfix `main.cf`/`master.cf`、Edge properties | 邮件链路和系统服务边界 |

不要把“应该运行时调整的策略”硬编码到 YAML，也不要把“必须保密的材料”写入数据库明文字段或仓库文件。

### 13.1 Secret 引用

推荐模型：

```text
config stores reference
runtime resolves secret
secret never appears in git
```

例如：

```json
{
  "passwordSecretRef": "file:/run/secrets/sealmail_relay_password"
}
```

这比直接保存密码更好，因为：

- 数据库泄漏时不会直接泄漏 SMTP 密码。
- 可以由部署平台轮换 secret。
- 审计和配置导出时不会带出明文。

## 14. 运行时决策的统一形式

可以把邮件处理看成一个决策函数：

```text
Decision(m, ctx, policies, certs, secrets) -> {
  route,
  sign_smime?,
  encrypt_smime?,
  sign_dkim?,
  verify_smime?,
  verify_mail_auth?,
  quarantine?,
  relay_profile,
  audit_events
}
```

主要输入：

| 输入 | 来源 |
| --- | --- |
| `m` | SMTP envelope、邮件头、MIME 内容 |
| `ctx` | 来源 IP、入口类型、processing id |
| `policies` | 域名策略、DLP、Relay、隔离、邮件认证 |
| `certs` | 证书库和证书绑定 |
| `secrets` | 私钥、DKIM key、JWT secret、SMTP 密码 |

一个实用的优先级模型是：

```text
if malformed_or_oversized(m):
  reject_or_quarantine

auth_results := verify SPF/DKIM/DMARC when enabled
if auth_results.decision requires quarantine:
  quarantine

dlp_decision := scan DLP when policy applies
if dlp_decision.action in {BLOCK, QUARANTINE}:
  quarantine

if encryption required:
  if encryption_plan succeeds:
    encrypt
  else:
    quarantine

if signing required:
  sign

if dkim signing required:
  sign DKIM

route and deliver
```

实际代码可能按流水线步骤拆分，但理解配置时可以按这个逻辑排查。

## 15. 常见配置项的安全语义

| 配置 | 不是 | 真正含义 |
| --- | --- | --- |
| 本地域名 | 只是一段描述文字 | 决定出站、入站、Relay 和域名策略适用范围 |
| 强制加密 | “尽量加密” | 无可用证书时不能正常明文投递 |
| 算法偏好 GM/STANDARD | UI 风格选择 | 决定证书选择和 S/MIME 套件互操作边界 |
| 启用 S/MIME 签名 | 等于 DKIM | 内容级签名，依赖证书私钥 |
| 启用 DKIM | 等于防垃圾邮件 | 域名级完整性和身份认证输入 |
| SPF `mx` | 自动允许所有服务器 | 只允许 MX 解析出的主机 IP |
| DMARC `reject` | 绝对安全 | 会放大配置错误，可能拒收合法邮件 |
| 跳过内网 SPF | 放弃安全 | 避免对代理 IP 做错误判断，前提是有其他可信来源处理 |
| Relay `allowUnconfiguredExternalRecipientDomains` | 方便外发 | 开启后要防止开放中继风险 |
| `trust-all=true` | 解决 TLS 问题 | 关闭证书信任校验，生产环境高风险 |

## 16. 排错思路

### 16.1 邮件没有加密

按顺序检查：

1. 发件域或收件域是否命中正确域名配置。
2. 域名 `encryptionPolicy` 是 `MANDATORY`、`ALLOW` 还是 `NO_ENCRYPTION`。
3. DLP 是否把动作提升为 `MUST_ENCRYPT`。
4. 每个收件人是否有受信任、未吊销、未过期的证书。
5. 多收件人是否能共享同一 Profile。
6. `SEALMAIL_KEYSTORE_PATH` 是否保证私钥重启后仍存在。

### 16.2 DKIM 验证失败

检查：

1. DNS selector 是否与 `DKIM-Signature` 的 `s=` 一致。
2. DNS 公钥是否来自当前私钥。
3. 签名覆盖头部是否被下游系统改写。
4. 正文末尾空行、MIME 边界或编码是否被改写。
5. 是否发布了多个冲突 TXT。

### 16.3 SPF 失败

检查：

1. SealMail 看到的是公网发信 IP 还是 Postfix/Edge 内网 IP。
2. 是否启用了可信代理原始 IP 解析。
3. 发件域 SPF 是否包含实际发信服务。
4. include 是否递归超限。
5. `-all` 是否过早启用。

### 16.4 DMARC 隔离

检查：

1. Header From 域是什么。
2. SPF 通过的域是否与 Header From 对齐。
3. DKIM 通过的 `d=` 域是否与 Header From 对齐。
4. 对方 DMARC 策略是 `none`、`quarantine` 还是 `reject`。
5. 本地失败处理是 `LOG_ONLY` 还是 `APPLY_POLICY`。

### 16.5 DLP 误报或漏报

误报时检查：

1. 正则是否过宽。
2. 是否缺少校验位或上下文条件。
3. 规则是否绑定到了过大的 Selection 范围。
4. UBA 是否把低风险动作升级过高。

漏报时检查：

1. 内容抽取是否支持该 MIME 类型或附件类型。
2. 附件是否过大、损坏或加密压缩。
3. EDM 规范化是否与导入时一致。
4. 指纹切片参数是否过于严格。
5. 策略方向或作用范围是否没有命中。

## 17. 上线安全检查清单

上线前至少确认：

- `10025`、`10026`、`10027`、`2530` 等内部端口只允许可信主机访问。
- `SEALMAIL_JWT_SECRET` 足够随机，未提交仓库。
- 所有私钥、keystore、truststore、DKIM key、Relay 密码通过 secret 引用或仓库外文件注入。
- 本地域名、远程域名和 Relay 策略不会形成开放中继。
- DKIM、SPF、DMARC DNS TXT 已发布并探测通过。
- DMARC 从 `none` 或本地 `LOG_ONLY` 起步，再逐步收紧。
- 强制加密域的收件人证书完整、受信任、未过期、未吊销。
- CRL/OCSP 地址在生产网络中可达，超时策略明确。
- DLP 规则经过 test/simulate，证据脱敏。
- 隔离保留期、放行是否要求加密、审计权限已配置。
- GM Edge 的 keystore/truststore、TLCP/国密 TLS 端口和 Postfix transport map 已单独验证。
- 提交前运行敏感材料扫描脚本。

## 18. 最小学习路径

如果你从零开始学习这个项目，建议按以下顺序理解：

1. 先理解 SMTP envelope 与 Header From 的区别。
2. 再理解 Postfix content filter 为什么把邮件送到 `10025`。
3. 然后理解本地域名如何决定入站、出站和 Relay。
4. 学 S/MIME：证书、公钥加密、私钥解密、签名验签。
5. 学 SPF、DKIM、DMARC：分别解决 IP 授权、签名完整性、域名对齐。
6. 学 DLP：内容抽取、规则命中、动作优先级、隔离。
7. 最后学国密 Edge：它是特殊伙伴域的传输层入口，不替代 S/MIME 或 DKIM。

掌握这条路径后，再看 `docs/configuration-guide.md` 里的配置步骤，会更容易判断每个值为什么存在，以及改错后会影响哪条安全性质。
