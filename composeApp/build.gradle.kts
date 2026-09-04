import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    android {
        namespace = "com.novelstudio.app"
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
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:database"))
            implementation(project(":core:storage"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:workbench"))
            implementation(project(":feature:gallery"))
            implementation(project(":feature:compare"))
            implementation(project(":feature:swipe"))
            implementation(project(":feature:inpaint"))

            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jb.navigation.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.coil.compose)
            implementation(libs.datastore.preferences.core)
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coil.network.okhttp)
            implementation(libs.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.novelstudio.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "NovelAIDiffusionStudio"
            packageVersion = "0.1.0"
            description = "NovelAI Diffusion Studio"
            vendor = "Novel Studio"
            windows {
                menu = true
                shortcut = true
            }
        }
    }
}

// Windows 便携版：打包 createDistributable 生成的应用镜像，包含启动器、运行时和应用文件。
tasks.register<Zip>("packagePortableZip") {
    dependsOn("createDistributable")
    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/NovelAIDiffusionStudio")
    from(appImage)
    into("NovelAIDiffusionStudio")
    // 此项目未统一设置 Gradle project.version，使用发行包版本避免生成 unspecified 文件名。
    archiveFileName.set("NovelAIDiffusionStudio-0.1.0-windows-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portableZip"))
    includeEmptyDirs = false
}
