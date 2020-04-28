package fr.curie.utils.powell;


/**
 * This class represents a matrix and the associated matrix arithmetic.
 * rows		i [0 to r-1]
 * columns	j [0 to c-1]
 * 3 x 3 transformation matrices are define in terms of:
 * z [2]
 * /
 * (0,0)-x [0]
 * |
 * y [1]
 * pitch, x-axis, y,z-plane, top-out:bottom-in
 * yaw, y-axis, x,z-plane, left-in:right-out
 * roll, z-axis, x,y-plane, clockwise
 * <p/>
 * Top-level nested classes defined below: PitchMatrix, YawMatrix,
 * RollMatrix, MirrorMatrix, ScaleMatrix
 *
 * @author J. Anthony Parker, MD PhD <J.A.Parker@IEEE.org>
 * @version 31August2004
 */
public class Matrix implements java.io.Serializable {
    double[][] m;
    int r, c;
    // The following 3 routines are translated from Numerical Recipes
    // variables used for matrix inversion in following 3 routines
    private double[][] lu = null;
    private int[] indx = null;

    public Matrix(int rows, int columns) {
        m = new double[rows][columns];
        r = rows;
        c = columns;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                if (i == j)
                    m[i][j] = 1.0;
                else
                    m[i][j] = 0.0;
    }

    public Matrix(Matrix in) {
        m = new double[in.r][in.c];
        r = in.r;
        c = in.c;
        for (int i = 0; i < r; i++)
            System.arraycopy(in.m[i], 0, m[i], 0, c);
    }

    public Matrix(double[][] in) {
        r = in.length;
        c = in[0].length;
        m = new double[r][c];
        for (int i = 0; i < r; i++)
            System.arraycopy(in[i], 0, m[i], 0, c);
    }

    // out = m*in
    // out = null if columns of m not equal to rows of in

    boolean isIdentity() {
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                if (i == j) {
                    if (m[i][j] != 1.0) return false;
                } else {
                    if (m[i][j] != 0.0) return false;
                }
        return true;
    }

    // out = m*in
    // out = null if columns of m not equal to len of in

    boolean equals(Matrix in) {
        if (r != in.r || c != in.c)
            return false;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                if (m[i][j] != in.m[i][j])
                    return false;
        return true;
    }

    /* unused code
     // out = m+in
     // out = null if columns and rows of m not equal to in
     Matrix add(Matrix in) {
         if(c!=in.c||r!=in.r) return null;
         Matrix out = new Matrix(r, c);
         for(int i=0; i<r; i++)
             for(int j=0; j<c; j++)
                 out.m[i][j] = this.m[i][j]+in.m[i][j];
         return out;
     }
     */

    Matrix multiply(Matrix in) {
        if (c != in.r) return null;
        Matrix out = new Matrix(r, in.c);
        for (int i = 0; i < r; i++)
            for (int j = 0; j < in.c; j++) {
                out.m[i][j] = 0.0;
                for (int k = 0; k < c; k++)
                    out.m[i][j] += this.m[i][k] * in.m[k][j];
            }
        return out;
    }

    Vector multiply(Vector inVector) {
        double[] in = inVector.value();
        if (c != in.length) return null;
        double[] out = new double[r];
        for (int i = 0; i < r; i++) {
            out[i] = 0.0;
            for (int k = 0; k < c; k++)
                out[i] += this.m[i][k] * in[k];
        }
        return new Vector(out);
    }
    // private int d;	// from Numerical Recipes, but not used

    // Numerical Recipes 2.4
    // returns a Matrix which is the inverse of this or null if m is not square

    Matrix inverse() {
        if (r != c) return null;        // must be square
        Matrix mInv = new Matrix(r, c);
        double[] b = new double[r];
        if (!ludcmp()) return null;    // LU decomposition
        for (int j = 0; j < c; j++) {
            for (int i = 0; i < r; i++)
                b[i] = mInv.m[i][j];
            if (!lubksb(b)) return null;
            for (int i = 0; i < r; i++)
                mInv.m[i][j] = b[i];
        }
        return mInv;
    }

