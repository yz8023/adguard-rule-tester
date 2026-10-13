# AI 协作规则（adguard-rule-tester 仓库）

1. **成果必须落地**：每次改动都要提交；不删除 PR、不改仓库可见性、不 `push --force`。
2. **单元测试跑在 JVM 上**（不触碰 android.*）：新增字符串/解析/规则生成逻辑必须在
   `app/src/test` 配单测。
3. **解析逻辑以实测证据为准**：测试站点（paileactivist/toolz、mobileproxy.space）结构变化时，
   先抓取真实页面/脚本更新 `app/src/test/resources` 夹具并写断言，再改 `SourceParser`。
4. 签名材料（`*.jks` / `app/signing.properties`）永不入库。
