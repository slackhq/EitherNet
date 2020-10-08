package com.slack.retrofit

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Type
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class ResultTypeTest {

  @Test
  fun classType() = testType<String>()

  @Test
  fun parameterizedType() = testType<List<String>>()

  @Test
  fun parameterizedTypeWithOwner() {
    val typeWithOwner = Types.newParameterizedTypeWithOwner(
      ResultTypeTest::class.java,
      A::class.java,
      B::class.java
    )
    val annotation = createResultType(typeWithOwner)
    val created = annotation.toType()
    created.assertEqualTo(typeWithOwner)
  }

  @Test
  fun enumType() = testType<TestEnum>()

  @Test
  fun array() = testType<Array<String>>()

  @Test
  fun wildcard() {
    val wildcard = Types.subtypeOf(String::class.java)
    val annotation = createResultType(wildcard)
    val created = annotation.toType()
    Util.removeSubtypeWildcard(wildcard).assertEqualTo(created)
  }

  private class A

  private class B

  enum class TestEnum

  private fun Type.assertEqualTo(other: Type) {
    assertThat(Types.equals(this, other)).isTrue()
  }

  private inline fun <reified T> testType() {
    testType(typeOf<T>())
  }

  private fun testType(type: KType) {
    val annotation = createResultType(type)
    val created = annotation.toType()
    type.javaType.assertEqualTo(created)
  }
}