/*
 * Copyright (C) 2021 Slack Technologies, LLC
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
import okhttp3.Response

/*
 * Common tags added automatically to different ApiResult types.
 */

/** Returns the original [Response] used for this call. */
public fun ApiResult<*, *>.response(): Response? {
  return tags[Response::class] as? Response
}

/** Returns the original [Request] used for this call. */
public fun ApiResult<*, *>.request(): Request? {
  return response()?.request() ?: tags[Request::class] as? Request
}
