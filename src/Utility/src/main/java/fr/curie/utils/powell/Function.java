package fr.curie.utils.powell;

import ij.IJ;
import ij.gui.Plot;


/**
 * This abstract class provides access using a one dimensional variable to a
 * function with multiple independent variables. It can be used during
 * optimization to follow a particular direction in the independant variable
 * space. The starting point is given by Vector, p. One dimensional search is
 * in the direction given by Vector, xi, for a distance given by x*xi.
 * Subclasses provide calculation of the value of the function. For
 * registration, a subclass could implement a similarity measure between two
 * images. The minimize variable determines if the optimization is a
 * minimization or a maximization. Subclasses must provide: 1 A constructor 2 A
 * method to evaluate the function for independent variables, xt. abstract
 * protected double eval(double[] xt); 3 The number of independent variables,
 * xt.length. abstract protected int length(); Known subclasses: Well,
 * Polynomial, SliceFunction, StackFunction Known users: Objects of this class
 * are passed to Powell, ParabolicFit and SimulatedAnnealing during
 * optimization using those algorithms. see Press WH, Teukolsky SA, Vetterling
 * WT, Flannery: Numerical recipes in C++. Cambridge University Press,
 * Cambridge, 2002, Chapter 10. The value of the function at xt = p + x*xi can
 * be obtained 1 using p, xi, and x double value(double x, Vector p, Vector xi)
 * 2 by setting p and xi, and then using distance x: setP(Vector p)
 * setXi(Vector xi) double value(double x) 3 using xt = p + x*xi directly:
 * double value(double[] xt) Vectors x and p don't need to be set for 1
 * dimensional functions. showFunction == true shows F(xt) in status bar.
 * showMoves == true shows F(xt) and xt in Results window. plot() makes a graph
 * of the function from p in directions determined by the input variable xis.
 *
 * @author J. Anthony Parker, MD PhD <J.A.Parker@IEEE.org>
 * @version 19August2004
 * @created 27 octobre 2008
 * @see fr.curie.utils.powell.Vector
 */
public abstract class Function {
    /**
     * Description of the Field
     */
    protected boolean showMoves = false;
    private boolean minimize = true;
    private Vector p = null, xi = null;
    private int nEval = 0;
    // number of evaluations
    private boolean showFunction = true;

    /**
     * Value of the function starting at point p, going in direction xi for a
     * distance, x*xi.
     *
     * @param x  One dimensional search parameter.
     * @param p  Starting point.
     * @param xi Search direction.
     * @return Description of the Return Value
     * @throws IllegalArgumentException Description of the Exception
     */
    public double value(double x, Vector p, Vector xi)
            throws IllegalArgumentException {
        this.p = p;
        this.xi = xi;
        if (xi.length() != length() || p.length() != length()) {
            throw new IllegalArgumentException("Illegal Vector length");
        }
        return value(p.add(xi.multiply(x)));
    }

    /**
     * Number of independent variables.
     *
     * @return Description of the Return Value
     */
    protected abstract int length();

    /**
     * Value of the function for independent variables xt.
     *
     * @param xtv Description of the Parameter
     * @return Description of the Return Value
     * @throws IllegalArgumentException Description of the Exception
     */
    public double value(Vector xtv) throws IllegalArgumentException {
        double[] xt = xtv.value();
        if (length() != xt.length) {
            throw new IllegalArgumentException("Illegal length vector");
        }
        nEval++;
        double v = eval(xt);
        v = minimize ? v : -v;
        if (showFunction) {
            IJ.showStatus("Funtion = " + IJ.d2s(-v,5,10)+" , xt: " + new Vector(xt).toString(5));
        }
        if (showMoves) {
            IJ.log("Value = " + (-v) + ", xt : " + new Vector(xt).toString(15));
        }
        return v;
    }


    /**
     * Value of the function for independent variables, xt.
     *
     * @param xt Description of the Parameter
     * @return Description of the Return Value
     */
    protected abstract double eval(double[] xt);

    /**
     * Set to true for minimization; set to false for maximization.
     *
     * @param minimize The new minimize value
     */
    public void setMinimize(boolean minimize) {
        this.minimize = minimize;
    }

