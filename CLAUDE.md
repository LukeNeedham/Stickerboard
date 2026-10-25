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
- The APK is built to the standard path every app uses, `app/build/outputs/apk/debug/app-debug.apk` (see
  `archivesBaseName` in `app/build.gradle.kts`): the shared workflow expects exactly that.

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
- Debug pages: `Route.AppDebug` is the app's own page (`AppDebugPage` in `debug/AppDebugPage.kt`, opened from the Settings debug card, debug builds
  only); it has a card that opens the shared library's page (`Route.Debug`: animation speed, links to Logs and Crashes). The library's
  page shows only the library's things and has no slot for anything else, so add app-specific debug tools to `AppDebugPage`, never to the library's page.
- Animation durations go through `LocalAnimationSpeed.current.scale(ms)` (see the page slide in
  `StickerBoardApp`) so the Debug page's animation speed applies.

<!-- ci-workflows:begin (managed by LukeNeedham/ci-workflows, do not edit) -->
- `.claude/skills/lukeneedham-conventions` and the callers `trigger_on_pull_request.yml`, `build_main.yml`, `delete_prereleases.yml`, `release.yml` in `.github/workflows/` are kept in sync from
  `LukeNeedham/ci-workflows` by CI, which keeps one PR titled "Sync from ci-workflows" open when they
  differ. Do not edit them here and do not copy them by hand: change them in ci-workflows. The callers
  hold no settings: every app is built the same way (see `docs/app-config.md` there).
- **Always give the user the AppHub link when work on this app completes.** As soon as a PR is open,
  paste `https://www.lukeneedham.com/apphub/pr/<repo name>/<PR number>` into the chat (CI posts the
  same link as a PR comment). Do not wait for the build and do not paste the APK link instead.
<!-- ci-workflows:end -->
