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

import logging
import model

from google.appengine.api import users
from google.appengine.ext import ndb

def AuthUser(user):
  """Validates the passed in user.
 
  Args:
    user An App Engine / endpoints user object.
  Returns:
    The user model entity if the user is authorized, and None otherwise.
  """
  if not user:
    return None

  user_db = model.AuthorizedUser.get_by_id(user.email())

  if not user_db and users.is_current_user_admin():
    # Admins can automatically become users.
    logging.info('Adding a new admin user %s', user.email())
    user_db = model.AuthorizedUser(id=user.email())
    user_db.put()

  return user_db
