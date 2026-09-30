package co.uk.salyant.sdr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val Surface = Color(0xFF12141C)
private val Border = Color(0xFF292D3A)
private val TextMain = Color(0xFFF3F5FA)
private val Muted = Color(0xFF9AA3B8)
private val Accent = Color(0xFF5B6EF5)
private val Green = Color(0xFF33D9B0)
private val Warn = Color(0xFFF5A455)
private val Danger = Color(0xFFFF6B6B)

data class Account(val id:String,val name:String,val email:String,val unread:Int=0,val hot:Int=0,val guardrail:Int=0)
data class Mail(val id:String,val from:String,val subject:String,val body:String,val unread:Boolean)
data class Health(val ok:Boolean,val message:String)

object SalyantApi {
    private suspend fun request(path:String, method:String="GET", body:String?=null): JSONObject =
        withContext(Dispatchers.IO) {
            val c = URL(API_BASE + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("Accept","application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type","application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val raw = stream.bufferedReader().use { it.readText() }
            if (c.responseCode !in 200..299) error("HTTP " + c.responseCode + ": " + raw)
            JSONObject(if (raw.isBlank()) "{}" else raw)
        }

    suspend fun health():Health = try {
        val j=request("/health")
        Health(true,j.optString("message","Backend connected"))
    } catch(e:Exception) { Health(false,e.message ?: "Backend unavailable") }

    suspend fun accounts():List<Account> {
        val j=request("/api/accounts")
        val a=j.optJSONArray("accounts") ?: JSONArray()
        return (0 until a.length()).map { i ->
            val x=a.getJSONObject(i)
            Account(x.optString("id"),x.optString("name",x.optString("email","Mailbox")),
                x.optString("email",x.optString("address","")),
                x.optInt("unread"),x.optInt("hot"),x.optInt("guardrail"))
        }
    }

    suspend fun mail(account:String):List<Mail> {
        val q=URLEncoder.encode(account,"UTF-8")
        val j=request("/api/mail?account=" + q + "&limit=25")
        val a=j.optJSONArray("messages") ?: j.optJSONArray("data") ?: JSONArray()
        return (0 until a.length()).map { i ->
            val x=a.getJSONObject(i)
            Mail(x.optString("id",i.toString()),x.optString("from"),x.optString("subject"),
                x.optString("body",x.optString("snippet","")),x.optBoolean("unread"))
        }
    }

    suspend fun chat(message:String,account:String):String {
        val payload=JSONObject().put("chatInput",message).put("account",account).put("sessionId","android-director")
        val j=request("/chat","POST",payload.toString())
        return j.optString("output",j.optString("text",j.optString("reply",j.toString())))
    }
}

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SalyantApp() }
    }
}

enum class Screen { DASHBOARD, ACCOUNTS, INBOX, OPS, CHAT, SETTINGS }

@Composable
fun SalyantApp() {
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }
    var selected by remember { mutableStateOf<Account?>(null) }
    var connected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { connected=SalyantApi.health().ok }
    MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Surface,primary=Accent,onBackground=TextMain,onSurface=TextMain,secondary=Green,error=Danger)) {
        Scaffold(containerColor=Bg,topBar={TopBar(connected)},bottomBar={BottomNav(screen){screen=it}}) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when(screen) {
                    Screen.DASHBOARD -> Dashboard(connected){screen=it}
                    Screen.ACCOUNTS -> Accounts { selected=it; screen=Screen.INBOX }
                    Screen.INBOX -> Inbox(selected)
                    Screen.OPS -> Ops()
                    Screen.CHAT -> Chat()
                    Screen.SETTINGS -> Settings()
                }
            }
        }
    }
}

@Composable fun TopBar(connected:Boolean) {
    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SALYANT",fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text("SDR COMMAND",fontSize=10.sp,color=Muted,letterSpacing=1.6.sp)
        }
        Surface(shape=RoundedCornerShape(20.dp),color=Surface) {
            Row(Modifier.padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("●",fontSize=10.sp,color=if(connected)Green else Danger)
                Spacer(Modifier.width(6.dp))
                Text(if(connected)"LIVE" else "OFFLINE",fontSize=10.sp,color=Muted)
            }
        }
    }
}

