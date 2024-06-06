/*
 * Copyright (C) 2014 Slack Technologies, LLC
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
@file:JvmName("Util")
@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.slack.eithernet

import java.lang.StringBuilder
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.LinkedHashSet

@JvmField internal val EMPTY_TYPE_ARRAY: Array<Type> = arrayOf()

/**
 * Returns a type that is functionally equal but not necessarily equal according to
 * [[Object.equals()]][Object.equals].
 */
internal fun Type.canonicalize(): Type {
  return when (this) {
    is Class<*> -> {
      if (isArray) GenericArrayTypeImpl(this@canonicalize.componentType.canonicalize()) else this
    }
    is ParameterizedType -> {
      if (this is ParameterizedTypeImpl) return this
      ParameterizedTypeImpl(ownerType, rawType, *actualTypeArguments)
    }
    is GenericArrayType -> {
      if (this is GenericArrayTypeImpl) return this
      GenericArrayTypeImpl(genericComponentType)
    }
    is WildcardType -> {
      if (this is WildcardTypeImpl) return this
      WildcardTypeImpl(upperBounds, lowerBounds)
    }
    else -> this // This type is unsupported!
  }
}

/** If type is a "? extends X" wildcard, returns X; otherwise returns type unchanged. */
internal fun Type.removeSubtypeWildcard(): Type {
  if (this !is WildcardType) return this
  val lowerBounds = lowerBounds
  if (lowerBounds.isNotEmpty()) return this
  val upperBounds = upperBounds
  require(upperBounds.size == 1)
  return upperBounds[0]
}

public fun Type.resolve(context: Type, contextRawType: Class<*>): Type {
  return this.resolve(context, contextRawType, LinkedHashSet())
}

private fun Type.resolve(
  context: Type,
  contextRawType: Class<*>,
  visitedTypeVariables: MutableCollection<TypeVariable<*>>,
): Type {
  // This implementation is made a little more complicated in an attempt to avoid object-creation.
  var toResolve = this
  while (true) {
    when {
      toResolve is TypeVariable<*> -> {
        val typeVariable = toResolve
        if (typeVariable in visitedTypeVariables) {
          // cannot reduce due to infinite recursion
          return toResolve
        } else {
          visitedTypeVariables += typeVariable
        }
        toResolve = resolveTypeVariable(context, contextRawType, typeVariable)
        if (toResolve === typeVariable) return toResolve
      }
      toResolve is Class<*> && toResolve.isArray -> {
        val original = toResolve
        val componentType: Type = original.componentType
        val newComponentType = componentType.resolve(context, contextRawType, visitedTypeVariables)
        return if (componentType === newComponentType) original else newComponentType.asArrayType()
      }
      toResolve is GenericArrayType -> {
        val original = toResolve
        val componentType = original.genericComponentType
        val newComponentType = componentType.resolve(context, contextRawType, visitedTypeVariables)
        return if (componentType === newComponentType) original else newComponentType.asArrayType()
      }
      toResolve is ParameterizedType -> {
        val original = toResolve
        val ownerType: Type? = original.ownerType
        val newOwnerType =
          ownerType?.let { ownerType.resolve(context, contextRawType, visitedTypeVariables) }
        var changed = newOwnerType !== ownerType
        var args = original.actualTypeArguments
        for (t in args.indices) {
          val resolvedTypeArgument = args[t].resolve(context, contextRawType, visitedTypeVariables)
          if (resolvedTypeArgument !== args[t]) {
            if (!changed) {
              args = args.clone()
              changed = true
            }
            args[t] = resolvedTypeArgument
          }
        }
        return if (changed) ParameterizedTypeImpl(newOwnerType, original.rawType, *args)
        else original
      }
      toResolve is WildcardType -> {
        val original = toResolve
        val originalLowerBound = original.lowerBounds
        val originalUpperBound = original.upperBounds
        if (originalLowerBound.size == 1) {
          val lowerBound =
            originalLowerBound[0].resolve(context, contextRawType, visitedTypeVariables)
          if (lowerBound !== originalLowerBound[0]) {
            return Types.supertypeOf(lowerBound)
          }
        } else if (originalUpperBound.size == 1) {
          val upperBound =
            originalUpperBound[0].resolve(context, contextRawType, visitedTypeVariables)
          if (upperBound !== originalUpperBound[0]) {
            return Types.subtypeOf(upperBound)
          }
        }
        return original
      }
      else -> return toResolve
    }
  }
}

