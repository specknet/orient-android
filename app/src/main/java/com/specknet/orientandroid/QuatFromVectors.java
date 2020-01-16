package com.specknet.orientandroid;

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import static java.lang.Math.sqrt;

public class QuatFromVectors {
    RealMatrix m;
    Vector3D v1;
    Vector3D v2;
    Vector3D v3;
    Quaternion q;
    double w;
    double x;
    double y;
    double z;

    QuatFromVectors(Vector3D v1, Vector3D v2, Vector3D v3) {
        this.m = new Array2DRowRealMatrix(3,3);
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.q = new Quaternion(0,0,0,1);

        this.m.setRow(0, this.v1.toArray());
        this.m.setRow(1, this.v2.toArray());
        this.m.setRow(2, this.v3.toArray());

        setQuatFromMatrix();
    }

    Quaternion getQuat() {
        return q;
    }

    void setQuatFromMatrix(){
        // Set the quaternion to be eqivalent to a given 3x3 rotation matrix.
        double t = m.getEntry(0,0) + m.getEntry(1,1) + m.getEntry(2,2);
        if (t > 0) {
            double w2 = sqrt(t + 1.0);
            this.w = w2 / 2.0;
            this.x = (m.getEntry(2, 1) - m.getEntry(1, 2)) / (2.0 * w2);
            this.y = (m.getEntry(0, 2) - m.getEntry(2, 0)) / (2.0 * w2);
            this.z = (m.getEntry(1, 0) - m.getEntry(0, 1)) / (2.0 * w2);
        }
        else {
            double t2 = m.getEntry(0, 0) - m.getEntry(1, 1) - m.getEntry(2, 2);
            if (t2 > 0) {
                double x2 = sqrt(t2 + 1.0);
                this.w = (m.getEntry(2, 1) - m.getEntry(1, 2)) / (2.0 * x2);
                this.x = x2 / 2.0;
                this.y = (m.getEntry(1, 0) + m.getEntry(0, 1)) / (2.0 * x2);
                this.z = (m.getEntry(0, 2) + m.getEntry(2, 0)) / (2.0 * x2);
            }
            else {
                double t3 = m.getEntry(1, 1) - m.getEntry(0, 0) - m.getEntry(2, 2);
                if (t3 > 0) {
                    double y2 = sqrt(t + 1);
                    this.w = (m.getEntry(0, 2) - m.getEntry(2, 0)) / (2.0 * y2);
                    this.x = (m.getEntry(1, 0) + m.getEntry(0, 1)) / (2.0 * y2);
                    this.y = y2 / 2.0;
                    this.z = (m.getEntry(1, 2) + m.getEntry(2, 1)) / (2.0 * y2);
                }
                else {
                    double z2 = sqrt(m.getEntry(2, 2) - m.getEntry(0, 0) - m.getEntry(1, 1) + 1);
                    this.w = (m.getEntry(1, 0) - m.getEntry(0, 1)) / (2.0 * z2);
                    this.x = (m.getEntry(0, 2) + m.getEntry(2, 0)) / (2.0 * z2);
                    this.y = (m.getEntry(1, 2) + m.getEntry(2, 1)) / (2.0 * z2);
                    this.z = z2 / 2.0;
                }
            }
        }

        //update quaternion with new components
        this.q = new Quaternion(this.w, this.x, this.y, this.z);
    }
}
