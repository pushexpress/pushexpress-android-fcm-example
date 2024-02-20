package com.example.pxfcmapp

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.pxfcmapp.models.Postback
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// NOTIFICATION RELATED
const val NOTIFICATION_CHANNEL_ID = "sdkpushexpress_notification_channel"
const val NOTIFICATION_CHANNEL_NAME = "Default"
const val INTENT_ACTION_CLICK = "com.pushexpress.sdk.ACTION_CLICK"
const val PX_TITLE_KEY = "px.title"
const val PX_BODY_KEY = "px.body"
const val PX_MSG_ID_KEY = "px.msg_id"
const val PX_IMAGE_KEY = "px.image"
const val PX_ICON_KEY = "px.icon"
const val PX_LINK_KEY = "px.link"
const val EXTRA_PX_MSG_ID = "EXTRA_PX_MSG_ID"
const val EXTRA_PX_LINK = "EXTRA_PX_LINK"

// ***** BEGIN PX INTERNALS: DO NOT MODIFY! *****
const val PX_PREFERENCES: String = "px_preferences"
const val PX_PREFERENCES_APP_ID: String = "app_id"
const val PX_PREFERENCES_TT_TOKEN: String = "tt_token"
const val PX_PREFERENCES_IC_TOKEN: String = "ic_token"
const val PX_PREFERENCES_IC_ID: String = "ic_id"
const val PX_PREFERENCES_EXT_ID: String = "ext_id"
const val PX_LOG_TAG: String = "px_api"
// ***** END PX INTERNALS *****
const val TEST_EXTERNAL_ID: String = "666"

// Put your REAL px_app_id here
const val PX_APP_ID: String = ""
// Predefined tags, you can fill it later in code
var PX_TAGS: MutableMap<String, String> = mutableMapOf(
    "audiences" to "",
    "ad_id" to "",
    "webmaster" to "",
)

class MainActivity : ComponentActivity() {
    private lateinit var pxPreferences: SharedPreferences
    private val pxOkHttp = OkHttpClient()

    private val pxNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notifications permission granted",
                Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "FCM can't post notifications without POST_NOTIFICATIONS permission",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun pxAskNotificationPermission() {
        // This is only necessary for API Level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else {
                // Directly ask for the permission
                pxNotificationPermissionLauncher.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pxPreferences = getSharedPreferences("${packageName}_${PX_PREFERENCES}",
            Context.MODE_PRIVATE)
        setContent {
            MainScreen(extId = "", mainActivity = this@MainActivity)
        }
        // ***** BEGIN PX INITIALIZATION *****

        // Set audiences, like 'gender:F,age:>=21' or 'offer1234'
        PX_TAGS["audiences"] = "google_ads_offer555"

        // Set Advertising Id
        //   Warning:
        //   It is not easy to get advertising id properly!!!
        //   And you don't need it in most cases.
        //   Contact our support otherwise!
        // PX_TAGS["ad_id"] = getAdvId()

        // Set Webmaster tag (only if you use it!!!)
        // PX_TAGS["webmaster"] = ""



        pxInitialize(PX_APP_ID)
        pxActivate(TEST_EXTERNAL_ID)
        // make new install with empty extId
        // pxActivate("my_ext_id_user_12345")  // make new install with specified extId
        // pxActivate(pxGetInstanceToken())    // make new install with static PX Instance Token

        // other 'public' methods:
        /*
          pxGetInstanceToken()  // static installation id, uniq for app install
          pxGetExternalId()     // your extId, setted via pxActivate() or pxSetExternalId()
          pxSetExternalId("")   // modify extId for existed instance (logged in user session)
          pxDeactivate()        // deactivate (logout) current extId
        */

        if (intent.hasExtra("px.msg_id")) {
            pxSendClick(intent.getStringExtra("px.msg_id").orEmpty())
        }

        pxAskNotificationPermission()
        // ***** END PX INITIALIZATION *****
    }

    // ***** BEGIN PX SDK INTERFACE: DO NOT MODIFY! *****
    private fun pxInitialize(appId: String) {
        if (appId.isEmpty()) {
            throw IllegalArgumentException("Bad appId, get it from your px admin page'")
        }

        Log.d(PX_LOG_TAG, "pxInitialize: appId $appId")
        CoroutineScope(Dispatchers.IO).launch { pxInitializeIcToken(appId) }
    }

    private fun pxActivate(extId: String) {
        Log.d(PX_LOG_TAG, "pxActivate: extId $extId")
        pxPreferences.edit().putString(PX_PREFERENCES_EXT_ID, extId).apply()
        CoroutineScope(Dispatchers.IO).launch {
            pxGetInstanceId(extId)
            pxGetTransportToken()
        }
    }

    fun pxSetExternalId(extId: String) {
        Log.d(PX_LOG_TAG, "pxSetExternalId: extId $extId")
        pxPreferences.edit().putString(PX_PREFERENCES_EXT_ID, extId).apply()
        CoroutineScope(Dispatchers.IO).launch {
            pxUpdateAppInfo()
        }
    }

