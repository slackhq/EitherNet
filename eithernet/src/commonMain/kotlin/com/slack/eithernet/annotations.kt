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

/**
 * Returns a [Pair] of a [StatusCode] and subset of these annotations without that [StatusCode]
 * instance. This should be used in a custom Retrofit [retrofit2.Converter.Factory] to know what the
 * given status code of a non-2xx response was when decoding the error body. This can be useful for
 * contextually decoding error bodies based on the status code.
 *
 * ```
 * override fun responseBodyConverter(
 *   type: Type,
 *   annotations: Array<out Annotation>,
 *   retrofit: Retrofit
 * ): Converter<ResponseBody, *>? {
 *   val (statusCode, nextAnnotations) = annotations.statusCode()
 *     ?: return
 *   val errorType = when (statusCode) {
 *     401 -> Unuuthorized::class.java
 *     404 -> NotFound::class.java
 *     // ...
 *   }
 *   val errorDelegate = retrofit.nextResponseBodyConverter<Any>(this, errorType.toType(), nextAnnotations)
 *   return MyCustomBodyConverter(errorDelegate)
 * }
 * ```
 */
public fun Array<out Annotation>.statusCode(): Pair<StatusCode, Array<Annotation>>? {
  return nextAnnotations(StatusCode::class.java)
}

/**
 * Returns a [Pair] of a [ResultType] and subset of these annotations without that [ResultType]
 * instance. This should be used in a custom Retrofit [retrofit2.Converter.Factory] to determine the
 * error type of an [ApiResult] for the given endpoint.
 *
 * ```
 * override fun responseBodyConverter(
 *   type: Type,
 *   annotations: Array<out Annotation>,
 *   retrofit: Retrofit
 * ): Converter<ResponseBody, *>? {
 *   val (errorType, nextAnnotations) = annotations.errorType()
 *     ?: error("No error type found!")
 *   val errorDelegate = retrofit.nextResponseBodyConverter<Any>(this, errorType.toType(), nextAnnotations)
 *   return MyCustomBodyConverter(errorDelegate)
 * }
 * ```
 */
public fun Array<out Annotation>.errorType(): Pair<ResultType, Array<Annotation>>? {
  return nextAnnotations(ResultType::class.java)
}

private fun <A : Any> Array<out Annotation>.nextAnnotations(
  type: Class<A>
): Pair<A, Array<Annotation>>? {
  var nextIndex = 0
  val theseAnnotations = this
  var resultType: A? = null
  val nextAnnotations = arrayOfNulls<Annotation>(size - 1)
  for (i in indices) {
    val next = theseAnnotations[i]
    if (type.isInstance(next)) {
      @Suppress("UNCHECKED_CAST")
      resultType = next as A
    } else if (nextIndex < nextAnnotations.size) {
      nextAnnotations[nextIndex] = next
      nextIndex++
    }
  }

  return if (resultType != null) {
    @Suppress("UNCHECKED_CAST")
    resultType to (nextAnnotations as Array<Annotation>)
  } else {
    null
  }
}
