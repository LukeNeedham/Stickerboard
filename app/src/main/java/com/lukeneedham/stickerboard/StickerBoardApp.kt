package com.lukeneedham.stickerboard

import com.lukeneedham.stickerboard.utilities.SetupStatus
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
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
import com.lukeneedham.common.debug.scale
import com.lukeneedham.common.ui.CrashesPage
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.common.ui.LocalAnimationSpeed
import com.lukeneedham.common.ui.LogsPage
import com.lukeneedham.common.ui.ProvideDebugSettings
import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.debug.DebugRoute
import com.lukeneedham.stickerboard.gallery.GalleryRoute
import com.lukeneedham.stickerboard.keyboard.AppTheme
import com.lukeneedham.stickerboard.navigation.Route
import com.lukeneedham.stickerboard.onboarding.OnboardingRoute
import com.lukeneedham.stickerboard.settings.SettingsRoute
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.share.ShareImportRoute

private object StickerBoardAppConstants {
	const val SLIDE_DURATION_MS = 300
}

/** Incoming page slides in from the right, outgoing page slides out to the left. */
private fun <T : NavKey> AnimatedContentTransitionScope<Scene<T>>.slideForward(
	slideSpec: FiniteAnimationSpec<IntOffset>,
) = slideInHorizontally(slideSpec) { it } togetherWith slideOutHorizontally(slideSpec) { -it }

/** Incoming page slides in from the left, outgoing page slides out to the right. */
private fun <T : NavKey> AnimatedContentTransitionScope<Scene<T>>.slideBackward(
	slideSpec: FiniteAnimationSpec<IntOffset>,
) = slideInHorizontally(slideSpec) { -it } togetherWith slideOutHorizontally(slideSpec) { it }

/**
 * The single activity's nav host - decides whether to land on onboarding, settings, or (when
 * [sharedImageUris] is non-empty, i.e. [MainActivity] was opened via another app's Share action)
 * straight into the sticker-pack picker - and wires every page's navigation callbacks to the one
 * shared back stack.
 */
@Composable
fun StickerBoardApp(
	sharedImageUris: List<Uri> = emptyList(),
	onCancelShareImport: () -> Unit = {},
) {
	val context = LocalContext.current
	val startRoute = remember(sharedImageUris) {
		when {
			sharedImageUris.isNotEmpty() && SetupStatus.isOnboardingComplete(context) ->
				Route.ShareImport(sharedImageUris.map { it.toString() })

			SetupStatus.isOnboardingComplete(context) -> Route.Settings
			else -> Route.Onboarding
		}
	}
	val backStack = rememberNavBackStack(startRoute)

	AppTheme {
		StickerBoardSettingsTheme {
			ProvideDebugSettings {
				val animationSpeed = LocalAnimationSpeed.current
				val slideSpec = remember(animationSpeed) {
					tween<IntOffset>(
						durationMillis = animationSpeed.scale(StickerBoardAppConstants.SLIDE_DURATION_MS),
					)
				}
				NavDisplay(
					backStack = backStack,
					onBack = { backStack.removeLastOrNull() },
					transitionSpec = { slideForward(slideSpec) },
					popTransitionSpec = { slideBackward(slideSpec) },
					predictivePopTransitionSpec = { slideBackward(slideSpec) },
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
								onViewStickers = { backStack.add(Route.Gallery()) },
								onOpenDebug = { backStack.add(Route.Debug) },
							)
						}
						entry<Route.Gallery> { route ->
							GalleryRoute(
								onBack = { backStack.removeLastOrNull() },
								pendingImportPackName = route.pendingImportPackName,
								pendingImportUris = route.pendingImportUris,
							)
						}
						entry<Route.ShareImport> { route ->
							ShareImportRoute(
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
									backStack.clear()
									backStack.add(Route.Settings)
									backStack.add(
										Route.Gallery(
											pendingImportPackName = packName,
											pendingImportUris = route.imageUris,
										),
									)
								},
							)
						}
						entry<Route.Debug> {
							DebugRoute(
								onBack = { backStack.removeLastOrNull() },
								onOpenSdkDebug = { backStack.add(Route.SdkDebug) },
								onOpenOnboarding = { backStack.add(Route.Onboarding) },
							)
						}
						entry<Route.SdkDebug> {
							DebugPage(
								onBack = { backStack.removeLastOrNull() },
								onOpenLogs = { backStack.add(Route.Logs) },
								onOpenCrashes = { backStack.add(Route.Crashes) },
							)
						}
						entry<Route.Logs> {
							LogsPage(onBack = { backStack.removeLastOrNull() })
						}
						entry<Route.Crashes> {
							CrashesPage(onBack = { backStack.removeLastOrNull() })
						}
					},
				)
			}
		}
	}
}
