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

import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection

internal actual val KClass<*>.qualifiedNameForComparison: String?
  get() = qualifiedName

internal actual class KTypeImpl
actual constructor(
  actual override val classifier: KClassifier?,
  actual override val arguments: List<KTypeProjection>,
  actual override val isMarkedNullable: Boolean,
  actual override val annotations: List<Annotation>,
) : KType, EitherNetKType {
  private val impl = EitherNetKTypeImpl(classifier, arguments, isMarkedNullable, annotations)

  override fun equals(other: Any?) = impl == other

  override fun hashCode() = impl.hashCode()

  override fun toString() = impl.toString()
}
