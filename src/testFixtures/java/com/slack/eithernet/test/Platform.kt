/*
 * Copyright (C) 2013 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slack.eithernet.test

import android.annotation.SuppressLint
import android.os.Build.VERSION
import androidx.annotation.RequiresApi
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodHandles.Lookup
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Simple indirection for platform-specific implementations for Android and JVM. Adapted from
 * Retrofit's internal version.
 */
internal open class Platform(private val hasJava8Types: Boolean) {
  private val lookupConstructor: Constructor<Lookup>?

  init {
    var lookupConstructor: Constructor<Lookup>? = null
    @RequiresApi(26)
    if (hasJava8Types) {
      try {
        // Because the service interface might not be public, we need to use a MethodHandle lookup
        // that ignores the visibility of the declaringClass.
        lookupConstructor =
          Lookup::class.java.getDeclaredConstructor(Class::class.java, Int::class.javaPrimitiveType)
        lookupConstructor.setAccessible(true)
      } catch (ignored: NoClassDefFoundError) {
        // Android API 24 or 25 where Lookup doesn't exist. Calling default methods on non-public
        // interfaces will fail, but there's nothing we can do about it.
      } catch (ignored: NoSuchMethodException) {
        // Assume JDK 14+ which contains a fix that allows a regular lookup to succeed.
        // See https://bugs.openjdk.java.net/browse/JDK-8209005.
      }
    }
    this.lookupConstructor = lookupConstructor
  }

  @SuppressLint("NewApi")
  fun isDefaultMethod(method: Method): Boolean {
    return hasJava8Types && method.isDefault
  }

  @SuppressLint("NewApi")
  open fun invokeDefaultMethod(
    method: Method,
    declaringClass: Class<*>,
    obj: Any,
    vararg args: Any
  ): Any? {
    val lookup =
      if (lookupConstructor != null) {
        lookupConstructor.newInstance(declaringClass, -1 /* trusted */)
      } else {
        MethodHandles.lookup()
      }
    return lookup.unreflectSpecial(method, declaringClass).bindTo(obj).invokeWithArguments(*args)
  }

  internal class Android : Platform(VERSION.SDK_INT >= 24) {
    override fun invokeDefaultMethod(
      method: Method,
      declaringClass: Class<*>,
      obj: Any,
      vararg args: Any
    ): Any? {
      if (VERSION.SDK_INT < 26) {
        throw UnsupportedOperationException(
          "Calling default methods on API 24 and 25 is not supported"
        )
      }
      return super.invokeDefaultMethod(method, declaringClass, obj, *args)
    }
  }

  companion object {
    val INSTANCE by lazy { findPlatform() }

    private fun findPlatform(): Platform {
      return if ("Dalvik" == System.getProperty("java.vm.name")) {
        Android()
      } else {
        Platform(true)
      }
    }
  }
}
