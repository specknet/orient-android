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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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

import static java.lang.Math.asin;
import static java.lang.Math.atan2;

public class MainActivity extends Activity implements SmartGLViewController {

    private static final String ORIENT_BLE_ADDRESS = "D7:29:7E:13:28:11"; // test device

    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    //private static final String ORIENT_QUAT_CHARACTERISTIC = "00001527-1212-efde-1524-785feabcd123";
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
    private ToggleButton toggleW;
    private ToggleButton toggleX;
    private ToggleButton toggleY;
    private ToggleButton toggleZ;


    private RadioGroup characteristicRadioGroup;
    private String characteristic_str;

    private double attitude = 0.0;
    private double heading = 0.0;
    private double bank = 0.0;
    private double q_w = 1.0;
    private double q_x = 0.0;
    private double q_y = 0.0;
    private double q_z = 0.0;

    private static float divisor_quat = (1 << 30);
    private static String rotation;

    private boolean negate_w = false;
    private boolean negate_x = false;
    private boolean negate_y = false;
    private boolean negate_z = false;




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

        toggleW = (ToggleButton) findViewById(R.id.toggleButtonW);
        toggleW.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    negate_w = true;
                } else {
                    negate_w = false;
                    // The toggle is disabled
                }
            }
        });

        toggleX = (ToggleButton) findViewById(R.id.toggleButtonX);
        toggleX.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    negate_x = true;
                } else {
                    negate_x = false;
                    // The toggle is disabled
                }
            }
        });

        toggleY = (ToggleButton) findViewById(R.id.toggleButtonY);
        toggleY.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    negate_y = true;
                } else {
                    negate_y = false;
                    // The toggle is disabled
                }
            }
        });

        toggleZ = (ToggleButton) findViewById(R.id.toggleButtonZ);
        toggleZ.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    negate_z = true;
                } else {
                    negate_z = false;
                    // The toggle is disabled
                }
            }
        });

        packetData = ByteBuffer.allocate(180);
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
        //float frameDuration = renderer.getFrameDuration();

        if (mCube != null) {
            qtoa(q_w,q_x,q_y,q_z);

            float rz = (float)(bank * 180.0 / Math.PI);
            //float rx = 0;
            //float ry = 0;
            //float rz = 0;
            float ry = (float)(attitude * 180.0 / Math.PI);
            float rx = (float)(heading * 180.0 / Math.PI);
//            float rx = (float)(attitude * 180.0 / Math.PI);
//            float ry = (float)(heading * 180.0 / Math.PI);
//            float rz = (float)(bank * 180.0 / Math.PI);
            mCube.setRotation(rx, ry, rz);
            rotation = String.format("pitch : %.2f, yaw: %.2f, roll: %.2f", rx, ry, rx);
            //Log.e("MainActivity", rotation);
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
                            handleQuatPacket(bytes);
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                        }
                );
    }

    private void handleQuatPacket(final byte[] bytes) {

        float w = floatFromDataLittle(Arrays.copyOfRange(bytes, 0, 4)) / divisor_quat;
        float x = floatFromDataLittle(Arrays.copyOfRange(bytes, 4, 8)) / divisor_quat;
        float y = floatFromDataLittle(Arrays.copyOfRange(bytes, 8, 12)) / divisor_quat;
        float z = floatFromDataLittle(Arrays.copyOfRange(bytes, 12, 16)) / divisor_quat;


        if (negate_w) q_w = -w; else q_w = w;
        if (negate_x) q_x = -x; else q_x = x;
        if (negate_y) q_y = -y; else q_y = y;
        if (negate_z) q_z = -z; else q_z = z;

        //Negating y and z seems to work

        String q_str = "Quat: (" + w + ", " + x + ", " + y + ", " + z + ")";
        Log.d("quat", q_str);


        counter += 1;
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


    public void qtoa(double w, double x, double y, double z) {
        double sqw = Math.pow(w, 2);
        double sqx = Math.pow(x, 2);
        double sqy = Math.pow(y, 2);
        double sqz = Math.pow(z, 2);
        double unit = sqx + sqy + sqz + sqw;
        double test = x*y+z*w;
        if (test > 0.499 * unit) {
            heading = 2*atan2(x, w);
            attitude = Math.PI/2;
            bank = 0;
        } else if (test < -0.499*unit) {
            heading = -2*atan2(x, w);
            attitude = -Math.PI/2;
            bank = 0;
        } else {
            heading = atan2(2*y*w-2*x*z, sqx-sqy-sqz+sqw);
            attitude = asin(2*test/unit);
            bank = atan2(2*x*w-2*y*z, -sqx+sqy-sqz+sqw);
        }
    }
    public void qtoa3(double w, double x, double y, double z) {
        double t0 = w*x+y*z;
        double t1 = Math.pow(x, 2) + Math.pow(y, 2);
        double t2 = w*y-z*x;
        double t3 = w*z+x*y; // Test for gimbal lock
        double t4 = Math.pow(y, 2)+Math.pow(z, 2);
        bank = atan2(2*t0, 1-2*t1);
        attitude = asin(2*t2);
        heading = atan2(2*t3, 1-2*t4);

//        if (attitude > (Math.PI/2 - 0.1)) {
//            attitude = attitude - Math.PI;
//        }

//        if (Math.abs(attitude-(Math.PI/2)) < 0.1) {
//            if (attitude > 0) {
//                heading = 2*atan2(x,w);
//                bank = 0;
//                Log.e("MainActiviy", "+90");
//            } else if (attitude < 0) {
//                heading = -2*atan2(x,w);
//                bank = 0;
//                Log.e("MainActiviy", "-90");
//
//            }
//        }
        Log.e("MainActivity", String.format("Attitude: %.2f", attitude));
        Log.e("MainActivity", String.format("Bank: %.2f", bank));
        Log.e("MainActivity", String.format("Heading: %.2f", heading));

//        if (t3 > 0.499) {// && t3 < 0.501) {
//            heading = 2*atan2(x,w);
//            bank = 0;
//        } else if (t3 < -0.499) {//(t3 > -0.501 && t3 < -0.499) {
//            heading = -2*atan2(x,w);
//            bank = 0;
//        }
//
//        heading = heading > Math.PI ? heading -  2*Math.PI : heading;
//        attitude = attitude >  Math.PI ? attitude -  2*Math.PI : attitude;
//        bank = bank >  Math.PI ? bank -  2*Math.PI : bank;
    }

    public void qtoa2(double w, double x, double y, double z) {
        heading = atan2(2*(y*z+w*x), Math.pow(w, 2)-Math.pow(x, 2)-Math.pow(y, 2)+Math.pow(z, 2));
        attitude = asin(-2*(x*z-w*y));
        bank = atan2(2*(x*y+w*z), Math.pow(w, 2)+Math.pow(x, 2)-Math.pow(y, 2)-Math.pow(z, 2));
        if (Math.abs(attitude-(Math.PI/2)) < 0.01) {
            if (attitude > 0) {
                heading = 2*atan2(x,w);
                bank = 0;
            } else if (attitude < 0) {
                heading = -2*atan2(x,w);
                bank = 0;
            }
        }

        heading = heading > Math.PI ? heading -  2*Math.PI : heading;
        attitude = attitude >  Math.PI ? attitude -  2*Math.PI : attitude;
        bank = bank >  Math.PI ? bank -  2*Math.PI : bank;
    }

    private float floatFromDataLittle(byte[] bytes_slice) {
        // Bytes to float (little endian)
        return  java.nio.ByteBuffer.wrap(bytes_slice).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
