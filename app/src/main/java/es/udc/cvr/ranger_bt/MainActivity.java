package es.udc.cvr.ranger_bt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener  {

    Button but_sock, but_scan, but_left, but_stop, but_right, but_back, but_front, but_leds, but_get_gyro;
    EditText et_r, et_g, et_b;
    TextView tv_send, tv_rec;
    LinearLayout ll_1, ll_2, ll_3;
    Spinner sp_scan;
    JoystickView jv;

    BluetoothDevice BTdevice;
    BluetoothSocket BTsocket;
    InputStream inputStream;
    OutputStream outputStream;

    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

    BroadcastReceiver mReceiver;  // for scan BT devices
    ArrayList listBTdevices;
    int MY_PERMISSIONS_REQUEST_GPS = 1009;
    ArrayAdapter<String> spinnerAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // CVR
        but_sock = findViewById(R.id.but_sock);
        but_leds = findViewById(R.id.but_leds);
        but_left = findViewById(R.id.but_left);
        but_stop = findViewById(R.id.but_stop);
        but_right = findViewById(R.id.but_right);
        but_front = findViewById(R.id.but_front);
        but_back = findViewById(R.id.but_back);
        et_r = findViewById(R.id.et_red);
        et_g = findViewById(R.id.et_green);
        et_b = findViewById(R.id.et_blue);
        tv_send = findViewById(R.id.tv_send);
        tv_rec = findViewById(R.id.tv_rec);
        but_get_gyro = findViewById(R.id.but_get_gyro);
        ll_1 = findViewById(R.id.ll_1);
        ll_2 = findViewById(R.id.ll_2);
        ll_3 = findViewById(R.id.ll_3);


        but_leds.setOnClickListener(this);
        but_left.setOnClickListener(this);//v -> viewModel.sendMessage(""));
        but_stop.setOnClickListener(this); //v -> viewModel.sendMessage(""));
        but_right.setOnClickListener(this); //v -> viewModel.sendMessage(""));
        but_back.setOnClickListener(this);
        but_front.setOnClickListener(this);
        but_sock.setOnClickListener(this);
        but_get_gyro.setOnClickListener(this);

        but_scan = findViewById(R.id.but_scan);
        but_scan.setOnClickListener(this);
        sp_scan = findViewById(R.id.sp_scan);

        jv =  findViewById(R.id.jv);
        jv.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                double th_ang = 10.0;
                double pi4 = 45.0;
                double MAX_VEL = 120.0;
                /* if (Math.abs(angle-0.0)< th_ang ) angle = 0;
                if (Math.abs(angle-90.0)< th_ang ) angle = 90;
                if (Math.abs(angle-180.0)< th_ang ) angle = 180;
                if (Math.abs(angle-270.0)< th_ang ) angle = 270;
                if (Math.abs(angle-360.0)< th_ang ) angle = 0;
                **/

                // do whatever you want
                byte[] speedbytes = new byte[]{(byte)0xff, (byte)0x55, (byte)0x07, (byte)0x00, (byte)0x02, (byte)0x05,
                        (byte)0x02, (byte)0x00, (byte)0x02, (byte)0x00};
                int vel = (int) ( (float) strength * MAX_VEL/100.0); // MAX VEL = 255 !?
                double ang = ((double) (angle+0.0)/180.0* Math.PI);
                int vel_left, vel_right;
                //    vel_left = (int) ((double) vel * Math.sin(ang/2.0-Math.PI/4.0));
                //    vel_right = -(int) ((double) vel * Math.sin(ang/2.0-Math.PI/4.0));
                if ( angle>=0 && angle<90) { // MAL: va hacia atrás
                    vel_left = vel;
                    vel_right = (int) ((double) vel * Math.sin((double)(45-angle)*2.0 /180.0*Math.PI ));
                } else if ( angle>=90 && angle<180) {  // BIEN: gira izquierda
                    vel_left = (int) ((double) vel * Math.sin((double)(135-angle) *2.0 /180.0*Math.PI) );
                    vel_right = -vel;
                } else if (angle>=180 && angle<270) {  // MAL: va hacia adelante
                    vel_left = - vel;
                    vel_right = - (int) ((double) vel * Math.sin((double)(225-angle) *2.0 /180.0*Math.PI) );
                } else { //(angle >=270&&angle<360) )  // BIEN: gira derecha
                    vel_left = - (int) ((double) vel * Math.sin((double)(315-angle) *2.0 /180.0*Math.PI) );
                    vel_right = vel;
                }


                byte[] bytes_left = ByteBuffer.allocate(4).putInt(vel_left).array();
                speedbytes[6] = bytes_left[2];
                speedbytes[7] = bytes_left[3];
                byte[] bytes_right = ByteBuffer.allocate(4).putInt(vel_right).array();
                speedbytes[8] = bytes_right[2];
                speedbytes[9] = bytes_right[3];
                Log.d("_TAG", "angle "+ angle + " ("+ang+") streng: "+strength+ ";  speed: "+vel_left+" "+vel_right
                //        +"  hex: "+bytes_left[3]+bytes_left[2]+bytes_left[1]+bytes_left[0]
                //        +" "+bytes_right[3]+bytes_right[2]+bytes_right[1]+bytes_right[0]
                //        +" string HEX "+String.format("%1x", bytes_left[2])+String.format("%1x", bytes_left[3])
                //        +" , "+String.format("%1x", bytes_right[2])+String.format("%1x", bytes_right[3]
                );

                send_cmd(speedbytes);
            }
        }, 5); // 5 datos por segundo


        // init closing BT socket (that is, making invisible the command buttons)
        closeBT();
    }

    /**/
    public String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }
    public String encodeHexString(byte[] byteArray) {
        StringBuffer hexStringBuffer = new StringBuffer();
        for (int i = 0; i < byteArray.length; i++) {
            hexStringBuffer.append(byteToHex(byteArray[i]));
        }
        return hexStringBuffer.toString();
    }/**/
    public String encodeUsingBigIntegerToString(byte[] bytes) {
        BigInteger bigInteger = new BigInteger(1, bytes);
        return bigInteger.toString(16);
    }


    private void send_cmd (byte[] cmd) {
        if (BTsocket != null && BTsocket.isConnected()) {
            try {
                outputStream.write(cmd);
            } catch (IOException e) {
                e.printStackTrace();
            }
            tv_send.setText( encodeHexString(cmd));
        }
        Log.d("_TAG", " sended cmd: "+ encodeHexString(cmd));
    }

    @Override
    public void onClick(View view) {
        if (view == but_sock) {
            // check if BT connection is open
            if (BTsocket != null && BTsocket.isConnected()) {
               closeBT();
            } else {
                openBT();
            }
        } else if (view == but_scan) {
            scanBT();
        } else if (view == but_leds) {
                byte[] ledsbytes = new byte[]{(byte)0xff, (byte)0x55, (byte)0x09, (byte)0x00, (byte)0x02, (byte)0x08,
                        (byte)0x00, (byte)0x02, (byte)0x00, (byte)200, (byte)200, (byte)200};
                int red = Integer.valueOf(et_r.getText().toString());
                int green = Integer.valueOf(et_g.getText().toString());
                int blue = Integer.valueOf(et_b.getText().toString());
                Spinner sp_ledid = findViewById(R.id.sp_ledid);
                int ledid = Integer.valueOf(sp_ledid.getSelectedItem().toString());
                ledsbytes[8] = (byte) ledid;
                ledsbytes[9] = (byte) red;
                ledsbytes[10] = (byte) green;
                ledsbytes[11] = (byte) blue;

                send_cmd(ledsbytes);

        } else if (view == but_right) {
            byte[] rightbytes = new byte[]{(byte)0xff, (byte)0x55, (byte)0x07, (byte)0x00, (byte)0x02, (byte)0x05,
                    (byte)0x6e, (byte)0x00, (byte)0x6e, (byte)0x00};
            send_cmd(rightbytes);
        } else if (view == but_left) {
            byte[] leftbytes = new byte[]{(byte)0xff, (byte)0x55, (byte)0x07, (byte)0x00, (byte)0x02, (byte)0x05,
                    (byte)0x92, (byte)0xff, (byte)0x92, (byte)0xff};
            send_cmd(leftbytes);
        } else if (view == but_stop) {
            byte[] stopbytes = new byte[]{(byte)0xff, (byte)0x55, (byte)0x07, (byte)0x00, (byte)0x02, (byte)0x05,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
            send_cmd(stopbytes);
        } else if (view == but_front) {
            byte[] frontbytes = new byte[]{(byte)0xff, (byte)0x55, (byte)0x07, (byte)0x00, (byte)0x02, (byte)0x05,
                    (byte)0x92, (byte)0xff, (byte)0x6e, (byte)0x00};
            send_cmd(frontbytes);
        } else if (view == but_back) {
            byte[] backbytes = new byte[]{(byte)0xff, (byte)0x55, (byte)0x07, (byte)0x00, (byte)0x02, (byte)0x05,
                    (byte)0x6e, (byte)0x00, (byte)0x92, (byte)0xff};
            send_cmd(backbytes);
        }else if (view == but_get_gyro) {  // # ff 55 05 00 01 06 01 <axis>   {x, y, z]

            byte[] getgyrobytes = new byte[]{(byte)0xff, (byte)0x55, 05, 00, 01, 06, 01, 01};
            send_cmd(getgyrobytes);
        }
    }

    /**
     **  Open and close BT socket
     ** (and also make visible the command buttons)
     **/
    void openBT () {
        EditText BT_add = findViewById(R.id.et_BT_address);
        String BT_add_str = BT_add.getText().toString();
        if (BT_add_str.isEmpty())
            BT_add_str = "00:1B:10:10:1D:69";
        BTdevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(BT_add_str);
        Method m = null;
        try {
            m = BTdevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
            BTsocket = (BluetoothSocket) m.invoke(BTdevice, Integer.valueOf(1));
            BTsocket.connect();
            inputStream = BTsocket.getInputStream();
            outputStream = BTsocket.getOutputStream();
            stopWorker = false;
            beginListenForData();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (BTsocket.isConnected()) {
            ll_1.setVisibility(View.VISIBLE);
            ll_2.setVisibility(View.VISIBLE);
            ll_3.setVisibility(View.VISIBLE);
            jv.setVisibility(View.VISIBLE);
            tv_send.setText("");
            tv_rec.setText("");
        }
    }

    void closeBT() {
        try {
            if (BTsocket != null && BTsocket.isConnected()) {
                BTsocket.close();
                stopWorker = false;
                workerThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (BTsocket == null || !BTsocket.isConnected()) {
            ll_1.setVisibility(View.GONE);
            ll_2.setVisibility(View.GONE);
            ll_3.setVisibility(View.GONE);
            jv.setVisibility(View.GONE);
        }
    }


    /**
     **  Thread to read message received from the BT socket
    **/
    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = inputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            inputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    //final String data = new String(encodedBytes, "US-ASCII");
                                    final String data = encodeHexString(encodedBytes);
                                    readBufferPosition = 0;
                                    Log.d("_TAG", " received: "+ data);

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            tv_rec.setText(data);
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeBT();
    }


    /**
     ** Scan BT devices
     */
    void scanBT() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,  new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_GPS);
        }
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.startDiscovery();
        if (listBTdevices == null)
             listBTdevices = new ArrayList();
        else
            listBTdevices.clear();

        mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                //Finding devices
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Add the name and address to an array adapter to show in a ListView
                    Log.d("_TAG", " new BT device: "+device.getAddress());
                    if (device.getAddress().contains("00:1B:10")) {
                        listBTdevices.add(device.getAddress());
                        spinnerAdapter.notifyDataSetChanged();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
        Log.d("_TAG", " scanning BT device: ");

        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, listBTdevices);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp_scan.setAdapter(spinnerAdapter);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if ( requestCode == MY_PERMISSIONS_REQUEST_GPS) {
            if (grantResults.length > 0  &&  grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanBT();
            } else {
                Toast.makeText(getApplicationContext(), " GPS denied: no scan BT", Toast.LENGTH_SHORT).show();
            }
            return;
        }
    }
}
