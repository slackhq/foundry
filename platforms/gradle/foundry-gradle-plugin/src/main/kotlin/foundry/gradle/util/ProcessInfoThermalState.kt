/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.gradle.util

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * Reads `NSProcessInfo.processInfo.thermalState` through the Objective-C runtime with the Foreign
 * Function & Memory API (FFM).
 *
 * Works only on macOS. Safe to call from any thread.
 */
internal object ProcessInfoThermalState {
  /**
   * Resolved once on the first [read]. If resolution throws, `lazy` keeps no value, so the next
   * [read] tries again and throws the same error.
   */
  private val bindings by lazy { Bindings() }

  /**
   * Returns the raw `NSProcessInfoThermalState` value (0 to 3). Throws if a library or symbol is
   * missing or native access is blocked.
   */
  fun read(): Int = bindings.readThermalState()

  /**
   * Holds the `objc_msgSend` handle, the `processInfo` singleton, and the `thermalState` selector.
   */
  private class Bindings {
    private val msgSendLong: MethodHandle
    private val processInfo: MemorySegment
    private val thermalStateSel: MemorySegment

    init {
      // An arena owns the lifetime of native resources. Libraries opened with the global arena
      // stay loaded for the life of the JVM.
      val global = Arena.global()
      // Both paths are macOS system libraries. macOS serves them from the dyld shared cache. No
      // binary exists at either path on disk, and the lookups still succeed.
      // Loading Foundation registers NSProcessInfo with the Objective-C runtime.
      SymbolLookup.libraryLookup(FOUNDATION_PATH, global)
      val objc = SymbolLookup.libraryLookup(LIBOBJC_PATH, global)

      // A FunctionDescriptor is the C signature of a native function, return type first. A
      // downcall handle is a MethodHandle that calls the native function with that signature.
      val linker = Linker.nativeLinker()
      // `Class objc_getClass(const char *)` and `SEL sel_registerName(const char *)` both take
      // one pointer and return one pointer.
      val ptrToPtr = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
      val getClass = linker.downcallHandle(objc.find("objc_getClass").get(), ptrToPtr)
      val selRegisterName = linker.downcallHandle(objc.find("sel_registerName").get(), ptrToPtr)

      // Every Objective-C message send goes through objc_msgSend(receiver, selector, args...).
      // objc_msgSend must be called with the target method's prototype, so there is one handle
      // per prototype: `id (id, SEL)` for +processInfo and `NSInteger (id, SEL)` for -thermalState.
      val msgSend = objc.find("objc_msgSend").get()
      val msgSendPtr =
        linker.downcallHandle(
          msgSend,
          FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )

      // NSInteger is 64 bits on every macOS architecture the JVM supports.
      msgSendLong =
        linker.downcallHandle(
          msgSend,
          FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )

      // Kotlin takes the return type of an invokeExact call from the `as` cast that follows it.
      // Each cast must name the descriptor's return type exactly, or the call throws
      // WrongMethodTypeException at runtime.
      //
      // allocateFrom copies each name into the arena as a NUL-terminated C string. The strings are
      // only needed for the lookups. Classes and selectors are owned by the runtime and stay valid
      // after the arena closes.
      val (cls, processInfoSel, thermalStateSelector) =
        Arena.ofConfined().use { arena ->
          Triple(
            getClass.invokeExact(arena.allocateFrom("NSProcessInfo")) as MemorySegment,
            selRegisterName.invokeExact(arena.allocateFrom("processInfo")) as MemorySegment,
            selRegisterName.invokeExact(arena.allocateFrom("thermalState")) as MemorySegment,
          )
        }

      thermalStateSel = thermalStateSelector

      // Equivalent to `[NSProcessInfo processInfo]`. It returns a process-wide singleton, so the
      // pointer is resolved once and reused for every read.
      processInfo = msgSendPtr.invokeExact(cls, processInfoSel) as MemorySegment
    }

    fun readThermalState(): Int {
      return (msgSendLong.invokeExact(processInfo, thermalStateSel) as Long).toInt()
    }
  }

  private const val FOUNDATION_PATH = "/System/Library/Frameworks/Foundation.framework/Foundation"
  private const val LIBOBJC_PATH = "/usr/lib/libobjc.A.dylib"
}
