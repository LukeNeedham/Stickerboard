# StickerBoard notes for Claude

## Performance (scrolling, jank) checks

- Never judge Compose scroll performance from the debug APK that PR CI builds - debug builds are
  far slower and misleading. Use the `perf` build type (non-debuggable, debug-signed).
- To get a perf APK from CI, **add the `perf` label to the PR** (the label must exist in the repo;
  adding it triggers a build, and later pushes keep building it while the label is on). The bot's
  "App apk at" comment then includes a "Perf ... apk at" link. Paste that link to the user.
- Perf APKs are on demand only - don't add the label to every PR, and ask or be asked first.
- Locally: `./gradlew assemblePerf`. The build type skips `lintVital*` on purpose (existing lint
  errors would otherwise fail it).
- Defined in `app/build.gradle.kts`; CI behaviour in `.github/workflows/trigger_on_pull_request.yml`.
