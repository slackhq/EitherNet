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

import com.slack.eithernet.canonicalize
import kotlin.coroutines.Continuation
import kotlin.reflect.KType

/** A simple parameter endpoint key for use with [EndpointKey]. */
public class ParameterKey internal constructor(type: KType) {

  private val type = type.canonicalize()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as ParameterKey

    return type == other.type
  }

  override fun hashCode(): Int {
    return type.hashCode()
  }

  override fun toString(): String {
    return "ParameterKey(type=$type)"
  }
}

internal fun createParameterKey(parameterType: KType): ParameterKey? {
  if (parameterType.classifier == Continuation::class) return null
  return ParameterKey(parameterType)
}
