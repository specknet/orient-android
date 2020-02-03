package com.specknet.orientandroid;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVWriter;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import org.apache.commons.math3.complex.Quaternion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.reactivex.disposables.Disposable;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import java.net.SocketException;
import java.net.UnknownHostException;

import javax.vecmath.Vector3f;

import static java.lang.Math.atan2;

public class MainActivity extends Activity {

    // test device - replace with the real BLE address of your sensor, which you can find
    // by scanning for devices with the NRF Connect App

    private static final String ORIENT_BLE_ADDRESS = "D5:71:F3:51:9E:73";

    private static final String ORIENT_QUAT_CHARACTERISTIC = "ef680404-9b35-4933-9b10-52ffa9740042";
    private static final String ORIENT_RAW_CHARACTERISTIC = "ef680406-9b35-4933-9b10-52ffa9740042";

    private static final int UDP_PORT = 5555;
    //private static final String HOST_NAME = "192.168.137.1";
    static final String HOST_NAME = "127.0.0.1";
    private static final boolean raw = true;

    private RxBleDevice orient_device;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    private int port;
    private DatagramSocket s;
    private InetAddress local;

    private int msg_length;
    private byte[] message;
    private DatagramPacket p;


    boolean connected = false;
    private float freq = 0.f;

    private int counter = 0;


    private Button start_unity_button;
    private Context ctx;
    private TextView accelTextView;
    private TextView gyroTextView;
    private TextView freqTextView;

    private final int RC_LOCATION_AND_STORAGE = 1;

    private OrientationGyroscope orientationGyroscope;
    private OrientationFusedComplementary orientationFusion;
    private OrientationFusedKalman orientationKalman;
    private OrientationOrient orientationOrient;

    private float[] latest_mag;

