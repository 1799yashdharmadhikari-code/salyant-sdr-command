package co.uk.salyant.sdr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val API_BASE = "https://n8n.salyant.co.uk/webhook/salyant-sdr"
private const val PREFS = "salyant_settings"

private data class Palette(
    val bg: Color,
    val bg2: Color,
    val glass: Color,
    val glassStrong: Color,
    val solid: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val muted2: Color,
    val accent: Color,
    val accent2: Color,
    val warn: Color,
    val danger: Color,
    val purple: Color
)

private val DarkPalette = Palette(
    Color(0xFF07080C), Color(0xFF0B0D14), Color(0x121B1D28), Color(0x1BFFFFFF),
    Color(0xFF12141C), Color(0x2AFFFFFF), Color(0x3DFFFFFF),
    Color(0xFFF3F5FA), Color(0xFFA7B0C4), Color(0xFF707A91),
    Color(0xFF6073FF), Color(0xFF35D9B2), Color(0xFFF5A455), Color(0xFFFF6B6B),
    Color(0xFF9C74F7)
)

private val LightPalette = Palette(
    Color(0xFFF4F7FB), Color(0xFFEDF2F8), Color(0xD9FFFFFF), Color(0xF2FFFFFF),
    Color(0xFFFFFFFF), Color(0x260B1220), Color(0x4510182A),
    Color(0xFF111827), Color(0xFF667085), Color(0xFF98A2B3),
    Color(0xFF5165E9), Color(0xFF10B88F), Color(0xFFD98524), Color(0xFFD64545),
    Color(0xFF7650D8)
)

private val PaletteState = mutableStateOf(DarkPalette)
private val Bg get() = PaletteState.value.bg
private val Bg2 get() = PaletteState.value.bg2
private val Glass get() = PaletteState.value.glass
private val GlassStrong get() = PaletteState.value.glassStrong
private val GlassSolid get() = PaletteState.value.solid
private val Border get() = PaletteState.value.border
private val BorderStrong get() = PaletteState.value.borderStrong
private val TextMain get() = PaletteState.value.text
private val Muted get() = PaletteState.value.muted
private val Muted2 get() = PaletteState.value.muted2
private val Accent get() = PaletteState.value.accent
private val Accent2 get() = PaletteState.value.accent2
private val Warn get() = PaletteState.value.warn
private val Danger get() = PaletteState.value.danger
private val Purple get() = PaletteState.value.purple

data class Account(val id:String,val name:String,val email:String,val unread:Int=0,val hot:Int=0,val guardrail:Int=0)
data class Mail(val id:String,val from:String,val subject:String,val body:String,val unread:Boolean)
data class Health(val ok:Boolean,val message:String)
object SalyantApi {
    @Volatile private var baseUrl = API_BASE

    fun setBaseUrl(value:String){ baseUrl=value.trim().trimEnd('/') }

