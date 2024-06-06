package com.slack.eithernet.test

import com.slack.eithernet.ApiResult
import com.slack.eithernet.InternalEitherNetApi

/**
 * This is a helper API returned by [newEitherNetController] to help with testing EitherNet
 * endpoints.
 *
 * Simple usage looks something like this:
 * ```
 * val controller = newEitherNetController<PandaApi>()
 *
 * // Take the api instance from the controller!
 * val provider = PandaDataProvider(controller.api)
 *
 * // Later in a test
 * controller.enqueue(PandaApi::getPandas, ApiResult.success("Po"))
 * ```
 *
 * Enqueued results are keyed to their corresponding endpoint. There is also an overload of
 * [enqueue] that accepts a `suspend` function body to allow for custom/dynamic behavior.
 *
 * For Java interop, there is a limited API available at [enqueueFromJava].
 */
public interface EitherNetController<T : Any> {
  /** The underlying API implementation that should be passed into the thing being tested. */
  public val api: T

  /** Asserts that there are no more remaining results in the queue. */
  public fun assertNoMoreQueuedResults()

  @InternalEitherNetApi
  public fun <S : Any, E : Any> unsafeEnqueue(
    key: EndpointKey,
    resultBody: suspend (args: Array<Any>) -> ApiResult<S, E>,
  )
}