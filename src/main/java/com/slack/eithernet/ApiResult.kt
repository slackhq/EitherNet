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

import com.slack.eithernet.ApiResult.Failure
import com.slack.eithernet.ApiResult.Failure.ApiFailure
import com.slack.eithernet.ApiResult.Failure.HttpFailure
import com.slack.eithernet.ApiResult.Failure.NetworkFailure
import com.slack.eithernet.ApiResult.Failure.UnknownFailure
import com.slack.eithernet.ApiResult.Success
import okhttp3.Request
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
import java.util.Collections.unmodifiableMap
import kotlin.DeprecationLevel.ERROR
import kotlin.reflect.KClass

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
public sealed class ApiResult<out T : Any, out E : Any> {

  /** Extra metadata associated with the result such as original requests, responses, etc. */
  internal abstract val tags: Map<KClass<*>, Any>

  /** A successful result with the data available in [response]. */
  public class Success<T : Any> internal constructor(
    public val value: T,
    public override val tags: Map<KClass<*>, Any>
  ) : ApiResult<T, Nothing>() {

    /** Returns a new copy of this with the given [tags]. */
    public fun withTags(tags: Map<KClass<*>, Any>): Success<T> {
      return Success(value, unmodifiableMap(tags.toMap()))
    }

    @Deprecated("Use value. This will be removed in 1.0", ReplaceWith("value"), ERROR)
    public val response: T get() = value
  }

  /** Represents a failure of some sort. */
  public sealed class Failure<E : Any> : ApiResult<Nothing, E>() {

    /**
     * A network failure caused by a given [error]. This error is opaque, as the actual type could
     * be from a number of sources (connectivity, etc). This event is generally considered to be a
     * non-recoverable and should be used as signal or logging before attempting to gracefully
     * degrade or retry.
     */
    public class NetworkFailure internal constructor(
      public val error: IOException,
      public override val tags: Map<KClass<*>, Any>
    ) : Failure<Nothing>() {
      /** Returns a new copy of this with the given [tags]. */
      public fun withTags(tags: Map<KClass<*>, Any>): NetworkFailure {
        return NetworkFailure(error, unmodifiableMap(tags.toMap()))
      }
    }

    /**
     * An unknown failure caused by a given [error]. This error is opaque, as the actual type could
     * be from a number of sources (serialization issues, etc). This event is generally considered
     * to be a non-recoverable and should be used as signal or logging before attempting to
     * gracefully degrade or retry.
     */
    public class UnknownFailure internal constructor(
      public val error: Throwable,
      public override val tags: Map<KClass<*>, Any>
    ) : Failure<Nothing>() {
      /** Returns a new copy of this with the given [tags]. */
      public fun withTags(tags: Map<KClass<*>, Any>): UnknownFailure {
        return UnknownFailure(error, unmodifiableMap(tags.toMap()))
      }
    }

    /**
     * An HTTP failure. This indicates a 4xx or 5xx response. The [code] is available for reference.
     *
     * @property code The HTTP status code.
     * @property error An optional [error][E]. This would be from the error body of the response.
     */
    public class HttpFailure<E : Any> internal constructor(
      public val code: Int,
      public val error: E?,
      public override val tags: Map<KClass<*>, Any>
    ) : Failure<E>() {
      /** Returns a new copy of this with the given [tags]. */
      public fun withTags(tags: Map<KClass<*>, Any>): HttpFailure<E> {
        return HttpFailure(code, error, unmodifiableMap(tags.toMap()))
      }
    }

    /**
     * An API failure. This indicates a 2xx response where [ApiException] was thrown
     * during response body conversion.
     *
     * An [ApiException], the [error] property will be best-effort populated with the
     * value of the [ApiException.error] property.
     *
     * @property error An optional [error][E].
     */
    public class ApiFailure<E : Any> internal constructor(
      public val error: E?,
      public override val tags: Map<KClass<*>, Any>
    ) : Failure<E>() {
      /** Returns a new copy of this with the given [tags]. */
      public fun withTags(tags: Map<KClass<*>, Any>): ApiFailure<E> {
        return ApiFailure(error, unmodifiableMap(tags.toMap()))
      }
    }
  }

