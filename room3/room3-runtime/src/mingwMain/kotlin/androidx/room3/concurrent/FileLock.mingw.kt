/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room3.concurrent

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LOCKFILE_EXCLUSIVE_LOCK
import platform.windows.LockFileEx
import platform.windows.OPEN_ALWAYS
import platform.windows.OVERLAPPED
import platform.windows.UnlockFileEx

@OptIn(ExperimentalForeignApi::class)
internal actual class FileLock actual constructor(filename: String) {
    private val lockFilename = "$filename.lck"
    private var lockHandle: HANDLE? = null

    actual fun lock() {
        if (lockHandle != null) return
        // Do not share deletion: replacing a held lock file would create a second lock identity.
        val handle =
            CreateFileW(
                lockFilename,
                GENERIC_READ or GENERIC_WRITE.toUInt(),
                (FILE_SHARE_READ or FILE_SHARE_WRITE).toUInt(),
                null,
                OPEN_ALWAYS.toUInt(),
                FILE_ATTRIBUTE_NORMAL.toUInt(),
                null,
            )
        check(handle != null && handle != INVALID_HANDLE_VALUE) {
            "Unable to open lock file (${GetLastError()}): '$lockFilename'."
        }
        try {
            memScoped {
                val overlapped = cValue<OVERLAPPED>()
                check(
                    LockFileEx(
                        handle,
                        LOCKFILE_EXCLUSIVE_LOCK.toUInt(),
                        0u,
                        1u,
                        0u,
                        overlapped.ptr,
                    ) != 0
                ) {
                    "Unable to lock file (${GetLastError()}): '$lockFilename'."
                }
            }
            lockHandle = handle
        } catch (ex: Throwable) {
            CloseHandle(handle)
            throw ex
        }
    }

    actual fun unlock() {
        val handle = lockHandle ?: return
        try {
            memScoped {
                val overlapped = cValue<OVERLAPPED>()
                check(UnlockFileEx(handle, 0u, 1u, 0u, overlapped.ptr) != 0) {
                    "Unable to unlock file (${GetLastError()}): '$lockFilename'."
                }
            }
        } finally {
            CloseHandle(handle)
            lockHandle = null
        }
    }
}
