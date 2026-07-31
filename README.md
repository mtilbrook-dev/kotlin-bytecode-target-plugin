## Kotlin Target Bytecode Plugin

The Kotlin `-jdk-release` does not follow the JvmTarget. So, invalid bytecode can be generated when using new JDKs and targeting an older release bytecode. Such as the example show here in [jakewharton/kotlins-jdk-release-compatibility-flag](https://jakewharton.com/kotlins-jdk-release-compatibility-flag/)  

There is a proposal slated for Kotlin 2.1.0 that would allow the `-jdk-release` to follow the Kotlin JvmTarget. See
[KT-49746](https://youtrack.jetbrains.com/issue/KT-49746/Support-Xjdk-release-in-gradle-toolchain#focus=Comments-27-8935065.0-0)

This plugin will set the `-jdk-release` for Android, Kotlin JVM, and Kotlin Multiplatform JVM modules and can be used as a polyfill until
[KT-49746](https://youtrack.jetbrains.com/issue/KT-49746/Support-Xjdk-release-in-gradle-toolchain#focus=Comments-27-8935065.0-0) is ready.


### Installation

Root `build.gradle.kts`

```kotlin
plugins {
    id("dev.tilbrook.kotlin.bytecode-target") version "1.0.0" apply false
}
```

Each Kotlin module `build.gradle.kts`

```kotlin
plugins {
    id("dev.tilbrook.kotlin.bytecode-target")
}
```

For Kotlin Multiplatform, apply the plugin in the same module as `org.jetbrains.kotlin.multiplatform`. Its JVM targets are configured automatically:

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.tilbrook.kotlin.bytecode-target")
}

kotlin {
    jvm()
}
```

### Bytecode Target

The default bytecode target is Java 17. You can set the Java target by adding `dev.tilbrook.kotlin.bytecodeTarget` to the root `gradle.properties`

```properties
dev.tilbrook.kotlin.bytecodeTarget=11
```