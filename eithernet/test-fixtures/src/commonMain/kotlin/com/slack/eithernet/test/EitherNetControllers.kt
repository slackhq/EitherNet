package com.slack.eithernet.test

import kotlin.reflect.KClass

/** Returns a new [EitherNetController] for API service type [T]. */
public inline fun <reified T : Any> newEitherNetController(): EitherNetController<T> {
  return newEitherNetController(T::class)
}

/** Returns a new [EitherNetController] for API service type [T]. */
public fun <T : Any> newEitherNetController(service: KClass<T>): EitherNetController<T> {
  service.validateApi()
  // Get functions with retrofit annotations
  val endpoints =
    service.getFunctions()
      .filter { it.isApplicable }
      .map { it.toEndpointKey() }
      .associateWithTo(newConcurrentMap()) { ArrayDeque<SuspendedResult>() }
  val orchestrator = EitherNetTestOrchestrator(endpoints)
  val proxy = newServiceInstance(service, orchestrator)
  return RealEitherNetController(orchestrator, proxy)
}