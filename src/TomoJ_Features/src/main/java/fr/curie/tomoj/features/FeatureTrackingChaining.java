package fr.curie.tomoj.features;

import ij.IJ;
import ij.Prefs;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.workflow.CommandWorkflow;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 04/05/2016.
 */
public class FeatureTrackingChaining {
    private double completion = 0;

    private ListFeature[] allFeatures;

    public ArrayList<Point2D[]> computeFeatureChaining(TiltSeries ts, ListFeature model, int minChainSize, int jumpMax, boolean validateWithHomography) {
        //create all features
        completion = 0;
        double completionIncrement = 1.0 / 3.0 / ts.getImageStackSize();
        ArrayList<ListFeature> allFeatures = new ArrayList<ListFeature>(ts.getImageStackSize());
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            if (completion < 0) return null;
            ListFeature features = model.createListFeature();
            ts.setSlice(i + 1);
            features.detectAndCompute(ts.getProcessor());
            completion += completionIncrement;
            allFeatures.add(features);
            IJ.showStatus("step 1: feature detection");
            IJ.showProgress(i + 1, ts.getImageStackSize());
            if (features.getFeatures().size() == 0) {
                if (!IJ.showMessageWithCancel("no feature found on image " + (i + 1), "no feature found on image " + (i + 1) + " \ndo you want to continue?"))
                    return null;
            }
        }

