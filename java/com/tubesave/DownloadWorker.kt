package com.tubesave.pro.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.tubesave.pro.TubeSaveApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class DownloadWorker(context:Context, params:WorkerParameters):CoroutineWorker(context,params) {
 private val dao=(context.applicationContext as TubeSaveApplication).db.downloads()
 private val client=OkHttpClient.Builder().followRedirects(false).callTimeout(0,TimeUnit.MILLISECONDS).build()
 override suspend fun doWork():Result = withContext(Dispatchers.IO) {
  val id=inputData.getString("id")?:return@withContext Result.failure()
  val item=dao.get(id)?:return@withContext Result.failure()
  val temp=File(applicationContext.filesDir,"downloads/$id.part").apply{parentFile?.mkdirs()}
  var done=temp.takeIf{it.exists()}?.length()?:0L
  var total=item.total
  var published:Uri?=null
  try {
   setForeground(foreground())
   var url=item.url
   var response:Response?=null
   for(hop in 0..5){
    validate(url)
    val request=Request.Builder().url(url).apply {
     if(done>0){header("Range","bytes=$done-");(item.etag?:item.lastModified)?.let{header("If-Range",it)}}
    }.build()
    val r=client.newCall(request).execute()
    if(r.code in 300..399){
     val location=r.header("Location")?:throw IOException("Redirect without Location")
     url=URI(url).resolve(location).toString();r.close()
     if(hop==5)throw IOException("Too many redirects")
    }else{response=r;break}
   }
   response?.use {r->
    if(!r.isSuccessful)throw IOException("HTTP ${r.code}")
    val mime=r.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    if(mime !in setOf("video/mp4","video/webm","audio/mpeg","audio/mp4","audio/webm","audio/ogg"))throw IOException("Unsupported media type: $mime")
    val body=r.body?:throw IOException("Empty body")
    val append=done>0 && r.code==206
    if(append){
     val contentRange=r.header("Content-Range")?:throw IOException("Missing Content-Range")
     val match=Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(contentRange)
      ?:throw IOException("Invalid Content-Range")
     if(match.groupValues[1].toLong()!=done)throw IOException("Incorrect resume offset")
     val previous=item.etag?:item.lastModified
     val current=if(item.etag!=null)r.header("ETag") else r.header("Last-Modified")
     if(previous!=null && current!=previous)throw IOException("Source changed; restart download")
     total=match.groupValues[3].toLongOrNull()?:-1L
    }else{
     // A server may ignore Range and send HTTP 200; restart rather than corrupt the file.
     done=0L
     total=body.contentLength()
    }
    dao.validators(id,r.header("ETag"),r.header("Last-Modified"))
    dao.update(id,"DOWNLOADING",done,total,null,null)
    java.io.FileOutputStream(temp,append).use{output->
     body.byteStream().use{input->
      val buffer=ByteArray(128*1024)
      var last=0L
      while(true){
       if(isStopped)throw IOException("Stopped")
       val count=input.read(buffer)
       if(count<0)break
       output.write(buffer,0,count);done+=count
       val now=System.currentTimeMillis()
       if(now-last>500){dao.update(id,"DOWNLOADING",done,total,null,null);last=now}
      }
      output.fd.sync()
     }
    }
    if(total>=0 && done!=total)throw IOException("Incomplete download ($done/$total)")
    if(done==0L)throw IOException("Empty download")
    val video=mime.startsWith("video/")
    val ext=if(mime=="video/webm" || mime=="audio/webm")"webm" else if(mime=="video/mp4")"mp4" else if(mime=="audio/mpeg")"mp3" else if(mime=="audio/ogg")"ogg" else "m4a"
    val safeName=item.fileName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"),"_").take(65).ifBlank{"media"}+".$ext"
    val values=ContentValues().apply{
     put(MediaStore.MediaColumns.DISPLAY_NAME,safeName)
     put(MediaStore.MediaColumns.MIME_TYPE,mime)
     put(MediaStore.MediaColumns.RELATIVE_PATH,if(video)"Movies/TubeSavePro" else "Music/TubeSavePro")
     put(MediaStore.MediaColumns.IS_PENDING,1)
    }
    val collection=if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val uri=applicationContext.contentResolver.insert(collection,values)?:throw IOException("MediaStore insert failed")
    published=uri
    applicationContext.contentResolver.openOutputStream(uri,"w")?.use{output->temp.inputStream().use{it.copyTo(output,128*1024)}}
     ?:throw IOException("MediaStore output failed")
    applicationContext.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
    published=null
    temp.delete()
    dao.update(id,"COMPLETED",done,total,uri.toString(),null)
   }?:throw IOException("No response")
   Result.success()
  }catch(e:Exception){
   published?.let{applicationContext.contentResolver.delete(it,null,null)}
   val latest=dao.get(id)
   val status=if(latest?.status=="PAUSED")"PAUSED" else if(isStopped)"CANCELLED" else "FAILED"
   dao.update(id,status,temp.takeIf{it.exists()}?.length()?:done,total,null,e.message)
   Result.failure()
  }
 }
 private fun validate(url:String){
  val u=URI(url)
  if(u.scheme!="https" || u.userInfo!=null || u.host.isNullOrBlank())throw IOException("Public HTTPS URL required")
  val host=u.host.lowercase()
  if(host=="localhost" || host.endsWith(".local") || host.endsWith(".internal"))throw IOException("Local network blocked")
  if(InetAddress.getAllByName(host).any{it.isAnyLocalAddress || it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isMulticastAddress})throw IOException("Private IP blocked")
 }
 private fun foreground():ForegroundInfo{
  val manager=applicationContext.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("downloads","Downloads",NotificationManager.IMPORTANCE_LOW))
  val note=Notification.Builder(applicationContext,"downloads").setSmallIcon(android.R.drawable.stat_sys_download)
   .setContentTitle("TubeSave Pro").setContentText("Downloading media").setOngoing(true).build()
  return if(Build.VERSION.SDK_INT>=29)ForegroundInfo(1001,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(1001,note)
 }
}
