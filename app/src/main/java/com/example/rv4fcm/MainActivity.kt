package com.example.rv4fcm

import android.os.Bundle
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
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.UUID


const val PUSHEXPRESS_APP_ID: String = "9999-999999"

// Local storage keys.
const val FIREBASE_TOKEN: String = "fb_token"
const val PUSHEXPRESS_ID: String = "pushex_id"
const val IC_TOKEN: String = "icToken"

// Log level.
const val LOG_APP_INFO: String = "APP INFO"
const val LOG_LOCAL_STORAGE: String = "LOCAL STORAGE"
const val LOG_TESTING: String = "TESTING"

// Endpoints.
const val PUSHEXPRESS_BASE: String = "https://core.push.express/api/r/v2/apps"
const val PUSHEXPRESS_INSTANCES: String = "$PUSHEXPRESS_BASE/$PUSHEXPRESS_APP_ID/instances"

// TBA.
val DEVICE_LANGUAGE = Locale.getDefault().language

class MainActivity : ComponentActivity() {
    // 1.1. Generate some local-persistent installation-token
    private var icToken = UUID.randomUUID().toString()
    var localStorageManager = LocalStorageManager(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localStorageManager.clearLocalStorage()
        // Save ic_id to local storage.
        localStorageManager.saveItem(IC_TOKEN, icToken)
        // Get FireBase token and save it to local storage. Key: fb_token.
        getAndSaveFireBaseToken()
        // Get IC ID and save it to local storage. Key: pushex_id.
        getAndSavePushExID()
        // Update app instance info
        updateAppInstanceInfo()
    }
    private fun getAndSaveFireBaseToken(){
        Firebase.messaging.token.addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d(LOG_APP_INFO,  "token=${it.result}")
                // Save fb token to a local storage.
                localStorageManager.saveItem(FIREBASE_TOKEN, it.result)
            }
        }
    }
    private fun getAndSavePushExID(){
        val url = "https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances"
        val json = """
    {
        "ic_token": "$icToken"
    }
""".trimIndent()
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RESPONSE ERROR", e.printStackTrace().toString())
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(LOG_APP_INFO, responseBody!!)
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val id = jsonObject.getString("id")
                        localStorageManager.saveItem(PUSHEXPRESS_ID, id)
                        localStorageManager.getItem(PUSHEXPRESS_ID)
                            ?.let { Log.d("PUSHEXPRESSID", it) }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }
    private fun updateAppInstanceInfo(){
        var icID = localStorageManager.getItem(PUSHEXPRESS_ID)
        val url = "https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances/$icID/info"
        Log.d("INSTANCE URL", url)
        val json = """
    {
        "transport_type": "fcm",
        "transport_token": "$FIREBASE_TOKEN",
    }
""".trimIndent()
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .put(body)
            .build()
        val client = OkHttpClient()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RESPONSE ERROR", e.printStackTrace().toString())
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(LOG_APP_INFO + "ASDADS", responseBody!!)
                } else {
                    response.body?.let { Log.d("TAHT DOESNT SEEM RIGHT", it.string()) }
                }
            }
        })
        Log.d("REQUEST BODY", request.body.toString())
    }


}