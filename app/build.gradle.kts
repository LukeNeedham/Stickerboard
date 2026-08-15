plugins {
	id("com.android.application")
	id("kotlin-android")
	id("org.jetbrains.dokka")
	id("org.jlleitschuh.gradle.ktlint")
}

tasks.dokkaGfm.configure {
	outputDirectory.set(file(layout.buildDirectory.dir("../../documentation/reference")))
	dokkaSourceSets {
		named("main") {
			skipDeprecated.set(true)
			skipEmptyPackages.set(true)
			sourceRoots.from(file("src/main/java"))
			suppressInheritedMembers.set(true)
			includeNonPublic.set(true)
		}
	}
}

tasks.register("genDocs") {
	val ref = layout.buildDirectory.dir("../../documentation/reference")
	delete(ref)
	dependsOn("dokkaGfm")
	doLast {
		copy {
			from("${ref.get()}/index.md")
			into(ref.get())
			rename { "README.md" }
		}
	}
}

android {
	compileSdk = 35
	buildToolsVersion = "35.0.0"
	namespace = "com.lukeneedham.stickerboard"

	kotlinOptions {
		jvmTarget = "17"
	}

	androidResources {
		generateLocaleConfig = true
	}

	defaultConfig {
		applicationId = "com.lukeneedham.stickerboard"
		minSdk = 26
		targetSdk = 35
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
	dokkaPlugin("org.jetbrains.dokka:android-documentation-plugin:2.0.0")
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
	implementation("androidx.core:core-ktx:1.15.0")
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("com.google.android.material:material:1.12.0")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("io.coil-kt:coil:2.7.0")
	implementation("io.coil-kt:coil-gif:2.7.0")
	implementation("io.coil-kt:coil-video:2.7.0")
	implementation("io.coil-kt:coil-svg:2.7.0")
	implementation("androidx.gridlayout:gridlayout:1.0.0")
	implementation("androidx.viewpager2:viewpager2:1.1.0")
	implementation("io.noties.markwon:core:4.6.2")
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
