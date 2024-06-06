package com.slack.eithernet

import kotlin.reflect.KClass

internal expect val KClass<*>.qualifiedNameForComparison: String?