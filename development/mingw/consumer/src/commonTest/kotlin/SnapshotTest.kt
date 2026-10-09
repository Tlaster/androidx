/*
 * Copyright 2026 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.room3.Room
import androidx.room3.integration.mingw.Item
import androidx.room3.integration.mingw.StorageDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem

class SnapshotTest {
    @Test
    fun publishedLibrariesPersistOnWindows() = runTest {
        val fs = FileSystem.SYSTEM
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "snapshot-资料-${Random.nextInt()}"
        fs.createDirectories(directory)
        val path = directory / "database.db"
        fun open() =
            Room.databaseBuilder<StorageDatabase>(path.toString())
                .setDriver(BundledSQLiteDriver())
                .build()
        try {
            val database = open()
            try {
                database.items().insert(Item(1, "snapshot"))
                assertFailsWith<IllegalStateException> {
                    database.items().insertAndFail(Item(2, "rollback"))
                }
            } finally {
                database.close()
            }
            val reopened = open()
            try {
                assertEquals(listOf(Item(1, "snapshot")), reopened.items().all())
            } finally {
                reopened.close()
            }
            val job = SupervisorJob()
            val storage =
                OkioStorage(fs, IntegerSerializer, producePath = { directory / "settings" })
            val store =
                DataStoreFactory.create(storage, scope = CoroutineScope(Dispatchers.IO + job))
            try {
                assertEquals(1, store.updateData { it + 1 })
                assertEquals(1, store.data.first())
            } finally {
                job.cancelAndJoin()
            }
        } finally {
            fs.deleteRecursively(directory)
        }
    }

    private object IntegerSerializer : OkioSerializer<Int> {
        override val defaultValue = 0

        override suspend fun readFrom(source: BufferedSource) = source.readInt()

        override suspend fun writeTo(t: Int, sink: BufferedSink) {
            sink.writeInt(t)
        }
    }
}