    fun pxDeactivate() {
        val icToken = pxPreferences.getString(PX_PREFERENCES_IC_TOKEN, "").orEmpty()
        val icId = pxPreferences.getString(PX_PREFERENCES_IC_ID, "").orEmpty()
        val extId = pxPreferences.getString(PX_PREFERENCES_EXT_ID, "").orEmpty()
        Log.d(PX_LOG_TAG, "pxDeactivate: icToken $icToken, icId $icId, extId $extId")

        if (icToken == "" || icId == "") {
            Log.d(PX_LOG_TAG, "pxDeactivate: can't deactivate: not activated")
            return
        }

        val data = JSONObject().apply {
            put("ic_token", icToken)
            put("ext_id", extId)
        }
        CoroutineScope(Dispatchers.IO).launch {
            pxMakeRequest("POST", "instances/$icId/deactivate", data, raise = true)

            pxPreferences.edit().apply {
                this.putString(PX_PREFERENCES_IC_ID, "")
                this.putString(PX_PREFERENCES_EXT_ID, "")
            }.apply()
        }
    }

    private fun pxSendClick(msgId: String) {
        if (msgId == "") { return }

        val icId = pxPreferences.getString(PX_PREFERENCES_IC_ID, "").orEmpty()
        Log.d(PX_LOG_TAG, "pxSendClick: icId $icId, msgId $msgId")

        val data = JSONObject().apply {
            put("msg_id", msgId)
            put("event", "clicked")
        }
        CoroutineScope(Dispatchers.IO).launch {
            pxMakeRequest("POST", "instances/$icId/events/notification", data)
        }
    }

    fun pxGetInstanceToken(): String {
        return pxPreferences.getString(PX_PREFERENCES_IC_TOKEN, "").orEmpty()
    }

    fun pxGetExternalId(): String {
        return pxPreferences.getString(PX_PREFERENCES_EXT_ID, "").orEmpty()
    }
    // ***** END PX SDK INTERFACE *****

    // ***** BEGIN PX INTERNALS: DO NOT MODIFY! *****
    private fun pxGetTransportToken() {
        Firebase.messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                pxPreferences.edit().putString(PX_PREFERENCES_TT_TOKEN, token).apply()
                Log.d(PX_LOG_TAG, "pxGetTransportToken: $token")
                CoroutineScope(Dispatchers.IO).launch { pxUpdateAppInfo() }
            }
        }
    }

    private fun pxInitializeIcToken(appId: String) {
        val curAppId = pxPreferences.getString(PX_PREFERENCES_APP_ID, "").orEmpty()
        val newIcToken = UUID.randomUUID().toString()

        val needReinitialize = curAppId != "" && appId != curAppId
        val initialized = curAppId != "" &&
                pxPreferences.getString(PX_PREFERENCES_IC_TOKEN, "").orEmpty() != ""

        if (!initialized || needReinitialize) {
            pxPreferences.edit().apply {
                this.putString(PX_PREFERENCES_IC_TOKEN, newIcToken)
                this.putString(PX_PREFERENCES_IC_ID, "")
                this.putString(PX_PREFERENCES_EXT_ID, "")
                this.putString(PX_PREFERENCES_APP_ID, appId)
            }.apply()
            Log.d(PX_LOG_TAG,
                "pxInitializeIcToken: appId $appId, icToken $newIcToken, " +
                        "reinit $needReinitialize")
        } else {
            Log.d(PX_LOG_TAG,
                "pxInitializeIcToken: already initialized and no need for reinit")
        }
    }

    private suspend fun pxGetInstanceId(extId: String) {
        // create new or get existed instance by ic_token + ext_id
        val icToken = pxPreferences.getString(PX_PREFERENCES_IC_TOKEN, "").orEmpty()
        val json = JSONObject().apply {
            put("ic_token", icToken)
            put("ext_id", extId)
        }

        val responseBody = pxMakeRequest("POST", "instances", json)
        try {
            val id = responseBody.let { JSONObject(it).getString("id") }
            pxPreferences.edit().putString(PX_PREFERENCES_IC_ID, id).apply()
        } catch (e: Exception) {
            Log.d(PX_LOG_TAG, "pxGetInstanceId: exception: $e")
        }
    }

    private fun pxGetDeviceCountry(context: Context): String {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .simCountryIso.uppercase()
        } catch (e: Exception) {
            Log.e(PX_LOG_TAG, e.toString())
            ""
        }
    }

    private suspend fun pxUpdateAppInfo() {
        val icId = pxPreferences.getString(PX_PREFERENCES_IC_ID, "").orEmpty()
        if (icId == "") {
            Log.d(PX_LOG_TAG, "pxUpdateAppInfo: no icId yet, skipping")
            return
        }

        val ttToken = pxPreferences.getString(PX_PREFERENCES_TT_TOKEN, "").orEmpty()
        val extId = pxPreferences.getString(PX_PREFERENCES_EXT_ID, "").orEmpty()
        val deviceCountry = pxGetDeviceCountry(this)
        val deviceLanguage = Locale.getDefault().language
        val deviceTimeZone = TimeZone.getDefault().rawOffset / 1000

        val data = JSONObject().apply {
            put("transport_type", "fcm")
            put("transport_token", ttToken)
            put("platform_type", "android")
            put("platform_name", "android_api_${Build.VERSION.SDK_INT}")
            put("agent_name", "px_kt_droid_plain")
            put("ext_id", extId)
            put("country", deviceCountry)
            put("lang", deviceLanguage)
            put("tz_sec", deviceTimeZone)
            put("tags", JSONObject(PX_TAGS.toMap()))
        }

        pxMakeRequest("PUT", "instances/$icId/info", data)
    }

    suspend fun pxMakeRequest(method: String, urlSuffix: String, data: JSONObject,
                                      raise: Boolean = false): String {
        val appId = pxPreferences.getString(PX_PREFERENCES_APP_ID, "").orEmpty()
        var responseBody = ""
        try {
            val requestBody =
                data.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://core.push.express/api/r/v2/apps/$appId/$urlSuffix")
                .method(method, requestBody)
                .build()

            Log.d(PX_LOG_TAG, "$method /$appId/$urlSuffix request: $data")

            val response = withContext(Dispatchers.IO) { pxOkHttp.newCall(request).execute() }
            responseBody = response.body?.string().orEmpty()
            Log.d(PX_LOG_TAG, "$method /$appId/$urlSuffix response: $responseBody")
        } catch (e: Exception) {
            Log.d(PX_LOG_TAG, "$method /$appId/$urlSuffix exception: $e")
            if (raise) {
                throw e
            }
        }
        return responseBody
    }
    // ***** END PX INTERNALS *****

    suspend fun sendPostback(postback: Postback, extId: String): String {
        val userId = PX_APP_ID.split("-").last()
        var responseBody = ""
        try {
            val request = Request.Builder()
                .url("https://app.push.express/api/postback/?id=$extId&status=${postback.event}_$userId")
                .method("POST", "".toRequestBody())
                .build()
            Log.d(PX_LOG_TAG, "Sending postback at ${request.url}")

            val response = withContext(Dispatchers.IO) { pxOkHttp.newCall(request).execute() }
            responseBody = response.body?.string().orEmpty()
            Log.d(PX_LOG_TAG, "sendPostback response: $responseBody")
        } catch (e: Exception) {
            Log.d(PX_LOG_TAG, e.toString())
        }
        return responseBody
    }

}

