package com.lukeneedham.stickerboard.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The few looks a button can have. Every button of a style has the same colours, from [AppColors]. */
enum class AppButtonStyle { Primary, Secondary, Text, Destructive }

/** The smallest accessible touch target: every button is at least this tall. */
private val BUTTON_MIN_HEIGHT = 48.dp

/**
 * A rounded button with a [text] label. It is as wide as its label unless the [modifier] says otherwise. A [compact] one is
 * for use inline next to other content, with a smaller corner radius and less padding.
 */
@Composable
fun AppButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	style: AppButtonStyle = AppButtonStyle.Primary,
	enabled: Boolean = true,
	compact: Boolean = false,
) {
	val colors = AppTheme.current.colors.button(style)
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.heightIn(min = BUTTON_MIN_HEIGHT)
			.clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
			.background(if (enabled) colors.background else colors.disabledBackground)
			.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
			.padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 8.dp),
	) {
		Text(
			text = text,
			color = if (enabled) colors.content else colors.disabledContent,
			fontSize = 14.sp,
			lineHeight = 20.sp,
			fontWeight = FontWeight.Medium,
			textAlign = TextAlign.Center,
		)
	}
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
	AppTheme {
		Column(
			verticalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.background(AppTheme.current.colors.background).padding(16.dp),
		) {
			AppButtonStyle.entries.forEach { style ->
				AppButton(text = style.name, onClick = {}, style = style)
				AppButton(text = "Disabled", onClick = {}, style = style, enabled = false)
			}
		}
	}
}
