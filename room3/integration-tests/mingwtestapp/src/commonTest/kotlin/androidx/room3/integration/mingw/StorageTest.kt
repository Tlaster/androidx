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

package androidx.room3.integration.mingw

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.kruth.assertThat
import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem

class StorageTest {
    private val directory =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "androidx-mingw-${Random.nextInt()}" / "资料"
    private val databasePath = directory / "nested" / "database.db"

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.deleteRecursively(directory.parent!!, mustExist = false)
    }

    private fun openDatabase(): StorageDatabase {
        FileSystem.SYSTEM.createDirectories(databasePath.parent!!)
        return Room.databaseBuilder<StorageDatabase>(databasePath.toString())
            .setDriver(BundledSQLiteDriver())
            .addMigrations(
                Migration(1, 2) {
                    it.execSQL("ALTER TABLE Item ADD COLUMN value TEXT NOT NULL DEFAULT ''")
                }
            )
            .build()
    }

    @Test
    fun fileDatabasePersistsAndRollsBack() = runTest {
        val database = openDatabase()
        try {
            database.items().insert(Item(1, "持久化"))
            assertFailsWith<IllegalStateException> {
                database.items().insertAndFail(Item(2, "rolled back"))
            }
            assertThat(database.items().all()).containsExactly(Item(1, "持久化"))
        } finally {
            database.close()
        }
        val reopened = openDatabase()
        try {
            assertThat(reopened.items().all()).containsExactly(Item(1, "持久化"))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun fileDatabaseMigratesExistingRows() = runTest {
        FileSystem.SYSTEM.createDirectories(databasePath.parent!!)
        BundledSQLiteDriver().open(databasePath.toString()).use {
            it.execSQL("CREATE TABLE Item (id INTEGER NOT NULL PRIMARY KEY)")
            it.execSQL("INSERT INTO Item VALUES (7)")
            it.execSQL("PRAGMA user_version = 1")
        }
        val database = openDatabase()
        try {
            assertThat(database.items().all()).containsExactly(Item(7, ""))
        } finally {
            database.close()
        }
    }

    @Test
    fun flowObservesCommittedWrite() = runTest {
        val database = openDatabase()
        try {
            val subscribed = CompletableDeferred<Unit>()
            val observed = async {
                database
                    .items()
                    .observe()
                    .onEach { if (it.isEmpty()) subscribed.complete(Unit) }
                    .first { it.isNotEmpty() }
            }
            subscribed.await()
            database.items().insert(Item(1, "changed"))
            assertThat(observed.await()).containsExactly(Item(1, "changed"))
        } finally {
            database.close()
        }
    }

    @Test
    fun dataStoreConcurrentUpdatesSurviveReopen() = runTest {
        val path = directory / "settings.pb"
        val job = SupervisorJob()
        fun storage() = OkioStorage(FileSystem.SYSTEM, IntegerSerializer, producePath = { path })
        val store =
            DataStoreFactory.create(
                storage = storage(),
                scope = CoroutineScope(Dispatchers.IO + job),
            )
        try {
            (1..40).map { async { store.updateData { it + 1 } } }.awaitAll()
            assertThat(store.data.first()).isEqualTo(40)
            assertFailsWith<IllegalStateException> { store.updateData { error("cancel update") } }
        } finally {
            job.cancelAndJoin()
        }
        val reopenedJob = SupervisorJob()
        try {
            val reopened =
                DataStoreFactory.create(
                    storage = storage(),
                    scope = CoroutineScope(Dispatchers.IO + reopenedJob),
                )
            assertThat(reopened.data.first()).isEqualTo(40)
        } finally {
            reopenedJob.cancelAndJoin()
        }
    }

    private object IntegerSerializer : OkioSerializer<Int> {
        override val defaultValue: Int = 0

        override suspend fun readFrom(source: BufferedSource): Int = source.readInt()

        override suspend fun writeTo(t: Int, sink: BufferedSink) {
            sink.writeInt(t)
        }
    }
}
