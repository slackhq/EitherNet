/*
 * Copyright (C) 2020 Slack Technologies, LLC
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

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Retention(RUNTIME) annotation class SampleAnnotation

class ResultTypeTest {

  @Test fun classType() = testType<String>()

  @Test fun parameterizedType() = testType<List<String>>()

  @Test fun enumType() = testType<TestEnum>()

  @Test fun array() = testType<Array<String>>()

  @Test
  fun errorType_present() {
    val annotations = Array<Annotation>(4) { SampleAnnotation() }
    val resultTypeAnnotation = typeOf<String>().toResultType()
    annotations[0] = resultTypeAnnotation
    val (resultType, nextAnnotations) = annotations.errorType() ?: error("No annotation found")
    assertEquals(3, nextAnnotations.size)
    assertSame(resultTypeAnnotation, resultType)
  }

  @Test
  fun errorType_absent() {
    val annotations = Array<Annotation>(4) { SampleAnnotation() }
    assertNull(annotations.errorType())
  }

  @Test
  fun statusCode_present() {
    val annotations = Array<Annotation>(4) { SampleAnnotation() }
    val statusCodeAnnotation = createStatusCode(404)
    annotations[0] = statusCodeAnnotation
    val (statusCode, nextAnnotations) = annotations.statusCode() ?: error("No annotation found")
    assertEquals(3, nextAnnotations.size)
    assertSame(statusCodeAnnotation, statusCode)
  }

  @Test
  fun statusCode_absent() {
    val annotations = Array<Annotation>(4) { SampleAnnotation() }
    assertNull(annotations.statusCode())
  }

  enum class TestEnum

  private inline fun <reified T> testType() {
    testType(typeOf<T>())
  }

  private fun testType(type: KType) {
    val annotation = type.toResultType()
    val created = annotation.toKType()
    assertTrue(type.isFunctionallyEqualTo(created))
  }

  private fun KType.toResultType(): ResultType {
    val klass = classifier as KClass<*>
    if (klass.qualifiedNameForComparison?.endsWith("Array") == true) {
      return ResultType(rawType = arguments.first().type!!.classifier as KClass<*>, isArray = true)
    }
    return ResultType(
      rawType = klass,
      typeArgs = arguments.mapNotNull { it.type?.toResultType() }.toTypedArray(),
      isArray = false,
    )
  }
}