@Composable fun BottomNav(screen:Screen,onSelect:(Screen)->Unit) {
    NavigationBar(containerColor=Color(0xFF0B0D14)) {
        val items=listOf(
            Screen.DASHBOARD to Icons.Default.Dashboard,
            Screen.ACCOUNTS to Icons.Default.People,
            Screen.INBOX to Icons.Default.Mail,
            Screen.OPS to Icons.Default.Settings,
            Screen.CHAT to Icons.Default.AutoAwesome
        )
        items.forEach { (s,icon) ->
            NavigationBarItem(selected=screen==s,onClick={onSelect(s)},
                icon={Icon(icon,contentDescription=null)},
                label={Text(s.name.lowercase().replaceFirstChar{it.uppercase()})})
        }
    }
}

@Composable fun Dashboard(connected:Boolean,onNavigate:(Screen)->Unit) {
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        accounts=runCatching{SalyantApi.accounts()}.getOrDefault(emptyList())
        loading=false
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(4.dp)); Header("Director Dashboard","Live operational view across SALYANT SDR") }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Kpi("Accounts",if(loading)"—" else accounts.size.toString(),Accent,Modifier.weight(1f))
            Kpi("Unread",if(loading)"—" else accounts.sumOf{it.unread}.toString(),Warn,Modifier.weight(1f))
            Kpi("Hot",if(loading)"—" else accounts.sumOf{it.hot}.toString(),Green,Modifier.weight(1f))
        }}
        item { CardBox {
            Text("PIPELINE",fontSize=11.sp,color=Muted,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)
            Spacer(Modifier.height(14.dp)); Pipeline()
        }}
        item { CardBox {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Backend",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                Text(if(connected)"Connected" else "Unavailable",color=if(connected)Green else Danger,fontSize=12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Vercel API → n8n → Zoho / AI Router",fontSize=12.sp,color=Muted)
        }}
        item { Button(onClick={onNavigate(Screen.ACCOUNTS)},modifier=Modifier.fillMaxWidth()) { Text("Open SDR Accounts") } }
        item { OutlinedButton(onClick={onNavigate(Screen.CHAT)},modifier=Modifier.fillMaxWidth()) { Text("Ask SDR AI Director") } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable fun Header(title:String,sub:String) {
    Column { Text(title,fontSize=24.sp,fontWeight=FontWeight.Bold); Text(sub,fontSize=13.sp,color=Muted) }
}
@Composable fun Kpi(label:String,value:String,color:Color,modifier:Modifier) {
    Card(modifier,colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) { Text(value,fontSize=24.sp,fontWeight=FontWeight.Bold); Text(label,fontSize=11.sp,color=Muted); Spacer(Modifier.height(3.dp)); Text("●",color=color,fontSize=9.sp) }
    }
}
@Composable fun CardBox(content:@Composable ColumnScope.()->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp),content=content) }
}
@Composable fun Pipeline() {
    val stages=listOf("Discover","Reachability","Draft","Review","Send")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        stages.forEachIndexed { i,s -> Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Surface(shape=RoundedCornerShape(50),color=if(i<3)Accent else Border) {
                Box(Modifier.size(34.dp),contentAlignment=Alignment.Center){Text((i+1).toString(),fontWeight=FontWeight.Bold)}
            }
            Spacer(Modifier.height(7.dp)); Text(s,fontSize=9.sp,color=Muted)
        }}
    }
}

@Composable fun Accounts(onSelect:(Account)->Unit) {
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching{SalyantApi.accounts()}.onSuccess{accounts=it}.onFailure{error=it.message} }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Header("Accounts","Mailboxes and SDR performance") }
        error?.let { item { Text(it,color=Danger,fontSize=12.sp) } }
        items(accounts) { a -> AccountCard(a){onSelect(a)} }
        if(accounts.isEmpty()) item { CardBox { Text("No live accounts returned.",color=Muted); Text("Check the Vercel SDR API.",fontSize=12.sp,color=Muted) } }
    }
}

@Composable fun AccountCard(a:Account,onClick:()->Unit) {
    Card(Modifier.fillMaxWidth().clickable{onClick()},colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(a.name.ifBlank{"Mailbox"},fontWeight=FontWeight.Bold,fontSize=16.sp); Text(a.email,color=Muted,fontSize=11.sp) }
                Text("›",fontSize=24.sp,color=Muted)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Stat("Unread",a.unread.toString()); Stat("Hot",a.hot.toString()); Stat("Guardrail",a.guardrail.toString()+"%")
            }
        }
    }
}
@Composable fun Stat(label:String,value:String) {
    Surface(color=Color(0xFF191C25),shape=RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(value,fontWeight=FontWeight.Bold,fontSize=14.sp); Text(label,fontSize=9.sp,color=Muted)
        }
    }
}

