Changelog
=========

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
