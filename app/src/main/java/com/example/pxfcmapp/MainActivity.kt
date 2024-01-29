package com.example.pxfcmapp

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

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat


// ***** BEGIN PX INTERNALS: DO NOT MODIFY! *****
const val PX_PREFERENCES: String = "px_preferences"
const val PX_PREFERENCES_APP_ID: String = "app_id"
const val PX_PREFERENCES_TT_TOKEN: String = "tt_token"
const val PX_PREFERENCES_IC_TOKEN: String = "ic_token"
const val PX_PREFERENCES_IC_ID: String = "ic_id"
const val PX_PREFERENCES_EXT_ID: String = "ext_id"
const val PX_LOG_TAG: String = "px_api"
// ***** END PX INTERNALS *****

// Put your REAL px_app_id here
const val PX_APP_ID: String = "xxxxx-yyy"
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

        pxPreferences = getSharedPreferences("${packageName}_${PX_PREFERENCES}",
            Context.MODE_PRIVATE)

        pxInitialize(PX_APP_ID)
        pxActivate("")                   // make new install with empty extId
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

    private fun pxSetExternalId(extId: String) {
        Log.d(PX_LOG_TAG, "pxSetExternalId: extId $extId")
        pxPreferences.edit().putString(PX_PREFERENCES_EXT_ID, extId).apply()
        CoroutineScope(Dispatchers.IO).launch {
            pxUpdateAppInfo()
        }
    }

    private fun pxDeactivate() {
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

    private fun pxGetInstanceToken(): String {
        return pxPreferences.getString(PX_PREFERENCES_IC_TOKEN, "").orEmpty()
    }

    private fun pxGetExternalId(): String {
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

    private suspend fun pxMakeRequest(method: String, urlSuffix: String, data: JSONObject,
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
}