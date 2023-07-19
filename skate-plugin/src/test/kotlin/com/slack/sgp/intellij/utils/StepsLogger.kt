// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.

package com.slack.sgp.intellij.utils

import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker

object StepsLogger {
  private var initializaed = false
  @JvmStatic
  fun init() {
    if (initializaed.not()) {
      StepWorker.registerProcessor(StepLogger())
      initializaed = true
    }
  }
}
