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

import kotlin.math.nextUp
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times
import kotlinx.coroutines.delay

/**
 * Retries a [block] of code with exponential backoff.
 *
 * This function will attempt the operation you give it up to [maxAttempts] times, multiplying the
 * delay between each attempt by [delayFactor], starting from [initialDelay] and not exceeding
 * [maxDelay]. If the operation continues to fail after [maxAttempts] times, it will return the last
 * failure result. If the operation succeeds at any point, it will immediately return the success
 * result.
 *
 * Note: This uses a default exponential backoff strategy with optional jitter. Depending on your
 * use case, you might want to customize the strategy, for example by handling certain kinds of
 * failures differently.
 *
 * @param maxAttempts The maximum number of times to retry the operation.
 * @param initialDelay The delay before the first retry.
 * @param delayFactor The factor by which the delay should increase after each failed attempt.
 * @param maxDelay The maximum delay between retries.
 * @param jitterFactor The maximum factor of jitter to introduce. For example, a value of 0.1 will
 *   introduce up to 10% jitter (both positive and negative).
 * @param onFailure An optional callback for failures, useful for logging.
 * @return The result of the operation if it's successful, or the last failure result if all
 *   attempts fail.
 */
@Suppress("LongParameterList")
public tailrec suspend fun <T : Any, E : Any> retryWithExponentialBackoff(
  maxAttempts: Int = 3,
  initialDelay: Duration = 500.milliseconds,
  delayFactor: Double = 2.0,
  maxDelay: Duration = 10.seconds,
  jitterFactor: Double = 0.25,
  onFailure: ((attempt: Int, result: ApiResult.Failure<E>) -> Unit)? = null,
  block: suspend () -> ApiResult<T, E>
): ApiResult<T, E> {
  require(maxAttempts > 0) { "maxAttempts must be greater than 0" }

  return when (val result = block()) {
    is ApiResult.Success -> result
    is ApiResult.Failure -> {
      onFailure?.invoke(maxAttempts, result)
      val attemptsRemaining = maxAttempts - 1
      if (attemptsRemaining == 0) {
        result
      } else {
        val jitter = 1 + Random.nextDouble(-jitterFactor, jitterFactor.nextUp())
        val nextDelay = (initialDelay + initialDelay * jitter).coerceAtMost(maxDelay)
        delay(nextDelay)
        retryWithExponentialBackoff(
          maxAttempts = attemptsRemaining,
          initialDelay = delayFactor * initialDelay,
          delayFactor = delayFactor,
          maxDelay = maxDelay,
          jitterFactor = jitterFactor,
          onFailure = onFailure,
          block = block
        )
      }
    }
  }
}
