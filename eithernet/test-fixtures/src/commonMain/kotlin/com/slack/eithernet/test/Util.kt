package com.slack.eithernet.test

import com.slack.eithernet.ApiResult

internal typealias SuspendedResult = suspend (args: Array<Any>) -> ApiResult<*, *>