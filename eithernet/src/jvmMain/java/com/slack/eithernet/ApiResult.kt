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

import com.slack.eithernet.ApiResult.Failure.ApiFailure
import com.slack.eithernet.ApiResult.Failure.HttpFailure
import com.slack.eithernet.ApiResult.Failure.NetworkFailure
import com.slack.eithernet.ApiResult.Failure.UnknownFailure
import com.slack.eithernet.ApiResult.Success
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
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

/**
 * A custom [CallAdapter.Factory] for [ApiResult] calls. This creates a delegating adapter for
 * suspend function calls that return [ApiResult]. This facilitates returning all error types
 * through the possible [ApiResult] subtypes.
 */
public object ApiResultCallAdapterFactory : CallAdapter.Factory() {
  @Suppress("ReturnCount")
  override fun get(
    returnType: Type,
    annotations: Array<Annotation>,
    retrofit: Retrofit,
  ): CallAdapter<*, *>? {
    if (getRawType(returnType) != Call::class.java) {
      return null
    }
    val apiResultType = getParameterUpperBound(0, returnType as ParameterizedType)
    if (apiResultType !is ParameterizedType || apiResultType.rawType != ApiResult::class.java) {
      return null
    }

    val decodeErrorBody = annotations.any { it is DecodeErrorBody }
    return ApiResultCallAdapter(retrofit, apiResultType, decodeErrorBody, annotations)
  }

  private class ApiResultCallAdapter(
    private val retrofit: Retrofit,
    private val apiResultType: ParameterizedType,
    private val decodeErrorBody: Boolean,
    private val annotations: Array<Annotation>,
  ) : CallAdapter<ApiResult<*, *>, Call<ApiResult<*, *>>> {

    private companion object {
      private const val HTTP_NO_CONTENT = 204
      private const val HTTP_RESET_CONTENT = 205
    }

    override fun adapt(call: Call<ApiResult<*, *>>): Call<ApiResult<*, *>> {
      return object : Call<ApiResult<*, *>> by call {
        @Suppress("LongMethod")
        override fun enqueue(callback: Callback<ApiResult<*, *>>) {
          call.enqueue(
            object : Callback<ApiResult<*, *>> {
              override fun onFailure(call: Call<ApiResult<*, *>>, t: Throwable) {
                when (t) {
                  is ApiException -> {
                    callback.onResponse(
                      call,
                      Response.success(
                        ApiFailure(error = t.error, tags = mapOf(Request::class to call.request()))
                      ),
                    )
                  }
                  is IOException -> {
                    callback.onResponse(
                      call,
                      Response.success(
                        NetworkFailure(error = t, tags = mapOf(Request::class to call.request()))
                      ),
                    )
                  }
                  else -> {
                    callback.onResponse(
                      call,
                      Response.success(
                        UnknownFailure(error = t, tags = mapOf(Request::class to call.request()))
                      ),
                    )
                  }
                }
              }

              override fun onResponse(
                call: Call<ApiResult<*, *>>,
                response: Response<ApiResult<*, *>>,
              ) {
                if (response.isSuccessful) {
                  // Repackage the initial result with new tags with this call's request +
                  // response
                  val tags = mapOf(okhttp3.Response::class to response.raw())
                  val withTag =
                    when (val result = response.body()) {
                      is Success -> result.withTags(result.tags + tags)
                      null -> {
                        val responseCode = response.code()
                        if (
                          (responseCode == HTTP_NO_CONTENT || responseCode == HTTP_RESET_CONTENT) &&
                            apiResultType.actualTypeArguments[0] == Unit::class.java
                        ) {
                          @Suppress("UNCHECKED_CAST")
                          ApiResult.success(Unit).withTags(tags as Map<KClass<*>, Any>)
                        } else {
                          null
                        }
                      }
                      else -> null
                    }
                  callback.onResponse(call, Response.success(withTag))
                } else {
                  var errorBody: Any? = null
                  if (decodeErrorBody) {
                    response.errorBody()?.let { responseBody ->
                      // Don't try to decode empty bodies
                      // Unknown length bodies (i.e. -1L) are fine
                      if (responseBody.contentLength() == 0L) return@let
                      val errorType = apiResultType.actualTypeArguments[1]
                      val statusCode = createStatusCode(response.code())
                      val nextAnnotations = annotations + statusCode
                      @Suppress("TooGenericExceptionCaught")
                      errorBody =
                        try {
                          retrofit
                            .responseBodyConverter<Any>(errorType, nextAnnotations)
                            .convert(responseBody)
                        } catch (e: Throwable) {
                          @Suppress("UNCHECKED_CAST")
                          callback.onResponse(
                            call,
                            Response.success(
                              UnknownFailure(
                                error = e,
                                tags = mapOf(okhttp3.Response::class to response.raw()),
                              )
                            ),
                          )
                          return
                        }
                    }
                  }
                  @Suppress("UNCHECKED_CAST")
                  callback.onResponse(
                    call,
                    Response.success(
                      HttpFailure(
                        code = response.code(),
                        error = errorBody,
                        tags = mapOf(okhttp3.Response::class to response.raw()),
                      )
                    ),
                  )
                }
              }
            }
          )
        }
      }
    }

    override fun responseType(): Type = apiResultType
  }
}
