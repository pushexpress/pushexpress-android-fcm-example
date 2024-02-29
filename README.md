> [!NOTE]
> The contents of this readme were created using Android Studio Hedgehog | 2023.1.1 Build #AI-231.9392.1.2311.11076708, compiled on November 9, 2023.

[Set up a Firebase Cloud Messaging client app on Android](#set-up-a-firebase-cloud-messaging-client-app-on-android) \
    - [Option 1: Add Firebase using the Firebase console](#option-1-add-firebase-using-the-firebase-console-mostly-manual) \
    - [Option 2: Add Firebase using the Firebase Assistant](#option-2-add-firebase-using-the-firebase-assistant-mostly-automated) 

[Handling an api requests inside the application itself](#handling-an-api-requests-inside-the-application-itself) \
    - [Set up manifest configuration file](#set-up-manifest-configuration-file) \
    - [Set up Main Activity class](#set-up-main-activity-class) \
    - [Set up kotlin class to handle firebase messsaging](#set-up-kotlin-class-to-handle-firebase-messsaging) \
    - [Install OKHTTP library to manage http requests](#install-okhttp-library-to-manage-http-requests)

[Some key notes about implementation](#some-key-notes-about-implementation)

## Set up a Firebase Cloud Messaging client app on Android
### Option 1: Add Firebase using the Firebase console (mostly manual).

> [!NOTE]
> Assuming you already have a google account and firebase account. The following covers official video instructions from Firebase youtube channel: [Getting started with Firebase on Android](https://youtu.be/jbHfJpoOzkI)

1. Go to the [Firebase console](https://console.firebase.google.com/).

    - In the center of the project overview page, click the Android icon or Add app to launch the setup workflow
    <img src="/docs/images/get_started.png" width=50%>

    - Enter your app's package name in the Android package name field (_This field is the only mandatory one, if you are not sure how to fill other fields - just don't_)
    <img src="/docs/images/app_creation_menu.png" width=50%>

> [!WARNING]
> Make sure to enter the package name that your app is actually using. The package name value is case-sensitive, and it cannot be changed for this Firebase Android app after it's registered with your Firebase project.

> [!TIP]
> Find your app's package name in your module (app-level) Gradle file, usually app/build.gradle (example package name: com.yourcompany.yourproject)

<img src="/docs/images/android_app_id.png" width=50%>

2. Click **Register app**.

3. Download and add a Firebase configuration file.
    - Download and then add the Firebase Android configuration file `(google-services.json)` to your app
    - Move your config file into the **module (app-level)** root directory of your app

> [!TIP]
>  Your typical path should look like this `.../AndroidStudioProjects/your_app_name/app/google-services.json`

4. Install dependencies

    Filename: `build.gradle.kts` (Module :app). This is your module (app-level).
    ```kotlin
    plugins {
        // ...
        // Add the Google services Gradle plugin
        id("com.google.gms.google-services")
    }

    dependencies {
    // ...
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // When using the BoM, you don't specify versions in Firebase library dependencies
    // If you want analytics enabled: Add the dependency for the Firebase SDK for Google Analytics
    implementation("com.google.firebase:firebase-analytics")
    }
    ```

    Filename: `build.gradle.kts` (Project \<your app name>\). This is your root-level (project-level).
    ```kotlin
    plugins {
        // ...
        // Add the dependency for the Google services Gradle plugin
        id("com.google.gms.google-services") version "4.4.0" apply false
    }
    ```

### Option 2: Add Firebase using the Firebase Assistant (mostly automated).

> [!TIP]
> As per Firebase documentation: _The Firebase Assistant registers your app with a Firebase project and adds the necessary Firebase files, plugins, and dependencies to your Android project — all from within Android Studio!_

1. Open your Android project in Android Studio, then make sure that you're using the latest versions of Android Studio and the Firebase Assistant:

    Windows / Linux: \
    **Help** > **Check for updates**

    macOS: \
    **Android Studio** > **Check for updates**

2. Open the Firebase Assistant: **Tools** > **Firebase**.

    <img src="/docs/images/firebase_assistant.png" width=50%>

3. In a docked menu, choose _Cloud Messaging_ -> _Set up Firebase Cloud Messaging_
    <img src="/docs/images/firebase_assistant_menu.png" width=50%>
4. Follow the instructions up until second article.

If everything went as it should've, you can peek into logcat and confirm that Firebase has been initialized.
    <img src="/docs/images/firebase_init.jpg">
    
## Handling an api requests inside the application itself.
### Set up manifest configuration file.
`AndroidManifest.xml`
```xml
<manifest>
    // Declaring the permission (for API level 33 and higher).
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

    <application>

        <activity>
            ...
        </activity>

        // Declaring a service for handling Firebase Cloud Messaging (FCM) events.
        <service
            android:name=".MyFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT"/>
            </intent-filter>
        </service>

    </application>

</manifest>
```
### Set up Main Activity class.
`MainActivity.kt`
```kotlin
// Insert your push express application id. Format it as follows.
const val PUSHEXPRESS_APP_ID: String = "1234-567890"

// Rename a local storage file if needed.
const val PREFERENCE_FILENAME: String = "app_preference"

// Rename a local storage keys for important or persistent data.
const val LOCAL_STORAGE_FIREBASE_TOKEN: String = "fb_token" // Key for your firebase token.
const val LOCAL_STORAGE_PUSHEXPRESS_ID: String = "pushex_id" // Key for your pushexpress id in our database?
const val LOCAL_STORAGE_IC_TOKEN: String = "icToken" // Key for specific application's UUID. Generated inside onCreate function.

// Rename log tag for your application to easily navigate LogCat.
const val LOG_APP_INFO: String = "APP INFO"

// Rename ad campaign tags or add more if needed. As per api documentation, tag names are predefined, you can choose from "ad_id", "offer" and "webmaster".
val CAMPAIGN_TAGS: Map<String, String> = mapOf(
    "offer" to "google_ads_12321",
    "campaign" to "campaign1",
    "ad_id" to "id1"
)
```
You will need to add a logic from `MainActivity.kt` to your `onCreate()` method from our [MainActivity.kt](https://github.com/pushexpress/pushexpress-android-fcm-example/blob/feat/app/src/main/java/com/example/pxfcmapp/MainActivity.kt).

### Set up kotlin class to handle firebase messsaging.
`MyFirebaseMessagingService.kt`
```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val messageId = remoteMessage.messageId ?: ""
        sendDeliveryStatusToAPI(messageId)
    }

    fun sendDeliveryStatusToAPI(messageId: String) {
        val client = OkHttpClient()
        val sharedPreferences = getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
        val icID = sharedPreferences.getString(LOCAL_STORAGE_PUSHEXPRESS_ID, null) ?: ""
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
```
> [!TIP]
> Do not forget to add a package name at the top of your file.

### Install [OKHTTP](https://square.github.io/okhttp/) library to manage http requests.
`build.gradle.kts` (app-level) 

```gradle
implementation("com.squareup.okhttp3:okhttp:4.10.0")
```
## Some key notes about implementation.
1. This script only handles notifications in a foreground, since you need to implement your own android background logic to register user's clicks, redirects etc.
2. You might need to adjust some things on your own, depending on your application's version and build.
3. If you want an easy-peasy solution - use our ready-to-go [SDK](https://github.com/pushexpress/pushexpress-android-sdk/blob/main/docs/UseSDKInYourProject.md).
