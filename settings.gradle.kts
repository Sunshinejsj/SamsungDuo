pluginManagement {
    repositories {
        // Aliyun mirrors FIRST. They are reachable directly inside China
        // without a tunnel, so Gradle grabs deps from them and never has to
        // touch the GFW-blocked dl.google.com / repo1.maven.org directly.
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // Official endpoints kept only as a last-resort fallback.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Vendored copy of io.github.libxposed:*:102.0.0.
        maven {
            url = uri(rootDir.resolve(".tools/maven"))
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}

rootProject.name = "SamsungDuo"
include(":app")
