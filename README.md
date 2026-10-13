# AdGuard 规则测试器（AdGuardRuleTester）

独立 Android 应用（Java，minSdk 29）：**测试 AdGuard 实际拦截效果，把未被拦截的广告/跟踪域名
自动生成 AdGuard 补漏规则。**

## 功能

1. **解析测试站点列表**（在线抓取；按来源只抓相关脚本，减少无关脚本和噪声）：
   - `https://paileactivist.github.io/toolz/adblock.html`（广告/分析/错误跟踪/社交/混合 + OEM）
   - `https://mobileproxy.space/ch/adblock-test.html`（广告/分析/跟踪/错误监控、加密货币挖矿、社交、OEM、Cookie、联盟、A/B、邮件、视频等 12 类）
   - `https://adblock.turtlecute.org/`（开源测试页；广告网络、分析、社交跟踪和 OEM）
   - `https://superadblocktest.com/`（广告、分析、指纹、社交、OEM、Cookie、联盟、视频等多类主机）
   - `https://getblockify.com/adblock-test`（页面公开列出 80+ 测试网络主机）
   - 任意自定义页面 URL（通用解析器兜底）

   Turtlecute / Super Adblock Test / GetBlockify 默认不勾选，可按需启用。mobileproxy 若返回安全验证页，应用不会绕过验证，会提示用户复制链接并在浏览器中手动测试。
2. **并发拦截测试**：最多 10 个目标并行探测；较短超时减少卡顿，失败时可用公共 DoH（dns.google / cloudflare-dns.com）区分疑似拦截与域名失效。关闭 DoH 可更快，但会有更多“未知”结果。
3. **实时结果**：测试中即时更新进度和统计；未拦截目标置顶，列表可上下滑动。点击任一结果行或“复制链接”可复制该目标 URL；每个内置站点旁也有复制入口，便于手动打开。
4. **生成 AdGuard 规则**：对未拦截域名输出 `||域名^`（可选 `$important`、按分类分组注释、含统计头），一键复制 / 分享 / 保存为 `.txt`，导入 AdGuard「自定义过滤规则」即可生效。

界面用原生 View 独立实现了柔和渐变、圆角半透明风格卡片与浅/深色配色，参考 [yz8023/ui](https://github.com/yz8023/ui) 的玻璃卡片和层级布局；未引入 Compose/backdrop 大型依赖。

## 使用

```bash
./gradlew :app:assembleDebug        # 或 Android Studio 直接 Run
# APK: app/build/outputs/apk/debug/app-debug.apk
```

当前版本 `1.2.0`（versionCode `3`）。此处构建的 debug APK 仅用于试装；正式发布需要使用自己的 release keystore 签名。

1. 安装 APK，打开 AdGuard（本地 VPN 或 DNS 模式）保持拦截生效。
2. App 内：`解析测试列表` → `开始测试` → 完成后自动生成规则 → `复制/分享/保存文件`。
3. 在 AdGuard：设置 → 过滤器 → 自定义过滤规则（或“我的规则”）→ 粘贴保存的规则文本。

## 判定口径

| 状态 | 含义 | 是否写入规则 |
| --- | --- | --- |
| 未拦截 | 连接成功（任意 HTTP 状态码） | ✅ |
| 已拦截·DNS | 系统 DNS 失败而 DoH 可解析 | ❌ |
| 已拦截·连接 | 连接/TLS 失败而 DoH 确认域名存活 | ❌ |
| 域名失效 | DoH 亦为 NXDOMAIN/无记录 | ❌ |
| 无法验证 | DoH 不可达等（可选写入） | 默认❌ |

> 注意：“已拦截”依赖当前网络环境（AdGuard 模式、DNS、运营商等）；DoH 参照验证可显著减少误判。

## 实测证据（2026-10-14）

- paileactivist `resources/adblock/data.js`：5 类主列表 + 5 家 OEM 列表；单测覆盖重复项、尾随空格归一化和 127 个唯一主机。
- mobileproxy.space：重新抓取当前 `/ch/adblock-test.html` 并校验内联 `AdBlockTest` 的 12 类主机列表；报告中的 `/Ch/adblock test .html` 会归一化到规范 URL。若站点改返回“正在进行安全验证”或 HTTP 403/429，应用会停止解析并提示复制链接后用浏览器手动测试，不尝试绕过验证。
- Turtlecute：真实页面引用 `/js/index.js`，该脚本通过 `JSON.parse('…')` 保存分类主机数据；单测断言域名、分类和厂商归属。
- Super Adblock Test：解析 `/adblock.js` 中的 `HOST_TESTS`，支持 ``list(`…`,)`` 模板列表和对象尾随逗号；只抓测试数据脚本，避免分析 Bootstrap/统计脚本。
- GetBlockify：从 SSR `itemProp="name"` 语义列表抽取主机，并根据最近的标题分类；不下载 Next.js bundle，也过滤 YouTube / Googlevideo / Spotify 等内容根域。
- 目前 JVM 单测共 22 项，覆盖上述来源、安全验证页识别、URL 纠错、AdGuard 规则生成和去重。

## 结构

```
app/src/main/java/com/yz8023/adguardtester/
  model/    TestTarget · ProbeStatus · ProbeResult     （纯 JVM）
  parse/    SourceParser                               （纯 JVM：结构化 JS / 语义化 HTML + 通用兜底）
  rules/    RuleGenerator                              （纯 JVM）
  probe/    ProbeEngine · DohVerifier                  （OkHttp，无 android.*）
  net/      SourceFetcher                              （OkHttp，按来源筛选脚本、限量抓取）
  ui/       ResultAdapter · MainActivity
app/src/test/   SourceParserTest · SourceFetcherTest · RuleGeneratorTest + 站点夹具
```

解析/生成逻辑不依赖 `android.*`，JVM 单测（22 项）：`./gradlew :app:testDebugUnitTest`。

## 构建环境

- JDK 17、Android SDK（compileSdk 36）、Gradle 8.13（wrapper 已内置）
- CI：push/PR 触发单测 + debug APK 构建，产物 `AdGuardRuleTester-apk`
