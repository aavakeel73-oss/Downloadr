package com.tubesave.pro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadRecord(
 @PrimaryKey val id: String,
 val url: String,
 val fileName: String,
 val mimeType: String = "video/mp4",
 val status: String = "QUEUED",
 val bytes: Long = 0,
 val total: Long = -1,
 val destination: String? = null,
 val error: String? = null,
 val etag: String? = null,
 val lastModified: String? = null,
 val createdAt: Long = System.currentTimeMillis()
)

@Dao interface DownloadDao {
 @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observe(): Flow<List<DownloadRecord>>
 @Query("SELECT * FROM downloads WHERE id=:id LIMIT 1") suspend fun get(id:String):DownloadRecord?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(item:DownloadRecord)
 @Query("UPDATE downloads SET status=:status, bytes=:bytes, total=:total, destination=:destination, error=:error WHERE id=:id") suspend fun update(id:String,status:String,bytes:Long,total:Long,destination:String?,error:String?)
 @Query("UPDATE downloads SET etag=:etag, lastModified=:modified WHERE id=:id") suspend fun validators(id:String,etag:String?,modified:String?)
 @Query("UPDATE downloads SET status=:status WHERE id=:id") suspend fun setStatus(id:String,status:String)
}

@Database(entities=[DownloadRecord::class],version=2,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){ abstract fun downloads():DownloadDao }
