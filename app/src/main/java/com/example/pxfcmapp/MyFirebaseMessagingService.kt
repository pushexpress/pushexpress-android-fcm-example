package com.example.pxfcmapp

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val messageId = remoteMessage.data["px.msg_id"] ?: ""
        sendDeliveryStatusToAPI(messageId)
    }

    private fun sendDeliveryStatusToAPI(messageId: String) {
        val client = OkHttpClient()
        val sharedPreferences = getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        val icID = sharedPreferences.getString(LOCAL_STORAGE_PX_IC_ID, null) ?: ""
        val url = "https://core.push.express/api/r/v2/apps/$PUSHEXPRESS_APP_ID/instances/$icID/events/notification"

        val json = JSONObject().apply {
            put("msg_id", messageId)
            put("event", "delivered")
        }
        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(LOG_APP_INFO, "Delivery status sent to the API")
            }
        })
    }
}