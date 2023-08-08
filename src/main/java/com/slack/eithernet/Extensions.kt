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
package com.slack.eithernet

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
  defaultValue: (ApiResult.Failure<E>) -> T
): T =
  when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> defaultValue(this)
  }

/** Transforms an [ApiResult] into a [C] value. */
public fun <T : Any, E : Any, C> ApiResult<T, E>.fold(
  onSuccess: (ApiResult.Success<T>) -> C,
  onFailure: (ApiResult.Failure<E>) -> C,
): C {
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
public fun <T : Any, E : Any, C> ApiResult<T, E>.fold(
  onSuccess: (ApiResult.Success<T>) -> C,
  onNetworkFailure: (ApiResult.Failure.NetworkFailure) -> C,
  onUnknownFailure: (ApiResult.Failure.UnknownFailure) -> C,
  onHttpFailure: (ApiResult.Failure.HttpFailure<E>) -> C,
  onApiFailure: (ApiResult.Failure.ApiFailure<E>) -> C,
): C {
  return when (this) {
    is ApiResult.Success -> onSuccess(this)
    is ApiResult.Failure.ApiFailure -> onApiFailure(this)
    is ApiResult.Failure.HttpFailure -> onHttpFailure(this)
    is ApiResult.Failure.NetworkFailure -> onNetworkFailure(this)
    is ApiResult.Failure.UnknownFailure -> onUnknownFailure(this)
  }
}
