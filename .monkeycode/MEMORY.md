# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.

## Entries

[Build & Release Chain]
- Date: 2026-10-23
- Context: Discovered while iterating the zhenxun_bot_android_ui app
- Category: Build & Compilation
- Instructions:
  - Release build: `cd /workspace && ANDROID_HOME=/tmp/opencode/android-sdk GRADLE_OPTS="-Xmx2600m -Dorg.gradle.daemon=false" ./gradlew assembleRelease -x lint` (run in background terminal with memory_percent 60 / cpu_percent 200)
  - Verify APK signature with `/tmp/opencode/android-sdk/build-tools/34.0.0/apksigner verify --print-certs`; expected cert SHA-256: a0b67e82144aa6a3232ddb94e793e4c806439d224ad69d915c594eae9deeeae5
  - Release flow: commit to master -> tag `r<short-sha>` -> push both -> CI publishes GitHub release; verify assets at `https://github.com/huliaiya/zhenxun_bot_android_ui/releases/expanded_assets/r<sha>` (need `curl -4 --http1.1 -A 'Mozilla/5.0'`, takes ~5 min after push)
  - versionName equals git short hash, so the locally built APK is named with the pre-commit hash; the CI release APK uses the post-commit hash

[Local Zhenxun Server]
- Date: 2026-10-23
- Context: Discovered while verifying webui API shapes
- Category: Troubleshooting & Debugging
- Instructions:
  - Repo clone at /tmp/opencode/zxdeploy (shallow). Start server from the REPO ROOT (not the inner zhenxun/ package dir): `nohup env PORT=8080 uv run zx run > /tmp/opencode/zx_run<N>.log 2>&1 &`
  - WebUI login: POST /zhenxun/api/login form `username=admin&password=zhenxun123` -> data.access_token (Bearer, ~30 min)
  - To fake a bot for API tests: INSERT into bot_console (bot_id,status,block_plugins,block_tasks,available_plugins,available_tasks) VALUES ('test_bot_1',1,'','','','')
  - Kill the server by exact PID when done (ps aux | grep "zx run")

[User Workflow]
- Date: 2026-10-23
- Context: Established across sessions
- Category: Workflow & Collaboration
- Instructions:
  - Each iteration: implement -> local build+sign verify -> push master + tag r<sha> -> verify expanded_assets -> report
  - Communicate in Chinese; frosted-glass (毛玻璃) style throughout the app
  - External links open in in-app WebViewActivity; user reports issues via screenshots

[Commit Message Convention]
- Date: 2026-10-24
- Context: User asked to keep commit messages free of monkeycode attribution
- Category: Workflow & Collaboration
- Instructions:
  - Do not include monkeycode co-author trailers or signatures in commit messages
  - The repo had .git/hooks/prepare-commit-msg adding Co-authored-by from coauthor.* git config; config entries were removed so no trailer is appended
