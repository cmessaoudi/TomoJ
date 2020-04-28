package fr.curie.tomoj.application;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import com.intellij.uiDesigner.core.GridLayoutManager;
import delaunay.DelaunayTriangulation;
import delaunay.Pnt;
import delaunay.Simplex;
import fr.curie.tomoj.features.FeatureTrackingChaining;
import fr.curie.tomoj.features.FiducialMarkerListFeature;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.landmarks.LandmarksChain;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.workflow.CommandWorkflow;
import fr.curie.tomoj.landmarks.SeedDetector;
import fr.curie.tomoj.application.Application;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by cmessaoudi on 26/04/2017.
 */
public class GoldBeadChainGenerator implements Application {
    boolean useMinima = true;
    double percentageExcludeX = 0.1;
    double percentageExcludeY = 0.1;
    int critical_FilterSmall = 2;
    int critical_FilterLarge = 125;
    int critical_SeedNumber = 200;
    int critical_MinimaRadius = 8;
    int patchSize = 21;
    double thresholdFitGoldBead = 0.5;
    int minChainLength = 5;
    boolean tryTofuseLandmarks = false;
    int jumpmax = 0;

    TiltSeries ts;
    TomoJPoints tp;
    DelaunayTriangulation[] delaunayTriangulations;

    public GoldBeadChainGenerator(TiltSeries ts) {

        this.ts = ts;
        this.tp = ts.getTomoJPoints();
        delaunayTriangulations = new DelaunayTriangulation[ts.getImageStackSize()];
    }

    public boolean run() {
        OutputStreamCapturer capture = new OutputStreamCapturer();
        Chrono time = new Chrono();
        time.start();
        String resultString = "";

        FiducialMarkerListFeature fmlf = new FiducialMarkerListFeature(ts, useMinima, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, critical_MinimaRadius, critical_SeedNumber, patchSize, useMinima, thresholdFitGoldBead);
        FeatureTrackingChaining ftc = new FeatureTrackingChaining();
        ArrayList<Point2D[]> chains = ftc.computeFeatureChaining(ts, fmlf, fmlf, minChainLength, jumpmax, true);
        System.out.println("number of points detected" + chains.size());
        double avglength = 0;
        int minLength = ts.getImageStackSize();
        int maxLength = 0;
        for (int i = 1; i < chains.size(); i++) {
            int length = TomoJPoints.getLandmarkLength(chains.get(i));
            avglength += length;
            if (minLength > length) {
                minLength = length;
            }
            if (maxLength < length) maxLength = length;
        }
        avglength /= chains.size();
        System.out.println("landmarks created are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");

        //System.out.println("saving in : " + IJ.getDirectory("current"));
        //tp.addSetOfPoints(chains, true);
        //CommandWorkflow.saveLandmarks(IJ.getDirectory("current"), "test_landmarks_before_fusion.txt", ts);
        //        tp.removeAllSetsOfPoints();
        if (tryTofuseLandmarks) chains = tp.tryToFuseLandmarks(chains, 1);
        tp.addSetOfPoints(chains, true);
        System.out.println("tp number of points after fusion " + tp.getNumberOfPoints());
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        tp.showAll(true);
        tp.removeEmptyChains();
        ts.setSlice(1);
        System.out.println("saving in : " + IJ.getDirectory("current"));
        CommandWorkflow.saveLandmarks(IJ.getDirectory("current"), "test_landmarks.txt", ts);
        return true;



        /*SeedDetector sd = new SeedDetector(ts);
        ImageStack maskIS = new ImageStack(ts.getWidth(), ts.getHeight());
        ImagePlus maskWindow = null;
        for (int tiltIndex = 0; tiltIndex < ts.getImageStackSize(); tiltIndex += 1) {

            ArrayList<LandmarksChain> Q = sd.createsSeedsLocalExtrema(tiltIndex, useMinima, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, critical_MinimaRadius, critical_SeedNumber);
            //ArrayList<LandmarksChain> Q = sd.pointsCreateGridSeeds(ts, tiltIndex, patchSize);
            int nbBefore = Q.size();
            double s = sd.isGoldBeadPresent(Q, tiltIndex, patchSize, useMinima, thresholdFitGoldBead);
            System.out.println("#" + (tiltIndex) + " do bead selection : " + nbBefore + " --> " + Q.size());
            TomoJPoints tp = ts.getTomoJPoints();
            ImageProcessor maskIP = new ByteProcessor(ts.getWidth(), ts.getHeight());
            int count = 0;
            if (tp != null) {
                for (LandmarksChain c : Q) {
                    Point2D.Double[] tmp = c.getLandmarkchain();
                    boolean toadd = true;
                    for (Point2D.Double p : tmp)
                        if (p != null) {
                            p.setLocation(p.getX() + ((TiltSeries) ts).getCenterX(), p.getY() + ((TiltSeries) ts).getCenterY());
                            maskIP.setColor(255);
                            if (maskIP.getPixel((int) Math.round(p.getX()), (int) Math.round(p.getY())) != 255)
                                maskIP.drawDot((int) Math.round(p.getX()), (int) Math.round(p.getY()));
                            else toadd = false;
                        }
                    if (toadd) {
                        tp.addSetOfPoints(c.getLandmarkchain());
                        count++;
                    }
                }
                maskIS.addSlice(maskIP);
                if (maskWindow == null && maskIS.getSize() == 2) {
                    maskWindow = new ImagePlus("mask", maskIS);
                    maskWindow.show();
                }
                tp.showAll(true);
            }
            System.out.println("#" + tiltIndex + " final number of points : " + count);

            ts.setSlice(tiltIndex + 1);
            delaunayTriangulations[tiltIndex] = doTriangulation(tiltIndex);
        }
        AffineTransform[] resultAffine = new AffineTransform[ts.getImageStackSize()];
        for (int i = 0; i < delaunayTriangulations.length - 1; i++) {
            resultAffine[i] = findAffineTransform(i, i + 1, patchSize / 2);
        }

        for (int i = 0; i < resultAffine.length - 1; i++) {
            ts.setTransform(i, resultAffine[i]);
        }

        return false;*/
    }

