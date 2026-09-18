<!-- omit in toc -->
# Help Guide

Support is provided via GitHub issues, please note this is provided on a voluntary basis.

Please take a look at [Error Codes](#error-codes) first. This may provide some useful information
for why you are getting a certain error code.

- [Error Codes](#error-codes)
	- [E031](#e031)
	- [E032](#e032)
	- [E033](#e033)
	- [E034](#e034)
	- [E041](#e041)
- [Reach out](#reach-out)

## Error Codes

### E031
Some stickers failed to import (some number imported). Max stickers reached

This means that the total number of stickers that you are trying to import exceeds the
maximum number of stickers supported by StickerBoard. Try and import fewer stickers,
see [Tutorial](/documentation/tutorials)

**NOTE:** that the maximum pack size is currently **128** and the total maximum number of stickers supported
is **4096**

### E032
Some stickers failed to import (some number imported). Max pack size reached

This means that one of your sticker packs contains a number of stickers that exceeds the
maximum pack size supported by StickerBoard. Try splitting the pack up into smaller chunks,
see [Tutorial](/documentation/tutorials)

**NOTE:** that the maximum pack size is currently **128** and the total maximum number of stickers supported
is **4096**

### E033
Some stickers failed to import (some number imported). Unsupported formats found

This could be for a few reasons, perhaps you have a non sticker file in the sticker directory such
as a document in the wrong place. Alternatively this may result in a seemingly valid sticker not being
imported. Chances are that the sticker is not in a [supported format](/README.md#features).

### E034

Reloading stickers failed, try choosing a sticker source directory

### E041
Unexpected IOException when converting sticker

This is an unexpected error and happens when creating a compat-sticker to send to the application.
Please open an issue and provide as much information as you can. E.g. Android Version, phone
manufacturer, app you are trying to send the sticker in

## Reach out

Support is provided via GitHub issues, please note this is provided on a voluntary basis.

You are therefore not entitled to free customer service (that is not to say that contributions/
issues and questions are not welcome - more reminding you that project maintainers are well
within their rights to prioritize other issues).

To open a new issue, head to https://github.com/LukeNeedham/Stickerboard/issues/new

**NOTE:** you will need to have a GitHub account to open issues (create one at https://github.com/signup)
