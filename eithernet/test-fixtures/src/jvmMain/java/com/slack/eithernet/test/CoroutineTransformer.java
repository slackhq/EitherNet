package com.slack.eithernet.test;

import com.slack.eithernet.ApiResult;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;

enum CoroutineTransformer {
  ;

  /**
   * A weird but effective way to redirect a {@link Continuation} acquired via reflection back into
   * a true {@code suspend} function.
   */
  public static Object transform(
      Object[] args, Object body, Continuation<? super ApiResult<?, ?>> continuation) {
    try {
      //noinspection unchecked
      return JvmUtilKt.awaitResponse(
          (Function2<
                  ? super Object[],
                  ? super Continuation<? super ApiResult<?, ?>>,
                  ?>)
              body,
          args,
          continuation);
    } catch (Exception e) {
      return JvmUtilKt.suspendAndThrow(e, continuation);
    }
  }
}