  public companion object {
    private const val OK = 200
    private val HTTP_SUCCESS_RANGE = OK..299
    private val HTTP_FAILURE_RANGE = 400..599

    /** Returns a new [Success] with given [value]. */
    @JvmOverloads
    public fun <T : Any> success(
      value: T,
      tags: Map<KClass<*>, Any> = emptyMap()
    ): Success<T> = Success(value, tags)

    /** Returns a new [HttpFailure] with given [code] and optional [error]. */
    @JvmOverloads
    public fun <E : Any> httpFailure(
      code: Int,
      error: E? = null,
      tags: Map<KClass<*>, Any> = emptyMap()
    ): HttpFailure<E> {
      checkHttpFailureCode(code)
      return HttpFailure(code, error, tags)
    }

    /** Returns a new [ApiFailure] with given [error]. */
    @JvmOverloads
    public fun <E : Any> apiFailure(
      error: E? = null,
      tags: Map<KClass<*>, Any> = emptyMap()
    ): ApiFailure<E> = ApiFailure(error, tags)

    /** Returns a new [NetworkFailure] with given [error]. */
    public fun networkFailure(
      error: IOException,
      tags: Map<KClass<*>, Any> = emptyMap()
    ): NetworkFailure = NetworkFailure(error, tags)

    /** Returns a new [UnknownFailure] with given [error]. */
    @JvmOverloads
    public fun unknownFailure(
      error: Throwable,
      tags: Map<KClass<*>, Any> = emptyMap()
    ): UnknownFailure = UnknownFailure(error, tags)

    internal fun checkHttpFailureCode(code: Int) {
      require(code !in HTTP_SUCCESS_RANGE) {
        "Status code '$code' is a successful HTTP response. If you mean to use a $OK code + error " +
          "string to indicate an API error, use the ApiResult.apiFailure() factory."
      }
      require(code in HTTP_FAILURE_RANGE) {
        "Status code '$code' is not a HTTP failure response. Must be a 4xx or 5xx code."
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
    annotations: Array<Annotation>,
    retrofit: Retrofit,
  ): Converter<ResponseBody, *>? {
    if (getRawType(type) != ApiResult::class.java) return null

    val successType = (type as ParameterizedType).actualTypeArguments[0]
    val errorType = type.actualTypeArguments[1]
    val errorResultType: Annotation = createResultType(errorType)
    val nextAnnotations = annotations + errorResultType
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
    private val annotations: Array<Annotation>,
  ) : CallAdapter<ApiResult<*, *>, Call<ApiResult<*, *>>> {
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
                        ApiResult.apiFailure(
                          error = t.error,
                          tags = mapOf(Request::class to call.request())
                        )
                      )
                    )
                  }
                  is IOException -> {
                    callback.onResponse(
                      call,
                      Response.success(
                        ApiResult.networkFailure(
                          error = t,
                          tags = mapOf(Request::class to call.request())
                        ),
                      )
                    )
                  }
                  else -> {
                    callback.onResponse(
                      call,
                      Response.success(
                        ApiResult.unknownFailure(
                          error = t,
                          tags = mapOf(Request::class to call.request())
                        ),
                      )
                    )
                  }
                }
              }

              override fun onResponse(
                call: Call<ApiResult<*, *>>,
                response: Response<ApiResult<*, *>>,
              ) {
                if (response.isSuccessful) {
                  // Repackage the initial result with new tags with this call's request + response
                  val tags = mapOf(
                    okhttp3.Response::class to response.raw()
                  )
                  val withTag = when (val result = response.body()) {
                    is Success -> result.withTags(result.tags + tags)
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
                      errorBody = try {
                        retrofit.responseBodyConverter<Any>(errorType, nextAnnotations)
                          .convert(responseBody)
                      } catch (e: Throwable) {
                        @Suppress("UNCHECKED_CAST")
                        callback.onResponse(
                          call,
                          Response.success(
                            ApiResult.unknownFailure(
                              error = e,
                              tags = mapOf(
                                okhttp3.Response::class to response.raw()
                              )
                            )
                          )
                        )
                        return
                      }
                    }
                  }
                  @Suppress("UNCHECKED_CAST")
                  callback.onResponse(
                    call,
                    Response.success(
                      ApiResult.httpFailure(
                        code = response.code(),
                        error = errorBody,
                        tags = mapOf(
                          okhttp3.Response::class to response.raw()
                        )
                      )
                    )
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
