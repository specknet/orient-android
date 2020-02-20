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

public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {

    // test device - replace with the real BLE address of your sensor, which you can find
    // by scanning for devices with the NRF Connect App

    //private static final String ORIENT_BLE_ADDRESS = "D5:71:F3:51:9E:73";
    private static final String ORIENT_BLE_ADDRESS = "F4:D9:CB:46:8A:D7";

    private static final float SAMPLE_RATE = 25.0f;

    private static final String ORIENT_QUAT_CHARACTERISTIC = "ef680404-9b35-4933-9b10-52ffa9740042";
    //private static final String ORIENT_RAW_CHARACTERISTIC = "ef680406-9b35-4933-9b10-52ffa9740042";
    private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    private static final int UDP_PORT = 5555;
    //static final String HOST_NAME = "192.168.137.1";
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


    //private int n = 0;
    private Long connected_timestamp = null;
    private Long capture_started_timestamp = null;
    boolean connected = false;
    private float freq = 0.f;

    private int counter = 0;
    private CSVWriter writer;
    private File path;
    private File file;
    private boolean logging = false;

    private Button start_button;
    private Button stop_button;
    private Context ctx;
    private TextView captureTimetextView;
    private TextView accelTextView;
    private TextView gyroTextView;
    private TextView freqTextView;
    private EditText nameEditText;
    private EditText notesEditText;

    private Spinner positionSpinner;
    private Spinner groupSpinner;
    private Spinner activitySpinner;

    private String group_str = null;
    private String position_str = null;
    private String act_type_str = null;
    private String name_str = null;
    private String notes_str = null;
    private String side_str = null;

    private String orientation_str = null;
    private String steps_str = null;
    private String mounting_str = null;

    private RadioGroup sideRadioGroup;
    private RadioGroup orientationRadioGroup;
    private RadioGroup stepsRadioGroup;
    private RadioGroup mountingRadioGroup;

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

    private void runApp() {

        //startDiaryApp(getActivity(), "com.specknet.rehabdiary");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Intent intent = new Intent(this, MyService2.class);
        startService(intent);


        path = Environment.getExternalStorageDirectory();

        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        captureTimetextView = findViewById(R.id.captureTimetextView);
        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        freqTextView = findViewById(R.id.freqTextView);
        positionSpinner = findViewById(R.id.positionSpinner);
        groupSpinner = findViewById(R.id.groupSpinner);
        activitySpinner = findViewById(R.id.activitySpinner);
        nameEditText = findViewById(R.id.nameEditText);
        notesEditText = findViewById(R.id.notesEditText);

        positionSpinner.setOnItemSelectedListener(this);
        groupSpinner.setOnItemSelectedListener(this);
        activitySpinner.setOnItemSelectedListener(this);

        sideRadioGroup = findViewById(R.id.radioSide);
        orientationRadioGroup = findViewById(R.id.radioSide);
        stepsRadioGroup = findViewById(R.id.radioSteps);
        mountingRadioGroup = findViewById(R.id.radioMouting);

        List<String> position_list = new ArrayList<String>();
        position_list.add("---");
        position_list.add("Wrist");
        position_list.add("Upper arm");
        position_list.add("Torso");
        position_list.add("Upper Leg");
        position_list.add("Lower Leg");
        position_list.add("Foot");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, position_list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        positionSpinner.setAdapter(adapter);

        List<String> group_list = new ArrayList<String>();
        group_list.add("---");
        for (char c = 'A'; c <= 'Z'; c++){
            group_list.add(String.valueOf(c));
        }

        ArrayAdapter<String> adapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, group_list);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(adapter2);

        List<String> activity_list = new ArrayList<String>();
        activity_list.add("---");
        activity_list.add("Walking (level ground)");
        activity_list.add("Climbing stairs");
        activity_list.add("Descending stairs");
        activity_list.add("Running");

        ArrayAdapter<String> adapter3 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, activity_list);
        adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activitySpinner.setAdapter(adapter3);

        start_button.setOnClickListener(v-> {

            if (group_str == null || act_type_str == null || position_str == null || (nameEditText.getText().toString().length() == 0)) {
                Toast.makeText(this,"Please complete all details",
                        Toast.LENGTH_SHORT).show();
            }
            else {
                start_button.setEnabled(false);

                name_str = nameEditText.getText().toString();
                notes_str = notesEditText.getText().toString();

                int selectedId;
                RadioButton rb;

                selectedId = sideRadioGroup.getCheckedRadioButtonId();
                rb = findViewById(selectedId);
                side_str = rb.getText().toString();

                selectedId = orientationRadioGroup.getCheckedRadioButtonId();
                rb = findViewById(selectedId);
                orientation_str = rb.getText().toString();

                selectedId = stepsRadioGroup.getCheckedRadioButtonId();
                rb = findViewById(selectedId);
                steps_str = rb.getText().toString();

                selectedId = mountingRadioGroup.getCheckedRadioButtonId();
                rb = findViewById(selectedId);
                mounting_str = rb.getText().toString();


                // make a new filename based on the start timestamp
                String file_ts = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date());
                file = new File(path, "PDIoT_" + group_str + "_" + file_ts + ".csv");

                try {
                    writer = new CSVWriter(new FileWriter(file), ',');
                } catch (IOException e) {
                    Log.e("MainActivity", "Caught IOException: " + e.getMessage());
                }

                String[] h1 = {"# PDIoT walking data"};
                writer.writeNext(h1);

                String[] h2 = {"# Start time: " + file_ts};
                writer.writeNext(h2);

                String[] h3 = {"# Group: " + group_str};
                writer.writeNext(h3);

                String[] h4 = {"# Subject: " + name_str};
                writer.writeNext(h4);

                String[] h5 = {"# Activity type: " + act_type_str};
                writer.writeNext(h5);

                String[] h6 = {"# Sensor position: " + position_str};
                writer.writeNext(h6);

                String[] h7 = {"# Side of body: " + side_str};
                writer.writeNext(h7);

                String[] h8 = {"# Sensor mounting: " + mounting_str};
                writer.writeNext(h8);

                String[] h9 = {"# Number of steps: " + steps_str};
                writer.writeNext(h9);

                String[] h10 = {"# Notes: " + notes_str};
                writer.writeNext(h10);

                String[] h11 = {""};
                writer.writeNext(h11);

                String[] entries = "timestamp#seq#accel_x#accel_y#accel_z#gyro_x#gyro_y#gyro_z#mag_x#mag_y#mag_z".split("#");
                writer.writeNext(entries);

                logging = true;
                capture_started_timestamp = System.currentTimeMillis();
                counter = 0;
                Toast.makeText(this, "Start logging",
                        Toast.LENGTH_SHORT).show();
                stop_button.setEnabled(true);
            }
        });

        stop_button.setOnClickListener(v-> {
            logging = false;
            stop_button.setEnabled(false);
            try {
                writer.flush();
                writer.close();
                Toast.makeText(this,"Recording saved",
                        Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }
            start_button.setEnabled(true);
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

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
                               long id) {
        // TODO Auto-generated method stub
        if (parent.getItemAtPosition(position).toString().compareTo("---") == 0) return;

        if (parent.getId() == groupSpinner.getId()) {
            //Toast.makeText(this, "GROUP : " + parent.getItemAtPosition(position).toString(), Toast.LENGTH_SHORT).show();
            group_str = parent.getItemAtPosition(position).toString();
        }
        else if (parent.getId() == positionSpinner.getId()) {
            //Toast.makeText(this, "POSITION : " + parent.getItemAtPosition(position).toString(), Toast.LENGTH_SHORT).show();
            this.position_str = parent.getItemAtPosition(position).toString();
        }
        else if (parent.getId() == activitySpinner.getId()) {
            //Toast.makeText(this, "ACTIVITY : " + parent.getItemAtPosition(position).toString(), Toast.LENGTH_SHORT).show();
            this.act_type_str = parent.getItemAtPosition(position).toString();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
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
                                connected_timestamp = System.currentTimeMillis();
                                runOnUiThread(() -> {
                                                               Toast.makeText(ctx, "Receiving sensor data",
                                                                       Toast.LENGTH_SHORT).show();
                                    start_button.setEnabled(true);
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
            float[] q = orientationGyroscope.calculateOrientation(new float[]{gyro_x * (float)Math.PI / 180f,gyro_y * (float)Math.PI / 180f,gyro_z * (float)Math.PI / 180f}, 1.0f/SAMPLE_RATE);
            //float[] q = orientationFusion.calculateFusedOrientation(new float[]{gyro_x * (float) Math.PI / 180f, gyro_y * (float) Math.PI / 180f, gyro_z * (float) Math.PI / 180f}, 1.0f / 50.0f, new float[]{accel_x, accel_y, accel_z}, latest_mag);
            //float[] q = orientationKalman.calculateFusedOrientation(new float[]{gyro_x * (float) Math.PI / 180f, gyro_y * (float) Math.PI / 180f, gyro_z * (float) Math.PI / 180f}, 1.0f / 50.0f, new float[]{accel_x, accel_y, accel_z}, latest_mag);
            //float[] q = orientationOrient.calculateOrientation(new float[]{gyro_x * (float) Math.PI / 180f, gyro_y * (float) Math.PI / 180f, gyro_z * (float) Math.PI / 180f}, 1.0f / SAMPLE_RATE, new float[]{accel_x, accel_y, accel_z}, latest_mag);

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

        if (logging) {
            //String[] entries = "first#second#third".split("#");
            String[] entries = {Long.toString(ts),
                    Integer.toString(counter),
                    Float.toString(accel_x),
                    Float.toString(accel_y),
                    Float.toString(accel_z),
                    Float.toString(gyro_x),
                    Float.toString(gyro_y),
                    Float.toString(gyro_z),
                    Float.toString(mag_x),
                    Float.toString(mag_y),
                    Float.toString(mag_z),
            };
            writer.writeNext(entries);

            if (counter % 12 == 0) {
                long elapsed_time = System.currentTimeMillis() - capture_started_timestamp;
                int total_secs = (int)elapsed_time / 1000;
                int s = total_secs % 60;
                int m = total_secs / 60;

                String m_str = Integer.toString(m);
                if (m_str.length() < 2) {
                    m_str = "0" + m_str;
                }

                String s_str = Integer.toString(s);
                if (s_str.length() < 2) {
                    s_str = "0" + s_str;
                }


                Long elapsed_capture_time = System.currentTimeMillis() - capture_started_timestamp;
                float connected_secs = elapsed_capture_time / 1000.f;
                freq = counter / connected_secs;
                //Log.i("OrientAndroid", "Packet count: " + Integer.toString(n) + ", Freq: " + Float.toString(freq));

                String time_str = m_str + ":" + s_str;

                String accel_str = "Accel: (" + accel_x + ", " + accel_y + ", " + accel_z + ")";
                String gyro_str = "Gyro: (" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")";
                String freq_str = "Freq: " + freq;

                runOnUiThread(() -> {
                    captureTimetextView.setText(time_str);
                    accelTextView.setText(accel_str);
                    gyroTextView.setText(gyro_str);
                    freqTextView.setText(freq_str);
                });
            }

            counter += 1;
        }
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
