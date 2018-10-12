package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.math.*;

import fr.arnaudguyon.smartgl.math.Vector3D;
import fr.arnaudguyon.smartgl.opengl.LightParallel;
import fr.arnaudguyon.smartgl.opengl.Object3D;
import fr.arnaudguyon.smartgl.opengl.RenderPassObject3D;
import fr.arnaudguyon.smartgl.opengl.RenderPassSprite;
import fr.arnaudguyon.smartgl.opengl.SmartColor;
import fr.arnaudguyon.smartgl.opengl.SmartGLRenderer;
import fr.arnaudguyon.smartgl.opengl.SmartGLView;
import fr.arnaudguyon.smartgl.opengl.SmartGLViewController;
import fr.arnaudguyon.smartgl.opengl.Sprite;
import fr.arnaudguyon.smartgl.opengl.Texture;
import fr.arnaudguyon.smartgl.tools.WavefrontModel;
import fr.arnaudguyon.smartgl.touch.TouchHelperEvent;
import io.reactivex.disposables.Disposable;

public class MainActivity extends Activity implements SmartGLViewController {

    private static final String ORIENT_BLE_ADDRESS = "CB:D5:E1:DD:8F:0D"; // test device

    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    private SmartGLView mSmartGLView;
    private Texture mSpriteTexture;
    private Sprite mSprite;
    private Object3D mSpaceship;
    private Object3D mCube;
    private Texture mShipTexture;
    private RenderPassObject3D mRenderPassObject3D;
    private RenderPassObject3D mRenderPassObject3DColor;

    private boolean raw = true;
    private RxBleDevice orient_device;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    private Long capture_started_timestamp = null;
    boolean connected = false;
    private float freq = 0.f;

    private int counter = 0;
    private CSVWriter writer;
    private File path;
    private File file;
    private boolean logging = false;

    private Button connect_button;
    private Button start_button;
    private Button stop_button;
    private Context ctx;
    private TextView captureTimetextView;
    private TextView accelTextView;
    private TextView gyroTextView;
    private TextView freqTextView;


    private RadioGroup characteristicRadioGroup;
    private String characteristic_str;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        path = Environment.getExternalStorageDirectory();

        connect_button = findViewById(R.id.connect_button);
        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        captureTimetextView = findViewById(R.id.captureTimetextView);
        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        freqTextView = findViewById(R.id.freqTextView);

        characteristicRadioGroup = findViewById(R.id.radioCharacteristic);


