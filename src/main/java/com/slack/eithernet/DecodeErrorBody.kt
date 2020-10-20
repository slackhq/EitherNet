package com.slack.eithernet

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Indicates that this endpoint should attempt to decode 4xx response error bodies if present.
 *
 * This API should be considered read-only.
 */
@Target(FUNCTION)
@Retention(RUNTIME)
public annotation class DecodeErrorBody
