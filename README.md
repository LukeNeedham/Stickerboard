
# StickerBoard

StickerBoard is an Android keyboard for sending your own stickers, GIFs and short videos in any
app - built from a folder of images you choose yourself. It started life as a fork of
[EweSticker](https://github.com/FredHappyface/Android.EweSticker).

## Features

- **Send stickers anywhere**: StickerBoard works as a normal Android keyboard, so it can send stickers directly into any app's text field. Where an app doesn't support that directly, it automatically falls back to sending a PNG version, then to the system share sheet, so a sticker almost always gets through.

- **Wide format support**: gif, png, webp, jpeg, heif and svg images, plus 3gpp, mp4, mkv and webm video.

- **Your own sticker folder**: point StickerBoard at any folder on your device (chosen with the system folder picker) and it copies the contents in, with every sub-folder becoming a sticker pack. Sub-folders can be nested arbitrarily deep - a pack's name is built from the chain of folder names leading to it, e.g. `A/B/C` becomes pack "A-B-C". Supports up to 4096 stickers in total, 128 per pack.

- **Stays in sync with your folder**: pull down on the keyboard, or on the in-app Stickers page, to re-scan your source folder for changes - only what's actually changed gets re-copied.

- **Sticker gallery**: a dedicated screen in the app to browse every pack, see stats on your sticker collection, jump to or change your source folder, and add photos straight from your device's gallery into any pack.

- **Recently used**: stickers you've sent recently automatically appear in their own section at the top of the keyboard.

- **Built-in search**: switch to a dedicated search view, with its own compact keyboard, to find stickers by file or pack name as you type.

- **Pinch to zoom**: pinch or spread on the keyboard to change how many stickers are shown per row.

- **Resizable keyboard**: drag the handle at the top of the keyboard to make it taller or shorter - your preferred height is remembered.

- **Long-press preview**: long-press any sticker to see an enlarged preview before sending it.

- **Guided first-run setup**: a short onboarding flow walks new users through enabling the keyboard and choosing a sticker folder, with live progress feedback at every step.

- **Try it out**: a test field on the Settings screen lets you send a sticker and see the result immediately, without needing to leave the app or find another text field.

- **Translated UI**: available in Arabic, Bengali, German, Spanish, French, Hindi, Indonesian, Japanese, Korean, Portuguese, Russian, Urdu, and both Simplified and Traditional Chinese, alongside English.

- **Follows your system theme**: a Material 3 UI that adapts to your device's light or dark theme.

## Documentation

A high-level overview of how the documentation is organized will help you know
where to look for certain things:

- [Tutorials](/documentation/tutorials) take you by the hand through a series of steps to get
  started using the software. Start here if you're new.
- The [Help](/documentation/help) guide provides a starting point and outlines common issues that you
  may have.

## Installation

Every pull request build publishes a debug APK as a GitHub Release. Grab the latest one from
the Releases page:

[<img src="readme-assets/badges/badge_github.png" alt="Get it on GitHub" height="80">](../../releases)



## Development

### Gradle tasks

- `./gradlew assembleDebug`: build a debug APK
- `./gradlew assemblePerf`: build a non-debuggable, debug-signed APK for judging performance (see
  [Performance testing](#performance-testing))
- `./gradlew ktlintCheck`: run ktlint over the codebase
- `./gradlew ktlintFormat`: auto-format the codebase with ktlint

### Performance testing

Debug builds are much slower than release builds at things like scrolling Compose lists, so don't
judge scroll smoothness from a debug APK. The `perf` build type is non-debuggable (and signed with
the debug key, so it installs over a debug build), which makes it representative.

Pull request CI builds only the debug APK. To get a perf APK, build it locally with
`./gradlew assemblePerf`.

The perf build skips `lintVital*`, which would otherwise fail it on existing lint errors.

### Code style

Code style is enforced with [ktlint](https://github.com/JLLeitschuh/ktlint-gradle), and
[pre-commit](https://pre-commit.com/) hooks are configured in `.pre-commit-config.yaml` to catch
common issues (trailing whitespace, oversized images, merge conflict markers, etc.) before a
commit is made.

