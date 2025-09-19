import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
}

kotlin {
  // region KMP Targets
  jvm()
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  js(IR) {
    outputModuleName.set(property("POM_ARTIFACT_ID").toString())
    browser()
  }
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName.set(property("POM_ARTIFACT_ID").toString())
    browser()
  }
  // endregion

  applyDefaultHierarchyTemplate()


  sourceSets {
    commonMain {
      dependencies {
        api(project(":eithernet"))
        api(libs.ktor.client)
        implementation(libs.coroutines.core)
        implementation(libs.okio)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.coroutines.core)
        implementation(libs.coroutines.test)
        implementation(libs.kotlin.test)
      }
    }
  }
}