@Composable fun Inbox(selected:Account?) {
    var mails by remember(selected?.id) { mutableStateOf<List<Mail>>(emptyList()) }
    var active by remember(selected?.id) { mutableStateOf<Mail?>(null) }
    LaunchedEffect(selected?.id) { if(selected!=null) mails=runCatching{SalyantApi.mail(selected.id)}.getOrDefault(emptyList()) }
    Row(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(Modifier.width(145.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            item { Text(selected?.name ?: "All Inbox",fontWeight=FontWeight.Bold,modifier=Modifier.padding(8.dp)) }
            items(mails) { m -> Surface(Modifier.fillMaxWidth().clickable{active=m},color=if(active?.id==m.id)Color(0xFF202431) else Color.Transparent,shape=RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(9.dp)) { Text(m.from,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,fontWeight=if(m.unread)FontWeight.Bold else FontWeight.Normal); Text(m.subject,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=10.sp,color=Muted) }
            }}
        }
        VerticalDivider(color=Border)
        Column(Modifier.weight(1f).padding(16.dp)) {
            if(active==null) Text("Select a message",color=Muted) else {
                Text(active!!.subject,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Text(active!!.from,fontSize=11.sp,color=Muted)
                Spacer(Modifier.height(18.dp)); Text(active!!.body,fontSize=13.sp,lineHeight=20.sp)
                Spacer(Modifier.height(20.dp))
                Button(onClick={}) { Text("Draft reply with AI") }
                OutlinedButton(onClick={}) { Text("Summarise thread") }
            }
        }
    }
}

@Composable fun Chat() {
    val scope=rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf("Hello Director. I’m connected to the SALYANT SDR backend.")) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Header("AI Director","Ask questions or execute supported SDR actions")
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(messages) { m -> Surface(color=Surface,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) { Text(m,Modifier.padding(14.dp),fontSize=13.sp) } }
            if(busy) item { Text("Thinking…",color=Muted,fontSize=12.sp) }
        }
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input,{input=it},Modifier.weight(1f),placeholder={Text("Ask the SDR…")},maxLines=4)
            IconButton(enabled=!busy && input.isNotBlank(),onClick={
                val q=input.trim(); input=""; messages=messages+("You: "+q); busy=true
                scope.launch {
                    val reply=runCatching{SalyantApi.chat(q,"acc-main")}.getOrElse{"Backend error: "+it.message}
                    messages=messages+reply; busy=false
                }
            }) { Icon(Icons.Default.Send,contentDescription="Send") }
        }
    }
}

@Composable fun Ops() {
    var health by remember { mutableStateOf<Health?>(null) }
    LaunchedEffect(Unit) { health=SalyantApi.health() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Header("Operations Center","Production integration status") }
        item { CardBox {
            Text("Vercel API",fontWeight=FontWeight.Bold)
            Text(if(health?.ok==true)"Healthy" else "Checking / unavailable",color=if(health?.ok==true)Green else Warn)
            Text("The Android app never stores n8n credentials.",fontSize=12.sp,color=Muted)
        }}
        item { CardBox {
            Text("Architecture",fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp))
            Text("Android → Vercel proxy → n8n → Zoho / AI Router",fontSize=13.sp)
            Text("Next: tenant auth, billing, push alerts and audit trail.",fontSize=12.sp,color=Muted)
        }}
    }
}

@Composable fun Settings() {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Header("Settings","SALYANT product configuration") }
        item { CardBox {
            Text("Product",fontWeight=FontWeight.Bold); Text("SALYANT SDR",fontSize=18.sp)
            Text("Native Android command centre for lead discovery, reachability, drafting, inbox and AI operations.",fontSize=12.sp,color=Muted)
        }}
        item { CardBox {
            Text("Security",fontWeight=FontWeight.Bold)
            Text("No n8n, Zoho or AI provider secrets are embedded in the APK.",fontSize=12.sp,color=Green)
            Text("Production customer release should add tenant authentication before distribution.",fontSize=12.sp,color=Muted)
        }}
        item { CardBox {
            Text("Commercial roadmap",fontWeight=FontWeight.Bold)
            Text("Internal beta → customer workspaces → subscriptions → usage-based SDR services.",fontSize=12.sp,color=Muted)
        }}
    }
}
