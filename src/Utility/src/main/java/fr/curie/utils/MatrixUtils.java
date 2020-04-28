package fr.curie.utils;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import cern.jet.math.tdouble.DoublePlusMultSecond;
import edu.emory.mathcs.utils.ConcurrencyUtils;
import ij.Prefs;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.lang.Math.toRadians;

/**
 * Created by cedric on 21/10/2014.
 */
public class MatrixUtils {

    /**
     * perform <A, B>C = At C B
     * @param A
     * @param B
     * @param C
     * @param result
     * @return
     */
    public static DoubleMatrix2D scalarProduct(DoubleMatrix2D A,DoubleMatrix2D B, DoubleMatrix2D C, DoubleMatrix2D result){
        result=A.viewDice().zMult(C.zMult(B,null),result);
        return result;
    }

    public static double scalarProduct21(DoubleMatrix2D A21, DoubleMatrix2D B22, DoubleMatrix2D C21){
       double A1=A21.getQuick(0,0);
       double A2=A21.getQuick(1,0);
       return (A1* B22.getQuick(0,0)+A2* B22.getQuick(1,0))*C21.getQuick(0,0) +(A1* B22.getQuick(0,1)+A2*B22.getQuick(1,1))*C21.getQuick(1,0) ;
   }


    public static DoubleMatrix1D vectorProduct(DoubleMatrix1D v1, DoubleMatrix1D v2) {
        double[] res = new double[3];
        res[0] = v1.getQuick(1) * v2.getQuick(2) - v1.getQuick(2) * v2.getQuick(1);
        res[1] = v1.getQuick(2) * v2.getQuick(0) - v1.getQuick(0) * v2.getQuick(2);
        res[2] = v1.getQuick(0) * v2.getQuick(1) - v1.getQuick(1) * v2.getQuick(0);
        return new DenseDoubleMatrix1D(res);
    }

    public static DoubleMatrix1D normalize(DoubleMatrix1D V) {
        //module
        DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
        double m = alg.normF(V);
        if (StrictMath.abs(m) > 0.00000001) {
            V.assign(DoubleFunctions.div(m));
        } else
            V.assign(0);

        return V;
    }

    public static DoubleMatrix1D UprojectToPlane(DoubleMatrix1D r, double rot, double tilt, double psi) {
        DoubleMatrix2D euler = MatrixUtils.eulerAngles2Matrix(rot, tilt, psi);
        return euler.zMult(r, null);
    }

    public static DoubleMatrix1D UprojectToPlane(DoubleMatrix1D point, DoubleMatrix1D direction, double distance) {
            DoubleMatrix1D res = new DenseDoubleMatrix1D(3);
            res.assign(point);
            double xx = distance - point.zDotProduct(direction);
            res.assign(direction, DoublePlusMultSecond.plusMult(xx));
            return res;
        }

    /**
     * do ||A||b = sqrt(At b A )
     * @param A
     * @param b
     * @param result
     * @return
     */
    public static DoubleMatrix2D norm(DoubleMatrix2D A, DoubleMatrix2D b, DoubleMatrix2D result){
        result=A.viewDice().zMult(b.zMult(A,null),result);
        result.assign(DoubleFunctions.sqrt);
        return  result;
    }
    /**
         * perform <A, C>b = At b C  <BR>
     *     and  ||A||b = sqrt(At b A )
         * @param A
         * @param B
         * @param C
         * @param result    if null a new one is created
         * @return
         */
    public static ArrayList<DoubleMatrix2D> scalarAndNorm2(DoubleMatrix2D A,DoubleMatrix2D B, DoubleMatrix2D C, ArrayList<DoubleMatrix2D> result){

        DoubleMatrix2D tmp=A.viewDice().zMult(B,null);
        if(result==null || result.size()!=2){
            result=new ArrayList<DoubleMatrix2D>(2);
            result.add(tmp.zMult(C,null));
            DoubleMatrix2D tmp2=tmp.zMult(A,null);
            //tmp2.assign(DoubleFunctions.sqrt);
            result.add(tmp2);
        }
        else{
            tmp.zMult(C,result.get(0));
            tmp.zMult(A,result.get(1));
            //result.get(1).assign(DoubleFunctions.sqrt);
        }

        return result;
    }

    /**
     * Description of the Method
     *
     * @param alpha in radians
     * @param beta  in radians
     * @return Description of the Return Value
     */
    public static DoubleMatrix1D eulerDirection(double alpha, double beta) {
        double[] v = new double[3];
        double ca = Math.cos(alpha);
        double cb = Math.cos(beta);
        double sa = Math.sin(alpha);
        double sb = Math.sin(beta);
        v[0] = sb * ca;
        v[1] = sb * sa;
        v[2] = cb;
        //System.out.println("eulerDirection : "+v[0]+" "+v[1]+" "+v[2]);
        //System.out.println("eulerDirection : alpha"+alpha+" beta="+beta);
        return DoubleFactory1D.dense.make(v);
    }


