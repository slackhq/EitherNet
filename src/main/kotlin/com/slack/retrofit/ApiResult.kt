/*
 * Copyright (C) 2020 Slack Technologies, Inc.
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
package com.slack.retrofit

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Represents a result from a traditional HTTP API. These are represented by 3 distinct types: a typed [Success] and two
 * untyped [Failure] types [Failure.NetworkFailure] and [Failure.ApiFailure]. This allows for simple handling of
 * results through a consistent, non-exceptional flow.
 *
 * ```
 * when (val result = myApi.someEndpoint()) {
 *   is Success -> doSomethingWith(result.response)
 *   is Failure -> when (result) -> {
 *     is NetworkFailure -> showError(result.error)
 *     is ApiFailure -> showError(result.code)
 *   }
 * }
 * ```
 *
 * Usually, user code for this would just simply show a generic error message for either [Failure] case, but a sealed
 * class is exposed for more specific error messaging.
 */
public sealed class ApiResult<out T, out E> {

  /** A successful result with the data available in [response]. */
  public data class Success<T : Any>(public val response: T) : ApiResult<T, Nothing>()

  public sealed class Failure<out E> : ApiResult<Nothing, E>() {

    /**
     * A network failure cause by a given [error]. This error is opaque, as the actual type could be from a number of
     * sources (connectivity, serialization issues, etc). This event is generally considered to be a non-recoverable and
     * should be used as signal or logging before attempting to gracefully degrade or retry.
     */
    public data class NetworkFailure(public val error: Throwable) : Failure<Nothing>()

    /**
     * An API failure. This indicates a non-2xx response *OR* a 200 response where and [ApiException] was thrown
     * during response body conversion. The [code] is available for reference.
     *
     * If this is a 200 response with an [ApiException], the [error] property will be best-effort populated with the
     * value of the [ApiException.error] property.
     */
    public data class ApiFailure<out E> internal constructor(
      public val code: Int,
      public val error: E?,
    ) : Failure<E>() {

      /** Returns whether or not this is an http failure (i.e. non-2xx response). */
      public val isHttpFailure: Boolean get() = code !in HTTP_SUCCESS_RANGE

      /** Returns whether or not this is an API failure (i.e. 2xx response with error). */
      public val isApiFailure: Boolean get() = error != null || code in HTTP_SUCCESS_RANGE

      public companion object {
        private const val OK = 200
        private val HTTP_SUCCESS_RANGE = OK..299

        @JvmStatic
        public fun httpFailure(code: Int): ApiFailure<Nothing> {
          require(code !in HTTP_SUCCESS_RANGE) {
            "Status code '$code' is a successful HTTP response. If you mean to use a $OK code + error string to " +
              "indicate an API error, use the apiFailure() factory."
          }
          return ApiFailure(code, null)
        }

        @Suppress("MemberNameEqualsClassName")
        @JvmStatic
        public fun <E> apiFailure(error: E? = null): ApiFailure<E> {
          return ApiFailure(OK, error)
        }
      }
    }
  }
}

/**
 * A custom [Converter.Factory] for [ApiResult] responses. This creates a delegating adapter for the underlying type
 * of the result, and wraps successful results in a new [ApiResult].
 */
public object ApiResultConverterFactory : Converter.Factory() {

  override fun responseBodyConverter(
    type: Type,
    annotations: Array<out Annotation>,
    retrofit: Retrofit
  ): Converter<ResponseBody, *>? {
    if (getRawType(type) != ApiResult::class.java) return null

    val typeParam = (type as ParameterizedType).actualTypeArguments[0]
    val delegateConverter = retrofit.nextResponseBodyConverter<Any>(
      this,
      typeParam,
      annotations
    )
    return ApiResultConverter(delegateConverter)
  }

  private class ApiResultConverter(
    private val delegate: Converter<ResponseBody, Any>
  ) : Converter<ResponseBody, ApiResult<*, *>> {
    override fun convert(value: ResponseBody): ApiResult<*, *>? {
      return delegate.convert(value)?.let { result ->
        ApiResult.Success(result)
      }
    }
  }
}

/**
 * A custom [CallAdapter.Factory] for [ApiResult] calls. This creates a delegating adapter for suspend function calls
 * that return [ApiResult]. This facilitates returning all error types through the possible [ApiResult] subtypes.
 */
public object ApiResultCallAdapterFactory : CallAdapter.Factory() {
  @Suppress("ReturnCount")
  override fun get(
    returnType: Type,
    annotations: Array<out Annotation>,
    retrofit: Retrofit
  ): CallAdapter<*, *>? {
    if (getRawType(returnType) != Call::class.java) {
      return null
    }
    val upperBound = getParameterUpperBound(0, returnType as ParameterizedType)
    if (getRawType(upperBound) != ApiResult::class.java) {
      return null
    }

    return ApiResultCallAdapter(upperBound)
  }

  private class ApiResultCallAdapter(
    private val responseType: Type
  ) : CallAdapter<ApiResult<*, *>, Call<ApiResult<*, *>>> {
    override fun adapt(call: Call<ApiResult<*, *>>): Call<ApiResult<*, *>> {
      return object : Call<ApiResult<*, *>> by call {
        override fun enqueue(callback: Callback<ApiResult<*, *>>) {
          call.enqueue(
            object : Callback<ApiResult<*, *>> {
              override fun onFailure(call: Call<ApiResult<*, *>>, t: Throwable) {
                if (t is ApiException) {
                  callback.onResponse(call, Response.success(ApiResult.Failure.ApiFailure.apiFailure(t.error)))
                  return
                }
                callback.onResponse(call, Response.success(ApiResult.Failure.NetworkFailure(t)))
              }

              override fun onResponse(call: Call<ApiResult<*, *>>, response: Response<ApiResult<*, *>>) {
                if (response.isSuccessful) {
                  callback.onResponse(call, response)
                } else {
                  callback.onResponse(call, Response.success(ApiResult.Failure.ApiFailure.httpFailure(response.code())))
                }
              }
            }
          )
        }
      }
    }

    override fun responseType(): Type = responseType
  }
}
