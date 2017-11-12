#!/usr/bin/python

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
import copy
import bluetooth
import os
import select
import struct
import threading
import RPi.GPIO as GPIO
import time
import traceback

LIGHT_PIN_NUMBERS=set([2,3])
BUTTON_PIN_NUMBER=4

BUTTON_POLL_INTERVAL_SEC=0.02

def SetupGpioPins():
  GPIO.setmode(GPIO.BCM)
  for light_pin in LIGHT_PIN_NUMBERS:
    GPIO.setup(light_pin, GPIO.OUT)
  GPIO.setup(BUTTON_PIN_NUMBER, GPIO.IN, pull_up_down=GPIO.PUD_UP)


class BluetoothServer(object):
  """Class that manages Bluetooth communication with the tablet.

  The server receives blink commands from inbound connections, which it
  acknowledges by sending _ACK_MSG. It relays these requests to the constructor
  injected 'blinker'.

  The server can also send a "dismissal" on all active connections (connections
  are held open indefinitely until device shutdown / out of range). The
  dismissal indicates that the message currently displayed on the tablet has
  been seen and may be "dismissed".
  """
  UUID = '00001101-0000-1000-8000-00805F9B34FB'

  # All messages are one octet long, for simplicity.

  # Messages we send.
  _DISMISS_MSG = '\x00'
  _ACK_MSG = '\x01'

  # Messages we receive.
  _STOP_BLINKING_MSG = '\x00'

  def __init__(self, blinker):
    """Creates and starts the BluetoothServer.

    Args:
      blinker: An object that has 2 methods Blink(duration_on, duration_off),
               and StopBlinking().
    """
    self._blinker = blinker
    self._connections = set()
    self._connections_lock = threading.Lock()
    self._thread = threading.Thread(target=self._ThreadBody)
    self._thread.start()

  def _ThreadBody(self):
    server_socket = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    with self._connections_lock:
      self._connections.add(server_socket)
    server_socket.bind(("", bluetooth.PORT_ANY))
    print 'Listening for connections'
    server_socket.listen(5)
    bluetooth.advertise_service(
        server_socket, 'PiNotifyBtServer',  service_id=self.UUID, 
        service_classes = [bluetooth.SERIAL_PORT_CLASS],
        profiles = [bluetooth.SERIAL_PORT_PROFILE])

    while True:
      with self._connections_lock:
        connections = copy.copy(self._connections)
      rlist, _, _ = select.select(connections, [], [])
      for sock in rlist:
        if sock is server_socket:
          accepted, addr = sock.accept() 
          print 'Accepted a connection from %s' % str(addr)
          with self._connections_lock:
            self._connections.add(accepted)
        else:
          # We expect an octet -- if we get too much, close the socket.
          BUFFER_SIZE=1024
          received = self._TrySockOp(sock.recv, BUFFER_SIZE)
          if received is None:
            # Cleanup already happened...continue.
            print 'Cleanup already happened'
            continue
          if len(received) != 1:
            print ('Unexpectedly received a message of length %s: %s' % 
                   (len(received), received))
            self._CleanupSock(sock)
            continue
          self._HandleMessage(received)

  def _HandleMessage(self, received):
    """Handle the received inbound message."""
    if received == self._STOP_BLINKING_MSG:
      self._blinker.StopBlinking()
      return
    unpacked = struct.unpack('B', received)[0]
    low_nibble = unpacked & 0x0F
    high_nibble = (unpacked & 0xF0) >> 4
    self._blinker.Blink(duration_on=high_nibble, duration_off=low_nibble)

  def _TrySockOp(self, bound_op, *args, **kwargs):
    """Tries a socket operation, cleaning up the socket on failure

    Bluetooth socket operations can fail (device out of range, offline,
    I/O error, etc.), in which case we want to clean up the socket and its
    references. This function automatically does this.

    Args:
      bound_op: A bound socket method, like connection.send (without parens).
      argv: Positional args, forwared to bound_op().
      kwargs: Keyword arguments, forwarded to bound_op().
    """
    try:
      return bound_op(*args, **kwargs)
    except bluetooth.BluetoothError as e:
      traceback.print_exc()
      CONNECTION_RESET = 104
      if str(CONNECTION_RESET) in e.message:
        # For some reason, after getting a connection reset on a socket, the
        # listener socket silently stops working...since we're stateless, we
        # can solve this by quitting, which will result in a re-launch of the
        # server.
        os._exit(1)
      self._CleanupSock(sock = bound_op.__self__)

  def _CleanupSock(self, sock):
    """Cleans up sock, if cleanup is necessary."""
    with self._connections_lock:
      if sock not in self._connections:
        return
      print 'Cleaning up connection'
      self._connections.remove(sock)
      sock.close()
  
  def Dismiss(self):
    """Sends a dismiss message on all active connections."""
    with self._connections_lock:
      connections = copy.copy(self._connections)
    for connection in connections:
      self._TrySockOp(connection.send, self._DISMISS_MSG)


