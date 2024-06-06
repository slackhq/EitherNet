package com.slack.eithernet.integration.retrofit

import com.slack.eithernet.ApiException
import com.slack.eithernet.ApiResult
import com.slack.eithernet.DecodeErrorBody
import com.slack.eithernet.createStatusCode
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import okhttp3.Request
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

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
                        ApiResult.Failure.ApiFailure(error = t.error, tags = mapOf(Request::class to call.request()))
                      ),
                    )
                  }
                  is IOException -> {
                    callback.onResponse(
                      call,
                      Response.success(
                        ApiResult.Failure.NetworkFailure(error = t, tags = mapOf(Request::class to call.request()))
                      ),
                    )
                  }
                  else -> {
                    callback.onResponse(
                      call,
                      Response.success(
                        ApiResult.Failure.UnknownFailure(error = t, tags = mapOf(Request::class to call.request()))
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
                      is ApiResult.Success -> result.withTags(result.tags + tags)
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
                              ApiResult.Failure.UnknownFailure(
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
                      ApiResult.Failure.HttpFailure(
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