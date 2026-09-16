package com.lukeneedham.stickerboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.preference.PreferenceManager
import com.lukeneedham.stickerboard.crash.CrashDetailRoute
import com.lukeneedham.stickerboard.crash.CrashesRoute
import com.lukeneedham.stickerboard.debug.DebugRoute
import com.lukeneedham.stickerboard.gallery.GalleryRoute
import com.lukeneedham.stickerboard.navigation.Route
import com.lukeneedham.stickerboard.onboarding.OnboardingRoute
import com.lukeneedham.stickerboard.settings.SettingsRoute
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme

/**
 * The single activity's nav host - decides whether to land on onboarding or settings, and wires
 * every screen's navigation callbacks to the one shared back stack.
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
	val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
	if (sharedPreferences.getBoolean("onboardingComplete", false)) {
		return true
	}
	if (sharedPreferences.contains("stickerDirPath")) {
		sharedPreferences.edit().putBoolean("onboardingComplete", true).apply()
		return true
	}
	return false
}