    private suspend fun request(path:String, method:String="GET", body:String?=null):JSONObject = withContext(Dispatchers.IO) {
        val c=URL(baseUrl+path).openConnection() as HttpURLConnection
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

enum class Appearance{SYSTEM,DARK_GLASS,LIGHT_GLASS}

object SettingsStore{
    private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun appearance(c:Context)=runCatching{Appearance.valueOf(p(c).getString("appearance","SYSTEM")?:"SYSTEM")}.getOrDefault(Appearance.SYSTEM)
    fun saveAppearance(c:Context,v:Appearance)=p(c).edit().putString("appearance",v.name).apply()
    fun notifications(c:Context)=p(c).getBoolean("notifications",NotificationManagerCompat.from(c).areNotificationsEnabled())
    fun saveNotifications(c:Context,v:Boolean)=p(c).edit().putBoolean("notifications",v).apply()
    fun backgroundMonitor(c:Context)=p(c).getBoolean("background_monitor",true)
    fun saveBackgroundMonitor(c:Context,v:Boolean)=p(c).edit().putBoolean("background_monitor",v).apply()
    fun autoRefresh(c:Context)=p(c).getBoolean("auto_refresh",true)
    fun saveAutoRefresh(c:Context,v:Boolean)=p(c).edit().putBoolean("auto_refresh",v).apply()
    fun compact(c:Context)=p(c).getBoolean("compact",false)
    fun saveCompact(c:Context,v:Boolean)=p(c).edit().putBoolean("compact",v).apply()
    fun n8nBase(c:Context)=p(c).getString("n8n_base",API_BASE) ?: API_BASE
    fun saveN8nBase(c:Context,v:String)=p(c).edit().putString("n8n_base",v).apply()
}

object HealthHistory{
    private const val KEY="health_history"
    fun record(c:Context,ok:Boolean){
        val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val old=p.getString(KEY,"")?:""
        p.edit().putString(KEY,(old+if(ok)"1" else "0").takeLast(48)).apply()
    }
    fun read(c:Context)=(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"")?:"").map{it=='1'}
}

object NotificationController{
    const val CHANNEL_ID="salyant_operations"
    fun ensureChannel(c:Context){
        if(Build.VERSION.SDK_INT<26)return
        val m=c.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(CHANNEL_ID,"SALYANT operations",NotificationManager.IMPORTANCE_HIGH).apply{
            description="Important SDR backend and operational status changes"
        })
    }
    fun allowed(c:Context):Boolean{
        if(!NotificationManagerCompat.from(c).areNotificationsEnabled())return false
        return Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
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
    val context=LocalContext.current
    var screen by rememberSaveable{mutableStateOf(Screen.DASHBOARD)}
    var selected by remember{mutableStateOf<Account?>(null)}
    var connected by remember{mutableStateOf(false)}
    var lastCheck by remember{mutableStateOf("")}
    var appearance by remember{mutableStateOf(SettingsStore.appearance(context))}
    var autoRefresh by remember{mutableStateOf(SettingsStore.autoRefresh(context))}
    val systemDark=androidx.compose.foundation.isSystemInDarkTheme()
    val isDark=when(appearance){
        Appearance.SYSTEM->systemDark
        Appearance.DARK_GLASS->true
        Appearance.LIGHT_GLASS->false
    }
    LaunchedEffect(appearance,systemDark){PaletteState.value=if(isDark)DarkPalette else LightPalette}
    LaunchedEffect(Unit){
        SalyantApi.setBaseUrl(SettingsStore.n8nBase(context))
        if(SettingsStore.backgroundMonitor(context))SalyantMonitorWorker.schedule(context)
    }
    LaunchedEffect(autoRefresh){
        while(autoRefresh){
            val h=SalyantApi.health()
            connected=h.ok
            HealthHistory.record(context,h.ok)
            lastCheck=if(h.ok)"Just now" else h.message
            delay(30000)
        }
    }
    androidx.compose.runtime.SideEffect{
        (context as? MainActivity)?.window?.statusBarColor=Bg.toArgb()
        (context as? MainActivity)?.window?.navigationBarColor=Bg2.toArgb()
    }
    val scheme=if(isDark)
        darkColorScheme(background=Bg,surface=GlassSolid,primary=Accent,secondary=Accent2,onBackground=TextMain,onSurface=TextMain,error=Danger)
    else
        lightColorScheme(background=Bg,surface=GlassSolid,primary=Accent,secondary=Accent2,onBackground=TextMain,onSurface=TextMain,error=Danger)
    MaterialTheme(colorScheme=scheme){
        CompositionLocalProvider(LocalContentColor provides TextMain){
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Accent.copy(alpha=if(isDark).14f else .08f),Bg),radius=1000f))){
                Scaffold(
                    containerColor=Color.Transparent,
                    contentWindowInsets=WindowInsets(0,0,0,0),
                    topBar={TopBar(connected,lastCheck){screen=Screen.SETTINGS}},
                    bottomBar={GlassNav(screen){screen=it}}
                ){pad->
                    Box(Modifier.padding(pad).fillMaxSize()){
                        androidx.compose.animation.AnimatedContent(
                            targetState=screen,
                            label="screen_transition"
                        ){destination->
                            when(destination){
                                Screen.DASHBOARD->Dashboard(connected,lastCheck){screen=it}
                                Screen.ACCOUNTS->Accounts{selected=it;screen=Screen.INBOX}
                                Screen.INBOX->Inbox(selected)
                                Screen.OPS->Ops(connected,autoRefresh){autoRefresh=it;SettingsStore.saveAutoRefresh(context,it)}
                                Screen.CHAT->Chat()
                                Screen.SETTINGS->Settings(
                                    appearance,
                                    onAppearance={appearance=it;SettingsStore.saveAppearance(context,it)},
                                    autoRefresh,
                                    onAutoRefresh={autoRefresh=it;SettingsStore.saveAutoRefresh(context,it)}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun TopBar(connected:Boolean,lastCheck:String,onSettings:()->Unit){
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min=78.dp).padding(horizontal=18.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Box(Modifier.width(118.dp).height(44.dp),contentAlignment=Alignment.CenterStart){
            Image(
                painterResource(co.uk.salyant.sdr.R.drawable.salyant_logo),
                "SALYANT",
                Modifier.width(116.dp).height(28.dp),
                contentScale=ContentScale.Fit
            )
        }
        Box(Modifier.width(1.dp).height(36.dp).background(BorderStrong))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text("SDR COMMAND",fontSize=11.sp,color=Muted,letterSpacing=1.9.sp,fontWeight=FontWeight.Medium)
            Text("Autonomous AI SDR",fontSize=10.5.sp,color=Muted2)
        }
        Spacer(Modifier.width(8.dp))
        LiveStatus(connected,lastCheck)
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick=onSettings,
            modifier=Modifier.size(46.dp).background(GlassStrong,RoundedCornerShape(16.dp))
                .border(1.dp,Border,RoundedCornerShape(16.dp))
        ){
            Icon(Icons.Default.Settings,"Settings",tint=TextMain,modifier=Modifier.size(20.dp))
        }
    }
}

@Composable fun LiveStatus(connected:Boolean,lastCheck:String){
    val transition=rememberInfiniteTransition(label="live_pulse")
    val pulse by transition.animateFloat(
        initialValue=.45f,targetValue=1f,
        animationSpec=infiniteRepeatable(tween(1200),androidx.compose.animation.core.RepeatMode.Reverse),
        label="pulse"
    )
    Surface(
        color=GlassStrong,
        shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,if(connected)Accent2.copy(alpha=.35f) else Danger.copy(alpha=.35f))
    ){
        Row(Modifier.padding(horizontal=11.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(12.dp),contentAlignment=Alignment.Center){
                Box(Modifier.size(12.dp).border(1.dp,(if(connected)Accent2 else Danger).copy(alpha=pulse),CircleShape))
                Box(Modifier.size(6.dp).background(if(connected)Accent2 else Danger,CircleShape))
            }
            Spacer(Modifier.width(7.dp))
            Column{
                Text(if(connected)"LIVE" else "OFFLINE",fontSize=9.5.sp,color=if(connected)Accent2 else Danger,fontWeight=FontWeight.Bold)
                Text(if(connected)"backend reachable" else "backend unavailable",fontSize=7.sp,color=Muted2,maxLines=1)
            }
        }
    }
}

@Composable fun GlassPill(content:@Composable RowScope.()->Unit){
    Row(Modifier.background(Glass,RoundedCornerShape(24.dp)).border(1.dp,Border,RoundedCornerShape(24.dp)).padding(horizontal=11.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,content=content)
}

@Composable fun GlassCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    val shape=RoundedCornerShape(22.dp)
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(GlassStrong,Glass,Glass.copy(alpha=.72f))))
        .border(1.dp,Border,shape).animateContentSize()){
        Column(Modifier.padding(16.dp),content=content)
    }
}
@Composable fun GlassNav(screen:Screen,onSelect:(Screen)->Unit){
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start=12.dp,end=12.dp,bottom=9.dp)){
        Row(
            Modifier.fillMaxWidth().height(72.dp)
                .background(Brush.verticalGradient(listOf(GlassStrong,Glass)),RoundedCornerShape(30.dp))
                .border(1.dp,BorderStrong,RoundedCornerShape(30.dp))
                .padding(horizontal=6.dp,vertical=5.dp),
            horizontalArrangement=Arrangement.SpaceEvenly,
            verticalAlignment=Alignment.CenterVertically
        ){
            val items=listOf(
                Screen.DASHBOARD to Pair(Icons.Default.Dashboard,"Dashboard"),
                Screen.ACCOUNTS to Pair(Icons.Default.Groups,"Accounts"),
                Screen.INBOX to Pair(Icons.Default.MailOutline,"Inbox"),
                Screen.OPS to Pair(Icons.Default.Tune,"Ops"),
                Screen.CHAT to Pair(Icons.Default.Psychology,"AI Director")
            )
            items.forEach{(s,p)->
                val active=screen==s
                val size by androidx.compose.animation.core.animateDpAsState(if(active)40.dp else 34.dp,tween(220),label="nav_size")
                Column(Modifier.weight(1f).fillMaxHeight().clickable{onSelect(s)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    Box(Modifier.size(size).background(if(active)Accent.copy(alpha=.20f) else Color.Transparent,CircleShape),contentAlignment=Alignment.Center){
                        Icon(p.first,p.second,tint=if(active)Accent2 else Muted,modifier=Modifier.size(20.dp))
                    }
                    Text(p.second,fontSize=8.sp,color=if(active)Accent2 else Muted,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
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

@Composable fun Dashboard(connected:Boolean,lastCheck:String,onNavigate:(Screen)->Unit){
    val context=LocalContext.current
    var accounts by remember{mutableStateOf<List<Account>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){
        runCatching{SalyantApi.accounts()}.onSuccess{accounts=it;error=null}.onFailure{error=it.message}
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
        error?.let{item{GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(8.dp).background(Danger,CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Live data unavailable",color=Danger,fontWeight=FontWeight.Bold)
            }
            Text(it,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=7.dp))
            Text("No mailbox metric is fabricated while the API is unhealthy.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=5.dp))
        }}}
        item{GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("SDR control surface",fontSize=15.sp,fontWeight=FontWeight.Bold)
                    Text("Explicit server-side stages — no guessed completion state",fontSize=10.sp,color=Muted)
                }
                StatusPill(if(connected)"LIVE" else "CHECK",connected)
            }
            Spacer(Modifier.height(10.dp))
            ControlRow("Discovery & qualification","Server workflow")
            ControlRow("Reachability verification","Before drafting")
            ControlRow("Guardrailed drafting","AI + policy gate")
            ControlRow("Human approval","Approval gate")
            ControlRow("Throttled dispatch","Server worker")
            ControlRow("Reply intelligence & recovery","Inbox orchestration")
        }}
        item{GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Backend",fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text("Android → n8n → Zoho / AI Router",fontSize=12.sp,color=Muted)
                }
                Text(if(connected)"Reachable" else "Unavailable",color=if(connected)Accent2 else Danger,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
            }
            Text(if(lastCheck.isBlank())"Checking the live control endpoint…" else "Last check: "+lastCheck,fontSize=10.sp,color=Muted2,modifier=Modifier.padding(top=8.dp))
        }}
        item{Button(onClick={onNavigate(Screen.ACCOUNTS)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Open SDR Accounts",color=TextMain,fontWeight=FontWeight.SemiBold)}}
        item{OutlinedButton(onClick={onNavigate(Screen.CHAT)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,BorderStrong)){Text("Ask SDR AI Director",color=TextMain,fontWeight=FontWeight.SemiBold)}}
    }
}

@Composable fun Kpi(label:String,value:String,color:Color,modifier:Modifier){
    GlassCard(modifier.height(102.dp)){
        Text(value,fontSize=27.sp,fontWeight=FontWeight.Bold,color=TextMain)
        Text(label,fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
        Spacer(Modifier.height(9.dp))
        Box(Modifier.size(7.dp).background(color,CircleShape))
    }
}

@Composable fun ControlRow(title:String,detail:String){
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(8.dp).background(Accent2,CircleShape))
        Column(Modifier.padding(start=10.dp).weight(1f)){
            Text(title,fontSize=11.5.sp)
            Text(detail,fontSize=9.sp,color=Muted2)
        }
    }
}

@Composable fun StatusPill(label:String,good:Boolean){
    Surface(color=if(good)Accent2.copy(alpha=.10f) else Danger.copy(alpha=.10f),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,if(good)Accent2.copy(alpha=.35f) else Danger.copy(alpha=.35f))){
        Text(label,fontSize=8.sp,color=if(good)Accent2 else Danger,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
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

@Composable fun Ops(currentConnected:Boolean,currentAutoRefresh:Boolean,onAutoRefresh:(Boolean)->Unit){
    val context=LocalContext.current
    var backgroundMonitor by remember{mutableStateOf(SettingsStore.backgroundMonitor(context))}
    var history by remember{mutableStateOf(HealthHistory.read(context))}

    LaunchedEffect(Unit){
        while(true){
            history=HealthHistory.read(context)
            delay(15000)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Ops Center","Operational controls, live connector posture and persistent monitoring — not decorative status cards.")}

        item{GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Live system posture",fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text("Core workflows remain server-side on n8n.",fontSize=10.5.sp,color=Muted)
                }
                StatusPill(if(currentConnected)"LIVE" else "CHECK",currentConnected)
            }
            Spacer(Modifier.height(11.dp))
            ConnectorLine("n8n workflow engine",if(currentConnected)"REACHABLE" else "UNAVAILABLE",currentConnected)
            ConnectorLine("Zoho Mail","SERVER-SIDE",false)
            ConnectorLine("AI Router","SERVER-SIDE",false)
            ConnectorLine("CRM","OPTIONAL",false)
        }}

        item{GlassCard{
            Text("Command controls",fontSize=16.sp,fontWeight=FontWeight.Bold)
            Text("These switches affect actual Android runtime behaviour.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=3.dp))
            SettingSwitch(
                "Background health monitor",
                "WorkManager check every 15 minutes; survives app closure",
                backgroundMonitor
            ){
                backgroundMonitor=it
                SettingsStore.saveBackgroundMonitor(context,it)
                SalyantMonitorWorker.setEnabled(context,it)
            }
            SettingSwitch(
                "Live refresh while open",
                "Refresh backend posture every 30 seconds",
                currentAutoRefresh
            ){onAutoRefresh(it)}
        }}

        item{GlassCard{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Recent monitor history",fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text("Persisted Android health checks",fontSize=10.5.sp,color=Muted)
                }
                Text(history.size.toString()+" checks",fontSize=9.sp,color=Muted2)
            }
            Spacer(Modifier.height(11.dp))
            MiniHealthChart(history)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Insight("Healthy",history.count{it}.toString(),Accent2,Modifier.weight(1f))
                Insight("Failed",history.count{!it}.toString(),Danger,Modifier.weight(1f))
                Insight("Current",if(history.lastOrNull()==true)"LIVE" else if(history.isEmpty())"—" else "CHECK",Accent,Modifier.weight(1f))
            }
        }}

        item{GlassCard{
            Text("Server workflow posture",fontSize=16.sp,fontWeight=FontWeight.Bold)
            Text("Android does not run the SDR engine, so killing the app process does not stop discovery, drafting, approval or dispatch.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=8.dp))
            WorkflowRow("Discovery & qualification","Server-side")
            WorkflowRow("Reachability verification","Before drafting")
            WorkflowRow("Guardrailed drafting","AI + policy")
            WorkflowRow("Human approval","Approval gate")
            WorkflowRow("Throttled dispatch & recovery","Server worker")
            WorkflowRow("Reply intelligence","Inbox orchestration")
        }}
    }
}

@Composable fun ConnectorLine(title:String,state:String,connected:Boolean){
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(8.dp).background(if(connected)Accent2 else Muted2,CircleShape))
        Text(title,fontSize=11.5.sp,modifier=Modifier.padding(start=9.dp).weight(1f))
        Text(state,fontSize=8.5.sp,color=if(connected)Accent2 else Muted2,fontWeight=FontWeight.SemiBold)
    }
}

