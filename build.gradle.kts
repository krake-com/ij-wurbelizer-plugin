import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

/**
 * Home of the locally installed IntelliJ IDEA the plugin is compiled against.
 *
 * The path is machine-specific, so it must never be hardcoded in a tracked file.
 * Resolution order:
 *   1. the `platformLocalPath` Gradle property (`-P`, or `~/.gradle/gradle.properties`)
 *   2. the `IDEA_HOME` environment variable
 *   3. the JetBrains Toolbox launcher script, if present
 *   4. the usual installation directories of the current OS
 *
 * A directory counts as an IDE home only if it contains a `build.txt`
 * (directly on Linux/Windows, under `Resources/` on macOS).
 */
fun ideaHome(): String {
  fun File.isIdeHome() = resolve("build.txt").isFile || resolve("Resources/build.txt").isFile

  fun explicit(source: String, value: String): String {
    val dir = File(value)
    require(dir.isDirectory) { "$source points to '$value', which does not exist or is not a directory." }
    require(dir.isIdeHome()) { "$source points to '$value', which is not an IntelliJ IDEA installation (no build.txt)." }
    return dir.absolutePath
  }

  providers.gradleProperty("platformLocalPath").orNull
    ?.takeIf { it.isNotBlank() }
    ?.let { return explicit("Gradle property 'platformLocalPath'", it) }

  providers.environmentVariable("IDEA_HOME").orNull
    ?.takeIf { it.isNotBlank() }
    ?.let { return explicit("Environment variable IDEA_HOME", it) }

  val home = File(System.getProperty("user.home"))

  // JetBrains Toolbox generates a launcher script that quotes the real installation path.
  val toolboxLauncher = home.resolve(".local/share/JetBrains/Toolbox/scripts/idea")
  if (toolboxLauncher.isFile) {
    Regex("\"([^\"]+)/bin/idea\"").find(toolboxLauncher.readText())
      ?.groupValues?.get(1)
      ?.let { File(it) }
      ?.takeIf { it.isIdeHome() }
      ?.let { return it.absolutePath }
  }

  val os = System.getProperty("os.name").lowercase()
  val candidates = when {
    os.contains("mac") -> listOf(
      File("/Applications/IntelliJ IDEA.app/Contents"),
      home.resolve("Applications/IntelliJ IDEA.app/Contents"),
    )
    os.contains("win") -> listOf(
      File("C:/Program Files/JetBrains").listFiles().orEmpty().toList(),
      home.resolve("AppData/Local/JetBrains/Toolbox/apps").listFiles().orEmpty().toList(),
    ).flatten()
    else -> listOf(
      File("/opt").listFiles().orEmpty().toList(),
      File("/usr/local").listFiles().orEmpty().toList(),
      File("/snap/intellij-idea-ultimate/current").let(::listOf),
      home.resolve(".local/share/JetBrains/Toolbox/apps").listFiles().orEmpty().toList(),
    ).flatten().filter { it.name.lowercase().let { n -> n.contains("idea") || n.contains("intellij") } }
  }

  return candidates.firstOrNull { it.isDirectory && it.isIdeHome() }?.absolutePath
    ?: error(
      """
      No local IntelliJ IDEA installation found.
      Point the build at your IDE by either
        * setting IDEA_HOME in the environment, or
        * adding `platformLocalPath=<path to your IDE>` to ~/.gradle/gradle.properties, or
        * passing -PplatformLocalPath=<path to your IDE> on the command line.
      Use the directory containing build.txt (on macOS: `IntelliJ IDEA.app/Contents`).
      """.trimIndent()
    )
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    local(ideaHome())
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
