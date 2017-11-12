# Copyright 2017 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from google.appengine.ext import ndb

class RelayDevice(ndb.Model):
  """Info about relaying tablet devices.
  
  The key name shall be the unique, immutable device ID,
  concatenated with the owner email.

  The parent of each RelayDevice shall be a AuthorizedUser key of the owner
  of the device so that lookups of owned devices for a user will be strongly
  consistent.
  """
  # The curent Firebase Cloud Messaging ID for the device -- may change over
  # time.
  fcm_id = ndb.StringProperty()

  # If present, all messages up to this date time have been aked.
  acked_until = ndb.DateTimeProperty()


class AuthorizedUser(ndb.Model):
  """Users that are authorized to use app.
  
  The key name shall be the email address of the user.

  The may_post_to property maintains the many-to-many relationship between
  users and devices indicating to which devices a user may post messages.
  """
  # A list of keys to devices to which this user may post.
  may_post_to = ndb.KeyProperty(kind=RelayDevice, repeated=True)


class Message(ndb.Model):
  """A message sent to the relay device.
  
  The parent of each Message shall be a RelayDevice key so that lookups of
  messages for a device will be strongly consistent.
  """
  # The user that sent the message.
  sender = ndb.KeyProperty(kind=AuthorizedUser)

  # The textual content of the message, limited to 140 characters.
  body = ndb.StringProperty()

  # The date and time the message was posted.
  date = ndb.DateTimeProperty(auto_now_add=True)

