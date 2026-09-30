package co.uk.salyant.sdr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val API_BASE = "https://n8n.salyant.co.uk/webhook/salyant-sdr"
private val Bg = Color(0xFF07080C)
private val Bg2 = Color(0xFF0B0D14)
private val Glass = Color(0x0DFFFFFF)
private val GlassStrong = Color(0x14FFFFFF)
private val GlassSolid = Color(0xFF12141C)
private val Border = Color(0x17FFFFFF)
private val BorderStrong = Color(0x29FFFFFF)
private val TextMain = Color(0xFFF3F5FA)
private val Muted = Color(0xFF9AA3B8)
private val Muted2 = Color(0xFF666F84)
private val Accent = Color(0xFF5B6EF5)
private val Accent2 = Color(0xFF33D9B0)
private val Warn = Color(0xFFF5A455)
private val Danger = Color(0xFFFF6B6B)
private val Purple = Color(0xFF9B6BF5)

data class Account(val id:String,val name:String,val email:String,val unread:Int=0,val hot:Int=0,val guardrail:Int=0)
data class Mail(val id:String,val from:String,val subject:String,val body:String,val unread:Boolean)
data class Health(val ok:Boolean,val message:String)
object SalyantApi {
    private suspend fun request(path:String, method:String="GET", body:String?=null):JSONObject = withContext(Dispatchers.IO) {
        val c=URL(API_BASE+path).openConnection() as HttpURLConnection
        c.requestMethod=method; c.connectTimeout=10000; c.readTimeout=20000
        c.setRequestProperty("Accept","application/json")
        if(body!=null){ c.doOutput=true; c.setRequestProperty("Content-Type","application/json"); c.outputStream.use{it.write(body.toByteArray())} }
        val raw=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
        if(c.responseCode !in 200..299) error("HTTP "+c.responseCode+": "+raw.ifBlank{"Backend error"})
        JSONObject(raw.ifBlank{"{}"})
    }
    suspend fun health()=try{ val j=request("/health"); Health(true,j.optString("message","Backend connected")) }catch(e:Exception){Health(false,e.message?:"Backend unavailable")}
    suspend fun accounts():List<Account>{
        val a=request("/api/accounts").optJSONArray("accounts")?:JSONArray()
        return (0 until a.length()).map{val x=a.getJSONObject(it); Account(x.optString("id"),x.optString("name",x.optString("email","Mailbox")),x.optString("email",x.optString("address","")),x.optInt("unread"),x.optInt("hot"),x.optInt("guardrail"))}
    }
    suspend fun mail(account:String):List<Mail>{
        val q=URLEncoder.encode(account,"UTF-8"); val j=request("/api/mail?account="+q+"&limit=25")
        val a=j.optJSONArray("messages")?:j.optJSONArray("data")?:JSONArray()
        return (0 until a.length()).map{val x=a.getJSONObject(it); Mail(x.optString("id",it.toString()),x.optString("from"),x.optString("subject"),x.optString("body",x.optString("snippet","")),x.optBoolean("unread"))}
    }
    suspend fun chat(message:String,account:String):String{
        val payload=JSONObject().put("chatInput",message).put("account",account).put("sessionId","android-director")
        val j=request("/chat","POST",payload.toString())
        return j.optString("output",j.optString("text",j.optString("reply",j.toString())))
    }
}

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Bg.toArgb()
        window.navigationBarColor=Bg2.toArgb()
        setContent{SalyantApp()}
    }
}

enum class Screen{DASHBOARD,ACCOUNTS,INBOX,OPS,CHAT,SETTINGS}

