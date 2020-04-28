package fr.curie.utils.powell;

import ij.IJ;

/**
 * This class impements Brent's Method of parabolic search for a minimum.
 * After Press WH, Teukolsky SA, Vetterling WT, Flannery BP: Numverical
 * recipes in C++. Cambridge University Press, 2002, Chap10
 * Given points ax, bx, cx, and function, func, such that ax<bx<cx |
 * ax>bx>cx and the corresponding functional values are fb<fa & fb<fc.
 * Output is xmin, the position of the minimum, and fmin, the value
 * at the minimum.
 * I have changed stopping condition.  The stopping condition in Numerical
 * Recipes is relative to the value of xmin, the abcissa of the minimum.
 * This allows searching to the machine precision.  I have kept this
 * condition, but have removed it as an arguement to the call and set it to
 * the machine precision.  I have replaced ZEPS with myZeps.  myZeps a class
 * variable which is initially 1.0e-3 instead of 1.0e-18 for ZEPS.  myZeps
 * is a stopping criteria based on absolute size.  Originally, ZEPS
 * was intended as a stopping criterium when xmin is very close to zero.
 * With tol very small and myZeps larger, this absolute condition will be
 * the usual stopping criteria.  When the search parameters are an affine
 * transform of an image function, this means that Brent will search
 * to roughly myZeps of a pixel for unit vectors, say 1/10th pixel.
 * <BR>
 * func The function to be minimized<BR>
 * ax Smallest point<BR>
 * bx Middle point<BR>
 * cx Largest point<BR>
 * tol Fractional tolerence > root(machine precision) (sp>e-4, dp>3e-8)<BR>
 *
 * @author J. Anthony Parker, MD PhD <J.A.Parker@IEEE.org>
 * @version 16July2004
 */
public class Brent {
    // maximum iterations
    private final static int ITMAX = 100;
    // golden section
    private final static double CGOLD = 0.3819660;
    // machine precision, 1e-15 times 1e-3
    private final static double ZEPS = 1.0e-15 * 1.0e-3;
    // default tolerence 1/10 pixel
    private static double myZeps = 1.0e-3, tol = 3.0e-8;
    private static boolean showSteps = false;
    private Function func;
    private double ax, bx, cx, xmin, fmin = 0.0;
    private int iter;

    public Brent(Function func, double ax, double bx, double cx)
            throws ArithmeticException {
        if (!((ax < bx && bx < cx) || (ax > bx && bx > cx)))
            throw new ArithmeticException("Invalid arguements");
        this.func = func;
        this.ax = ax;
        this.bx = bx;
        this.cx = cx;
        doIt();
    }

    private void doIt() throws ArithmeticException {
        double a, b, d = 0.0, etemp, fu, fv, fw, fx, p, q, r, tol1, tol2,
                u, v, w, x, xm, e = 0.0;
        // make a<c
        a = ax < cx ? ax : cx;
        b = ax > cx ? ax : cx;
        x = w = v = bx;
        fw = fv = fx = func.value(x);
        for (iter = 0; iter < ITMAX; iter++) {
            xm = 0.5 * (a + b);
            // fractional tolerence at x
            // Parker myZeps replaces ZEPS
            tol2 = 2.0 * (tol1 = tol * Math.abs(x) + myZeps);
            // test for done. 1. not done if the interval b-a is more than
            // 4*tol1. 2. done if distance from center is less than 2*tol1
            // minus 1/2 the interval.
            if (Math.abs(x - xm) <= (tol2 - 0.5 * (b - a))) {
                xmin = x;
                fmin = fx;
                return;
            }
            if (Math.abs(e) > tol1) {
                // parabolic interpolation through x, v, w
                r = (x - w) * (fx - fv);
                q = (x - v) * (fx - fw);
                p = (x - v) * q - (x - w) * r;
                q = 2.0 * (q - r);
                if (q > 0.0) p = -p;
                q = Math.abs(q);
                etemp = e;
                e = d;
                // accept if 1 in (a,b) and 2 move less than half the
                // previous step, e.g. converging
                if (Math.abs(p) >= Math.abs(0.5 * q * etemp) || p <= q * (a - x)
                        || p >= q * (b - x))
                    d = CGOLD * (e = (x > xm ? a - x : b - x));
                else {
                    d = p / q;
                    u = x + d;
                    if (u - a < tol2 || b - u < tol2)
                        d = sign(tol1, xm - x);
                }
            } else {
                d = CGOLD * (e = (x >= xm ? a - x : b - x));
            }
            u = (Math.abs(d) >= tol1 ? x + d : x + sign(tol1, d));
            fu = func.value(u);
            if (showSteps) IJ.log("a = " + a + ", b = " + b +
                    ", x = " + x + ", fx = " + fx +
                    ", w = " + w + ", fw = " + fw +
                    "\n        v = " + v + ", fv = " + fv +
                    ", u = " + u + ", fu = " + fu);
            if (fu <= fx) {
                if (u >= x) a = x;
                else b = x;
                v = w;
                w = x;
                x = u;
                fv = fw;
                fw = fx;
                fx = fu;
            } else {
                if (u < x) a = u;
                else b = u;
                if (fu <= fw || w == x) {
                    v = w;
                    w = u;
                    fv = fw;
                    fw = fu;
                } else if (fu <= fv || v == x || v == w) {
                    v = u;
                    fv = fu;
                }
            }
        }
        xmin = x;
        fmin = fx;
        throw new ArithmeticException("Too many iterations in Brent");
    }

    private double sign(double a, double b) {
        return b >= 0.0 ? (a >= 0.0 ? a : -a) : (a >= 0.0 ? -a : a);
    }

    /**
     * Minimum accuracy of search as absoulte value; it should be set prior
     * to instantiation.
     */
    public static void setZeps(double newZeps) {
        myZeps = Math.max(newZeps, ZEPS);
        return;
    }

    /**
     * Minimum relative accuracy of search; it should be set prior to
     * instantiation.
     */
    public static void setTol(double tol) {
        Brent.tol = tol;
    }

    /**
     * abcissa of the minimum
     */
    public double getXmin() {
        return xmin;
    }

    /* a & b bracket the minimum.
      * x is the point with the smallest value, f(x), so far
      * w is the point with the second smallest value, f(w)
      * v is the previous value of w
      * u is the point at which the function was most recently evaluated
      * xm is midpoint between a and b
      */

    /**
     * ordinate of the minimum, f(xMin)
     */
    public double getFmin() {
        return fmin;
    }

    // Compiler should in-line this routine

    /**
     * number of iterations
     */
    public int getIter() {
        return iter;
    }

}    // end of Brent