plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release CI derives both numbers from the pushed tag and passes them in, so the
// version in the APK, the version in the About card and the version the updater
// compares against are all the same value. A local build falls back to the
// checked-in defaults below.
val tagVersionName = (findProperty("samsungDuoVersionName") as String?)?.takeIf { it.isNotBlank() }
val tagVersionCode = (findProperty("samsungDuoVersionCode") as String?)?.toIntOrNull()

android {
    // The module id is a plain reverse-domain id; it needs no DNS TXT record
    // verification because we use our own com.jsj.samsungduo namespace.
    namespace = "com.jsj.samsungduo"
    // Bumped to 37: the pinned deps (Compose 1.12.0, core-ktx 1.19.0,
    // material3 1.5.0-alpha22, libxposed 102.0.0) all declare minCompileSdk 37
    // in their AAR metadata, so 36 fails the CheckAarMetadata task. targetSdk
    // stays 36 to avoid opting into Android 17 runtime behavior; compileSdk only
    // affects which APIs we build against.
    compileSdk = 37

    defaultConfig {
        // Changing this makes the module a different app to Android: an existing
        // install of this module cannot be updated in place, and its settings
        // and LSPosed scope entry do not carry over. Uninstall the old one first.
        applicationId = "com.jsj.samsungduo"
        minSdk = 29
        targetSdk = 36
        versionCode = tagVersionCode ?: 101
        versionName = tagVersionName ?: "10.1"
    }

    signingConfigs {
        // Same keystore the hand-rolled pipeline used, so that in-place updates
        // over the already-installed module keep working.
        create("release") {
            storeFile = rootProject.file(".tools/debug.keystore")
            storePassword = "android"
            keyAlias = "samsungduo"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        // Must match the Kotlin target below: AGP fails the build outright on a
        // mismatch. 21 rather than 17 because Miuix ships Java 21 bytecode and
        // miuix-nav's entry points are inline functions, which cannot be inlined
        // into 17-targeted output.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // Keep META-INF/xposed/{java_init.list,scope.list,module.prop} in the APK.
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = false
    }
}

// 产物命名：SamsungDuo-{versionName}.apk（如 SamsungDuo-10.0.apk），与历史约定一致，
// 不再用默认的 app-release.apk。
androidComponents {
    onVariants(selector().all()) { variant ->
        val vn = (findProperty("samsungDuoVersionName") as String?)?.takeIf { it.isNotBlank() }
            ?: tagVersionName ?: "10.1"
        variant.outputs.forEach { output ->
            output.outputFileName.set("SamsungDuo-$vn.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Provided by the Xposed framework at runtime, never bundled.
    compileOnly("io.github.libxposed:api:102.0.0")
    // Needed by the settings app to talk to the framework service.
    implementation("io.github.libxposed:service:102.0.0")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // The updater does its network work off the main thread and reports state
    // back on it; Compose alone has no scheduling primitive for that.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // @Serializable on the nav routes. miuix-nav only exposes it as a runtime
    // dependency, so the annotation and the inlined serializer() lookup need it
    // declared here to be on the compile classpath.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    // Blurred top bar. The artifact declares minSdk 33, but every effect in it
    // is gated on isRuntimeShaderSupported() and degrades to plain drawing
    // below that, so the manifest override keeps our own minSdk at 29.
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    // Only for the isRuntimeShaderSupported() gate the settings screen applies
    // before it touches any blur API. Blur pulls it in transitively, but the
    // code imports from it directly, so it is declared directly.
    implementation("top.yukonga.miuix.kmp:miuix-shader-android:0.9.4")
    // Compose navigation with a continuous back stack. Exists only from 0.9.4,
    // which is why the whole Miuix stack above moved up with it. Its aar
    // declares minSdk 24, so it needs no manifest override of its own.
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4")
}
