Changelog
=========

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

See its section in our README for full more details.



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
