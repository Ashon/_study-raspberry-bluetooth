package com.example;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

//import com.android.internal.telephony.ITelephony;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.FROYO)
public class MainActivity extends Activity {
  // final static UUID PI_UUID = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee");

  // bluetooth RFCOMM UUID
  final static UUID PI_UUID = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB");

  final static String PI_DEVICE_NAME = "raspberrypi-0";
  final static String PI_LOGTAG = "log-pi";
  final static String PI_STATUS_MSG_CONNECTED = "Connected";
  final static String PI_STATUS_MSG_NOT_CONNECTED = "Not onnected";

  MainActivity mainInstance = this;

  BluetoothAdapter mBluetoothAdapter;
  BluetoothSocket btSocket = null;
  BluetoothDevice btDevice = null;
  Set<BluetoothDevice> pairedDevices;

  InputStream btInputStream;
  OutputStream btOutputStream;

  TelephonyManager telephonyManager;
  ReceiveThread rThread;

  TextView tv_status;

  Button btn_connectToPi;
  Button btn_closeConnect;

  Button btn_setRinging;
  Button btn_ringOff;

  Button btn_lightOnButton;
  Button btn_lightOffButton;

  public void connectToPi() {
    // if bluetooth adapter is not enabled, show popup to turn on bluetooth apadter
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if(!mBluetoothAdapter.isEnabled()) {
      Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBluetooth, 0);
      Log.e(PI_LOGTAG, "generate intent");
    }

    // if exists paired device, set device to instance
    Log.e(PI_LOGTAG, "try to get paired device");
    pairedDevices = mBluetoothAdapter.getBondedDevices();
    try {
      if(pairedDevices.size() > 0) {
        for(BluetoothDevice device : pairedDevices) {
          // target devices name is "raspberrypi-0"
          if(device.getName().equals(PI_DEVICE_NAME)) {
            Log.e(PI_LOGTAG, "found raspberry pi !!");
            btDevice = device;
            btSocket = btDevice.createRfcommSocketToServiceRecord(PI_UUID);
            if(btSocket.isConnected()) {
              Toast.makeText(mainInstance, "You are already connected raspberry pi", Toast.LENGTH_SHORT).show();
            } else {
              btSocket.connect();
              // connect
              // show connect success message toast
              Toast.makeText(mainInstance, "Connect to raspberry pi first.", Toast.LENGTH_SHORT).show();
              tv_status.setText(PI_STATUS_MSG_CONNECTED);
              break;
            }
          }
        }
      }
    } catch(IOException e) {
      Log.e(PI_LOGTAG, "bluetooth connect error");
    } catch(NullPointerException e) {
      Log.e(PI_LOGTAG, "get device failed. btDevice is null");
    }

