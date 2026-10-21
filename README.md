# AdGuard 规则测试器（AdGuardRuleTester）

独立 Android 应用（Java，minSdk 29）：**测试 AdGuard 实际拦截效果，把未被拦截的广告/跟踪域名
自动生成 AdGuard 补漏规则。**

## 功能

1. **解析测试站点列表**（在线抓取；内置 Cloudflare Turnstile / 5秒盾自动解算器，无感通过安全验证与限流保护）：
   - `https://paileactivist.github.io/toolz/adblock.html`（广告/分析/错误跟踪/社交/混合 + OEM）
   - `https://mobileproxy.space/ch/adblock-test.html`（广告/分析/跟踪/错误监控、加密货币挖矿、社交、OEM、Cookie、联盟、A/B、邮件、视频等 12 类，支持 Cloudflare 自动过盾）
   - `https://adblock.turtlecute.org/`（开源测试页；广告网络、分析、社交跟踪和 OEM）
   - `https://superadblocktest.com/`（广告、分析、指纹、社交、OEM、Cookie、联盟、视频等多类主机）
   - `https://getblockify.com/adblock-test`（页面公开列出 80+ 测试网络主机）
   - 任意自定义页面 URL（通用解析器兜底）

   Turtlecute / Super Adblock Test / GetBlockify 默认不勾选，可按需启用。针对受 Cloudflare 保护的测试站（如 mobileproxy.space），应用内置自动化 WebView 解算器，无感通过安全验证获取目标规则列表。
2. **并发拦截测试**：最多 10 个目标并行探测；较短超时减少卡顿，失败时可用公共 DoH（dns.google / cloudflare-dns.com）区分疑似拦截与域名失效。关闭 DoH 可更快，但会有更多“未知”结果。
3. **实时结果**：测试中即时更新进度和统计；未拦截目标置顶，列表可上下滑动。点击任一结果行或“复制链接”可复制该目标 URL；每个内置站点旁也有复制入口，便于手动打开。
4. **生成 AdGuard 规则**：对未拦截域名输出 `||域名^`（可选 `$important`、按分类分组注释、含统计头），一键复制 / 分享 / 保存为 `.txt`，导入 AdGuard「自定义过滤规则」即可生效。

界面用原生 View 独立实现了柔和渐变、圆角半透明风格卡片与浅/深色配色，参考 [yz8023/ui](https://github.com/yz8023/ui) 的玻璃卡片和层级布局；未引入 Compose/backdrop 大型依赖。

## 使用

```bash
./gradlew :app:assembleDebug        # 或 Android Studio 直接 Run
# APK: app/build/outputs/apk/debug/app-debug.apk
```

当前版本 `1.4.0`（versionCode `5`）。正式发布见 GitHub Releases：签名 release APK（R8 裁剪 + 资源收缩，约 1.2 MB）与源码归档（tag 自动附带 zip/tar.gz）。

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

- mobileproxy.space：内联 `AdBlockTest` 包含 12 类测试主机；适配 Cloudflare 客户端安全验证保护，支持后台无感过盾与交互式验证降级，成功提取 450+ 规则测试目标。
- paileactivist `resources/adblock/data.js`：5 类主列表 + 5 家 OEM 列表；单测覆盖重复项、尾随空格归一化和 127 个唯一主机。
- Turtlecute：真实页面引用 `/js/index.js`，该脚本通过 `JSON.parse('…')` 保存分类主机数据；单测断言域名、分类和厂商归属。
- Super Adblock Test：解析 `/adblock.js` 中的 `HOST_TESTS`，支持 ``list(`…`,)`` 模板列表和对象尾随逗号；只抓测试数据脚本，避免分析 Bootstrap/统计脚本。
- GetBlockify：从 SSR `itemProp="name"` 语义列表抽取主机，并根据最近的标题分类；不下载 Next.js bundle，也过滤 YouTube / Googlevideo / Spotify 等内容根域。
- 目前 JVM 单测共 42 项，覆盖上述来源、Cloudflare 安全验证识别与过盾解算、URL 纠错、AdGuard 规则生成、去重、探测分类（MockWebServer）与 DoH 判定/缓存语义。

## 1.4.0 变更（Cloudflare 自动过盾与网络层升级）

- **Cloudflare 自动过盾**：内置 Chromium WebView 挑战解算器，支持 Cloudflare Turnstile、JS 5秒盾及安全验证页。请求遇到 CF 保护时自动调度解算器完成挑战，提取已渲染的测试目标与 `cf_clearance` 会话凭据，并同步至网络引擎复用。
- **交互式验证降级**：若遇到需人工点选的人机验证卡片，应用自动浮出内嵌验证弹窗供快速点选，验证通过后自动关闭弹窗并继续解析。
- **请求头拟真与会话记忆**：内置移动端 Chrome 标准请求头（`Sec-CH-UA`、`Accept-Language` 等）与主机 Cookie 记忆，显著降低触发风控概率。
- **稳定与性能**：完善生命周期与内存管理，WebView 使用后及时清理与销毁；单测全面覆盖 Cloudflare 挑战识别与解算器触发。

## 1.3.0 变更（稳定性与合规修复）

- **防远程崩溃**：JS 字面量解析加嵌套深度上限（64 层），恶意页面构造的超深嵌套不再引发 `StackOverflowError` 崩溃。
- **取消语义**：中途取消的探测目标标记为“已取消”，即使勾选“无法验证也写入规则”也不会把未测过的域名写进规则。
- **DoH 缓存**：`ERROR` 不再缓存，DoH 一次抖动不会让域名整个会话都停留“无法验证”；确定结论（存活/NXDOMAIN/无记录）仍缓存。
- **探测提速**：目标探测从 GET 改为 HEAD，判定口径不变（任意 HTTP 状态码 = 未拦截），大幅减少流量。
- **超时语义**：连接超时明确标注“网络慢或被静默丢弃，无法判定”，与“连接被拒绝”区分，减少把慢站误读为已拦截。
- **旋转屏恢复**：引擎/线程池/目标/结果迁至进程级运行时，横竖屏切换后界面自动恢复测试进度，后台任务不再泄漏 Activity。
- **分类索引**：通用解析的分类定位从“每个域名全文重扫”改为一次预扫描 + 二分查找，大页面解析从 O(hosts×textLen) 降为 O(textLen)。
- **深色适配**：结果状态色改引用主题资源，深色模式下红/绿对比度达标。
- **平台合规**：targetSdk 34 → 36（含 edge-to-edge insets 适配），`allowBackup` 关闭，CI 增加 release 构建校验。

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
