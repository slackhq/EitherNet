Changelog
=========

1.8.0
-----

_2023-11-03_

- **Fix**: Deprecate old `fold()` functions and introduce new ones that use the underlying value rather than `Success`. This was an oversight in the previous implementation. Binary compatibility is preserved.
- **Enhancement:** Mark functions `inline` where possible to allow carried over context (i.e. in suspend functions, etc)
- **Enhancement:** Use contracts to inform the compiler about possible calls to lambdas.
- **New:** Add fluent `onSuccess` and `onFailure*` functional extension APIs to `ApiResult`.

1.7.0
-----

_2023-11-02_

- **Enhancement**: Add new `ApiResult<*, *>.successOrNothing()` and `ApiResult.Failure<*>.exceptionOrNull()` functional extension APIs.
- Update to Kotlin `1.9.20`.

1.6.0
-----

_2023-09-26_

- **Enhancement**: Add `shouldRetry` parameter to `retryWithExponentialBackoff()` to allow conditional short-circuiting of retries.

1.5.0
-----

_2023-08-08_

- **New**: Add new `successOrNull`, `successOrElse`, and `fold` functional extension APIs to `ApiResult`. These allow easy happy path-ing in user code to coerce results into a concrete value.
- Update to Kotlin `1.9.0`.

1.4.1
-----

_2023-05-31_

- **Enhancement**: Gracefully handle `Unit`-returning endpoints when encountering a 204 or 205 response code.

Thanks to [@JDSM01](https://github.com/JDSM01) for contributing to this release!

1.4.0
-----

_2023-05-19_

Happy new year!

A common pattern in making network requests is to retry with exponential backoff. EitherNet now ships with a highly configurable `retryWithExponentialBackoff()` function for this case.

```kotlin
// Defaults for reference
val result = retryWithExponentialBackoff(
  maxAttempts = 3,
  initialDelay = 500.milliseconds,
  delayFactor = 2.0,
  maxDelay = 10.seconds,
  jitterFactor = 0.25,
  onFailure = null, // Optional Failure callback for logging
) {
    api.getData()
}
```

- Update to Kotlin `1.8.21`.
- Update to Kotlin Coroutines `1.7.1`.
- EitherNet now depends on `org.jetbrains.kotlinx:kotlinx-coroutines-core`.

1.3.1
-----

_2022-12-31_

- Fix missing Gradle module metadata for test fixtures.

1.3.0
-----

_2022-12-30_

- Update to Kotlin `1.8.0`.
- **Fix:** Fix exception on annotation traversal when target is not present
- **Fix:** Publish test-fixtures artifact sources.
- Update to JVM target 11.

1.2.1
-----

_2022-01-23_

* Update to Kotlin `1.6.10`.
* Promote test-fixtures APIs to stable.
* Update kotlinx-coroutines to `1.6.0` (test-fixtures only).
* **Fix:** test-fixtures artifact module metadata using the wrong artifact ID. They should correctly resolve when using Gradle's `testFixtures(...)` syntax.

1.2.0
-----

_2021-11-16_

* Update to Kotlin `1.6.0`
* **New:** Directly instantiate intermediate EitherNet annotations. This is an internal change only.

1.1.0
-----

_2021-09-22_

* Update Kotlin to `1.5.31`
* **New:** This release introduces a new `EitherNetController` API for testing EitherNet APIs via [Test Fixtures](https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures). This is similar to OkHttp’s `MockWebServer`, where results can be enqueued for specific endpoints.

Simply create a new controller instance in your test using one of the `newEitherNetController()` functions.

```kotlin
val controller = newEitherNetController<PandaApi>() // reified type
```

Then you can access the underlying faked `api` property from it and pass that on to whatever’s being tested.


```kotlin
// Take the api instance from the controller and pass it to whatever's being tested
val provider = PandaDataProvider(controller.api)
```

Finally, enqueue results for endpoints as needed.

```kotlin
// Later in a test you can enqueue results for specific endpoints
controller.enqueue(PandaApi::getPandas, ApiResult.success("Po"))
```

You can also optionally pass in full suspend functions if you need dynamic behavior

```kotlin
controller.enqueue(PandaApi::getPandas) {
  // This is a suspend function!
  delay(1000)
  ApiResult.success("Po")
}
```

See its [section in our README](https://github.com/slackhq/EitherNet#testing) for full more details.

1.0.0
-----

_2021-09-9_

Stable release!

* **Fix:** Embed proguard rules to keep relevant generics information on `ApiResult`. This is important for new versions of R8, which otherwise strips this information.
* **Fix:** Require `ApiResult` type arguments to be non-null (i.e. `T : Any`).
* **New:** Add a tags API for breadcrumbing information in `ApiResult`. We expose a few APIs through here, namely the original OkHttp `Request` or `Response` instances when relevant.
* `ApiResult` subtypes are no longer `data` classes since many of their underlying properties don't reliably implement equals/hashCode/immutability.
* The deprecated `ApiResult.response` property is now removed.

Thanks to [@okamayana](https://github.com/okamayana) for contributing to this release!

1.0.0-rc01
----------

_2021-07-19_

This is our first (and hopefully final!) 1.0 release candidate. Please report any issues or API
surface area issues now or forever hold your peace.

_Note that we consider this stable for production use, this is mostly about API stability._

* **Breaking:** `ApiResult` and `ApiResult.Failure` are both now `sealed interface` types rather than sealed classes. For most consumers this shouldn't be a source breaking change!
* **Breaking:** `ApiResult.Success` constructor is now `internal`, please use the `ApiResult.success(<value>)` factory.
* Test up to JDK 17.

Updated dependencies
```
Kotlin      1.5.21
Coroutines  1.5.1
Dokka       1.5.0
```

Special thanks to [@danieldisu](https://github.com/danieldisu) and [@JvmName](https://github.com/JvmName) for contributing to this release!

0.2.0
-----

_2020-10-22_

**New:** Support for decoding error bodies. `HttpFailure` is now typed with the `E` error generic
and decoding error bodies can be opted into via the `@DecodeErrorBody` annotation.

```kotlin
interface TestApi {
  @DecodeErrorBody
  @GET("/")
  suspend fun testEndpoint(): ApiResult<SuccessType, ErrorType>
}
```

See the [README](https://github.com/slackhq/EitherNet/blob/main/README.md#decoding-error-bodies) section for more details.

0.1.0
-----

_2020-10-22_

Initial release
