package fr.curie.utils.powell;

/**
 * This class is used during minimization to Bracket the Minimum.
 * After Press WH, Teukolsky SA, Vetterling WT, Flannery BP: Numverical
 * recipes in C++. Cambridge University Press, 2002. Chap 10.
 * Given two distinct starting points, ax, bx, and function, func, this
 * class returns ax, bx, cx, and the corresponding functional values,
 * fa, fb, fc, such that ax<bx<cx || ax>bx>cx and fb<fa & fb<fc.
 * <BR>
 * func The function to be minimized<BR>
 * ax One starting point<BR>
 * bx Distinct second point<BR>
 *
 * @author J. Anthony Parker, MD PhD <J.A.Parker@IEEE.org>
 * @version 1September2002
 */
public class BracketMin {
    private final static double GOLD = 1.618034, GLIMIT = 100.0,
            TINY = 1.0e-20;
    private Function func;
    private double ax, bx, cx, fa, fb, fc;
    private int iter = 0;

    public BracketMin(Function func, double ax, double bx)
            throws IllegalArgumentException {
        if (ax == bx) throw new IllegalArgumentException("ax == bx");
        if (func == null) throw new IllegalArgumentException("func is null");
        this.ax = ax;
        this.bx = bx;
        this.func = func;
        doIt();
    }

    private void doIt() {
        double ulim, u, r, q, fu, temp;
        fa = func.value(ax);
        fb = func.value(bx);
        if (fb > fa) {
            temp = ax;
            ax = bx;
            bx = temp;
            temp = fa;
            fa = fb;
            fb = temp;
        }
        cx = bx + GOLD * (bx - ax);
        fc = func.value(cx);
        while (fb > fc) {
            iter++;
            r = (bx - ax) * (fb - fc);
            q = (bx - cx) * (fb - fa);
            u = bx - ((bx - cx) * q - (bx - ax) * r) /
                    (2.0 * sign(Math.max(Math.abs(q - r), TINY), q - r));
            ulim = bx + GLIMIT * (cx - bx);
            if ((bx - u) * (u - cx) > 0.0) {
                fu = func.value(u);
                if (fu < fc) {
                    ax = bx;
                    bx = u;
                    fa = fb;
                    fb = fu;
                    return;
                } else if (fu > fb) {
                    cx = u;
                    fc = fu;
                    return;
                }
                u = cx + GOLD * (cx - bx);
                fu = func.value(u);
            } else if ((cx - u) * (u - ulim) > 0.0) {
                fu = func.value(u);
                if (fu < fc) {
                    bx = cx;
                    cx = u;
                    u = cx + GOLD * (cx - bx);
                    fb = fc;
                    fc = fu;
                    fu = func.value(u);
                }
            } else if ((u - ulim) * (ulim - cx) >= 0.0) {
                u = ulim;
                fu = func.value(u);
            } else {
                u = cx + GOLD * (cx - bx);
                fu = func.value(u);
            }
            ax = bx;
            bx = cx;
            cx = u;
            fa = fb;
            fb = fc;
            fc = fu;
        }
    }

    private double sign(double a, double b) {
        return b >= 0.0 ? Math.abs(a) : -Math.abs(a);
    }

    public double getAx() {
        return ax;
    }

    public double getBx() {
        return bx;
    }

    public double getCx() {
        return cx;
    }

    public double getFa() {
        return fa;
    }

    public double getFb() {
        return fb;
    }

    public double getFc() {
        return fc;
    }

    public int getIter() {
        return iter;
    }

}    // end of BracketMin