package com.lukeneedham.stickerboard

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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.common.debug.scale
import com.lukeneedham.common.ui.LocalAnimationSpeed
import com.lukeneedham.stickerboard.navigation.Route
import com.lukeneedham.stickerboard.utilities.SetupStatus

private object AppNavigationConstants {
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
 * Owns the back stack and the [NavDisplay]; called only by [Root]. Decides whether to land on onboarding, settings, or
 * (when [sharedImageUris] is non-empty, i.e. [MainActivity] was opened via another app's Share action) straight into
 * the sticker-pack picker.
 */
@Composable
fun AppNavigation(
	sharedImageUris: List<Uri>,
	onCancelShareImport: () -> Unit,
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

	val animationSpeed = LocalAnimationSpeed.current
	val slideSpec = remember(animationSpeed) {
		tween<IntOffset>(
			durationMillis = animationSpeed.scale(AppNavigationConstants.SLIDE_DURATION_MS),
		)
	}
	NavDisplay(
		backStack = backStack,
		onBack = { backStack.removeLastOrNull() },
		transitionSpec = { slideForward(slideSpec) },
		popTransitionSpec = { slideBackward(slideSpec) },
		predictivePopTransitionSpec = { slideBackward(slideSpec) },
		entryProvider = { key ->
			val route = key as Route
			NavEntry(route) {
				Router(backStack, route, onCancelShareImport)
			}
		},
	)
}