class HasWaiterEvent(object):
  def __init__(self):
    self._main_event = threading.Event()
    self._has_waiter_event = threading.Event()

  def set(self, *args, **xargs):
    self._main_event.set(*args, **xargs)

  def clear(self, *args, **xargs):
    self._main_event.clear(*args, **xargs)

  def wait(self, *args, **xargs):
    self._has_waiter_event.set()
    self._main_event.wait(*args, **xargs)
    self._has_waiter_event.clear()

  def WaitForWaiter(self, *args, **xargs):
    self._has_waiter_event.wait(*args, **xargs)


class RepeatingTimer(object):
  def __init__(self, function):
    self._function = function
    self._interval = 0
    self._interval_lock = threading.Lock()

    self._running = HasWaiterEvent()
    self._stop = threading.Event()
    self._events_lock = threading.Lock()

    self._thread = threading.Thread(target=self._ThreadBody)
    self._thread.start()

  def Start(self):
    with self._events_lock:
      self._running.set()

  def Cancel(self):
    with self._events_lock:
      self._running.clear()
      self._stop.set()
      self._running.WaitForWaiter()
      self._stop.clear()

  def SetInterval(self, interval):
    with self._interval_lock:
      self._interval = interval

  def _ThreadBody(self):
    while True:
      self._running.wait()
      while True:
        self._function()
        with self._interval_lock:
          interval = self._interval
        if self._stop.wait(interval):
          break


class Blinker(object):
  def __init__(self):
    self._duration_on = 0
    self._duration_off = 0
    self._lights_on = False
    self._timer = RepeatingTimer(self._TimerBody)

  def _TimerBody(self):
    self._lights_on = not self._lights_on
    self._SetLights(self._lights_on)
    duration = self._duration_off if self._lights_on else self._duration_on
    self._timer.SetInterval(duration)

  def _SetLights(self, lights_on):
    gpio_value = GPIO.HIGH if lights_on else GPIO.LOW
    for light_pin in LIGHT_PIN_NUMBERS:
      GPIO.output(light_pin, gpio_value)

  def Blink(self, duration_on, duration_off):
    print 'Told to blink %s on %s off.' % (duration_on, duration_off)
    self._duration_on, self._duration_off = duration_on, duration_off
    self._timer.Cancel()
    self._lights_on = False
    # The timer will immediately turn on the lights, so no need to call
    # _SetLights().
    self._timer.SetInterval(self._duration_on)
    self._timer.Start()

  def StopBlinking(self):
    print 'Told to stop blinking'
    self._timer.Cancel()
    self._lights_on = False
    self._SetLights(self._lights_on)


class ButtonReader(object):
  def __init__(self, action):
    self._action = action
    self._thread = threading.Thread(target=self._ThreadBody)
    self._thread.start()

  def _ThreadBody(self):
    button_down = False
    while(True):
      cur_button_down = GPIO.input(BUTTON_PIN_NUMBER) == 0
      # print 'cur_button_down %s' % cur_button_down
      if button_down == False and cur_button_down == True:
        self._action()
      button_down = cur_button_down
      time.sleep(BUTTON_POLL_INTERVAL_SEC)

if __name__ == "__main__":
  SetupGpioPins()
  blinker = Blinker()
  bluetooth_server = BluetoothServer(blinker=blinker)
  def ButtonAction():
    blinker.StopBlinking()
    bluetooth_server.Dismiss()
  button_reader = ButtonReader(action=ButtonAction)
