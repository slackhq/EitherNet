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

import com.google.common.truth.Truth.assertThat
import com.slack.eithernet.ApiResult.Failure.ApiFailure
import com.slack.eithernet.ApiResult.Failure.HttpFailure
import com.slack.eithernet.ApiResult.Failure.NetworkFailure
import com.slack.eithernet.ApiResult.Failure.UnknownFailure
import com.slack.eithernet.ApiResult.Success
import java.io.IOException
import java.lang.reflect.Type
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create
import retrofit2.http.GET

class ApiResultTest {

  @get:Rule val server = MockWebServer()

  private lateinit var service: TestApi

  @Before
  fun before() {
    val retrofit =
      Retrofit.Builder()
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
  fun success() = runTest {
    val response = MockResponse().setResponseCode(200).setBody("Response!")

    server.enqueue(response)
    val result = service.testEndpoint()
    check(result is Success)
    assertThat(result.value).isEqualTo("Response!")

    // Assert tags
    assertThat(result.request()).isNotNull()
    assertThat(result.response()).isNotNull()
  }

  @Test
  fun successWithUnit() = runTest {
    val response = MockResponse().setResponseCode(200).setBody("Ignored!")

    server.enqueue(response)
    val result = service.unitEndpoint()
    check(result is Success)
    assertThat(result.value).isEqualTo(Unit)
  }

  @Test
  fun successWithUnitWithNullBody() = runTest {
    val response = MockResponse().setResponseCode(204)

    server.enqueue(response)
    val result = service.unitEndpoint()
    check(result is Success)
    assertThat(result.value).isEqualTo(Unit)
  }

  @Test
  fun failureWithUnit() = runTest {
    val response = MockResponse().setResponseCode(404).setBody("Ignored errors!")

    server.enqueue(response)
    val result = service.unitEndpoint()
    check(result is HttpFailure)
    assertThat(result.code).isEqualTo(404)
    assertThat(result.error).isNull()

    // Assert tags
    assertThat(result.request()).isNotNull()
    assertThat(result.response()).isNotNull()
  }

  @Test
  fun apiHttpFailure() = runTest {
    val response = MockResponse().setResponseCode(404)

    server.enqueue(response)
    val result = service.testEndpoint()
    check(result is HttpFailure)
    assertThat(result.code).isEqualTo(404)
    assertThat(result.error).isNull()
  }

  @Test
  fun apiHttpFailure_5xx() = runTest {
    val response = MockResponse().setResponseCode(500)

    server.enqueue(response)
    val result = service.testEndpoint()
    check(result is HttpFailure)
    assertThat(result.code).isEqualTo(500)
    assertThat(result.error).isNull()
  }

  @Test
  fun apiHttpFailure_withBody() = runTest {
    val response = MockResponse().setResponseCode(404).setBody("Custom errors for all")

    server.enqueue(response)
    val result = service.testEndpointWithErrorBody()
    check(result is HttpFailure)
    assertThat(result.code).isEqualTo(404)
    assertThat(result.error).isEqualTo("Custom errors for all")
  }

  @Test
  fun apiHttpFailure_withBodyEncodingIssue() = runTest {
    val response = MockResponse().setResponseCode(404).setBody("Custom errors for all")

    server.enqueue(response)
    val result = service.badEndpointWithErrorBody()
    check(result is UnknownFailure)
    assertThat(result.error).isInstanceOf(BadEndpointException::class.java)

    // Assert tags
    assertThat(result.request()).isNotNull()
    assertThat(result.response()).isNotNull()
    // ApiResult.Failure.ApiFailure<E>.request(
    // ApiResult.Failure.NetworkFailure.request(): Request
  }

  @Test
  fun apiHttpFailure_withBody_missingBody() = runTest {
    val response = MockResponse().setResponseCode(404)

    server.enqueue(response)
    val result = service.testEndpointWithErrorBody()
    check(result is HttpFailure)
    assertThat(result.code).isEqualTo(404)
    assertThat(result.error).isNull()
  }

  @Test
  fun apiFailure() = runTest {
    val errorMessage = "${ErrorConverterFactory.ERROR_MARKER}This is an error message."
    val response = MockResponse().setResponseCode(200).setBody(errorMessage)

    server.enqueue(response)
    val result = service.testEndpoint()
    check(result is ApiFailure)
    assertThat(result.error).isEqualTo(errorMessage)

    // Assert tags
    assertThat(result.request()).isNotNull()
  }

  @Test
  fun apiFailure_customMarker() = runTest {
    val errorMessage = "${ErrorConverterFactory.ERROR_MARKER}The rest of this is ignored."
    val response = MockResponse().setResponseCode(200).setBody(errorMessage)

    server.enqueue(response)
    val result = service.customErrorTypeEndpoint()
    check(result is ApiFailure)
    assertThat(result.error).isEqualTo(ErrorMarker.MARKER)
  }

  @Test
  fun apiFailure_unknownErrorType() = runTest {
    val errorMessage = "${ErrorConverterFactory.ERROR_MARKER}The rest of this is ignored."
    val response = MockResponse().setResponseCode(200).setBody(errorMessage)

    server.enqueue(response)
    val result = service.unknownErrorTypeEndpoint()
    check(result is ApiFailure)
    assertThat(result.error).isNull()
  }

  @Test
  fun networkFailure() = runTest {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
    val result = service.testEndpoint()
    check(result is NetworkFailure)
    assertThat(result.error).isInstanceOf(IOException::class.java)

    // Assert tags
    assertThat(result.request()).isNotNull()
  }

  @Test
  fun networkFailureUnit() = runTest {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
    val result = service.unitEndpoint()
    check(result is NetworkFailure)
    assertThat(result.error).isInstanceOf(IOException::class.java)
  }

  @Test
  fun unknownFailure() = runTest {
    // Triggers an encoding failure
    server.enqueue(MockResponse().setResponseCode(200).setBody(""))
    val result = service.badEndpoint()
    assertThat(result).isInstanceOf(UnknownFailure::class.java)
    assertThat((result as UnknownFailure).error).isInstanceOf(BadEndpointException::class.java)
  }

  @Test
  fun statusCodeTests() {
    // Basic value checks
    val fourHundred = createStatusCode(400)
    assertThat(fourHundred.value).isEqualTo(400)
    val fiveHundred = createStatusCode(500)
    assertThat(fiveHundred.value).isEqualTo(500)

    // Basic equality checks
    assertThat(fourHundred).isEqualTo(createStatusCode(400))
    assertThat(fourHundred).isNotEqualTo(fiveHundred)
  }

  @Test
  fun statusCode_200() {
    try {
      createStatusCode(200)
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageThat().contains("use the ApiResult.apiFailure()")
    }
  }

  @Test
  fun statusCode_non_error() {
    try {
      createStatusCode(307)
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageThat().contains("Must be a 4xx or 5xx code")
    }
  }

  @Test
  fun tags() {
    // ApiResult.Success<T>.response():
    // ApiResult.Success<T>.request():
    // ApiResult.Failure.HttpFailure<E>.response(
    // ApiResult.Failure.HttpFailure<E>.request(
    // ApiResult.Failure.UnknownFailure.response(): Response
    // ApiResult.Failure.UnknownFailure.request(): Request
    // ApiResult.Failure.ApiFailure<E>.request(
    // ApiResult.Failure.NetworkFailure.request(): Request
  }

  interface TestApi {
    @GET("/") suspend fun testEndpoint(): ApiResult<String, String>

    @DecodeErrorBody @GET("/") suspend fun testEndpointWithErrorBody(): ApiResult<String, String>

    @BadEndpoint
    @DecodeErrorBody
    @GET("/")
    suspend fun badEndpointWithErrorBody(): ApiResult<String, String>

    @GET("/") suspend fun unitEndpoint(): ApiResult<Unit, String>

    @GET("/") suspend fun customErrorTypeEndpoint(): ApiResult<String, ErrorMarker>

    @GET("/") suspend fun unknownErrorTypeEndpoint(): ApiResult<String, Unit>

    @BadEndpoint @GET("/") suspend fun badEndpoint(): ApiResult<String, Unit>
  }

  /**
   * Just here for testing. In a real endpoint this would be handled by something like
   * MoshiConverterFactory.
   */
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

  @Retention(RUNTIME) annotation class BadEndpoint

  class BadEndpointException : RuntimeException()

  object ErrorConverterFactory : Converter.Factory() {
    // Indicates this body is an error
    const val ERROR_MARKER = "ERROR: "

    @Suppress("ReturnCount")
    override fun responseBodyConverter(
      type: Type,
      annotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
      if (annotations.any { it is BadEndpoint }) {
        return Converter<ResponseBody, Any> { throw BadEndpointException() }
      } else if (annotations.any { it is StatusCode }) {
        return null
      }
      val (errorType, _) = annotations.errorType() ?: error("No error type found!")
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

    class ResponseBodyConverter(private val errorType: Type) : Converter<ResponseBody, String> {
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
