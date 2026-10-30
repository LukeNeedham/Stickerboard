package com.lukeneedham.stickerboard.di

import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.gallery.GalleryViewModel
import com.lukeneedham.stickerboard.onboarding.OnboardingViewModel
import com.lukeneedham.stickerboard.settings.SettingsViewModel
import com.lukeneedham.stickerboard.share.ShareImportViewModel
import com.lukeneedham.stickerboard.utilities.KeyboardStatus
import com.lukeneedham.stickerboard.utilities.StickerStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
	single { AppPreferences(androidContext()) }
	single { StickerStorage(androidContext()) }
	single { KeyboardStatus(androidContext()) }
	viewModelOf(::GalleryViewModel)
	viewModelOf(::OnboardingViewModel)
	viewModelOf(::SettingsViewModel)
	viewModelOf(::ShareImportViewModel)
}
