package com.slack.retrofit

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass

/**
 * Represents a [java.lang.reflect.Type] via its components. Retrieve it from Retrofit annotations
 * via [nextAnnotations] and piece this back into a real instance via [toType].
 *
 * This API should be considered read-only.
 */
@Retention(RUNTIME)
public annotation class ResultType(
  val rawType: KClass<*>,
  val typeArgs: Array<ResultType> = [],
  val ownerType: KClass<*> = Nothing::class,
  // If it's an array, the rawType is used as the component type
  val isArray: Boolean
)