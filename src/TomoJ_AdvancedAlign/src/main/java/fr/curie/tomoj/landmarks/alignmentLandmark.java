package fr.curie.tomoj.landmarks;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import fr.curie.tomoj.align.AffineAlignment;
import fr.curie.tomoj.align.Alignment;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.ResultsTable;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.MatrixUtils;
import fr.curie.utils.StudentStatisitics;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.ArrayList;

import static java.lang.Math.toRadians;

/**
 * Created by IntelliJ IDEA.
 * User: MESSAOUDI C�dric
 * Date: 2 mars 2009
 * Time: 14:28:20
 * To change this template use File | Settings | File Templates.
 */
public class alignmentLandmark implements Alignment {
    DoubleMatrix1D psiD;
    double rotD;
    double tiltD;
    double z0;
    DoubleMatrix1D raxis;
    int Nimg;
    int Nlandmark;
    ArrayList<DoubleMatrix2D> Ai;
    ArrayList<DoubleMatrix2D> Ait;
    ArrayList<DoubleMatrix2D> Aip;
    ArrayList<DoubleMatrix2D> Aipt;
    ArrayList<DoubleMatrix1D> barri;
    ArrayList<DoubleMatrix1D> diaxis;
    //DoubleMatrix2D Binvraxis;
    DoubleMatrix2D allLandmarksPredictedX;
    DoubleMatrix2D allLandmarksPredictedY;
    DoubleMatrix1D errorLandmark;
    boolean show = false;
    //ArrayList<DoubleMatrix2D> B1i;
    //ArrayList<DoubleMatrix2D> B2i;
    private TomoJPoints tp;
     ArrayList<DoubleMatrix1D> di;
    private ArrayList<DoubleMatrix1D> rj;
    private double psiMax = 0;
    private DoubleMatrix1D[] barpi;
    private DoubleMatrix1D scalei;
    DoubleMatrix2D[] eulerMatrices;

    public alignmentLandmark(TomoJPoints tp) {
        this.tp = tp;
        Nimg = tp.getTiltSeries().getImageStackSize();
        Nlandmark = tp.getNumberOfPoints();
        psiD = DoubleFactory1D.dense.make(Nimg);
        scalei = DoubleFactory1D.dense.make(Nimg);
        scalei.assign(1);
        raxis = DoubleFactory1D.dense.make(3);
        rotD = 90;
        tiltD = 90;
        z0 = 0;
        //rot=0;
        //tilt=0;
        Ai = new ArrayList<DoubleMatrix2D>(Nimg);
        Ait = new ArrayList<DoubleMatrix2D>(Nimg);
        Aip = new ArrayList<DoubleMatrix2D>(Nimg);
        Aipt = new ArrayList<DoubleMatrix2D>(Nimg);
        di = new ArrayList<DoubleMatrix1D>(Nimg);
        diaxis = new ArrayList<DoubleMatrix1D>(Nimg);
        barri = new ArrayList<DoubleMatrix1D>(Nimg);
        //B1i=new ArrayList<DoubleMatrix2D>(Nimg);
        //B2i=new ArrayList<DoubleMatrix2D>(Nimg);
        barpi = new DoubleMatrix1D[Nimg];

        for (int i = 0; i < Nimg; i++) {
            DoubleMatrix2D tmp = DoubleFactory2D.dense.make(2, 3);
            Ai.add(tmp);
            Ait.add(tmp.viewDice());
            tmp = DoubleFactory2D.dense.make(2, 3);
            Aip.add(tmp);
            Aipt.add(tmp.viewDice());
            di.add(DoubleFactory1D.dense.make(2));
            diaxis.add(DoubleFactory1D.dense.make(2));
            //B1i.add(DoubleFactory2D.dense.make(3,3));
            //B2i.add(DoubleFactory2D.dense.make(3,3));
            barri.add(DoubleFactory1D.dense.make(3));
            barpi[i] = DoubleFactory1D.dense.make(2);
        }

        allLandmarksPredictedX = DoubleFactory2D.dense.make(Nlandmark, Nimg);
        allLandmarksPredictedY = DoubleFactory2D.dense.make(Nlandmark, Nimg);
        errorLandmark = DoubleFactory1D.dense.make(Nlandmark);
        rj = new ArrayList<DoubleMatrix1D>(Nlandmark);
        for (int j = 0; j < Nlandmark; j++) {
            rj.add(DoubleFactory1D.dense.make(3));
        }
    }

    public void clear() {
        clear(0.0);
    }

    public void clear(double value) {
        psiD.assign(value);
        rotD = 90 + value;
        tiltD = 90 + value;
        z0 = 0;
        //rot=0+value;
        //tilt=0+value;
        raxis.assign(value);
        for (int i = 0; i < Nimg; i++) {
            Ai.get(i).assign(value);
            Aip.get(i).assign(value);
            di.get(i).assign(value);
            diaxis.get(i).assign(value);
            //B1i.get(i).assign(value);
            //B2i.get(i).assign(value);
            barri.get(i).assign(value);
        }
        allLandmarksPredictedX.assign(value);
        allLandmarksPredictedY.assign(value);
        errorLandmark.assign(value);
        for (int j = 0; j < Nlandmark; j++) {
            rj.get(j).assign(value);
        }
        scalei.assign(1);
    }