@Composable fun WorkflowRow(title:String,state:String){
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Default.AccountTree,null,tint=Accent2,modifier=Modifier.size(16.dp))
        Text(title,fontSize=11.sp,modifier=Modifier.padding(start=8.dp).weight(1f))
        Surface(color=GlassStrong,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){
            Text(state,fontSize=8.sp,color=Muted,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
        }
    }
}

@Composable fun MiniHealthChart(history:List<Boolean>){
    if(history.isEmpty()){
        Surface(color=GlassStrong,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border)){
            Row(Modifier.fillMaxWidth().height(82.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                Text("No monitor history yet — the chart will build automatically.",fontSize=10.5.sp,color=Muted,textAlign=TextAlign.Center,modifier=Modifier.padding(horizontal=18.dp))
            }
        }
        return
    }
    Canvas(Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(16.dp)).background(GlassStrong)){
        val left=18f
        val right=size.width-18f
        val top=17f
        val bottom=size.height-17f
        val step=if(history.size<2)0f else (right-left)/(history.size-1)
        history.forEachIndexed{i,ok->
            if(i>0){
                val prev=history[i-1]
                drawLine(
                    if(ok&&prev)Accent2 else if(!ok&&!prev)Danger else Accent,
                    Offset(left+step*(i-1),if(prev)top else bottom),
                    Offset(left+step*i,if(ok)top else bottom),
                    strokeWidth=5f,cap=StrokeCap.Round
                )
            }
            drawCircle(if(ok)Accent2 else Danger,4.5f,Offset(left+step*i,if(ok)top else bottom))
        }
    }
}

