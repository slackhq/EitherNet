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
package com.slack.eithernet

import com.slack.eithernet.ApiResult.Failure
import com.slack.eithernet.ApiResult.Failure.ApiFailure
import com.slack.eithernet.ApiResult.Failure.HttpFailure
import com.slack.eithernet.ApiResult.Failure.NetworkFailure
import com.slack.eithernet.ApiResult.Failure.UnknownFailure
import com.slack.eithernet.ApiResult.Success
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Represents a result from a traditional HTTP API. [ApiResult] has two sealed subtypes: [Success]
 * and [Failure]. [Success] is typed to [T] with no error type and [Failure] is typed to [E] with
 * no success type.
 *
 * [Failure] in turn is represented by four sealed subtypes of its own: [Failure.NetworkFailure],
 * [Failure.ApiFailure], [Failure.HttpFailure], and [Failure.UnknownFailure]. This allows for
 * simple handling of results through a consistent, non-exceptional flow via sealed `when` branches.
 *
 * ```
 * when (val result = myApi.someEndpoint()) {
 *   is Success -> doSomethingWith(result.response)
 *   is Failure -> when (result) {
 *     is NetworkFailure -> showError(result.error)
 *     is HttpFailure -> showError(result.code)
 *     is ApiFailure -> showError(result.error)
 *     is UnknownError -> showError(result.error)
 *   }
 * }
 * ```
 *
 * Usually, user code for this could just simply show a generic error message for a [Failure]
 * case, but a sealed class is exposed for more specific error messaging.
 */
public sealed class ApiResult<out T, out E> {

  /** A successful result with the data available in [response]. */
  public data class Success<T : Any>(public val response: T) : ApiResult<T, Nothing>()

  /** Represents a failure of some sort. */
  public sealed class Failure<out E> : ApiResult<Nothing, E>() {

    /**
     * A network failure caused by a given [error]. This error is opaque, as the actual type could
     * be from a number of sources (connectivity, etc). This event is generally considered to be a
     * non-recoverable and should be used as signal or logging before attempting to gracefully
     * degrade or retry.
     */
    public data class NetworkFailure internal constructor(
      public val error: IOException,
    ) : Failure<Nothing>()

    /**
     * An unknown failure caused by a given [error]. This error is opaque, as the actual type could
     * be from a number of sources (serialization issues, etc). This event is generally considered
     * to be a non-recoverable and should be used as signal or logging before attempting to
     * gracefully degrade or retry.
     */
    public data class UnknownFailure internal constructor(
      public val error: Throwable,
    ) : Failure<Nothing>()

    /**
     * An HTTP failure. This indicates a 4xx response. The [code] is available for reference.
     *
     * @property code The HTTP status code.
     * @property error An optional [error][E]. This would be from the error body of the response.
     */
    public data class HttpFailure<out E> internal constructor(
      public val code: Int,
      public val error: E?,
    ) : Failure<E>()

    /**
     * An API failure. This indicates a 2xx response where [ApiException] was thrown
     * during response body conversion.
     *
     * An [ApiException], the [error] property will be best-effort populated with the
     * value of the [ApiException.error] property.
     *
     * @property error An optional [error][E].
     */
    public data class ApiFailure<out E> internal constructor(public val error: E?) : Failure<E>()
  }

  public companion object {
    private const val OK = 200
    private val HTTP_SUCCESS_RANGE = OK..299
    private val HTTP_FAILURE_RANGE = 400..499

    /** Returns a new [HttpFailure] with given [code] and optional [error]. */
    public fun <E> httpFailure(code: Int, error: E? = null): HttpFailure<E> {
      checkHttpFailureCode(code)
      return HttpFailure(code, error)
    }

    /** Returns a new [ApiFailure] with given [error]. */
    public fun <E> apiFailure(error: E? = null): ApiFailure<E> = ApiFailure(error)

    /** Returns a new [NetworkFailure] with given [error]. */
    public fun networkFailure(error: IOException): NetworkFailure = NetworkFailure(error)

    /** Returns a new [UnknownFailure] with given [error]. */
    public fun unknownFailure(error: Throwable): UnknownFailure = UnknownFailure(error)

    internal fun checkHttpFailureCode(code: Int) {
      require(code !in HTTP_SUCCESS_RANGE) {
        "Status code '$code' is a successful HTTP response. If you mean to use a $OK code + error " +
          "string to indicate an API error, use the ApiResult.apiFailure() factory."
      }
      require(code in HTTP_FAILURE_RANGE) {
        "Status code '$code' is not a HTTP failure response. Must be a 4xx code."
      }
    }
  }
}