    public void reset() {
        clear(0);
        for (int i = 0; i < Nimg; i++) {
            barpi[i].assign(0);
        }
    }

    public void test2balles() {
        Ai.get(0).setQuick(0, 0, 3);
        Ai.get(0).setQuick(1, 0, 90);
    }

    public void updateLandmarks() {
        Nlandmark = tp.getNumberOfPoints();
        allLandmarksPredictedX = DoubleFactory2D.dense.make(Nlandmark, Nimg);
        allLandmarksPredictedY = DoubleFactory2D.dense.make(Nlandmark, Nimg);
        errorLandmark = DoubleFactory1D.dense.make(Nlandmark);
        rj = new ArrayList<DoubleMatrix1D>(Nlandmark);
        for (int j = 0; j < Nlandmark; j++) {
            rj.add(DoubleFactory1D.dense.make(3));
        }
    }

    public alignmentLandmark copyFrom(alignmentLandmark other) {
        this.tp = other.tp;
        this.Nimg = other.Nimg;
        this.Nlandmark = other.Nlandmark;
        this.psiD.assign(other.psiD);
        this.rotD = other.rotD;
        this.tiltD = other.tiltD;
        this.z0 = other.z0;
        this.raxis.assign(other.raxis);
        this.scalei.assign(other.scalei);
        this.psiMax = other.psiMax;
        for (int i = 0; i < Nimg; i++) {
            this.Ai.get(i).assign(other.Ai.get(i));
            this.Aip.get(i).assign(other.Aip.get(i));
            this.di.get(i).assign(other.di.get(i));
            this.diaxis.get(i).assign(other.diaxis.get(i));
            //this.B1i.get(i).assign(other.B1i.get(i));
            //this.B2i.get(i).assign(other.B2i.get(i));
            this.barri.get(i).assign(other.barri.get(i));
            this.barpi[i].assign(other.barpi[i]);
        }
        if (this.allLandmarksPredictedX.size() == other.allLandmarksPredictedX.size()) {
            this.allLandmarksPredictedX.assign(other.allLandmarksPredictedX);
            this.allLandmarksPredictedY.assign(other.allLandmarksPredictedY);
            this.errorLandmark.assign(other.errorLandmark);
        } else {
            this.allLandmarksPredictedX = other.allLandmarksPredictedX.copy();
            this.allLandmarksPredictedY = other.allLandmarksPredictedY.copy();
            this.errorLandmark = other.errorLandmark;
        }
        if (this.rj.size() != other.rj.size()) {
            rj = new ArrayList<DoubleMatrix1D>(Nlandmark);
            for (int j = 0; j < Nlandmark; j++) {
                rj.add(other.rj.get(j).copy());
            }
        } else {
            for (int j = 0; j < Nlandmark; j++) {
                this.rj.get(j).assign(other.rj.get(j));
            }
        }
        return this;
    }

    public String toString() {
        String result = "Alignment parameters=============================\n";
        result += "rot=" + rotD + " tilt=" + tiltD + "\n";
        result += "raxis=" + raxis + "\n";
        result += "Images-----------------------------\n";
        for (int i = 0; i < Nimg; i++) {
            result += "Image " + i + " psi=" + psiD.getQuick(i) + " di=" + di.get(i) + " diaxis=" + diaxis.get(i) + "\n";
            //result+="Ai="+Ai.get(i)+"\n";
            //result+="Ait="+Ait.get(i)+"\n";
        }
        result += "Landmarks-----------------------------\n";
        for (int j = 0; j < Nlandmark; j++) {
            result+="landmark "+j+" rj="+rj.get(j)+"\n";
        }

        return result;
    }

    public double getRotation() {
        return rotD;
    }

    /**
     * rotation in degrees
     *
     * @param rot
     */
    public void setRotation(double rot) {
        this.rotD = rot;
    }

    public double getTilt() {
        return tiltD;
    }

    public ArrayList<DoubleMatrix2D> getAi() {
        return Ai;
    }

    public ArrayList<DoubleMatrix2D> getAit() {
        return Ait;
    }

    public ArrayList<DoubleMatrix2D> getAip() {
        return Aip;
    }

    public ArrayList<DoubleMatrix2D> getAipt() {
        return Aipt;
    }

    public ArrayList<DoubleMatrix1D> getDi() {
        return di;
    }

    public int getNlandmark() {
        return Nlandmark;
    }

    public DoubleMatrix1D getErrorLandmark() {
        return errorLandmark;
    }

    /**
     * set tilt in degrees
     *
     * @param tilt
     */
    public void setTilt(double tilt) {
        this.tiltD = tilt;
    }

    public void setDi(ArrayList<DoubleMatrix1D> di) {
        this.di = di;
    }

    public void setPsi(DoubleMatrix1D psi) {
        psiD = psi;
    }

    public void setPsiMax(double psiMax) {
        this.psiMax = psiMax;
    }

    public void setScale(double[] scales) {
        this.scalei.assign(scales);
    }

    public DoubleMatrix1D getErrorLandmarks() {
        return errorLandmark;
    }

