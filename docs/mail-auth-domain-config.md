# 域名配置与邮件认证说明

这份文档解释前端“域名配置”页面里的开关和 DKIM、SPF、DMARC 配置项。

## 一句话理解

域名配置管的是：这个网关遇到某个域名时，是否加密、是否签名、是否启用这条配置。

邮件认证管的是：如何用 DKIM、SPF、DMARC 判断邮件是不是伪造的，以及你的域名应该发布哪些 DNS TXT 记录。

## 域名配置

### 启用配置

控制这条域名配置是否生效。

关闭后，这个域名不会再参与邮件路由、加密、签名、DKIM 这些域名级判断。

### 本地域名

表示这个域名属于你自己的系统。

例如公司域名是 `example.com`，那么 `example.com` 应该是本地域名。

### 加密策略

控制 S/MIME 加密要求。

| 选项 | 含义 | 适合场景 |
| --- | --- | --- |
| 强制加密 | 必须加密，条件不满足时可能失败或隔离 | 内部核心域名、强安全要求 |
| 允许加密 | 有证书就加密，没有证书可放行 | 普通业务域名 |
| 不加密 | 不做加密 | 测试、兼容旧系统 |

推荐：

| 域名类型 | 推荐值 |
| --- | --- |
| 本地域名 | 强制加密 |
| 外部域名 | 允许加密 |
| 临时测试 | 不加密或允许加密 |

### 算法偏好

控制 S/MIME 签名和加密时优先使用什么算法。

| 选项 | 含义 |
| --- | --- |
| 自动选择 | 系统自动选择可用算法 |
| 国密优先 | 优先使用 SM2/SM3/SM4 |
| 国际优先 | 优先使用 RSA/AES/SHA256 |

一般用“自动选择”。

### 启用邮件签名

控制 S/MIME 签名。

它和 DKIM 不一样：

| 类型 | 作用 |
| --- | --- |
| S/MIME 签名 | 证明邮件内容和发件人证书可信 |
| DKIM 签名 | 让收件方通过 DNS 公钥验证邮件没有被篡改 |

### 启用 DKIM 签名

控制这个域名出站邮件是否做 DKIM 签名。

真正生效需要同时满足：

1. 域名配置里启用 DKIM 签名。
2. 邮件认证里的 DKIM 总开关开启。
3. DKIM 配置里有可用私钥。

## 邮件认证总开关

路径：域名配置页面右上角“邮件认证”。

### 邮件认证

总开关。

关闭后，SPF、DKIM、DMARC 的入站验证基本不会参与处理。

### DKIM

DKIM 全局开关。

关闭后：

- 入站 DKIM 验证不生效。
- 出站 DKIM 签名也不会生效。

### SPF

SPF 全局开关。

关闭后，不验证发件方 IP 是否被发件域名授权。

### DMARC

DMARC 全局开关。

关闭后，不会根据发件方 DMARC 策略做最终处理。

### 跳过内网中继 SPF

推荐开启。

原因：如果系统部署在 Postfix content-filter、内网网关、代理之后，网关看到的 IP 可能是 `127.0.0.1` 或内网 IP，而不是外部真实发信 IP。

这种情况下强行做 SPF 容易误判，所以开启后会跳过内网来源的 SPF 验证。

### 认证服务标识

写入邮件头 `Authentication-Results` 的名字。

例如：

```text
sealmail-gateway
```

一般不用改。

## DKIM

DKIM 的作用：给出站邮件加一个签名。收件方通过 DNS 上的公钥验证邮件没有被篡改。

### Selector

DKIM DNS 记录的前缀。

如果：

```text
Selector = sealmail
域名 = example.com
```

那么 DNS 主机名是：

```text
sealmail._domainkey.example.com
```

### 签名头

表示哪些邮件头参与 DKIM 签名。

推荐值：

```text
from
to
subject
date
message-id
```

不要随便删 `from`，否则 DKIM 意义会变弱。

### 私钥路径

服务器上的 DKIM 私钥文件路径。

例如：

