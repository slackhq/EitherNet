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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okio.IOException

class RetriesTest {

  @Test
  fun retryUntilSuccess() = runTest {
    var attempts = 0
    val expectedResult = ApiResult.success("result")
    val result = retryWithExponentialBackoff {
      attempts++
      if (attempts < 3) {
        ApiResult.unknownFailure(RuntimeException("error"))
      } else {
        expectedResult
      }
    }
    assertEquals(expectedResult, result)
    assertEquals(3, attempts)
  }

  @Test
  fun reachMaxAttempts() = runTest {
    var attempts = 0
    val expectedException = RuntimeException("error")
    val result =
      retryWithExponentialBackoff<String, Unit>(maxAttempts = 3) {
        attempts++
        ApiResult.unknownFailure(expectedException)
      }
    check(result is ApiResult.Failure.UnknownFailure)
    assertEquals(expectedException, result.error)
    assertEquals(3, attempts)
  }

  @Test
  fun logFailedAttempts() = runTest {
    var attempts = 0
    val recordedAttempts = mutableListOf<ApiResult.Failure<Unit>>()
    val expectedException = RuntimeException("error")
    val result =
      retryWithExponentialBackoff<String, Unit>(
        maxAttempts = 3,
        onFailure = { failure -> recordedAttempts.add(failure) },
      ) {
        attempts++
        ApiResult.unknownFailure(expectedException)
      }
    check(result is ApiResult.Failure.UnknownFailure)
    assertEquals(expectedException, result.error)
    assertEquals(3, attempts)
    assertTrue(recordedAttempts.size == 3)
  }

  @Test
  fun doesNotExceedMaxDelay() = runTest {
    var attempts = 0
    var lastAttemptTime = currentTime
    retryWithExponentialBackoff(initialDelay = 100.milliseconds, maxDelay = 500.milliseconds) {
      attempts++
      if (attempts > 1) {
        val delay = currentTime - lastAttemptTime
        assertTrue(delay >= 100)
        assertTrue(delay <= 500)
      }
      lastAttemptTime = currentTime
      ApiResult.unknownFailure(RuntimeException("error"))
    }
  }

  @Test
  fun appliesJitterResultsInDifferentDelays() = runTest {
    var attempts = 0
    val delays = mutableListOf<Long>()
    var lastAttemptTime = currentTime
    retryWithExponentialBackoff(delayFactor = 1.0, jitterFactor = 0.5) {
      attempts++
      if (attempts > 1) {
        val delay = currentTime - lastAttemptTime
        lastAttemptTime = currentTime
        delays.add(delay)
      }
      ApiResult.unknownFailure(RuntimeException("error"))
    }
    // Check that delays are not all the same, which would indicate that jitter was not applied
    assertEquals(delays.size, delays.distinct().size)
  }

  @Test
  fun doesNotApplyJitterWhenJitterFactorIs0() = runTest {
    var attempts = 0
    val delays = mutableListOf<Long>()
    var lastAttemptTime = currentTime
    retryWithExponentialBackoff(delayFactor = 1.0, jitterFactor = 0.0) {
      attempts++
      if (attempts > 1) {
        val delay = currentTime - lastAttemptTime
        lastAttemptTime = currentTime
        delays.add(delay)
      }
      ApiResult.unknownFailure(RuntimeException("error"))
    }
    // Check that all delays are the same, which would indicate that jitter was not applied
    assertEquals(1, delays.distinct().size)
  }

  @Test
  fun doesNotRetryOnMatchingCondition() = runTest {
    var attempts = 0
    retryWithExponentialBackoff(
      maxAttempts = 5,
      shouldRetry = { it !is ApiResult.Failure.UnknownFailure },
    ) {
      attempts++
      if (attempts > 2) {
        ApiResult.unknownFailure(RuntimeException("error"))
      } else {
        ApiResult.networkFailure(IOException("error"))
      }
    }
    // Check that we only attempted until an unknown failure happened
    assertEquals(3, attempts)
  }
}
