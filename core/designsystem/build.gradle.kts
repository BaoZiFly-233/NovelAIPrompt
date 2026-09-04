plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    android {
        namespace = "com.novelstudio.core.designsystem"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(libs.compose.material.icons.core)
            api(libs.compose.material.icons.extended)
            api(compose.ui)
        }
        androidMain.dependencies {
            api(libs.androidx.graphics.shapes)
        }
        jvmMain.dependencies {
            api(libs.androidx.graphics.shapes)
        }
    }
}

kotlin {
    sourceSets.all {
        languageSettings {
            optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        }
    }
}
