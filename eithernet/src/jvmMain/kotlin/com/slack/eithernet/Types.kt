/*
 * Copyright (C) 2008 Slack Technologies, LLC
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

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance.INVARIANT

/** Returns the raw [Class] type of this type. */
internal val Type.rawType: Class<*>
  get() = Types.getRawType(this)

/** Returns a [GenericArrayType] with [this] as its [GenericArrayType.getGenericComponentType]. */
internal fun Type.asArrayType(): GenericArrayType = Types.arrayOf(this)

/**
 * Creates a new [KType] representation of this [Type] for use with Moshi serialization. Note that
 * [wildcards][WildcardType] are stripped away in [type projections][KTypeProjection] as they are
 * not relevant for serialization and are also not standalone [KType] subtypes in Kotlin.
 */
@InternalEitherNetApi
public fun Type.toKType(
  isMarkedNullable: Boolean = true,
  annotations: List<Annotation> = emptyList(),
): KType {
  return when (this) {
    is Class<*> -> KTypeImpl(kotlin, emptyList(), isMarkedNullable, annotations)
    is ParameterizedType ->
      KTypeImpl(
        classifier = (rawType as Class<*>).kotlin,
        arguments = actualTypeArguments.map { it.toKTypeProjection() },
        isMarkedNullable = isMarkedNullable,
        annotations = annotations,
      )
    is GenericArrayType -> {
      KTypeImpl(
        classifier = rawType.kotlin,
        arguments = listOf(genericComponentType.toKTypeProjection()),
        isMarkedNullable = isMarkedNullable,
        annotations = annotations,
      )
    }
    is WildcardType -> removeSubtypeWildcard().toKType(isMarkedNullable, annotations)
    is TypeVariable<*> ->
      KTypeImpl(
        classifier = KTypeParameterImpl(false, name, bounds.map { it.toKType() }, INVARIANT),
        arguments = emptyList(),
        isMarkedNullable = isMarkedNullable,
        annotations = annotations,
      )
    else -> throw IllegalArgumentException("Unsupported type: $this")
  }
}

/** Creates a new [KTypeProjection] representation of this [Type] for use in [KType.arguments]. */
@InternalEitherNetApi
public fun Type.toKTypeProjection(): KTypeProjection {
  return when (this) {
    is Class<*>,
    is ParameterizedType,
    is TypeVariable<*> -> KTypeProjection.invariant(toKType())
    is WildcardType -> {
      val lowerBounds = lowerBounds
      val upperBounds = upperBounds
      if (lowerBounds.isEmpty() && upperBounds.isEmpty()) {
        return KTypeProjection.STAR
      }
      return if (lowerBounds.isNotEmpty()) {
        KTypeProjection.contravariant(lowerBounds[0].toKType())
      } else {
        KTypeProjection.invariant(upperBounds[0].toKType())
      }
    }
    else -> {
      throw NotImplementedError("Unsupported type: $this")
    }
  }
}

/** Factory methods for types. */
@InternalEitherNetApi
public object Types {
  /**
   * Returns a new parameterized type, applying `typeArguments` to `rawType`. Use this method if
   * `rawType` is not enclosed in another type.
   */
  @JvmStatic
  public fun newParameterizedType(rawType: Type, vararg typeArguments: Type): ParameterizedType {
    require(typeArguments.isNotEmpty()) { "Missing type arguments for $rawType" }
    return ParameterizedTypeImpl(null, rawType, *typeArguments)
  }

  /**
   * Returns a new parameterized type, applying `typeArguments` to `rawType`. Use this method if
   * `rawType` is enclosed in `ownerType`.
   */
  @JvmStatic
  public fun newParameterizedTypeWithOwner(
    ownerType: Type?,
    rawType: Type,
    vararg typeArguments: Type,
  ): ParameterizedType {
    require(typeArguments.isNotEmpty()) { "Missing type arguments for $rawType" }
    return ParameterizedTypeImpl(ownerType, rawType, *typeArguments)
  }

  /** Returns an array type whose elements are all instances of `componentType`. */
  @JvmStatic
  public fun arrayOf(componentType: Type): GenericArrayType {
    return GenericArrayTypeImpl(componentType)
  }

