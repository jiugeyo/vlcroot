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
        versionCode = 2
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "jgjiuge"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 优先用 CI 里由 JGjiuge.key 转换出来的 release keystore。
            // 本地没有该 secret 时退回 debug key，仍可构建开发包。
            signingConfig = if (!System.getenv("RELEASE_STORE_FILE").isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