    DelaunayTriangulation doTriangulation(int indexTilt) {
        DelaunayTriangulation delaunay = null;
        double inf = ts.getWidth() + ts.getHeight();
        TomoJPoints tp = ts.getTomoJPoints();
        Simplex initial = new Simplex(new Pnt[]{
                new Pnt(-inf, -inf),
                new Pnt(-inf, 5 * inf),
                new Pnt(5 * inf, -inf)});
        delaunay = new DelaunayTriangulation(initial);
        for (int i = 0; i < tp.getNumberOfPoints(); i++) {
            Point2D tmp = tp.getCenteredPoint(i, indexTilt);
            if (tmp != null) delaunay.delaunayPlace(new Pnt(tmp.getX(), tmp.getY()));
        }
        ImageProcessor ip = ts.getImageStack().getProcessor(indexTilt + 1);
        ip.setColor(1.0);
        int countTriangles = 0;
        for (Iterator iter = delaunay.iterator();
             iter.hasNext(); ) {
            Simplex triangle = (Simplex) iter.next();
            countTriangles++;

            Iterator iter2 = triangle.iterator();
            Pnt a = (Pnt) iter2.next();
            Pnt b = (Pnt) iter2.next();
            Pnt c = (Pnt) iter2.next();
            ip.drawLine((int) Math.round(a.coord(0) + ts.getCenterX()), (int) Math.round(a.coord(1) + ts.getCenterY()), (int) Math.round(b.coord(0) + ts.getCenterX()), (int) Math.round(b.coord(1) + ts.getCenterY()));
            ip.drawLine((int) Math.round(a.coord(0) + ts.getCenterX()), (int) Math.round(a.coord(1) + ts.getCenterY()), (int) Math.round(c.coord(0) + ts.getCenterX()), (int) Math.round(c.coord(1) + ts.getCenterY()));
            ip.drawLine((int) Math.round(b.coord(0) + ts.getCenterX()), (int) Math.round(b.coord(1) + ts.getCenterY()), (int) Math.round(c.coord(0) + ts.getCenterX()), (int) Math.round(c.coord(1) + ts.getCenterY()));

        }

        System.out.println("triangles:" + countTriangles);
        return delaunay;
    }

