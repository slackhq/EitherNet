# Keep ApiResult's generics or else R8 could strip them. We introspect these at runtime
-keep,allowobfuscation,allowshrinking class com.slack.eithernet.ApiResult
