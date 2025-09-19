// Copyright (C) 2025 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.eithernet.integration.ktor

import com.slack.eithernet.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.util.network.UnresolvedAddressException
import okio.IOException

/**
 * Converts an [HttpResponse] returned by [block] to an [ApiResult] with the response body as the
 * success value.
 */
public suspend inline fun <reified T : Any, E : Any> HttpClient.apiResultOf(
  block: HttpClient.() -> HttpResponse
): ApiResult<T, E> {
  return try {
    val response = block()
    ApiResult.success(response.body<T>())
  } catch (e: Exception) {
    e.asKtorApiResult()
  }
}

@PublishedApi
internal fun <E : Any> Exception.asKtorApiResult(): ApiResult<Nothing, E> {
  return when (this) {
    is ClientRequestException -> {
      // 4xx errors
      ApiResult.httpFailure(response.status.value)
    }
    is ServerResponseException -> {
      // 5xx errors
      ApiResult.httpFailure(response.status.value)
    }
    is ConnectTimeoutException -> {
      ApiResult.networkFailure(IOException("", cause = this))
    }
    is SocketTimeoutException -> {
      ApiResult.networkFailure(IOException("", cause = this))
    }
    is UnresolvedAddressException -> {
      ApiResult.networkFailure(IOException("", cause = this))
    }
    else -> {
      ApiResult.unknownFailure(this)
    }
  }
}