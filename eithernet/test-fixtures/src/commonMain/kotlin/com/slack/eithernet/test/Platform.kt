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
import kotlin.reflect.KFunction

internal expect fun KClass<*>.getFunctions(): Collection<KFunction<*>>

internal expect fun <K, V> newConcurrentMap(): MutableMap<K, V>

internal expect fun KFunction<*>.toEndpointKey(): EndpointKey

internal expect val KFunction<*>.isApplicable: Boolean

internal expect fun KClass<*>.validateApi()

internal expect fun <T : Any> newServiceInstance(
  klass: KClass<T>,
  orchestrator: EitherNetTestOrchestrator,
): T