    AffineTransform findAffineTransform(int indexTilt1, int indexTilt2, int radius) {
        AffineTransform result = new AffineTransform();
        DelaunayTriangulation delaunay1 = delaunayTriangulations[indexTilt1];
        DelaunayTriangulation delaunay2 = delaunayTriangulations[indexTilt2];
        int maxNumberCorrespondences = 0;
        //for all triangles in image1
        //System.out.println("for all triangles in image 1" + delaunay1);
        Iterator iter1 = delaunay1.iterator();
        while (iter1.hasNext()) {
            Simplex triangle1 = (Simplex) iter1.next();
            //for all triangles in image2
            Iterator iter2 = delaunay2.iterator();
            while (iter2.hasNext()) {
                Simplex triangle2 = (Simplex) iter2.next();
                //System.out.println("find all possible triangles Affine transforms");
                ArrayList<AffineTransform> tmp = findAffineTransformAllPossibleTriangles(triangle1, triangle2);
                for (AffineTransform t : tmp) {
                    if (Math.abs(t.getTranslateX()) > ts.getWidth() / 4 || Math.abs(t.getTranslateY()) > ts.getHeight() / 4)
                        continue;
                    if (t.getScaleX() < 0 || t.getScaleY() < 0) continue;
                    if (t.getScaleX() < 0.95 || t.getScaleY() < 0.95) continue;
                    double[] mat = new double[6];
                    t.getMatrix(mat);
                    if (Math.abs(mat[0]) + Math.abs(mat[2]) > 1.05) continue;
                    if (Math.abs(mat[1]) + Math.abs(mat[3]) > 1.05) continue;

                    int score = computeScore(t, indexTilt1, indexTilt2, radius);
                    //System.out.println("compute score" + score);
                    if (score > maxNumberCorrespondences) {
                        maxNumberCorrespondences = score;
                        result = t;
                    }
                }
            }
        }
        System.out.println("#" + indexTilt1 + " score : " + maxNumberCorrespondences + "\n" + result);
        return result;
    }

    ArrayList<AffineTransform> findAffineTransformAllPossibleTriangles(Simplex triangle1, Simplex triangle2) {
        ArrayList<AffineTransform> result = new ArrayList<AffineTransform>();
        //first possibility 123
        //System.out.println("123");
        result.add(findAffineTransform(triangle1, triangle2));
        Iterator iter = triangle2.iterator();
        Pnt p1 = (Pnt) iter.next();
        Pnt p2 = (Pnt) iter.next();
        Pnt p3 = (Pnt) iter.next();
        //change summit 132
        //System.out.println("132");
        Collection c = new ArrayList(3);
        c.add(p1);
        c.add(p3);
        c.add(p2);
        Simplex triangletmp = new Simplex(c);
        result.add(findAffineTransform(triangle1, triangletmp));

        //change summit 213
        //System.out.println("213");
        c = new ArrayList(3);
        c.add(p2);
        c.add(p1);
        c.add(p3);
        triangletmp = new Simplex(c);
        result.add(findAffineTransform(triangle1, triangletmp));

        //change summit 231
        //System.out.println("231");
        c = new ArrayList(3);
        c.add(p2);
        c.add(p3);
        c.add(p1);
        triangletmp = new Simplex(c);
        result.add(findAffineTransform(triangle1, triangletmp));

        //change summit 321
        //System.out.println("321");
        c = new ArrayList(3);
        c.add(p3);
        c.add(p2);
        c.add(p1);
        triangletmp = new Simplex(c);
        result.add(findAffineTransform(triangle1, triangletmp));

        //change summit 312
        //System.out.println("312");
        c = new ArrayList(3);
        c.add(p3);
        c.add(p1);
        c.add(p2);
        triangletmp = new Simplex(c);
        result.add(findAffineTransform(triangle1, triangletmp));


        return result;
    }

