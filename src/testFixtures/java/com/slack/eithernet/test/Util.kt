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
package com.slack.eithernet.test

import com.slack.eithernet.ApiResult
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.coroutines.Dispatchers

internal typealias SuspendedResult = suspend (args: Array<Any>) -> ApiResult<*, *>

internal suspend fun SuspendedResult.awaitResponse(args: Array<Any>): ApiResult<*, *> = invoke(args)

/**
 * Force the calling coroutine to suspend before throwing [this].
 *
 * This is needed when a checked exception is synchronously caught in a [java.lang.reflect.Proxy]
 * invocation to avoid being wrapped in [java.lang.reflect.UndeclaredThrowableException].
 *
 * The implementation is derived from:
 * https://github.com/Kotlin/kotlinx.coroutines/pull/1667#issuecomment-556106349
 */
internal suspend fun Exception.suspendAndThrow(): Nothing {
  suspendCoroutineUninterceptedOrReturn<Nothing> { continuation ->
    Dispatchers.Default.dispatch(continuation.context) {
      continuation.intercepted().resumeWithException(this@suspendAndThrow)
    }
    COROUTINE_SUSPENDED
  }
}

/**
 * Validates that the type declarations on [this] endpoint function's return type match the reified
 * [S] and [E] types that [newEitherNetController] specifies. We do this in lieu of stronger type
 * checking from the IDE, which appears to throw type checking out the window due to [ApiResult]'s
 * use of covariant (i.e. `out`) types.
 */
@OptIn(ExperimentalStdlibApi::class)
@PublishedApi
internal inline fun <reified S : Any, reified E : Any> KFunction<ApiResult<S, E>>.validateTypes() {
  // Note that we don't use Moshi's nicer canonicalize APIs because it would lose the Kotlin type
  // information like nullability and intrinsic types.
  val type = returnType
  val (success, error) = type.arguments.map { it.type!! }

  check(success isEqualTo typeOf<S>()) {
    "Type check failed! Expected success type of '$success' but found '${typeOf<S>()}'. Ensure that your result type matches the target endpoint as the IDE won't correctly infer this!"
  }
  check(error isEqualTo typeOf<E>()) {
    "Type check failed! Expected error type of '$error' but found '${typeOf<E>()}'. Ensure that your result type matches the target endpoint as the IDE won't correctly infer this!"
  }
}

/**
 * Kotlin 1.6 introduces a new `platformTypeUpperBound` value to `KType.equals()` (under
 * [kotlin.jvm.internal.TypeReference]) that we can't control or access, so we compare these
 * directly.
 *
 * https://youtrack.jetbrains.com/issue/KT-49318
 */
@PublishedApi
internal infix fun KType.isEqualTo(other: KType): Boolean {
  return classifier == other.classifier &&
    arguments == other.arguments &&
    isMarkedNullable == other.isMarkedNullable
}
