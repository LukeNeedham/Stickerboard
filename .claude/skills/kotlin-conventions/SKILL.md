---
name: kotlin-conventions
description: Kotlin code conventions for StickerBoard. Use when writing or refactoring Kotlin, especially when adding helper or utility functions.
---

# Kotlin conventions

## No top-level functions (except extensions)

Do not write top-level `fun`s in Kotlin files. The exceptions are:

- Extension functions (`fun File.foo()`, `fun Modifier.bar()`).
- `@Composable` functions. These are conventionally top-level in Compose, and wrapping them in objects would fight that idiom. This exception is deliberate, not an oversight.

Top-level constants and `private val`s are fine.

## Utils live in an object named after the file

Put helper and utility functions in a Kotlin `object` whose name is **exactly the file's name**
(`StickerNames.kt` contains `object StickerNames`). The name defines the scope and domain of the
utils in it, so pick a specific one: `StickerNames`, `KeyboardStatus`, `ShareIntents`. Do not use
catch-alls such as `Utils` or `Helpers`.

- Name functions for what they do within that scope, without repeating it: `KeyboardStatus.isEnabled(context)`, not `KeyboardStatus.isKeyboardEnabled(context)`.
- Private helpers go in the same object, marked `private`.
- If a file already holds a class, don't put an object with the same name next to it. Put the utilities in their own file and object.
- Before creating a new object, check whether an existing one covers the same domain. Related utils belong together (e.g. `SetupStatus` holds both the keyboard-enabled and onboarding-complete checks). Keep one object per domain, not one per function.
- Add new utils to the existing object for their domain if one exists, rather than creating a near-duplicate.

```kotlin
// StickerNames.kt
object StickerNames {
	fun prettifyPackName(name: String): String = ...
}
```