        start_button.setOnClickListener(v -> {


            start_button.setEnabled(false);


            // make a new filename based on the start timestamp
            String file_ts = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date());

            String[] entries = null;
            if (raw) {
                file = new File(path, "Orient_raw_" + file_ts + ".csv");
                entries = "timestamp#seq#accel_x#accel_y#accel_z#gyro_x#gyro_y#gyro_z#mag_x#mag_y#mag_z".split("#");
            } else {
                file = new File(path, "Orient_quat_" + file_ts + ".csv");
                entries = "timestamp#seq#quat_w#quat_x#quat_y#quat_z".split("#");
            }


            try {
                writer = new CSVWriter(new FileWriter(file), ',');
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }


            writer.writeNext(entries);

            logging = true;
            capture_started_timestamp = System.currentTimeMillis();
            counter = 0;
            Toast.makeText(this, "Start logging",
                    Toast.LENGTH_SHORT).show();
            stop_button.setEnabled(true);
        });

        stop_button.setOnClickListener(v -> {
            logging = false;
            stop_button.setEnabled(false);
            try {
                writer.flush();
                writer.close();
                Toast.makeText(this, "Recording saved",
                        Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }
            start_button.setEnabled(true);
        });

        connect_button.setOnClickListener(v -> {
            int selectedId;
            RadioButton rb;

            selectedId = characteristicRadioGroup.getCheckedRadioButtonId();
            rb = findViewById(selectedId);
            characteristic_str = rb.getText().toString();

            if (characteristic_str.compareTo("Raw") == 0) {
                raw = true;
            } else {
                raw = false;
            }

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

        });

        packetData = ByteBuffer.allocate(18);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        rxBleClient = RxBleClient.create(this);

        mSmartGLView = findViewById(R.id.smartGLView);
        mSmartGLView.setDefaultRenderer(this);
        mSmartGLView.setController(this);
    }

    @Override
    public void onPrepareView(SmartGLView smartGLView) {
        Context context = smartGLView.getContext();
        SmartGLRenderer renderer = smartGLView.getSmartGLRenderer();

        mRenderPassObject3D = new RenderPassObject3D(RenderPassObject3D.ShaderType.SHADER_TEXTURE_LIGHTS, true, true);
        mRenderPassObject3DColor = new RenderPassObject3D(RenderPassObject3D.ShaderType.SHADER_COLOR, true, false);

        mCube = loadCube(context);

        mRenderPassObject3DColor.addObject(mCube);

        renderer.addRenderPass(mRenderPassObject3D);  // add it only once for all 3D Objects
        renderer.addRenderPass(mRenderPassObject3DColor);

        renderer.setDoubleSided(false);

        SmartColor lightColor = new SmartColor(1, 1, 1);
        Vector3D lightDirection = new Vector3D(0, 1, 1);
        lightDirection.normalize();
        LightParallel lightParallel = new LightParallel(lightColor, lightDirection);
        renderer.setLightParallel(lightParallel);

    }

    private Object3D loadCube(Context context) {
        WavefrontModel modelColored = new WavefrontModel.Builder(context, R.raw.cube_color_obj)
                .create();
        Object3D object3D = modelColored.toObject3D();
        object3D.setPos(0, 0, -4);
        return object3D;
    }

    @Override
    public void onReleaseView(SmartGLView smartGLView) {
    }

    @Override
    public void onResizeView(SmartGLView smartGLView) {
    }

    @Override
    public void onTick(SmartGLView smartGLView) {
        SmartGLRenderer renderer = smartGLView.getSmartGLRenderer();
        float frameDuration = renderer.getFrameDuration();

        if (mCube != null) {
            float rx = mCube.getRotX() + 100 * frameDuration;
            float ry = mCube.getRotY() + 77 * frameDuration;
            float rz = mCube.getRotZ() + 56 * frameDuration;
            mCube.setRotation(rx, ry, rz);
        }
    }

    @Override
    public void onTouchEvent(SmartGLView smartGLView, TouchHelperEvent event) {
    }


    private void connectToOrient(String addr) {
        orient_device = rxBleClient.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC;
        else characteristic = ORIENT_QUAT_CHARACTERISTIC;

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
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Receiving sensor data",
                                            Toast.LENGTH_SHORT).show();
                                    start_button.setEnabled(true);
                                });
                            }
                            if (raw) handleRawPacket(bytes);
                            else handleQuatPacket(bytes);
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                        }
                );
    }

    private void handleQuatPacket(final byte[] bytes) {
        long ts = System.currentTimeMillis();
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

        //Log.i("OrientAndroid", "QuatInt: (w=" + w + ", x=" + x + ", y=" + y + ", z=" + z + ")");
        //Log.i("OrientAndroid", "QuatDbl: (w=" + dw + ", x=" + dx + ", y=" + dy + ", z=" + dz + ")");

        if (logging) {
            //String[] entries = "first#second#third".split("#");
            String[] entries = {Long.toString(ts),
                    Integer.toString(counter),
                    Double.toString(dw),
                    Double.toString(dx),
                    Double.toString(dy),
                    Double.toString(dz),
            };
            writer.writeNext(entries);

            if (counter % 12 == 0) {
                long elapsed_time = System.currentTimeMillis() - capture_started_timestamp;
                int total_secs = (int) elapsed_time / 1000;
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

                String accel_str = "Quat: (" + dw + ", " + dx + ", " + dy + ", " + dz + ")";
                String freq_str = "Freq: " + freq;

                runOnUiThread(() -> {
                    captureTimetextView.setText(time_str);
                    accelTextView.setText(accel_str);
                    freqTextView.setText(freq_str);
                });
            }

            counter += 1;
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
        //Log.i("OrientAndroid", "Gyro:(" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")");
        //if (mag_x != 0f || mag_y != 0f || mag_z != 0f)
        //Log.i("OrientAndroid", "Mag:(" + mag_x + ", " + mag_y + ", " + mag_z + ")");

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
                int total_secs = (int) elapsed_time / 1000;
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
    protected void onPause() {
        if (mSmartGLView != null) {
            mSmartGLView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSmartGLView != null) {
            mSmartGLView.onResume();
        }
    }

    public void set(Quat4d q1) {
        double sqw = q1.w*q1.w;
        double sqx = q1.x*q1.x;
        double sqy = q1.y*q1.y;
        double sqz = q1.z*q1.z;
        double unit = sqx + sqy + sqz + sqw; // if normalised is one, otherwise is correction factor
        double test = q1.x*q1.y + q1.z*q1.w;
        if (test > 0.499*unit) { // singularity at north pole
            heading = 2 * atan2(q1.x,q1.w);
            attitude = Math.PI/2;
            bank = 0;
            return;
        }
        if (test < -0.499*unit) { // singularity at south pole
            heading = -2 * atan2(q1.x,q1.w);
            attitude = -Math.PI/2;
            bank = 0;
            return;
        }
        heading = atan2(2*q1.y*q1.w-2*q1.x*q1.z , sqx - sqy - sqz + sqw);
        attitude = asin(2*test/unit);
        bank = atan2(2*q1.x*q1.w-2*q1.y*q1.z , -sqx + sqy - sqz + sqw)
    }
}
