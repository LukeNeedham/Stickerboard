package com.lukeneedham.stickerboard

import android.content.Context
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.stickerboard.crash.CrashDetailRoute
import com.lukeneedham.stickerboard.crash.CrashesRoute
import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.debug.DebugRoute
import com.lukeneedham.stickerboard.gallery.GalleryRoute
import com.lukeneedham.stickerboard.navigation.Route
import com.lukeneedham.stickerboard.onboarding.OnboardingRoute
import com.lukeneedham.stickerboard.settings.SettingsRoute
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme

private val SLIDE_SPEC = tween<IntOffset>(durationMillis = 300)

/** Incoming page slides in from the right, outgoing page slides out to the left. */
private fun <T : NavKey> AnimatedContentTransitionScope<Scene<T>>.slideForward() =
	slideInHorizontally(SLIDE_SPEC) { it } togetherWith slideOutHorizontally(SLIDE_SPEC) { -it }

/** Incoming page slides in from the left, outgoing page slides out to the right. */
private fun <T : NavKey> AnimatedContentTransitionScope<Scene<T>>.slideBackward() =
	slideInHorizontally(SLIDE_SPEC) { -it } togetherWith slideOutHorizontally(SLIDE_SPEC) { it }

/**
 * The single activity's nav host - decides whether to land on onboarding or settings, and wires
 * every page's navigation callbacks to the one shared back stack.
 */
@Composable
fun StickerBoardApp() {
	val context = LocalContext.current
	val startRoute = remember {
		if (isOnboardingComplete(context)) Route.Settings else Route.Onboarding
	}
	val backStack = rememberNavBackStack(startRoute)

	StickerBoardSettingsTheme {
		NavDisplay(
			backStack = backStack,
			onBack = { backStack.removeLastOrNull() },
			transitionSpec = { slideForward() },
			popTransitionSpec = { slideBackward() },
			predictivePopTransitionSpec = { slideBackward() },
			entryProvider = entryProvider {
				entry<Route.Onboarding> {
					OnboardingRoute(
						onFinished = {
							backStack.clear()
							backStack.add(Route.Settings)
						},
					)
				}
				entry<Route.Settings> {
					SettingsRoute(
						onViewStickers = { backStack.add(Route.Gallery) },
						onOpenDebug = { backStack.add(Route.Debug) },
					)
				}
				entry<Route.Gallery> {
					GalleryRoute(onBack = { backStack.removeLastOrNull() })
				}
				entry<Route.Debug> {
					DebugRoute(
						onBack = { backStack.removeLastOrNull() },
						onOpenCrashes = { backStack.add(Route.Crashes) },
						onOpenOnboarding = { backStack.add(Route.Onboarding) },
					)
				}
				entry<Route.Crashes> {
					CrashesRoute(
						onBack = { backStack.removeLastOrNull() },
						onCrashClick = { crashId -> backStack.add(Route.CrashDetail(crashId)) },
					)
				}
				entry<Route.CrashDetail> { route ->
					CrashDetailRoute(crashId = route.crashId, onBack = { backStack.removeLastOrNull() })
				}
			},
		)
	}
}

/**
 * Checks whether the user has completed the onboarding flow. Installs that already had a sticker
 * directory configured before onboarding existed are treated as already onboarded, so existing
 * users aren't sent through it retroactively.
 */
private fun isOnboardingComplete(context: Context): Boolean {
	val prefs = AppPreferences(context)
	if (prefs.onboardingComplete) {
		return true
	}
	if (prefs.stickerDirPath != null) {
		prefs.onboardingComplete = true
		return true
	}
	return false
}
