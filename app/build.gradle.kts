plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.androidpractice18"
    // 建议使用 compileSdk = 34，因为 36 可能尚未完全适配百度地图 SDK
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.androidpractice18"
        minSdk = 29
        targetSdk = 34   // 与 compileSdk 保持一致，稳定优先
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 关键：指定支持的 CPU 架构，确保 .so 文件被包含
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 配置 .so 文件打包选项，防止丢失或冲突
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
        // 如果多个依赖中存在相同的 .so，选择第一个即可
        pickFirst("lib/armeabi-v7a/libBaiduMapSDK_base_v8_0_0.so")
        pickFirst("lib/arm64-v8a/libBaiduMapSDK_base_v8_0_0.so")
    }

    // 保留你原有的 jniLibs 源目录设置（通常不需要，但保留无妨）
    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 百度地图 SDK（直接引用 libs 目录下的 aar 文件）
    implementation(files("libs/BaiduLBS_Android.aar"))
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
}