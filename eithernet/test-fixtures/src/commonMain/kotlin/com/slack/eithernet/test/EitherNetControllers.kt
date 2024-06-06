/*
 * Copyright (C) 2024 Slack Technologies, LLC
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

import kotlin.reflect.KClass

/** Returns a new [EitherNetController] for API service type [T]. */
public inline fun <reified T : Any> newEitherNetController(): EitherNetController<T> {
  return newEitherNetController(T::class)
}

/** Returns a new [EitherNetController] for API service type [T]. */
public fun <T : Any> newEitherNetController(service: KClass<T>): EitherNetController<T> {
  service.validateApi()
  // Get functions with retrofit annotations
  val endpoints =
    service
      .getFunctions()
      .filter { it.isApplicable }
      .map { it.toEndpointKey() }
      .associateWithTo(newConcurrentMap()) { ArrayDeque<SuspendedResult>() }
  val orchestrator = EitherNetTestOrchestrator(endpoints)
  val proxy = newServiceInstance(service, orchestrator)
  return RealEitherNetController(orchestrator, proxy)
}
