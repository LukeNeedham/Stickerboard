
<img src="metadata/en-US/images/featureGraphic.png" alt="Feature Graphic" width="">

[![GitHub top language](https://img.shields.io/github/languages/top/LukeNeedham/Stickerboard.svg?style=for-the-badge&cacheSeconds=28800)](../../)
[![Issues](https://img.shields.io/github/issues/LukeNeedham/Stickerboard.svg?style=for-the-badge&cacheSeconds=28800)](../../issues)
[![License](https://img.shields.io/github/license/LukeNeedham/Stickerboard.svg?style=for-the-badge&cacheSeconds=28800)](/LICENSE.md)
[![Commit activity](https://img.shields.io/github/commit-activity/m/LukeNeedham/Stickerboard.svg?style=for-the-badge&cacheSeconds=28800)](../../commits/main)
[![Last commit](https://img.shields.io/github/last-commit/LukeNeedham/Stickerboard.svg?style=for-the-badge&cacheSeconds=28800)](../../commits/main)
[![GitHub all releases](https://img.shields.io/github/downloads/LukeNeedham/Stickerboard/total?style=for-the-badge&cacheSeconds=28800)](../../releases)

<!-- omit in toc -->
# StickerBoard

StickerBoard is an Android sticker keyboard application, specifically designed for sharing a wide variety of custom stickers in supported messaging apps. This project is a fork of [EweSticker](https://github.com/FredHappyface/Android.EweSticker), which itself draws inspiration from the uSticker project and is a fork of the woosticker repository.

- [Features](#features)
- [Dark Theme](#dark-theme)
- [Light Theme](#light-theme)
- [Documentation](#documentation)
- [Installation](#installation)
- [Application compatibility](#application-compatibility)
	- [Working](#working)
	- [Known Issues/ Workarounds](#known-issues-workarounds)
- [Gradle tasks](#gradle-tasks)
- [Kotlin and Android Version](#kotlin-and-android-version)
- [Building From Source](#building-from-source)
	- [Git Clone](#git-clone)
		- [Using The Command Line](#using-the-command-line)
		- [Using GitHub Desktop](#using-github-desktop)
	- [(or) Download Zip File](#or-download-zip-file)
	- [Download Android Studio](#download-android-studio)
- [License](#license)
- [Credits](#credits)

## Features

The StickerBoard Android app offers the following key features to enhance your messaging experience:

- **Wide Range of Custom Stickers Supported**: StickerBoard supports a diverse set of sticker formats, ensuring that users can share their creativity in various ways. Supported formats include image/gif, image/png, image/webp, image/jpeg, image/heif, video/3gpp, video/mp4, video/x-matroska, and video/webm.

- **Seamless Sticker Sharing**: Easily send stickers within messaging apps that support custom media sharing using image/png as a fallback.

- **Customizable Scrolling**: Use either vertical or horizontal scrolling to navigate through your sticker collection.

- **Display Options**: adjust the number of rows and the sticker preview size, tailoring the viewing experience to your liking and device screen dimensions.

- **Search your Sticker library**: Use a qwerty keyboard to search stickers by file name to ease discovery.

- **Integration with System Theme**: StickerBoard seamlessly integrates with the system's theme, ensuring that the app's appearance aligns with your device-wide design choices.

- **Sticker Preview on Long Press**: To facilitate sticker selection, you can long-press on a sticker to reveal a preview to help you quickly decide which sticker to share without the need to open the sticker collection separately.

StickerBoard brings a wide range of customization options, diverse format support, and integration with messaging apps. Whether users are sharing static images, animated GIFs, or even short videos, the app aims to provide an engaging and expressive way to communicate using custom stickers.

## Dark Theme

<p>
<img src="metadata/en-US/images/phoneScreenshots/dark-1.png" alt="Dark 1" width="200">
<img src="metadata/en-US/images/phoneScreenshots/dark-2.png" alt="Dark 2" width="200">
<img src="metadata/en-US/images/phoneScreenshots/dark-3.png" alt="Dark 3" width="200">
<img src="metadata/en-US/images/phoneScreenshots/dark-4.png" alt="Dark 4" width="200">
<img src="metadata/en-US/images/phoneScreenshots/dark-5.png" alt="Dark 5" width="200">
<img src="metadata/en-US/images/phoneScreenshots/dark-6.png" alt="Dark 6" width="200">
</p>

## Light Theme

<p>
<img src="metadata/en-US/images/phoneScreenshots/light-1.png" alt="Light 1" width="200">
<img src="metadata/en-US/images/phoneScreenshots/light-2.png" alt="Light 2" width="200">
<img src="metadata/en-US/images/phoneScreenshots/light-3.png" alt="Light 3" width="200">
</p>

## Documentation

A high-level overview of how the documentation is organized will help you know
where to look for certain things:

- [Tutorials](/documentation/tutorials) take you by the hand through a series of steps to get
  started using the software. Start here if you’re new.
- The [Help](/documentation/help) guide provides a starting point and outlines common issues that you
  may have.

## Installation

Every pull request build publishes a debug APK as a GitHub Release. Grab the latest one from
the Releases page:

[<img src="readme-assets/badges/badge_github.png" alt="Get it on GitHub" height="80">](../../releases)

## Application compatibility

### Working

I've tested StickerBoard with the various apps I have installed. Please note that it will likely be
compatible with other applications as well.

| app              | id                                | webp | animated |
| ---------------- | --------------------------------- | ---- | -------- |
| Android Messages | com.google.android.apps.messaging | ✔    | ✔        |
| WhatsApp         | com.whatsapp                      | ✔    | ✖        |
| Telegram         | org.telegram.messenger            | ✔    | ✖        |
| Signal           | org.thoughtcrime.securesms        | ✔    | ✖        |
| Discord          | com.discord                       | ✔    | ✔        |
| Fair Email       | eu.faircode.email                 | ✔    | ✔        |
| Element          | im.vector.app                     | ✔    | ✔        |
| Moshidon         | org.joinmastodon.android.moshinda | ✔    | ✔        |
| Google Keep      | com.google.android.keep           | ✔    | ✖        |
| Twitter          | com.twitter.android               | ✔    | ✖        |

### Known Issues/ Workarounds

| app         | id                                        | Workaround                                             |
| ----------- | ----------------------------------------- | ------------------------------------------------------ |
| WeChat      | com.tencent.mm                            | \[Unknown]                                             |
| Notion      | notion.id                                 | Displays the share sheet, can upload to a new note     |
| Gmail       | com.google.android.gm                     | Displays the share sheet, added to a new email         |
| Reddit      | com.reddit.frontpage                      | Displays the share sheet, added to a new post          |
| Google Docs | com.google.android.apps.docs.editors.docs | Displays the share sheet, cannot add to google docs :( |

<!-- omit in toc -->
### Build from Source

Follow the steps in the [Building from Source](#building-from-source) section.

## Gradle tasks

- ktlintCheck (`gradlew ktlintCheck`): run ktlint over the codebase

## Kotlin and Android Version

This app has been written in Kotlin 2.1 with the Android Studio IDE.

- The target SDK version is 35 (Android 15)
- The minimum SDK version is 26 (Android 8 Oreo)

## Building From Source

1. Download or clone this GitHub repository
2. (If downloaded) Extract the zip archive
3. In Android Studio click File > Open and then navigate to the project file
(Android studio defaults to the directory of the last opened file)

### Git Clone

#### Using The Command Line

1. Press the Clone or download button in the top right
2. Copy the URL (link)
3. Open the command line and change directory to where you wish to
clone to
4. Type 'git clone' followed by URL in step 2

	```bash
	git clone https://github.com/LukeNeedham/Stickerboard
	```

More information can be found at
https://help.github.com/en/articles/cloning-a-repository

#### Using GitHub Desktop

1. Press the Clone or download button in the top right
2. Click open in desktop
3. Choose the path for where you want and click Clone

More information can be found at
https://help.github.com/en/desktop/contributing-to-projects/cloning-a-repository-from-github-to-github-desktop

### (or) Download Zip File

1. Download this GitHub repository
2. Extract the zip archive
3. Copy/ move to the desired location

### Download Android Studio

Download the Android Studio IDE from <https://developer.android.com/studio/>.
For Windows, double click the downloaded .exe file and follow the instructions
provided by the installer - it will download the Android emulator and the
Android SDK. Additional information can be found at
<https://developer.android.com/studio/install>

## License

MIT License
(See the [LICENSE](/LICENSE.md) for more information.)

## Credits

StickerBoard is a fork of [EweSticker](https://github.com/FredHappyface/Android.EweSticker) by
FredHappyface, which draws inspiration from the uSticker project and is itself a fork of the
woosticker repository by Randy Zhou.
