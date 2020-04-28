package fr.curie.utils.powell;

import ij.IJ;
import ij.gui.PlotWindow;

/**
 * This class impements Powell's minimization method which is quadratically
 * convergent. After Press WH, Teukolsky SA, Vetterling WT, Flannery BP:
 * Numverical recipes in C++. Cambridge University Press, 2002, Chap. 10 Given
 * a function, func, a starting point, p, a tolerence, ftol, and a set of
 * directions, xi, Powell's method returns a new point, p, the value of the
 * function at p, fret, the new set of directions, xi, and the number of
 * iterations, iter, performed. If function doesn't decrease by ftol during one
 * iteration, then optimization is complete. Powell uses Brent to find minimum
 * along a line. Note Brent.setTol and especially Brent.setZeps. Powell's
 * method uses direction vectors which may become very small in order to be
 * able to locate the minimum very accurately. In applications using images,
 * accuracy much less than the pixel size doesn't make sense. By default this
 * vesrion uses unit direction vectors, but this may be changed back to
 * Numerical Recipes version using the static method
 * Powell.setUnitDirectionVectors.
 *
 * @author J. Anthony Parker, MD PhD <J.A.Parker@IEEE.org>
 * @version 15July2004
 * @created 27 octobre 2008
 */


public class Powell {
    // maximum number of iterations
    private final static int ITMAX = 200;
    private final static double TINY = 1.0e-25;
    // minimum new direction vector length if unitDirectionVectors==true
    private final static double MIN_VECTOR_LENGTH = 1.0e-3;
    private static boolean unitDirectionVectors = true;
    private static boolean showLineResults = false;
    private Function func;
    private double[] p;
    private double[][] xi;
    private double ftol, fret;
    private int n, iter;


