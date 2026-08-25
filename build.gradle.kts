import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

/**
 * Validates an explicitly configured IntelliJ IDEA home.
 *
 * Development builds may compile against a locally installed IDE instead of the
 * downloaded platform artifact, which saves the ~1 GB download. This is opt-in only:
 * set the `platformLocalPath` Gradle property or the `IDEA_HOME` environment variable.
 * Release builds must never use it, because the resulting artifact would be tied to
 * whatever IDE happens to be installed on the machine that produced it.
 *
 * A directory counts as an IDE home only if it contains a `build.txt`
 * (directly on Linux/Windows, under `Resources/` on macOS).
 */
fun explicitIdeaHome(source: String, value: String): String {
  fun File.isIdeHome() = resolve("build.txt").isFile || resolve("Resources/build.txt").isFile

  val dir = File(value)
  require(dir.isDirectory) { "$source points to '$value', which does not exist or is not a directory." }
  require(dir.isIdeHome()) { "$source points to '$value', which is not an IntelliJ IDEA installation (no build.txt)." }
  return dir.absolutePath
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    // Blank explicitly disables the local IDE, so a release build can override a
    // platformLocalPath set in ~/.gradle/gradle.properties with -PplatformLocalPath=
    val localPathProperty = providers.gradleProperty("platformLocalPath").orNull?.takeIf { it.isNotBlank() }
    val localPathEnv = providers.environmentVariable("IDEA_HOME").orNull?.takeIf { it.isNotBlank() }

    when {
      localPathProperty != null ->
        local(explicitIdeaHome("Gradle property 'platformLocalPath'", localPathProperty))

      localPathEnv != null ->
        local(explicitIdeaHome("Environment variable IDEA_HOME", localPathEnv))

      else -> create(
        providers.gradleProperty("platformType"),
        providers.gradleProperty("platformVersion"),
      )
    }

    bundledPlugin("com.intellij.java")

    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Plugin.Java)
  }

  testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
      untilBuild = provider { null }
    }
  }

  signing {
    certificateChainFile = providers.gradleProperty("signCertificateChainFile").map { file(it) }
    privateKeyFile = providers.gradleProperty("signPrivateKeyFile").map { file(it) }
    password = providers.gradleProperty("signPrivateKeyPassword")
  }

  publishing {
    token = providers.gradleProperty("intellijPlatformPublishingToken")
  }

  pluginVerification {
    ides {
      // IDEA Community and Ultimate were separate products up to 2025.2 and merged
      // into a single distribution in 2025.3, so the supported range needs both types
      // to be covered end to end. untilBuild is open in plugin.xml, so the upper half
      // is left unbounded and picks up every release from 2025.3 onwards.
      select {
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
        sinceBuild = providers.gradleProperty("pluginSinceBuild")
        untilBuild = "252.*"
      }
      select {
        types = listOf(IntelliJPlatformType.IntellijIdea)
        sinceBuild = "253"
      }
    }
  }
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release = 21
  options.encoding = "UTF-8"
}

tasks.test {
  useJUnit()
}
