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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail
import okio.IOException

@Suppress("ThrowsCount")
class ExtensionsTest {

  @Test
  fun successOrNullWithSuccess() {
    val result = ApiResult.success("Hello")
    assertEquals("Hello", result.successOrNull())
  }

  @Test
  fun successOrNullWithFailure() {
    val result: ApiResult<*, *> = ApiResult.unknownFailure(Throwable())
    assertNull(result.successOrNull())
  }

  @Test
  fun successOrElseWithSuccess() {
    val result = ApiResult.success("Hello")
    assertEquals("Hello", result.successOrElse { error("") })
  }

  @Test
  fun successOrElseWithFailure() {
    val result = ApiResult.unknownFailure(Throwable())
    assertEquals("Hello", result.successOrElse { "Hello" })
  }

  @Test
  fun successOrElseWithCustomFailure() {
    val result = ApiResult.unknownFailure(Throwable())
    assertEquals(
      "Hello",
      result.successOrElse { failure ->
        when (failure) {
          is ApiResult.Failure.UnknownFailure -> "Hello"
          else -> throw AssertionError()
        }
      },
    )
  }

  @Test
  fun foldSuccess() {
    val result = ApiResult.success("Hello")
    val folded = result.fold({ it }, { "Failure" })
    assertEquals("Hello", folded)
  }

  @Test
  fun foldFailure() {
    val result = ApiResult.apiFailure("Hello")
    val folded = result.fold({ throw AssertionError() }, { "Failure" })
    assertEquals("Failure", folded)
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
    assertEquals("Failure", folded)
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
    assertEquals("Failure", folded)
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
    assertEquals("Failure", folded)
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
    assertEquals("Failure", folded)
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
    assertSame(exception, result.exceptionOrNull())
  }

  @Test
  fun exceptionOrNull_with_unknownFailure() {
    val exception = IOException()
    val result = ApiResult.unknownFailure(exception)
    assertSame(exception, result.exceptionOrNull())
  }

  @Test
  fun exceptionOrNull_with_throwableApiFailure() {
    val exception = IOException()
    val result = ApiResult.apiFailure(exception)
    assertSame(exception, result.exceptionOrNull())
  }

  @Test
  fun exceptionOrNull_with_throwableHttpFailure() {
    val exception = IOException()
    val result = ApiResult.httpFailure(404, exception)
    assertSame(exception, result.exceptionOrNull())
  }

  @Test
  fun exceptionOrNull_with_nonThrowableApiFailure() {
    val result = ApiResult.apiFailure("nope")
    assertNull(result.exceptionOrNull())
  }

  @Test
  fun exceptionOrNull_with_nonThrowableHttpFailure() {
    val result = ApiResult.httpFailure(404, "nope")
    assertNull(result.exceptionOrNull())
  }
}
