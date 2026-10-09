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
- The APK is built to a fixed path, `app/build/outputs/apk/debug/app-debug.apk` (see
  `archivesBaseName` in `app/build.gradle.kts`), because the shared workflow needs a fixed `apk-path`.
