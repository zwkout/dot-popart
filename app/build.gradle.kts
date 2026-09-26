plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dotpopart.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dotpopart.app"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 配布用の鍵はまだないので、手元の確認用に debug の鍵で署名する（配布前に差し替える）
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Glyph Matrix SDK は利用条件で再配布が禁止されているので、リポジトリに含めない。
// 公式の GlyphMatrix-Developer-Kit から取ってきて app/libs/ に置く（README）
val glyphSdk = file("libs/glyph-matrix-sdk-2.0.aar")
check(glyphSdk.exists()) {
    "Glyph Matrix SDK が見つかりません。https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit から " +
        "glyph-matrix-sdk-2.0.aar を取ってきて app/libs/ に置いてください。"
}

dependencies {
    implementation(project(":toy-engine"))
    implementation(files(glyphSdk))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
