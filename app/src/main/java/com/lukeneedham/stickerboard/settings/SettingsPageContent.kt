

/**
 * The settings app's root page: the same enable-keyboard, try-it-out, view-stickers and
 * (debug-only) debug tools that MainActivity's old XML layout offered, rebuilt in Compose with a
 * modern Material 3 look. Choosing/reloading the sticker source directory lives entirely on the
 * Stickers page now, alongside its path, sticker/pack counts and last-refreshed time.
 */
@Composable
internal fun SettingsPageContent(
	state: SettingsUiState,
	onEnableKeyboard: () -> Unit,
	onTryItOutMediaReceived: (Uri) -> Unit,
	onViewStickers: () -> Unit,
	onOpenDebug: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = stringResource(R.string.app_name),
						fontWeight = FontWeight.Bold,
					)
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
					titleContentColor = MaterialTheme.colorScheme.onBackground,
				),
			)
		},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			EnableKeyboardCard(state.keyboardEnabled, onEnableKeyboard)
			TryItOutCard(state.tryItOutLastMedia, onTryItOutMediaReceived)
			ViewStickersCard(onViewStickers)
			if (state.showDebugCard) {
				DebugCard(onOpenDebug)
			}
			Spacer(Modifier.height(8.dp))
		}
	}
}

/** Common modern card shell: soft rounded corners, a flat tonal surface, no harsh elevation. */
@Composable
internal fun SettingsCard(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(24.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
		elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
	) {
		Column(
			modifier = Modifier.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			content = content,
		)
	}
}

@Composable
internal fun CardHeading(iconRes: Int, text: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Image(
			painter = painterResource(iconRes),
			contentDescription = null,
			colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
			modifier = Modifier.size(22.dp),
		)
		Spacer(Modifier.width(10.dp))
		Text(
			text = text,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
internal fun CardBody(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

@Composable
private fun EnableKeyboardCard(keyboardEnabled: Boolean, onEnableKeyboard: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_settings, stringResource(R.string.enable_keyboard_heading))
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			KeyboardStatusIndicator(keyboardEnabled)
			if (keyboardEnabled) {
				SmallActionButton(stringResource(R.string.enable_keyboard_settings_button), onEnableKeyboard)
			}
		}
		if (!keyboardEnabled) {
			CardBody(stringResource(R.string.enable_keyboard_info))
			FilledActionButton(stringResource(R.string.enable_keyboard_button), onEnableKeyboard)
		}
	}
}

/** Live readout of whether the StickerBoard keyboard is currently enabled in system settings. */
@Composable
internal fun KeyboardStatusIndicator(enabled: Boolean) {
	val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
	val label = stringResource(
		if (enabled) {
			R.string.keyboard_status_enabled
		} else {
			R.string.keyboard_status_not_enabled
		},
	)
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(Modifier.size(10.dp).background(color, CircleShape))
		Text(text = label, style = MaterialTheme.typography.bodyMedium, color = color)
	}
}

@Composable
private fun TryItOutCard(lastMedia: Uri?, onMediaReceived: (Uri) -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_send, stringResource(R.string.try_it_out_heading))
		CardBody(stringResource(R.string.try_it_out_info))
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			TryItOutInputField(onMediaReceived, modifier = Modifier.weight(1f))
			TryItOutMediaBox(lastMedia)
		}
	}
}

/** Shows the last sticker sent through [TryItOutInputField], or an empty placeholder before the
 * first one arrives. */
@Composable
private fun TryItOutMediaBox(media: Uri?, modifier: Modifier = Modifier) {
	val boxModifier = modifier.width(64.dp)
	if (media != null) {
		// An image's height can safely follow its width via aspect ratio - unlike text, it
		// won't get clipped when the user scales up their system font size.
		AsyncImage(
			model = media,
			contentDescription = stringResource(R.string.try_it_out_image_content_description),
			contentScale = ContentScale.Fit,
			modifier = boxModifier.aspectRatio(1f),
		)
	} else {
		Box(
			modifier = boxModifier
				.background(MaterialTheme.colorScheme.surfaceVariant)
				.padding(vertical = 16.dp),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = stringResource(R.string.try_it_out_placeholder_text),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(horizontal = 4.dp),
			)
		}
	}
}

@Composable
private fun TryItOutInputField(onMediaReceived: (Uri) -> Unit, modifier: Modifier = Modifier) {
	val hint = stringResource(R.string.try_it_out_hint)
	val currentOnMediaReceived by rememberUpdatedState(onMediaReceived)
	val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
	val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
	val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
	val paddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
	AndroidView(
		modifier = modifier.fillMaxWidth(),
		factory = { context ->
			EditText(context).apply {
				this.hint = hint
				// Sizes via its (sp-scaled) text plus padding, rather than a fixed dp height,
				// so it grows correctly when the user scales up their system font size.
				textSize = 16f // mirrors the keyboard's own body text size
				setTextColor(textColor)
				setHintTextColor(hintColor)
				setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
				background = GradientDrawable().apply {
					setColor(backgroundColor)
					cornerRadius = 16f * resources.displayMetrics.density
				}
				ViewCompat.setOnReceiveContentListener(this, arrayOf("image/*", "video/*")) { _, payload ->
					val split = payload.partition { it.uri != null }
					val mediaContent = split.first
					if (mediaContent != null) {
						for (i in 0 until mediaContent.clip.itemCount) {
							mediaContent.clip.getItemAt(i).uri?.let { uri -> currentOnMediaReceived(uri) }
						}
					}
					split.second
				}
			}
		},
	)
}

@Composable
private fun ViewStickersCard(onViewStickers: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_recent, stringResource(R.string.view_stickers_heading))
		CardBody(stringResource(R.string.view_stickers_info))
		FilledActionButton(stringResource(R.string.view_stickers_button), onViewStickers)
	}
}

@Composable
private fun DebugCard(onOpenDebug: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_search, stringResource(R.string.debug_heading))
		CardBody(stringResource(R.string.debug_info))
		TonalActionButton(stringResource(R.string.debug_open_button), onOpenDebug)
	}
}

@Composable
internal fun FilledActionButton(
	text: String,
	onClick: () -> Unit,
	enabled: Boolean = true,
	modifier: Modifier = Modifier,
) {
	Button(
		onClick = onClick,
		enabled = enabled,
		shape = RoundedCornerShape(16.dp),
		modifier = modifier.fillMaxWidth(),
	) {
		Text(text)
	}
}

@Composable
internal fun TonalActionButton(
	text: String,
	onClick: () -> Unit,
	enabled: Boolean = true,
	modifier: Modifier = Modifier,
) {
	FilledTonalButton(
		onClick = onClick,
		enabled = enabled,
		shape = RoundedCornerShape(16.dp),
		modifier = modifier.fillMaxWidth(),
	) {
		Text(text)
	}
}

/** A compact, content-width button - for inline use alongside other content, unlike the
 * full-width [FilledActionButton]/[TonalActionButton] used for a card's primary action. */
@Composable
internal fun SmallActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
	FilledTonalButton(
		onClick = onClick,
		shape = RoundedCornerShape(12.dp),
		contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
		modifier = modifier,
	) {
		Text(text, style = MaterialTheme.typography.labelLarge)
	}
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
	StickerBoardSettingsTheme {
		SettingsPageContent(
			state = SettingsUiState(keyboardEnabled = true),
			onEnableKeyboard = {},
			onTryItOutMediaReceived = {},
			onViewStickers = {},
			onOpenDebug = {},
		)
	}
}
