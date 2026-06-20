pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    // Mirrors to resolve geo-blocking (403 Forbidden) and proxy issues
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // Mirrors to resolve geo-blocking (403 Forbidden) and proxy issues
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
  }
}

rootProject.name = "My Application"

include(":app")