internal fun resolveTypeVariable(
  context: Type,
  contextRawType: Class<*>,
  unknown: TypeVariable<*>,
): Type {
  val declaredByRaw = declaringClassOf(unknown) ?: return unknown

  // We can't reduce this further.
  val declaredBy = getGenericSupertype(context, contextRawType, declaredByRaw)
  if (declaredBy is ParameterizedType) {
    val index = declaredByRaw.typeParameters.indexOf(unknown)
    return declaredBy.actualTypeArguments[index]
  }
  return unknown
}

/**
 * Returns the generic supertype for `supertype`. For example, given a class `IntegerSet`, the
 * result for when supertype is `Set.class` is `Set<Integer>` and the result when the supertype is
 * `Collection.class` is `Collection<Integer>`.
 */
internal fun getGenericSupertype(
  context: Type,
  rawTypeInitial: Class<*>,
  toResolve: Class<*>,
): Type {
  var rawType = rawTypeInitial
  if (toResolve == rawType) {
    return context
  }

  // we skip searching through interfaces if unknown is an interface
  if (toResolve.isInterface) {
    val interfaces = rawType.interfaces
    for (i in interfaces.indices) {
      if (interfaces[i] == toResolve) {
        return rawType.genericInterfaces[i]
      } else if (toResolve.isAssignableFrom(interfaces[i])) {
        return getGenericSupertype(rawType.genericInterfaces[i], interfaces[i], toResolve)
      }
    }
  }

  // check our supertypes
  if (!rawType.isInterface) {
    while (rawType != Any::class.java) {
      val rawSupertype = rawType.superclass
      if (rawSupertype == toResolve) {
        return rawType.genericSuperclass
      } else if (toResolve.isAssignableFrom(rawSupertype)) {
        return getGenericSupertype(rawType.genericSuperclass, rawSupertype, toResolve)
      }
      rawType = rawSupertype
    }
  }

  // we can't resolve this further
  return toResolve
}

internal val Any?.hashCodeOrZero: Int
  get() {
    return this?.hashCode() ?: 0
  }

internal fun Type.typeToString(): String {
  return if (this is Class<*>) name else toString()
}

/** Returns the declaring class of `typeVariable`, or `null` if it was not declared by a class. */
internal fun declaringClassOf(typeVariable: TypeVariable<*>): Class<*>? {
  val genericDeclaration = typeVariable.genericDeclaration
  return if (genericDeclaration is Class<*>) genericDeclaration else null
}

internal fun Type.checkNotPrimitive() {
  require(!(this is Class<*> && isPrimitive)) { "Unexpected primitive $this. Use the boxed type." }
}

internal fun Type.toStringWithAnnotations(annotations: Set<Annotation>): String {
  return toString() +
    if (annotations.isEmpty()) " (with no annotations)" else " annotated $annotations"
}