    public DoubleMatrix1D getNormalizedErrorLandmarks() {
        DoubleMatrix1D res = errorLandmark.copy();
        for (int j = 0; j < Nlandmark; j++) {
            int length = 0;
            for (int i = 0; i < Nimg; i++) {
                Point2D p = tp.getCenteredPoint(j, i);
                if (p != null) {
                    length++;
                }
            }
            res.setQuick(j, res.getQuick(j));
            //res.setQuick(j, res.getQuick(j) / length + computeNeighborhoodWeighting(j));
            //res.setQuick(j, res.getQuick(j) / Math.sqrt(length));

        }
        return res;
    }

    public ArrayList<Point2D[]> getReprojectedLandmarks() {
        ArrayList<Point2D[]> res = new ArrayList<Point2D[]>(Nlandmark);
        for (int j = 0; j < Nlandmark; j++) {
            res.add(new Point2D[Nimg]);
        }
        for (int i = 0; i < Nimg; i++) {
            for (int j = 0; j < Nlandmark; j++) {
                Point2D p = tp.getCenteredPoint(j, i);
                if (p != null) {
                    res.get(j)[i] = new Point2D.Double(allLandmarksPredictedX.getQuick(j, i) / scalei.get(i), allLandmarksPredictedY.getQuick(j, i) / scalei.get(i));
                }
            }
        }

        return res;
    }

    public void updateNumberOfLandmarks() {
        Nlandmark = tp.getNumberOfPoints();
        allLandmarksPredictedX = DoubleFactory2D.dense.make(Nlandmark, Nimg);
        allLandmarksPredictedY = DoubleFactory2D.dense.make(Nlandmark, Nimg);
        errorLandmark = DoubleFactory1D.dense.make(Nlandmark);
        rj = new ArrayList<DoubleMatrix1D>(Nlandmark);
        for (int j = 0; j < Nlandmark; j++) {
            rj.add(DoubleFactory1D.dense.make(3));
        }
    }



    /**
     * optimize for rotation
     *
     * @return
     */
    public double optimizeGivenAxisDirection() {
        double bestError = Double.MAX_VALUE;
        int nIteration = 0;
        boolean finish = false;
        do {
            computeGeometryDependentOfAxis();
            computeGeometryDependentOfRotation();
            double error = computeError();
            //System.out.println("error:"+error);
            updateModel();
            nIteration++;
            //max iteration=1000 min =20
            finish = ((error > bestError) || (nIteration > 1000) || (Math.abs(error - bestError) / bestError < 0.001)) && nIteration > 20;
            if (error < bestError) bestError = error;
        } while (!finish);
        //printAlignment();
        return bestError;
    }

    /**
     * compute the geometry part corresponding to axis
     */
    public void computeGeometryDependentOfAxis() {
        DoubleMatrix1D axis = eulerDirection(toRadians(rotD), toRadians(tiltD), 0);
        //DoubleMatrix2D tmpRaxisSum=DoubleFactory2D.dense.make(3,3);
        //compute Aip
        //System.out.println("computeGeometryDependantOfAxis");
        //System.out.println("axis before"+axis);
        for (int i = 0; i < Nimg; i++) {
            DoubleMatrix2D Raxis = rotation3DMatrix(tp.getTiltSeries().getTiltAngle(i), axis);
            //System.out.println("Raxis ("+tp.getTiltSeries().getTiltAngle(i)+"): "+Raxis);
            //tmpRaxisSum.assign(Raxis,DoubleFunctions.plus);
            DoubleMatrix2D tmp = Aip.get(i);
            tmp.setQuick(0, 0, Raxis.getQuick(0, 0));
            tmp.setQuick(0, 1, Raxis.getQuick(0, 1));
            tmp.setQuick(0, 2, Raxis.getQuick(0, 2));
            tmp.setQuick(1, 0, Raxis.getQuick(1, 0));
            tmp.setQuick(1, 1, Raxis.getQuick(1, 1));
            tmp.setQuick(1, 2, Raxis.getQuick(1, 2));

        }
        //System.out.println("axis after"+axis);
        //System.out.println(tmpRaxisSum);
    }

    public void computeGeometryDependentOfRotation() {
        //System.out.println("computeGeometryDependantOfRotation");
        DoubleMatrix2D RinPlane;
        DoubleMatrix2D RinPlaneView22;
        DoubleMatrix2D I = DoubleFactory2D.dense.make(2, 3);
        //DoubleMatrix2D tmpAiSum=DoubleFactory2D.dense.make(2,3);
        for (int i = 0; i < Nimg; i++) {
            RinPlane = rotation3DMatrixZ(-psiD.getQuick(i));
            RinPlaneView22 = RinPlane.viewPart(0, 0, 2, 2);
            DoubleMatrix2D tmp2 = RinPlaneView22.zMult(Aip.get(i), null);
            Ai.get(i).assign(tmp2);
            //tmpAiSum.assign(Ai.get(i),DoubleFunctions.plus);
            I.assign(0.0);
            I.setQuick(0, 0, 1);
            I.setQuick(1, 1, 1);
            I.assign(Aip.get(i), DoubleFunctions.minus);
            DoubleMatrix1D tmp3 = I.zMult(raxis, null);
            diaxis.get(i).assign(RinPlaneView22.zMult(tmp3, null));
        }
        //System.out.println(tmpAiSum);
    }

    public double computeError() {
        computeErrorForLandmarks();
        double error = 0;
        for (int j = 0; j < Nlandmark; j++) {
            //if(j==0) System.out.println(j+" error "+errorLandmark.getQuick(j));
            error += errorLandmark.getQuick(j);
        }
        error /= Nlandmark;
        error += computeErrorForLandmarks2();
        //error/=2;
        return error;

    }

