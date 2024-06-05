/*
 * Copyright (C) 2020 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import java.net.URI
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.binaryCompatibilityValidator) apply false
}

tasks.dokkaHtmlMultiModule {
  outputDirectory.set(rootDir.resolve("docs/api/2.x"))
  includes.from(project.layout.projectDirectory.file("README.md"))
}

val tomlJvmTarget = libs.versions.jvmTarget.get()

subprojects {
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain { languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of)) }
    }

    project.tasks.withType<JavaCompile>().configureEach {
      options.release.set(tomlJvmTarget.toInt())
    }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    configure<KotlinJvmProjectExtension> {
      explicitApi()
      compilerOptions {
        progressiveMode.set(true)
        jvmTarget.set(libs.versions.jvmTarget.map(JvmTarget::fromTarget))
      }
    }
  }

  apply(plugin = "io.gitlab.arturbosch.detekt")
  tasks.withType<Detekt>().configureEach { jvmTarget = tomlJvmTarget }

  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")
    tasks.withType<DokkaTaskPartial>().configureEach {
      outputDirectory.set(layout.buildDirectory.dir("docs/partial"))
      dokkaSourceSets.configureEach {
        val readMeProvider = project.layout.projectDirectory.file("README.md")
        if (readMeProvider.asFile.exists()) {
          includes.from(readMeProvider)
        }
        skipDeprecated.set(true)
        sourceLink {
          localDirectory.set(layout.projectDirectory.dir("src").asFile)
          val relPath = rootProject.projectDir.toPath().relativize(projectDir.toPath())
          remoteUrl.set(
            providers.gradleProperty("POM_SCM_URL").map { scmUrl ->
              URI("$scmUrl/tree/main/$relPath/src").toURL()
            }
          )
          remoteLineSuffix.set("#L")
        }
      }
    }

    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
    }
    // Ref: https://github.com/slackhq/EitherNet/issues/58
    project.group = project.property("GROUP").toString()
    project.version = project.property("VERSION_NAME").toString()
  }
}

tasks.named<DokkaTask>("dokkaHtml") {
  outputDirectory.set(layout.projectDirectory.dir("docs/1.x"))
  dokkaSourceSets.configureEach {
    if (name == "testFixtures") {
      suppress.set(false)
    }

    skipDeprecated.set(true)
    externalDocumentationLink {
      url.set(URI("https://square.github.io/retrofit/2.x/retrofit/").toURL())
    }
  }
}

val ktfmtVersion = libs.versions.ktfmt.get()

allprojects {
  apply(plugin = "com.diffplug.spotless")

  spotless {
    format("misc") {
      target("*.md", ".gitignore")
      trimTrailingWhitespace()
      endWithNewline()
    }
    kotlin {
      target("**/*.kt")
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(rootProject.layout.projectDirectory.file("spotless/spotless.kt"))
      targetExclude("**/spotless.kt")
    }
    kotlinGradle {
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(
        rootProject.layout.projectDirectory.file("spotless/spotless.kt"),
        "(import|plugins|buildscript|dependencies|pluginManagement|rootProject)",
      )
    }
  }
}