```text
/etc/sealmail/dkim/example.com.private.pem
```

适合生产环境。

### 私钥 PEM

也可以直接把 PEM 私钥填到配置里。

适合测试或小规模部署。

生产环境更推荐用“私钥路径”，避免把私钥内容存进数据库。

### 清空 PEM 私钥

打开后保存，会删除数据库里保存的 PEM 私钥。

不会删除“私钥路径”指向的文件。

### DKIM DNS TXT

后端会根据 DKIM 私钥生成公钥 TXT 记录。

如果没有私钥，DKIM TXT 记录不会生成有效 `p=` 内容。

示例：

```text
主机名:
sealmail._domainkey.example.com

TXT:
v=DKIM1; k=rsa; p=MIIBIjANBgkqh...
```

## SPF

SPF 的作用：声明哪些服务器可以代表你的域名发邮件。

它靠 DNS TXT 记录生效。

### DNS 查询上限

验证别人 SPF 时最多递归查询多少次。

推荐：

```text
10
```

### A

是否允许域名 A 记录对应的 IP 发邮件。

如果你的 Web 主机也发邮件，可以开。

如果只有专门邮件服务器发邮件，可以关。

### MX

是否允许域名 MX 记录对应的邮件服务器发邮件。

大多数情况下可以开。

### IPv4

允许发信的 IPv4。

示例：

```text
192.0.2.10
```

### IPv6

允许发信的 IPv6。

示例：

```text
2001:db8::10
```

### Include

引用第三方邮件服务商的 SPF。

例如你的邮件由第三方系统发送，可以填：

```text
mail.example.net
```

生成后类似：

```text
include:mail.example.net
```

### All 策略

SPF 最后的兜底规则。

| 选项 | 含义 | 严格程度 |
| --- | --- | --- |
| `-all` | 未声明来源直接失败 | 严格 |
| `~all` | 未声明来源软失败 | 中等 |
| `?all` | 未声明来源中立 | 宽松 |

推荐：

| 阶段 | 推荐值 |
| --- | --- |
| 刚上线 | `~all` |
| 确认发信来源完整后 | `-all` |
| 不确定来源 | `?all` 或 `~all` |

### SPF DNS TXT

示例：

```text
主机名:
example.com

TXT:
v=spf1 mx ip4:192.0.2.10 include:mail.example.net ~all
```

## DMARC

DMARC 的作用：把 SPF 和 DKIM 的结果合起来，决定伪造邮件怎么处理。

DMARC 不是单独工作的，它依赖 SPF 或 DKIM 至少一个通过，并且域名要对齐。

### 策略

| 选项 | 含义 |
| --- | --- |
| `none` | 只观察，不要求处理 |
| `quarantine` | 建议隔离失败邮件 |
| `reject` | 建议拒绝失败邮件 |

推荐上线顺序：

1. 先用 `none` 观察。
2. 没问题后改 `quarantine`。
3. 最后再考虑 `reject`。

### DKIM 对齐

控制 DKIM 签名域名和 From 域名如何匹配。

| 选项 | 含义 |
| --- | --- |
| `r` | 宽松，允许子域名对齐 |
| `s` | 严格，必须完全一致 |

一般先用 `r`。

### SPF 对齐

控制 SPF 的 Mail From 域名和邮件 From 域名如何匹配。

| 选项 | 含义 |
| --- | --- |
| `r` | 宽松，允许子域名对齐 |
| `s` | 严格，必须完全一致 |

一般先用 `r`。

### 生效比例

DMARC 的 `pct`。

示例：

```text
100
```

表示策略对 100% 邮件生效。

灰度时可以用：

```text
10
```

### RUA

聚合报告地址。

示例：

```text
mailto:dmarc@example.com
```

### RUF

取证报告地址。

示例：

```text
mailto:forensic@example.com
```

不是所有收件方都会发送 RUF。

### 失败处理

这是网关自己的处理方式。

| 选项 | 含义 |
| --- | --- |
| 按策略处理 | 如果 DMARC 失败且对方策略是 quarantine/reject，网关会隔离 |
| 仅记录 | 只记录认证结果，不隔离 |

