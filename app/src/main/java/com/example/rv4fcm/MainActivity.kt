package com.example.rv4fcm

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import java.util.UUID
import kotlin.math.pow


const val PUSHEXPRESS_APP_ID: String = "9999-999999"

// App preference filename.
const val PREFERENCE_FILENAME: String = "app_preference"

// Local storage keys.
const val FIREBASE_TOKEN: String = "fb_token"
const val PUSHEXPRESS_ID: String = "pushex_id"
const val IC_TOKEN: String = "icToken"

// Log tag.
const val LOG_APP_INFO: String = "APP INFO"

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var icToken: String
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        icToken = sharedPreferences.getString(IC_TOKEN, "").toString()

        if (icToken.isEmpty()) {
            icToken = UUID.randomUUID().toString()
            with (sharedPreferences.edit()) {
                putString(IC_TOKEN, icToken)
                apply()
            }
        }
        Log.d(LOG_APP_INFO, "CURRENT UUID: $icToken")

        Firebase.messaging.token.addOnCompleteListener {
            if (it.isSuccessful) {
                with (sharedPreferences.edit()){
                    putString(FIREBASE_TOKEN, it.result)
                    apply()
                }
            }
        }
        getAppID()
        updateAppInfo()
        Log.d(LOG_APP_INFO, "firebase token ${sharedPreferences
            .getString(FIREBASE_TOKEN, null)}")
        Log.d(LOG_APP_INFO, "pushexpress id ${sharedPreferences
            .getString(PUSHEXPRESS_ID, null)}")
        Log.d(LOG_APP_INFO, "ic token ${sharedPreferences
            .getString(IC_TOKEN, null)}")
    }
    private fun getAppID() {
        val sharedPreferences = getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        json.put("ic_token", icToken)

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                var request = chain.request()
                    .newBuilder()
                    .post(requestBody)
                    .build()
                var response = chain.proceed(chain.request())
                var tryCount = 0
                while (!response.isSuccessful && tryCount < 10) {
                    tryCount++
                    val sleepTime = (2.0.pow(tryCount.toDouble()) + Random().nextInt(3000 - 1000) + 1000).toLong()
                    Thread.sleep(sleepTime)
                    response.close()

                    response = chain.proceed(chain.request())
                    request = request.newBuilder().build()
                }
                response
            }
            .build()

        val request = Request.Builder()
            .url("https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(LOG_APP_INFO, e.printStackTrace().toString())
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful){
                    val id = responseBody?.let { JSONObject(it).getString("id") }
                    with (sharedPreferences.edit()){
                        putString(PUSHEXPRESS_ID, id)
                        apply()
                    }
                } else {
                    Log.e(LOG_APP_INFO, "failed to get response from ${request.url}")
                }
            }
        })
    }

    private fun getDeviceCountry(context: Context): String {
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.networkCountryIso.orEmpty()
        } catch (e: Exception) {
            Log.e(LOG_APP_INFO, e.toString())
            ""
        }
    }

    private fun updateAppInfo() {
        val sharedPreferences = getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)

        val firebaseToken = sharedPreferences.getString(FIREBASE_TOKEN, null)
        val icID = sharedPreferences.getString(PUSHEXPRESS_ID, null)
        val deviceCountry = getDeviceCountry(this)
        val deviceLanguage = Locale.getDefault().language
        val deviceTimeZone = TimeZone.getDefault().rawOffset / 1000

        val json = JSONObject()
        json.put("transport_type", "fcm")
        json.put("transport_token", firebaseToken)
        json.put("platform_type", "android")
        json.put("lang", deviceLanguage)
        json.put("country", deviceCountry.uppercase())
        json.put("tz_sec", deviceTimeZone)

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances/$icID/info")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful){
                    Log.d(LOG_APP_INFO, "update info response: $responseBody")
                } else {
                    Log.e(LOG_APP_INFO, "failed to get response from ${request.url}")
                }
            }
        })
    }
}