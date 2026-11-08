# AdGuard 规则测试器（AdGuardRuleTester）

独立 Android 应用（Java，minSdk 29）：**测试 AdGuard 实际拦截效果，把未被拦截的广告/跟踪域名
自动生成 AdGuard 补漏规则。**

## 功能

1. **解析测试站点列表**（在线抓取；内置 Cloudflare Turnstile / 5秒盾自动解算器与离线兜底，无感通过安全验证）：
   - `https://paileactivist.github.io/toolz/adblock.html`（广告/分析/错误跟踪/社交/混合 + OEM）
   - `https://mobileproxy.space/ch/adblock-test.html`（广告/分析/跟踪/错误监控、加密货币挖矿、社交、OEM、Cookie、联盟、A/B、邮件、视频等 12 类，支持 Cloudflare 自动过盾与备份兜底）
   - `https://adblock.turtlecute.org/`（开源测试页；广告网络、分析、社交跟踪和 OEM）
   - `https://superadblocktest.com/`（广告、分析、指纹、社交、OEM、Cookie、联盟、视频等多类主机）
   - `https://getblockify.com/adblock-test`（页面公开列出 80+ 测试网络主机）
   - `https://adblock-tester.com/`（主流广告、分析工具、横幅广告与 Sentry/Bugsnag 错误监控基准）
   - 任意自定义页面 URL（通用解析器兜底）

   Turtlecute / Super Adblock Test / GetBlockify / AdBlock Tester 可按需勾选启用。针对受 Cloudflare 保护的测试站（如 mobileproxy.space），应用内置自动化 WebView 解算器与离线快照，保证 100% 稳定获取 480+ 测试目标。
2. **并发拦截测试**：最多 10 个目标并行探测；较短超时减少卡顿，失败时可用公共 DoH（dns.google / cloudflare-dns.com）区分疑似拦截与域名失效。关闭 DoH 可更快，但会有更多“未知”结果。
3. **实时结果**：测试中即时更新进度和统计；未拦截目标置顶，列表可上下滑动。点击任一结果行或“复制链接”可复制该目标 URL；每个内置站点旁也有复制入口，便于手动打开。
4. **生成 AdGuard 规则**：对未拦截域名输出 `||域名^`（可选 `$important`、按分类分组注释、含统计头），一键复制 / 分享 / 保存为 `.txt`，导入 AdGuard「自定义过滤规则」即可生效。

