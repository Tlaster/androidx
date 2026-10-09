/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.sqlite.driver

import androidx.sqlite.throwSQLiteException
import cnames.structs.sqlite3
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import platform.posix.RTLD_NOW
import platform.posix.dlclose
import platform.posix.dlopen
import platform.posix.dlsym
import sqlite3.SQLITE_ERROR

// C Signature:
//   int sqlite3_load_extension(sqlite3 *db, const char *zFile, const char *zProc, char **pzErrMsg)
private typealias LoadExtensionCFunction =
    CFunction<
        (
            CPointer<sqlite3>?,
            CPointer<ByteVar>?,
            CPointer<ByteVar>?,
            CPointer<CPointerVar<ByteVar>>?,
        ) -> Int
    >

/**
 * Dynamically loads sqlite3_load_extension and invokes it. This dynamic invocation is needed
 * because a dynamically linked `libsqlite3` might have been compiled with `OMIT_LOAD_EXTENSION`
 * such as is the case with iOS. b/445992581
 */
internal actual fun tryLoadExtension(
    db: CPointer<sqlite3>,
    zFile: CPointer<ByteVar>,
    zProc: CPointer<ByteVar>?,
    pzErrMsg: CPointer<CPointerVar<ByteVar>>,
): Int {
    val handle = dlopen(null, RTLD_NOW) ?: return SQLITE_ERROR
    try {
        val symbol =
            dlsym(handle, "sqlite3_load_extension")
                ?: throwSQLiteException(SQLITE_ERROR, "Symbol sqlite3_load_extension not found.")
        val cFunction = symbol.reinterpret<LoadExtensionCFunction>()
        return cFunction.invoke(db, zFile, zProc, pzErrMsg)
    } finally {
        dlclose(handle)
    }
}
