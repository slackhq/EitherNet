/*
 * Copyright (C) 2020 Slack Technologies, LLC
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

import com.slack.eithernet.Util.canonicalize
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/** Returns a new [Type] representation of this [ResultType]. */
@Suppress("SpreadOperator") // This is _the worst_ detekt check
public fun ResultType.toType(): Type {
  return when {
    typeArgs.isEmpty() -> {
      val clazz = rawType.java
      if (isArray) {
        Types.arrayOf(clazz)
      } else {
        clazz
      }
    }
    else -> {
      val hasOwnerType = ownerType != Nothing::class
      if (hasOwnerType) {
        Types.newParameterizedTypeWithOwner(
          ownerType.javaObjectType,
          rawType.javaObjectType,
          *typeArgs.map(ResultType::toType).toTypedArray(),
        )
      } else {
        Types.newParameterizedType(
          rawType.javaObjectType,
          *typeArgs.map(ResultType::toType).toTypedArray(),
        )
      }
    }
  }
}

internal fun createStatusCode(code: Int): StatusCode {
  ApiResult.checkHttpFailureCode(code)
  return StatusCode(code)
}

internal fun createResultType(type: Type): ResultType {
  var ownerType: Type = Nothing::class.java
  val rawType: Class<*>
  val typeArgs: Array<ResultType>
  var isArray = false
  when (type) {
    is Class<*> -> {
      typeArgs = emptyArray()
      if (type.isArray) {
        rawType = type.componentType
        isArray = true
      } else {
        rawType = type
      }
    }
    is ParameterizedType -> {
      ownerType = type.ownerType ?: Nothing::class.java
      rawType = Types.getRawType(type)
      typeArgs =
        Array(type.actualTypeArguments.size) { i -> createResultType(type.actualTypeArguments[i]) }
    }
    is WildcardType -> return createResultType(Util.removeSubtypeWildcard(type))
    else -> error("Unrecognized type: $type")
  }

  return createResultType(
    canonicalize(ownerType) as Class<*>,
    canonicalize(rawType) as Class<*>,
    typeArgs,
    isArray,
  )
}

private fun createResultType(
  ownerType: Class<*>,
  rawType: Class<*>,
  typeArgs: Array<ResultType>,
  isArray: Boolean,
): ResultType =
  ResultType(
    rawType = rawType.kotlin,
    typeArgs = typeArgs,
    ownerType = ownerType.kotlin,
    isArray = isArray,
  )
