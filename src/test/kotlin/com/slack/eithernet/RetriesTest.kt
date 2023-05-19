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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RetriesTest {

  @Test
  fun `retry until success`() = runTest {
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
    assertThat(result).isEqualTo(expectedResult)
    assertThat(attempts).isEqualTo(3)
  }

  @Test
  fun `reach max attempts`() = runTest {
    var attempts = 0
    val expectedException = RuntimeException("error")
    val result =
      retryWithExponentialBackoff<String, Unit>(maxAttempts = 3) {
        attempts++
        ApiResult.unknownFailure(expectedException)
      }
    check(result is ApiResult.Failure.UnknownFailure)
    assertThat(result.error).isEqualTo(expectedException)
    assertThat(attempts).isEqualTo(3)
  }

  @Test
  fun `log failed attempts`() = runTest {
    var attempts = 0
    val recordedAttempts = mutableListOf<Int>()
    val expectedException = RuntimeException("error")
    val result =
      retryWithExponentialBackoff<String, Unit>(
        maxAttempts = 3,
        onFailure = { attempt, _ -> recordedAttempts.add(attempt) }
      ) {
        attempts++
        ApiResult.unknownFailure(expectedException)
      }
    check(result is ApiResult.Failure.UnknownFailure)
    assertThat(result.error).isEqualTo(expectedException)
    assertThat(attempts).isEqualTo(3)
    assertThat(recordedAttempts).hasSize(3)
  }

  @Test
  fun `does not exceed max delay`() = runTest {
    var attempts = 0
    var lastAttemptTime = currentTime
    retryWithExponentialBackoff(initialDelay = 100.milliseconds, maxDelay = 500.milliseconds) {
      attempts++
      if (attempts > 1) {
        val delay = currentTime - lastAttemptTime
        assertThat(delay >= 100).isTrue()
        assertThat(delay <= 500).isTrue()
      }
      lastAttemptTime = currentTime
      ApiResult.unknownFailure(RuntimeException("error"))
    }
  }

  @Test
  fun `applies jitter results in different delays`() = runTest {
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
    assertThat(delays.distinct().size).isEqualTo(delays.size)
  }

  @Test
  fun `does not apply jitter when jitter factor is 0`() = runTest {
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
    assertThat(delays.distinct().size).isEqualTo(1)
  }
}
