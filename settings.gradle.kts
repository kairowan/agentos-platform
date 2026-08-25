pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "agentos-platform"
include(":apps:AgentShell")
include(":libraries:CapabilityApi")
include(":libraries:CapabilityCore")
include(":services:AgentCapabilityService")
