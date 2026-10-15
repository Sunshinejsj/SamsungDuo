// Top-level build file. Plugins are declared here and applied in :app.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    // miuix-nav keeps its back stack in rememberSaveable, which needs a
    // serializer for the route types. Version tracks the Kotlin plugin above.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
}