fun myBackendAutoLoginFlow(): String {
    // get stored login credentials
    // if no stored creds, show login flow
    // if not signed up, show signup flow and login then
    // return some external user id
    return "uid_12345"
}

@Composable
fun MainScreen(extId: String, mainActivity: MainActivity) {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                UserFlow(extId, mainActivity)
                LogcatMemo(tag = "")
            }
        }

}

@Composable
fun UserFlow(extId: String, mainActivity: MainActivity) {
    var userId by remember { mutableStateOf(extId) }
    var loginEnabled by remember { mutableStateOf(true) }
    var eventStatus by remember { mutableStateOf("Initial state") }
    var loginStatus by remember { mutableStateOf("Logged out") }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 8.dp)) {
            //Text("Your userId (login): ", modifier = Modifier.padding(end = 8.dp))
            TextField(
                value = userId,
                onValueChange = { userId = it },
                modifier = Modifier.weight(1f),
                label = { Text("Your userId (login):") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
        }
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            Button(
                onClick = { CoroutineScope(Dispatchers.Main).launch {
                    mainActivity.sendPostback(Postback.REGISTER, TEST_EXTERNAL_ID)
                } },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Signup/reg")
            }
            Button(
                onClick = { CoroutineScope(Dispatchers.Main).launch {
                    mainActivity.sendPostback(Postback.DEPOSIT, TEST_EXTERNAL_ID)
                } },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Buy/dep")
            }
        }
//        Row(modifier = Modifier.padding(bottom = 8.dp)) {
//            Button(
//                enabled = loginEnabled,
//                onClick =
//                {
//                    // pxActivate(userId)
//                    loginStatus = "Logged in extId '${userId}'"
//                    loginEnabled = false
//                },
//                modifier = Modifier.padding(bottom = 8.dp)
//            ) {
//                Text("Login")
//            }
//            Button(
//                enabled = !loginEnabled,
//                onClick =
//                {
//                    // pxDeactivate()
//                    loginStatus = "Deactivated extId '${userId}', you can login again"
//                    loginEnabled = true
//                },
//                modifier = Modifier.padding(bottom = 8.dp)
//            ) {
//                Text("Log out")
//            }
//        }
        Text(eventStatus)
        Text(loginStatus)
    }
}

@Composable
fun LogcatMemo(tag: String) {
    val logMessages = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(tag) {
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time $tag:* *:S")
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

                bufferedReader.forEachLine {
                    launch {
                        withContext(Dispatchers.Main) {
                            logMessages.add(it)
                            listState.animateScrollToItem(logMessages.size - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    LazyColumn(state = listState) {
        items(logMessages) { message ->
            Text(text = message)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen("user_id_1234", mainActivity = MainActivity())
}
