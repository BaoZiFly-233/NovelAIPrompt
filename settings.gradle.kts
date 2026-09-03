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
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
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
