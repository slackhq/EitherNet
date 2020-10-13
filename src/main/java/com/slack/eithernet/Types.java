/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slack.eithernet;

import com.slack.eithernet.Util.GenericArrayTypeImpl;
import com.slack.eithernet.Util.ParameterizedTypeImpl;
import com.slack.eithernet.Util.WildcardTypeImpl;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

import static com.slack.eithernet.Util.EMPTY_TYPE_ARRAY;

/** Factory methods for types. */
public final class Types {
  private Types() {}

  /**
   * Returns a new parameterized type, applying {@code typeArguments} to {@code rawType}. Use this
   * method if {@code rawType} is not enclosed in another type.
   */
  public static ParameterizedType newParameterizedType(Type rawType, Type... typeArguments) {
    if (typeArguments.length == 0) {
      throw new IllegalArgumentException("Missing type arguments for " + rawType);
    }
    return new ParameterizedTypeImpl(null, rawType, typeArguments);
  }

  /**
   * Returns a new parameterized type, applying {@code typeArguments} to {@code rawType}. Use this
   * method if {@code rawType} is enclosed in {@code ownerType}.
   */
  public static ParameterizedType newParameterizedTypeWithOwner(Type ownerType,
      Type rawType,
      Type... typeArguments) {
    if (typeArguments.length == 0) {
      throw new IllegalArgumentException("Missing type arguments for " + rawType);
    }
    return new ParameterizedTypeImpl(ownerType, rawType, typeArguments);
  }

  /** Returns an array type whose elements are all instances of {@code componentType}. */
  public static GenericArrayType arrayOf(Type componentType) {
    return new GenericArrayTypeImpl(componentType);
  }

  /**
   * Returns a type that represents an unknown type that extends {@code bound}. For example, if
   * {@code bound} is {@code CharSequence.class}, this returns {@code ? extends CharSequence}. If
   * {@code bound} is {@code Object.class}, this returns {@code ?}, which is shorthand for {@code ?
   * extends Object}.
   */
  public static WildcardType subtypeOf(Type bound) {
    Type[] upperBounds;
    if (bound instanceof WildcardType) {
      upperBounds = ((WildcardType) bound).getUpperBounds();
    } else {
      upperBounds = new Type[] { bound };
    }
    return new WildcardTypeImpl(upperBounds, EMPTY_TYPE_ARRAY);
  }

  /**
   * Returns a type that represents an unknown supertype of {@code bound}. For example, if {@code
   * bound} is {@code String.class}, this returns {@code ? super String}.
   */
  public static WildcardType supertypeOf(Type bound) {
    Type[] lowerBounds;
    if (bound instanceof WildcardType) {
      lowerBounds = ((WildcardType) bound).getLowerBounds();
    } else {
      lowerBounds = new Type[] { bound };
    }
    return new WildcardTypeImpl(new Type[] { Object.class }, lowerBounds);
  }

  public static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      // type is a normal class.
      return (Class<?>) type;
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;

      // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
      // suspects some pathological case related to nested classes exists.
      Type rawType = parameterizedType.getRawType();
      return (Class<?>) rawType;
    } else if (type instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) type).getGenericComponentType();
      return Array.newInstance(getRawType(componentType), 0)
          .getClass();
    } else if (type instanceof TypeVariable) {
      // We could use the variable's bounds, but that won't work if there are multiple. having a raw
      // type that's more general than necessary is okay.
      return Object.class;
    } else if (type instanceof WildcardType) {
      return getRawType(((WildcardType) type).getUpperBounds()[0]);
    } else {
      String className = type == null ? "null" : type.getClass()
          .getName();
      throw new IllegalArgumentException("Expected a Class, ParameterizedType, or "
          + "GenericArrayType, but <"
          + type
          + "> is of type "
          + className);
    }
  }

  /** Returns true if {@code a} and {@code b} are equal. */
  public static boolean equals(@Nullable Type a, @Nullable Type b) {
    if (a == b) {
      return true; // Also handles (a == null && b == null).
    } else if (a instanceof Class) {
      if (b instanceof GenericArrayType) {
        return equals(((Class) a).getComponentType(),
            ((GenericArrayType) b).getGenericComponentType());
      }
      return a.equals(b); // Class already specifies equals().
    } else if (a instanceof ParameterizedType) {
      if (!(b instanceof ParameterizedType)) return false;
      ParameterizedType pa = (ParameterizedType) a;
      ParameterizedType pb = (ParameterizedType) b;
      Type[] aTypeArguments =
          pa instanceof ParameterizedTypeImpl ? ((ParameterizedTypeImpl) pa).typeArguments
              : pa.getActualTypeArguments();
      Type[] bTypeArguments =
          pb instanceof ParameterizedTypeImpl ? ((ParameterizedTypeImpl) pb).typeArguments
              : pb.getActualTypeArguments();
      return equals(pa.getOwnerType(), pb.getOwnerType()) && pa.getRawType()
          .equals(pb.getRawType()) && Arrays.equals(aTypeArguments, bTypeArguments);
    } else if (a instanceof GenericArrayType) {
      if (b instanceof Class) {
        return equals(((Class) b).getComponentType(),
            ((GenericArrayType) a).getGenericComponentType());
      }
      if (!(b instanceof GenericArrayType)) return false;
      GenericArrayType ga = (GenericArrayType) a;
      GenericArrayType gb = (GenericArrayType) b;
      return equals(ga.getGenericComponentType(), gb.getGenericComponentType());
    } else if (a instanceof WildcardType) {
      if (!(b instanceof WildcardType)) return false;
      WildcardType wa = (WildcardType) a;
      WildcardType wb = (WildcardType) b;
      return Arrays.equals(wa.getUpperBounds(), wb.getUpperBounds())
          && Arrays.equals(wa.getLowerBounds(), wb.getLowerBounds());
    } else if (a instanceof TypeVariable) {
      if (!(b instanceof TypeVariable)) return false;
      TypeVariable<?> va = (TypeVariable<?>) a;
      TypeVariable<?> vb = (TypeVariable<?>) b;
      return va.getGenericDeclaration() == vb.getGenericDeclaration() && va.getName()
          .equals(vb.getName());
    } else {
      // This isn't a supported type.
      return false;
    }
  }
}
