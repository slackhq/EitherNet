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
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

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

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  `java-test-fixtures`
  alias(libs.plugins.dokka)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
}

kotlin {
  jvm { withJava() }
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  compilerOptions {
    optIn.addAll("kotlin.ExperimentalStdlibApi", "kotlinx.coroutines.ExperimentalCoroutinesApi")
  }
  sourceSets {
    commonMain { dependencies { implementation(libs.coroutines.core) } }
    commonTest {
      dependencies {
        implementation(libs.coroutines.core)
        implementation(libs.coroutines.test)
        implementation(libs.kotlin.test)
      }
    }
    jvmMain { dependencies { implementation(libs.retrofit) } }
    jvmTest {
      dependencies {
        implementation(libs.coroutines.core)
        implementation(libs.coroutines.test)
        implementation(libs.retrofit.converterScalars)
        implementation(libs.okhttp)
        implementation(libs.okhttp.mockwebserver)
        implementation(libs.moshi)
        implementation(libs.moshi.kotlin)
        implementation(libs.junit)
        implementation(libs.truth)
        implementation(libs.autoService.annotations)
        implementation(project(":eithernet:test-fixtures"))
      }
    }
  }
}

dependencies {
  "kspJvmTest"(libs.autoService.ksp)
  testFixturesApi(project(":eithernet:test-fixtures"))
}
