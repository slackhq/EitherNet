package com.slack.eithernet.test

import co.touchlab.stately.collections.ConcurrentMutableMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

internal actual fun KClass<*>.getFunctions(): Collection<KFunction<*>> {
  TODO("Kotlin Reflect is not supported on Native yet.")
}

internal actual fun <K, V> newConcurrentMap(): MutableMap<K, V> {
  return ConcurrentMutableMap()
}

internal actual fun KFunction<*>.toEndpointKey(): EndpointKey {
  TODO("Kotlin Reflect is not supported on Native yet.")
}

internal actual val KFunction<*>.isApplicable: Boolean
  get() = false

internal actual fun KClass<*>.validateApi() {
  // Not implemented
}

internal actual fun <T : Any> newServiceInstance(
  klass: KClass<T>,
  orchestrator: EitherNetTestOrchestrator
): T {
  TODO("Kotlin Reflect is not supported on Native yet.")
}