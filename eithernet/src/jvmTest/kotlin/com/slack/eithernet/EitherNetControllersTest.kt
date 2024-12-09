/*
 * Copyright (C) 2021 Slack Technologies, LLC
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
package com.slack.eithernet

import com.google.auto.service.AutoService
import com.google.common.truth.Truth.assertThat
import com.slack.eithernet.ApiResult.Failure.HttpFailure
import com.slack.eithernet.ApiResult.Success
import com.slack.eithernet.test.ApiValidator
import com.slack.eithernet.test.enqueue
import com.slack.eithernet.test.newEitherNetController
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.hasAnnotation
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

class EitherNetControllersTest {

  @Test
  fun happyPath() = runTest {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api

    testApi.enqueue(PandaApi::getPandas) { ApiResult.success("Po") }

    val result = api.getPandas()
    check(result is Success)
    assertThat(result.value).isEqualTo("Po")
  }

  @Test
  fun happyPath_scalar() = runTest {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api

    testApi.enqueue(PandaApi::getPandas, ApiResult.success("Po"))

    val result = api.getPandas()
    check(result is Success)
    assertThat(result.value).isEqualTo("Po")
  }

  @Test
  fun functionWithParams() = runTest {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api

    testApi.enqueue(PandaApi::getPandasWithParams, ApiResult.success("Po"))

    val result = api.getPandasWithParams(1)
    check(result is Success)
    assertThat(result.value).isEqualTo("Po")
  }

  @Test
  fun failure() = runTest {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api

    testApi.enqueue(PandaApi::getPandas, ApiResult.httpFailure(404))

    val result = api.getPandas()
    check(result is HttpFailure)
    assertThat(result.code).isEqualTo(404)
  }

  @Test
  fun mismatchedResultType() {
    val testApi = newEitherNetController<PandaApi>()

    try {
      // This will be an error in future kotlin versions fortunately. This test just covers that
      // case until then
      @Suppress("TYPE_INTERSECTION_AS_REIFIED_WARNING")
      testApi.enqueue(PandaApi::getPandas, ApiResult.success(3))
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e).hasMessageThat().contains("Type check failed")
    }
  }

  @Test
  fun mismatchedApiFunctions() {
    val testApi = newEitherNetController<PandaApi>()

    try {
      testApi.enqueue(AnotherApi::getPandas, ApiResult.success("Po"))
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e).hasMessageThat().contains("is not a member of target API")
    }
  }

  @Test
  fun invalidApi() {
    try {
      newEitherNetController<BadApi>()
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e)
        .hasMessageThat()
        .contains(
          """
        Service errors found for BadApi
        - Function missingApiResult must return ApiResult for EitherNet to work.
        - Function missingSlackEndpoint is missing @SlackEndpoint annotation.
        - Function missingSuspend must be a suspend function for EitherNet to work.
        """
            .trimIndent()
        )
    }
  }

  @Test
  fun unstubbed_failure() = runTest {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api

    testApi.enqueue(PandaApi::getPandasWithParams, ApiResult.success("Po"))

    try {
      api.getPandas()
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e).hasMessageThat().contains("No result enqueued for getPandas.")
    }
  }

  @Test
  fun custom_behavior_example() {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api

    testApi.enqueue(PandaApi::getPandas) {
      // Never "returns"!
      awaitCancellation()
    }

    assertFailsWith<TimeoutCancellationException> {
      runBlocking { withTimeout(1000) { api.getPandas() } }
    }
  }

  @Test
  fun custom_behavior_example2() {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api
    val expected = Exception()

    testApi.enqueue(PandaApi::getPandas) { throw expected }

    try {
      runBlocking { api.getPandas() }
      fail()
    } catch (e: Exception) {
      assertThat(e).isSameInstanceAs(expected)
    }
  }

  @Test
  fun assertNoMoreQueuedResults() {
    val testApi = newEitherNetController<PandaApi>()
    val api = testApi.api

    // Enqueue a result
    testApi.enqueue(PandaApi::getPandas, ApiResult.success("Po"))

    // Asserting before taking results fails correctly
    try {
      testApi.assertNoMoreQueuedResults()
      fail()
    } catch (e: AssertionError) {
      assertThat(e)
        .hasMessageThat()
        .isEqualTo(
          """
          Found unprocessed ApiResults:
          -- getPandas() has 1 unprocessed result
          """
            .trimIndent()
        )
    }

    // Now process the result
    runBlocking { api.getPandas() }

    // Now it successfully asserts
    testApi.assertNoMoreQueuedResults()
  }

  // Cover for inherited APIs
  interface BaseApi {
    @SlackEndpoint suspend fun getPandas(): ApiResult<String, Unit>
  }

  interface PandaApi : BaseApi {
    @SlackEndpoint suspend fun getPandasWithParams(count: Int): ApiResult<String, Unit>
  }

  interface AnotherApi {
    @SlackEndpoint suspend fun getPandas(): ApiResult<String, Unit>
  }

  interface BadApi {
    suspend fun missingSlackEndpoint(): ApiResult<String, Unit>

    @SlackEndpoint fun missingSuspend(): ApiResult<String, Unit>

    @SlackEndpoint suspend fun missingApiResult(): String

    fun defaultMethodIsSkipped() {}

    @JvmSynthetic fun syntheticMethodIsSkipped() {}

    companion object {
      @JvmStatic fun staticMethodsIsSkipped() {}
    }
  }
}

/** Example of a marker annotation for a validator to require */
annotation class SlackEndpoint

@AutoService(ApiValidator::class)
class SlackEndpointValidator : ApiValidator {
  override fun validate(apiClass: KClass<*>, function: KFunction<*>, errors: MutableList<String>) {
    if (!function.hasAnnotation<SlackEndpoint>()) {
      errors += "- Function ${function.name} is missing @SlackEndpoint annotation."
    }
  }
}
