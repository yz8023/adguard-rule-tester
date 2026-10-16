# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Entries

[Project Knowledge Summary]
- Date: 2026-10-16
- Context: Agent 搭建本地构建环境并验证 adguard-rule-tester 单测/打包
- Category: Build Methods / Environment Configuration
- Instructions:
  - 环境默认无 JDK/Android SDK；已安装 OpenJDK 17（/usr/lib/jvm/java-17-openjdk-amd64）与 Android SDK（/opt/android-sdk，含 platforms;android-36、build-tools;35.0.0、cmdline-tools/latest）。
  - 构建命令模板：`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-daemon`（在 /workspace 下执行）。
  - Gradle 命令必须经 background_terminal_create 后台执行（内存约 40% 限额，全量构建约 3 分钟）。
  - 推送含 `.github/workflows/*` 变更的提交会被 GitHub 拒绝（当前 PAT 无 workflow scope）；此类提交只能留在本地（如 b9369dc），需用户用带 workflow scope 的凭据推送。