    /**
     * computes a matrix which makes the turning axis coincident with Z and turn
     * around this axis
     *
     * @param ang  in degrees
     * @param axis Description of the Parameter
     * @return Description of the Return Value
     */
    public static DoubleMatrix2D rotation3DMatrix(double ang, DoubleMatrix1D axis) {
        DoubleMatrix2D A = alignWithZ(axis);
        //System.out.println("rotation3DMatrix "+ang+": alignWithZ: "+A);
        DoubleMatrix2D Z = rotation3DMatrixZ(ang);
        //System.out.println("rotation3DMatrix : rotation3DMatrixZ("+ang+"): "+Z);
        DoubleMatrix2D tmp = Z.zMult(A, null);
        DoubleMatrix2D res = A.viewDice().zMult(tmp, null);
        //System.out.println("rotation3DMatrix ("+ang+"): returned value: "+res);
        return res;
    }

    /**
     * Description of the Method
     *
     * @param origaxis Description of the Parameter
     * @return Description of the Return Value
     */
    public static DoubleMatrix2D alignWithZ(DoubleMatrix1D origaxis) {
        DoubleMatrix1D axis = origaxis.copy();
        double module = 0;
        for (int i = 0; i < 3; i++) {
            module += axis.getQuick(i) * axis.getQuick(i);
        }
        module = Math.sqrt(module);
        axis.assign(DoubleFunctions.div(module));
        //axis.normalize();
        //System.out.println("alignWithZ: original axis "+origaxis+" normalized: "+axis);
        DoubleMatrix2D result = DoubleFactory2D.dense.make(3, 3);

        double proj_mod = Math.sqrt(axis.getQuick(1) * axis.getQuick(1) + axis.getQuick(2) * axis.getQuick(2));
        if (proj_mod > 0.000001) {
            //build matrix result
            result.setQuick(0, 0, proj_mod);
            result.setQuick(0, 1, -axis.getQuick(0) * axis.getQuick(1) / proj_mod);
            result.setQuick(0, 2, -axis.getQuick(0) * axis.getQuick(2) / proj_mod);
            result.setQuick(1, 0, 0);
            result.setQuick(1, 1, axis.getQuick(2) / proj_mod);
            result.setQuick(1, 2, -axis.getQuick(1) / proj_mod);
            result.setQuick(2, 0, axis.getQuick(0));
            result.setQuick(2, 1, axis.getQuick(1));
            result.setQuick(2, 2, axis.getQuick(2));
        } else {
            // I know that axis is X axis
            result.setQuick(0, 2, -1);
            result.setQuick(1, 1, 1);
            result.setQuick(2, 0, 1);
        }
        return result;
    }

    /**
     * Description of the Method
     *
     * @param angdegrees in degree
     * @return Description of the Return Value
     */
    public static DoubleMatrix2D rotation3DMatrixZ(double angdegrees) {
        double[][] Z = new double[3][3];
        double cosine = Math.cos(toRadians(angdegrees));
        double sine = Math.sin(toRadians(angdegrees));
        Z[0][0] = cosine;
        Z[0][1] = -sine;
        Z[1][0] = sine;
        Z[1][1] = cosine;
        Z[2][2] = 1;
        return DoubleFactory2D.dense.make(Z);
    }

    /**
        * get the transform matrix corresponding to the euler angle<BR>
        * for more information Heymann et al 2005 journal of structural biology
        *
        * @param phi   angle in degrees
        * @param theta angle in degrees
        * @param psi   angle in degrees
        * @return transformation matrix conrresponding to the angles
        */
       public static DoubleMatrix2D eulerAngles2Matrix(double phi, double theta, double psi) {
           phi = Math.toRadians(phi);
           theta = Math.toRadians(theta);
           psi = Math.toRadians(psi);
           double cphi = Math.cos(phi);
           double sphi = Math.sin(phi);
           double ctheta = Math.cos(theta);
           double stheta = Math.sin(theta);
           double cpsi = Math.cos(psi);
           double spsi = Math.sin(psi);
           double[][] mat = new double[3][3];

           mat[0][0] = cpsi * ctheta * cphi - spsi * sphi;
           mat[0][1] = cpsi * ctheta * sphi + spsi * cphi;
           mat[0][2] = -cpsi * stheta;
           mat[1][0] = -spsi * ctheta * cphi - cpsi * sphi;
           mat[1][1] = -spsi * ctheta * sphi + cpsi * cphi;
           mat[1][2] = spsi * stheta;
           mat[2][0] = stheta * cphi;
           mat[2][1] = stheta * sphi;
           mat[2][2] = ctheta;

           DoubleMatrix2D ret = new DenseDoubleMatrix2D(3, 3);
           ret.assign(mat);
           return ret;
       }