刚上线建议用“仅记录”。

确认无误后再改成“按策略处理”。

### DMARC DNS TXT

示例：

```text
主机名:
_dmarc.example.com

TXT:
v=DMARC1; p=quarantine; adkim=r; aspf=r; pct=100; rua=mailto:dmarc@example.com
```

## DKIM、SPF、DMARC 怎么协同

### 出站邮件

你的系统发邮件时：

1. DKIM 用私钥给邮件签名。
2. 你的 DNS 上发布 DKIM 公钥。
3. 你的 DNS 上发布 SPF，声明哪些服务器能发信。
4. 你的 DNS 上发布 DMARC，声明验证失败时怎么处理。

收件方收到邮件后，会查你的 DNS 来验证。

### 入站邮件

别人发邮件给你时：

1. 网关检查对方 SPF。
2. 网关检查对方 DKIM。
3. 网关读取对方 DMARC。
4. 如果 SPF 或 DKIM 通过并且域名对齐，DMARC 通过。
5. 如果都不通过，按对方 DMARC 策略和本网关“失败处理”决定是否隔离。

## 推荐配置模板

### 刚上线观察期

域名配置：

```text
启用配置: 开
加密策略: 允许加密
算法偏好: 自动选择
启用邮件签名: 按需
启用 DKIM 签名: 开
```

邮件认证：

```text
邮件认证: 开
DKIM: 开
SPF: 开
DMARC: 开
跳过内网中继 SPF: 开
```

SPF：

```text
A: 按需
MX: 开
All策略: ~all
```

DMARC：

```text
策略: none
DKIM对齐: r
SPF对齐: r
生效比例: 100
失败处理: 仅记录
```

### 稳定运行后

SPF：

```text
All策略: -all
```

DMARC：

```text
策略: quarantine
失败处理: 按策略处理
```

### 强安全域名

域名配置：

```text
加密策略: 强制加密
启用邮件签名: 开
启用 DKIM 签名: 开
```

DMARC：

```text
策略: reject
DKIM对齐: s
SPF对齐: s
失败处理: 按策略处理
```

## 常见问题

### 开了 DKIM 为什么 DNS 记录为空？

通常是没有配置 DKIM 私钥。

需要配置“私钥路径”或“私钥 PEM”。

### 开了域名 DKIM，为什么邮件没签名？

检查三件事：

1. 域名配置里“启用 DKIM 签名”是否开启。
2. 邮件认证里 DKIM 总开关是否开启。
3. DKIM 私钥是否存在并能被后端读取。

### SPF 配了为什么入站验证还是失败？

前端 SPF 配置生成的是“你的域名应该发布的 SPF DNS 记录”。

验证别人发来的邮件时，网关读取的是“对方域名 DNS 上真实发布的 SPF 记录”。

### DMARC 为什么会隔离邮件？

同时满足这些条件时可能隔离：

1. DMARC 开启。
2. 对方 DMARC 验证失败。
3. 对方 DMARC 策略是 `quarantine` 或 `reject`。
4. 网关失败处理是“按策略处理”。

### 生产环境最容易踩什么坑？

最常见的是这几个：

1. SPF 里漏掉第三方发信服务。
2. DKIM 私钥和 DNS 公钥不匹配。
3. DMARC 一上来就 `reject`。
4. 网关在内网中继后面，却关闭了“跳过内网中继 SPF”。

## 最短操作步骤

如果你只是想先跑起来：

1. 添加本地域名，例如 `example.com`。
2. 开启域名配置。
3. 开启域名 DKIM 签名。
4. 打开“邮件认证”。
5. 开启 DKIM、SPF、DMARC。
6. DKIM 填 selector 和私钥路径。
7. SPF 先用 `mx` + `~all`。
8. DMARC 先用 `none` + `仅记录`。
9. 复制 DKIM、SPF、DMARC 的 DNS TXT 到域名 DNS 服务商。
10. 等 DNS 生效后，再逐步收紧 SPF 和 DMARC。
