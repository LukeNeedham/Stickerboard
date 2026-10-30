package com.lukeneedham.stickerboard.di

import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.gallery.GalleryViewModel
import com.lukeneedham.stickerboard.onboarding.OnboardingViewModel
import com.lukeneedham.stickerboard.settings.SettingsViewModel
import com.lukeneedham.stickerboard.share.ShareImportViewModel
import com.lukeneedham.stickerboard.utilities.KeyboardStatus
import com.lukeneedham.stickerboard.utilities.StickerStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

object KoinModules {
	private val data = module {
		single { AppPreferences(androidContext()) }
		single { StickerStorage(androidContext()) }
		single { KeyboardStatus(androidContext()) }
	}

	private val viewModels = module {
		viewModelOf(::GalleryViewModel)
		viewModelOf(::OnboardingViewModel)
		viewModelOf(::SettingsViewModel)
		viewModelOf(::ShareImportViewModel)
	}

	val modules: List<Module> = listOf(data, viewModels)
}
