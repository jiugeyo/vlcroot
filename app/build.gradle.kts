plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.vlcroot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.vlcroot"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 不配置 signingConfig：输出 app-release-unsigned.apk，
            // 由用户后续用 MT 管理器注入自己的签名。
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            // 保留 libxposed 的模块描述文件
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }
}

dependencies {
    // 由 LSPosed/Vector 在运行时提供，不能打进 APK
    compileOnly("io.github.libxposed:api:102.0.0")

    // 仅用于本机回环 HTTP
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