        //do the matching with jump
        completionIncrement = 1.0 / 3.0 / ((jumpMax + 1) * ts.getImageStackSize());
        ArrayList<ArrayList<HashMap<Point2D, Point2D>>> listMatch = new ArrayList<ArrayList<HashMap<Point2D, Point2D>>>();
        for (int jump = 0; jump <= jumpMax; jump++) {
            ArrayList<HashMap<Point2D, Point2D>> matches = new ArrayList<HashMap<Point2D, Point2D>>(ts.getImageStackSize());
            for (int i = 0; i < ts.getImageStackSize() - jump - 1; i++) {
                if (completion < 0) return null;
                HashMap<Point2D, Point2D> tmp = allFeatures.get(i).matchWith(allFeatures.get(i + 1 + jump), validateWithHomography);
                matches.add(tmp);
                completion += completionIncrement;
                IJ.showStatus("step 2: matching feature between 2 images");
                IJ.showProgress(i * (jump + 1), ts.getImageStackSize() * jumpMax);
                System.out.println("#" + i + "-" + (i + 1 + jump) + " : " + tmp.size() + " matches");
            }
            listMatch.add(matches);
        }
        completion = 2.0 / 3.0;
        //matching 2 by 2
//        ArrayList<HashMap<Point2D,Point2D>> matches=new ArrayList<HashMap<Point2D, Point2D>>(ts.getImageStackSize());
//        for(int i=0;i<ts.getImageStackSize()-1;i++){
//            matches.add(allFeatures.get(i).matchWith(allFeatures.get(i+1)));
//        }
        //chaining
        //int j=0;
        //int chainSize=5;
        completionIncrement = 1.0 / 3.0 / (ts.getImageStackSize() - minChainSize);
        ArrayList<Point2D[]> listeChainObtained = new ArrayList<Point2D[]>();
        for (int j = 0; j < ts.getImageStackSize() - minChainSize; j++) {
            if (completion < 0) return null;
            ArrayList<ArrayList<Point2D>> tabList = new ArrayList<ArrayList<Point2D>>(allFeatures.size());
            for (int i = 0; i < allFeatures.size(); i++) {
                tabList.add(new ArrayList<Point2D>(allFeatures.get(i).getFeatures()));
            }
            ArrayList<ArrayList<Point2D>> chains = Chain.createChainWithJump(listMatch, tabList, j, minChainSize);
            System.out.println("nb chains with jump : " + chains.size());
            int countOK = 0, countNotOK = 0;
            for (ArrayList<Point2D> chainList : chains) {
                //System.out.println("chainList size : " + chainList.size());
                if (chainList.size() > minChainSize) {
                    Point2D[] chain = new Point2D[ts.getImageStackSize()];
                    for (int p = 0; p < chain.length; p++) {
                        chain[p] = null;
                    }
                    for (int p = 0; p < chainList.size(); p++) {
                        chain[p + j] = chainList.get(p);
                    }
                    listeChainObtained.add(chain);
                    countOK++;
                } else {
                    countNotOK++;
                }

            }
            System.out.println("OK: " + countOK + ", not OK: " + countNotOK);
            System.out.println("nb listeChainObtained : " + listeChainObtained.size());
            IJ.showStatus("step 3: chain creation");
            IJ.showProgress(j + 1, ts.getImageStackSize() - minChainSize);
            completion += completionIncrement;
        }
        ArrayList<Point2D[]> result = new ArrayList<Point2D[]>(listeChainObtained.size());
        for (Point2D[] chain : listeChainObtained) {
            Point2D[] unexpandChain = new Point2D[chain.length];
            for (int i = 0; i < chain.length; i++) {
                Point2D x = chain[i];
                //System.out.println("PAPUCHE !");
                if (x != null) {
                    x = new Point2D.Double(x.getX() - ts.getCenterX(), x.getY() - ts.getCenterY());
                    AffineTransform T = ts.getAlignment().getTransform(i);
                    try {
                        T = T.createInverse();
                        Point2D p = T.transform(x, null);
                        p = new Point2D.Double(p.getX() + ts.getCenterX(), p.getY() + ts.getCenterY());
                        unexpandChain[i] = p;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            result.add(unexpandChain);
        }
        return result;
    }

    public ArrayList<Point2D[]> computeFeatureChaining(final TiltSeries ts, final ListFeature modelDetector, final ListFeature modelDescriptor, final int minChainSize, final int jumpMax, final boolean validateWithHomography) {
        allFeatures = new ListFeature[ts.getImageStackSize()];

        //create all features
        completion = 0;
        final double completionIncrement = 1.0 / 3.0 / ts.getImageStackSize();
        final ListFeature[] imgFeatures = new ListFeature[ts.getImageStackSize()];
        ArrayList<Future> futures = new ArrayList<Future>(ts.getImageStackSize());
        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {

                    if (completion < 0) return;
                    System.out.println("working on " + ii);
                    ListFeature featuresDetector = modelDetector.createListFeature();
                    ListFeature featuresDescriptor = modelDescriptor.createListFeature();
                    ImageProcessor fp;
                    synchronized (ts) {
                        ts.setSlice(ii + 1);
                        fp = ts.getProcessor().duplicate();
                    }
//                    opencv_core.KeyPointVector kpoints=featuresDetector.detect(ts.getProcessor());
//                    ImageProcessor fp=new FloatProcessor(ts.getWidth(),ts.getHeight(),ts.getPixels(i,ts.isApplyingTransform()));
//                    fp.setMinAndMax(ts.getMin(),ts.getMax());

                    // Detect features using the user's features detector of choice
                    Object kpoints = featuresDetector.detect(fp);
                    System.out.println("number of points detected " + featuresDetector.getFeatures().size());

                    // Compute descriptors of previously found features
//                    featuresDescriptor.compute(ts.getProcessor(),kpoints);
                    featuresDescriptor.compute(fp, kpoints);

//                    features.detectAndCompute(ts.getProcessor());

                    completion += completionIncrement;

                    imgFeatures[ii] = featuresDescriptor; // Contains features location and description
                    allFeatures[ii] = featuresDescriptor;
                    IJ.showStatus("step 1: feature detection");
                    IJ.showProgress(ii + 1, ts.getImageStackSize());

                    if (featuresDescriptor.getFeatures() == null || featuresDescriptor.getFeatures().size() == 0) {
                        if (!IJ.showMessageWithCancel("no feature found on image " + (ii + 1), "no feature found on image " + (ii + 1) + " \ndo you want to continue?"))
                            completion = -1000000;
                    } else
                        System.out.println("#" + ii + " nb features found: " + featuresDescriptor.getFeatures().size());

                    if (featuresDescriptor instanceof FiducialMarkerListFeature) {
                        ((FiducialMarkerListFeature) featuresDescriptor).drawTriangulation(ts.getImageStack().getProcessor(ii + 1));
                        TomoJPoints tp = ts.getTomoJPoints();
                        ArrayList<Point2D> points = featuresDescriptor.getFeatures();
                        for (Point2D p : points) {
                            synchronized (tp) {
                                tp.addNewSetOfPoints(true);
                                tp.setPoint(tp.getCurrentIndex(), ii, p);
                            }
                        }
                    }
                }
            }));

        }
        for (Future f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (modelDescriptor instanceof FiducialMarkerListFeature) {
            CommandWorkflow.saveLandmarks(IJ.getDirectory("current"), "testLandmarksAfterDetection.txt", ts);
            ts.getTomoJPoints().removeAllSetsOfPoints();
        }

        if (completion < 0) return null;
        //do the matching with jump
        System.out.println("now matching!");
        System.out.flush();
        final double completionIncrement2 = 1.0 / 3.0 / ((jumpMax + 1) * ts.getImageStackSize());
        ArrayList<ArrayList<HashMap<Point2D, Point2D>>> listMatch = new ArrayList<ArrayList<HashMap<Point2D, Point2D>>>();
        for (int jump = 0; jump <= jumpMax; jump++) {
            final ArrayList<HashMap<Point2D, Point2D>> matches = new ArrayList<HashMap<Point2D, Point2D>>(ts.getImageStackSize());
            for (int i = 0; i < ts.getImageStackSize() - jump - 1; i++) matches.add(null);
            futures.clear();
            for (int i = 0; i < ts.getImageStackSize() - jump - 1; i++) {
                final int ii = i;
                final int jj = 1 + jump;
                futures.add(exec.submit(new Thread() {
                    public void run() {
                        if (completion < 0) return;
                        HashMap<Point2D, Point2D> tmp = imgFeatures[ii].matchWith(imgFeatures[ii + jj], validateWithHomography);
                        matches.set(ii, tmp);
                        //matches.set(ii,allFeatures[ii].matchWith(allFeatures[ii+jj],validateWithHomography));
                        completion += completionIncrement2;
                        IJ.showStatus("step 2: matching feature between 2 images");
                        IJ.showProgress(ii * (jj), ts.getImageStackSize() * jumpMax);
                        System.out.println("#" + ii + "-" + (ii + jj) + " : " + tmp.size() + " matches");
                    }
                }));
            }
            for (Future f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            listMatch.add(matches);
            if (modelDescriptor instanceof FiducialMarkerListFeature) {
                TomoJPoints tp = ts.getTomoJPoints();

                for (int i = 0; i < ts.getImageStackSize() - jump - 1; i++) {
                    HashMap<Point2D, Point2D> m = matches.get(i);
                    synchronized (tp) {
                        Set<Point2D> keys = m.keySet();
                        for (Point2D p1 : keys) {
                            Point2D p2 = m.get(p1);
                            tp.addNewSetOfPoints(true);
                            tp.setPoint(tp.getCurrentIndex(), i, p1);
                            tp.setPoint(tp.getCurrentIndex(), i + jump + 1, p2);
                            //System.out.println("#"+i+" jump:"+jump+"p1:"+p1+", p2:"+p2);
                        }
                    }
                }
            }
        }

        if (modelDescriptor instanceof FiducialMarkerListFeature) {
            //System.out.println(ts.getTomoJPoints().getPoint(0,0));
            CommandWorkflow.saveLandmarks(IJ.getDirectory("current"), "testLandmarksMatches.txt", ts);
            ts.getTomoJPoints().removeAllSetsOfPoints();
        }
        if (completion < 0) return null;
        completion = 2.0 / 3.0;
        System.out.println("now chaining!");
        System.out.flush();
        //matching 2 by 2
//        ArrayList<HashMap<Point2D,Point2D>> matches=new ArrayList<HashMap<Point2D, Point2D>>(ts.getImageStackSize());
//        for(int i=0;i<ts.getImageStackSize()-1;i++){
//            matches.add(allFeatures.get(i).matchWith(allFeatures.get(i+1)));
//        }
        //chaining
        //int j=0;
        //int chainSize=5;
        double completionIncrement3 = 1.0 / 3.0 / (ts.getImageStackSize() - minChainSize);
        ArrayList<Point2D[]> listeChainObtained = new ArrayList<Point2D[]>();
        ArrayList<ArrayList<Point2D>> tabList = new ArrayList<ArrayList<Point2D>>(imgFeatures.length);
        for (int i = 0; i < imgFeatures.length; i++) {
            tabList.add(new ArrayList<Point2D>(imgFeatures[i].getFeatures()));
        }
        for (int j = 0; j < ts.getImageStackSize() - minChainSize; j++) {
            if (completion < 0) return null;
            //tabList=null;
            ArrayList<ArrayList<Point2D>> chains = Chain.createChainWithJump(listMatch, tabList, j, minChainSize);
            //System.out.println("nb chains with jump : " + chains.size());
            int countOK = 0, countNotOK = 0;
            for (ArrayList<Point2D> chainList : chains) {
                //System.out.println("chainList size : " + chainList.size());
                if (chainList.size() > minChainSize) {
                    Point2D[] chain = new Point2D[ts.getImageStackSize()];
                    for (int p = 0; p < chain.length; p++) {
                        chain[p] = null;
                    }
                    for (int p = 0; p < chainList.size(); p++) {
                        chain[p + j] = chainList.get(p);
                    }
                    listeChainObtained.add(chain);
                    countOK++;
                } else {
                    countNotOK++;
                }

            }
            System.out.println("#" + j + " added " + countOK + " chains");
            //System.out.println("OK: " + countOK + ", not OK: " + countNotOK);
            //System.out.println("nb listeChainObtained : " + listeChainObtained.size());
            IJ.showStatus("step 3: chain creation");
            IJ.showProgress(j + 1, ts.getImageStackSize() - minChainSize);
            completion += completionIncrement3;
        }
        if (modelDescriptor instanceof FiducialMarkerListFeature) {
            TomoJPoints tp = ts.getTomoJPoints();
            for (Point2D[] c : listeChainObtained) {
                synchronized (tp) {
                    int indexLandmark = tp.addNewSetOfPoints(true);
                    for (int index = 0; index < c.length; index++) {
                        tp.setPoint(indexLandmark, index, c[index]);
                    }
                }
            }
        }
        if (modelDescriptor instanceof FiducialMarkerListFeature) {
            //System.out.println(ts.getTomoJPoints().getPoint(0,0));
            CommandWorkflow.saveLandmarks(IJ.getDirectory("current"), "testLandmarksChains.txt", ts);
            ts.getTomoJPoints().removeAllSetsOfPoints();
        }
        ArrayList<Point2D[]> result = new ArrayList<Point2D[]>(listeChainObtained.size());

        for (Point2D[] chain : listeChainObtained) {
            Point2D[] unexpandChain = new Point2D[chain.length];
            for (int i = 0; i < chain.length; i++) {
                Point2D x = chain[i];
                if (x != null) {
                    x = new Point2D.Double(x.getX() - ts.getCenterX(), x.getY() - ts.getCenterY());
                    AffineTransform T = ts.getAlignment().getTransform(i);
                    try {
                        T = T.createInverse();
                        Point2D p = T.transform(x, null);
                        p = new Point2D.Double(p.getX() + ts.getCenterX(), p.getY() + ts.getCenterY());
                        unexpandChain[i] = p;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //unexpandChain[i]=x;
                }
            }
            result.add(unexpandChain);
        }
        return result;
    }


    public ListFeature getListFeature(int index) {
        return allFeatures[index];
    }

    public void interrupt() {
        completion = -1000;
    }

    public double getCompletion() {
        return completion;
    }
}
