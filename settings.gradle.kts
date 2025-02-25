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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            setUrl("https://maven.pkg.github.com/FairyDevicesRD/thinklet.app.sdk")
            credentials {
                val properties = java.util.Properties()
                properties.load(file("local.properties").inputStream())
                username = properties.getProperty("USERNAME") ?: ""
                password = properties.getProperty("TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "Lifelog"
include(":app")
include(":data")