/**
 * A custom [Converter.Factory] for [ApiResult] responses. This creates a delegating adapter for the underlying type
 * of the result, and wraps successful results in a new [ApiResult].
 *
 * When delegating to a converter for the `Success` type, a [ResultType] annotation is added to
 * the forwarded annotations to allow for a downstream adapter to potentially contextually decode
 * the result and throw an [ApiException] with a decoded error type.
 */
public object ApiResultConverterFactory : Converter.Factory() {

  override fun responseBodyConverter(
    type: Type,
    annotations: Array<out Annotation>,
    retrofit: Retrofit,
  ): Converter<ResponseBody, *>? {
    if (getRawType(type) != ApiResult::class.java) return null

    val successType = (type as ParameterizedType).actualTypeArguments[0]
    val errorType = type.actualTypeArguments[1]
    val errorResultType: Annotation = createResultType(errorType)
    val nextAnnotations = Array(annotations.size + 1) { i ->
      if (i < annotations.size) {
        annotations[i]
      } else {
        errorResultType
      }
    }
    val delegateConverter = retrofit.nextResponseBodyConverter<Any>(
      this,
      successType,
      nextAnnotations
    )
    return ApiResultConverter(delegateConverter)
  }

  private class ApiResultConverter(
    private val delegate: Converter<ResponseBody, Any>,
  ) : Converter<ResponseBody, ApiResult<*, *>> {
    override fun convert(value: ResponseBody): ApiResult<*, *>? {
      return delegate.convert(value)?.let(::Success)
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
    annotations: Array<out Annotation>,
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
    return ApiResultCallAdapter(
      retrofit,
      apiResultType,
      decodeErrorBody,
      annotations
    )
  }

  private class ApiResultCallAdapter(
    private val retrofit: Retrofit,
    private val apiResultType: ParameterizedType,
    private val decodeErrorBody: Boolean,
    private val annotations: Array<out Annotation>,
  ) : CallAdapter<ApiResult<*, *>, Call<ApiResult<*, *>>> {
    override fun adapt(call: Call<ApiResult<*, *>>): Call<ApiResult<*, *>> {
      return object : Call<ApiResult<*, *>> by call {
        override fun enqueue(callback: Callback<ApiResult<*, *>>) {
          call.enqueue(
            object : Callback<ApiResult<*, *>> {
              override fun onFailure(call: Call<ApiResult<*, *>>, t: Throwable) {
                when (t) {
                  is ApiException -> {
                    callback.onResponse(call, Response.success(ApiResult.apiFailure(t.error)))
                  }
                  is IOException -> {
                    callback.onResponse(call, Response.success(ApiResult.networkFailure(t)))
                  }
                  else -> {
                    callback.onResponse(call, Response.success(ApiResult.unknownFailure(t)))
                  }
                }
              }

              override fun onResponse(
                call: Call<ApiResult<*, *>>,
                response: Response<ApiResult<*, *>>,
              ) {
                if (response.isSuccessful) {
                  callback.onResponse(call, response)
                } else {
                  var errorBody: Any? = null
                  if (decodeErrorBody) {
                    response.errorBody()?.let { responseBody ->
                      // Don't try to decode empty bodies
                      // Unknown length bodies (i.e. -1L) are fine
                      if (responseBody.contentLength() == 0L) return@let
                      val errorType = apiResultType.actualTypeArguments[1]
                      val statusCode = createStatusCode(response.code())
                      val nextAnnotations = arrayOfNulls<Annotation>(annotations.size + 1)
                      nextAnnotations[0] = statusCode
                      annotations.copyInto(nextAnnotations, 1)
                      errorBody = try {
                        retrofit.responseBodyConverter<Any>(errorType, nextAnnotations)
                          .convert(responseBody)
                      } catch (e: Throwable) {
                        callback.onResponse(call, Response.success(ApiResult.unknownFailure(e)))
                        return
                      }
                    }
                  }
                  callback.onResponse(
                    call,
                    Response.success(ApiResult.httpFailure(response.code(), errorBody))
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