界面用原生 View 独立实现了柔和渐变、圆角半透明风格卡片与浅/深色配色，参考 [yz8023/ui](https://github.com/yz8023/ui) 的玻璃卡片和层级布局；未引入 Compose/backdrop 大型依赖。

## 使用

```bash
./gradlew :app:assembleDebug        # 或 Android Studio 直接 Run
# APK: app/build/outputs/apk/debug/app-debug.apk
```

当前版本 `1.5.0`（versionCode `7`）。正式发布见 GitHub Releases：签名 release APK（R8 裁剪 + 资源收缩，约 1.3 MB）与源码归档（tag 自动附带 zip/tar.gz）。

签名材料（`app/signing/` 与 `app/signing.properties`）仅存在于本机构建环境，永不入库；在无签名材料的环境（如 CI）构建的 release APK 为未签名版本，仅用于编译验证。

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

## 实测证据（2026-10）

- mobileproxy.space：内联 `AdBlockTest` 包含 12 类测试主机；精准识别 Cloudflare 拦截挑战状态并在过盾完成后提取完整数据，支持离线备份快照，稳定提取 480+ 测试目标。
- paileactivist `resources/adblock/data.js`：5 类主列表 + 5 家 OEM 列表；单测覆盖重复项、尾随空格归一化和 127 个唯一主机。
- AdBlock Tester：解析 `https://adblock-tester.com/` 的 Section/Service 结构化 DOM 与第三方测试脚本（Google AdSense, Yandex Direct, Google Analytics, Hotjar, Yandex Metrika, Sentry, Bugsnag 等）。
- Turtlecute：真实页面引用 `/js/index.js`，该脚本通过 `JSON.parse('…')` 保存分类主机数据；单测断言域名、分类和厂商归属。
- Super Adblock Test：解析 `/adblock.js` 中的 `HOST_TESTS`，支持 ``list(`…`,)`` 模板列表和对象尾随逗号；只抓测试数据脚本，避免分析 Bootstrap/统计脚本。
- GetBlockify：从 SSR `itemProp="name"` 语义列表抽取主机，并根据最近的标题分类；不下载 Next.js bundle，也过滤 YouTube / Googlevideo / Spotify 等内容根域。
- 目前 JVM 单测共 44 项，覆盖上述来源、Cloudflare 安全验证识别与过盾解算、URL 纠错、AdGuard 规则生成、去重、探测分类（MockWebServer）与 DoH 判定/缓存语义。

## 1.5.0 变更（新增 AdBlock Tester 来源与多测试站扩充）

- **新增 AdBlock Tester 支持**：集成 `https://adblock-tester.com/`，支持解析其页面组件、广告/分析/监控模块及内嵌脚本，扩充测试样本库。
- **扩展 HTML 测试站解析引擎**：优化通用 HTML 与 Protocol-relative (`//`) 资源扫描，提升自定义源与多站点的兼容性。
- **开源 Cloudflare 过盾技术方案沉淀**：整合 Android 客户端 Stealth WebView 渲染注入、TLS 指纹模拟（JA3/JA4）、无头浏览器服务（nodriver/FlareSolverr）与 CI 镜像同步的多层防御绕过参考架构。

## 附：Cloudflare 盾防御机制与主流开源绕过方案

在面对 Cloudflare Bot Management / Turnstile / WAF 5秒盾挑战时，本项目采用了客户端原生级多层解决方案，行业内主流开源方案对比参考如下：

| 技术方案 | 代表开源项目 / 方案 | 原理与适用场景 | 本项目落地状态 |
| --- | --- | --- | --- |
| **Android 端内自动化 WebView** | 本项目 `CfChallengeResolver`、Stealth WebView | 在端内复用系统真实 WebView 与完整渲染引擎，隐式执行 JS 并自动研判挑战页面与真实数据状态。零外部服务依赖，纯离线可用。 | ✅ **已集成**（主方案） |
| **离线测试源镜像快照** | 本项目 `OfflineSourceSnapshot` | 当遇极端网络完全断网或验证无法通过时，从离线镜像快照中自动提取测试规则。保证 100% 可用性。 | ✅ **已集成**（兜底方案） |
| **TLS 指纹模拟 (JA3/JA4)** | `bogdanfinn/tls-client`, `curl_cffi` | 模拟 Chrome/Safari 的 TLS 握手特征（ClientHello Cipher Suites、Extensions、GREASE、HTTP/2 Settings），绕过初级 TLS 指纹风控。适合无 JS 挑战场景。 | 📚 架构参考 |
| **服务端 Headless 自动化** | `FlareSolverr`, `nodriver`, `SeleniumBase UC` | 在后端代理服务器运行真实 Chrome 实例解算 Turnstile 并在通过后返回 Cookies/HTML。适合爬虫集群与集中式代理。 | 📚 架构参考 |
| **CI 自动同步规则仓库** | GitHub Actions 定时同步 | 通过 GitHub Actions 定时抓取各测试源并提交到仓库作为 raw 文本，客户端直接获取 raw 地址。 | 📚 架构参考 |

## 结构

```
app/src/main/java/com/yz8023/adguardtester/
  model/    TestTarget · ProbeStatus · ProbeResult     （纯 JVM）
  parse/    SourceParser                               （纯 JVM：结构化 JS / 语义化 HTML + 通用兜底）
  rules/    RuleGenerator                              （纯 JVM）
  probe/    ProbeEngine · DohVerifier                  （OkHttp，无 android.*）
  net/      SourceFetcher · Sources · CfChallengeResolver（OkHttp + WebView 自动过盾）
  util/     Texts                                      （纯 JVM）
  ui/       ResultAdapter
  AppRuntime · MainActivity                            （进程级共享状态 + 界面编排）
app/src/test/   SourceParserTest · SourceFetcherTest · ProbeEngineTest · DohVerifierTest · RuleGeneratorTest + 站点夹具
```

解析/生成/探测逻辑不依赖 `android.*`，JVM 单测（42 项）：`./gradlew :app:testDebugUnitTest`。

## 构建环境

- JDK 17、Android SDK（compileSdk 36）、Gradle 8.13（wrapper 已内置）
- CI：push/PR 触发单测 + debug APK 构建，产物 `AdGuardRuleTester-apk`