    /**
        * create a 2D rotation matrix (corresponding to affine) but use this one for matrix multiplication
        *
        * @param ang in degree
        * @return
        */
       public static DoubleMatrix2D rotation2DMatrix(double ang) {
           double[][] Z = new double[3][3];
           ang = toRadians(ang);
           double cosine = Math.cos(ang);
           double sine = Math.sin(ang);
           Z[0][0] = cosine;
           Z[0][1] = sine;
           Z[1][0] = -sine;
           Z[1][1] = cosine;
           Z[2][2] = 1;
           return DoubleFactory2D.dense.make(Z);
       }

       public static DoubleMatrix2D rotation3DMatrixY(double ang) {
           double[][] Y = new double[3][3];
           ang = toRadians(ang);
           double cosine = Math.cos(ang);
           double sine = Math.sin(ang);
           Y[0][0] = cosine;
           Y[0][2] = -sine;
           Y[2][0] = sine;
           Y[2][2] = cosine;
           Y[1][1] = 1;
           return DoubleFactory2D.dense.make(Y);
       }

    public static ArrayList<DoubleMatrix2D> getCopy2D(ArrayList<DoubleMatrix2D> from, ArrayList<DoubleMatrix2D> to){
        if(to==null){
            to=new ArrayList<DoubleMatrix2D>(from.size());
        } else{
            to.clear();
        }
        for(DoubleMatrix2D mat:from){
            to.add(mat.copy());
        }
        return to;
    }
    public static ArrayList<DoubleMatrix1D> getCopy1D(ArrayList<DoubleMatrix1D> from, ArrayList<DoubleMatrix1D> to){
            if(to==null){
                to=new ArrayList<DoubleMatrix1D>(from.size());
            } else{
                to.clear();
            }
            for(DoubleMatrix1D mat:from){
                to.add(mat.copy());
            }
            return to;
        }
    public static ArrayList<ArrayList<DoubleMatrix2D>> getCopyArrayList(ArrayList<ArrayList<DoubleMatrix2D>> from, ArrayList<ArrayList<DoubleMatrix2D>> to){
            if(to==null){
                to=new ArrayList<ArrayList<DoubleMatrix2D>>(from.size());
            } else{
                to.clear();
            }
            for(ArrayList<DoubleMatrix2D> al:from){
                to.add(getCopy2D(al,null));
            }
            return to;
        }

    /**
         * convolve 2 DHT   <BR></BR>
         * Result = H1 * H2
         * if conjugate is true then
         * Result = H1 * H2c
         *
         * @param H1        first DHT
         * @param H2        second dHT
         * @param Result    result DHT
         * @param conjugate true to conjugate the second DHT
         */
        public static void convolveFD(DenseDoubleMatrix2D H1, DenseDoubleMatrix2D H2, DenseDoubleMatrix2D Result, final boolean conjugate) {
            final int rows = H1.rows();
            final int columns = H1.columns();
            final double[] h1 = H1.elements();
            final double[] h2 = H2.elements();
            final double[] result = Result.elements();
            //int np = ConcurrencyUtils.getNumberOfProcessors();
            int np = Prefs.getThreads();
            if ((np > 1) && (columns * rows >= ConcurrencyUtils.getThreadsBeginN_2D())) {
                Future[] futures = new Future[np];
                int k = rows / np;
                for (int j = 0; j < np; j++) {
                    final int startrow = j * k;
                    final int stoprow;
                    if (j == np - 1) {
                        stoprow = rows;
                    } else {
                        stoprow = startrow + k;
                    }
                    futures[j] = ConcurrencyUtils.submit(new Runnable() {
                        public void run() {
                            int cC, rC, idx1, idx2;
                            double h2e, h2o;
                            for (int r = startrow; r < stoprow; r++) {
                                rC = (rows - r) % rows;
                                for (int c = 0; c < columns; c++) {
                                    cC = (columns - c) % columns;
                                    idx1 = c + columns * r;
                                    idx2 = cC + columns * rC;
                                    h2e = (h2[idx1] + h2[idx2]) / 2;
                                    h2o = (h2[idx1] - h2[idx2]) / 2;
                                    if (conjugate)
                                        result[idx1] = h1[idx1] * h2e - h1[idx2] * h2o;
                                    else
                                        result[idx1] = h1[idx1] * h2e + h1[idx2] * h2o;

                                }
                            }
                        }
                    });
                }
                try {
                    for (int j = 0; j < np; j++) {
                        futures[j].get();
                    }
                } catch (ExecutionException ex) {
                    ex.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                int cC, rC, idx1, idx2;
                double h2e, h2o;
                for (int r = 0; r < rows; r++) {
                    rC = (rows - r) % rows;
                    for (int c = 0; c < columns; c++) {
                        cC = (columns - c) % columns;
                        idx1 = c + columns * r;
                        idx2 = cC + columns * rC;
                        h2e = (h2[idx1] + h2[idx2]) / 2;
                        h2o = (h2[idx1] - h2[idx2]) / 2;
                        if (conjugate)
                            result[idx1] = h1[idx1] * h2e - h1[idx2] * h2o;
                        else
                            result[idx1] = h1[idx1] * h2e + h1[idx2] * h2o;
                    }
                }
            }
        }
}
