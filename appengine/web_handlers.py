#!/usr/bin/env python

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
import datetime
import logging
import model
import os
import random
import urllib

from google.appengine.api import users
from google.appengine.api import urlfetch
from google.appengine.ext import ndb

import jinja2
import webapp2

JINJA_ENVIRONMENT = jinja2.Environment(
    loader=jinja2.FileSystemLoader(os.path.dirname(__file__)),
    extensions=['jinja2.ext.autoescape'],
    autoescape=True)

JSON_REQUEST='{"to": "%s","data": {"score":"123"}}'

# SETUP_TODO: Get the API key from the Firebase console.
API_KEY = 'firebase-cloud-messaging-id-here'

def SendPushNotification(client_id):
  res = urlfetch.fetch(url='https://fcm.googleapis.com/fcm/send', 
                       payload=JSON_REQUEST % client_id,
                       method=urlfetch.POST,
                       headers={'Authorization': 'key=%s' % API_KEY,
                                'Content-Type': 'application/json'})
  logging.info('Urlfetch return %s %s %s', res.content, res.status_code,
               res.headers)

def HandleAuthFailure(request_handler):
  request_handler.redirect('/login')
  
def GetLoginLinks(request_handler):
  user = users.get_current_user()
  if user:
    url = users.create_logout_url(request_handler.request.uri)
    url_linktext = 'Logout'
  else:
    url = users.create_login_url(request_handler.request.uri)
    url_linktext = 'Login'
  return user, url, url_linktext


class MainPage(webapp2.RequestHandler):

  def get(self):
    curr_user_db = auth.AuthUser(users.get_current_user())
    if not curr_user_db:
      HandleAuthFailure(self)
      return
    owned_devices = model.RelayDevice.query(ancestor=curr_user_db.key).fetch()
    may_post_to_devices = ndb.get_multi(curr_user_db.may_post_to)
    user, url, url_linktext = GetLoginLinks(self)
    template_values = {
      'user': user,
      'url': url,
      'url_linktext': url_linktext,
      'owned_devices': owned_devices,
      'may_post_to_devices': may_post_to_devices,
    }

    template = JINJA_ENVIRONMENT.get_template('index.html')
    self.response.write(template.render(template_values))


class RegisterUsers(webapp2.RequestHandler):
  def get(self):
    if not auth.AuthUser(users.get_current_user()):
      HandleAuthFailure(self)
      return
    user, url, url_linktext = GetLoginLinks(self)
    template_values = {
        'user': user,
        'url': url,
        'url_linktext': url_linktext,
        'authorized_users': model.AuthorizedUser.query().fetch(),
    }
    template = JINJA_ENVIRONMENT.get_template('register_users.html')
    self.response.write(template.render(template_values))

  def post(self):
    if not auth.AuthUser(users.get_current_user()):
      HandleAuthFailure(self)
      return
    email = self.request.get('email_to_authorize')
    # TODO: validate that this is a real email address
    if email:
    	user_db = model.AuthorizedUser(id=email)
    	user_db.put()
    self.redirect('/register_users')


class User(webapp2.RequestHandler):
  def get(self, url_user=''):
    curr_user_db = auth.AuthUser(users.get_current_user())
    if not curr_user_db:
      HandleAuthFailure(self)
      return
    user, url, url_linktext = GetLoginLinks(self)
    url_user_db = model.AuthorizedUser.get_by_id(url_user)
    # TODO: bad url_user
    curr_user_owned_devices = (model.RelayDevice
                               .query(ancestor=curr_user_db.key)
                               .fetch())
    for device in curr_user_owned_devices:
      device.url_user_may_post_to = device.key in url_user_db.may_post_to
    template_values = {
        'user': user,
        'url': url,
        'url_linktext': url_linktext,
        'url_user': url_user,
        'curr_user_owned_devices': curr_user_owned_devices,
    }
    logging.info('%s %s' % (url_user, url_user_db))
    template = JINJA_ENVIRONMENT.get_template('user.html')
    self.response.write(template.render(template_values))

  def post(self, url_user=''):
    curr_user_db = auth.AuthUser(users.get_current_user())
    if not curr_user_db:
      HandleAuthFailure(self)
      return
    new_allowed_device_ids = self.request.get('device_checkbox',
                                              allow_multiple=True)
    curr_user_owned_devices = (model.RelayDevice
                               .query(ancestor=curr_user_db.key)
                               .fetch())
    
    url_user_db = model.AuthorizedUser.get_by_id(url_user)
    if not url_user_db:
        request_handler.response.write(
          'Bad request -- url_user %s not found.' % url_user)
        request_handler.response.set_status(400)
        return
        
    url_user_db.may_post_to = []
    for device_id in new_allowed_device_ids:
      try:
        device = next(device for device in curr_user_owned_devices
                      if device.key.id() == device_id)
      except StopIteration:
        request_handler.response.write(
          'Bad request -- device_id %s not found.' % device_id)
        request_handler.response.set_status(400)
        return
      url_user_db.may_post_to.append(device.key)
    url_user_db.put()
    self.redirect('/user/%s' % url_user)


