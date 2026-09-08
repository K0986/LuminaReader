package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Book
import com.example.data.model.BookCollectionCrossRef
import com.example.data.model.BookTagCrossRef
import com.example.data.model.Bookmark
import com.example.data.model.Collection
import com.example.data.model.Highlight
import com.example.data.model.ReadingSession
import com.example.data.model.Tag

@Database(
    entities = [
        Book::class,
        Collection::class,
        BookCollectionCrossRef::class,
        Bookmark::class,
        Highlight::class,
        ReadingSession::class,
        Tag::class,
        BookTagCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun collectionDao(): CollectionDao
    abstract fun readingSessionDao(): ReadingSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumina_reader.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
