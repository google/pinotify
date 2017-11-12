# PiNotify project

PiNotify seeks to provide a way to notify and send short (140 character) messages via a tablet
display, along with physical blinking lights and a physical button.

The flashing lights will blink until the message is acknowledged.

See PiNotify Design.py for more details about the project design.

This is not an official Google product.

# Setup

The project consists of 3 apps that work in concert: a backend and web UI that runs on Google
AppEngine, an Android app that runs on the tablet, and a Raspberry Pi app that controls the lights
and physical button based on Bluetooth commands from the tablet.

Before the AppEngine and Android apps can be built and run, some setup is required. Most of this
setup involves creating keys for Firebase Push Notifications and Google Cloud Endpoints, which are
used to allow the Android app to communicate with the backend.

Comments marked in the code with SETUP_TODO are things that need to be done to build, run, and
deploy.

Some of this setup can probably be simplified in the future.

Setup steps:
1. Create a Google Cloud Platform project and enable AppEngine on https://console.cloud.google.com.
1. `cd appengine`
1. `mkdir lib`
1. `pip install -t lib google-endpoints --ignore-installed`
1. Follow the instructions at https://firebase.google.com/docs/cloud-messaging/android/first-message
   to setup Firebase Cloud Messaging and add it to the Android app. This should replace the
   placeholder file `app/google-services.json`. You can link your Firebase project to your Google
   Cloud Platform project. 
1. Follow
   https://cloud.google.com/endpoints/docs/frameworks/legacy/v1/python/getstarted/backend/setup to
   setup Google Cloud Endpoints. 
1. In `app/src/main/java/com/pinotify/BackendService.java`, enter the Cloud Endpoints audience.
1. Also in `app/src/main/java/com/pinotify/BackendService.java`, enter the URL of the backend
   service. This can either be your local development server on the same WiFi, or your deployed
   backend URL.
1. In `app/src/main/java/com/pinotify/RPiBluetoothConnection.java`, hard-code the Bluetooth address
   of your Raspberry Pi. 
1. In `appengine/api.py`, enter the Android and web client IDs from your project's Cloud Console
   credentials section.
1. Follow the instructions on
   https://cloud.google.com/endpoints/docs/frameworks/legacy/v1/java/generate-client-libraries-android
   to create a client library for Android. Place it in a lib/ subdirectory of
   app/.
