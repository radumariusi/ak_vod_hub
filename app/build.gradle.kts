import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.baseline.profile)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Versiunea: CI trimite -Pakvod.versionCode=<număr build>; local rămâne 1 / "-dev".
val versionBase = providers.gradleProperty("akvod.versionBase").getOrElse("0.1")
val ciVersionCode = providers.gradleProperty("akvod.versionCode").orNull?.toIntOrNull()
val supportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
val releaseAbi = providers.gradleProperty("akvod.releaseAbi").orNull
require(releaseAbi == null || releaseAbi in supportedAbis) { "Arhitectură APK necunoscută: $releaseAbi" }

val updateRepo = providers.gradleProperty("akvod.updateRepo").getOrElse("radumariusi/ak_vod_hub")

// Semnare: secretul AK_SIGNING_KEY are forma "<parolă>:<keystore PKCS12 în base64>". Doar în CI.
val signingSecret = providers.environmentVariable("AK_SIGNING_KEY").orNull?.takeIf { it.contains(':') }

android {
    namespace = "io.github.radumariusi.akvodhub"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "io.github.radumariusi.akvodhub"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode = ciVersionCode ?: 1
        versionName = if (ciVersionCode != null) "$versionBase.$ciVersionCode" else "$versionBase.0-dev"

        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("boolean", "CHECK_UPDATES_ON_STARTUP", "true")
        // Căutarea globală: autoritatea providerului trebuie scrisă și în res/xml/searchable.xml.
        resValue("string", "search_authority", "io.github.radumariusi.akvodhub.search")

        // FFmpeg are biblioteci native: doar procesoarele ARM ale TV-urilor (+ x86_64 pentru emulator).
        ndk {
            abiFilters += releaseAbi?.let(::listOf) ?: supportedAbis
        }
    }

    signingConfigs {
        if (signingSecret != null) {
            create("release") {
                val password = signingSecret.substringBefore(':')
                val keystore =
                    layout.buildDirectory
                        .file("signing/release.p12")
                        .get()
                        .asFile
                keystore.parentFile.mkdirs()
                keystore.writeBytes(Base64.getMimeDecoder().decode(signingSecret.substringAfter(':').trim()))
                storeFile = keystore
                storeType = "PKCS12"
                storePassword = password
                keyAlias = "akvodhub"
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingSecret != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "search_authority", "io.github.radumariusi.akvodhub.debug.search")
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = false
        // Aplicația se instalează din GitHub, nu din Play: regulile Play nu se aplică.
        disable +=
            setOf(
                "ExpiredTargetSdkVersion",
                "OldTargetApi",
                "GradleDependency",
                "AndroidGradlePluginVersion",
                "NewerVersionAvailable",
            )
    }
}

// AndroidX copiază manifestul release când creează varianta de profilare.
androidComponents {
    finalizeDsl { extension ->
        extension.buildTypes
            .getByName("nonMinifiedRelease")
            .buildConfigField("boolean", "CHECK_UPDATES_ON_STARTUP", "false")
        extension.sourceSets
            .getByName("nonMinifiedRelease")
            .manifest
            .srcFile("src/nonMinifiedRelease/AndroidManifest.xml")
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)

    implementation(project(":core:data"))
    implementation(project(":core:player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.qrcodegen)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
