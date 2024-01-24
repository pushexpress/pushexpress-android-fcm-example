package com.example.pxfcmapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

const val PUSHEXPRESS_APP_ID: String = "9999-999999"
const val PREFERENCE_FILENAME: String = "app_preference"
const val LOCAL_STORAGE_FIREBASE_TOKEN: String = "fb_token"
const val LOCAL_STORAGE_PX_IC_ID: String = "pushex_id"
const val LOCAL_STORAGE_PX_IC_TOKEN: String = "icToken"
const val LOG_APP_INFO: String = "APP INFO"

var CAMPAIGN_TAGS: MutableMap<String, String> = mutableMapOf(
    "audiences" to "",
    "ad_id" to "",
    "webmaster" to "",
)

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CAMPAIGN_TAGS["audiences"] = "google_ads_offer555"
        // Warning: it is not easy to get advertising id properly
        // You don't need it in most cases
        // Contact our support otherwise
        // CAMPAIGN_TAGS["ad_id"] = getAdvId()

        sharedPreferences = getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        pushExpressInitialize()

        if (intent.hasExtra("px.msg_id")) {
            val pxMsgId = intent.getStringExtra("px.msg_id")
            Log.d(LOG_APP_INFO, "px.msg_id: $pxMsgId")
        }
    }

    private fun pushExpressInitialize() {
        CoroutineScope(Dispatchers.IO).launch {
            initializeFirebase()
        }
        CoroutineScope(Dispatchers.IO).launch {
            pushExpressInitializeIcToken()
            pushExpressGetInstanceId()
            pushExpressUpdateAppInfo()
        }
        Log.d(LOG_APP_INFO, "firebase_token ${sharedPreferences
            .getString(LOCAL_STORAGE_FIREBASE_TOKEN, null)}")
        Log.d(LOG_APP_INFO, "ic_id ${sharedPreferences
            .getString(LOCAL_STORAGE_PX_IC_ID, null)}")
        Log.d(LOG_APP_INFO, "ic_token ${sharedPreferences
            .getString(LOCAL_STORAGE_PX_IC_TOKEN, null)}")
    }

    private suspend fun pushExpressInitializeIcToken() {
        withContext(Dispatchers.IO) {
            if (sharedPreferences.getString(LOCAL_STORAGE_PX_IC_TOKEN, null) == null) {
                val icToken = UUID.randomUUID().toString()
                sharedPreferences.edit().putString(LOCAL_STORAGE_PX_IC_TOKEN, icToken).apply()
                Log.d(LOG_APP_INFO, "initialized new ic_token: $icToken")
            }
        }
    }

    private fun initializeFirebase() {
        Firebase.messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                sharedPreferences.edit().putString(LOCAL_STORAGE_FIREBASE_TOKEN, token).apply()
                Log.d(LOG_APP_INFO, "got fcm_token: $token")
            }
        }
    }

    private suspend fun pushExpressGetInstanceId() {
        sharedPreferences.getString(LOCAL_STORAGE_PX_IC_ID, null)?.also {
            Log.d(LOG_APP_INFO, "already has ic_id '$it', skipping new instance request")
            return
        }

        val icToken = sharedPreferences.getString(LOCAL_STORAGE_PX_IC_TOKEN, "")
        val json = JSONObject().apply { put("ic_token", icToken) }
        try {
            val requestBody =
                json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances")
                .post(requestBody)
                .build()

            Log.d(
                LOG_APP_INFO, "get_ic_id request: $json"
            )

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val responseBody = response.body?.string()
            Log.d(LOG_APP_INFO, "get_ic_id response: $responseBody")

            val id = responseBody?.let { JSONObject(it).getString("id") }
            sharedPreferences.edit().putString(LOCAL_STORAGE_PX_IC_ID, id).apply()
        } catch (e: Exception) {
            Log.d(LOG_APP_INFO, "get_ic_id request exception: $e")
        }
    }

    private fun getDeviceCountry(context: Context): String {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .simCountryIso.uppercase()
        } catch (e: Exception) {
            Log.e(LOG_APP_INFO, e.toString())
            ""
        }
    }

    private suspend fun pushExpressUpdateAppInfo() {
        val firebaseToken = sharedPreferences.getString(LOCAL_STORAGE_FIREBASE_TOKEN, "")
        val icID = sharedPreferences.getString(LOCAL_STORAGE_PX_IC_ID, "")
        val icToken = sharedPreferences.getString(LOCAL_STORAGE_PX_IC_TOKEN, "")
        val deviceCountry = getDeviceCountry(this)
        val deviceLanguage = Locale.getDefault().language
        val deviceTimeZone = TimeZone.getDefault().rawOffset / 1000

        val json = JSONObject().apply {
            put("transport_type", "fcm")
            put("transport_token", firebaseToken)
            put("platform_type", "android")
            put("platform_name", "android_api_${Build.VERSION.SDK_INT}")
            put("agent_name", "px_kt_droid_plain")
            put("ext_id", icToken)
            put("country", deviceCountry)
            put("lang", deviceLanguage)
            put("tz_sec", deviceTimeZone)
            put("tags", JSONObject(CAMPAIGN_TAGS.toMap()))
        }

        try {
            val requestBody =
                json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances/$icID/info")
                .put(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val responseBody = response.body?.string()
            Log.d(LOG_APP_INFO, "update info response: $responseBody")
        } catch (e: Exception) {
            Log.d(LOG_APP_INFO, "update info request: exception: $e")
        }
    }
}
