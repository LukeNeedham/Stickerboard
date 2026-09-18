<!-- omit in toc -->
# Tutorial

See below for a step-by-step tutorial on how to use StickerBoard with your existing
sticker collection.

- [Step 1 - Create Sticker Directory (and transfer to device)](#step-1---create-sticker-directory-and-transfer-to-device)
- [Step 2 - Download StickerBoard](#step-2---download-stickerboard)
- [Step 3 - Activate the keyboard](#step-3---activate-the-keyboard)
- [Step 4 - Select Directory with StickerBoard](#step-4---select-directory-with-stickerboard)
- [Step 5 - Send Stickers in your favourite apps](#step-5---send-stickers-in-your-favourite-apps)

## Step 1 - Create Sticker Directory (and transfer to device)

<img src="assets/make-packs.png" alt="VSCode showing an example sticker pack structure" width="600">

The sticker directory has the following structure:

```none
/root
     /sticker-pack-name-1
                         /sticker-1
                         /sticker-2
     /sticker-pack-name-2
                         /sticker-1
                         /sticker-2
```

Then transfer this to your phone/ device. Plugging this into a PC is a pretty
convenient way to do this.

**NOTE:** that the maximum pack size is currently **128** and the total maximum number of stickers supported
is **4096**

## Step 2 - Download StickerBoard

Every pull request build publishes a debug APK as a GitHub Release. Grab the latest one from
the Releases page:

[<img src="/readme-assets/badges/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/LukeNeedham/Stickerboard/releases)

## Step 3 - Activate the keyboard

Click the `Launch Settings` button

<img src="assets/enable-import.png" alt="StickerBoard UI with 'Launch Settings' button" width="300">

Toggle StickerBoard on

<img src="assets/sys-enable.png" alt="System UI 'On Screen Keyboard' with StickerBoard toggle" width="300">

## Step 4 - Select Directory with StickerBoard

Click the `Choose sticker source directory` button

<img src="assets/enable-import.png" alt="StickerBoard UI with 'Choose sticker source directory' button" width="300">

Select the sticker directory created in step 1

<img src="assets/sys-import.png" alt="System UI file chooser" width="300">

## Step 5 - Send Stickers in your favourite apps

Tap the keyboard switcher icon and select StickerBoard

<img src="assets/sys-switcher.png" alt="System UI keyboard switcher with select StickerBoard" width="300">

Find and send a sticker of your choosing

<img src="assets/few.png" alt="StickerBoard UI showing horizontal layout 5 wide x 3 stickers deep" width="300">

**Note:** That you can configure StickerBoard to:

- enable/disable the back button
- use the vertical scroll layout
- restore the previous keyboard when the keyboard is closed (through tapping away from a text input)
- enable swipe between sticker packs (perpendicular to scroll direction, i.e. vertical swipe if not using the vertical layout)
- number of rows (between 2 and 6)
- icon size if not in vertical scroll layout

<img src="assets/configure.png" alt="StickerBoard UI showing configuration options" width="300">

<img src="assets/many.png" alt="StickerBoard UI showing horizontal layout 5 wide x 6 stickers deep" width="300">
