# StickerBoard notes for Claude

## Performance (scrolling, jank) checks

- Never judge Compose scroll performance from the debug APK that PR CI builds - debug builds are
  far slower and misleading. Use the `perf` build type (non-debuggable, debug-signed).
- PR CI only builds the debug APK. Build the perf APK locally with `./gradlew assemblePerf`; the
  build type skips `lintVital*` on purpose (existing lint errors would otherwise fail it).
- Defined in `app/build.gradle.kts`.

## CI

- `.github/workflows/trigger_on_pull_request.yml` calls the shared `android_pr.yml` from
  `LukeNeedham/ci-workflows` (`@main`): it builds the debug APK on PRs, publishes it as a GitHub
  pre-release, comments the download link, and deletes the PR's pre-releases when the PR is closed.
  Its logic lives in the shared repo, so make changes there.
- `.github/workflows/delete_prereleases.yml` (manual) calls the shared `delete_prereleases.yml`, which
  deletes every pre-release and tag. Use it to clear old PR builds.
- The APK is built to a fixed path, `app/build/outputs/apk/debug/app-debug.apk` (see
  `archivesBaseName` in `app/build.gradle.kts`), because the shared workflow needs a fixed `apk-path`.

## Shared debug library

- Logging, crash capture and the Debug / Logs / Crashes pages come from `com.lukeneedham:common`
  (source in `LukeNeedham/ci-workflows`, `common/`; docs in its `docs/using-the-library.md`). The
  maven repo is declared in `settings.gradle`; the version is in `app/build.gradle.kts`.
- `StickerBoardApplication.onCreate` calls `DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))`
  first, so the non-debuggable `perf` build has the debug UI off (crashes are still saved).
- `StickerBoardApp` wraps the UI in `ProvideDebugSettings` (the library's single provider of debug composition
  locals). Keep it wrapped so a debug setting added to the library later reaches every screen; read them through
  `LocalDebugSettings` / `LocalAnimationSpeed`.
- Log with `Logger.d/i/w/e(msg, throwable)` from `com.lukeneedham.common.logger`; never `Log`,
  `println` or another logger. There is no app-specific crash handler or logger any more.
- Debug pages: `Route.Debug` / `Logs` / `Crashes` in `StickerBoardApp`, opened from the Settings
  debug card (debug builds only). App-specific debug tools go in `DebugRoute`'s `extraContent`.
- Animation durations go through `LocalAnimationSpeed.current.scale(ms)` (see the page slide in
  `StickerBoardApp`) so the Debug page's animation speed applies.
