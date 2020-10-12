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

import com.google.common.truth.Truth.assertThat
import com.slack.retrofit.ApiResult.Failure.ApiFailure
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create
import retrofit2.http.GET
import java.io.IOException
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME

class ApiResultTest {

  @get:Rule
  val server = MockWebServer()

  private lateinit var service: TestApi

  @Before
  fun before() {
    val retrofit = Retrofit.Builder()
      .baseUrl(server.url("/"))
      .addConverterFactory(ApiResultConverterFactory)
      .addCallAdapterFactory(ApiResultCallAdapterFactory)
      .addConverterFactory(UnitConverterFactory)
      .addConverterFactory(ErrorConverterFactory)
      .addConverterFactory(ScalarsConverterFactory.create())
      .validateEagerly(true)
      .build()

    service = retrofit.create()
  }

  @Test
  fun success() {
    val response = MockResponse()
      .setResponseCode(200)
      .setBody("Response!")

    server.enqueue(response)
    val result = runBlocking { service.testEndpoint() }
    assertThat(result).isEqualTo(ApiResult.Success("Response!"))
  }

  @Test
  fun successWithUnit() {
    val response = MockResponse()
      .setResponseCode(200)
      .setBody("Ignored!")

    server.enqueue(response)
    val result = runBlocking { service.unitEndpoint() }
    assertThat(result).isEqualTo(ApiResult.Success(Unit))
  }

  @Test
  fun failureWithUnit() {
    val response = MockResponse()
      .setResponseCode(404)
      .setBody("Ignored errors!")

    server.enqueue(response)
    val result = runBlocking { service.unitEndpoint() }
    assertThat(result).isEqualTo(ApiResult.httpFailure(404))
  }

  @Test
  fun apiHttpFailure() {
    val response = MockResponse()
      .setResponseCode(404)
      .setBody("Errors?")

    server.enqueue(response)
    val result = runBlocking { service.testEndpoint() }
    assertThat(result).isEqualTo(ApiResult.httpFailure(404))
  }

  @Test
  fun apiFailure() {
    val errorMessage = "${ErrorConverterFactory.ERROR_MARKER}This is an error message."
    val response = MockResponse()
      .setResponseCode(200)
      .setBody(errorMessage)

    server.enqueue(response)
    val result = runBlocking { service.testEndpoint() }
    check(result is ApiFailure)
    assertThat(result).isEqualTo(ApiResult.apiFailure(errorMessage))
  }

  @Test
  fun apiFailure_customMarker() {
    val errorMessage = "${ErrorConverterFactory.ERROR_MARKER}The rest of this is ignored."
    val response = MockResponse()
      .setResponseCode(200)
      .setBody(errorMessage)

    server.enqueue(response)
    val result = runBlocking { service.customErrorTypeEndpoint() }
    check(result is ApiFailure)
    assertThat(result).isEqualTo(ApiResult.apiFailure(ErrorMarker.MARKER))
  }

  @Test
  fun apiFailure_unknownErrorType() {
    val errorMessage = "${ErrorConverterFactory.ERROR_MARKER}The rest of this is ignored."
    val response = MockResponse()
      .setResponseCode(200)
      .setBody(errorMessage)

    server.enqueue(response)
    val result = runBlocking { service.unknownErrorTypeEndpoint() }
    check(result is ApiFailure)
    assertThat(result).isEqualTo(ApiResult.apiFailure(null))
  }

  @Test
  fun networkFailure() {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
    val result = runBlocking { service.testEndpoint() }
    assertThat(result).isInstanceOf(ApiResult.Failure.NetworkFailure::class.java)
    assertThat((result as ApiResult.Failure.NetworkFailure).error).isInstanceOf(IOException::class.java)
  }

  @Test
  fun networkFailureUnit() {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
    val result = runBlocking { service.unitEndpoint() }
    assertThat(result).isInstanceOf(ApiResult.Failure.NetworkFailure::class.java)
    assertThat((result as ApiResult.Failure.NetworkFailure).error).isInstanceOf(IOException::class.java)
  }

  @Test
  fun unknownFailure() {
    // Triggers an encoding failure
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("")
    )
    val result = runBlocking { service.badEndpoint() }
    assertThat(result).isInstanceOf(ApiResult.Failure.UnknownFailure::class.java)
    assertThat((result as ApiResult.Failure.UnknownFailure).error)
      .isInstanceOf(BadEndpointException::class.java)
  }

  interface TestApi {
    @GET("/")
    suspend fun testEndpoint(): ApiResult<String, String>

    @GET("/")
    suspend fun unitEndpoint(): ApiResult<Unit, String>

    @GET("/")
    suspend fun customErrorTypeEndpoint(): ApiResult<String, ErrorMarker>

    @GET("/")
    suspend fun unknownErrorTypeEndpoint(): ApiResult<String, Unit>

    @BadEndpoint
    @GET("/")
    suspend fun badEndpoint(): ApiResult<String, Unit>
  }

  /** Just here for testing. In a real endpoint this would be handled by something like MoshiConverterFactory. */
  object UnitConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
      type: Type,
      annotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
      return if (getRawType(type) == Unit::class.java) {
        ResponseBodyConverter
      } else {
        null
      }
    }

    override fun requestBodyConverter(
      type: Type,
      parameterAnnotations: Array<out Annotation>,
      methodAnnotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<*, RequestBody>? {
      throw NotImplementedError("Test only")
    }

    object ResponseBodyConverter : Converter<ResponseBody, Unit> {
      override fun convert(value: ResponseBody) {
        value.close()
      }
    }
  }

  enum class ErrorMarker {
    MARKER
  }

  @Retention(RUNTIME)
  annotation class BadEndpoint

  class BadEndpointException : RuntimeException()

  object ErrorConverterFactory : Converter.Factory() {
    // Indicates this body is an error
    const val ERROR_MARKER = "ERROR: "

    override fun responseBodyConverter(
      type: Type,
      annotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
      if (annotations.any { it is BadEndpoint }) {
        return Converter<ResponseBody, Any> { throw BadEndpointException() }
      }
      val (errorType, _) = annotations.nextAnnotations() ?: error("No error type found!")
      return ResponseBodyConverter(errorType.toType())
    }

    override fun requestBodyConverter(
      type: Type,
      parameterAnnotations: Array<out Annotation>,
      methodAnnotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<*, RequestBody>? {
      throw NotImplementedError("Test only")
    }

    class ResponseBodyConverter(
      private val errorType: Type
    ) : Converter<ResponseBody, String> {
      override fun convert(value: ResponseBody): String {
        val text = value.string()
        if (text.startsWith(ERROR_MARKER)) {
          when (errorType) {
            String::class.java -> throw ApiException(text)
            ErrorMarker::class.java -> throw ApiException(ErrorMarker.MARKER)
            else -> throw ApiException(null)
          }
        } else {
          return text
        }
      }
    }
  }
}
