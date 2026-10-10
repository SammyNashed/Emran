plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

// Roborazzi renders Compose screenshots off-screen during unit tests.
System.setProperty("java.awt.headless", "true")

android {
  namespace = "io.github.sammynashed.emran"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.github.sammynashed.emran"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "2.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Release signing is opt-in. F-Droid and any contributor running
    // `./gradlew assembleRelease` without a keystore should get an unsigned APK
    // rather than a confusing "keystore password must not be null" failure, so
    // the config is only registered when the keystore is actually present.
    val keystoreFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
    if (keystoreFile.exists() && System.getenv("STORE_PASSWORD") != null) {
      create("release") {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  // 97% of the release download was one native library shipped four times:
  // libonnxruntime.so is 70 MB across four architectures, against 6 MB for all
  // of Emran's own code. x86 and x86_64 serve emulators and a few Chromebooks,
  // never a phone, so a release build carries neither - and the two that remain
  // are split into their own APKs rather than bundled into one that makes every
  // device download the other's copy.
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a")
      // Kept so there is still one file that installs anywhere, for a release
      // page where asking people to identify their own CPU is a poor welcome.
      isUniversalApk = true
    }
  }

  buildTypes {
    release {
      // splits{} decides which per-ABI APKs are produced; it does not decide
      // what goes into the universal one. Without this the "installs anywhere"
      // APK still carried x86 and x86_64 and stayed at 76 MB, which is the
      // artifact most people would actually download.
      ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")

      // A phone and an emulator, nothing else. Debug installs go over a cable
      // dozens of times a day and every unused architecture is dead weight on
      // each one; x86_64 stays because that is what an Android Studio emulator
      // runs on, and a contributor who cannot run the app is worse off than a
      // slightly larger debug APK.
      ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
  }

  // Strip the Google-signed dependency metadata blob from release artifacts.
  // It is opaque, unreproducible, and F-Droid rejects builds that carry it.
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  // Hadith packs (assets/hadith/*.hdp) are deflated per chapter already. Storing
  // them uncompressed in the APK lets them be read in place with openFd(), instead
  // of being compressed a second time and then copied out to be opened.
  androidResources {
    noCompress += "hdp"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Emran's only network I/O is two user-started downloads (the Hifz model and the
// optional Hadith packs), both over plain HttpURLConnection. It ships no API keys,
// so there is deliberately no secrets plugin, no Firebase, and no Retrofit/OkHttp here. The unused entries
// remain in gradle/libs.versions.toml if a future release ever needs them.
dependencies {
  implementation("ai.ratl:ratl-android:1.0.0-SNAPSHOT")
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // Coil only ever loads local content:// profile pictures -- no network needed.
  implementation(libs.coil.compose)
  implementation(libs.moshi.kotlin)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
