plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.radumariusi.akvodhub.player"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = file("lint.xml")
    }
}

dependencies {
    api(project(":core:parser"))
    api(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    api(libs.media3.ui)
    // Decodor audio software (AC3, EAC3, DTS, TrueHD…) pentru box-urile fără decodor hardware.
    implementation(libs.media3.ffmpeg)

    testImplementation(libs.junit)
}