@Composable fun SalyantApp(){
    var screen by remember{mutableStateOf(Screen.DASHBOARD)}
    var selected by remember{mutableStateOf<Account?>(null)}
    var connected by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){connected=SalyantApi.health().ok}
    MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=GlassSolid,primary=Accent,secondary=Accent2,onBackground=TextMain,onSurface=TextMain,error=Danger)){
        CompositionLocalProvider(LocalContentColor provides TextMain){
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x222B35A8),Bg),radius=900f))){
            Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),
                topBar={TopBar(connected){screen=Screen.SETTINGS}},
                bottomBar={GlassNav(screen){screen=it}}){pad->
                Box(Modifier.padding(pad).fillMaxSize()){
                    when(screen){
                        Screen.DASHBOARD->Dashboard(connected){screen=it}
                        Screen.ACCOUNTS->Accounts{selected=it;screen=Screen.INBOX}
                        Screen.INBOX->Inbox(selected)
                        Screen.OPS->Ops()
                        Screen.CHAT->Chat()
                        Screen.SETTINGS->Settings()
                    }
                }
            }
        }
        }
    }
}

@Composable fun TopBar(connected:Boolean,onSettings:()->Unit){
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal=22.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
        Image(painterResource(co.uk.salyant.sdr.R.drawable.salyant_logo),null,Modifier.width(92.dp).height(50.dp),contentScale=ContentScale.Fit)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(30.dp).background(BorderStrong))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)){
            Text("SDR COMMAND",fontSize=11.sp,color=Muted,letterSpacing=1.8.sp,fontWeight=FontWeight.Medium)
            Text("Autonomous AI SDR",fontSize=10.sp,color=Muted2)
        }
        GlassPill{
            Box(Modifier.size(7.dp).background(if(connected)Accent2 else Danger,CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(if(connected)"LIVE" else "OFFLINE",fontSize=10.sp,color=Muted)
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick=onSettings,modifier=Modifier.size(38.dp).background(Glass,RoundedCornerShape(12.dp)).border(1.dp,Border,RoundedCornerShape(12.dp))){
            Icon(Icons.Default.Settings,null,tint=Muted,modifier=Modifier.size(18.dp))
        }
    }
}

