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
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
  kotlin("jvm") version "1.6.0"
  `java-test-fixtures`
  id("org.jetbrains.dokka") version "1.5.31"
  id("com.google.devtools.ksp") version "1.6.0-RC-1.0.1-RC"
  id("com.diffplug.spotless") version "6.0.0"
  id("com.vanniktech.maven.publish") version "0.18.0"
  id("io.gitlab.arturbosch.detekt") version "1.18.1"
  id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.8.0"
}

repositories {
  mavenCentral()
  google() // for androidx.annotation
}

pluginManager.withPlugin("java") {
  configure<JavaPluginExtension> {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(11))
    }
  }

  project.tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
  }
}

tasks.withType<KotlinCompile>().configureEach {
  val taskName = name
  kotlinOptions {
    jvmTarget = "1.8"
    val argsList = mutableListOf(
      "-progressive",
      "-Xopt-in=kotlin.RequiresOptIn"
    )
    if (taskName == "compileTestKotlin") {
      argsList += "-Xopt-in=kotlin.ExperimentalStdlibApi"
      // Enable new jvmdefault behavior
      // https://blog.jetbrains.com/kotlin/2020/07/kotlin-1-4-m3-generating-default-methods-in-interfaces/
      argsList += "-Xjvm-default=all"
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
  val ktlintVersion = "0.41.0"
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

apiValidation {
  // Exclude directly instantiated annotations
  // https://github.com/Kotlin/binary-compatibility-validator/issues/71
  ignoredClasses.add("com/slack/eithernet/AnnotationsKt\$annotationImpl\$com_slack_eithernet_ResultType\$0")
  ignoredClasses.add("com/slack/eithernet/AnnotationsKt\$annotationImpl\$com_slack_eithernet_StatusCode\$0")
}

val moshiVersion = "1.12.0"
val retrofitVersion = "2.9.0"
val okhttpVersion = "4.9.0"
val coroutinesVersion = "1.5.2"
dependencies {
  implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
  testImplementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion")
  testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
  testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
  testImplementation("com.squareup.moshi:moshi:$moshiVersion")
  testImplementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("org.jetbrains.kotlin:kotlin-test:1.5.31")
  testImplementation("com.google.auto.service:auto-service:1.0")
  kspTest("dev.zacsweers.autoservice:auto-service-ksp:1.0.0")

  // Android APIs access, gated at runtime
  testFixturesCompileOnly("androidx.annotation:annotation:1.2.0")
  testFixturesCompileOnly("com.google.android:android:4.1.1.4")
  testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  // For access to Types
  testFixturesImplementation("com.squareup.moshi:moshi:1.12.0")
  testFixturesApi("org.jetbrains.kotlin:kotlin-reflect:1.5.31")
}