    // open bluetooeh IOStream if socket is connected
    try {
      if (btSocket.isConnected()) {
        btInputStream = btSocket.getInputStream();
        btOutputStream = btSocket.getOutputStream();
        Log.e(PI_LOGTAG, "get IO stream success");
      }
    } catch(IOException e) {
      Log.e(PI_LOGTAG, "open IOStream error");
    }
  }

  public void closeConnect() {
    try {
      btInputStream.close();
      btOutputStream.close();
      btSocket.close();
      btSocket = null;
      tv_status.setText(PI_STATUS_MSG_NOT_CONNECTED);
      Toast.makeText(mainInstance, "Close the Connection", Toast.LENGTH_SHORT).show();
    } catch(IOException e) {

    } catch(NullPointerException e) {

    }
  }

  // send message to device via bluetooth
  public void sendMessageViaBluetooth(String message){
    try {
      if(btSocket.isConnected()) {
        // send message
        btOutputStream.write(message.getBytes());
      } else {
        Log.e(PI_LOGTAG, "not connect");
      }
    } catch (IOException e) {
      Log.e(PI_LOGTAG, "error occurred - sendMessageViaBluetooth " + e);
    } catch (NullPointerException e) {
      Toast.makeText(mainInstance, "Connect to raspberry pi first.", Toast.LENGTH_SHORT).show();
      Log.e(PI_LOGTAG, "btSocket is null");
    } catch (Exception e) {
      Log.e(PI_LOGTAG, "btSocket Error");
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // init
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // init UI Resource
    tv_status = (TextView) findViewById(R.id.tv_status);
    btn_connectToPi = (Button)findViewById(R.id.connectToPi);
    btn_closeConnect = (Button)findViewById(R.id.closeConnect);
    btn_setRinging = (Button)findViewById(R.id.tempButton);
    btn_ringOff = (Button)findViewById(R.id.ringOff);
    btn_lightOnButton = (Button)findViewById(R.id.lightOn);
    btn_lightOffButton = (Button)findViewById(R.id.lightOff);

    final class PhoneStateCheckListener extends PhoneStateListener {
      @Override
      public void onCallStateChanged(int state, String incomingNumber) {
        if (state == TelephonyManager.CALL_STATE_IDLE) {
          Toast.makeText(mainInstance, "CALL_STATE_IDLE" + incomingNumber, Toast.LENGTH_SHORT).show();
          sendMessageViaBluetooth("ringIdle");
          try {
            rThread.stop();
          } catch(Exception e) {
            Log.e(PI_LOGTAG, "rThread Start Error in " + this.getClass().getName());
          }
        } else if (state == TelephonyManager.CALL_STATE_RINGING) {
          Toast.makeText(mainInstance, "CALL_STATE_RINGING : Incoming number " + incomingNumber, Toast.LENGTH_SHORT).show();
          try {
            rThread.setMessage("ringing");
            rThread.start();
          } catch(Exception e) {
            Log.e(PI_LOGTAG, "rThread Start Error");
          }
        } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
          Toast.makeText(mainInstance, "STATE_OFFHOOK : Incoming number " + incomingNumber, Toast.LENGTH_SHORT).show();
          sendMessageViaBluetooth("ringIdle");
          try {
            rThread.stop();
          } catch(Exception e) {
            Log.e(PI_LOGTAG, "rThread Start Error in " + this.getClass().getName());
          }
        }
      }
    }

    rThread = new ReceiveThread();
    PhoneStateCheckListener phoneCheckListener = new PhoneStateCheckListener();

    // phone call event listener
    telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
    telephonyManager.listen(phoneCheckListener, PhoneStateListener.LISTEN_CALL_STATE);

    btn_setRinging.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        sendMessageViaBluetooth("ringing");
        try {
          rThread.setMessage("ringing");
          rThread.start();
        } catch(Exception e) {
          Log.e(PI_LOGTAG, "rThread Start Error");
          e.printStackTrace();
        }
      }
    });

    btn_ringOff.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        sendMessageViaBluetooth("ringIdle");
        try {
          rThread.stop();
        } catch(Exception e){
          Log.e(PI_LOGTAG, "rThread Start Error");
        }
      }
    });

    btn_connectToPi.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        connectToPi();
      }
    });

    btn_closeConnect.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        closeConnect();
      }
    });

    btn_lightOnButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        sendMessageViaBluetooth("lightOn");
      }
    });

    btn_lightOffButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        sendMessageViaBluetooth("lightOff");
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  class ReceiveThread implements Runnable {
    public boolean isRunning;
    Handler handler;
    int bytesAvailable;
    volatile Thread timer;
    String messageFromDevice;
    String messageFromPi;



    public ReceiveThread() {
      handler = new Handler();
      messageFromDevice = "";
    }

    public void setMessage(String message) {
      this.messageFromDevice = message;
    }

    public void start() {
      isRunning = true;
      timer = new Thread(this);
      timer.start();
    }

    public void stop() {
      timer = null;
      isRunning = false;
    }

    @Override
    public void run() {
      while(isRunning && !Thread.currentThread().isInterrupted()) {
        // sleep for 10ms.
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        try {
          sendMessageViaBluetooth(messageFromDevice);
          // get bytesAvailable from input stream
          bytesAvailable = btInputStream.available();
          if(bytesAvailable > 0) {
            byte buffer[] = new byte[bytesAvailable];
            int k = btInputStream.read(buffer, 0, bytesAvailable);
            if(k > 0) {
              messageFromPi = new String(buffer);
              if(messageFromPi.contains("pressed")) {
//                Class c = Class.forName(telephonyManager.getClass().getName());
//                Method m = c.getDeclaredMethod("getITelephony");
//                m.setAccessible(true);
//                ITelephony iTelephony = (ITelephony) m.invoke(telephonyManager);
//                // Silence the ringer and then answer the call
//                iTelephony.silenceRinger();
//                iTelephony.answerRingingCall();
                isRunning = false;
              }
              Log.e(PI_LOGTAG, "MSG_FROM_PI : " + messageFromPi);
            }
          } else {
            Log.e(PI_LOGTAG, "Inputstream is not available..");
          }
          // if work done, then close socket, stream
          if (!isRunning) {
            Log.e(PI_LOGTAG, "work done");
            stop();
            break;
          }
        } catch(IOException e) {
          Log.e(PI_LOGTAG, "thread error : IOException");
          isRunning = false;
          break;
        } catch(NullPointerException e) {
          Log.e(PI_LOGTAG, "thread error : NullPointerException :: " + e);
          e.printStackTrace();
          isRunning = false;
          break;
        } catch(Exception e) {
          Log.e(PI_LOGTAG, "thread error : " + e);
          isRunning = false;
          break;
        }
      }
    }
  }
}