internal class ParameterizedTypeImpl
private constructor(
  private val ownerType: Type?,
  private val rawType: Type,
  @JvmField val typeArguments: Array<Type>,
) : ParameterizedType {
  override fun getActualTypeArguments() = typeArguments.clone()

  override fun getRawType() = rawType

  override fun getOwnerType() = ownerType

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  override fun equals(other: Any?) =
    other is ParameterizedType && Types.equals(this, other as ParameterizedType?)

  override fun hashCode(): Int {
    return typeArguments.contentHashCode() xor rawType.hashCode() xor ownerType.hashCodeOrZero
  }

  override fun toString(): String {
    val result = StringBuilder(30 * (typeArguments.size + 1))
    result.append(rawType.typeToString())
    if (typeArguments.isEmpty()) {
      return result.toString()
    }
    result.append("<").append(typeArguments[0].typeToString())
    for (i in 1 until typeArguments.size) {
      result.append(", ").append(typeArguments[i].typeToString())
    }
    return result.append(">").toString()
  }

  companion object {
    @JvmName("create")
    @JvmStatic
    operator fun invoke(
      ownerType: Type?,
      rawType: Type,
      vararg typeArguments: Type,
    ): ParameterizedTypeImpl {
      // Require an owner type if the raw type needs it.
      if (rawType is Class<*>) {
        val enclosingClass = rawType.enclosingClass
        if (ownerType != null) {
          require(!(enclosingClass == null || ownerType.rawType != enclosingClass)) {
            "unexpected owner type for $rawType: $ownerType"
          }
        } else {
          require(enclosingClass == null) { "unexpected owner type for $rawType: null" }
        }
      }
      @Suppress("UNCHECKED_CAST") val finalTypeArgs = typeArguments.clone() as Array<Type>
      for (t in finalTypeArgs.indices) {
        finalTypeArgs[t].checkNotPrimitive()
        finalTypeArgs[t] = finalTypeArgs[t].canonicalize()
      }
      return ParameterizedTypeImpl(ownerType?.canonicalize(), rawType.canonicalize(), finalTypeArgs)
    }
  }
}

internal class GenericArrayTypeImpl private constructor(private val componentType: Type) :
  GenericArrayType {
  override fun getGenericComponentType() = componentType

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  override fun equals(other: Any?) =
    other is GenericArrayType && Types.equals(this, other as GenericArrayType?)

  override fun hashCode() = componentType.hashCode()

  override fun toString() = componentType.typeToString() + "[]"

  companion object {
    @JvmName("create")
    @JvmStatic
    operator fun invoke(componentType: Type): GenericArrayTypeImpl {
      return GenericArrayTypeImpl(componentType.canonicalize())
    }
  }
}

/**
 * The WildcardType interface supports multiple upper bounds and multiple lower bounds. We only
 * support what the Java 6 language needs - at most one bound. If a lower bound is set, the upper
 * bound must be Object.class.
 */
internal class WildcardTypeImpl
private constructor(private val upperBound: Type, private val lowerBound: Type?) : WildcardType {

  override fun getUpperBounds() = arrayOf(upperBound)

  override fun getLowerBounds() = lowerBound?.let { arrayOf(it) } ?: EMPTY_TYPE_ARRAY

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  override fun equals(other: Any?) =
    other is WildcardType && Types.equals(this, other as WildcardType?)

  override fun hashCode(): Int {
    // This equals Arrays.hashCode(getLowerBounds()) ^ Arrays.hashCode(getUpperBounds()).
    return (if (lowerBound != null) 31 + lowerBound.hashCode() else 1) xor
      31 + upperBound.hashCode()
  }

  override fun toString(): String {
    return when {
      lowerBound != null -> "? super ${lowerBound.typeToString()}"
      upperBound === Any::class.java -> "?"
      else -> "? extends ${upperBound.typeToString()}"
    }
  }

  companion object {
    @JvmStatic
    @JvmName("create")
    operator fun invoke(upperBounds: Array<Type>, lowerBounds: Array<Type>): WildcardTypeImpl {
      require(lowerBounds.size <= 1)
      require(upperBounds.size == 1)
      return if (lowerBounds.size == 1) {
        lowerBounds[0].checkNotPrimitive()
        require(!(upperBounds[0] !== Any::class.java))
        WildcardTypeImpl(lowerBound = lowerBounds[0].canonicalize(), upperBound = Any::class.java)
      } else {
        upperBounds[0].checkNotPrimitive()
        WildcardTypeImpl(lowerBound = null, upperBound = upperBounds[0].canonicalize())
      }
    }
  }
}