    /**
     * This constructor starts with unit vectors, xi.
     * <p/>
     * *@param  func  function to be minimized
     *
     * @param p    starting point
     * @param ftol tolerance > machine precision (sp>3e-8, dp>e-15)
     * @param func Description of the Parameter
     * @throws IllegalArgumentException Description of the Exception
     * @throws ArithmeticException      Description of the Exception
     */
    public Powell(Function func, double[] p, double ftol)
            throws IllegalArgumentException, ArithmeticException {
        this.p = p;
        n = p.length;
        if (n != func.length()) {
            throw new IllegalArgumentException("Incompatible dimensions");
        }
        xi = new double[n][n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                xi[j][i] = i == j ? 1.0 : 0.0;
            }
        }
        this.func = func;
        this.ftol = ftol == 0.0 ? 1.0e-8 : ftol;
        doIt();
    }

    /**
     * Description of the Method
     *
     * @throws ArithmeticException Description of the Exception
     */
    private void doIt() throws ArithmeticException {
        int ibig;
        double del;
        double fp;
        double fptt;
        double t;
        double[] pt = new double[n];
        double[] ptt = new double[n];
        double[] xit = new double[n];
        fret = func.value(new Vector(p));
        System.arraycopy(p, 0, pt, 0, n);
        for (iter = 1; true; iter++) {
            fp = fret;
            ibig = 0;
            del = 0.0;
            // minimize in all directions, p records the change
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    xit[j] = xi[j][i];
                }
                fptt = fret;
                fret = lineMinimization(func, p, xit);
                if (fptt - fret > del) {
                    // find direction with largest change
                    del = fptt - fret;
                    ibig = i + 1;
                }
            }
            // fractional change in one iteration, fp-fret/|fret|, is
            // less than the tolerence
            if (2.0 * (fp - fret) <= ftol * (Math.abs(fp) + Math.abs(fret)) + TINY) {
                return;
            }
            // normal termination
            if (iter == ITMAX) {
                throw new ArithmeticException(
                        "Powell exceeding maximum iterations");
            }
            for (int j = 0; j < n; j++) {
                ptt[j] = 2.0 * p[j] - pt[j];
                xit[j] = p[j] - pt[j];
                pt[j] = p[j];
            }
            fptt = func.value(new Vector(ptt));
            if (fptt < fp) {
                t = 2.0 * (fp - 2.0 * fret + fptt) * sqr(fp - fret - del) -
                        del * sqr(fp - fptt);
                if (t < 0.0) {
                    fret = lineMinimization(func, p, xit);
                    // Parker option
                    if (unitDirectionVectors) {
                        double z = 0.0;
                        for (int j = 0; j < n; j++) {
                            z += xit[j] * xit[j];
                        }
                        z = Math.sqrt(z);
                        if (z > MIN_VECTOR_LENGTH) {
                            z = 1.0 / z;
                            for (int j = 0; j < n; j++) {
                                xi[j][ibig - 1] = xi[j][n - 1];
                                xi[j][n - 1] = xit[j] * z;
                            }
                        }
                    } else {
                        // from Numerical Recipies
                        for (int j = 0; j < n; j++) {
                            xi[j][ibig - 1] = xi[j][n - 1];
                            xi[j][n - 1] = xit[j];
                        }
                    }
                }
                if (showLineResults) {
                    IJ.log("Basis vector = " + new Matrix(xi).toString(15));
                }
            }
        }
        // end of iteration loop
    }

    /**
     * Description of the Method
     *
     * @param func Description of the Parameter
     * @param p    Description of the Parameter
     * @param xit  Description of the Parameter
     * @return Description of the Return Value
     * @throws ArithmeticException Description of the Exception
     */
    private double lineMinimization(Function func, double[] p, double[] xit)
            throws ArithmeticException {
        func.setP(new Vector(p));
        func.setXi(new Vector(xit));
        BracketMin bm = new BracketMin(func, 0.0, 1.0);
        Brent br = new Brent(func, bm.getAx(), bm.getBx(), bm.getCx());
        double xmin = br.getXmin();
        for (int j = 0; j < n; j++) {
            xit[j] *= xmin;
            p[j] += xit[j];
        }
        if (showLineResults) {
            // for debugging
            func.setP(new Vector(p));
            int nEval = func.getNEval();
            PlotWindow pw = func.plot(new double[][]{xit}, -10f, 10f, 32).show();
            func.setNEval(nEval);
            IJ.log("Evaluations = " + nEval +
                    ", BraketMin iterations = " + bm.getIter() +
                    ", Brent iterations = " + br.getIter() +
                    "\nBracketMin limits a = " + bm.getAx() +
                    ", b = " + bm.getBx() +
                    ", C = " + bm.getCx() +
                    "\nxmin = " + xmin + ", Fmin = " + br.getFmin() +
                    "\nNew point = " +
                    new Vector(p).toString(15) + "New direction = " +
                    new Vector(xit).toString(15));
            if (!IJ.showMessageWithCancel("From Powel", "Coninue?\n" +
                    "(Cancel throws IllegalArguementException)")) {
                pw.close();
                throw new IllegalArgumentException();
            }
            pw.close();
        }
        return br.getFmin();
    }

    /**
     * Description of the Method
     *
     * @param x Description of the Parameter
     * @return Description of the Return Value
     */
    private double sqr(double x) {
        return x * x;
    }


    /**
     * Construtor which uses initial directions xi; this construtor typically used
     * when unitDirectionVectors==false.
     *
     * @param func Description of the Parameter
     * @param p    starting point
     * @param ftol tolerance > machine precision (sp>3e-8, dp>e-15)
     * @param xi   starting set of vector (columns) directions
     * @throws IllegalArgumentException Description of the Exception
     * @throws ArithmeticException      Description of the Exception
     */
    public Powell(Function func, double[] p, double[][] xi, double ftol)
            throws IllegalArgumentException, ArithmeticException {
        this.p = p;
        n = p.length;
        if (n != xi.length || n != func.length()) {
            throw new IllegalArgumentException("Incompatible dimensions");
        }
        for (int j = 0; j < n; j++) {
            if (n != xi[j].length) {
                throw new IllegalArgumentException("Incompatible dimensions");
            }
        }
        this.xi = xi;
        this.func = func;
        this.ftol = ftol;
        doIt();
    }

    /**
     * Use unit length directon vectors, especially in the case of image
     * functions.
     *
     * @param unitDirectionVectors The new unitDirectionVectors value
     */
    public static void setUnitDirectionVectors(boolean unitDirectionVectors) {
        Powell.unitDirectionVectors = unitDirectionVectors;
    }

    /**
     * Show results for each line minimization; used during debugging; note that
     * this variable should be set before object instantiation.
     *
     * @param showLineResults The new showLineResults value
     */
    public static void setShowLineResults(boolean showLineResults) {
        Powell.showLineResults = showLineResults;
    }

    /**
     * p becomes the minimum point.
     *
     * @return The p value
     */
    public double[] getP() {
        return p;
    }

    /**
     * Value of the function at p.
     *
     * @return The fret value
     */
    public double getFret() {
        return fret;
    }


/*
    This routine is considerably different from Numerical Recipes
    since the Function class takes care of switching to 1D.  The point,
    p, and the direction, xit, are set in func, then func is passed
    to BracketMin and Brent.
    Numerical Recipes uses arguement, xi, but it passes xit and uses
    xi for a matrix above; therefore, I use xit in this subroutine.
    @param func function being minimized
    @param p starting point -> min point
    @param xit minimization direction
    @return value at the mimimum point, p
  */

    /**
     * Set of direction vectors at minimum.
     *
     * @return The xi value
     */
    public double[][] getXi() {
        return xi;
    }

    /**
     * Number or iterations.
     *
     * @return The iter value
     */
    public int getIter() {
        return iter;
    }


}
