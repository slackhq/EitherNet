/*
 * Copyright (C) 2024 Slack Technologies, LLC
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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
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
    }
    .joinToString(" ")
}

private fun KClassifier.simpleToString(): String {
  return when (this) {
    is KClass<*> -> qualifiedNameForComparison ?: "<anonymous>"
    is KTypeParameter -> simpleToString()
    else -> error("Unknown type classifier: $this")
  }
}

// Not every platform has KType.annotations, so we have to expect/actual this.
// We use a "real" EitherNetKType impl below to actually share all the logic and implement
// via class delegation on platforms.
internal expect class KTypeImpl(
  classifier: KClassifier?,
  arguments: List<KTypeProjection>,
  isMarkedNullable: Boolean,
  annotations: List<Annotation>,
) : KType, EitherNetKType {
  override val classifier: KClassifier?
  override val arguments: List<KTypeProjection>
  override val isMarkedNullable: Boolean
  override val annotations: List<Annotation>
}

@InternalEitherNetApi
public fun KType.canonicalize(): KType {
  return when (this) {
    is KTypeImpl -> this
    else ->
      KTypeImpl(
        classifier = classifier,
        arguments = arguments.map { it.copy(type = it.type?.canonicalize()) },
        isMarkedNullable = isMarkedNullable,
        annotations = emptyList(),
      )
  }
}

internal interface EitherNetKType {
  val classifier: KClassifier?
  val arguments: List<KTypeProjection>
  val isMarkedNullable: Boolean
  val annotations: List<Annotation>
}

internal class EitherNetKTypeImpl(
  override val classifier: KClassifier?,
  override val arguments: List<KTypeProjection>,
  override val isMarkedNullable: Boolean,
  override val annotations: List<Annotation>,
) : EitherNetKType {

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
        append("?")
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    // On the JVM we'd ideally do this too
    // if (javaClass != other?.javaClass) return false
    if (other !is KType) return false

    if (classifier != other.classifier) return false
    if (arguments != other.arguments) return false
    if (isMarkedNullable != other.isMarkedNullable) return false
    if (other is KTypeImpl) {
      if (annotations != other.annotations) return false
    }

    return true
  }

  override fun hashCode(): Int {
    var result = classifier?.hashCode() ?: 0
    result = 31 * result + arguments.hashCode()
    result = 31 * result + isMarkedNullable.hashCode()
    result = 31 * result + annotations.hashCode()
    return result
  }
}

internal class KTypeParameterImpl(
  override val isReified: Boolean,
  override val name: String,
  override val upperBounds: List<KType>,
  override val variance: KVariance,
) : KTypeParameter {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true

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

// Sneaky backdoor way of marking a value as non-null to the compiler and skip the null-check
// intrinsic.
// Safe to use (unstable) contracts since they're gone in the final bytecode
@OptIn(ExperimentalContracts::class)
@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> markNotNull(value: T?) {
  contract { returns() implies (value != null) }
}

/** Returns true if [this] and [other] are equal. */
@InternalEitherNetApi
public fun KType?.isFunctionallyEqualTo(other: KType?): Boolean {
  if (this === other) {
    return true // Also handles (a == null && b == null).
  }

  markNotNull(this)
  markNotNull(other)

  if (isMarkedNullable != other.isMarkedNullable) return false
  if (!arguments.contentEquals(other.arguments) { a, b -> a.type.isFunctionallyEqualTo(b.type) })
    return false

  // This isn't a supported type.
  when (val classifier = classifier) {
    is KClass<*> -> {
      if (classifier.qualifiedNameForComparison == "kotlin.Array") {
        // We can't programmatically create array types that implement equals fully, as the runtime
        // versions look at the underlying jClass that we can't get here. So we just do a simple
        // check for arrays.
        return (other.classifier as? KClass<*>?)?.qualifiedNameForComparison == "kotlin.Array"
      }
      return classifier == other.classifier
    }
    is KTypeParameter -> {
      val otherClassifier = other.classifier
      if (otherClassifier !is KTypeParameter) return false
      // TODO Use a plain KTypeParameter.equals again once
      // https://youtrack.jetbrains.com/issue/KT-39661 is fixed
      return (classifier.upperBounds.contentEquals(
        otherClassifier.upperBounds,
        KType::isFunctionallyEqualTo,
      ) && (classifier.name == otherClassifier.name))
    }
    else -> return false // This isn't a supported type.
  }
}

private fun <T> List<T>.contentEquals(
  other: List<T>,
  comparator: (a: T, b: T) -> Boolean,
): Boolean {
  if (size != other.size) return false
  for (i in indices) {
    val arg = get(i)
    val otherArg = other[i]
    // TODO do we care about variance?
    if (!comparator(arg, otherArg)) return false
  }
  return true
}
