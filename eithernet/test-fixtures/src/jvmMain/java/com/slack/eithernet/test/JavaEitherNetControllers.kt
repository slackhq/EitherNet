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
@file:JvmName("JavaEitherNetControllers")

package com.slack.eithernet.test

import com.slack.eithernet.ApiResult
import com.slack.eithernet.InternalEitherNetApi

/** Enqueues a suspended [resultBody]. Note that this is not safe and only available for Java. */
public fun <T : Any, S : Any, E : Any> EitherNetController<T>.enqueueFromJava(
  clazz: Class<T>,
  methodName: String,
  resultBody: ApiResult<S, E>,
) {
  val method = clazz.declaredMethods.first { it.name == methodName }
  val key = EndpointKey.create(method)
  @OptIn(InternalEitherNetApi::class) unsafeEnqueue(key) { resultBody }
}
