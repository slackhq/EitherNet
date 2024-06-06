package com.slack.eithernet

import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection

internal actual val KClass<*>.qualifiedNameForComparison: String? get() = qualifiedName

internal actual class KTypeImpl actual constructor(
  override val classifier: KClassifier?,
  override val arguments: List<KTypeProjection>,
  override val isMarkedNullable: Boolean,
  override val annotations: List<Annotation>,
) : KType, EitherNetKType {
  private val impl = EitherNetKTypeImpl(
    classifier,
    arguments,
    isMarkedNullable,
    annotations,
  )

  override fun equals(other: Any?) = impl == other

  override fun hashCode() = impl.hashCode()

  override fun toString() = impl.toString()
}