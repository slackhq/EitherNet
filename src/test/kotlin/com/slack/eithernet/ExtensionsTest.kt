/*
 * Copyright (C) 2023 Slack Technologies, LLC
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

import com.google.common.truth.Truth.assertThat
import kotlin.test.fail
import okio.IOException
import org.junit.Test

@Suppress("ThrowsCount")
class ExtensionsTest {

  @Test
  fun successOrNullWithSuccess() {
    val result = ApiResult.success("Hello")
    assertThat(result.successOrNull()).isEqualTo("Hello")
  }

  @Test
  fun successOrNullWithFailure() {
    val result: ApiResult<*, *> = ApiResult.unknownFailure(Throwable())
    assertThat(result.successOrNull()).isNull()
  }

  @Test
  fun successOrElseWithSuccess() {
    val result = ApiResult.success("Hello")
    assertThat(result.successOrElse { error("") }).isEqualTo("Hello")
  }

  @Test
  fun successOrElseWithFailure() {
    val result = ApiResult.unknownFailure(Throwable())
    assertThat(result.successOrElse { "Hello" }).isEqualTo("Hello")
  }

  @Test
  fun successOrElseWithCustomFailure() {
    val result = ApiResult.unknownFailure(Throwable())
    assertThat(
        result.successOrElse { failure ->
          when (failure) {
            is ApiResult.Failure.UnknownFailure -> "Hello"
            else -> throw AssertionError()
          }
        }
      )
      .isEqualTo("Hello")
  }

  @Test
  fun foldSuccess() {
    val result = ApiResult.success("Hello")
    val folded = result.fold({ it.value }, { "Failure" })
    assertThat(folded).isEqualTo("Hello")
  }

  @Test
  fun foldFailure() {
    val result = ApiResult.apiFailure("Hello")
    val folded = result.fold({ throw AssertionError() }, { "Failure" })
    assertThat(folded).isEqualTo("Failure")
  }

  @Test
  fun foldApiFailure() {
    val result = ApiResult.apiFailure("Hello")
    val folded =
      result.fold(
        onApiFailure = { "Failure" },
        onSuccess = { throw AssertionError() },
        onHttpFailure = { throw AssertionError() },
        onNetworkFailure = { throw AssertionError() },
        onUnknownFailure = { throw AssertionError() },
      )
    assertThat(folded).isEqualTo("Failure")
  }

  @Test
  fun foldHttpFailure() {
    val result = ApiResult.httpFailure(404, "Hello")
    val folded =
      result.fold(
        onSuccess = { throw AssertionError() },
        onHttpFailure = { "Failure" },
        onApiFailure = { throw AssertionError() },
        onNetworkFailure = { throw AssertionError() },
        onUnknownFailure = { throw AssertionError() },
      )
    assertThat(folded).isEqualTo("Failure")
  }

  @Test
  fun foldUnknownFailure() {
    val result = ApiResult.unknownFailure(Throwable())
    val folded =
      result.fold(
        onSuccess = { throw AssertionError() },
        onUnknownFailure = { "Failure" },
        onApiFailure = { throw AssertionError() },
        onNetworkFailure = { throw AssertionError() },
        onHttpFailure = { throw AssertionError() },
      )
    assertThat(folded).isEqualTo("Failure")
  }

  @Test
  fun foldNetworkFailure() {
    val result = ApiResult.networkFailure(IOException())
    val folded =
      result.fold(
        onNetworkFailure = { "Failure" },
        onSuccess = { throw AssertionError() },
        onApiFailure = { throw AssertionError() },
        onUnknownFailure = { throw AssertionError() },
        onHttpFailure = { throw AssertionError() },
      )
    assertThat(folded).isEqualTo("Failure")
  }

  @Test
  fun successOrNothingShouldEscape() {
    val result: ApiResult<*, *> = ApiResult.networkFailure(IOException())
    result.successOrNothing {
      return
    }
    fail("Should not reach here")
  }

  @Test
  fun exceptionOrNull_with_networkFailure() {
    val exception = IOException()
    val result = ApiResult.networkFailure(exception)
    assertThat(result.exceptionOrNull()).isSameInstanceAs(exception)
  }

  @Test
  fun exceptionOrNull_with_unknownFailure() {
    val exception = IOException()
    val result = ApiResult.unknownFailure(exception)
    assertThat(result.exceptionOrNull()).isSameInstanceAs(exception)
  }

  @Test
  fun exceptionOrNull_with_throwableApiFailure() {
    val exception = IOException()
    val result = ApiResult.apiFailure(exception)
    assertThat(result.exceptionOrNull()).isSameInstanceAs(exception)
  }

  @Test
  fun exceptionOrNull_with_throwableHttpFailure() {
    val exception = IOException()
    val result = ApiResult.httpFailure(404, exception)
    assertThat(result.exceptionOrNull()).isSameInstanceAs(exception)
  }

  @Test
  fun exceptionOrNull_with_nonThrowableApiFailure() {
    val result = ApiResult.apiFailure("nope")
    assertThat(result.exceptionOrNull()).isNull()
  }

  @Test
  fun exceptionOrNull_with_nonThrowableHttpFailure() {
    val result = ApiResult.httpFailure(404, "nope")
    assertThat(result.exceptionOrNull()).isNull()
  }
}
