/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
@file:OptIn(ExperimentalContracts::class)

package com.slack.eithernet

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** If [ApiResult.Success], returns the underlying [T] value. Otherwise, returns null. */
public fun <T : Any, E : Any> ApiResult<T, E>.successOrNull(): T? =
  when (this) {
    is ApiResult.Success -> value
    else -> null
  }

/**
 * If [ApiResult.Success], returns the underlying [T] value. Otherwise, returns the result of the
 * [defaultValue] function.
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.successOrElse(
  defaultValue: (failure: ApiResult.Failure<E>) -> T
): T {
  contract { callsInPlace(defaultValue, InvocationKind.AT_MOST_ONCE) }
  return when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> defaultValue(this)
  }
}

/**
 * If [ApiResult.Success], returns the underlying [T] value. Otherwise, calls [body] with the
 * failure, which can either throw an exception or return early (since this function is inline).
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.successOrNothing(
  body: (failure: ApiResult.Failure<E>) -> Nothing
): T {
  contract { callsInPlace(body, InvocationKind.AT_MOST_ONCE) }
  return when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> body(this)
  }
}

/**
 * Returns the encapsulated [Throwable] exception of this failure type if one is available or null
 * if none are available.
 *
 * Note that if this is [ApiResult.Failure.HttpFailure] or [ApiResult.Failure.ApiFailure], the
 * `error` property will be returned IFF it's a [Throwable].
 */
public fun <E : Any> ApiResult.Failure<E>.exceptionOrNull(): Throwable? {
  return when (this) {
    is ApiResult.Failure.NetworkFailure -> error
    is ApiResult.Failure.UnknownFailure -> error
    is ApiResult.Failure.HttpFailure -> error as? Throwable?
    is ApiResult.Failure.ApiFailure -> error as? Throwable?
  }
}

/** Transforms an [ApiResult] into a [C] value. */
@Suppress("NOTHING_TO_INLINE") // Inline to allow contextual actions
@JvmName("foldWithValue")
public inline fun <T : Any, E : Any, C> ApiResult<T, E>.fold(
  noinline onSuccess: (value: T) -> C,
  noinline onFailure: (failure: ApiResult.Failure<E>) -> C,
): C {
  contract {
    callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
  }
  @Suppress("UNCHECKED_CAST")
  return fold(
    onSuccess,
    onFailure as (ApiResult.Failure.NetworkFailure) -> C,
    onFailure as (ApiResult.Failure.UnknownFailure) -> C,
    onFailure,
    onFailure,
  )
}

/** Transforms an [ApiResult] into a [C] value. */
@JvmName("foldWithValue")
public inline fun <T : Any, E : Any, C> ApiResult<T, E>.fold(
  onSuccess: (value: T) -> C,
  onNetworkFailure: (failure: ApiResult.Failure.NetworkFailure) -> C,
  onUnknownFailure: (failure: ApiResult.Failure.UnknownFailure) -> C,
  onHttpFailure: (failure: ApiResult.Failure.HttpFailure<E>) -> C,
  onApiFailure: (failure: ApiResult.Failure.ApiFailure<E>) -> C,
): C {
  contract {
    callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onNetworkFailure, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onUnknownFailure, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onHttpFailure, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onApiFailure, InvocationKind.AT_MOST_ONCE)
  }
  return when (this) {
    is ApiResult.Success -> onSuccess(value)
    is ApiResult.Failure.ApiFailure -> onApiFailure(this)
    is ApiResult.Failure.HttpFailure -> onHttpFailure(this)
    is ApiResult.Failure.NetworkFailure -> onNetworkFailure(this)
    is ApiResult.Failure.UnknownFailure -> onUnknownFailure(this)
  }
}

