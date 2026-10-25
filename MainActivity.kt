package com.tubesave.pro

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.work.*
import com.tubesave.pro.data.DownloadRecord
import com.tubesave.pro.download.DownloadWorker
import kotlinx.coroutines.launch
import java.net.URI
import java.util.UUID

private val Navy=Color(0xFF020817)
private val Cyan=Color(0xFF00F0FF)
class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(2,8,23);window.navigationBarColor=android.graphics.Color.rgb(2,8,23);setContent{ TubeSaveScreen() } } }

@Composable fun TubeSaveScreen(){
 val context=LocalContext.current
 val dao=remember{(context.applicationContext as TubeSaveApplication).db.downloads()}
 val records by dao.observe().collectAsStateWithLifecycle(initialValue=emptyList())
 val scope=rememberCoroutineScope()
 var tab by remember{mutableStateOf(0)}
 var link by remember{mutableStateOf("")}
 var message by remember{mutableStateOf("")}
 var selected by remember{mutableStateOf<String?>(null)}
 val manager=remember{WorkManager.getInstance(context)}
 val scheme=darkColorScheme(primary=Cyan,background=Navy,surface=Color(0xFF101E32),onSurface=Color.White,onBackground=Color.White)
 MaterialTheme(colorScheme=scheme){
 Column(Modifier.fillMaxSize().background(Navy).systemBarsPadding().padding(16.dp)){
 Text("TUBESAVE PRO · 4K source-ready",style=MaterialTheme.typography.headlineMedium,color=Cyan)
 Spacer(Modifier.height(14.dp))
 Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
  listOf("Home","Downloads","Library").forEachIndexed{index,name-> Button(onClick={tab=index;selected=null},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=if(tab==index)Cyan else Color(0xFF14253B),contentColor=if(tab==index)Navy else Color.White)){Text(name)} }
 }
 Spacer(Modifier.height(18.dp))
 when(tab){
 0->{ Text("Authorized direct HTTPS media link",color=Color.LightGray)
 OutlinedTextField(value=link,onValueChange={link=it},label={Text("Media URL")},modifier=Modifier.fillMaxWidth(),singleLine=true)
 Spacer(Modifier.height(12.dp))
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
 Button(onClick={val clip=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;link=clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()}){Text("Paste")}
 Button(onClick={link=""}){Text("Clear")}
 Button(onClick={
  val valid=runCatching{val u=URI(link);u.scheme=="https" && !u.host.isNullOrBlank()}.getOrDefault(false)
  if(!valid)message="Enter a valid HTTPS URL" else {
   val name="media_${System.currentTimeMillis()}";val id=UUID.randomUUID().toString()
   scope.launch{dao.save(DownloadRecord(id,link,name));val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("id" to id)).addTag(id).build();manager.enqueue(request);tab=1}
   message="Download queued"
  }
 }){Text("Download")}
 }
 if(message.isNotBlank())Text(message,color=Cyan,modifier=Modifier.padding(top=12.dp))
 Text("Only directly downloadable MP4/WebM or supported audio URLs. YouTube extraction is not supported.",color=Color.LightGray,modifier=Modifier.padding(top=20.dp))
 }
 1->LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
 items(records,key={it.id}){r->DownloadCard(r,onCancel={manager.cancelAllWorkByTag(r.id);scope.launch{dao.setStatus(r.id,"CANCELLED")}},onPause={scope.launch{dao.setStatus(r.id,"PAUSED");manager.cancelAllWorkByTag(r.id)}},onResume={scope.launch{dao.setStatus(r.id,"QUEUED");manager.enqueue(OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("id" to r.id)).addTag(r.id).build())}},onPlay={selected=r.destination;tab=2})}
 }
 2->{if(selected!=null){PlayerScreen(selected!!,onBack={selected=null})}else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
 items(records.filter{it.status=="COMPLETED" && it.destination!=null},key={it.id}){r->DownloadCard(r,onCancel={},onPause={},onResume={},onPlay={selected=r.destination})}
 }}
 }
 }
 }
}
@Composable fun DownloadCard(r:DownloadRecord,onCancel:()->Unit,onPause:()->Unit,onResume:()->Unit,onPlay:()->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF12243A)),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
 Column(Modifier.padding(14.dp)){
 Text(r.fileName,color=Color.White)
 Text("${r.status} • ${r.bytes/1024} KB"+(if(r.total>0)" / ${r.total/1024} KB" else ""),color=Cyan)
 if(r.total>0)LinearProgressIndicator(progress={ (r.bytes.toFloat()/r.total).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))
 if(r.error!=null)Text(r.error,color=Color(0xFFFF9999))
 Row{if(r.status=="COMPLETED")Button(onClick=onPlay){Text("Play")}
 if(r.status=="QUEUED" || r.status=="DOWNLOADING") { TextButton(onClick=onPause){Text("Pause")};TextButton(onClick=onCancel){Text("Cancel")} }
 if(r.status=="PAUSED" || r.status=="FAILED" || r.status=="CANCELLED")TextButton(onClick=onResume){Text("Resume / Retry")}}
 }
 }
}
@Composable fun PlayerScreen(uri:String,onBack:()->Unit){
 val context=LocalContext.current
 val player=remember(uri){ExoPlayer.Builder(context).build().apply{setMediaItem(MediaItem.fromUri(Uri.parse(uri)));prepare();playWhenReady=true}}
 DisposableEffect(player){onDispose{player.release()}}
 Column{Button(onClick=onBack){Text("Back")};AndroidView(factory={PlayerView(it).apply{this.player=player}},modifier=Modifier.fillMaxWidth().height(260.dp))}
}
