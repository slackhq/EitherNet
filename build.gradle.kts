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
import com.google.devtools.ksp.gradle.KspTaskJvm
import io.gitlab.arturbosch.detekt.Detekt
import java.net.URI
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

plugins {
  alias(libs.plugins.kotlin.jvm)
  `java-test-fixtures`
  alias(libs.plugins.dokka)
  alias(libs.plugins.ksp)
  alias(libs.plugins.spotless)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.detekt)
  alias(libs.plugins.binaryCompatibilityValidator)
}

repositories { mavenCentral() }

val tomlJvmTarget = libs.versions.jvmTarget.get()

pluginManager.withPlugin("java") {
  configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt())) }
  }

  project.tasks.withType<JavaCompile>().configureEach { options.release.set(tomlJvmTarget.toInt()) }
}

apiValidation {
  // https://github.com/Kotlin/binary-compatibility-validator/issues/139
  validationDisabled = findProperty("kotlin.experimental.tryK2") == "true"
}

val kotlinCompilerOptions: KotlinJvmCompilerOptions.() -> Unit = {
  progressiveMode.set(true)
  jvmTarget.set(libs.versions.jvmTarget.map(JvmTarget::fromTarget))
}

kotlin {
  explicitApi()
  compilerOptions(kotlinCompilerOptions)
}

tasks.compileTestKotlin {
  compilerOptions {
    optIn.addAll("kotlin.ExperimentalStdlibApi", "kotlinx.coroutines.ExperimentalCoroutinesApi")
    // Enable new jvmdefault behavior
    // https://blog.jetbrains.com/kotlin/2020/07/kotlin-1-4-m3-generating-default-methods-in-interfaces/
    freeCompilerArgs.add("-Xjvm-default=all")
  }
}

// https://github.com/google/ksp/issues/1387
tasks.withType<KspTaskJvm>().configureEach { compilerOptions(kotlinCompilerOptions) }

tasks.withType<Detekt>().configureEach { jvmTarget = tomlJvmTarget }

tasks.named<DokkaTask>("dokkaHtml") {
  outputDirectory.set(rootDir.resolve("docs/0.x"))
  dokkaSourceSets.configureEach {
    skipDeprecated.set(true)
    externalDocumentationLink {
      url.set(URI("https://square.github.io/retrofit/2.x/retrofit/").toURL())
    }
  }
}

val ktfmtVersion = libs.versions.ktfmt.get()

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
    licenseHeaderFile("spotless/spotless.kt")
    targetExclude("**/spotless.kt")
  }
  kotlinGradle {
    ktfmt(ktfmtVersion).googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile(
      "spotless/spotless.kt",
      "(import|plugins|buildscript|dependencies|pluginManagement|rootProject)",
    )
  }
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()
}

// Ref: https://github.com/slackhq/EitherNet/issues/58
project.group = project.property("GROUP").toString()

project.version = project.property("VERSION_NAME").toString()

dependencies {
  implementation(libs.retrofit)
  implementation(libs.coroutines.core)

  testImplementation(libs.coroutines.core)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.retrofit.converterScalars)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.moshi)
  testImplementation(libs.moshi.kotlin)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.autoService.annotations)
  kspTest(libs.autoService.ksp)

  // Android APIs access, gated at runtime
  testFixturesCompileOnly(libs.androidProcessingApi)
  testFixturesImplementation(libs.coroutines.core)
  // For access to Types
  testFixturesImplementation(libs.moshi)
  testFixturesApi(libs.kotlin.reflect)
}
