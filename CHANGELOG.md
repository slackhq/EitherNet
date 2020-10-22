Changelog
=========

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

See the [README](https://github.com/slackhq/EitherNet/tree/53c2e02292b17b48019139305f0ec6d1d782db95#decoding-error-bodies) section for more details.

0.1.0
-----

_2020-10-22_

Initial release
