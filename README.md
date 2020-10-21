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
    is UnknownError -> showError(result.error)
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

If you don't have custom error return types, simply use `Nothing` for the error type.

### Decoding Error Bodies

If you want to decode error types in `HttpFailure`s, annotate your endpoint with `@DecodeErrorBody`:

```kotlin
interface TestApi {
  @DecodeErrorBody
  @GET("/")
  suspend fun getData(): ApiResult<SuccessResponse, ErrorResponse>
}
```

Now a 4xx response will try to decode its error body (if any) as `ErrorResponse`. If you want to
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
    ?: return
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
    val (errorType, nextAnnotations) = annotations.errorType() ?: error("No error type found!")
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

## Installation

```gradle
dependencies {
  implementation("com.slack.eithernet:eithernet:<version>")
}
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

License
--------

    Copyright 2020 Slack Technologies, Inc.

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
