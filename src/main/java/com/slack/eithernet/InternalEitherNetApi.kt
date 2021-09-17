/*
 * Copyright (C) 2021 Slack Technologies, LLC
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

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPEALIAS

/**
 * Marks declarations that are **internal** in EitherNet API, which means that they should not be used outside of
 * `com.slack.eithernet`, because their signatures and semantics will change between future releases without any
 * warnings and without providing any migration aids.
 */
@Retention(BINARY)
@Target(CLASS, FUNCTION, TYPEALIAS, PROPERTY)
@RequiresOptIn(
  level = ERROR,
  message = "This is an internal EitherNet API that " +
    "should not be used from outside of EitherNet. No compatibility guarantees are provided."
)
public annotation class InternalEitherNetApi
