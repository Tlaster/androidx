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

import androidx.room3.ColumnInfo
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Entity
data class Item(@PrimaryKey val id: Long, @ColumnInfo(defaultValue = "''") val value: String)

@Dao
interface ItemDao {
    @Insert suspend fun insert(item: Item)

    @Query("SELECT * FROM Item ORDER BY id") suspend fun all(): List<Item>

    @Query("SELECT * FROM Item ORDER BY id") fun observe(): Flow<List<Item>>

    @Transaction
    suspend fun insertAndFail(item: Item) {
        insert(item)
        error("Rollback this insert")
    }
}

@Database(entities = [Item::class], version = 2, exportSchema = false)
@ConstructedBy(StorageDatabaseConstructor::class)
abstract class StorageDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object StorageDatabaseConstructor : RoomDatabaseConstructor<StorageDatabase> {
    override fun initialize(): StorageDatabase
}
