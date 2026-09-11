package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Book
import com.example.data.model.BookCollectionCrossRef
import com.example.data.model.BookTagCrossRef
import com.example.data.model.Bookmark
import com.example.data.model.Collection
import com.example.data.model.Highlight
import com.example.data.model.PdfPageTextEntity
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
        BookTagCrossRef::class,
        PdfPageTextEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun collectionDao(): CollectionDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun pdfPageTextDao(): PdfPageTextDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Adds the PDF text index. Libraries, bookmarks, highlights and reading history are
         * preserved: an upgrade must never cost a reader their annotations.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pdf_page_text` (" +
                        "`bookId` INTEGER NOT NULL, " +
                        "`pageIndex` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`extractedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`bookId`, `pageIndex`), " +
                        "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pdf_page_text_bookId` " +
                        "ON `pdf_page_text` (`bookId`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumina_reader.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
