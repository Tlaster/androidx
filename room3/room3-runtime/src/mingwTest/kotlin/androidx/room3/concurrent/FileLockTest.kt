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

import androidx.kruth.assertThat
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.FileSystem
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.ERROR_LOCK_VIOLATION
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetLastError
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LOCKFILE_EXCLUSIVE_LOCK
import platform.windows.LOCKFILE_FAIL_IMMEDIATELY
import platform.windows.LockFileEx
import platform.windows.OPEN_EXISTING
import platform.windows.OVERLAPPED

@OptIn(ExperimentalForeignApi::class)
class FileLockTest {
    private val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "room-lock-资料-${Random.nextInt()}"

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.delete(path.parent!! / "${path.name}.lck", mustExist = false)
    }

    @Test
    fun blocksOtherHandlesAndReleasesAfterUnlock() {
        val lock = FileLock(path.toString())
        try {
            lock.lock()
            lock.lock()
            assertThat(tryLockFromAnotherHandle()).isFalse()
        } finally {
            lock.unlock()
        }
        lock.unlock()
        assertThat(tryLockFromAnotherHandle()).isTrue()
        lock.lock()
        lock.unlock()
    }

    @Test
    fun failedOpenDoesNotKeepHandle() {
        val lock = FileLock((path / "missing" / "database").toString())
        assertFailsWith<IllegalStateException> { lock.lock() }
        lock.unlock()
        FileSystem.SYSTEM.createDirectories(path / "missing")
        try {
            lock.lock()
            lock.unlock()
        } finally {
            FileSystem.SYSTEM.deleteRecursively(path)
        }
    }

    private fun tryLockFromAnotherHandle(): Boolean = memScoped {
        val handle =
            CreateFileW(
                "$path.lck",
                GENERIC_READ or GENERIC_WRITE.toUInt(),
                (FILE_SHARE_READ or FILE_SHARE_WRITE).toUInt(),
                null,
                OPEN_EXISTING.toUInt(),
                FILE_ATTRIBUTE_NORMAL.toUInt(),
                null,
            )
        check(handle != null && handle != INVALID_HANDLE_VALUE)
        try {
            val overlapped = cValue<OVERLAPPED>()
            val locked =
                LockFileEx(
                    handle,
                    (LOCKFILE_EXCLUSIVE_LOCK or LOCKFILE_FAIL_IMMEDIATELY).toUInt(),
                    0u,
                    1u,
                    0u,
                    overlapped.ptr,
                ) != 0
            if (!locked) assertThat(GetLastError()).isEqualTo(ERROR_LOCK_VIOLATION.toUInt())
            locked
        } finally {
            CloseHandle(handle)
        }
    }
}
