package com.slack.eithernet

internal fun <K, V> Map<K, V>.toUnmodifiableMap() = buildMap { putAll(this@toUnmodifiableMap) }