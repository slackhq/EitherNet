/*
 * Copyright (C) 2024 Slack Technologies, LLC
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

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
}

kotlin {
  compilerOptions {
    optIn.addAll("kotlin.ExperimentalStdlibApi", "com.slack.eithernet.InternalEitherNetApi")
  }
}

dependencies {
  api(libs.retrofit)
  api(project(":eithernet"))

  testImplementation(libs.coroutines.core)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.retrofit.converterScalars)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.moshi)
  testImplementation(libs.moshi.kotlin)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.autoService.annotations)
  testImplementation(project(":eithernet:test-fixtures"))
}
