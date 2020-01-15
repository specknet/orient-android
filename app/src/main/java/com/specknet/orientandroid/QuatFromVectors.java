package com.specknet.orientandroid;

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

public class QuatFromVectors {
    RealMatrix m;
    Vector3D v1;
    Vector3D v2;
    Vector3D v3;
    Quaternion q;

    QuatFromVectors(Vector3D v1, Vector3D v2, Vector3D v3) {
        this.m = new Array2DRowRealMatrix();
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.q = new Quaternion(0,0,0,1);

        this.m.createMatrix(3,3);
        this.m.setRow(0, this.v1.toArray());
        this.m.setRow(1, this.v2.toArray());
        this.m.setRow(2, this.v3.toArray());

        
    }

    Quaternion getQuat() {
        return q;
    }
}
