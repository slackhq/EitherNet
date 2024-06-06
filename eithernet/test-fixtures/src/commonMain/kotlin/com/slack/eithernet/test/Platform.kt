package com.slack.eithernet.test

import kotlin.reflect.KClass
import kotlin.reflect.KFunction

internal expect fun KClass<*>.getFunctions(): Collection<KFunction<*>>
internal expect fun <K, V> newConcurrentMap(): MutableMap<K, V>
internal expect fun KFunction<*>.toEndpointKey(): EndpointKey
internal expect val KFunction<*>.isApplicable: Boolean
internal expect fun KClass<*>.validateApi()
internal expect fun <T : Any> newServiceInstance(klass: KClass<T>, orchestrator: EitherNetTestOrchestrator): T