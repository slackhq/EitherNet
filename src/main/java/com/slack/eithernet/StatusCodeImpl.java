package com.slack.eithernet;

import java.lang.annotation.Annotation;
import java.util.Objects;

class StatusCodeImpl implements StatusCode {

  private final int value;

  StatusCodeImpl(int value) {
    this.value = value;
  }

  @Override public int value() {
    return value;
  }

  @Override public Class<? extends Annotation> annotationType() {
    return StatusCode.class;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StatusCodeImpl that = (StatusCodeImpl) o;
    return value == that.value;
  }

  @Override public int hashCode() {
    return Objects.hash(value);
  }

  @Override public String toString() {
    return "StatusCode{" + "value=" + value + '}';
  }
}
