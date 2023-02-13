plugins {
  id("java")
  id("org.jetbrains.intellij") version "1.10.1"
}

group = "tel.panfilov.intellij.plugin"
version = "1.0"

repositories {
  mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  version.set("2021.3")
  type.set("IC") // Target IDE Platform
  updateSinceUntilBuild.set(false)
  plugins.set(listOf("org.jetbrains.plugins.terminal", "org.jetbrains.idea.maven", "org.jetbrains.idea.maven.model"))
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "11"
    targetCompatibility = "11"
  }

  patchPluginXml {
    sinceBuild.set("213.0")
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}
