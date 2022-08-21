package com.android1500.gpssetter.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Database(entities = [User::class], version = 18)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    class Callback @Inject constructor(@ApplicationScope private val applicationScope: CoroutineScope) : RoomDatabase.Callback(){
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            applicationScope.launch {

            }
        }
    }
}