@Composable fun Insight(label:String,value:String,color:Color,modifier:Modifier){
    Surface(color=GlassStrong,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border),modifier=modifier){
        Column(Modifier.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(value,fontSize=13.sp,fontWeight=FontWeight.Bold,color=color)
            Text(label,fontSize=8.5.sp,color=Muted2)
        }
    }
}

@Composable fun Settings(
    appearance:Appearance,
    onAppearance:(Appearance)->Unit,
    autoRefresh:Boolean,
    onAutoRefresh:(Boolean)->Unit
){
    val context=LocalContext.current
    var notifications by remember{mutableStateOf(SettingsStore.notifications(context)&&NotificationController.allowed(context))}
    var backgroundMonitor by remember{mutableStateOf(SettingsStore.backgroundMonitor(context))}
    var compact by remember{mutableStateOf(SettingsStore.compact(context))}
    var n8n by remember{mutableStateOf(SettingsStore.n8nBase(context))}
    var applied by remember{mutableStateOf(true)}
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        notifications=granted&&NotificationController.allowed(context)
        SettingsStore.saveNotifications(context,notifications)
        if(notifications)SalyantMonitorWorker.schedule(context)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Settings","Appearance, notifications, background monitoring and connector configuration.")}

        item{GlassCard{
            Text("Appearance",fontSize=16.sp,fontWeight=FontWeight.Bold)
            Text("Choose the visual mode independently from system dark mode.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=10.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                AppearanceOption("System",Appearance.SYSTEM,appearance,onAppearance,Modifier.weight(1f))
                AppearanceOption("Dark Glass",Appearance.DARK_GLASS,appearance,onAppearance,Modifier.weight(1f))
                AppearanceOption("Light Glass",Appearance.LIGHT_GLASS,appearance,onAppearance,Modifier.weight(1f))
            }
        }}

        item{GlassCard{
            Text("Notifications",fontSize=16.sp,fontWeight=FontWeight.Bold)
            SettingSwitch(
                "Operational alerts",
                "Android permission for important SDR state changes",
                notifications
            ){
                if(!it){
                    notifications=false
                    SettingsStore.saveNotifications(context,false)
                }else if(Build.VERSION.SDK_INT>=33){
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }else{
                    notifications=true
                    SettingsStore.saveNotifications(context,true)
                    SalyantMonitorWorker.schedule(context)
                }
            }
            SettingSwitch(
                "Background health monitor",
                "Keep checking the control endpoint after the app is closed",
                backgroundMonitor
            ){
                backgroundMonitor=it
                SettingsStore.saveBackgroundMonitor(context,it)
                SalyantMonitorWorker.setEnabled(context,it)
            }
            SettingSwitch(
                "Live refresh while open",
                "Refresh the SDR control endpoint every 30 seconds",
                autoRefresh
            ){onAutoRefresh(it)}
        }}

        item{GlassCard{
            Text("Data sources & connectors",fontSize=16.sp,fontWeight=FontWeight.Bold)
            Text("n8n can be changed for trusted environments; provider secrets remain server-side.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=4.dp,bottom=6.dp))
            ConnectorField("n8n base URL",n8n){n8n=it;applied=false}
            Row(Modifier.fillMaxWidth().padding(top=9.dp),verticalAlignment=Alignment.CenterVertically){
                Text(if(applied)"Client uses the saved endpoint" else "Unsaved endpoint change",fontSize=9.5.sp,color=if(applied)Accent2 else Warn,modifier=Modifier.weight(1f))
                Button(
                    onClick={
                        val value=n8n.trim().trimEnd('/')
                        if(value.startsWith("https://")){
                            SettingsStore.saveN8nBase(context,value)
                            SalyantApi.setBaseUrl(value)
                            applied=true
                        }
                    },
                    enabled=!applied,
                    shape=RoundedCornerShape(12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Accent)
                ){Text("Apply",fontSize=10.sp)}
            }
            Spacer(Modifier.height(4.dp))
            ConnectorLine("n8n workflow engine","CLIENT",true)
            ConnectorLine("Zoho Mail","SERVER-SIDE",false)
            ConnectorLine("AI Router","SERVER-SIDE",false)
            ConnectorLine("CRM","OPTIONAL",false)
        }}

        item{GlassCard{
            Text("Security & continuity",fontSize=16.sp,fontWeight=FontWeight.Bold)
            Text("No Zoho passwords, n8n API keys or model provider secrets are embedded in this APK.",fontSize=10.5.sp,color=Accent2,modifier=Modifier.padding(top=6.dp))
            Text("Core SDR workflows continue on n8n when Android is closed. WorkManager only handles Android-side health monitoring and alerts.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=7.dp))
        }}

        item{GlassCard{
            Text("About SALYANT SDR",fontSize=16.sp,fontWeight=FontWeight.Bold)
            Text("Native Android command centre for the SALYANT Autonomous AI SDR.",fontSize=10.5.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
            Text("Version 0.1.3 · UI refinement build",fontSize=9.5.sp,color=Muted2,modifier=Modifier.padding(top=5.dp))
        }}
    }
}

@Composable fun AppearanceOption(label:String,value:Appearance,selected:Appearance,onSelect:(Appearance)->Unit,modifier:Modifier){
    val active=value==selected
    Surface(
        modifier.clickable{onSelect(value)},
        color=if(active)Accent.copy(alpha=.15f) else GlassStrong,
        shape=RoundedCornerShape(15.dp),
        border=BorderStroke(1.dp,if(active)Accent.copy(alpha=.55f) else Border)
    ){
        Column(Modifier.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.size(22.dp).background(
                when(value){
                    Appearance.SYSTEM->Brush.linearGradient(listOf(DarkPalette.bg,LightPalette.bg))
                    Appearance.DARK_GLASS->Brush.linearGradient(listOf(Color(0xFF141722),Color(0xFF30364A)))
                    Appearance.LIGHT_GLASS->Brush.linearGradient(listOf(Color.White,Color(0xFFEAF0F8)))
                },CircleShape
            ).border(1.dp,if(active)Accent else Border,CircleShape))
            Text(label,fontSize=8.5.sp,color=if(active)Accent2 else Muted,modifier=Modifier.padding(top=6.dp),maxLines=1)
        }
    }
}

@Composable fun SettingSwitch(title:String,sub:String,value:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().padding(top=13.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f).padding(end=12.dp)){
            Text(title,fontSize=11.5.sp,fontWeight=FontWeight.Medium)
            Text(sub,fontSize=9.5.sp,color=Muted)
        }
        Switch(checked=value,onCheckedChange=onChange,colors=SwitchDefaults.colors(
            checkedThumbColor=TextMain,checkedTrackColor=Accent,
            uncheckedThumbColor=Muted,uncheckedTrackColor=GlassStrong
        ))
    }
}

@Composable fun ConnectorField(label:String,value:String,onChange:(String)->Unit){
    OutlinedTextField(
        value,onChange,modifier=Modifier.fillMaxWidth().padding(top=7.dp),
        label={Text(label,fontSize=10.sp)},singleLine=true,shape=RoundedCornerShape(12.dp),
        colors=OutlinedTextFieldDefaults.colors(
            focusedBorderColor=Accent,unfocusedBorderColor=BorderStrong,
            focusedContainerColor=GlassStrong,unfocusedContainerColor=Glass
        )
    )
}