    // Numerical Recipes 2.3 LU Decomposition
    // Lower triangular + upper triangular, LU, decomposition of m
    // called only by inverse()

    private boolean ludcmp() {
        if (r != c) return false;        // must be square
        final double tiny = 1e5 * Double.MIN_VALUE;
        lu = new double[r][c];
        for (int i = 0; i < r; i++)
            System.arraycopy(m[i], 0, lu[i], 0, c);
        indx = new int[r];
        double aamax, sum, dum;
        double[] vv = new double[r];
        int iMax = 0;

        // d=1;		// row interchanges; not used
        for (int i = 0; i < r; i++) {
            aamax = 0.0;
            for (int j = 0; j < c; j++)
                if (Math.abs(lu[i][j]) > aamax) aamax = Math.abs(lu[i][j]);
            if (aamax == 0.0) return false;        // singular
            vv[i] = 1.0 / aamax;
        }
        for (int j = 0; j < c; j++) {
            if (j > 0) {
                for (int i = 0; i < j; i++) {
                    sum = lu[i][j];
                    if (i > 0) {
                        for (int k = 0; k < i; k++)
                            sum = sum - lu[i][k] * lu[k][j];
                        lu[i][j] = sum;
                    }
                }
            }
            aamax = 0.0;
            for (int i = j; i < r; i++) {
                sum = lu[i][j];
                if (j > 0) {
                    for (int k = 0; k < j; k++)
                        sum = sum - lu[i][k] * lu[k][j];
                    lu[i][j] = sum;
                }
                dum = vv[i] * Math.abs(sum);
                if (dum >= aamax) {
                    iMax = i;
                    aamax = dum;
                }
            }
            if (j != iMax) {
                for (int k = 0; k < r; k++) {
                    dum = lu[iMax][k];
                    lu[iMax][k] = lu[j][k];
                    lu[j][k] = dum;
                }
                // d = -d;
                vv[iMax] = vv[j];
            }
            indx[j] = iMax;
            if (j != c) {
                if (lu[j][j] == 0.0) lu[j][j] = tiny;
                dum = 1.0 / lu[j][j];
                for (int i = j + 1; i < r; i++)
                    lu[i][j] = lu[i][j] * dum;
            }
        }
        if (lu[r - 1][c - 1] == 0.0) lu[r - 1][c - 1] = tiny;
        return true;
    }

    // Numerical Recipes 2.3
    // called only by inverse()
    // must be called after ludcmp()

    private boolean lubksb(double[] b) {
        if (lu == null) return false;
        int ii;
        double sum;

        ii = -1;
        for (int i = 0; i < r; i++) {
            sum = b[indx[i]];
            b[indx[i]] = b[i];
            if (ii >= 0)
                for (int j = ii; j < i; j++)
                    sum = sum - lu[i][j] * b[j];
            else if (sum != 0.0)
                ii = i;
            b[i] = sum;
        }
        for (int i = r - 1; i >= 0; i--) {
            sum = b[i];
            if (i < r - 1)
                for (int j = i + 1; j < c; j++)
                    sum = sum - lu[i][j] * b[j];
            b[i] = sum / lu[i][i];
        }
        return true;
    }

    public String toString() {
        return toString(3);
    }

    public String toString(int digits) {
        if (r == 0 || c == 0) return "Zero dimension.\n";
        double rounder = Math.pow(10.0, (double) digits);
        String s = "";
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                s = s + "m(" + Integer.toString(i) + "," + Integer.toString(j) + ") = " +
                        Double.toString(Math.rint(rounder * m[i][j]) / rounder) + ", ";
            }
            s = s.substring(0, s.length() - 2) + "\n";
        }
        return s;
    }

/**
 * The following are nested top level classes.
 * They may be imported with: import Align3TP.Matrix.*;
 *
 * rows		i [0 to r-1]
 * columns	j [0 to c-1]
 */

    /**
     * Three by three matrix which scales each axis by scale[i].
     */
    static class ScaleMatrix extends Matrix {
        ScaleMatrix(double[] scale) {
            super(scale.length, scale.length);
            for (int i = 0; i < scale.length; i++)
                m[i][i] = scale[i];

        }
    }

    /**
     * Three by three matrix which mirrors around axis.
     */
    static class MirrorMatrix extends Matrix {
        MirrorMatrix(String axis) {
            super(3, 3);
            if (axis.equalsIgnoreCase("x"))
                m[0][0] = -1.0;
            else if (axis.equalsIgnoreCase("y"))
                m[1][1] = -1.0;
            else if (axis.equalsIgnoreCase("z"))
                m[2][2] = -1.0;

        }
    }

