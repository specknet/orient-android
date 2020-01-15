package com.specknet.orientandroid;

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public class GramSchmidt {

    GramSchmidt(){}

    Quaternion process(Vector3D g, Vector3D m) {
        Vector3D z = g.scalarMultiply(1.0 / g.getNorm());
        Vector3D x = m.subtract(z.scalarMultiply(m.dotProduct(z)));
        x = x.scalarMultiply(1.0 / x.getNorm());
        Vector3D y = z.crossProduct(x);

        return new QuatFromVectors(x, y, z).getQuat();
    }
}
