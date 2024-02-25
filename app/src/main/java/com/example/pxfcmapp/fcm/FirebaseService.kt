package com.example.pxfcmapp.fcm

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pxfcmapp.EXTRA_PX_LINK
import com.example.pxfcmapp.EXTRA_PX_MSG_ID
import com.example.pxfcmapp.INTENT_ACTION_CLICK
import com.example.pxfcmapp.MainActivity
import com.example.pxfcmapp.NOTIFICATION_CHANNEL_ID
import com.example.pxfcmapp.NOTIFICATION_CHANNEL_NAME
import com.example.pxfcmapp.PX_BODY_KEY
import com.example.pxfcmapp.PX_LINK_KEY
import com.example.pxfcmapp.PX_LOG_TAG
import com.example.pxfcmapp.PX_MSG_ID_KEY
import com.example.pxfcmapp.PX_TITLE_KEY
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random


var NOTIFICATION_ID = Random.nextInt()

class FirebaseService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Check if message contains a notification payload.
        if (remoteMessage.data != null) {
            Log.d(PX_LOG_TAG, "Message Notification Body: " + remoteMessage.data)
            sendNotification(remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(PX_LOG_TAG, "FCM Token $token")
    }

    // gotta change arguments from title and body -> remoteMessage class
    // or map<str str> as Ilya did.
    private fun sendNotification(data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java)
        intent.action = INTENT_ACTION_CLICK
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0 /* Request code */, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val channelId = NOTIFICATION_CHANNEL_ID
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // *******************************************
        val msgID = data[PX_MSG_ID_KEY]
        data[PX_MSG_ID_KEY]?.let { intent.putExtra(EXTRA_PX_MSG_ID, it) }
        data[PX_LINK_KEY]?.let { intent.putExtra(EXTRA_PX_LINK, it) }
        Log.d(PX_LOG_TAG, msgID.toString())
        // *******************************************
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.sym_def_app_icon)
                .setContentTitle(data[PX_TITLE_KEY])
                .setContentText(data[PX_BODY_KEY])
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(
            NOTIFICATION_ID /* ID of notification */,
            notificationBuilder.build()
        )
    }
}