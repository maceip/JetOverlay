pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
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

includeBuild("../FlexibleBottomSheet") {
    dependencySubstitution {
        substitute(module("com.github.skydoves:flexible-bottomsheet-material3"))
            .using(project(":flexible-bottomsheet-material3"))
        substitute(module("com.github.skydoves:flexible-core"))
            .using(project(":flexible-core"))
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://packages.jetbrains.team/maven/p/jcs/maven")
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "jetoverlay"
include(":app")
include(":jetoverlay")
