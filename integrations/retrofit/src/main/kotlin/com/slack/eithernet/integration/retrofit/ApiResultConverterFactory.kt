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
package com.slack.eithernet.integration.retrofit

import com.slack.eithernet.ApiException
import com.slack.eithernet.ApiResult
import com.slack.eithernet.ResultType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * A custom [Converter.Factory] for [ApiResult] responses. This creates a delegating adapter for the
 * underlying type of the result, and wraps successful results in a new [ApiResult].
 *
 * When delegating to a converter for the `Success` type, a [ResultType] annotation is added to the
 * forwarded annotations to allow for a downstream adapter to potentially contextually decode the
 * result and throw an [ApiException] with a decoded error type.
 */
public object ApiResultConverterFactory : Converter.Factory() {
  override fun responseBodyConverter(
    type: Type,
    annotations: Array<Annotation>,
    retrofit: Retrofit,
  ): Converter<ResponseBody, *>? {
    if (getRawType(type) != ApiResult::class.java) return null

    val successType = (type as ParameterizedType).actualTypeArguments[0]
    val errorType = type.actualTypeArguments[1]
    val errorResultType: Annotation = createResultType(errorType)
    val nextAnnotations = annotations + errorResultType
    val delegateConverter =
      retrofit.nextResponseBodyConverter<Any>(this, successType, nextAnnotations)
    return ApiResultConverter(delegateConverter)
  }

  private class ApiResultConverter(private val delegate: Converter<ResponseBody, Any>) :
    Converter<ResponseBody, ApiResult<*, *>> {
    override fun convert(value: ResponseBody): ApiResult<*, *>? {
      return delegate.convert(value)?.let(ApiResult.Companion::success)
    }
  }
}