    AffineTransform findAffineTransform(Simplex triangle1, Simplex triangle2) {
        Iterator iter = triangle1.iterator();
        Pnt p1 = (Pnt) iter.next();
        Pnt p2 = (Pnt) iter.next();
        Pnt p3 = (Pnt) iter.next();
        iter = triangle2.iterator();
        Pnt p1p = (Pnt) iter.next();
        Pnt p2p = (Pnt) iter.next();
        Pnt p3p = (Pnt) iter.next();

        DoubleMatrix2D M = new DenseDoubleMatrix2D(6, 6);
        M.setQuick(0, 0, p1.coord(0));
        M.setQuick(0, 1, p1.coord(1));
        M.setQuick(0, 2, 1);
        M.setQuick(1, 3, p1.coord(0));
        M.setQuick(1, 4, p1.coord(1));
        M.setQuick(1, 5, 1);
        M.setQuick(2, 0, p2.coord(0));
        M.setQuick(2, 1, p2.coord(1));
        M.setQuick(2, 2, 1);
        M.setQuick(3, 3, p2.coord(0));
        M.setQuick(3, 4, p2.coord(1));
        M.setQuick(3, 5, 1);
        M.setQuick(4, 0, p3.coord(0));
        M.setQuick(4, 1, p3.coord(1));
        M.setQuick(4, 2, 1);
        M.setQuick(5, 3, p3.coord(0));
        M.setQuick(5, 4, p3.coord(1));
        M.setQuick(5, 5, 1);
        //System.out.println("M: " + M);

        DenseDoubleMatrix1D proj = new DenseDoubleMatrix1D(6);
        proj.setQuick(0, p1p.coord(0));
        proj.setQuick(1, p1p.coord(1));
        proj.setQuick(2, p2p.coord(0));
        proj.setQuick(3, p2p.coord(1));
        proj.setQuick(4, p3p.coord(0));
        proj.setQuick(5, p3p.coord(1));
        //System.out.println("proj: " + proj);

        DoubleMatrix1D result = new DenseDoubleAlgebra().solve(M, proj);

        //System.out.println("result : " + result);
        double[] mat = new double[]{result.getQuick(0), result.getQuick(3), result.getQuick(1), result.getQuick(4), result.getQuick(2), result.getQuick(5)};

        return new AffineTransform(mat);

    }

    int computeScore(AffineTransform T, int tiltIndex1, int tiltIndex2, double radius) {
        int nbPoints = 0;
        Point2D[] p1s = ts.getTomoJPoints().getCenteredPointsOnImage(tiltIndex1);
        Point2D[] p2s = ts.getTomoJPoints().getCenteredPointsOnImage(tiltIndex2);
        for (Point2D p1 : p1s) {
            if (p1 != null) {
                Point2D p1t = T.transform(p1, null);
                for (Point2D p2 : p2s) {
                    if (p2 != null) {
                        if (p2.distance(p1t) < radius) {
                            nbPoints++;
                            break;
                        }
                    }
                }
            }

        }
        return nbPoints;
    }


    public void setParameters(Object... parameters) {
        useMinima = (Boolean) parameters[0];
        percentageExcludeX = (Double) parameters[1];
        percentageExcludeY = (Double) parameters[2];
        critical_FilterSmall = (Integer) parameters[3];
        critical_FilterLarge = (Integer) parameters[4];
        critical_SeedNumber = (Integer) parameters[5];
        critical_MinimaRadius = (Integer) parameters[6];
        patchSize = (Integer) parameters[7];
        thresholdFitGoldBead = (Double) parameters[8];
        jumpmax = (Integer) parameters[9];
        tryTofuseLandmarks = (Boolean) parameters[10];
    }

    public String help() {
        return null;
    }

    public String name() {
        return null;
    }

    public ArrayList<Object> getResults() {
        return null;
    }

    public ArrayList<Object> getParametersType() {
        return null;
    }

    public ArrayList<String> getParametersName() {
        return null;
    }

    public String getParametersValuesAsString() {
        return null;
    }

    public JPanel getJPanel() {
        return null;
    }

    public void interrupt() {

    }

    public double getCompletion() {
        return 0;
    }

    public void setDisplayPreview(boolean display) {

    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    }
}
