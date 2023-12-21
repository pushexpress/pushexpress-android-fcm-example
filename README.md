## Set up a Firebase Cloud Messaging client app on Android
> [!NOTE]
> Everything in this readme has been done using Android Studio Hedgehog | 2023.1.1 Build #AI-231.9392.1.2311.11076708, built on November 9, 2023

### Route 1 (manual)

#### Firebase account setup
> [!NOTE]
> Assuming you already have a google account and firebase account.

> [!NOTE]
> The following covers official video instructions from Firebase youtube channel: [Getting started with Firebase on Android](https://youtu.be/jbHfJpoOzkI)

1. Go to the [Firebase console](https://console.firebase.google.com/).

    - In the center of the project overview page, click the Android icon or Add app to launch the setup workflow
    - Enter your app's package name in the Android package name field (_This field is the only mandatory one, if are not sure how to fill other fields - just don't_.)

    
    <img src="/docs/images/get_started.png" width=50% margin=1rem>

> [!WARNING]
> Make sure to enter the package name that your app is actually using. The package name value is case-sensitive, and it cannot be changed for this Firebase Android app after it's registered with your Firebase project.

<img src="/docs/images/android_app_id.png" width=50%>
> [!TIP]
> Find your app's package name in your module (app-level) Gradle file, usually app/build.gradle (example package name: com.yourcompany.yourproject)



#### Installing dependecies
Filename: `build.gradle.kts` (Module :app). This is your module (app-level).
```
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
```
plugins {
    // ...
    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.0" apply false
}
```