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

import cnames.structs.sqlite3
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import sqlite3.sqlite3_load_extension

// Windows links SQLite with extension support; bundled symbols need no executable exports.
internal actual fun tryLoadExtension(
    db: CPointer<sqlite3>,
    zFile: CPointer<ByteVar>,
    zProc: CPointer<ByteVar>?,
    pzErrMsg: CPointer<CPointerVar<ByteVar>>,
): Int = sqlite3_load_extension(db, zFile, zProc, pzErrMsg)
