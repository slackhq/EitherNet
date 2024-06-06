package com.slack.eithernet.test

import com.slack.eithernet.toKType
import java.lang.reflect.Method

@PublishedApi
internal fun EndpointKey.Companion.create(method: Method): EndpointKey {
  return EndpointKey(
    method.name,
    method.parameterTypes.mapNotNull { createParameterKey(it.toKType()) },
  )
}