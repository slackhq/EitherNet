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
    assertThat(result).isEqualTo(ApiFailure<String>(404, null))
  }

  @Test
  fun apiHttpFailure() {
    val response = MockResponse()
      .setResponseCode(404)
      .setBody("Errors?")

    server.enqueue(response)
    val result = runBlocking { service.testEndpoint() }
    check(result is ApiFailure)
    assertThat(result).isEqualTo(ApiFailure.httpFailure(404))
    assertThat(result.isApiFailure).isFalse()
    assertThat(result.isHttpFailure).isTrue()
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
    assertThat(result).isEqualTo(ApiFailure.apiFailure(errorMessage))
    assertThat(result.isApiFailure).isTrue()
    assertThat(result.isHttpFailure).isFalse()
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

  interface TestApi {
    @GET("/")
    suspend fun testEndpoint(): ApiResult<String, String>

    @GET("/")
    suspend fun unitEndpoint(): ApiResult<Unit, String>
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

  object ErrorConverterFactory : Converter.Factory() {
    // Indicates this body is an error
    const val ERROR_MARKER = "ERROR: "

    override fun responseBodyConverter(
      type: Type,
      annotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
      return ResponseBodyConverter
    }

    override fun requestBodyConverter(
      type: Type,
      parameterAnnotations: Array<out Annotation>,
      methodAnnotations: Array<out Annotation>,
      retrofit: Retrofit
    ): Converter<*, RequestBody>? {
      throw NotImplementedError("Test only")
    }

    object ResponseBodyConverter : Converter<ResponseBody, String> {
      override fun convert(value: ResponseBody): String {
        val text = value.string()
        if (text.startsWith(ERROR_MARKER)) {
          throw ApiException(text)
        } else {
          return text
        }
      }
    }
  }
}