    /**
     * Set starting point, p.
     *
     * @param p The new p value
     * @throws IllegalArgumentException Description of the Exception
     */
    public void setP(Vector p) throws IllegalArgumentException {
        if (p.length() != length()) {
            throw new IllegalArgumentException("Illegal Vector length");
        }
        this.p = p;
    }

    /**
     * Number of times the function has been evaluated.
     *
     * @return The nEval value
     */
    public int getNEval() {
        return nEval;
    }

    /**
     * Sets the nEval attribute of the Function object
     *
     * @param nEval The new nEval value
     */
    public void setNEval(int nEval) {
        this.nEval = nEval;
    }

    /**
     * showFunction displays function value in status bar.
     *
     * @param showFunction The new showFunction value
     */
    public void setShowFunction(boolean showFunction) {
        this.showFunction = showFunction;
    }

    /**
     * showMoves is used during debugging to follow the changes in xt.
     *
     * @param showMoves The new showMoves value
     */
    public void setShowMoves(boolean showMoves) {
        this.showMoves = showMoves;
    }

    /**
     * Plot Function values with respect to p.
     *
     * @param xis    set of directions, along which to plot function
     * @param start  offset from p in direction xis[j], e.g. -5.0
     * @param end    offset from p in direction xis[j], e.g. 5.0
     * @param points number of points in graph
     * @return Description of the Return Value
     */
    public Plot plot(double[][] xis, float start,
                     float end, int points) {
        int n = length();
        // number of dimensions
        int m = xis.length;
        // number of directions
        if (p == null) {
            int len = length();
            double[] pVal = new double[len];
            for (int i = 0; i < len; i++) {
                pVal[i] = 0.0;
            }
            p = new Vector(pVal);
        }
        if (points < 2) {
            throw new IllegalArgumentException("2 points minimum");
        }
        for (int j = 0; j < m; j++) {
            if (xis[j].length != n) {
                throw new IllegalArgumentException("Illegal xis[j] length");
            }
        }
        float[] xValues = new float[points];
        float[][] yValues = new float[m][points];
        for (int i = 0; i < points; i++) {
            xValues[i] = start + (end - start) * i / (points - 1);
        }
        float xMin = xValues[0];
        float xMax = xValues[points - 1];
        float
                yMin = Float.MAX_VALUE;
        float yMax = Float.MIN_VALUE;
        for (int j = 0; j < m; j++) {
            setXi(new Vector(xis[j]).unit());
            for (int i = 0; i < points; i++) {
                yValues[j][i] = (float) value(xValues[i]);
                if (!minimize) {
                    yValues[j][i] = -yValues[j][i];
                }
                if (yValues[j][i] < yMin) {
                    yMin = yValues[j][i];
                }
                if (yValues[j][i] > yMax) {
                    yMax = yValues[j][i];
                }
            }
        }
        Plot pw = new Plot("Function values", "x[i]", "f(x[i])", xValues, yValues[0]);
        // implementation not deprecated
//        Plot pw = new Plot("Function values", "x[i]", "f(x[i])");
        pw.setLimits((double) xMin, (double) xMax, (double) yMin, (double) yMax);
        for (int j = 1; j < m; j++) {
            pw.addPoints(xValues, yValues[j], Plot.LINE);
        }
        pw.draw();
        return pw;
    }

    /**
     * Set search direction xi.
     *
     * @param xi The new xi value
     * @throws IllegalArgumentException Description of the Exception
     */
    public void setXi(Vector xi) throws IllegalArgumentException {
        if (xi.length() != length()) {
            throw new IllegalArgumentException("Illegal Vector length");
        }
        this.xi = xi;
    }

    /**
     * Value of the function for distance, x, when point, p, and direction, xi,
     * are already set.
     *
     * @param x One dimensional search parameter.
     * @return Description of the Return Value
     * @throws IllegalArgumentException Description of the Exception
     */
    public double value(double x) throws IllegalArgumentException {
        if (length() == 1) {
            // 1D functions don't require p or xi
            if (p == null) {
                p = new Vector(new double[]{0.0});
            }
            if (xi == null) {
                xi = new Vector(new double[]{1.0});
            }
        } else {
            if (p == null || xi == null) {
                throw new IllegalArgumentException("Undefined parameter(s)");
            } else if (p.length() != xi.length()) {
                throw new IllegalArgumentException(
                        "Debugging: incompatible Vector lengths");
            }
        }
        return value(p.add(xi.multiply(x)));
    }

}

