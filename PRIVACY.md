# Privacy Policy

The PiNotify web interface and Android app require login with a Google account.
Only users authorized in the AppEngine backend by other authorized users may
access the backend. Google accounts that are AppEngine administrators for the
backend deployment are not subject to this restriction.

AppEngine backend administrators have access to *all* information for that
backend deployment (users, messages, logs, etc.)

Furthermore, users may only post to and view messages of devices to which they
are authorized to post. Backend administrators can force authorize themselves
for any device. Otherwise, device owners choose whom to authorize to post to
(and view messages of) their device(s).

The backend stores information about users, devices, and messages. The following
is stored for each:

Users:
 - Google account email address -- this serves as the user ID.
 - Devices to which this user is allowed to post.

Devices:
 - The owner Google account email address.
 - The Android ID of the device -- this, along with the owner email, serves as
   a unique ID for the device.
 - The current Google Firebase Cloud Messaging ID of the device.
 - The timestamp of the most recent message acknowledged by the user of the
   device.

Messages:
 - The sender Google email address.
 - The body of the message.
 - The date and time when the message was posted.

The AppEngine web UI is used for composing and viewing lists of messages, and
the Android / Raspberry PI interface is for viewing and acknowleding individual
messages.

AppEngine also collects logs for each request to the backend. See
https://www.google.com/policies/privacy/ and
https://www.google.com/cloud/security/privacy/ for more details about Google
Cloud privacy policies. Google Firebase is also used for sending push
notifications. See the Google Firebase privacy policy at
https://www.firebase.com/terms/privacy-policy.html.
