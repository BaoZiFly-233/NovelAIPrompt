plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.novelstudio.core.database"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            api(libs.room.runtime)
            implementation(libs.coroutines.core)
        }
        androidMain.dependencies {
            // room-ktx 仅提供 Android 变体（AAR），协程事务等能力在 JVM 端由 room-runtime 提供
            implementation(libs.room.ktx)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
