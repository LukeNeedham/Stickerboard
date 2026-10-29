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

## Theme

- There is one theme, `AppTheme { }` (`theme/AppTheme.kt`), and **there is no `MaterialTheme`**: never call it or read it. The UI reads
  `AppTheme.current`: `.colors` (`AppColors`, named by use, e.g. `accent`, `textMuted`, `placeholder`, and one `AppButtonColors` per
  button style), `.keyboardColors` (`KeyboardColors`, for the keyboard only) and `.fontFamily`. The two colour sets stay independent,
  so restyling the settings pages can never change how the keyboard looks. A new use gets a new name in `AppColors`, not a borrowed slot.
- **No shared text styles**: the theme holds colours and the typeface only. Each `Text` sets its own `fontSize`, `lineHeight` and
  `fontWeight` inline.
- **Buttons are `AppButton`** (`theme/AppButton.kt`) in one of the `AppButtonStyle`s (Primary, Secondary, Text, Destructive), each with one set
  of colours and a 48dp minimum height. Do not add a one-off button and do not use Material's `Button`, `FilledTonalButton` or `TextButton`.
- Any other Material component that is used (`Scaffold`, `TopAppBar`, `Card`, `ModalBottomSheet`, `AlertDialog`, `OutlinedTextField`, ...) is
  **given its colours explicitly** from `AppTheme.current.colors` (`appTextFieldColors()` for text fields), because nothing sets Material's
  defaults and a component left alone draws in Material's baseline light colours, wrong in dark mode.

## Shared debug library

- Logging, crash capture and the Debug / Logs / Crashes pages come from `com.lukeneedham:common`
  (source in `LukeNeedham/ci-workflows`, `common/`; docs in its `docs/using-the-library.md`). The
  maven repo is declared in `settings.gradle`; the version is in `app/build.gradle.kts`.
- `App.onCreate` calls `DebugKit.init(DebugKitConfig(enabled = BuildConfig.DEBUG))`
  first, so the non-debuggable `perf` build has the debug UI off (crashes are still saved).
- `Root` (called only by `MainActivity`) wraps the UI in `ProvideDebugSettings` (the library's single provider of debug composition
  locals). Keep it wrapped so a debug setting added to the library later reaches every screen; read them through
  `LocalDebugSettings` / `LocalAnimationSpeed`.
- Log with `Logger.d/i/w/e(msg, throwable)` from `com.lukeneedham.common.logger`; never `Log`,
  `println` or another logger. There is no app-specific crash handler or logger any more.
- Debug pages: `Route.AppDebug` is the app's own page (`AppDebugPage` in `debug/AppDebugPage.kt`, opened from the Settings debug card, debug builds
  only); it has a card that opens the shared library's page (`Route.Debug`: animation speed, links to Logs and Crashes). The library's
  page shows only the library's things and has no slot for anything else, so add app-specific debug tools to `AppDebugPage`, never to the library's page.
- Animation durations go through `LocalAnimationSpeed.current.scale(ms)` (see the page slide in
  `AppNavigation`) so the Debug page's animation speed applies.

<!-- ci-workflows:begin (managed by LukeNeedham/ci-workflows, do not edit) -->
- `.claude/skills/lukeneedham-conventions` and the callers `trigger_on_pull_request.yml`, `build_main.yml`, `delete_prereleases.yml`, `release.yml` in `.github/workflows/` are kept in sync from
  `LukeNeedham/ci-workflows` by CI, which keeps one PR titled "Sync from ci-workflows" open when they
  differ. Do not edit them here and do not copy them by hand: change them in ci-workflows. The callers
  hold no settings: every app is built the same way (see `docs/app-config.md` there).
- **Always give the user the AppHub link when work on this app completes.** As soon as a PR is open,
  paste `📦 [Open in AppHub](https://www.lukeneedham.com/apphub/pr/<repo name>/<PR number>)` into the chat
  on a line of its own, never inside a sentence (CI posts the same link as a PR comment), and put the same
  line at the very top of the PR description. Do not wait for the build and do not paste the APK link instead.
- **`README.md` is generated by CI from `readme.yml`**: never edit `README.md`, edit `readme.yml` (write it once
  from `docs/readme-template.yml` in `LukeNeedham/ci-workflows` if it is missing) and keep it accurate. Every app
  has a `LICENSE` file. The skill has the details.
- **Every page is an `XPage` plus a stateless `internal` `XPageContent`, always** (`XPage` gets its
  `XViewModel`; its route is always `Route.X`, a member of the one `sealed interface Route`; each of `XPage`,
  `XPageContent`, `XViewModel` and `XUiState` is in its own file; never `XScreen`). The README screenshots are private
  `@Preview`s in the package `screenshot` (in `src/debug`, or `src/androidDebug` in a Kotlin Multiplatform app), each owning its mock data (a page can have several,
  or none; nothing is shared); CI renders them into `docs/screenshots/`, never by hand. The skill has the details.
- **The Stop hook's git warnings** ("unpushed commit(s)", or commits "Unverified" with a `github-actions[bot]` email):
  run `git fetch origin` and check again, and do nothing else. CI pushes commits of its own to a PR branch after you
  push (the README, the screenshots), so your local copy of the remote branch is out of date and Git thinks commits are local only. Never amend,
  rebase or force-push to silence the hook, never edit the hook, and do not write about it at length.
<!-- ci-workflows:end -->
