package com.example.rv4fcm
import android.content.Context
import android.content.SharedPreferences
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
const val LOCAL_STORAGE_PUSHEXPRESS_ID: String = "pushex_id"
const val LOCAL_STORAGE_IC_TOKEN: String = "icToken"
const val LOG_APP_INFO: String = "APP INFO"

val CAMPAIGN_TAGS: Map<String, String> = mapOf(
    "offer" to "google_ads_12321",
    "campaign" to "campaign1",
    "ad_id" to "id1"
)

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var icToken: String
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        icToken = sharedPreferences.getString(LOCAL_STORAGE_IC_TOKEN, "") ?: ""
        initializePushExpressApi()
    }

    private fun initializePushExpressApi() {
        CoroutineScope(Dispatchers.IO).launch {
            initializeIcToken()
            initializeFirebase()
            getPushExpressId()
            updateAppInfo()
        }
        Log.d(LOG_APP_INFO, "firebase token ${sharedPreferences
            .getString(LOCAL_STORAGE_FIREBASE_TOKEN, null)}")
        Log.d(LOG_APP_INFO, "pushexpress id ${sharedPreferences
            .getString(LOCAL_STORAGE_PUSHEXPRESS_ID, null)}")
        Log.d(LOG_APP_INFO, "ic token ${sharedPreferences
            .getString(LOCAL_STORAGE_IC_TOKEN, null)}")
    }

    private suspend fun initializeIcToken() {
        withContext(Dispatchers.IO) {
            if (icToken.isEmpty()) {
                icToken = UUID.randomUUID().toString()
                sharedPreferences.edit().putString(LOCAL_STORAGE_IC_TOKEN, icToken).apply()
            }
        }
    }

    private suspend fun initializeFirebase() {
        Firebase.messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                sharedPreferences.edit().putString(LOCAL_STORAGE_FIREBASE_TOKEN, token).apply()
            }
        }
    }

    private suspend fun getPushExpressId() {
        val json = JSONObject().apply { put("ic_token", icToken) }
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances")
            .post(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        val responseBody = response.body?.string()
        val id = responseBody?.let { JSONObject(it).getString("id") }
        sharedPreferences.edit().putString(LOCAL_STORAGE_PUSHEXPRESS_ID, id).apply()
    }

    private fun getDeviceCountry(context: Context): String {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.networkCountryIso.orEmpty()
        } catch (e: Exception) {
            Log.e(LOG_APP_INFO, e.toString())
            ""
        }
    }

    private suspend fun updateAppInfo() {
        val firebaseToken = sharedPreferences.getString(LOCAL_STORAGE_FIREBASE_TOKEN, null) ?: ""
        val icID = sharedPreferences.getString(LOCAL_STORAGE_PUSHEXPRESS_ID, null) ?: ""
        val deviceCountry = getDeviceCountry(this)
        val deviceLanguage = Locale.getDefault().language
        val deviceTimeZone = TimeZone.getDefault().rawOffset / 1000

        val json = JSONObject().apply {
            put("transport_type", "fcm")
            put("transport_token", firebaseToken)
            put("ext_id", icToken)
            put("platform_type", "android")
            put("lang", deviceLanguage)
            put("country", deviceCountry.uppercase())
            put("tz_sec", deviceTimeZone)
            put("tags", JSONObject(CAMPAIGN_TAGS))
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances/$icID/info")
            .put(requestBody)
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        val responseBody = response.body?.string()
        Log.d(LOG_APP_INFO, "update info response: $responseBody")
    }
}
