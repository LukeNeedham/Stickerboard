<!-- omit in toc -->
# Tutorial

See below for a step-by-step tutorial on how to use StickerBoard with your existing
sticker collection.

- [Step 1 - Create Sticker Directory (and transfer to device)](#step-1---create-sticker-directory-and-transfer-to-device)
- [Step 2 - Download StickerBoard](#step-2---download-stickerboard)
- [Step 3 - Run the setup flow](#step-3---run-the-setup-flow)
- [Step 4 - Send Stickers in your favourite apps](#step-4---send-stickers-in-your-favourite-apps)
- [Step 5 - Get more out of the keyboard](#step-5---get-more-out-of-the-keyboard)

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

## Step 3 - Run the setup flow

The first time you open StickerBoard, it walks you through two things, one screen at a time:

1. **Enable the keyboard** - tap the button to open your device's keyboard settings, then switch
   StickerBoard on there. The app shows a live "enabled"/"not enabled" status as you do this, so
   you'll know as soon as it's picked up.
2. **Choose your sticker folder** - tap the button and select the sticker directory you created
   in Step 1 using the system file/folder picker. StickerBoard copies its contents in and shows a
   progress indicator, followed by how many stickers were loaded.

You can't move on to the next step until the current one is done, so you can't accidentally skip
past enabling the keyboard or choosing a folder.

## Step 4 - Send Stickers in your favourite apps

Tap the keyboard switcher icon in any text field and select StickerBoard from the list of
keyboards, then tap a sticker to send it.

## Step 5 - Get more out of the keyboard

A few gestures make the keyboard quicker to use once you're set up:

- **Pinch or spread** on the sticker grid to change how many stickers are shown per row.
- **Drag the handle** at the top of the keyboard up or down to resize it - your chosen height is
  remembered.
- **Tap the search icon** to switch to a dedicated search view with its own compact keyboard,
  which filters stickers by file or pack name as you type.
- **Long-press a sticker** to see an enlarged preview before deciding whether to send it.
- **Pull down** on the sticker grid to re-scan your sticker folder for anything added, removed,
  or changed since it was last imported.
- **Tap a pack icon** in the row above the grid to jump straight to that pack's section.

You can also revisit your source folder at any time from the **Stickers** page in the app: open
StickerBoard's settings from the keyboard's own settings icon, then **View Stickers**, to browse
every pack, add photos from your device's gallery into a pack, or change/reload your source
folder.
