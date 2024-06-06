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

import com.slack.eithernet.ApiResult.Failure.ApiFailure
import com.slack.eithernet.ApiResult.Failure.HttpFailure
import com.slack.eithernet.ApiResult.Failure.NetworkFailure
import com.slack.eithernet.ApiResult.Failure.UnknownFailure
import com.slack.eithernet.ApiResult.Success
import kotlin.reflect.KClass

/*
 * Common tags added automatically to different ApiResult types.
 */

/** Returns the tag attached with [T] as a key, or null if no tag is attached with that key. */
public inline fun <reified T : Any> ApiResult<*, *>.tag(): T? = tag(T::class)

/** Returns the tag attached with [klass] as a key, or null if no tag is attached with that key. */
public fun <T : Any> ApiResult<*, *>.tag(klass: KClass<T>): T? {
  val tags =
    when (this) {
      is ApiFailure -> tags
      is HttpFailure -> tags
      is NetworkFailure -> tags
      is UnknownFailure -> tags
      is Success -> tags
    }
  return tags[klass] as? T
}
