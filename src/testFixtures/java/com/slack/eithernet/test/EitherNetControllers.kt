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
import com.slack.eithernet.ExperimentalEitherNetApi
import com.slack.eithernet.test.Platform.Companion
import com.squareup.moshi.rawType
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaMethod

/** Returns a new [EitherNetController] for API service type [T]. */
inline fun <reified T : Any> newEitherNetController(): EitherNetController<T> {
  return newEitherNetController(T::class)
}

/** Returns a new [EitherNetController] for API service type [T]. */
fun <T : Any> newEitherNetController(service: Class<T>): EitherNetController<T> {
  return newEitherNetController(service.kotlin)
}

/** Returns a new [EitherNetController] for API service type [T]. */
fun <T : Any> newEitherNetController(service: KClass<T>): EitherNetController<T> {
  service.validateApi()
  // Get functions with retrofit annotations
  val endpoints =
    service.functions
      .filter { it.isApplicable }
      .map { createEndpointKey(it.javaMethod!!) } // We know javaMethod is present per the filter
      .associateWithTo(ConcurrentHashMap()) { ArrayDeque<SuspendedResult>() }
  val orchestrator = EitherNetTestOrchestrator(endpoints)
  val proxy = newProxy(service.java, orchestrator)
  return RealEitherNetController(orchestrator, proxy)
}

private val KFunction<*>.isApplicable: Boolean
  get() {
    // Default, static, synthetic, and bridge methods are not applicable
    return javaMethod?.let { method ->
      method.declaringClass != Object::class.java &&
        !method.isDefault &&
        !Modifier.isStatic(method.modifiers) &&
        !method.isSynthetic &&
        !method.isBridge
    } ?: false
  }

@OptIn(ExperimentalStdlibApi::class, ExperimentalEitherNetApi::class)
private fun KClass<*>.validateApi() {
  val validators = loadValidators()
  val errors = mutableListOf<String>()
  for (function in functions) {
    if (!function.isApplicable) {
      continue
    }
    if (!function.isSuspend) {
      errors += "- Function ${function.name} must be a suspend function for EitherNet to work."
    }
    if (function.returnType.javaType.rawType != ApiResult::class.java) {
      errors += "- Function ${function.name} must return ApiResult for EitherNet to work."
    }
    for (validator in validators) {
      validator.validate(this, function, errors)
    }
  }

  if (errors.isNotEmpty()) {
    error("Service errors found for $simpleName\n${errors.joinToString("\n")}")
  }
}

/**
 * Creates a new [Proxy] instance of the given [T] API service that delegates to the underlying
 * [orchestrator] for enqueued responses.
 *
 * This heavily mirrors Retrofit's own internal implementation of creating Proxies.
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T> newProxy(service: Class<T>, orchestrator: EitherNetTestOrchestrator): T {
  return Proxy.newProxyInstance(
    service.classLoader,
    arrayOf(service),
    object : InvocationHandler {
      override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        // If the method is a method from Object then defer to normal invocation.
        val finalArgs = args.orEmpty()
        if (method.declaringClass == Object::class.java) {
          return method.invoke(this, *finalArgs)
        }

        return if (Companion.INSTANCE.isDefaultMethod(method)) {
          Companion.INSTANCE.invokeDefaultMethod(method, service, proxy, finalArgs)
        } else {
          val key = createEndpointKey(method)
          check(finalArgs.isNotEmpty()) {
            "No arguments found on method ${key.name}, did you forget to add the 'suspend' modifier?"
          }
          val continuation = finalArgs.last()
          check(continuation is Continuation<*>) {
            "Last arg is not a Continuation, did you forget to add the 'suspend' modifier?"
          }
          val argsArray = Array(finalArgs.size - 1) { i -> finalArgs[i] }
          val body =
            orchestrator.endpoints.getValue(key).removeFirstOrNull()
              ?: error("No result enqueued for ${key.name}.")
          CoroutineTransformer.transform(
            argsArray,
            body,
            continuation as Continuation<ApiResult<*, *>>
          )
        }
      }
    }
  ) as T
}
