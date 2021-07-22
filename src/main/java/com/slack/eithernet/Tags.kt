/*
 * Copyright (C) 2021 Slack Technologies, Inc.
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
@file:Suppress("UNCHECKED_CAST")
package com.slack.eithernet

import okhttp3.Request
import retrofit2.Response

/*
 * Common tags added automatically to different ApiResult types.
 */

public fun <T : Any> ApiResult.Success<T>.response(): Response<ApiResult<T, Nothing>>? {
  return tags[Response::class] as? Response<ApiResult<T, Nothing>>
}

public fun <T : Any> ApiResult.Success<T>.request(): Request? {
  return tags[Request::class] as? Request
}

public fun <E : Any> ApiResult.Failure.HttpFailure<E>.response(): Response<ApiResult<Nothing, E>>? {
  return tags[Response::class] as? Response<ApiResult<Nothing, E>>
}

public fun <E : Any> ApiResult.Failure.HttpFailure<E>.request(): Request? {
  return tags[Request::class] as? Request
}

public fun ApiResult.Failure.UnknownFailure.response(): Response<ApiResult<Nothing, Nothing>>? {
  return tags[Response::class] as? Response<ApiResult<Nothing, Nothing>>
}

public fun ApiResult.Failure.UnknownFailure.request(): Request? {
  return tags[Request::class] as? Request
}

public fun <E : Any> ApiResult.Failure.ApiFailure<E>.request(): Request? {
  return tags[Request::class] as? Request
}

public fun ApiResult.Failure.NetworkFailure.request(): Request? {
  return tags[Request::class] as? Request
}
