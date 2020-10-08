/*
 * Copyright (C) 2020 Slack Technologies, Inc.
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
    val annotation = createResultType(type.javaType)
    val created = annotation.toType()
    type.javaType.assertEqualTo(created)
  }
}