    public void updateModel() {
        DoubleMatrix1D pij = DoubleFactory1D.dense.make(2);
        DoubleMatrix1D piN = DoubleFactory1D.dense.make(2);
        DoubleMatrix2D A = DoubleFactory2D.dense.make(3, 3);
        DoubleMatrix2D AitAi;
        DoubleMatrix1D b = DoubleFactory1D.dense.make(3);
        //update the 3D position of the landmarks
        //DoubleMatrix1D sumrj=DoubleFactory1D.dense.make(3);
        //DoubleMatrix1D sumpij= DoubleFactory1D.dense.make(2);
        //long countpij=0;
        for (int j = 0; j < Nlandmark; j++) {
            A.assign(0.0);
            b.assign(0.0);
            //compute the part corresponding to Vj
            for (int i = 0; i < Nimg; i++) {
                Point2D p = tp.getCenteredPoint(j, i);
                if (p != null) {
                    pij.setQuick(0, p.getX());
                    pij.setQuick(1, p.getY());
                    /*System.out.println("pij="+p);
                         try{
                         System.in.read();
                         }catch(Exception e){
                             System.out.println(e);
                         }*/
                    //sumpij.assign(pij, DoubleFunctions.plus);
                    //countpij++;
                    DoubleMatrix2D tmp = Ait.get(i).zMult(Ai.get(i), null);
                    A.assign(tmp, DoubleFunctions.plus);
                    pij.assign(di.get(i), DoubleFunctions.minus);
                    pij.assign(diaxis.get(i), DoubleFunctions.minus);
                    b.assign(Ait.get(i).zMult(pij, null), DoubleFunctions.plus);
                }
            }
            //update rj
            try {
                rj.get(j).assign(new DenseDoubleAlgebra().inverse(A).zMult(b, null));
                //sumrj.assign(rj.get(j), DoubleFunctions.plus);
            } catch (Exception e) {
                System.out.println(e);
                System.out.println("update model error with landmark "+j);
                e.printStackTrace();

            }
        }
        //System.out.println("updatemodel: sumpij="+sumpij);
        //System.out.println("updatemodel: sumrj="+sumrj);
        //System.out.println("count="+countpij);
        /*try{
              System.in.read();
          }catch(Exception e){
              System.out.println(e);
          }*/
        //compute the average landmarks seen in each image
        //DoubleMatrix1D sumbarri=DoubleFactory1D.dense.make(3);
        for (int i = 0; i < Nimg; i++) {
            barri.get(i).assign(0.0);
            int count = 0;
            for (int j = 0; j < Nlandmark; j++) {
                Point2D p = tp.getCenteredPoint(j, i);
                if (p != null) {
                    barri.get(i).assign(rj.get(j), DoubleFunctions.plus);
                    count++;
                }
            }
            barri.get(i).assign(DoubleFunctions.div(count));
            //sumbarri.assign(barri.get(i), DoubleFunctions.plus);
        }
        //System.out.println("sum barri="+sumbarri);
        /*try{
              System.in.read();
          }catch(Exception e){
              System.out.println(e);
          }*/
        //System.out.println("rj[0]="+rj.get(0));
        //update shifts
        //update the individual di
        //DoubleMatrix1D sumbarpi=DoubleFactory1D.dense.make(2);
        //DoubleMatrix1D sumdi=DoubleFactory1D.dense.make(2);
        for (int i = 0; i < Nimg; i++) {
            if (i != tp.getTiltSeries().getZeroIndex()) {
                DoubleMatrix1D tmp = barpi[i].copy();
                //if(i==0)System.out.println("#i--- barpi:"+tmp);
                //if(i==0)System.out.println("#i--- barri:"+barri.get(i));
                //System.out.println("barpi="+tmp);
                //System.out.println("barri="+barri.get(i));
                //System.out.println("Ai*barri"+Ai.get(i).zMult(barri.get(i), null));
                tmp.assign(Ai.get(i).zMult(barri.get(i), null), DoubleFunctions.minus);
                //if(i==0)System.out.println("#i--- barpi - Ai*barri:"+tmp);
                //System.out.println("di="+tmp);
                tmp.assign(diaxis.get(i), DoubleFunctions.minus);
                di.get(i).assign(tmp);
                //sumbarpi.assign(barpi[i], DoubleFunctions.plus);
                //sumdi.assign(di.get(i), DoubleFunctions.plus);
            }
        }
        /*System.out.println("sum barpi="+sumbarpi);
          System.out.println("sum di="+sumdi);
          try{
              System.in.read();
          }catch(Exception e){
              System.out.println(e);
          }*/
        //System.out.println("barpi[0]="+barpi[0]);
        //System.out.println("di[0]="+di.get(0));
        //update rotations
        if (psiMax > 0) {
            //DoubleMatrix2D tmp1=new DenseDoubleMatrix2D(2,2);
            DoubleMatrix2D Ri = new DenseDoubleMatrix2D(2, 2);
            DoubleMatrix2D dim, Aiprj, Aiprjt;
            for (int i = 0; i < Nimg; i++) {
                //tmp1.assign(0);
                Ri.assign(0);
                dim = di.get(i).reshape(2, 1);
                DoubleMatrix2D tmp4 = diaxis.get(i).reshape(2, 1);
                dim.assign(tmp4, DoubleFunctions.minus);
                for (int j = 0; j < Nlandmark; j++) {
                    Point2D p = tp.getCenteredPoint(j, i);
                    if (p != null) {
                        Aiprj = Aip.get(i).zMult(rj.get(j), null).reshape(2, 1);
                        Aiprjt = Aiprj.viewDice();
                        //tmp1.assign(Aiprj.zMult(Aiprjt,null),DoubleFunctions.plus);
                        tmp4 = new DenseDoubleMatrix2D(2, 1);
                        tmp4.setQuick(0, 0, dim.getQuick(0, 0) - p.getX());
                        tmp4.setQuick(1, 0, dim.getQuick(1, 0) - p.getY());
                        Ri.assign(tmp4.zMult(Aiprjt, null), DoubleFunctions.plus);
                    }
                }
                double psi = StrictMath.toDegrees(StrictMath.atan((Ri.getQuick(0, 1) - Ri.getQuick(1, 0)) / (Ri.getQuick(0, 0) + Ri.getQuick(1, 1))));
                if (psi < -psiMax) psi = -psiMax;
                if (psi > psiMax) psi = psiMax;
                //System.out.println("psi="+psi+" psimax="+psiMax);
                psiD.setQuick(i, psi);
            }
        }
        //update the rotation dependant part
        computeGeometryDependentOfRotation();
    }