@Composable fun GlassPill(content:@Composable RowScope.()->Unit){
    Row(Modifier.background(Glass,RoundedCornerShape(24.dp)).border(1.dp,Border,RoundedCornerShape(24.dp)).padding(horizontal=11.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,content=content)
}

@Composable fun GlassCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Card(modifier,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Glass),border=BorderStroke(1.dp,Border)){
        Column(Modifier.padding(16.dp),content=content)
    }
}
@Composable fun GlassNav(screen:Screen,onSelect:(Screen)->Unit){
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).background(Bg2.copy(alpha=.97f)).border(1.dp,Border).padding(horizontal=6.dp,vertical=3.dp),horizontalArrangement=Arrangement.SpaceEvenly){
        val items=listOf(
            Screen.DASHBOARD to Pair(Icons.Default.Dashboard,"Dashboard"),
            Screen.ACCOUNTS to Pair(Icons.Default.People,"Accounts"),
            Screen.INBOX to Pair(Icons.Default.Mail,"Inbox"),
            Screen.OPS to Pair(Icons.Default.Tune,"Ops"),
            Screen.CHAT to Pair(Icons.Default.AutoAwesome,"AI Director")
        )
        items.forEach{(s,p)->
            val active=screen==s
            Column(Modifier.weight(1f).clickable{onSelect(s)}.padding(vertical=1.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Box(Modifier.size(34.dp).background(if(active)Accent.copy(alpha=.22f) else Color.Transparent,CircleShape),contentAlignment=Alignment.Center){
                    Icon(p.first,null,tint=if(active)Accent2 else Muted,modifier=Modifier.size(20.dp))
                }
                Text(p.second,fontSize=8.sp,color=if(active)Accent2 else Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable fun Header(title:String,sub:String){
    Column(Modifier.padding(top=6.dp,bottom=4.dp)){
        Text(title,fontSize=25.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.4).sp,color=TextMain)
        Text(sub,fontSize=13.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
    }
}

@Composable fun Dashboard(connected:Boolean,onNavigate:(Screen)->Unit){
    var accounts by remember{mutableStateOf<List<Account>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){
        runCatching{SalyantApi.accounts()}.onSuccess{accounts=it}.onFailure{error=it.message}
        loading=false
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Header("Command Overview","High-level posture across every mailbox — live from the SDR backend.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            val live=error==null
            Kpi("Accounts",if(loading||!live)"—" else accounts.size.toString(),Accent,Modifier.weight(1f))
            Kpi("Unread",if(loading||!live)"—" else accounts.sumOf{it.unread}.toString(),Warn,Modifier.weight(1f))
            Kpi("Hot",if(loading||!live)"—" else accounts.sumOf{it.hot}.toString(),Accent2,Modifier.weight(1f))
        }}
        error?.let{item{GlassCard{Text("Live data unavailable",color=Danger,fontWeight=FontWeight.Bold);Text(it,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=5.dp));Text("The UI remains usable; backend data will appear when the SDR API is healthy.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=5.dp))}}}
        item{GlassCard{Text("PIPELINE",fontSize=11.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp);Spacer(Modifier.height(16.dp));Pipeline()}}
        item{GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){Text("Backend",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(if(connected)"Reachable" else "Unavailable",color=if(connected)Accent2 else Danger,fontSize=12.sp)}
            Text("Android → n8n → Zoho / AI Router",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
        }}
        item{Button(onClick={onNavigate(Screen.ACCOUNTS)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Open SDR Accounts",color=TextMain)}}
        item{OutlinedButton(onClick={onNavigate(Screen.CHAT)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,BorderStrong)){Text("Ask SDR AI Director",color=TextMain)}}
        item{Spacer(Modifier.height(8.dp))}
    }
}
@Composable fun Kpi(label:String,value:String,color:Color,modifier:Modifier){
    GlassCard(modifier){
        Text(value,fontSize=25.sp,fontWeight=FontWeight.Bold)
        Text(label,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
        Box(Modifier.padding(top=8.dp).size(6.dp).background(color,CircleShape))
    }
}

@Composable fun Pipeline(){
    val stages=listOf("Discover","Reachability","Draft","Review","Send")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        stages.forEachIndexed{i,s->
            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.weight(1f)){
                Box(Modifier.size(37.dp).background(if(i<3)Accent else Color(0xFF262A36),CircleShape).border(1.dp,if(i<3)Accent else BorderStrong,CircleShape),contentAlignment=Alignment.Center){Text((i+1).toString(),fontWeight=FontWeight.Bold,color=TextMain)}
                Text(s,fontSize=9.sp,color=Muted,modifier=Modifier.padding(top=7.dp),maxLines=1)
            }
        }
    }
}

@Composable fun Accounts(onSelect:(Account)->Unit){
    var accounts by remember{mutableStateOf<List<Account>>(emptyList())}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){runCatching{SalyantApi.accounts()}.onSuccess{accounts=it}.onFailure{error=it.message}}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Accounts","Each mailbox runs its own AI SDR — discovery, drafting, approval, replies and recovery.")}
        error?.let{item{Text(it,color=Danger,fontSize=12.sp)}}
        items(accounts){a->AccountCard(a){onSelect(a)}}
        if(accounts.isEmpty())item{GlassCard{
            Icon(Icons.Default.CloudOff,null,tint=Muted2,modifier=Modifier.size(28.dp))
            Text("No live accounts returned.",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp))
            Text("The backend currently returned no mailbox data. This is not treated as a fake zero.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
        }}
    }
}

@Composable fun AccountCard(a:Account,onClick:()->Unit){
    GlassCard(Modifier.fillMaxWidth().clickable{onClick()}){
        Row(verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(43.dp).background(Brush.linearGradient(listOf(Accent,Purple)),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){
                Text(a.name.take(2).uppercase(),fontWeight=FontWeight.Bold,color=Color.White)
            }
            Column(Modifier.weight(1f).padding(start=12.dp)){
                Text(a.name.ifBlank{"Mailbox"},fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(a.email.ifBlank{"No address returned"},fontSize=10.5.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight,null,tint=Muted2)
        }
        Row(Modifier.padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            Stat("Unread",a.unread.toString());Stat("Hot",a.hot.toString());Stat("Guardrail",a.guardrail.toString()+"%")
        }
    }
}

@Composable fun Stat(label:String,value:String){
    Surface(color=GlassStrong,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Border)){
        Column(Modifier.padding(horizontal=12.dp,vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(value,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(label,fontSize=8.5.sp,color=Muted2)
        }
    }
}
@Composable fun Inbox(selected:Account?){
    var mails by remember(selected?.id){mutableStateOf<List<Mail>>(emptyList())}
    var active by remember(selected?.id){mutableStateOf<Mail?>(null)}
    var error by remember(selected?.id){mutableStateOf<String?>(null)}
    LaunchedEffect(selected?.id){
        if(selected!=null)runCatching{SalyantApi.mail(selected.id)}.onSuccess{mails=it}.onFailure{error=it.message}
    }
    if(selected==null){
        EmptyState("Inbox","Select a mailbox from Accounts to open its live inbox.")
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)){
        Row(Modifier.padding(top=7.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Inbox",fontSize=25.sp,fontWeight=FontWeight.Bold);Text(selected.name,color=Muted,fontSize=12.sp)}
            GlassPill{Text(mails.size.toString()+" messages",fontSize=10.sp,color=Muted)}
        }
        error?.let{Text(it,color=Danger,fontSize=11.sp,modifier=Modifier.padding(bottom=8.dp))}
        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            GlassCard(Modifier.width(150.dp).fillMaxHeight()){
                LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)){
                    items(mails){m->
                        Surface(Modifier.fillMaxWidth().clickable{active=m},color=if(active?.id==m.id)GlassStrong else Color.Transparent,shape=RoundedCornerShape(11.dp)){
                            Column(Modifier.padding(9.dp)){
                                Text(m.from.ifBlank{"Unknown sender"},fontSize=10.5.sp,fontWeight=if(m.unread)FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(m.subject.ifBlank{"No subject"},fontSize=9.5.sp,color=Muted,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
                            }
                        }
                    }
                    if(mails.isEmpty())item{Text("No messages returned.",fontSize=10.sp,color=Muted2,modifier=Modifier.padding(8.dp))}
                }
            }
            GlassCard(Modifier.weight(1f).fillMaxHeight()){
                if(active==null)EmptyStateInline("Select a message")
                else{
                    Text(active!!.subject.ifBlank{"No subject"},fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text(active!!.from,fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
                    HorizontalDivider(color=Border,modifier=Modifier.padding(vertical=12.dp))
                    Text(active!!.body.ifBlank{"No message body returned."},fontSize=12.5.sp,lineHeight=19.sp,color=TextMain)
                    Row(Modifier.padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        SmallButton("Draft reply with AI",true){}
                        SmallButton("Summarise thread",false){}
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable fun EmptyState(title:String,text:String){
    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Box(Modifier.size(54.dp).background(GlassStrong,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Inbox,null,tint=Muted2)}
        Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp))
        Text(text,fontSize=12.sp,color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(top=6.dp))
    }
}

@Composable fun EmptyStateInline(text:String){
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Icon(Icons.Default.MarkEmailUnread,null,tint=Muted2,modifier=Modifier.size(32.dp));Text(text,fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
    }
}

@Composable fun SmallButton(text:String,primary:Boolean,onClick:()->Unit){
    if(primary)Button(onClick=onClick,shape=RoundedCornerShape(10.dp),contentPadding=PaddingValues(horizontal=11.dp,vertical=7.dp)){Text(text,fontSize=9.sp)}
    else OutlinedButton(onClick=onClick,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,BorderStrong),contentPadding=PaddingValues(horizontal=11.dp,vertical=7.dp)){Text(text,fontSize=9.sp,color=Muted)}
}
@Composable fun Chat(){
    val scope=rememberCoroutineScope()
    var input by remember{mutableStateOf("")}
    var messages by remember{mutableStateOf(listOf("Hello Director. I’m connected to the SALYANT SDR backend."))}
    var busy by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)){
        Header("AI Director","Ask questions or execute supported SDR actions")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("Pipeline status","What needs attention?","Summarise today").forEach{q->GlassPill{Text(q,fontSize=9.5.sp,color=Muted,modifier=Modifier.clickable{input=q})}}
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.Bottom,contentPadding=PaddingValues(top=12.dp,bottom=10.dp),reverseLayout=false){
            items(messages){m->
                Surface(color=GlassStrong,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()){
                    Text(m,Modifier.padding(13.dp),fontSize=12.5.sp,lineHeight=19.sp)
                }
            }
            if(busy)item{Text("Thinking…",color=Muted,fontSize=11.sp)}
        }
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(bottom=8.dp)){
            OutlinedTextField(input,{input=it},Modifier.weight(1f),placeholder={Text("Tell the agent what to do…",color=Muted2)},maxLines=4,shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=BorderStrong))
            IconButton(enabled=!busy&&input.isNotBlank(),onClick={
                val q=input.trim();input="";messages=messages+("You: "+q);busy=true
                scope.launch{val reply=runCatching{SalyantApi.chat(q,"acc-main")}.getOrElse{"Backend error: "+it.message};messages=messages+reply;busy=false}
            },modifier=Modifier.size(48.dp).background(Accent,RoundedCornerShape(14.dp))){
                Icon(Icons.Default.ArrowUpward,null,tint=TextMain)
            }
        }
    }
}

@Composable fun Ops(){
    var health by remember{mutableStateOf<Health?>(null)}
    LaunchedEffect(Unit){health=SalyantApi.health()}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Ops Center","Workflow health, deliverability, CRM sync and team notes — in one place.")}
        item{ConnectorCard("n8n Workflow Engine","Production workflow orchestration","https://n8n.salyant.co.uk",health?.ok==true,"Connected to SDR webhook")}
        item{ConnectorCard("Zoho Mail","Mailbox provider","Managed through n8n",null,"Credentials remain server-side")}
        item{ConnectorCard("AI Router","Model gateway","Managed through n8n",null,"Provider keys never ship in the APK")}
        item{ConnectorCard("CRM","Bitrix24 / configurable","Optional connector",null,"Configure from Settings")}
        item{GlassCard{
            Text("Workflow stages",fontWeight=FontWeight.Bold)
            listOf("Discovery","Reachability","Guardrailed drafting","Human review","Throttled dispatch","Reply intelligence").forEachIndexed{i,s->
                Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(7.dp).background(if(i<3)Accent2 else Muted2,CircleShape));Text(s,fontSize=11.sp,color=Muted,modifier=Modifier.padding(start=9.dp))
                }
            }
        }}
    }
}

@Composable fun ConnectorCard(title:String,sub:String,value:String,ok:Boolean?,detail:String){
    GlassCard(Modifier.fillMaxWidth()){
        Row(verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(42.dp).background(GlassStrong,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){
                Icon(if(title=="n8n Workflow Engine")Icons.Default.AccountTree else Icons.Default.Extension,null,tint=if(ok==true)Accent2 else Muted)
            }
            Column(Modifier.weight(1f).padding(start=12.dp)){
                Text(title,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                Text(sub,fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
                Text(value,fontSize=10.sp,color=Muted2,modifier=Modifier.padding(top=5.dp))
            }
            if(ok!=null)Text(if(ok)"LIVE" else "CHECK",fontSize=9.sp,color=if(ok)Accent2 else Warn,fontWeight=FontWeight.Bold)
        }
        Text(detail,fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
    }
}
@Composable fun Settings(){
    var notifications by remember{mutableStateOf(true)}
    var compact by remember{mutableStateOf(false)}
    var n8n by remember{mutableStateOf("https://n8n.salyant.co.uk")}
    var deliv by remember{mutableStateOf("")}
    var crm by remember{mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Settings","Connections, notifications, security and product configuration")}
        item{GlassCard{
            Text("Appearance",fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("Salyant dark glass",fontSize=12.sp);Text("Matches salyant.co.uk SDR Command",fontSize=10.sp,color=Muted)}
                Text("DEFAULT",fontSize=9.sp,color=Accent2,fontWeight=FontWeight.Bold)
            }
        }}
        item{GlassCard{
            Text("Notifications",fontWeight=FontWeight.Bold)
            SettingSwitch("Operational alerts","Reachability, drafting, replies and failures",notifications){notifications=it}
            SettingSwitch("Compact density","Tighter lists for high-volume SDR work",compact){compact=it}
        }}
        item{GlassCard{
            Text("Data sources & connectors",fontWeight=FontWeight.Bold)
            Text("These values are local configuration only. Provider credentials stay server-side.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=10.dp))
            ConnectorField("n8n base URL",n8n){n8n=it}
            ConnectorField("Deliverability webhook",deliv){deliv=it}
            ConnectorField("CRM webhook / base",crm){crm=it}
            Text("Connectors",fontSize=10.sp,color=Muted2,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp,bottom=6.dp))
            ConnectorLine("n8n workflow engine","Required",true)
            ConnectorLine("Zoho Mail","Server-side",false)
            ConnectorLine("AI Router","Server-side",false)
            ConnectorLine("Bitrix24 / CRM","Optional",crm.isNotBlank())
        }}
        item{GlassCard{
            Text("Security",fontWeight=FontWeight.Bold)
            Text("No Zoho passwords, n8n API keys or AI provider secrets are embedded in this APK.",fontSize=11.sp,color=Accent2,modifier=Modifier.padding(top=7.dp))
            Text("Customer release requires tenant authentication, role-based access, server-side connector isolation, audit logging and billing entitlements.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=7.dp))
        }}
        item{GlassCard{
            Text("About SALYANT SDR",fontWeight=FontWeight.Bold)
            Text("Native Android command centre for the SALYANT Autonomous AI SDR.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
            Text("Version 0.1.2 · Release channel",fontSize=10.sp,color=Muted2,modifier=Modifier.padding(top=5.dp))
        }}
        item{Spacer(Modifier.height(10.dp))}
    }
}

@Composable fun SettingSwitch(title:String,sub:String,value:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(title,fontSize=12.sp);Text(sub,fontSize=10.sp,color=Muted)}
        Switch(checked=value,onCheckedChange=onChange,colors=SwitchDefaults.colors(checkedThumbColor=TextMain,checkedTrackColor=Accent))
    }
}

@Composable fun ConnectorField(label:String,value:String,onChange:(String)->Unit){
    OutlinedTextField(value,onChange,modifier=Modifier.fillMaxWidth().padding(top=7.dp),label={Text(label,fontSize=10.sp)},singleLine=true,shape=RoundedCornerShape(11.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Accent,unfocusedBorderColor=BorderStrong))
}

@Composable fun ConnectorLine(title:String,state:String,connected:Boolean){
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(7.dp).background(if(connected)Accent2 else Muted2,CircleShape))
        Text(title,fontSize=11.sp,modifier=Modifier.padding(start=9.dp).weight(1f))
        Text(state,fontSize=9.sp,color=if(connected)Accent2 else Muted2)
    }
}

@Composable fun SettingsDialog(onClose:()->Unit){
    Dialog(onDismissRequest=onClose){
        GlassCard(Modifier.fillMaxWidth().padding(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Settings",fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=Muted)}
            }
            Text("Use the Settings tab for connectors, notifications, security and product configuration.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
            Button(onClick=onClose,modifier=Modifier.fillMaxWidth().padding(top=14.dp),shape=RoundedCornerShape(12.dp)){Text("Open from navigation")}
        }
    }
}
