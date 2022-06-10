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
package com.slack.eithernet.test

import com.slack.eithernet.ApiResult
import com.slack.eithernet.ExperimentalEitherNetApi
import com.slack.eithernet.InternalEitherNetApi

@OptIn(ExperimentalEitherNetApi::class)
internal class RealEitherNetController<T : Any>(
  private val orchestrator: EitherNetTestOrchestrator,
  override val api: T,
) : EitherNetController<T> {
  @InternalEitherNetApi
  override fun <S : Any, E : Any> unsafeEnqueue(
    key: EndpointKey,
    resultBody: suspend (args: Array<Any>) -> ApiResult<S, E>
  ) {
    orchestrator.endpoints.getValue(key).add(resultBody)
  }

  override fun assertNoMoreQueuedResults() {
    val errors = mutableListOf<String>()
    orchestrator.endpoints.forEach { (endpoint, resultsQueue) ->
      if (resultsQueue.isNotEmpty()) {
        val directObject =
          if (resultsQueue.size == 1) {
            "result"
          } else {
            "results"
          }
        errors += "-- ${endpoint.name}() has ${resultsQueue.size} unprocessed $directObject"
      }
    }
    if (errors.isNotEmpty()) {
      throw AssertionError("Found unprocessed ApiResults:\n${errors.joinToString("\n")}")
    }
  }
}
