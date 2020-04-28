package fr.curie.utils.powell;

/**
 * Represents a vector.
 *
 * @author J. Anthony Parker, MD PhD <J.A.Parker@IEEE.org>
 * @version 30August2002
 * @modified 28 novevember 2008 by C�dric Messaoudi
 */

public class Vector {
    private double[] v;

    public Vector(int length) {
        v = new double[length];
        for (int i = 0; i < length; i++)
            v[i] = 0.0;
    }

    public Vector(Vector in) {
        v = new double[in.length()];
        System.arraycopy(in.v, 0, v, 0, v.length);
    }

    int length() {
        return v.length;
    }

    public Vector(double[] v) {
        this.v = new double[v.length];
        System.arraycopy(v, 0, this.v, 0, v.length);
    }

    Vector add(Vector in) {
        if (v.length != in.length()) return null;
        Vector out = new Vector(v.length);
        for (int i = 0; i < v.length; i++)
            out.v[i] = this.v[i] + in.v[i];
        return out;
    }

    // out = v+in
    // out = null if len of v not equal to len of in

    Vector sub(Vector in) {
        if (v.length != in.length()) return null;
        Vector out = new Vector(v.length);
        for (int i = 0; i < v.length; i++)
            out.v[i] = this.v[i] - in.v[i];
        return out;
    }

    // out = v-in
    // out = null if len of v not equal to len of in

    Vector minus() {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++)
            out[i] = -v[i];
        return new Vector(out);
    }

    Vector multiply(double d) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++)
            out[i] = d * v[i];
        return new Vector(out);
    }

    Vector cross(Vector in) throws IllegalArgumentException {
        double[] inV = in.value();
        if (inV.length < 3 || v.length < 3)
            throw new IllegalArgumentException("Illegal dimension");
        double[] out = new double[3];
        out[0] = v[1] * inV[2] - v[2] * inV[1];
        out[1] = v[2] * inV[0] - v[0] * inV[2];
        out[2] = v[0] * inV[1] - v[1] * inV[0];
        return new Vector(out);
    }

    double[] value() {
        return v;
    }

    Vector unit() {
        double[] out = new double[v.length];
        double len = vectorLength();
        for (int i = 0; i < v.length; i++)
            out[i] = v[i] / len;
        return new Vector(out);
    }

    double vectorLength() {
        double lenSq = 0.0;
        for (double aV : v) lenSq += aV * aV;
        return Math.sqrt(lenSq);
    }

    double innerAngle(Vector in) {
        return Math.acos(this.multiply(in) /
                (this.vectorLength() * in.vectorLength()));
    }

    double multiply(Vector in) {
        if (v.length != in.length()) return Double.NaN;
        double x = 0.0;
        for (int i = 0; i < v.length; i++)
            x += this.v[i] * in.v[i];
        return x;
    }

    public String toString() {
        return toString(3);
    }

    public String toString(int digits) {
        if (v.length == 0) return "Zero dimension.\n";
        double rounder = Math.pow(10.0, (double) digits);
        String s = "";
        for (int i = 0; i < v.length; i++)
            s = s + "v(" + Integer.toString(i) + ") = " +
                    Double.toString(Math.rint(rounder * v[i]) / rounder) + ", ";
        s = s.substring(0, s.length() - 2) + "\n";
        return s;
    }

} // end of the Vector class
