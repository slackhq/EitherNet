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

import java.util.ServiceLoader
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * A simple callback API for validating APIs in [EitherNetController]. Implementations of this can
 * be supplied via [ServiceLoader].
 */
fun interface ApiValidator {
  /**
   * Callback to validate a given [function] from the given [apiClass].
   *
   * If any errors are found, add a detailed description to [errors].
   */
  fun validate(apiClass: KClass<*>, function: KFunction<*>, errors: MutableList<String>)
}

internal fun loadValidators(): Set<ApiValidator> =
  ServiceLoader.load(ApiValidator::class.java).toSet()
