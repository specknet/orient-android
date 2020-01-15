package com.specknet.orientandroid;

/*
        Implementation of the complementary filter used on the Orient IMU.

        See A. Young, M. Ling, and D. K. Arvind. "Orient-2: A Realtime Wireless
        Posture Tracking System using Local Orientation Estimation". in Proc.
        4th Workshop on Embedded Network Sensors, pp 53-57. ACM, 2007.

@ivar vectorObservation: L{TimeSeries} of vector observation results.
        """

        def __init__(self, initialRotation, k, aT, **kwargs):
        """
@param aT: acceleration threshold (float). The correction step will
        only be performed if the condition M{abs(norm(accel) - 1) <= aT}
        is met.
        """

        self._k = float(k)
        self._aT = float(aT)
        self.qHat = initialRotation.copy()
        self._vectorObservation = vector_observation.GramSchmidt()

        def update(self, accel, mag, gyro, dt, k=None, aT=None):
        dotq = 0.5 * self.qHat * Quaternion(0,*gyro)
        self.qHat += dotq * dt

        if aT is None:
        _aT = self._aT
        else:
        _aT = float(aT)

        if k is None:
        _k = self._k
        else:
        _k = float(k)

        if abs(vectors.norm(accel) - 1) < _aT:
        #if False:
        qMeas = self._vectorObservation(-accel, mag)
        if self.qHat.dot(qMeas) < 0:
        qMeas.negate()
        qError = qMeas - self.qHat
        self.qHat += (1/_k) * dt * qError
        #else:
        #print "hello"
        #qMeas = Quaternion.nan()

        self.qHat.normalise()
        return self.qHat
*/

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3DFormat;

public class OrientCF {
    private float _k;
    private float _aT;
    private Quaternion qHat;
    private GramSchmidt _vectorObservation;

    OrientCF(Quaternion initialRotation, float k, float aT) {
        this._k = k;
        this._aT = aT;
        this.qHat = initialRotation;
        this._vectorObservation = new GramSchmidt();
    }

    Quaternion update(float[] accel, float[] mag, float[] gyro, float dt, float k, float aT) {
        Quaternion dotq = this.qHat.multiply(new Quaternion(0.0, floatToDouble(gyro))).multiply(0.5);
        this.qHat.add(dotq.multiply(dt));
        this._aT = aT;
        this._k = k;

        Vector3D accel_v3d = new Vector3D(floatToDouble(accel));
        Vector3D mag_v3d = new Vector3D(floatToDouble(mag));

        if (Math.abs(accel_v3d.getNorm() - 1) < this._aT) {
            Quaternion qMeas = this._vectorObservation.process(accel_v3d.negate(), mag_v3d);
            if (this.qHat.dotProduct(qMeas) < 0.0) {
                qMeas.multiply(-1.0);
                Quaternion qError = qMeas.subtract(qHat);
                this.qHat = this.qHat.add(qError.multiply((1.0 / this._k) * dt));
            }
        }
        this.qHat.normalize();
        return this.qHat;
    }

    private static double[] floatToDouble(float[] values) {
        double[] d = new double[values.length];

        for(int i = 0; i < d.length; i++){
            d[i] = (double) values[i];
        }

        return d;
    }
}


