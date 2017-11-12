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

import auth
import calendar
import datetime
import endpoints
import logging
import model
from protorpc import message_types
from protorpc import messages
from protorpc import remote

# TODO: config
# SETUP_TODO: Get these from the Google Cloud Console API Credentials.
ANDROID_CLIENT_ID = 'android-client-id-here'
WEB_CLIENT_ID = 'web-client-id-here'

class DeviceRequest(messages.Message):
  """A status request from the device -- the message also bears device info.

  If the device making the request is unknown, it is registered, along with
  its fcm_id (of course, assuming the user making the request passed
  authentication). The device will henceforth receive push notifications
  when new messages arrive.

  Devices should periodically send this request for health checking --
  the server takes note of the time of the last request, and it also
  notes the response latency from FCM message to device request.
  """
  # The unique immutable ID of this device.
  device_id = messages.StringField(1) 

  # The Firebase Cloud Messaging ID of this device. 
  fcm_id = messages.StringField(2)

  # The UTC timestamp of the last message acknowledged by this device. Set to
  # None if the no message acknowledgements have been made since app
  # installation.
  acked_until = messages.IntegerField(3)

  # The ID of the push notification that caused the device to send this
  # request. If this request wasn't promoted by a push notification, this
  # is left blank. 
  caused_by_push_id = messages.IntegerField(4)


class DeviceResponse(messages.Message):
  """A response to the device

  Contains the least recent unacknowledged message.

  All fields will be None if there are no unacknowledged messages on the
  server.
  """
  # Contains the textual content of the message.
  message = messages.StringField(1)

  # Contains the name of the sender of the message.
  sender = messages.StringField(2)

  # Contains the timestamp the message was sent.
  date = messages.IntegerField(3)


@endpoints.api(name='pinotify_api', version='v1',
               allowed_client_ids=[endpoints.API_EXPLORER_CLIENT_ID, ANDROID_CLIENT_ID,
                                   WEB_CLIENT_ID], audiences=[WEB_CLIENT_ID])
class PiNotifyApi(remote.Service):

  @endpoints.method(
    DeviceRequest,
    DeviceResponse,
    path='device_request',
    http_method='POST',
    name='pynotify_api.device_request')
  def device_request(self, request):
    """Upserts a device, then returns the least recent unacknowledged message."""
    logging.info('User %s', endpoints.get_current_user())
    user_model = auth.AuthUser(endpoints.get_current_user())
    if not user_model:
      raise endpoints.ForbiddenException('Unauthorized client device')

    # Get the device and upsert its info if needed
    device = model.RelayDevice.get_or_insert('%s%s' % (request.device_id,
                                                       user_model.key.id()),
                                             parent=user_model.key)
    modified = False
    if device.fcm_id != request.fcm_id:
      device.fcm_id = request.fcm_id
      modified = True
    acked_until_utc = datetime.datetime.utcfromtimestamp(request.acked_until)
    if device.acked_until != acked_until_utc:
      device.acked_until = acked_until_utc
      modified = True
    if modified:
      device.put()

    # Get the oldest unacked message on the device and return
    oldest_unacked = (model.Message
                     .query(ancestor=device.key)
                     .filter(model.Message.date > acked_until_utc)
                     .order(model.Message.date)
                     .get())
    response = DeviceResponse()
    if oldest_unacked:
      response.message = oldest_unacked.body
      response.sender = oldest_unacked.sender.get().key.id()
      # We add one since the timegm() function always rounds down any
      # sub-seconds. This guarantees that the returned time is >= the message
      # time in the datastore.
      response.date = calendar.timegm(oldest_unacked.date.timetuple()) + 1
    return response

api = endpoints.api_server([PiNotifyApi])

