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

import com.squareup.moshi.internal.Util
import com.squareup.moshi.rawType
import java.lang.reflect.Type
import kotlin.coroutines.Continuation

/** A simple parameter endpoint key for use with [EndpointKey]. */
data class ParameterKey internal constructor(val type: Type)

internal fun createParameterKey(parameterType: Type): ParameterKey? {
  if (parameterType.rawType == Continuation::class.java) return null
  return ParameterKey(
    // Borrow an internal API from Moshi because it has nicer and more consistent Type
    // implementations that use safer equality checks. This matches how Moshi internally keys
    // cached adapters.
    Util.canonicalize(parameterType)
  )
}
