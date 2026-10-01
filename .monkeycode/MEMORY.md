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

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-10-01
- Context: Discovered by Agent while building and publishing the MT论坛 Android client at /workspace/mtLuntan
- Category: Build Methods / Environment Configuration
- Instructions:
  - This project has no gradlew wrapper. Use the system Gradle 8.5 at /tmp/opencode/gradle-8.5/bin/gradle with `--no-daemon`.
  - Before any build, export `ANDROID_HOME=/opt/android-sdk` and `JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))`.
  - Compile: `gradle --no-daemon :app:compileDebugKotlin`; test: `gradle --no-daemon :app:testDebugUnitTest`; release: `gradle --no-daemon :app:assembleRelease` → APK at app/build/outputs/apk/release/app-release.apk. Release build takes ~4-5 min; run it in a background terminal.
  - All builds/tests that call java/gradle must run through the managed background terminal (background_terminal_create) with a timeout; do not use the plain bash tool for them.

[Project Knowledge Summary]
- Date: 2026-10-01
- Context: Discovered by Agent while porting features and fixing "内容无法查看" in the MT论坛 client
- Category: Troubleshooting & Debugging
- Instructions:
  - The reference project (battle-tested, v3.8) is cloned at /tmp/opencode/mt-lun-tan. When any parser or network behaviour is in doubt, consult reference sources there: network/ForumParser.java (proven Comiis selectors), network/HttpClient.java (UA, cookie sync), session/*, ai/*.
  - Parser invariant: never trust a single selector; always keep a fallback. Titles via ownText then text; tid regex `thread-(\d+)` and query `tid=(\d+)` both supported.
  - The site's ESA WAF returns 403 empty for datacenter IPs even with a correct acw cookie; live e2e validation is only reliable from a residential/real-device IP.