    static SensorManager sensorManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        getPermissions();
    }

    @AfterPermissionGranted(RC_LOCATION_AND_STORAGE)
    private void getPermissions() {
        String[] perms = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            // ...
            runApp();

        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.location_and_storage_rationale),
                    RC_LOCATION_AND_STORAGE, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    public void startUnityGame(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage("");

        if (intent == null) {
            Toast.makeText(context, "Can't find Unity teddybear visualisation app",
                    Toast.LENGTH_LONG).show();
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private void runApp() {
        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        freqTextView = findViewById(R.id.freqTextView);


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Intent intent = new Intent(this, MyService2.class);
        startService(intent);

        start_unity_button.setOnClickListener(v-> {
            startUnityGame(ctx);
        });

        port = UDP_PORT;

        try {
            s = new DatagramSocket();
            local = Inet4Address.getByName(HOST_NAME);
        } catch (Exception e) {
            Log.i("MainActivity", e.getMessage());
        }


        packetData = ByteBuffer.allocate(18);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        orientationGyroscope = new OrientationGyroscope();
        orientationGyroscope.setBaseOrientation(new Quaternion(0,0,0,1));

        orientationFusion = new OrientationFusedComplementary();
        orientationFusion.setBaseOrientation(new Quaternion(0,0,0,1));

        orientationKalman = new OrientationFusedKalman();
        orientationKalman.setBaseOrientation(new Quaternion(0,0,0,1));
        orientationKalman.startFusion();

        orientationOrient = new OrientationOrient();
        orientationOrient.setBaseOrientation(new Quaternion(0,0,0,1));

        latest_mag = null;

        rxBleClient = RxBleClient.create(this);

        scanSubscription = rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )
                .subscribe(
                        scanResult -> {
                            Log.i("OrientAndroid", "FOUND: " + scanResult.getBleDevice().getName() + ", " +
                                    scanResult.getBleDevice().getMacAddress());
                            // Process scan result here.
                            if (scanResult.getBleDevice().getMacAddress().equals(ORIENT_BLE_ADDRESS)) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Found " + scanResult.getBleDevice().getName() + ", " +
                                                    scanResult.getBleDevice().getMacAddress(),
                                            Toast.LENGTH_SHORT).show();
                                });
                                connectToOrient(ORIENT_BLE_ADDRESS);
                                scanSubscription.dispose();
                            }
                        },
                        throwable -> {
                            // Handle an error here.
                            runOnUiThread(() -> {
                                Toast.makeText(ctx, "BLE scanning error",
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                );
    }


    private void connectToOrient(String addr) {
        orient_device = rxBleClient.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        orient_device.establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            //n += 1;
                            // Given characteristic has been changes, here is the value.

                            //Log.i("OrientAndroid", "Received " + bytes.length + " bytes");
                            if (!connected) {
                                connected = true;
                                //connected_timestamp = System.currentTimeMillis();
                                runOnUiThread(() -> {
                                                               Toast.makeText(ctx, "Receiving sensor data",
                                                                       Toast.LENGTH_SHORT).show();
                                    start_unity_button.setEnabled(true);
                                                           });
                            }
                            if (raw) handleRawPacket(bytes); else handleQuatPacket(bytes);
                        },
                        throwable -> {
                            // Handle an error here.
                            throwable.printStackTrace();
                            Log.e("OrientAndroid", "Error: " + throwable.getStackTrace());
                        }
                );
    }

    private void handleQuatPacket(final byte[] bytes) {
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        int w = packetData.getInt();
        int x = packetData.getInt();
        int y = packetData.getInt();
        int z = packetData.getInt();

        double dw = w / 1073741824.0;  // 2^30
        double dx = x / 1073741824.0;
        double dy = y / 1073741824.0;
        double dz = z / 1073741824.0;

        Log.i("OrientAndroid", "QuatInt: (w=" + w + ", x=" + x + ", y=" + y + ", z=" + z + ")");
        Log.i("OrientAndroid", "QuatDbl: (w=" + dw + ", x=" + dx + ", y=" + dy + ", z=" + dz + ")");

        String report = String.format("0,%.2f,%.2f,%.2f,%.2f", dw, dx, dy, dz);

        msg_length = report.length();
        message = report.getBytes();
        p = new DatagramPacket(message, msg_length, local, port);
        try {
            s.send(p);
        } catch (IOException e){
            Log.i("MainActivity", "Exception " + e.getMessage());
        }
    }

    private void handleRawPacket(final byte[] bytes) {
        long ts = System.currentTimeMillis();
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        float accel_x = packetData.getShort() / 1024.f;  // integer part: 6 bits, fractional part 10 bits, so div by 2^10
        float accel_y = packetData.getShort() / 1024.f;
        float accel_z = packetData.getShort() / 1024.f;

        float gyro_x = packetData.getShort() / 32.f;  // integer part: 11 bits, fractional part 5 bits, so div by 2^5
        float gyro_y = packetData.getShort() / 32.f;
        float gyro_z = packetData.getShort() / 32.f;

        float mag_x = packetData.getShort() / 16.f;  // integer part: 12 bits, fractional part 4 bits, so div by 2^4
        float mag_y = packetData.getShort() / 16.f;
        float mag_z = packetData.getShort() / 16.f;

        //Log.i("OrientAndroid", "Accel:(" + accel_x + ", " + accel_y + ", " + accel_z + ")");
        Log.i("OrientAndroid", "Gyro:(" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")");
        //if (mag_x != 0f || mag_y != 0f || mag_z != 0f)
            //Log.i("OrientAndroid", "Mag:(" + mag_x + ", " + mag_y + ", " + mag_z + ")");

        Vector3f va = new Vector3f(accel_x, accel_y, accel_z);
        //va.normalize();
        accel_x = va.getX();
        accel_y = va.getY();
        accel_z = va.getZ();


        if (mag_x != 0.f || mag_y != 0.f || mag_z != 0.f)
        {
            Vector3f v = new Vector3f(mag_x, mag_y, mag_z);
            //v.normalize();
            latest_mag = new float[]{v.getX(), v.getY() ,v.getZ()};
        }

        if (latest_mag != null) {
            //float[] q = orientationGyroscope.calculateOrientation(new float[]{gyro_x * (float)Math.PI / 180f,gyro_y * (float)Math.PI / 180f,gyro_z * (float)Math.PI / 180f}, 1.0f/50.0f);
            //float[] q = orientationFusion.calculateFusedOrientation(new float[]{gyro_x * (float) Math.PI / 180f, gyro_y * (float) Math.PI / 180f, gyro_z * (float) Math.PI / 180f}, 1.0f / 50.0f, new float[]{accel_x, accel_y, accel_z}, latest_mag);
            //float[] q = orientationKalman.calculateFusedOrientation(new float[]{gyro_x * (float) Math.PI / 180f, gyro_y * (float) Math.PI / 180f, gyro_z * (float) Math.PI / 180f}, 1.0f / 50.0f, new float[]{accel_x, accel_y, accel_z}, latest_mag);
            float[] q = orientationOrient.calculateOrientation(new float[]{gyro_x * (float) Math.PI / 180f, gyro_y * (float) Math.PI / 180f, gyro_z * (float) Math.PI / 180f}, 1.0f / 50.0f, new float[]{accel_x, accel_y, accel_z}, latest_mag);

            String report = String.format("0,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f", q[0], q[1], q[2], q[3], accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z, latest_mag[0], latest_mag[1], latest_mag[2]);

            //String report2 = String.format("0,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f", accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z, latest_mag[0], latest_mag[1], latest_mag[2]);

            Log.i("OrientAndroid", "Quat:(" + q[0] + ", " + q[1] + ", " + q[2] + ", " + q[3] + ")");


            msg_length = report.length();
            message = report.getBytes();
            p = new DatagramPacket(message, msg_length, local, port);
            try {
                s.send(p);
            } catch (IOException e) {
                Log.i("MainActivity", "Exception " + e.getMessage());
            }
        }



        if (counter % 12 == 0) {

            String accel_str = "Accel: (" + accel_x + ", " + accel_y + ", " + accel_z + ")";
            String gyro_str = "Gyro: (" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")";
            String freq_str = "Freq: " + freq;

            runOnUiThread(() -> {
                accelTextView.setText(accel_str);
                gyroTextView.setText(gyro_str);
                freqTextView.setText(freq_str);
            });
        }

        counter += 1;

    }

    @Override
    protected void onResume() {
        super.onResume();
        //sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //sensorManager.unregisterListener(this);
    }

}
