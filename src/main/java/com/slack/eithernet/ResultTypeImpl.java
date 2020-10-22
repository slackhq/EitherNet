package com.slack.eithernet;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;

// Has to be Java because Kotlin doesn't allow subclassing annotations
@SuppressWarnings("ClassExplicitlyAnnotation")
final class ResultTypeImpl implements ResultType {

  private final Class<?> ownerType;
  private final Class<?> rawType;
  private final ResultType[] typeArgs;
  private final boolean isArray;

  public ResultTypeImpl(
      Class<?> ownerType,
      Class<?> rawType,
      ResultType[] typeArgs,
      boolean isArray
  ) {
    this.ownerType = ownerType;
    this.rawType = rawType;
    this.typeArgs = typeArgs;
    this.isArray = isArray;
  }

  @Override public Class<?> ownerType() {
    return ownerType;
  }

  @Override public Class<?> rawType() {
    return rawType;
  }

  @Override public ResultType[] typeArgs() {
    return typeArgs.clone();
  }

  @Override public Class<? extends Annotation> annotationType() {
    return ResultType.class;
  }

  @Override public boolean isArray() {
    return isArray;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResultTypeImpl that = (ResultTypeImpl) o;
    return isArray == that.isArray && Objects.equals(ownerType, that.ownerType) && Objects.equals(
        rawType,
        that.rawType) && Arrays.equals(typeArgs, that.typeArgs);
  }

  @Override public int hashCode() {
    int result = Objects.hash(ownerType, rawType, isArray);
    result = 31 * result + Arrays.hashCode(typeArgs);
    return result;
  }

  @Override public String toString() {
    return "ResultType{"
        + "ownerType="
        + ownerType
        + ", rawType="
        + rawType
        + ", typeArgs="
        + Arrays.toString(typeArgs)
        + ", isArray="
        + isArray
        + '}';
  }
}
