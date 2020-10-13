/*
 * Copyright (C) 2020 Slack Technologies, Inc.
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
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
  kotlin("jvm") version "1.4.10"
  id("org.jetbrains.dokka") version "1.4.10"
  id("com.diffplug.spotless") version "5.6.0"
  id("com.vanniktech.maven.publish") version "0.13.0"
  id("io.gitlab.arturbosch.detekt") version "1.13.1"
}

repositories {
  mavenCentral()
  exclusiveContent {
    forRepository {
      maven {
        name = "JCenter"
        setUrl("https://jcenter.bintray.com/")
      }
    }
    filter {
      // Required for Dokka
      includeModule("org.jetbrains.kotlinx", "kotlinx-html-jvm")
      includeGroup("org.jetbrains.dokka")
      includeModule("org.jetbrains", "markdown")
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
  val taskName = name
  kotlinOptions {
    jvmTarget = "1.8"
    val argsList = mutableListOf("-progressive")
    if (taskName == "compileTestKotlin") {
      argsList += "-Xopt-in=kotlin.ExperimentalStdlibApi"
    }
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += argsList
  }
}

tasks.withType<Detekt>().configureEach {
  jvmTarget = "1.8"
}

kotlin {
  explicitApi()
}

tasks.named<DokkaTask>("dokkaHtml") {
  outputDirectory.set(rootDir.resolve("docs/0.x"))
  dokkaSourceSets.configureEach {
    skipDeprecated.set(true)
    externalDocumentationLink {
      url.set(URL("https://square.github.io/retrofit/2.x/retrofit/"))
    }
  }
}

spotless {
  format("misc") {
    target("*.md", ".gitignore")
    trimTrailingWhitespace()
    endWithNewline()
  }
  val ktlintVersion = "0.39.0"
  val ktlintUserData = mapOf("indent_size" to "2", "continuation_indent_size" to "2")
  kotlin {
    target("**/*.kt")
    ktlint(ktlintVersion).userData(ktlintUserData)
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kt")
    targetExclude("**/spotless.kt")
  }
  kotlinGradle {
    ktlint(ktlintVersion).userData(ktlintUserData)
    trimTrailingWhitespace()
    endWithNewline()
    licenseHeaderFile("spotless/spotless.kt", "(import|plugins|buildscript|dependencies|pluginManagement)")
  }
}

val moshiVersion = "1.10.0"
val retrofitVersion = "2.9.0"
val okhttpVersion = "4.9.0"
val coroutinesVersion = "1.3.9"
dependencies {
  implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
  testImplementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion")
  testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
  testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
  testImplementation("com.squareup.moshi:moshi:$moshiVersion")
  testImplementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
  testImplementation("junit:junit:4.13.1")
  testImplementation("com.google.truth:truth:1.0.1")
}
