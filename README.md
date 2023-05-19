# EitherNet

A pluggable sealed API result type for modeling [Retrofit](https://github.com/square/retrofit) responses.

## Usage

By default, Retrofit uses exceptions to propagate any errors. This library leverages Kotlin sealed types
to better model these responses with a type-safe single point of return and no exception handling needed!

The core type for this is `ApiResult<out T, out E>`, where `T` is the success type and `E` is a possible
error type.

`ApiResult` has two sealed subtypes: `Success` and `Failure`. `Success` is typed to `T` with no
error type and `Failure` is typed to `E` with no success type. `Failure` in turn is represented by
four sealed subtypes of its own: `Failure.NetworkFailure`, `Failure.ApiFailure`, `Failure.HttpFailure`,
and `Failure.UnknownFailure`. This allows for simple handling of results through a consistent,
non-exceptional flow via sealed `when` branches.

```kotlin
when (val result = myApi.someEndpoint()) {
  is Success -> doSomethingWith(result.response)
  is Failure -> when (result) {
    is NetworkFailure -> showError(result.error)
    is HttpFailure -> showError(result.code)
    is ApiFailure -> showError(result.error)
    is UnknownFailure -> showError(result.error)
  }
}
```

Usually, user code for this could just simply show a generic error message for a `Failure`
case, but the sealed subtypes also allow for more specific error messaging or pluggability of error
types.

Simply change your endpoint return type to the typed `ApiResult` and include our call adapter and
delegating converter factory.


```kotlin
interface TestApi {
  @GET("/")
  suspend fun getData(): ApiResult<SuccessResponse, ErrorResponse>
}

val api = Retrofit.Builder()
  .addConverterFactory(ApiResultConverterFactory)
  .addCallAdapterFactory(ApiResultCallAdapterFactory)
  .build()
  .create<TestApi>()
```

If you don't have custom error return types, simply use `Unit` for the error type.

### Decoding Error Bodies

If you want to decode error types in `HttpFailure`s, annotate your endpoint with `@DecodeErrorBody`:

```kotlin
interface TestApi {
  @DecodeErrorBody
  @GET("/")
  suspend fun getData(): ApiResult<SuccessResponse, ErrorResponse>
}
```

Now a 4xx or 5xx response will try to decode its error body (if any) as `ErrorResponse`. If you want to
contextually decode the error body based on the status code, you can retrieve a `@StatusCode` annotation
from annotations in a custom Retrofit `Converter`.

```kotlin
// In your own converter factory.
override fun responseBodyConverter(
  type: Type,
  annotations: Array<out Annotation>,
  retrofit: Retrofit
): Converter<ResponseBody, *>? {
  val (statusCode, nextAnnotations) = annotations.statusCode()
    ?: return null
  val errorType = when (statusCode.value) {
    401 -> Unauthorized::class.java
    404 -> NotFound::class.java
    // ...
  }
  val errorDelegate = retrofit.nextResponseBodyConverter<Any>(this, errorType.toType(), nextAnnotations)
  return MyCustomBodyConverter(errorDelegate)
}
```

Note that error bodies with a content length of 0 will be skipped.

### Plugability

A common pattern for some APIs is to return a polymorphic `200` response where the data needs to be
dynamically parsed. Consider this example:

```JSON
{
  "ok": true,
  "data": {
    ...
  }
}
```

The same API may return this structure in an error event

```JSON
{
  "ok": false,
  "error_message": "Please try again."
}
```

This is hard to model with a single concrete type, but easy to handle with `ApiResult`. Simply
throw an `ApiException` with the decoded error type in a custom Retrofit `Converter` and it will be
automatically surfaced as a `Failure.ApiFailure` type with that error instance.

```kotlin
@GET("/")
suspend fun getData(): ApiResult<SuccessResponse, ErrorResponse>

// In your own converter factory.
class ErrorConverterFactory : Converter.Factory() {
  override fun responseBodyConverter(
    type: Type,
    annotations: Array<out Annotation>,
    retrofit: Retrofit
  ): Converter<ResponseBody, *>? {
    // This returns a `@ResultType` instance that can be used to get the error type via toType()
    val (errorType, nextAnnotations) = annotations.errorType() ?: return null
    return ResponseBodyConverter(errorType.toType())
  }

  class ResponseBodyConverter(
    private val errorType: Type
  ) : Converter<ResponseBody, *> {
    override fun convert(value: ResponseBody): String {
      if (value.isErrorType()) {
        val errorResponse = ...
        throw ApiException(errorResponse)
      } else {
        return SuccessResponse(...)
      }
    }
  }
}
```

### Retries

A common pattern in making network requests is to retry with exponential backoff. EitherNet ships with a highly configurable `retryWithExponentialBackoff()` function for this case.

```kotlin
// Defaults for reference
val result = retryWithExponentialBackoff(
  maxAttempts = 3,
  initialDelay = 500.milliseconds,
  delayFactor = 2.0,
  maxDelay = 10.seconds,
  jitterFactor = 0.25,
  onFailure = null, // Optional Failure callback w/ attempt
) {
    api.getData()
}
```

## Testing

EitherNet ships with a [Test Fixtures](https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures)
artifact containing a `EitherNetController` API to allow for easy testing with EitherNet APIs. This
is similar to OkHttp’s `MockWebServer`, where results can be enqueued for specific endpoints.

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

In instrumentation tests with DI, you can provide the controller and its underlying API in a test
module and replace the standard one. This works particularly well with [Anvil](https://github.com/square/anvil).

```kotlin
@ContributesTo(
  scope = UserScope::class,
  replaces = [PandaApiModule::class] // Replace the standard module
)
@Module
object TestPandaApiModule {
  @Provides
  fun providePandaApiController(): EitherNetController<PandaApi> = newEitherNetController()

  @Provides
  fun providePandaApi(
    controller: EitherNetController<PandaApi>
  ): PandaApi = controller.api
}
```

Then you can inject the controller in your test while users of `PandaApi` will get your test instance.

### Java Interop

For Java interop, there is a limited API available at `JavaEitherNetControllers.enqueueFromJava`.

### Validation

`EitherNetController` will run some small validation on API endpoints under the hood. If you want to
add your own validations on top of this, you can provide implementations of `ApiValidator` via
`ServiceLoader`. See `ApiValidator`'s docs for more information.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.slack.eithernet/eithernet.svg)](https://mvnrepository.com/artifact/com.slack.eithernet/eithernet)
```gradle
dependencies {
  implementation("com.slack.eithernet:eithernet:<version>")

  // Test fixtures
  testImplementation(testFixtures("com.slack.eithernet:eithernet:<version>"))
}
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

License
--------

    Copyright 2020 Slack Technologies, LLC

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


[snap]: https://oss.sonatype.org/content/repositories/snapshots/com/slack/eithernet/
