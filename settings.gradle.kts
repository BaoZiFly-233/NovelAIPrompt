pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // 中国网络环境下 Google/Maven Central 的镜像后备（官方仓库 TLS 握手被重置时自动接管）
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // 镜像后备：仅当 google()/mavenCentral() 均未命中时生效
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "NovelAIPrompt"

include(":composeApp")
include(":androidApp")
include(":core:model")
include(":core:common")
include(":core:network")
include(":core:database")
include(":core:designsystem")
include(":feature:workbench")
include(":feature:gallery")
include(":feature:compare")
include(":feature:swipe")
include(":feature:inpaint")
