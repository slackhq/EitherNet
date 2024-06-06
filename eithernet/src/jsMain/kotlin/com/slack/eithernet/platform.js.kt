package com.slack.eithernet

import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection

internal actual val KClass<*>.qualifiedNameForComparison: String?
  get() {
  // Unfortunately qualifiedName isn't implemented in JS
  return simpleName
}

internal actual class KTypeImpl actual constructor(
  override val classifier: KClassifier?,
  override val arguments: List<KTypeProjection>,
  override val isMarkedNullable: Boolean,
  override val annotations: List<Annotation>,
  override val isPlatformType: Boolean
) : KType, EitherNetKType by EitherNetKTypeImpl(
  classifier,
  arguments,
  isMarkedNullable,
  annotations,
  isPlatformType
)