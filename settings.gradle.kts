pluginManagement {
    repositories {
        google {
//            content {
//                includeGroupByRegex("com\\.android.*")
//                includeGroupByRegex("com\\.google.*")
//                includeGroupByRegex("androidx.*")
//            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
    }
}

rootProject.name = "jetoverlay"
include(":app")
include(":jetoverlay")
