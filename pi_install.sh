#!/bin/bash

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

# This script sets the Raspberry Pi script to run on boot using systemd.
#
# Make sure you do this pre-setup to get Python Bluetooth RFCOMM support:
# sudo apt install bluetooth bluez python-bluez
# Edit /lib/systemd/system/bluetooth.service and add '-C' after 'bluetoothd'.
# sudo reboot
# sudo sdptool add SP

sudo cp bt_server_py.service /lib/systemd/system/
sudo chmod 644 /lib/systemd/system/bt_server_py.service
sudo systemctl daemon-reload
sudo systemctl enable bt_server_py.service
echo 'The bluetooth server is configured to start upon next boot.'
echo 'Ensure that runner.sh and bt_server.py are in /home/pi/PiNotify.'
echo 'To monitor service status "sudo systemctl status bt_server_py.service".'