/**
 * Matrices which implement rotations
 * rows		i [0 to r-1]
 * columns	j [0 to c-1]
 *
 *		z [2]
 *     /
 * (0,0)-x [0]
 *    |
 *    y [1]
 * pitch, x-axis, y,z-plane, top-out:bottom-in
 * yaw, y-axis, x,z-plane, left-in:right-out
 * roll, z-axis, x,y-plane, clockwise
 */

    /**
     * Three by three matrix which impliments pitch,
     * a rotation about the x-axis.
     */
    public static class PitchMatrix extends Matrix {
        public PitchMatrix(double pitch) {
            super(3, 3);
            m[0][0] = 1.0;
            m[0][1] = 0.0;
            m[0][2] = 0.0;
            m[1][0] = 0.0;
            m[1][1] = Math.cos(pitch);
            m[1][2] = Math.sin(pitch);
            m[2][0] = 0.0;
            m[2][1] = -Math.sin(pitch);
            m[2][2] = Math.cos(pitch);

        }
    }

    /**
     * Three by three matrix which impliments yaw,
     * a rotation about the y-axis.
     */
    public static class YawMatrix extends Matrix {
        public YawMatrix(double yaw) {
            super(3, 3);
            m[0][0] = Math.cos(yaw);
            m[0][1] = 0.0;
            m[0][2] = -Math.sin(yaw);
            m[1][0] = 0.0;
            m[1][1] = 1.0;
            m[1][2] = 0.0;
            m[2][0] = Math.sin(yaw);
            m[2][1] = 0.0;
            m[2][2] = Math.cos(yaw);

        }
    }

    /**
     * Three by three matrix which impliments roll,
     * a rotation about the z-axis.
     */
    public static class RollMatrix extends Matrix {
        public RollMatrix(double roll) {
            super(3, 3);
            m[0][0] = Math.cos(roll);
            m[0][1] = Math.sin(roll);
            m[0][2] = 0.0;
            m[1][0] = -Math.sin(roll);
            m[1][1] = Math.cos(roll);
            m[1][2] = 0.0;
            m[2][0] = 0.0;
            m[2][1] = 0.0;
            m[2][2] = 1.0;

        }
    }

/* unused code
// roll, yaw, pitch matrix
static class RYPMatrix extends Matrix {	// debugging
	RYPMatrix(double pitch, double yaw, double roll) {
		super(3,3);
		double cosp = Math.cos(pitch), sinp = Math.sin(pitch),
				cosy = Math.cos(yaw), siny = Math.sin(yaw),
				cosr = Math.cos(roll), sinr = Math.sin(roll);
		m[0][0] = cosr*cosy;
		m[0][1] = cosr*siny*sinp+sinr*cosp;
		m[0][2] = -cosr*siny*cosp+sinr*sinp;
		m[1][0] = -sinr*cosy;
		m[1][1] = -sinr*siny*sinp+cosr*cosp;
		m[1][2] = sinr*siny*cosp+cosr*sinp;
		m[2][0] = siny;
		m[2][1] = -cosy*sinp;
		m[2][2] = cosy*cosp;
		return;
	}
}
*/

    /**
     * Shearing matrix
     * 1   Syx  Szx
     * Sxy   1   Szy
     * Sxz  Syz   1
     */
    public static class ShearMatrix extends Matrix {
        public ShearMatrix(double sxy, double sxz, double syx,
                           double syz, double szx, double szy) {
            super(3, 3);
            m[1][0] = sxy;
            m[2][0] = sxz;
            m[0][1] = syx;
            m[2][1] = syz;
            m[0][2] = szx;
            m[1][2] = szy;

        }
    }

}    // end of Matrix
