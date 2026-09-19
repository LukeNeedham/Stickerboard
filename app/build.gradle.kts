plugins {
	id("com.android.application")
	id("kotlin-android")
	id("org.jetbrains.kotlin.plugin.compose")
	id("org.jetbrains.kotlin.plugin.serialization")
	id("org.jlleitschuh.gradle.ktlint")
}

android {
	compileSdk = 36
	buildToolsVersion = "36.0.0"
	namespace = "com.lukeneedham.stickerboard"

	kotlinOptions {
		jvmTarget = "17"
	}

	androidResources {
		generateLocaleConfig = true
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	defaultConfig {
		applicationId = "com.lukeneedham.stickerboard"
		minSdk = 26
		targetSdk = 36
		versionCode = 20250217
		versionName = "20250217"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		setProperty("archivesBaseName", "$applicationId-$versionName")
	}

	signingConfigs {
		getByName("debug") {
			// Use a fixed, checked-in debug keystore instead of the machine-local
			// ~/.android/debug.keystore. CI runners are ephemeral, so without this
			// every CI build would be signed with a freshly generated key, and
			// Android refuses to install an APK over an existing install with the
			// same applicationId but a different signing certificate (the "app
			// with same ID already exists" error).
			storeFile = file("debug.keystore")
			storePassword = "android"
			keyAlias = "androiddebugkey"
			keyPassword = "android"
		}
	}

	buildTypes {
		getByName("debug") {
			versionNameSuffix = "-debug"
			signingConfig = signingConfigs.getByName("debug")
		}
		getByName("release") {
			proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
			isMinifyEnabled = false
		}
	}

	compileOptions {
		sourceCompatibility(JavaVersion.VERSION_17)
		targetCompatibility(JavaVersion.VERSION_17)
	}
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
	implementation("androidx.core:core-ktx:1.15.0")
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("com.google.android.material:material:1.12.0")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("io.coil-kt:coil:2.7.0")
	implementation("io.coil-kt:coil-gif:2.7.0")
	implementation("io.coil-kt:coil-video:2.7.0")
	implementation("io.coil-kt:coil-svg:2.7.0")
	implementation("io.coil-kt:coil-compose:2.7.0")
	implementation(platform("androidx.compose:compose-bom:2025.12.00"))
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.foundation:foundation")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.activity:activity-compose")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
	implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
	implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
	implementation("androidx.savedstate:savedstate-ktx:1.4.0")
	implementation("androidx.navigation3:navigation3-runtime:1.0.1")
	implementation("androidx.navigation3:navigation3-ui:1.0.1")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
	implementation("androidx.gridlayout:gridlayout:1.0.0")
	implementation("com.elvishew:xlog:1.11.1")
	androidTestImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test:core:1.6.1")
	androidTestImplementation("androidx.test.ext:junit:1.2.1")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
	version.set("0.50.0")
	android.set(true)
	coloredOutput.set(false)
}
