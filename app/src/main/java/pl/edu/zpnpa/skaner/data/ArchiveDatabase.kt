package pl.edu.zpnpa.skaner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val text: String,
    val searchText: String,
    val createdAt: Long,
    val pageCount: Int,
)

@Entity(tableName = "pages", primaryKeys = ["documentId", "position"],
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"],
        childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId")])
data class PageEntity(val documentId: String, val position: Int, val fileName: String)

@Dao
interface DocumentDao {
    @Query("""SELECT * FROM documents WHERE :query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '\' ORDER BY createdAt DESC""")
    fun observeDocuments(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun document(id: String): DocumentEntity?

    @Query("SELECT * FROM pages WHERE documentId = :id ORDER BY position")
    suspend fun pages(id: String): List<PageEntity>

    @Query("SELECT fileName FROM pages")
    suspend fun allFileNames(): List<String>

    @Insert suspend fun insertDocument(document: DocumentEntity)
    @Insert suspend fun insertPages(pages: List<PageEntity>)
    @Update suspend fun update(document: DocumentEntity)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [DocumentEntity::class, PageEntity::class], version = 1, exportSchema = true)
abstract class ArchiveDatabase : RoomDatabase() { abstract fun documents(): DocumentDao }
