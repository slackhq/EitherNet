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

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * Retries a [block] of code with exponential backoff.
 *
 * @param maxAttempts The maximum number of times to retry the operation. Default is 5.
 * @param initialDelay The delay before the first retry. Default is 100 milliseconds.
 * @param maxDelay The maximum delay between retries. Default is 10,000 milliseconds.
 * @param factor The factor by which the delay should increase after each failed attempt. Default is
 *   2.0.
 * @param jitterFactor The maximum factor of jitter to introduce. For example, a value of 0.1 will
 *   introduce up to 10% jitter. Default is 0.
 * @param block The block of code to retry. This block should return an [ApiResult].
 * @return The result of the operation if it's successful, or the last failure result if all
 *   attempts fail.
 *
 * This function will attempt the operation you give it up to [maxAttempts] times, multiplying the
 * delay between each attempt by [factor], starting from [initialDelay] and not exceeding
 * [maxDelay]. If the operation continues to fail after [maxAttempts] times, it will return the last
 * failure result. If the operation succeeds at any point, it will immediately return the success
 * result.
 *
 * Note: This uses a default exponential backoff strategy with optional jitter. Depending on your
 * use case, you might want to customize the strategy, for example by handling certain kinds of
 * failures differently.
 */
public suspend fun <T : Any, E : Any> retryWithExponentialBackoff(
  maxAttempts: Int = 5,
  initialDelay: Duration = 1.seconds,
  maxDelay: Duration = 20.seconds,
  factor: Double = 2.0,
  jitterFactor: Double = 0.0,
  block: suspend () -> ApiResult<T, E>
): ApiResult<T, E> {
  require(maxAttempts > 0) { "maxAttempts must be greater than 0" }
  var currentDelay = initialDelay
  repeat(maxAttempts) { attempt ->
    when (val result = block()) {
      is ApiResult.Success -> return result
      is ApiResult.Failure -> {
        // TODO expose a logging hook here?
        if (attempt == maxAttempts - 1) {
          return result // return last failure
        } else {
          delay(currentDelay)
          // Compute a new delay using a combination of the factor and optional jitter.
          currentDelay = (currentDelay * factor).coerceAtMost(maxDelay)
          if (jitterFactor != 0.0) {
            // Note that Random.nextDouble requires a range,
            // so we provide it with -jitterFactor to jitterFactor.
            // It also cannot be 0, so only do this if jitter is non-zero
            val jitter = 1 + Random.nextDouble(-jitterFactor, jitterFactor)
            currentDelay = (currentDelay * jitter).coerceAtMost(maxDelay)
          }
        }
      }
    }
  }
  return ApiResult.unknownFailure(RuntimeException("Max attempts reached"))
}
