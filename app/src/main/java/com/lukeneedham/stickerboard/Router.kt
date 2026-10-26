package com.lukeneedham.stickerboard

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.lukeneedham.common.ui.CrashesPage
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.common.ui.LogsPage
import com.lukeneedham.stickerboard.debug.AppDebugPage
import com.lukeneedham.stickerboard.gallery.GalleryPage
import com.lukeneedham.stickerboard.navigation.Route
import com.lukeneedham.stickerboard.onboarding.OnboardingPage
import com.lukeneedham.stickerboard.settings.SettingsPage
import com.lukeneedham.stickerboard.share.ShareImportPage

/** Calls the page of [route], with the navigation as lambdas. The only place that calls a page. */
@Composable
fun Router(
	backStack: NavBackStack<NavKey>,
	route: Route,
	onCancelShareImport: () -> Unit,
) {
	// The common actions are defined once here, never repeated in each branch
	fun navigateTo(target: Route) { backStack.add(target) }
	fun onBack() { backStack.removeLastOrNull() }
	fun navigateToAndClear(target: Route) {
		backStack.clear()
		backStack.add(target)
	}

	when (route) { // exhaustive: no `else`
		Route.Onboarding -> OnboardingPage(
			onFinished = { navigateToAndClear(Route.Settings) },
		)

		Route.Settings -> SettingsPage(
			onViewStickers = { navigateTo(Route.Gallery()) },
			onOpenDebug = { navigateTo(Route.AppDebug) },
		)

		is Route.Gallery -> GalleryPage(
			onBack = ::onBack,
			pendingImportPackName = route.pendingImportPackName,
			pendingImportUris = route.pendingImportUris,
		)

		is Route.ShareImport -> ShareImportPage(
			imageUris = route.imageUris.map { Uri.parse(it) },
			onCancel = onCancelShareImport,
			onPackChosen = { packName ->
				// The picker screen was the start route (opened straight from another
				// app's Share action), so there's nothing under it to return to - rebuild
				// the stack as Settings with the Stickers page pushed on top (exactly how
				// reaching it normally looks), which runs the actual import itself and
				// shows its own loading state while it does. Landing on a bare Gallery
				// instead would leave its own back button with nothing left to pop,
				// crashing NavDisplay ("backstack cannot be empty").
				navigateToAndClear(Route.Settings)
				navigateTo(
					Route.Gallery(
						pendingImportPackName = packName,
						pendingImportUris = route.imageUris,
					),
				)
			},
		)

		Route.AppDebug -> AppDebugPage(
			onBack = ::onBack,
			onOpenSdkDebug = { navigateTo(Route.Debug) },
			onOpenOnboarding = { navigateTo(Route.Onboarding) },
		)

		Route.Debug -> DebugPage(
			onBack = ::onBack,
			onOpenLogs = { navigateTo(Route.Logs) },
			onOpenCrashes = { navigateTo(Route.Crashes) },
		)

		Route.Logs -> LogsPage(onBack = ::onBack)

		Route.Crashes -> CrashesPage(onBack = ::onBack)
	}
}