/**
 * Performs the given [action] on the encapsulated [ApiResult.Failure] if this instance represents
 * [failure][ApiResult.Failure]. Returns the original `ApiResult` unchanged.
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.onFailure(
  action: (failure: ApiResult.Failure<E>) -> Unit
): ApiResult<T, E> {
  contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
  if (this is ApiResult.Failure) action(this)
  return this
}

/**
 * Performs the given [action] on the encapsulated [ApiResult.Failure.HttpFailure] if this instance
 * represents [failure][ApiResult.Failure.HttpFailure]. Returns the original `ApiResult` unchanged.
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.onHttpFailure(
  action: (failure: ApiResult.Failure.HttpFailure<E>) -> Unit
): ApiResult<T, E> {
  contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
  if (this is ApiResult.Failure.HttpFailure) action(this)
  return this
}

/**
 * Performs the given [action] on the encapsulated [ApiResult.Failure.ApiFailure] if this instance
 * represents [failure][ApiResult.Failure.ApiFailure]. Returns the original `ApiResult` unchanged.
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.onApiFailure(
  action: (failure: ApiResult.Failure.ApiFailure<E>) -> Unit
): ApiResult<T, E> {
  contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
  if (this is ApiResult.Failure.ApiFailure) action(this)
  return this
}

/**
 * Performs the given [action] on the encapsulated [ApiResult.Failure.NetworkFailure] if this
 * instance represents [failure][ApiResult.Failure.NetworkFailure]. Returns the original `ApiResult`
 * unchanged.
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.onNetworkFailure(
  action: (failure: ApiResult.Failure.NetworkFailure) -> Unit
): ApiResult<T, E> {
  contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
  if (this is ApiResult.Failure.NetworkFailure) action(this)
  return this
}

/**
 * Performs the given [action] on the encapsulated [ApiResult.Failure.UnknownFailure] if this
 * instance represents [failure][ApiResult.Failure.UnknownFailure]. Returns the original `ApiResult`
 * unchanged.
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.onUnknownFailure(
  action: (failure: ApiResult.Failure.UnknownFailure) -> Unit
): ApiResult<T, E> {
  contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
  if (this is ApiResult.Failure.UnknownFailure) action(this)
  return this
}

/**
 * Performs the given [action] on the encapsulated value if this instance represents
 * [success][ApiResult.Success]. Returns the original `ApiResult` unchanged.
 */
public inline fun <T : Any, E : Any> ApiResult<T, E>.onSuccess(
  action: (value: T) -> Unit
): ApiResult<T, E> {
  contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
  if (this is ApiResult.Success) action(value)
  return this
}

// Deprecated functions

@Deprecated("Use fold instead", level = DeprecationLevel.HIDDEN)
@JvmName("fold")
@Suppress("NOTHING_TO_INLINE") // Inline to allow contextual actions
public inline fun <T : Any, E : Any, C> ApiResult<T, E>.foldOld(
  noinline onSuccess: (ApiResult.Success<T>) -> C,
  noinline onFailure: (ApiResult.Failure<E>) -> C,
): C {
  contract {
    callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
  }
  @Suppress("UNCHECKED_CAST")
  return foldOld(
    onSuccess,
    onFailure as (ApiResult.Failure.NetworkFailure) -> C,
    onFailure as (ApiResult.Failure.UnknownFailure) -> C,
    onFailure,
    onFailure,
  )
}

@Deprecated("Use fold instead", level = DeprecationLevel.HIDDEN)
@JvmName("fold")
public inline fun <T : Any, E : Any, C> ApiResult<T, E>.foldOld(
  onSuccess: (ApiResult.Success<T>) -> C,
  onNetworkFailure: (ApiResult.Failure.NetworkFailure) -> C,
  onUnknownFailure: (ApiResult.Failure.UnknownFailure) -> C,
  onHttpFailure: (ApiResult.Failure.HttpFailure<E>) -> C,
  onApiFailure: (ApiResult.Failure.ApiFailure<E>) -> C,
): C {
  contract {
    callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onNetworkFailure, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onUnknownFailure, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onHttpFailure, InvocationKind.AT_MOST_ONCE)
    callsInPlace(onApiFailure, InvocationKind.AT_MOST_ONCE)
  }
  return when (this) {
    is ApiResult.Success -> onSuccess(this)
    is ApiResult.Failure.ApiFailure -> onApiFailure(this)
    is ApiResult.Failure.HttpFailure -> onHttpFailure(this)
    is ApiResult.Failure.NetworkFailure -> onNetworkFailure(this)
    is ApiResult.Failure.UnknownFailure -> onUnknownFailure(this)
  }
}