  /**
   * Returns a type that represents an unknown type that extends `bound`. For example, if `bound` is
   * `CharSequence.class`, this returns `? extends CharSequence`. If `bound` is `Object.class`, this
   * returns `?`, which is shorthand for `? extends Object`.
   */
  @JvmStatic
  public fun subtypeOf(bound: Type): WildcardType {
    val upperBounds =
      if (bound is WildcardType) {
        bound.upperBounds
      } else {
        arrayOf<Type>(bound)
      }
    return WildcardTypeImpl(upperBounds, EMPTY_TYPE_ARRAY)
  }

  /**
   * Returns a type that represents an unknown supertype of `bound`. For example, if `bound` is
   * `String.class`, this returns `? super String`.
   */
  @JvmStatic
  public fun supertypeOf(bound: Type): WildcardType {
    val lowerBounds =
      if (bound is WildcardType) {
        bound.lowerBounds
      } else {
        arrayOf<Type>(bound)
      }
    return WildcardTypeImpl(arrayOf<Type>(Any::class.java), lowerBounds)
  }

  @JvmStatic
  public fun getRawType(type: Type?): Class<*> {
    return when (type) {
      is Class<*> -> {
        // type is a normal class.
        type
      }
      is ParameterizedType -> {
        // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either
        // but
        // suspects some pathological case related to nested classes exists.
        val rawType = type.rawType
        rawType as Class<*>
      }
      is GenericArrayType -> {
        val componentType = type.genericComponentType
        java.lang.reflect.Array.newInstance(getRawType(componentType), 0).javaClass
      }
      is TypeVariable<*> -> {
        // We could use the variable's bounds, but that won't work if there are multiple. having a
        // raw
        // type that's more general than necessary is okay.
        Any::class.java
      }
      is WildcardType -> getRawType(type.upperBounds[0])
      else -> {
        val className = type?.javaClass?.name?.toString()
        throw IllegalArgumentException(
          "Expected a Class, ParameterizedType, or GenericArrayType, but <$type> is of type $className"
        )
      }
    }
  }

  /** Returns true if `a` and `b` are equal. */
  @JvmStatic
  public fun equals(a: Type?, b: Type?): Boolean {
    if (a === b) {
      return true // Also handles (a == null && b == null).
    }
    // This isn't a supported type.
    when (a) {
      is Class<*> -> {
        return if (b is GenericArrayType) {
          equals(a.componentType, b.genericComponentType)
        } else if (b is ParameterizedType && a.rawType == b.rawType) {
          // Class instance with generic info, from method return types
          return a.typeParameters.flatMap { it.bounds.toList() } == b.actualTypeArguments.toList()
        } else {
          a == b // Class already specifies equals().
        }
      }
      is ParameterizedType -> {
        // Class instance with generic info, from method return types
        if (b is Class<*> && a.rawType == b.rawType) {
          return b.typeParameters.map { it.bounds }.toTypedArray().flatten() ==
            a.actualTypeArguments.toList()
        }
        if (b !is ParameterizedType) return false
        val aTypeArguments =
          if (a is ParameterizedTypeImpl) a.typeArguments else a.actualTypeArguments
        val bTypeArguments =
          if (b is ParameterizedTypeImpl) b.typeArguments else b.actualTypeArguments
        return (equals(a.ownerType, b.ownerType) &&
          (a.rawType == b.rawType) &&
          aTypeArguments.contentEquals(bTypeArguments))
      }
      is GenericArrayType -> {
        if (b is Class<*>) {
          return equals(b.componentType, a.genericComponentType)
        }
        if (b !is GenericArrayType) return false
        return equals(a.genericComponentType, b.genericComponentType)
      }
      is WildcardType -> {
        if (b !is WildcardType) return false
        return (a.upperBounds.contentEquals(b.upperBounds) &&
          a.lowerBounds.contentEquals(b.lowerBounds))
      }
      is TypeVariable<*> -> {
        if (b !is TypeVariable<*>) return false
        return (a.genericDeclaration === b.genericDeclaration && (a.name == b.name))
      }
      else -> return false // This isn't a supported type.
    }
  }
}
