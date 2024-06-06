package com.slack.eithernet

import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance

private fun KTypeParameter.simpleToString(): String {
  return buildList {
    if (isReified) add("reified")
    when (variance) {
      KVariance.IN -> add("in")
      KVariance.OUT -> add("out")
      KVariance.INVARIANT -> {}
    }
    if (name.isNotEmpty()) add(name)
    if (upperBounds.isNotEmpty()) {
      add(":")
      addAll(upperBounds.map { it.toString() })
    }
  }.joinToString(" ")
}

private fun KClassifier.simpleToString(): String {
  return when (this) {
    is KClass<*> -> qualifiedName ?: "<anonymous>"
    is KTypeParameter -> simpleToString()
    else -> error("Unknown type classifier: $this")
  }
}

internal class KTypeImpl(
  override val classifier: KClassifier?,
  override val arguments: List<KTypeProjection>,
  override val isMarkedNullable: Boolean,
  override val annotations: List<Annotation>,
  val isPlatformType: Boolean
) : KType {

  override fun toString(): String {
    return buildString {
      if (annotations.isNotEmpty()) {
        annotations.joinTo(this, " ") { "@$it" }
        append(' ')
      }
      append(classifier?.simpleToString() ?: "")
      if (arguments.isNotEmpty()) {
        append("<")
        arguments.joinTo(this, ", ") { it.toString() }
        append(">")
      }
      if (isMarkedNullable) {
        if (isPlatformType) {
          append("!")
        } else {
          append("?")
        }
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KTypeImpl

    if (classifier != other.classifier) return false
    if (arguments != other.arguments) return false
    if (isMarkedNullable != other.isMarkedNullable) return false
    if (annotations != other.annotations) return false
    if (isPlatformType != other.isPlatformType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = classifier?.hashCode() ?: 0
    result = 31 * result + arguments.hashCode()
    result = 31 * result + isMarkedNullable.hashCode()
    result = 31 * result + annotations.hashCode()
    result = 31 * result + isPlatformType.hashCode()
    return result
  }
}

internal class KTypeParameterImpl(
  override val isReified: Boolean,
  override val name: String,
  override val upperBounds: List<KType>,
  override val variance: KVariance
) : KTypeParameter {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KTypeParameterImpl

    if (isReified != other.isReified) return false
    if (name != other.name) return false
    if (upperBounds != other.upperBounds) return false
    if (variance != other.variance) return false

    return true
  }

  override fun hashCode(): Int {
    var result = isReified.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + upperBounds.hashCode()
    result = 31 * result + variance.hashCode()
    return result
  }

  override fun toString(): String {
    return simpleToString()
  }
}