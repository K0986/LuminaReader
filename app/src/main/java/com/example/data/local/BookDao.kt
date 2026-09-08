package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastOpened DESC, dateAdded DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookByIdDirect(id: Long): Book?

    @Query("SELECT * FROM books WHERE readingStatus = :status ORDER BY lastOpened DESC")
    fun getBooksByStatus(status: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastOpened DESC")
    fun getFavoriteBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE lastOpened > 0 ORDER BY lastOpened DESC LIMIT 10")
    fun getRecentlyOpenedBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE fileHash = :hash LIMIT 1")
    suspend fun getBookByHash(hash: String): Book?

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<Book>>

    @Query("""
        SELECT b.* FROM books b
        INNER JOIN book_collection_cross_ref ref ON b.id = ref.bookId
        WHERE ref.collectionId = :collectionId
        ORDER BY b.lastOpened DESC
    """)
    fun getBooksForCollection(collectionId: Long): Flow<List<Book>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBooks(books: List<Book>): List<Long>

    @Update
    suspend fun updateBook(book: Book)

    @Query("""
        UPDATE books 
        SET progressPercent = :percent, 
            progressLocation = :location, 
            currentChapterTitle = :chapterTitle,
            lastOpened = :lastOpened,
            readingStatus = :status
        WHERE id = :id
    """)
    suspend fun updateProgress(
        id: Long,
        percent: Float,
        location: String,
        chapterTitle: String,
        lastOpened: Long,
        status: String
    )

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("SELECT COUNT(*) FROM books")
    fun getBookCount(): Flow<Int>
}