class Device(webapp2.RequestHandler):
  def get(self, owner_email='', device_id=''):
    # TODO: restructure to have 2 URL parameters: owner first, then ID.
    # Construct the parent, child key from this. We can't do get_by_id without
    # the parent key.
    logging.info('%s %s', owner_email, device_id)
    curr_user_db = auth.AuthUser(users.get_current_user())
    if not curr_user_db:
      HandleAuthFailure(self)
      return
    device_db = (model.RelayDevice
                 .get_by_id(device_id,
                            parent=ndb.Key(model.AuthorizedUser,
                                           owner_email)))
    if (device_db.key not in curr_user_db.may_post_to
        and device_db.key.parent() != curr_user_db.key):
      HandleAuthFailure(self)
      return      
    messages = (model.Message
                .query(ancestor=device_db.key)
                .order(-model.Message.date)
                .fetch())
    
    sender_keys = []
    for message in messages:
      sender_keys.append(message.sender)
    senders_db = ndb.get_multi(sender_keys)
    
    for message, sender_db in zip(messages, senders_db):
      message.sender_email = sender_db.key.id()
      message.acknowledged = (device_db.acked_until
                              and device_db.acked_until >= message.date)
      
    # TODO: More robust timezone support once pytz lands.
    # local_tz = pytz.timezone('America/New_York')
    # message.date.replace(tzinfo=pytz.utc).astimezone(local_tz).replace(tzinfo=None)

    authorized_users_this_device = (
        model.AuthorizedUser
        .query()
        .filter(model.AuthorizedUser.may_post_to == device_db.key)
        .fetch())
    
    logging.info('authorized_users_this_device %s', authorized_users_this_device)
    
    user, url, url_linktext = GetLoginLinks(self)
    template_values = {
        'user': user,
        'url': url,
        'url_linktext': url_linktext,
        'authorized_users_this_device': authorized_users_this_device,
        'messages': messages,
        'owner_email': owner_email,
        'device_id': device_id,
    }
    template = JINJA_ENVIRONMENT.get_template('device.html')
    self.response.write(template.render(template_values))

  def post(self, owner_email='', device_id=''):
    curr_user_db = auth.AuthUser(users.get_current_user())
    if not curr_user_db:
      HandleAuthFailure(self)
      return
    device_db = (model.RelayDevice
                 .get_by_id(device_id,
                            parent=ndb.Key(model.AuthorizedUser,
                                           owner_email)))
    if (device_db.key not in curr_user_db.may_post_to
        and device_db.key.parent() != curr_user_db.key):
      HandleAuthFailure(self)
      return
    message = model.Message(parent=device_db.key)
    message.body = self.request.get('message_text')
    message.sender = curr_user_db.key
    message.put()
    if device_db.fcm_id:
      SendPushNotification(device_db.fcm_id)
    self.redirect('/device/%s/%s' % (owner_email, device_id))


class Login(webapp2.RequestHandler):
  def get(self):
    curr_user_db = auth.AuthUser(users.get_current_user())
    if curr_user_db:
      self.redirect('/')
      return
    user, url, url_linktext = GetLoginLinks(self)
    template_values = {
        'user': user,
        'url': url,
        'url_linktext': url_linktext,
    }
    template = JINJA_ENVIRONMENT.get_template('login.html')
    self.response.write(template.render(template_values))


app = webapp2.WSGIApplication([
    ('/', MainPage),
    ('/register_users', RegisterUsers),
    ('/user/(.*)', User),
    ('/device/(.*)/(.*)', Device),
    ('/login', Login)
])
