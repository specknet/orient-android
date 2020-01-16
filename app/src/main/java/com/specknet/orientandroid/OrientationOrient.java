package com.specknet.orientandroid;

import android.util.Log;

import com.kircherelectronics.fsensor.BaseFilter;

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention;
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder;


public class OrientationOrient extends BaseFilter {

    private static final String TAG = OrientationGyroscope.class.getSimpleName();
    private static final float NS2S = 1.0f / 1000000000.0f;
    //private static final float EPSILON = 0.000000001f;
    private Quaternion rotationVectorImusim;
    private float[] output;
    private long timestamp = 0;
    private OrientCF orientCF;

    /**
     * Initialize a singleton instance.
     */
    public OrientationOrient() {
        orientCF = null;
        output = new float[4];
    }

    @Override
    public float[] getOutput() {
        return output;
    }

    /**
     * Calculate the fused orientation of the device.
     * @param gyroscope the gyroscope measurements.
     * @param timestamp the gyroscope timestamp
     * @return An orientation vector -> @link SensorManager#getOrientation(float[], float[])}
     */
    public float[] calculateOrientation(float[] gyroscope, float dT, float[] acceleration, float[] magnetic) {
        if (isBaseOrientationSet()) {

            if (this.timestamp != 0 || true) {
                //final float dT = (timestamp - this.timestamp) * NS2S;
                rotationVectorImusim = orientCF.update(acceleration, magnetic, gyroscope, dT, 10.0f, 0.5f);

                Rotation rotation = new Rotation(rotationVectorImusim.getQ0(), rotationVectorImusim.getQ1(), rotationVectorImusim.getQ2(),
                        rotationVectorImusim.getQ3(), true);

                /*
                try {
                    output = doubleToFloat(rotation.getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR));
                } catch(Exception e) {
                    Log.d(TAG, "", e);
                }
                */

                output = doubleToFloat(new double[]{rotation.getQ0(),rotation.getQ1(),rotation.getQ2(),rotation.getQ3()});
            }

            this.timestamp = timestamp;

            return output;
        } else {
            throw new IllegalStateException("You must call setBaseOrientation() before calling calculateFusedOrientation()!");
        }
    }

    /**
     * Set the base orientation (frame of reference) to which all subsequent rotations will be applied.
     * <p>
     * To initialize to an arbitrary local frame of reference pass in the Identity Quaternion. This will initialize the base orientation as the orientation the device is
     * currently in and all subsequent rotations will be relative to this orientation.
     * <p>
     * To initialize to an absolute frame of reference (like Earth frame) the devices orientation must be determine from other sensors (such as the acceleration and magnetic
     * sensors).
     * @param baseOrientation The base orientation to which all subsequent rotations will be applied.
     */
    public void setBaseOrientation(Quaternion baseOrientation) {
        orientCF = new OrientCF(baseOrientation, 1.0f, 1.0f);
    }

    public void reset() {
        rotationVectorImusim = null;
        timestamp = 0;
    }

    public boolean isBaseOrientationSet() {
        return orientCF != null;
    }

    private static float[] doubleToFloat(double[] values) {
        float[] f = new float[values.length];

        for(int i = 0; i < f.length; i++){
            f[i] = (float) values[i];
        }

        return f;
    }
}