    /**
     * Description of the Method
     *
     * @param alpha in radians
     * @param beta  in radians
     * @param gamma in radians
     * @return Description of the Return Value
     */
    DoubleMatrix1D eulerDirection(double alpha, double beta, double gamma) {
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
    DoubleMatrix2D rotation3DMatrix(double ang, DoubleMatrix1D axis) {
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
     * @param ang in degree
     * @return Description of the Return Value
     */
    DoubleMatrix2D rotation3DMatrixZ(double ang) {
        double[][] Z = new double[3][3];
        double cosine = Math.cos(toRadians(ang));
        double sine = Math.sin(toRadians(ang));
        Z[0][0] = cosine;
        Z[0][1] = -sine;
        Z[1][0] = sine;
        Z[1][1] = cosine;
        Z[2][2] = 1;
        return DoubleFactory2D.dense.make(Z);
    }

    /**
     * compute for all landmarks the distance between reprojection and "true" position
     * <BR></BR>
     * results are stored in errorLandmark
     */
    public void computeErrorForLandmarks() {
        errorLandmark.assign(0.0);
        DoubleMatrix1D pijp;
        for (int j = 0; j < Nlandmark; j++) {
            ArrayList<Double> distances = new ArrayList<Double>();
            for (int i = 0; i < Nimg; i++) {
                Point2D p = tp.getCenteredPoint(j, i);
                if (p != null) {
                    pijp = Ai.get(i).zMult(rj.get(j), null);
                    pijp.assign(di.get(i), DoubleFunctions.plus);
                    pijp.assign(diaxis.get(i), DoubleFunctions.plus);
                    allLandmarksPredictedX.setQuick(j, i, pijp.getQuick(0));
                    allLandmarksPredictedY.setQuick(j, i, pijp.getQuick(1));
                    double diffx = p.getX() - allLandmarksPredictedX.getQuick(j, i);
                    double diffy = p.getY() - allLandmarksPredictedY.getQuick(j, i);
                    distances.add(Math.sqrt(diffx * diffx + diffy * diffy));

                }
            }
            errorLandmark.setQuick(j, StudentStatisitics.getStatistics(distances, 0.05)[4]);
            //errorLandmark.setQuick(j, StudentStatisitics.getStatistics(distances, 0.05)[0]);

        }
    }

    /**
     * compute the global direction of the differences between reprojection and "true" landmarks, and computing the distance. if there is no bias it should be 0!
     *
     * @return worse distance on X or Y between all images
     */
    public double computeErrorForLandmarks2() {
        //errorLandmark.assign(0.0);
        //DoubleMatrix1D pijp;
        double score = 0;
        double scorex = 0;
        double scorey = 0;
        for (int i = 0; i < Nimg; i++) {
            ArrayList<Double> distancesx = new ArrayList<Double>();
            ArrayList<Double> distancesy = new ArrayList<Double>();
            for (int j = 0; j < Nlandmark; j++) {
                Point2D p = tp.getCenteredPoint(j, i);
                if (p != null) {
                    //pijp = Ai.get(i).zMult(rj.get(j), null);
                    //pijp.assign(di.get(i), DoubleFunctions.plus);
                    //pijp.assign(diaxis.get(i), DoubleFunctions.plus);
                    //allLandmarksPredictedX.setQuick(j, i, pijp.getQuick(0));
                    //allLandmarksPredictedY.setQuick(j, i, pijp.getQuick(1));
                    double diffx = p.getX() - allLandmarksPredictedX.getQuick(j, i);
                    double diffy = p.getY() - allLandmarksPredictedY.getQuick(j, i);
                    distancesx.add(diffx);
                    distancesy.add(diffy);

                }
            }
            //errorLandmark.setQuick(j, StudentStatisitics.getStatistics(distances, 0.05)[4]);
            //errorLandmark.setQuick(j, StudentStatisitics.getStatistics(distances, 0.05)[0]);
            double scoreImgX = Math.abs(StudentStatisitics.getStatistics(distancesx, 0.05)[0]);
            double scoreImgY = Math.abs(StudentStatisitics.getStatistics(distancesy, 0.05)[0]);

            scorex = Math.max(scorex, scoreImgX);
            scorey = Math.max(scorey, scoreImgY);
            //System.out.println("score img "+i+" X:"+scoreImgX+" Y:"+scoreImgY+" used:"+((scoreImgX+scoreImgY)));
            score += (scoreImgX + scoreImgY);

        }
//        if(show)
//            System.out.println("final score:"+(score/Nimg));
//        return score/Nimg;
        return Math.max(scorex, scorey);

    }

    /**
     * Description of the Method
     *
     * @param origaxis Description of the Parameter
     * @return Description of the Return Value
     */
    DoubleMatrix2D alignWithZ(DoubleMatrix1D origaxis) {
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

    public double computeNeighborhoodWeighting(int index) {
        double smallestDist = Double.MAX_VALUE;
        DoubleMatrix1D currentLandmark3D = rj.get(index);
        for (DoubleMatrix1D l : rj) {
            if (l != currentLandmark3D) {
                double tmp = currentLandmark3D.getQuick(0) - l.getQuick(0);
                double dst = tmp * tmp;
                tmp = currentLandmark3D.getQuick(1) - l.getQuick(1);
                dst += tmp * tmp;
                tmp = currentLandmark3D.getQuick(2) - l.getQuick(2);
                dst += tmp * tmp;
                if (dst < smallestDist) smallestDist = dst;

            }
        }
        return 1.0 / Math.sqrt(smallestDist);
    }

    public void produceInfoFromLandmarks() {
        for (int i = 0; i < Nimg; i++) {
            int count = 0;
            barpi[i].assign(0);
            for (int j = 0; j < Nlandmark; j++) {
                Point2D p = tp.getCenteredPoint(j, i);
                if (p != null) {
                    barpi[i].setQuick(0, barpi[i].getQuick(0) + p.getX());
                    barpi[i].setQuick(1, barpi[i].getQuick(1) + p.getY());
                    count++;
                }
            }
            barpi[i].assign(DoubleFunctions.div(count));
        }
    }



    public void printAlignment() {
        getAsResultTable().show("old algo");
        System.out.println("rot=" + rotD + " tilt=" + tiltD);
        System.out.println("raxis="+raxis);
        System.out.println("Images");
        for (int i = 0; i < Nimg; i++) {
            System.out.println("" + i + " psi=" + psiD.getQuick(i) + "\t di=\t" + di.get(i).getQuick(0) + " \t" + di.get(i).getQuick(1) + "\t diaxis=" + diaxis.get(i).getQuick(0) + " " + diaxis.get(i).getQuick(1));
        }
        /*try{
            System.in.read();
        }catch(Exception e){
            System.out.println(e);
        }
        System.out.println("Landmarks");
        for(int j=0;j<Nlandmark;j++){
            System.out.println(""+j+" rj="+rj.get(j).getQuick(0)+" "+rj.get(j).getQuick(1)+" "+rj.get(j).getQuick(2)+" error="+errorLandmark.get(j));
            if(j%100==0){
                try{
                    System.in.read();
                }catch(Exception e){
                    System.out.println(e);
                }
            }
        }*/
    }

    public double correctAxisHeight() {
        //compute the average height of the 3D landmarks seen at 0�
        DoubleMatrix1D axis = eulerDirection(Math.toRadians(rotD), Math.toRadians(tiltD), 0);
        z0 = 0;
        int z0N = 0;
        TiltSeries ts = tp.getTiltSeries();
        int zeroindex = ts.getZeroIndex();
        double zeroTiltAngle = ts.getTiltAngle(zeroindex);
        //System.out.println("nlandmarks "+Nlandmark);
        for (int j = 0; j < Nlandmark; j++) {
            //System.out.println("j="+j);
            Point2D p = tp.getCenteredPoint(j, zeroindex);
            if (p != null) {
                DoubleMatrix2D Raxismin = rotation3DMatrix(zeroTiltAngle, axis);
                //System.out.println("p not null at zero index("+zeroindex);
                DoubleMatrix2D Rmin = rotation2DMatrix(90 - rotD + psiD.get(zeroindex));
                DoubleMatrix2D RtiltYmin = rotation3DMatrixY(-zeroTiltAngle);
                DoubleMatrix1D rjp = RtiltYmin.zMult(Rmin.zMult(Raxismin.zMult(rj.get(j), null), null), null);
                z0 += rjp.getQuick(2);
                z0N++;
            }
        }
        if (z0N == 0) {
            System.out.println("no landmarks at 0�!!! could not compute the height");
            z0 = 0;
        } else {
            z0 /= z0N;
        }
        System.out.println("correct axis height: z0=" + z0);
        return z0;
    }

    /**
     * create a 2D rotation matrix (corresponding to affine) but use this one for matrix multiplication
     *
     * @param ang in degree
     * @return
     */
    DoubleMatrix2D rotation2DMatrix(double ang) {
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

    DoubleMatrix2D rotation3DMatrixY(double ang) {
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

    public ImagePlus create3DLandmarksImage(int width, int height, int thickness) {
        ImagePlus res = NewImage.createFloatImage("3D landmarks", width, height, thickness, NewImage.FILL_BLACK + NewImage.CHECK_AVAILABLE_MEMORY);
        int cx = width / 2;
        int cy = height / 2;
        int cz = thickness / 2;
        if (res != null) {
            for (int j = 0; j < rj.size(); j++) {
                DoubleMatrix1D landmark3D = rj.get(j);
                int x = (int) landmark3D.getQuick(0) + cx;
                int y = (int) landmark3D.getQuick(1) + cy;
                int z = (int) -landmark3D.getQuick(2) + 1 + cz;
                if(z<1||z>thickness-2) continue;
                for (int xx = -1; xx < 2; xx++) {
                    for (int yy = -1; yy < 2; yy++) {
                        for (int zz = -1; zz < 2; zz++) {
                            res.getImageStack().getProcessor(z + zz).putPixelValue(x + xx, y + yy, j * 10);
                        }
                    }
                }
            }
        } else {
            System.out.println("not enough memory!");
        }
        return res;
    }

    public void saveRjs() {
        saveRjs("classic_alignment_rjs.txt");
    }
    public void saveRjs(String fileName){
        //System.out.println("save rjs : nb points "+rj.size());
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
            for(int j=0;j<rj.size();j++){
                DoubleMatrix1D r=rj.get(j);
                out.write("#"+j+"\t"+r.getQuick(0)+"\t"+r.getQuick(1)+"\t"+(-(r.getQuick(2)))+"\n");
            }
            out.flush();
            out.close();
        }catch (Exception e) {e.printStackTrace();}
    }

    public DoubleMatrix1D getRj(int index){
        return rj.get(index);
    }

    public ArrayList<DoubleMatrix1D> getRjs() {
        return rj;
    }

    public void correctRjsPositionforTomogram(double zheight){
        DoubleMatrix2D Rmin = MatrixUtils.rotation2DMatrix(90 - rotD + psiD.getQuick(tp.getTiltSeries().getZeroIndex()));
        Rmin=new DenseDoubleAlgebra().inverse(Rmin);
        for(DoubleMatrix1D point:rj) {
            DoubleMatrix1D rjr = Rmin.zMult(point, null);
            rjr.setQuick(2,rjr.getQuick(2)-zheight);
            point.setQuick(0,rjr.getQuick(0));
            point.setQuick(1,rjr.getQuick(1));
            point.setQuick(2,rjr.getQuick(2));
        }
        System.out.println("correct rjs position: nb points "+rj.size());

    }

    @Override
    public float[] applyTransformOnImage(TiltSeries ts, int index) {
        float[] res;

        AffineTransform T= getTransform(index);
        res= ts.getPixels(index,T);

        return res;
    }

    public AffineTransform getTransform(int index) {
        AffineTransform T = new AffineTransform();
        T.scale(scalei.get(index), scalei.get(index));
        //translation for axis height
        T.translate(-z0 * Math.sin(toRadians(tp.getTiltSeries().getTiltAngle(index))), 0);
        //rotation for axis
        T.rotate(toRadians(90 - rotD + psiD.getQuick(index)));
        //translation for shifts
        T.translate(-di.get(index).getQuick(0), -di.get(index).getQuick(1));
        T.translate(-diaxis.get(index).getQuick(0), -diaxis.get(index).getQuick(1));
        return T;
    }

    public AffineTransform getTranslationTransform(int index) {
        AffineTransform T = new AffineTransform();
        T.translate(-di.get(index).getQuick(0), -di.get(index).getQuick(1));
        T.translate(-diaxis.get(index).getQuick(0), -diaxis.get(index).getQuick(1));
        return T;
    }

    @Override
    /** compute transforms for all images and return them in an array
     * @return the array containing all transforms*/
    public AffineTransform[] getTransforms() {
        AffineTransform[] transfs=new AffineTransform[Nimg];
        for(int i=0;i<Nimg;i++) transfs[i]=getTransform(i);
        return transfs;
    }

    @Override
    /** Does nothing as zeroIndex is not needed in this class
     * @param zeroIndex index of zero tilt image*/
    public void setZeroIndex(int zeroIndex) {
    }

    @Override
    public void removeTransform(int index) {
        for(int i=index;i<Nimg-1;i++){
            scalei.setQuick(i,scalei.getQuick(i+1));
            psiD.setQuick(i,psiD.getQuick(i+1));
        }
        Nimg--;
        scalei=scalei.viewPart(0,Nimg);
        psiD=psiD.viewPart(0,Nimg);
        di.remove(index);
        diaxis.remove(index);

    }

    @Override
    public AffineTransform getZeroTransform() {
        return getTransform(tp.getTiltSeries().getZeroIndex());
    }



    public DoubleMatrix2D getEulerMatrix(int index) {
        if (eulerMatrices[index] == null) {
            DoubleMatrix1D axis = eulerDirection(Math.toRadians(rotD), Math.toRadians(tiltD), 0);
            int zeroindex = tp.getTiltSeries().getZeroIndex();
            double zeroTiltAngle = tp.getTiltSeries().getTiltAngle(zeroindex);
            DoubleMatrix2D Raxismin = rotation3DMatrix(zeroTiltAngle, axis);
            //System.out.println("p not null at zero index("+zeroindex);
            DoubleMatrix2D Rmin = rotation2DMatrix(90 - rotD + psiD.get(zeroindex));
            DoubleMatrix2D RtiltYmin = rotation3DMatrixY(-zeroTiltAngle);
            eulerMatrices[index] = RtiltYmin.zMult(Rmin.zMult(Raxismin, null), null);
        }
        return eulerMatrices[index];
    }

    public DoubleMatrix2D getEulerMatrix(TiltSeries ts, int index) {
        if (eulerMatrices[index] == null) {
            DoubleMatrix1D axis = eulerDirection(Math.toRadians(rotD), Math.toRadians(tiltD), 0);
            int zeroindex = tp.getTiltSeries().getZeroIndex();
            double zeroTiltAngle = tp.getTiltSeries().getTiltAngle(zeroindex);
            DoubleMatrix2D Raxismin = rotation3DMatrix(zeroTiltAngle, axis);
            //System.out.println("p not null at zero index("+zeroindex);
            DoubleMatrix2D Rmin = rotation2DMatrix(90 - rotD + psiD.get(zeroindex));
            DoubleMatrix2D RtiltYmin = rotation3DMatrixY(-zeroTiltAngle);
            eulerMatrices[index] = RtiltYmin.zMult(Rmin.zMult(Raxismin, null), null);
        }
        return eulerMatrices[index];
    }

    public void setEulerMatrix(int index, DoubleMatrix2D eulerMatrix) {
        eulerMatrices[index] = eulerMatrix;
    }

    public void resetEulerMatrices() {
        for (int i = 0; i < eulerMatrices.length; i++) {
            eulerMatrices[i] = null;
        }

    }

    public ResultsTable getAsResultTable() {
        ResultsTable rt = new ResultsTable();
        for (int i = 0; i < Nimg; i++) {
            rt.incrementCounter();
            rt.addValue("image", i);
            rt.addValue("tilt axis (rot)", rotD);
            rt.addValue("tilt axis (tilt)", tiltD);
            rt.addValue("tilt angle", tp.getTiltSeries().getTiltAngle(i));
            rt.addValue("di.x",di.get(i).getQuick(0));
            rt.addValue("di.y",di.get(i).getQuick(1));
            //rt.addValue("di", toString(current_di.get(i)));
            rt.addValue("psii", psiD.getQuick(i));
        }
        rt.setPrecision(5);
        rt.showRowNumbers(true);
        return rt;
    }

    @Override
    public void loadFromFile(String path,double binning, boolean... options) throws IOException{

        ResultsTable rt = ResultsTable.open(path);
        if(rt==null) return;
        rt.show("test");
        for (int i = 0; i < Nimg; i++) {
            DoubleMatrix1D di = this.getDi().get(i);
            di.setQuick(0,  rt.getValue("di.x",i)*binning);
            di.setQuick(1,  rt.getValue("di.y",i)*binning);
            psiD.setQuick(i,rt.getValue("psii",i));
            tiltD=rt.getValue("tilt axis (tilt)",i);
            rotD=rt.getValue("tilt axis (rot)",i);
        }

        System.out.println("loading finished : ");
    }

    public static void loadAlignmentLandmark(String pathToFile, TiltSeries ts) {
        final alignmentLandmark align = new alignmentLandmark(ts.getTomoJPoints());
        double rot = 0;
        double tilt = 0;
        DoubleMatrix1D psiD = DoubleFactory1D.dense.make(ts.getImageStackSize());
        ArrayList<DoubleMatrix1D> di = new ArrayList<DoubleMatrix1D>(ts.getImageStackSize());
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            di.add(DoubleFactory1D.dense.make(2));
        }
        try {
            BufferedReader in = new BufferedReader(new FileReader(pathToFile));
            String line;
            while ((line = in.readLine()) != null) {
                String[] words = line.split(" ");
                if (words[0].startsWith("rot=")) {
                    String[] s = words[0].split("=");
                    rot = Double.parseDouble(s[1]);
                }
                if (words[1].startsWith("tilt")) {
                    String[] s = words[0].split("=");
                    tilt = Double.parseDouble(s[1]);
                }
                if (words[0].equalsIgnoreCase("Image") && words.length > 7) {
                    int index = Integer.parseInt(words[1]);
                    psiD.setQuick(index, Double.parseDouble(words[3]));
                    DoubleMatrix1D tmp = di.get(index);
                    //attention 2 espaces entre di= et les 2 valeurs
                    tmp.setQuick(0, Double.parseDouble(words[6]));
                    tmp.setQuick(1, Double.parseDouble(words[7]));
                }
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
        align.setRotation(rot);
        align.setTilt(tilt);
        align.setPsi(psiD);
        align.setDi(di);
        align.printAlignment();

        //update the alignment in tilt series
        //update0�
        ts.setAlignment(align);

    }

    public void saveToFile(String path, boolean... options) throws IOException {
        try {
            ResultsTable rt=getAsResultTable();
            rt.saveAs(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AffineAlignment convertToAffine(){
        AffineAlignment result=new AffineAlignment(tp.getTiltSeries());
        for(int i=0;i<tp.getTiltSeries().getImageStackSize();i++){
            result.setTransform(i, getTransform(i));
        }
        result.convertTolocalTransform();
        return result;
    }
}
