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
import com.slack.eithernet.InternalEitherNetApi
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/** Enqueues a suspended [resultBody]. */
public inline fun <reified T : Any, reified S : Any, reified E : Any> EitherNetController<T>
  .enqueue(
  ref: KFunction<ApiResult<S, E>>,
  noinline resultBody: suspend (args: Array<Any>) -> ApiResult<S, E>,
) {
  ref.validateTypes()
  val apiClass = T::class.java
  check(ref.javaMethod?.declaringClass?.isAssignableFrom(apiClass) == true) {
    "Given function ${ref.javaMethod?.declaringClass}.${ref.name} is not a member of target API $apiClass."
  }
  val key = EndpointKey.create(ref.javaMethod!!)
  @OptIn(InternalEitherNetApi::class) unsafeEnqueue(key, resultBody)
}

/** Enqueues a scalar [result] instance. */
public inline fun <reified T : Any, reified S : Any, reified E : Any> EitherNetController<T>
  .enqueue(ref: KFunction<ApiResult<S, E>>, result: ApiResult<S, E>): Unit = enqueue(ref) { result }
