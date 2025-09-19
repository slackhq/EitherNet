// Copyright (C) 2025 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.eithernet.integration.ktor

import com.slack.eithernet.ApiResult
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import okio.IOException

class KtorEitherNetExtensionsTest {

  @Test
  fun `asKtorApiResult converts ConnectTimeoutException to networkFailure`() {
    val exception = ConnectTimeoutException("Connection timeout")

    val result: ApiResult<Nothing, Any> = exception.asKtorApiResult()

    assertIs<ApiResult.Failure.NetworkFailure>(result)
    assertIs<IOException>(result.error)
    assertEquals(exception, result.error.cause)
  }

  @Test
  fun `asKtorApiResult converts SocketTimeoutException to networkFailure`() {
    val exception = SocketTimeoutException("Socket timeout")

    val result: ApiResult<Nothing, Any> = exception.asKtorApiResult()

    assertIs<ApiResult.Failure.NetworkFailure>(result)
    assertIs<IOException>(result.error)
    assertEquals(exception, result.error.cause)
  }

  @Test
  fun `asKtorApiResult converts UnresolvedAddressException to networkFailure`() {
    val exception = UnresolvedAddressException()

    val result: ApiResult<Nothing, Any> = exception.asKtorApiResult()

    assertIs<ApiResult.Failure.NetworkFailure>(result)
    assertIs<IOException>(result.error)
    assertEquals(exception, result.error.cause)
  }

  @Test
  fun `asKtorApiResult converts unknown exceptions to unknownFailure`() {
    val exception = RuntimeException("Unknown error")

    val result: ApiResult<Nothing, Any> = exception.asKtorApiResult()

    assertIs<ApiResult.Failure.UnknownFailure>(result)
    assertEquals(exception, result.error)
  }

  @Test
  fun `asKtorApiResult preserves exception types for network errors`() {
    val connectTimeout = ConnectTimeoutException("Connect timeout")
    val socketTimeout = SocketTimeoutException("Socket timeout")
    val unresolvedAddress = UnresolvedAddressException()

    val connectResult: ApiResult<Nothing, Any> = connectTimeout.asKtorApiResult()
    val socketResult: ApiResult<Nothing, Any> = socketTimeout.asKtorApiResult()
    val addressResult: ApiResult<Nothing, Any> = unresolvedAddress.asKtorApiResult()

    assertIs<ApiResult.Failure.NetworkFailure>(connectResult)
    assertIs<ApiResult.Failure.NetworkFailure>(socketResult)
    assertIs<ApiResult.Failure.NetworkFailure>(addressResult)

    assertTrue(connectResult.error.cause is ConnectTimeoutException)
    assertTrue(socketResult.error.cause is SocketTimeoutException)
    assertTrue(addressResult.error.cause is UnresolvedAddressException)
  }
}
