package fr.curie.tomoj.landmarks;

import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PointRoi;
import ij.process.*;
import fr.curie.filters.ApplySymmetry_Filter;
import fr.curie.filters.FFTFilter_TomoJ;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.Chrono;
import fr.curie.utils.MatrixUtils;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 13/04/2015.
 */
public class LandmarksGenerator {
    TomoJPoints tp;
        protected boolean goldBead = false;
        protected int critical_FilterLarge = 125;
        protected int critical_FilterSmall = 2;
        protected int critical_SeedNumber = 150;
        protected int critical_MinimaRadius = 8;
        protected double percentageExcludedX = 0.1;
        protected double percentageExcludedY = 0.1;
        protected double completion = 0;
        protected ExecutorService exec;
        ArrayList<Point2D[]> landmarks ;
        ArrayList<Boolean> automaticGeneration = new ArrayList<Boolean>();
        TiltSeries ts;
        int nbThreads = Prefs.getThreads();
        FFTFilter_TomoJ fftf;


    public LandmarksGenerator(TomoJPoints tp,TiltSeries ts){
        this.ts=ts;
        this.tp=tp;
        landmarks=tp.getAllLandmarks();
        automaticGeneration=tp.getAllAutomaticGeneration();
    }

    public void interrupt() {
           completion = -1000;
           if (fftf != null) fftf.interrupt();
       }

       public double getCompletion() {
           return completion;
       }

       public void resetCompletion() {
           completion = 0;
       }

    public void setGoldBead(boolean value) {
        goldBead = value;
    }

    public boolean lookingForGoldBead() {
        return goldBead;
    }

    public void setCriticalFilter(int large, int small) {
        critical_FilterLarge = large;
        critical_FilterSmall = small;
    }

    public int[] getCriticalFilterParameters() {
        return new int[]{critical_FilterSmall, critical_FilterLarge};
    }

    public void setCriticalSeed(int nbSeed) {
        critical_SeedNumber = nbSeed;
    }

    public int getCriticalSeedNumber() {
        return critical_SeedNumber;
    }

    public int getCriticalMinimaRadius() {
        return critical_MinimaRadius;
    }

    public void setCriticalMinimaRadius(int radius) {
        critical_MinimaRadius = radius;
    }

    public void setPercentageExcluded(double percentageExcludedX, double percentageExcludedY) {
        this.percentageExcludedX = percentageExcludedX;
        this.percentageExcludedY = percentageExcludedY;
    }


    /**
        * the actual version of generating landmarks in TomoJ<br>
        * it create threads to generate landmarks chains starting from each image.
        *
        * @param seqLength        desired length of landmark chains
        * @param numberOfSamples  number of chains starting from each images kept
        * @param localSize        size (in pixel) of the local patch used to refine position of landmarks (cross correlation - not FFT)
        * @param refinementCycles number of refinement step
        * @param corrThreshold    correlation threshold
        * @param useMinima        for critical points true use local minima or false use local maxima
        * @param useCritical      true to use crtical version of algo, false to use grid version
        * @see fr.curie.tomoj.landmarks.LandmarksGenerator#threadGenerateLandmarkSet(int, int, int, int, double, int, boolean, boolean)
        */
       public void generateLandmarkSet2(final int seqLength, final int numberOfSamples, final int localSize, final int refinementCycles, final double corrThreshold, final boolean useMinima, final boolean useCritical, final boolean fuseLandmarks) {

           completion = 0;
           nbThreads = Prefs.getThreads();
           exec = Executors.newFixedThreadPool(nbThreads);
           System.out.println("generate Landmark V2 using " + nbThreads + " threads" + " / " + Runtime.getRuntime().availableProcessors());
           final int gridSamples = (int) Math.ceil(Math.sqrt(numberOfSamples));
           final ArrayList<LandmarksChain> candidateChainList = new ArrayList<LandmarksChain>(1000);
           final Chrono time = new Chrono();
           Chrono totaltime = new Chrono();
           totaltime.start();
           final ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>(1000);
           //
           Future[] seedFuture = new Future[ts.getImageStackSize()];
           for (int i = 0; i < ts.getImageStackSize(); i++) {
               final int ii = i;
               seedFuture[i] = exec.submit(new Thread() {
                   public void run() {
                       if (completion < 0) return;
                       ArrayList<LandmarksChain> tmp = createSeeds(ii, gridSamples, useCritical, useMinima, percentageExcludedX, percentageExcludedY);
                       System.out.println("there are " + tmp.size() + " seeds on image " + ii + " ");
                       IJ.showStatus("create seeds " + (ii + 1) + "/" + ts.getImageStackSize());
                       IJ.showProgress(ii + 1, ts.getImageStackSize());
                       //long totaltimefollow = 0;
                       //long totaltimerefineChain = 0;
                       //follow seeds
                       // final ArrayList<Double> corrQ = new ArrayList<Double>(Q.size());
                       synchronized (Q){Q.addAll(tmp);}
                   }
               });
               //create seeds

           }
           try {
               for (Future f : seedFuture) {
                   f.get();
               }
           } catch (Exception e1) {
               e1.printStackTrace();
           }

           System.out.println("now follow and refine all seeds");
           IJ.showStatus("now follow and refine all seeds");
           Future[] res = new Future[Q.size()];
           final int[] nbfinished = new int[1];
           for (int q = 0; q < Q.size(); q++) {
               //final int ii=i;
               final int qq = q;
               res[q] = exec.submit(new Thread() {
                   public void run() {
                       if (completion < 0) return;
                       //initiate a new landmark chain
                       LandmarksChain lc = Q.get(qq);
                       Point2D.Double[] landmarkchain = lc.getLandmarkchain();
                       // if gold bead try to center correctly the seed
                       if (goldBead) {
                           refineLandmark(lc.getImgIndex(), lc.getImgIndex(), landmarkchain, localSize, false, goldBead);
                       }
                       //follow this landmark
                       //time.start();
                       double corr = followSeed(landmarkchain, lc.getImgIndex(), seqLength, localSize, corrThreshold, useCritical, false);
                       lc.setCorrelation(corr);
                       //time.stop();
                       //totaltimefollow += time.delay();
                       //refine chain
                       //corrQ[q]=refineChainCriticalPoint(landmarkchain,localSize);
                       if (useCritical || (corr > corrThreshold && TomoJPoints.getLandmarkLength(landmarkchain) >= seqLength)) {
                           if (completion < 0) return;
                           //time.start();
                           corr = refineChain(landmarkchain, localSize, refinementCycles, corrThreshold, lookingForGoldBead(), true);
                           lc.setCorrelation(corr);

                           /*if (useCritical) {
                           candidateChainList.add(landmarkchain);
                       } else if (corr > corrThreshold && getLandmarkLength(landmarkchain) >= seqLength) {
                           candidateChainList.add(landmarkchain);
                       }    */
                           //candidateChainList.add(lc);
                           //time.stop();
                           //totaltimerefineChain += time.delay();
                       }
                       synchronized (candidateChainList){candidateChainList.add(lc);}
                       if (completion < 0) return;
                       nbfinished[0]++;
                       completion = nbfinished[0] * (double)ts.getImageStackSize() / Q.size();
                       IJ.showProgress(qq, Q.size());
                       IJ.showStatus("follow seeds (test)" + (nbfinished[0] * 100 / Q.size()) + "%");

                   }

               });

           }
           try {
               for (Future f : res) {
                   f.get();
               }
           } catch (Exception e1) {
               e1.printStackTrace();
           }
           IJ.showProgress(0, 0);

           // System.out.println(i + " time for following seeds "+totaltimefollow/Q.size());
           //System.out.println(i + " time for refine chain "+totaltimerefineChain/Q.size());
           //validate seeds
           /*ArrayList<LandmarksChain> tmp=validateChains(candidateChainList,useCritical,numberOfSamples,corrThreshold,seqLength);
       ArrayList<Point2D.Double[]> tmp2=new ArrayList<Point2D.Double[]>(tmp.size());
       for(LandmarksChain lc: tmp) tmp2.add(lc.getLandmarkchain());
       landmarks.addAll(tmp2);
       System.out.println("image "+i+" : "+printChainsStats(tmp));

       candidateChainList.clear();    */
           completion++;


           //validate
           System.out.println("validate chains before:"+candidateChainList.size());
           IJ.showStatus("validate chains");
           ArrayList<LandmarksChain> tmp = validateChains(candidateChainList, useCritical, numberOfSamples, corrThreshold, seqLength);
           ArrayList<Point2D.Double[]> tmp2 = new ArrayList<Point2D.Double[]>(tmp.size());
           double avgScore = 0;
           double minScore = tmp.get(0).getCorrelation();
           double maxScore = tmp.get(0).getCorrelation();
           double avglength = 0;
           int minLength = ts.getImageStackSize();
           int maxLength = 0;
           for (LandmarksChain lc : tmp) {
               tmp2.add(lc.getLandmarkchain());
               double corr = lc.getCorrelation();
               avgScore += corr;
               if (corr < minScore) minScore = corr;
               if (corr > maxScore) maxScore = corr;
               int length = TomoJPoints.getLandmarkLength(lc.getLandmarkchain());
               avglength += length;
               if (minLength > length) minLength = length;
               if (maxLength < length) maxLength = length;
           }
           avglength /= tmp.size();
           avgScore /= tmp.size();
           landmarks.addAll(tmp2);
           System.out.println(tmp2.size()+" landmarks chains found");
           System.out.println("landmarks length : " + avglength + " (" + minLength + ", " + maxLength + ")");
           System.out.println("landmarks correlation : " + avgScore + " (" + minScore + ", " + maxScore + ")");

           //try to fuse landmarks
           if (fuseLandmarks) {
               System.out.println("try to fuse landmarks : before " + landmarks.size() + " landmarks");
               IJ.showStatus("fuse landmarks");
               landmarks = TomoJPoints.tryToFuseLandmarks(landmarks,2);
               System.out.println("try to fuse landmarks : after " + landmarks.size() + " landmarks");
               tp.removeAllSetsOfPoints();
              for(Point2D[] l:landmarks){
                  tp.addSetOfPoints(l,true);
              }
           }
           // landmarks were created but not the corresponding markers saying if they are automatically generated or manually
           //so create the automatic generation markers
           while (automaticGeneration.size() < landmarks.size()) {
               automaticGeneration.add(true);
           }
           avglength = 0;
           minLength = ts.getImageStackSize();
           maxLength = 0;

           for (int i = 1; i < landmarks.size(); i++) {
               int length = TomoJPoints.getLandmarkLength(landmarks.get(i));
               //if(length<seqLength)System.out.println("#"+i+" : "+length);
               //if(length == 0 )System.out.println("length = 0 !!!! -> "+i);
               avglength += length;
               if (minLength > length) {
                   minLength = length;
                   //System.out.println("changing minlength #"+i+" new minlength: "+length);
               }
               if (maxLength < length) maxLength = length;
           }
           avglength /= landmarks.size();
           System.out.println("landmarks created are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");

           totaltime.stop();
           System.out.println("total computation time " + totaltime.delayString());
           IJ.showStatus("generate landmarks finished in " + totaltime.delayString());
       }


    /**
     * version of generating landmarks in TomoJ with a back and forth validation in the tracking and no refinement<br>
     * it create threads to generate landmarks chains starting from each image.
     *
     * @param seqLength        desired length of landmark chains
     * @param numberOfSamples  number of chains starting from each images kept
     * @param localSize        size (in pixel) of the local patch used to refine position of landmarks (cross correlation - not FFT)
     * @param corrThreshold    correlation threshold
     * @param useMinima        for critical points true use local minima or false use local maxima
     * @param useCritical      true to use crtical version of algo, false to use grid version
     */
    public void generateLandmarkSetWithBackAndForthValidation(final int seqLength, final int numberOfSamples, final int localSize, final double corrThreshold, final boolean useMinima, final boolean useCritical, final boolean fuseLandmarks) {

        completion = 0;
        nbThreads = Prefs.getThreads();
        exec = Executors.newFixedThreadPool(nbThreads);
        System.out.println("generate Landmark V3(back and forth) using " + nbThreads + " threads" + " / " + Runtime.getRuntime().availableProcessors());
        final int gridSamples = (int) Math.ceil(Math.sqrt(numberOfSamples));
        final ArrayList<LandmarksChain> candidateChainList = new ArrayList<LandmarksChain>(1000);
        final Chrono time = new Chrono();
        Chrono totaltime = new Chrono();
        totaltime.start();
        final ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>(1000);
        //
        Future[] seedFuture = new Future[ts.getImageStackSize()];
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            if (completion < 0) return;
            final int ii = i;
            seedFuture[i] = exec.submit(new Thread() {
                public void run() {
                    if (completion < 0) return;
                    ArrayList<LandmarksChain> tmp = createSeeds(ii, gridSamples, useCritical, useMinima, percentageExcludedX, percentageExcludedY);
                    System.out.println("there are " + tmp.size() + " seeds on image " + ii + " ");
                    IJ.showStatus("create seeds " + (ii + 1) + "/" + ts.getImageStackSize());
                    IJ.showProgress(ii + 1, ts.getImageStackSize());
                    //long totaltimefollow = 0;
                    //long totaltimerefineChain = 0;
                    //follow seeds
                    // final ArrayList<Double> corrQ = new ArrayList<Double>(Q.size());
                    synchronized (Q){Q.addAll(tmp);}
                }
            });
            //create seeds

        }
        try {
            for (Future f : seedFuture) {
                f.get();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        System.out.println("now follow and refine all seeds");
        IJ.showStatus("now follow and refine all seeds");
        ArrayList<Future> res = new ArrayList<Future>(Q.size());
        final int[] nbfinished = new int[1];
        for (int q = 0; q < Q.size(); q++) {
            //final int ii=i;
            final int qq = q;
            res.add( exec.submit(new Thread() {
                public void run() {
                    if (completion < 0) return;
                    //initiate a new landmark chain
                    try {
                        LandmarksChain lc = Q.get(qq);
                        if(lc==null){System.out.println("lc null"); System.out.flush();}
                        Point2D.Double[] landmarkchain = lc.getLandmarkchain();
                        // if gold bead try to center correctly the seed
                        if (goldBead) {
                            refineLandmark(lc.getImgIndex(), lc.getImgIndex(), landmarkchain, localSize, false, goldBead);
                        }
                        //follow this landmark
                        //time.start();
                        double corr = followSeedBackAndForth(landmarkchain, lc.getImgIndex(), seqLength, localSize, corrThreshold, useCritical, false);
                        lc.setCorrelation(corr);

                        synchronized (candidateChainList){candidateChainList.add(lc);}
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    if (completion < 0) return;
                    synchronized (nbfinished){nbfinished[0]++;}
                    completion = nbfinished[0] * ts.getImageStackSize() / Q.size();
                    IJ.showProgress(qq, Q.size());
                    IJ.showStatus("follow seeds " + (nbfinished[0] * 100 / Q.size()) + "%");

                }

            }));

        }
        try {
            for (Future f : res) {
                f.get();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        IJ.showProgress(0, 0);

        // System.out.println(i + " time for following seeds "+totaltimefollow/Q.size());
        //System.out.println(i + " time for refine chain "+totaltimerefineChain/Q.size());
        //validate seeds
        /*ArrayList<LandmarksChain> tmp=validateChains(candidateChainList,useCritical,numberOfSamples,corrThreshold,seqLength);
    ArrayList<Point2D.Double[]> tmp2=new ArrayList<Point2D.Double[]>(tmp.size());
    for(LandmarksChain lc: tmp) tmp2.add(lc.getLandmarkchain());
    landmarks.addAll(tmp2);
    System.out.println("image "+i+" : "+printChainsStats(tmp));

    candidateChainList.clear();    */
        completion++;


        //validate
        System.out.println("validate chains (number of candidates "+candidateChainList.size()+")");
        System.out.flush();
        IJ.showStatus("validate chains");
        if (completion < 0) return;
        if (candidateChainList.size() <= 0) IJ.error("no chains could be found while tracking");
        else {
            ArrayList<LandmarksChain> tmp = validateChains(candidateChainList, useCritical, numberOfSamples, corrThreshold, seqLength);
            if(tmp.size()<=0) IJ.error("no chains could be validated as correct");
            else {
                ArrayList<Point2D.Double[]> tmp2 = new ArrayList<Point2D.Double[]>(tmp.size());
                double avgScore = 0;
                double minScore = tmp.get(0).getCorrelation();
                double maxScore = tmp.get(0).getCorrelation();
                double avglength = 0;
                int minLength = ts.getImageStackSize();
                int maxLength = 0;
                for (LandmarksChain lc : tmp) {
                    tmp2.add(lc.getLandmarkchain());
                    double corr = lc.getCorrelation();
                    avgScore += corr;
                    if (corr < minScore) minScore = corr;
                    if (corr > maxScore) maxScore = corr;
                    int length = TomoJPoints.getLandmarkLength(lc.getLandmarkchain());
                    avglength += length;
                    if (minLength > length) minLength = length;
                    if (maxLength < length) maxLength = length;
                }
                avglength /= tmp.size();
                avgScore /= tmp.size();
                landmarks.addAll(tmp2);
                System.out.println("landmarks length : " + avglength + " (" + minLength + ", " + maxLength + ")");
                System.out.println("landmarks correlation : " + avgScore + " (" + minScore + ", " + maxScore + ")");

                //try to fuse landmarks
                if (fuseLandmarks) {
                    if (completion < 0) return;
                    System.out.println("try to fuse landmarks : before " + landmarks.size() + " landmarks");
                    IJ.showStatus("fuse landmarks");
                    landmarks = TomoJPoints.tryToFuseLandmarks(landmarks,2);
                    System.out.println("try to fuse landmarks : after " + landmarks.size() + " landmarks");
                    tp.removeAllSetsOfPoints();
                    for(Point2D[] l:landmarks){
                        tp.addSetOfPoints(l,true);
                    }
                }
                // landmarks were created but not the corresponding markers saying if they are automatically generated or manually
                //so create the automatic generation markers
                if (completion < 0) return;
                while (automaticGeneration.size() < landmarks.size()) {
                    automaticGeneration.add(true);
                }
                avglength = 0;
                minLength = ts.getImageStackSize();
                maxLength = 0;

                for (int i = 1; i < landmarks.size(); i++) {
                    int length = TomoJPoints.getLandmarkLength(landmarks.get(i));
                    //if(length<seqLength)System.out.println("#"+i+" : "+length);
                    //if(length == 0 )System.out.println("length = 0 !!!! -> "+i);
                    avglength += length;
                    if (minLength > length) {
                        minLength = length;
                        //System.out.println("changing minlength #"+i+" new minlength: "+length);
                    }
                    if (maxLength < length) maxLength = length;
                }
                avglength /= landmarks.size();
                System.out.println("landmarks created are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");
            }
        }


        totaltime.stop();
        System.out.println("total computation time " + totaltime.delayString());
        IJ.showStatus("generate landmarks finished in " + totaltime.delayString());
    }

    public ArrayList<LandmarksChain> createSeeds(int index, int gridSamples, boolean useCriticalPoints, boolean useMinima, double percentageExcludedX, double percentageExcludedY) {
        ImageProcessor ip = new FloatProcessor(ts.getWidth(), ts.getHeight());
        ImagePlus imptmp = new ImagePlus("", ip);
        ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>();
        double cx = ts.getCenterX();
        double cy = ts.getCenterY();
        int deltaShiftx = ts.getWidth() / gridSamples;
        int deltaShifty = ts.getHeight() / gridSamples;
        if (useCriticalPoints) {
            ip.setPixels(ts.getOriginalPixels(index));
            ip.resetRoi();
            imptmp.setTitle("" + index);
            //filter the image
            System.out.println("Filtering image " + index);
            getFilteredImageMSD(imptmp, useMinima, false, percentageExcludedX, percentageExcludedY);
            if (completion < 0) return null;
            FloatPolygon r = ((PointRoi) imptmp.getRoi()).getFloatPolygon();
            float[] xcoord = r.xpoints;
            float[] ycoord = r.ypoints;
            //Rectangle rec = r.getBounds();
            for (int i = 0; i < xcoord.length; i++) {
                if (completion < 0) return Q;
                //Point2D.Double pt = new Point2D.Double(xcoord[i] - cx + rec.getX(), ycoord[i] - cy + rec.getY());
                Point2D.Double pt = new Point2D.Double(xcoord[i] - cx, ycoord[i] - cy);
                Point2D.Double[] landmarkchain = new Point2D.Double[ts.getImageStackSize()];
                landmarkchain[index] = pt;
                Q.add(new LandmarksChain(landmarkchain, 0, index));
                /* if(i==0){
                   System.out.println("detected "+(xcoord[0]+rec.getX())+", "+(ycoord[0]+rec.getY())+" -> "+pt);
               } */
            }

        } else {
            //use Grid
            for (int x = 0; x < gridSamples; x++) {
                double xx = Math.round(deltaShiftx * (0.5 + x)) - cx;
                for (int y = 0; y < gridSamples; y++) {
                    double yy = Math.round(deltaShifty * (0.5 + y)) - cy;
                    Point2D.Double pt = new Point2D.Double(xx, yy);
                    Point2D.Double[] landmarkchain = new Point2D.Double[ts.getImageStackSize()];
                    landmarkchain[index] = pt;
                    Q.add(new LandmarksChain(landmarkchain, 0, index));
                    //System.out.println("seed added on image "+ii+" "+pt);
                }
            }
        }

        return Q;
    }

    protected ArrayList<LandmarksChain> validateChains(ArrayList<LandmarksChain> candidateChainList, boolean useCriticalPoints, int numberOfSamples, double corrTh, double seqLength) {
        ArrayList<LandmarksChain> localChainList = new ArrayList<LandmarksChain>(1000);
        int count = 0;
        // ArrayList<Double> finalCorr=new ArrayList<Double>(corrQ.size());
        double cx = ts.getCenterX();
        double cy = ts.getCenterY();
        if (useCriticalPoints) {
            //System.out.println(ii + " refinement finished, now selecting the bests ones ");
            for (int i = 0; i < ts.getImageStackSize(); i++) {
                if (completion < 0) return localChainList;
                //take chain from current image
                ArrayList<LandmarksChain> tmp = new ArrayList<LandmarksChain>();
                for (LandmarksChain lc : candidateChainList) if (lc.getImgIndex() == i) tmp.add(lc);
                if (tmp.size() > 0) {
                    double[] sortedcorr = new double[tmp.size()];
                    for (int t = 0; t < sortedcorr.length; t++) sortedcorr[t] = tmp.get(t).getCorrelation();
                    //Double[] sortedcorr = (Double[])corrQ.toArray();
                    Arrays.sort(sortedcorr);
                    double thr = Math.max(sortedcorr[sortedcorr.length - 1 - Math.min(sortedcorr.length - 1, numberOfSamples - 1)], corrTh);

                    for (LandmarksChain lc : tmp) {
                        if (lc.getCorrelation() >= thr) {
                            Point2D.Double[] centeredchain = lc.getLandmarkchain();
                            Point2D.Double[] finalChain = new Point2D.Double[centeredchain.length];
                            for (int p = 0; p < finalChain.length; p++) {
                                Point2D.Double pt = centeredchain[p];
                                if (pt != null) {
                                    finalChain[p] = new Point2D.Double(pt.getX() + cx, pt.getY() + cy);
                                }
                            }
                            lc.setLandmarkchain(finalChain);
                            localChainList.add(lc);
                        }
                    }
                }
            }
        } else {
            for (LandmarksChain lc : candidateChainList) {
                if (completion < 0) return localChainList;
                Point2D.Double[] landmarkchain = lc.getLandmarkchain();
                if (lc.getCorrelation() > corrTh && TomoJPoints.getLandmarkLength(landmarkchain) >= seqLength) {
                    Point2D.Double[] finalChain = new Point2D.Double[landmarkchain.length];
                    for (int p = 0; p < finalChain.length; p++) {
                        Point2D.Double pt = landmarkchain[p];
                        if (pt != null) {
                            finalChain[p] = new Point2D.Double(pt.getX() + cx, pt.getY() + cy);
                        }
                    }
                    lc.setLandmarkchain(finalChain);
                    localChainList.add(lc);
                }

            }

        }
        return localChainList;
    }

    protected String printChainsStats(ArrayList<LandmarksChain> list) {
        double min = 2;
        double max = -1;
        double avg = 0;
        int minlength = Integer.MAX_VALUE;
        int maxlength = -1;
        double avglength = 0;
        int count = 0;
        for (LandmarksChain lc : list) {
            Point2D.Double[] landmarkchain = lc.getLandmarkchain();
            avg += lc.getCorrelation();
            min = Math.min(min, lc.getCorrelation());
            max = Math.max(max, lc.getCorrelation());
            int length = TomoJPoints.getLandmarkLength(landmarkchain);
            minlength = Math.min(minlength, length);
            maxlength = Math.max(maxlength, length);
            avglength += length;
            count++;
        }
        return count + " chains \t(" + IJ.d2s(min) + " - " + IJ.d2s(max) + ") " + IJ.d2s(avg / count) + "\n\t\t\tlength \t(" + minlength + " - " + maxlength + ") " + IJ.d2s(avglength / count);


    }

    /**
     * the actual version of generating landmarks in TomoJ<br>
     * it create threads to generate landmarks chains starting from each image.
     *
     * @param seqLength     desired length of landmark chains
     * @param NCritical     number of chains starting from each images kept
     * @param localSize     size (in pixel) of the local patch used to refine position of landmarks (cross correlation - not FFT)
     * @param maxStep       number of refinement step
     * @param corrThreshold correlation threshold
     * @param useMinima     for critical points true use local minima or false use local maxima
     * @param useCritical   true to use crtical version of algo, false to use grid version
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#threadGenerateLandmarkSet(int, int, int, int, double, int, boolean, boolean)
     * @deprecated
     */
    public void generateLandmarkSet(final int seqLength, final int NCritical, final int localSize, final int maxStep, final double corrThreshold, final boolean useMinima, final boolean useCritical) {
        completion = 0;
        nbThreads = Prefs.getThreads();
        Thread[] ths = new Thread[nbThreads];
        Chrono time = new Chrono();
        time.start();
        //create threads and starts them
        for (int t = 0; t < nbThreads; t++) {
            final int tt = t;
            ths[t] = new Thread() {
                public void run() {
                    ArrayList<Point2D.Double[]> tmp = null;
                    tmp = threadGenerateLandmarkSet(tt, seqLength, NCritical, localSize, corrThreshold, maxStep, useMinima, useCritical);
                    /*if(useCritical)
                             tmp=threadGenerateLandmarkSetCriticalPoints(tt,seqLength,NCritical,localSize,useMinima);
                         else{
                             int gridSample=(int)Math.floor(Math.sqrt(NCritical));
                             tmp=threadGenerateLandmarkSetGrid(tt,seqLength,gridSample,localSize,maxStep,corrThreshold);
                         }*/
                    System.out.println("thread " + tt + " created " + tmp.size() + " landmarks");
                    //System.out.println("old size of landmarks "+landmarks.size());
                    landmarks.addAll(tmp);
                    //System.out.println("new size of landmarks "+landmarks.size());
                }
            };
            ths[t].start();
        }
        //wait for threads to finish
        for (int p = 0; p < nbThreads; p++) {
            try {
                ths[p].join();
            } catch (Exception e) {
                System.out.println(e);
            }
        }
        //try to fuse landmarks if goldbeads
        System.out.println("try to fuse landmarks : before " + landmarks.size() + " landmarks");
        landmarks = TomoJPoints.tryToFuseLandmarks(landmarks,2);
        tp.removeAllSetsOfPoints();
               for(Point2D[] l:landmarks){
                   tp.addSetOfPoints(l,true);
               }
        System.out.println("try to fuse landmarks : after " + landmarks.size() + " landmarks");

        // landmarks were created but not the corresponding markers saying if they are automatically generated or manually
        //so create the automatic generation markers
        while (automaticGeneration.size() < landmarks.size()) {
            automaticGeneration.add(true);
        }
        double avglength = 0;
        for (int i = 1; i < landmarks.size(); i++) {
            avglength += TomoJPoints.getLandmarkLength(landmarks.get(i));
        }
        avglength /= landmarks.size();
        System.out.println("landmarks created are seen on average on " + avglength + " images");
        time.stop();
        System.out.println("total computation time " + time.delayString());
    }

    /**
     * the computing part of generation of landmarks <br>
     * steps are the following for each image
     * <ul>
     * <li>filter the image (band pass)</li>
     * <li>get the threshold corresponding to the 2% of total pixels that are darkest</li>
     * <li>check if it is a local minima (radius of 4)</li>
     * <li>for each local minima found this way</li>
     * <li>follow in each previous and next images the position of this marker</li>
     * <li>refine the position of the landmarks in the chain</li>
     * <li>compute a correlation score that is the worse correlation between each landmark of the chain and the average</li>
     * <p/>
     * <li>keep only the NCritical chain with the best scores</li>
     * </ul>
     *
     * @param indexThread       used mainly to know at which image to start
     * @param seqLength         desired length of landmarks chains
     * @param numberOfSamples   number of  chains kept for each image
     * @param localSize         size (in pixel) of the local patch to refine and compute correlation scores
     * @param corrTh            correlation threshold above wich chains are not kept must be low in case of critical point use
     * @param refinementCycles  number of refinement cycles
     * @param useMinima         for critical points, true to use local minima, false to use local maxima
     * @param useCriticalPoints true to use the critical points version (best with gold beads) false to use the grid version
     * @return the landmark chains created by this procedure
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#followSeed(Point2D[], int, int, int, double, boolean, boolean)
     * @deprecated
     */
    protected ArrayList<Point2D.Double[]> threadGenerateLandmarkSet(final int indexThread, final int seqLength, final int numberOfSamples, final int localSize, final double corrTh, final int refinementCycles, final boolean useMinima, final boolean useCriticalPoints) {
        double cx = ts.getCenterX();
        double cy = ts.getCenterY();
        int gridSamples = (int) Math.ceil(Math.sqrt(numberOfSamples));
        int deltaShiftx = ts.getWidth() / gridSamples;
        int deltaShifty = ts.getHeight() / gridSamples;
        ArrayList<Point2D.Double[]> localChainList = new ArrayList<Point2D.Double[]>(1000);
        ArrayList<Point2D.Double[]> candidateChainList = new ArrayList<Point2D.Double[]>(1000);
        int halfSeqLength = seqLength / 2;
        ImageProcessor ip = new FloatProcessor(ts.getWidth(), ts.getHeight());
        ImagePlus imptmp = new ImagePlus("", ip);
        FFTFilter_TomoJ fftf;
        int startx = (int) (0.05 * ip.getWidth());
        int starty = (int) (0.05 * ip.getHeight());
        int endx = (int) (0.95 * ip.getWidth());
        int endy = (int) (0.95 * ip.getHeight());
        int[] hist;
        int sum;
        double th;
        double maxCount;
        Chrono time = new Chrono();
        for (int ii = indexThread; ii < ts.getImageStackSize(); ii += nbThreads) {
            if (completion < 0) return localChainList;
            double min = 2;
            double max = -1;
            double avg = 0;
            int minlength = Integer.MAX_VALUE;
            int maxlength = 0;
            double avglength = 0;
            int keptCount = 0;
            long totaltimefollow = 0;
            long totaltimerefineChain = 0;
            ArrayList<Point2D.Double> Q = new ArrayList<Point2D.Double>();
            int count = 0;
            time.start();
            if (useCriticalPoints) {
                ip.setPixels(ts.getOriginalPixels(ii));
                ip.resetRoi();
                imptmp.setTitle("" + ii);
                //filter the image
                System.out.println("Filtering image " + ii);
                getFilteredImage(imptmp, useMinima, false);
                if (completion < 0) return localChainList;
                FloatPolygon r = ((PointRoi) imptmp.getRoi()).getFloatPolygon();
                float[] xcoord = r.xpoints;
                float[] ycoord = r.ypoints;
                //Rectangle rec = r.getBounds();
                for (int i = 0; i < xcoord.length; i++) {
                    //Point2D.Double pt = new Point2D.Double(xcoord[i] - cx + rec.getX(), ycoord[i] - cy + rec.getY());
                    Point2D.Double pt = new Point2D.Double(xcoord[i] - cx, ycoord[i] - cy);
                    Q.add(pt);
                    count++;
                    /* if(i==0){
                       System.out.println("detected "+(xcoord[0]+rec.getX())+", "+(ycoord[0]+rec.getY())+" -> "+pt);
                   } */
                }

            } else {
                //use Grid
                for (int x = 0; x < gridSamples; x++) {
                    double xx = Math.round(deltaShiftx * (0.5 + x)) - cx;
                    for (int y = 0; y < gridSamples; y++) {
                        double yy = Math.round(deltaShifty * (0.5 + y)) - cy;
                        Point2D.Double pt = new Point2D.Double(xx, yy);
                        Q.add(pt);
                        //System.out.println("seed added on image "+ii+" "+pt);
                        count++;
                    }
                }
            }
            time.stop();
            System.out.println(ii + " time creation of seeds " + time.delayString());
            System.out.println("there are " + count + " seeds on image " + ii + " now follow & refine");
            IJ.showStatus("there are " + count + " seeds on image " + ii + " now follow & refine");
            double[] corrQ = new double[count];
            for (int q = 0; q < count; q++) {
                if (completion < 0) return localChainList;
                //initiate a new landmark chain
                Point2D.Double[] landmarkchain = new Point2D.Double[ts.getImageStackSize()];
                landmarkchain[ii] = Q.get(q);
                // if gold bead try to center correctly the seed
                if (goldBead) {
                    refineLandmark(ii, ii, landmarkchain, localSize, false, goldBead);
                }
                //follow this landmark
                time.start();
                double corr = followSeed(landmarkchain, ii, seqLength, localSize, corrTh, useCriticalPoints, false);
                time.stop();
                totaltimefollow += time.delay();
                //refine chain
                //corrQ[q]=refineChainCriticalPoint(landmarkchain,localSize);
                if (useCriticalPoints || (corr > corrTh && TomoJPoints.getLandmarkLength(landmarkchain) >= seqLength)) {
                    if (completion < 0) return localChainList;
                    time.start();
                    corr = refineChain(landmarkchain, localSize, refinementCycles, corrTh, lookingForGoldBead(), true);
                    corrQ[q] = corr;

                    if (useCriticalPoints) {
                        candidateChainList.add(landmarkchain);
                    } else if (corr > corrTh && TomoJPoints.getLandmarkLength(landmarkchain) >= seqLength) {
                        //System.out.println("keeping corr="+corr+" th="+corrTh+" length="+getLandmarkLength(landmarkchain)+" minlenght="+seqLength);
                        Point2D.Double[] finalChain = new Point2D.Double[landmarkchain.length];
                        for (int p = 0; p < finalChain.length; p++) {
                            Point2D.Double pt = landmarkchain[p];
                            if (pt != null) {
                                finalChain[p] = new Point2D.Double(pt.getX() + cx, pt.getY() + cy);
                            }
                        }
                        localChainList.add(finalChain);
                        avg += corr;
                        min = Math.min(min, corr);
                        max = Math.max(max, corr);
                        int length = TomoJPoints.getLandmarkLength(finalChain);
                        minlength = Math.min(minlength, length);
                        maxlength = Math.max(maxlength, length);
                        avglength += length;
                        keptCount++;
                    }
                    time.stop();
                    totaltimerefineChain += time.delay();
                }

            }

            System.out.println(ii + " time for following seeds " + totaltimefollow / count);
            System.out.println(ii + " time for refine chain " + totaltimerefineChain / count);
            //sort all chains according to its correlation for critical points
            time.start();
            if (useCriticalPoints) {
                //System.out.println(ii + " refinement finished, now selecting the bests ones ");
                double[] sortedcorr = Arrays.copyOf(corrQ, corrQ.length);
                Arrays.sort(sortedcorr);
                double thr = Math.max(sortedcorr[sortedcorr.length - 1 - Math.min(sortedcorr.length - 1, numberOfSamples - 1)], corrTh);
                count = 0;
                min = 2;
                max = -1;
                avg = 0;
                for (int q = 0; q < corrQ.length; q++) {
                    if (corrQ[q] >= thr) {
                        Point2D.Double[] centeredchain = candidateChainList.get(q);
                        Point2D.Double[] finalChain = new Point2D.Double[centeredchain.length];
                        for (int p = 0; p < finalChain.length; p++) {
                            Point2D.Double pt = centeredchain[p];
                            if (pt != null) {
                                finalChain[p] = new Point2D.Double(pt.getX() + cx, pt.getY() + cy);
                            }
                        }
                        min = Math.min(min, corrQ[q]);
                        max = Math.max(max, corrQ[q]);
                        avg += corrQ[q];
                        localChainList.add(finalChain);
                        //System.out.println("chain "+q+" on image "+ii+"added to Chainlist "+indexThread+" score "+corrQ[q]);
                        count++;
                    }
                }
                avg /= count;
                //avglength/=count;
                candidateChainList.clear();
                System.out.println(count + " chains start from image " + ii + " (" + IJ.d2s(min) + " - " + IJ.d2s(max) + ") " + IJ.d2s(avg));

                IJ.showStatus(count + " chains start from image " + ii + " (" + IJ.d2s(min) + " - " + IJ.d2s(max) + ") " + IJ.d2s(avg));
            } else {
                System.out.println(keptCount + " chains start from image " + ii + "\t(" + IJ.d2s(min) + " - " + IJ.d2s(max) + ") " + IJ.d2s(avg / keptCount) + "\n\t\t\tlength \t(" + minlength + " - " + maxlength + ") " + IJ.d2s(avglength / keptCount));
                IJ.showStatus(keptCount + " chains start from image " + ii + " (" + IJ.d2s(min) + " - " + IJ.d2s(max) + ") " + IJ.d2s(avg / keptCount));
            }
            time.stop();
            System.out.println(ii + " time for post-processing (critical) " + time.delayString());
            completion++;
        }
        return localChainList;
    }

    public ImageProcessor getFilteredImage(ImagePlus input, boolean useMinima, boolean show) {
        ImageProcessor ip = input.getProcessor();
        int startx = (int) (0.05 * ip.getWidth());
        int starty = (int) (0.05 * ip.getHeight());
        int endx = (int) (0.95 * ip.getWidth());
        int endy = (int) (0.95 * ip.getHeight());

        FFTFilter_TomoJ fftf = new FFTFilter_TomoJ();
        fftf.setup("filter_large=" + critical_FilterLarge + " filter_small=" + critical_FilterSmall + " suppress=None tolerance=5", input);
        fftf.run(ip);
        if (completion < 0) return ip;
        ImageProcessor ipfilter = null;
        if (show) {
            ipfilter = ip.duplicate();
        }
        //Identify low valued points and perform dilatation
        float[] pixs = (float[]) ip.getPixels();
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
                float value = ip.getPixelValue(x, y);
                boolean localMinima = true;
                for (int yy = y - critical_MinimaRadius; yy <= y + critical_MinimaRadius; yy++) {
                    int yyy = yy * ip.getWidth();
                    for (int xx = x - critical_MinimaRadius; xx <= x + critical_MinimaRadius; xx++) {
                        if (xx > startx && xx < endx && yy > starty && yy < endy) {
                            //if ((useMinima && value > ip.getPixelValue(xx, yy)) || (!useMinima && value < ip.getPixelValue(xx, yy))) {
                            if ((useMinima && value > pixs[yyy + xx]) || (!useMinima && value < pixs[yyy + xx])) {
                                localMinima = false;
                                break;
                            }
                        }
                    }
                    if (!localMinima) ip.putPixelValue(x, y, 0);
                }
            }
        }
        if (completion < 0) return ip;
        ip.setRoi(startx, starty, (int) (0.9 * ip.getWidth()), (int) (0.9 * ip.getHeight()));
        ImageStatistics stat = new FloatStatistics(ip);
        int[] hist = stat.histogram;
        int sum = 0;
        double th = 0;
        int maxCount = 0;
        for (int aHist : hist) maxCount += aHist;

        for (int i = 0; i < hist.length; i++) {
            if (useMinima) {
                sum += hist[i];
            } else {
                sum += hist[hist.length - 1 - i];
            }
            if (sum >= critical_SeedNumber) {
                if (useMinima)
                    th = i;
                else th = hist.length - 1 - i;
                break;
            }
        }
        th /= 256;
        th *= (stat.max - stat.min);
        th += (stat.min);
        PointRoi r = null;
        // System.out.println("threshold ="+th);
        if (completion < 0) return ip;
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
                float value = ip.getPixelValue(x, y);
                if ((useMinima && value < th) || (!useMinima && value > th)) {
                    if (r == null) {
                        r = new PointRoi(x, y);
                        //System.out.println("create point roi "+x+", "+y);
                    } else {
                        r = r.addPoint(x, y);
                        //System.out.println("add point "+x+", "+y);
                    }
                    /*
                        Point2D.Double pt = new Point2D.Double(x - cx, y - cy);
                        Q.add(pt);
                        //System.out.println("seed added on image "+ii+" "+pt);
                        count++;
                        //ipL.putPixel(x,y,count);
                    */
                }
            }
        }
        input.setRoi(r);
        if (show) {
            input.getProcessor().copyBits(ipfilter, 0, 0, Blitter.COPY);
            //input.show();
        }
        return ip;
    }

    public ImageProcessor getFilteredImageMSD(ImagePlus input, boolean useMinima, boolean show, double removepercentageX, double removepercentageY) {
        ImageProcessor ip = input.getProcessor();
        //System.out.println("getfilteredImage: ip width:"+ip.getWidth()+", height:"+ip.getHeight());
        int startx = (int) (removepercentageX / 2 * ip.getWidth());
        int starty = (int) (removepercentageY / 2 * ip.getHeight());
        int endx = (int) ((1 - removepercentageX / 2) * ip.getWidth());
        int endy = (int) ((1 - removepercentageY / 2) * ip.getHeight());

        if (completion < 0) return ip;
        fftf = new FFTFilter_TomoJ();
        fftf.setup("filter_large=" + critical_FilterLarge + " filter_small=" + critical_FilterSmall + " suppress=None tolerance=5", input);
        fftf.run(ip);
        if (completion < 0) return ip;
        ImageProcessor ipfilter = null;
        if (show) {
            ipfilter = ip.duplicate();
        }
        //Identify low valued points and perform dilatation
        float[] pixs = (float[]) ip.duplicate().getPixels();
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
                //float value = ip.getPixelValue(x, y);
                float value = pixs[y * ip.getWidth() + x];
                double msd = 0;
                boolean localMinima = true;
                if (completion < 0) return ip;
                for (int yy = y - critical_MinimaRadius; yy <= y + critical_MinimaRadius; yy++) {
                    int yyy = yy * ip.getWidth();
                    for (int xx = x - critical_MinimaRadius; xx <= x + critical_MinimaRadius; xx++) {
                        if (completion < 0) return ip;
                        if (xx > startx && xx < endx && yy > starty && yy < endy) {
                            //if ((useMinima && value > ip.getPixelValue(xx, yy)) || (!useMinima && value < ip.getPixelValue(xx, yy))) {
                            msd += Math.abs(value - pixs[yyy + xx]);
                            if ((useMinima && value > pixs[yyy + xx]) || (!useMinima && value < pixs[yyy + xx])) {
                                localMinima = false;
                                break;
                            }
                        }
                    }
                    if (!localMinima) ip.putPixelValue(x, y, 0);
                    else ip.putPixelValue(x, y, msd);
                }
            }
        }
        if (completion < 0) return ip;
        ip.setRoi(startx, starty, (int) ((1 - percentageExcludedX) * ip.getWidth()), (int) ((1 - percentageExcludedY) * ip.getHeight()));
        ImageStatistics stat = new FloatStatistics(ip);
        int[] hist = stat.histogram;
        int sum = 0;
        double th = 0;
        int maxCount = 0;
        if (completion < 0) return ip;
        for (int aHist : hist) maxCount += aHist;
        if (completion < 0) return ip;
        for (int i = 0; i < hist.length; i++) {
            //if (useMinima) {
            //    sum += hist[i];
            //} else {
            sum += hist[hist.length - 1 - i];
            //}
            if (sum > critical_SeedNumber) {
                th = hist.length - 1 - i;
                break;
            }
            //System.out.println("sum="+sum+"threshold ="+th);
        }
        //System.out.println("threshold ="+th);
        th /= 256;
        th *= (stat.max - stat.min);
        th += (stat.min);
        PointRoi r = null;
        //System.out.println("threshold ="+th);
        if (completion < 0) return ip;
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
                if (completion < 0) return ip;
                float value = ip.getPixelValue(x, y);
                //if ((useMinima && value < th) || (!useMinima && value > th)) {
                if (value > th) {
                    if (r == null) {
                        r = new PointRoi(x, y);
                        //System.out.println("create point roi "+x+", "+y);
                    } else {
                        r = r.addPoint(x, y);
                        //System.out.println("add point "+x+", "+y);
                    }
                       /*
                           Point2D.Double pt = new Point2D.Double(x - cx, y - cy);
                           Q.add(pt);
                           //System.out.println("seed added on image "+ii+" "+pt);
                           count++;
                           //ipL.putPixel(x,y,count);
                       */
                }
            }
        }
        input.setRoi(r);
        if (show) {
            input.getProcessor().copyBits(ipfilter, 0, 0, Blitter.COPY);
            //input.show();
        }
        return ip;
    }

    /**
     * the method to follow a landmark on previous and next images from a seed this method is the one used by generate landmarks <br>
     * <ol>
     * <li>it goes on previous image</li>
     * <li>evaluate theoritical position of landmark using transform between the 2 images</li>
     * <li>refine the landmark position</li>
     * <li>do 1-3 with current image as seed</li>
     * <li>do 1-4 with next images instead of previous</li>
     * </ol>
     *
     * @param landmarkchain     the chain to update
     * @param ii                initial index
     * @param SeqLength         the minimum length of chain or total length of chain (critical point)
     * @param localSize         size of the patch used in refinement by local refinement
     * @param corrThreshold     minimum correlation to follow on next images
     * @param useCriticalPoints if true follow in each direction until SeqLength is attained, if false continue as long as correlation is above corrThreshold
     * @return a landmark chain
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#generateLandmarkSet(int, int, int, int, double, boolean, boolean)
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#refineLandmark(int, int, Point2D[], int, boolean, boolean)
     */

    public double followSeed(Point2D[] landmarkchain, int ii, int SeqLength, int localSize, double corrThreshold, boolean useCriticalPoints, boolean FFT) {
        int halfSeqLength = SeqLength / 2;
        boolean cont = true;
        //follow landmark backward
        int jjmin = Math.max(0, ii - halfSeqLength);
        int jjmax = ii - 1;
        if (!useCriticalPoints) {
            jjmin = 0;
            jjmax = ts.getImageStackSize() - 1;
        }
        AffineTransform Aij;
        AffineTransform Aji;
        Point2D rcurrent = landmarkchain[ii];
        if (rcurrent == null) return -1;
        Point2D.Double rjj;
        double mincorr = 2;
        double corr;
        int jj_1;
        //for(int jj=jjmax;jj>=jjmin;jj--){
        int jj = ii - 1;
        double[] tx = new double[landmarkchain.length];
        double[] ty = new double[landmarkchain.length];

        while (cont && jj >= jjmin) {
            if (completion < 0) return mincorr;
            if (landmarkchain[jj] == null) {
                //compute the affine transformation between jj and jj+1
                jj_1 = jj + 1;

                //Aij = ts.getTransform(jj, true, false);
                Aij = ts.getAlignment().getTransform(jj);
                try {
                    //System.out.println("getting transform finished");
                    Aji = Aij.createInverse();
                    //System.out.println("computing inverse finished Aji="+Aji+" rcurrent="+rcurrent);
                    rjj = (Point2D.Double) Aji.transform(rcurrent, null);
                    //System.out.println("computing new point finished rjj="+rjj);
                    //landmarkchain[jj]=rjj;
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);

                    //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    //System.out.println("before refine landmark was "+landmarkchain[jj]);
                    corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                    //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                    // tx[jj]=0;
                    // ty[jj]=0;
                    //System.out.println("after refine landmark is "+landmarkchain[jj]);
                    cont = corr > corrThreshold || useCriticalPoints;
                    if (cont) {
                        //System.out.println(""+jj+" rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                        mincorr = Math.min(mincorr, corr);
                        rcurrent = landmarkchain[jj];
                    } else {
                        landmarkchain[jj] = null;
                    }
                } catch (Exception e) {
                    System.out.println("TomoJPoints followseed error backward " + e);
                }
            }
            jj--;

        }
        if (completion < 0) return mincorr;
        //follow landmark forward
        if (useCriticalPoints) {
            jjmin = ii + 1;
            jjmax = Math.min(ts.getImageStackSize() - 1, ii + halfSeqLength);
        }
        rcurrent = landmarkchain[ii];
        jj = ii + 1;
        cont=true;
        while (cont && jj <= jjmax) {
            if (completion < 0) return mincorr;
            //for(int jj=jjmin;jj<=jjmax;jj++){
            //compute the affine transform between jj-1 and jj
            if (landmarkchain[jj] == null) {
                jj_1 = jj - 1;

                //Aij = ts.getTransform(jj_1, true, false);
                Aij = ts.getAlignment().getTransform(jj_1);
                rjj = (Point2D.Double) Aij.transform(rcurrent, null);
                //landmarkchain[jj] = rjj;
                landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);

                //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                //System.out.println("before refine landmark was "+landmarkchain[jj]);
                corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                //System.out.println("Aij="+Aij);
                //System.out.println(""+jj+" before refine landmark (2) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                // System.out.println("before refine landmark was "+landmarkchain[jj]);
                //corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                //System.out.println("after refine landmark is "+landmarkchain[jj]);
                cont = corr > corrThreshold || useCriticalPoints;
                if (cont) {
                    rcurrent = landmarkchain[jj];
                    mincorr = Math.min(mincorr, corr);
                } else {
                    landmarkchain[jj] = null;
                }
            }
            jj++;
        }
        return mincorr;
    }

    /**
     * the method to follow a landmark on previous and next images from a seed this method is the one used by generate landmarks <br>
     * <ol>
     * <li>it goes on previous image</li>
     * <li>evaluate theoritical position of landmark using transform between the 2 images</li>
     * <li>refine the landmark position</li>
     * <li>do 1-3 with current image as seed</li>
     * <li>do 1-4 with next images instead of previous</li>
     * </ol>
     *
     * @param landmarkchain     the chain to update
     * @param ii                initial index
     * @param SeqLength         the minimum length of chain or total length of chain (critical point)
     * @param localSize         size of the patch used in refinement by local refinement
     * @param corrThreshold     minimum correlation to follow on next images
     * @param useCriticalPoints if true follow in each direction until SeqLength is attained, if false continue as long as correlation is above corrThreshold
     * @return a landmark chain
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#generateLandmarkSet(int, int, int, int, double, boolean, boolean)
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#refineLandmark(int, int, Point2D[], int, boolean, boolean)
     */
    public double followSeedBackAndForth(Point2D.Double[] landmarkchain, int ii, int SeqLength, int localSize, double corrThreshold, boolean useCriticalPoints, boolean FFT) {
        int halfSeqLength = SeqLength / 2;
        boolean cont = true;
        //follow landmark backward
        int jjmin = Math.max(0, ii - halfSeqLength);
        int jjmax = ii - 1;
        if (!useCriticalPoints) {
            jjmin = 0;
            jjmax = ts.getImageStackSize() - 1;
        }
        AffineTransform Aij;
        AffineTransform Aji;
        Point2D.Double rcurrent = landmarkchain[ii];
        if (rcurrent == null) return -1;
        Point2D.Double rjj;
        double mincorr = 2;
        double corr1, corr2;
        int jj_1;
        //for(int jj=jjmax;jj>=jjmin;jj--){
        int jj = ii - 1;
        double[] tx = new double[landmarkchain.length];
        double[] ty = new double[landmarkchain.length];

        while (cont && jj >= jjmin) {
            if (completion < 0) return mincorr;
            if (landmarkchain[jj] == null) {
                //compute the affine transformation between jj and jj+1
                jj_1 = jj + 1;
                //Aij = ts.getTransform(jj, true, false);
                Aij = ts.getAlignment().getTransform(jj);
                try {
                    //System.out.println("getting transform finished");
                    Aji = Aij.createInverse();
                    //System.out.println("computing inverse finished Aji="+Aji+" rcurrent="+rcurrent);
                    Point2D.Double rRef = new Point2D.Double(rcurrent.getX(), rcurrent.getY());
                    rjj = (Point2D.Double) Aji.transform(rcurrent, null);
                    //System.out.println("computing new point finished rjj="+rjj);
                    //landmarkchain[jj]=rjj;
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);

                    //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    //System.out.println("before refine landmark was "+landmarkchain[jj]);
                    corr1 = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    rjj = (Point2D.Double) Aij.transform(landmarkchain[jj], null);
                    landmarkchain[jj_1] = new Point2D.Double(rjj.getX() - tx[jj_1], rjj.getY() - ty[jj_1]);
                    corr2 = refineLandmark(jj, jj_1, landmarkchain, localSize, FFT, goldBead);
                    if (Math.sqrt((landmarkchain[jj_1].getX() - rRef.getX()) * (landmarkchain[jj_1].getX() - rRef.getX()) + (landmarkchain[jj_1].getY() - rRef.getY()) * (landmarkchain[jj_1].getY() - rRef.getY())) > 4) {
                        //the backward correlation is not the same I put a malus
                        corr1 -= 0.5;
                        corr2 -= 0.5;
                        landmarkchain[jj_1].setLocation(rRef);
                    } else {
                        landmarkchain[jj_1].setLocation(rRef);
                    }

                    //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                    //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                    // tx[jj]=0;
                    // ty[jj]=0;
                    //System.out.println("after refine landmark is "+landmarkchain[jj]);
                    cont = Math.min(corr1, corr2) > corrThreshold || useCriticalPoints;
                    if (cont) {
                        //System.out.println(""+jj+" rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                        mincorr = Math.min(mincorr, Math.min(corr1, corr2));
                        rcurrent = landmarkchain[jj];
                    } else {
                        landmarkchain[jj] = null;
                    }
                } catch (Exception e) {
                    System.out.println("TomoJPoints followseed error backward " + e);
                }
            }
            jj--;

        }
        if (completion < 0) return mincorr;
        //follow landmark forward
        if (useCriticalPoints) {
            jjmin = ii + 1;
            jjmax = Math.min(ts.getImageStackSize() - 1, ii + halfSeqLength);
        }
        rcurrent = landmarkchain[ii];
        jj = ii + 1;
        cont=true;
        while (cont && jj <= jjmax) {
            if (completion < 0) return mincorr;
            //for(int jj=jjmin;jj<=jjmax;jj++){
            //compute the affine transform between jj-1 and jj
            if (landmarkchain[jj] == null) {
                jj_1 = jj - 1;
                //Aij = ts.getTransform(jj_1, true, false);
                Aij = ts.getAlignment().getTransform(jj_1);
                try {
                    rjj = (Point2D.Double) Aij.transform(rcurrent, null);
                    //landmarkchain[jj] = rjj;
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);
                    Point2D.Double rRef = new Point2D.Double(rcurrent.getX(), rcurrent.getY());
                    //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    //System.out.println("before refine landmark was "+landmarkchain[jj]);
                    //corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    corr1 = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    Aji = Aij.createInverse();
                    rjj = (Point2D.Double) Aji.transform(landmarkchain[jj], null);
                    landmarkchain[jj_1] = new Point2D.Double(rjj.getX() - tx[jj_1], rjj.getY() - ty[jj_1]);
                    corr2 = refineLandmark(jj, jj_1, landmarkchain, localSize, FFT, goldBead);
                    if (Math.sqrt((landmarkchain[jj_1].getX() - rRef.getX()) * (landmarkchain[jj_1].getX() - rRef.getX()) + (landmarkchain[jj_1].getY() - rRef.getY()) * (landmarkchain[jj_1].getY() - rRef.getY())) > 4) {
                        //the backward correlation is not at the same location I put a malus
                        corr1 -= 0.5;
                        corr2 -= 0.5;
                        landmarkchain[jj_1].setLocation(rRef);
                    } else {
                        landmarkchain[jj_1].setLocation(rRef);
                    }
                    //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                    //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                    //System.out.println("Aij="+Aij);
                    //System.out.println(""+jj+" before refine landmark (2) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    // System.out.println("before refine landmark was "+landmarkchain[jj]);
                    //corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    //System.out.println("after refine landmark is "+landmarkchain[jj]);
                    //cont = corr > corrThreshold || useCriticalPoints;
                    cont = Math.min(corr1, corr2) > corrThreshold || useCriticalPoints;
                    if (cont) {
                        rcurrent = landmarkchain[jj];
                        mincorr = Math.min(mincorr, Math.min(corr1, corr2));
                    } else {
                        landmarkchain[jj] = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            jj++;
        }
        return mincorr;
    }



    /**
     * refine the chain of landmark :<BR>
     * <UL><LI>by refining landmarks with the average image of all landmarks in the chain </LI>
     * <LI>by refining all alandmarks in the chain with its neighbor</LI></UL>
     *
     * @param chain         the chain to refine
     * @param localSize     size of local patch fo refinement of landmarks
     * @param nbrefinement  how many time do I do this refinement
     * @param corrThreshold threshold for correlation to keep landmarks In the second case of refinement
     * @param goldbead      true to use refinement with average, false for refinement with neighbor
     * @return correlation score of the chain after refinement
     */
    public double refineChain(Point2D.Double[] chain, int localSize, int nbrefinement, double corrThreshold, boolean goldbead, boolean allowRemoval) {
        //if (showInIJ) IJ.log("refine chain");
        double corrChain = 2; // this is the minimum correlation of the chain
        float[] sum;
        for (int K = 0; K < nbrefinement; K++) {
            if (goldbead) {
                //compute the average piece
                sum = computeAverage(chain, localSize);
                if (sum == null) {
                    return -1;
                }
                //System.out.println("should show the average!");
                //new ImagePlus("avg",new FloatProcessor(localSize,localSize,sum,null)).show();
                //align all images with respect to this average
                corrChain = 2;
                for (int i = 0; i < chain.length; i++) {
                    Point2D.Double pt = chain[i];
                    if (pt != null) {
                        corrChain = Math.min(corrChain, refineLandmark(sum, i, chain, localSize));
                        //System.out.println("correlation image "+i+" with average="+corr);
                    }
                }
            } else {
                //refine every step
                for (int step = 0; step < nbrefinement; step++) {
                    //refine forward
                    int ileft = -1;
                    for (int i = 0; i < chain.length - 1 - step; i++) {
                        Point2D.Double rii = chain[i];
                        if (rii != null) {
                            int j = i + 1;
                            Point2D.Double rjj = chain[j];
                            if (rjj != null) {
                                Point2D.Double oldrjj = (Point2D.Double) rjj.clone();
                                double corr = refineLandmark(i, j, chain, localSize, false, goldbead);
                                if (corr < corrThreshold) {
                                    ileft = i;
                                    chain[j] = oldrjj;
                                }
                            }
                        }
                    }
                    //System.out.println("refinement after first step length="+getLandmarkLength(chain)+" ileft="+ileft);
                    //refine backward
                    int iright = chain.length;
                    corrChain = 2;
                    for (int i = chain.length - 1; i > 0; i--) {
                        Point2D.Double rii = chain[i];
                        if (rii != null) {
                            int j = i - 1;
                            Point2D.Double rjj = chain[j];
                            if (rjj != null) {
                                Point2D.Double oldrjj = (Point2D.Double) rjj.clone();
                                double corr = refineLandmark(i, j, chain, localSize, false, goldbead);
                                if (corr < corrThreshold) {
                                    iright = i;
                                    chain[j] = oldrjj;
                                } else {
                                    corrChain = Math.min(corrChain, corr);
                                }
                            }
                        }
                    }
//System.out.println("refinement after secod step length="+getLandmarkLength(chain)+" ileft="+ileft);
                    //remove bad landmarks from chain
                    if (allowRemoval) {
                        for (int i = 0; i < chain.length; i++) {
                            Point2D.Double rii = chain[i];
                            if (rii != null) {
                                if (i <= ileft || i >= iright) {
                                    chain[i] = null;
                                }
                            }
                        }
                    }
                }
            }

        }
        if (corrChain > 1.1) corrChain = -1.1;
        return corrChain;
    }

    /**
        * refine a landmark position by local crosscorrelation with a reference landmark image<br>
        *
        * @param pieceii   landmark image used as reference
        * @param jj        index of second image
        * @param landmark  array containing the landmark at the corresponding indexes
        * @param localSize size in pixel to use in local crosscorrelation
        * @return the minimum correlation score between two landmark images
        */
       public double refineLandmark(float[] pieceii, int jj, Point2D[] landmark, int localSize) {
           //normalize pieceii (reference)
           FloatProcessor fpii = new FloatProcessor(localSize, localSize, pieceii, null);
           ImageStatistics stat = new FloatStatistics(fpii);
           for (int i = 0; i < pieceii.length; i++) {
               pieceii[i] = (float) ((pieceii[i] - stat.mean) / stat.stdDev);
           }

           //try all possible shifts
           int corrSize = (int) (1.5 * localSize);
           int hcx = corrSize / 2;
           double[] corr = new double[corrSize * corrSize];
           Arrays.fill(corr, -1.1);
           double maxval = -1.1;
           ts.setAlignmentRoi(localSize, localSize);
           double imax = 0;
           double jmax = 0;
           ArrayList<Point2D.Double> Q = new ArrayList<Point2D.Double>(localSize * localSize);
           int halfSize = localSize / 2;
           Point2D.Double tmp1 = new Point2D.Double(0, 0);
           float[] piecejj;
           double corrRef;
           double newshiftx;
           double newshifty;
           int j;
           int step;
           int stepx, stepy;
           Point2D.Double pt;
           FloatProcessor fp = new FloatProcessor(localSize, localSize);
           Q.add(tmp1);
           while (!Q.isEmpty()) {
               //remove first position to evaluate
               tmp1 = Q.remove(0);
               //if not already tested
               if (corr[(int) (tmp1.getX() + hcx + (tmp1.getY() + hcx) * corrSize)] == -1.1) {
                   //select region in image jj and normalize
                   piecejj = ts.getSubImagePixels(jj, localSize, localSize, new Point2D.Double(landmark[jj].getX() + tmp1.getX(), landmark[jj].getY() + tmp1.getY()), false, false);
                   //float[] piecejj=ts.getPixelsForAlignment(jj,new Point2D.Double(landmark[jj].getX() + tmp1.getX(), landmark[jj].getY() + tmp1.getY()));
                   fp.setPixels(piecejj);
                   stat = new FloatStatistics(fp);
                   for (j = 0; j < piecejj.length; j++) {
                       piecejj[j] = (float) ((piecejj[j] - stat.mean) / stat.stdDev);
                   }
                   //compute correlation
                   corrRef = correlation(pieceii, piecejj);
                   //System.out.println("testing (" + tmp1.getX() + ", " + tmp1.getY() + ") score=" + corrRef + " maxval=" + maxval);
                   corr[(int) (tmp1.getX() + hcx + (tmp1.getY() + hcx) * corrSize)] = corrRef;
                   if (corrRef > maxval) {
                       maxval = corrRef;
                       imax = tmp1.getX();
                       jmax = tmp1.getY();
                       //System.out.println("updating maxval=" + maxval + " imax=" + imax + " jmax=" + jmax);
                       for (step = 1; step <= 5; step += 2) {
                           for (stepy = -1; stepy <= 1; stepy++) {
                               for (stepx = -1; stepx <= 1; stepx++) {
                                   newshiftx = tmp1.getX() + stepx * step;
                                   newshifty = tmp1.getY() + stepy * step;
                                   if (newshiftx >= -hcx && newshiftx < hcx && newshifty >= -hcx && newshifty < hcx) {
                                       if (corr[(int) (newshiftx + hcx + (newshifty + hcx) * corrSize)] < -1) {
                                           pt = new Point2D.Double(newshiftx, newshifty);
                                           if (tp.isInsideImage(pt, halfSize)) {
                                               //Q.add(new Point2D.Double(newshiftx, newshifty));
                                               Q.add(pt);
                                           }
                                       }
                                   }
                               }
                           }
                       }

                   }
               }
           }
           landmark[jj] = new Point2D.Double(landmark[jj].getX() + imax, landmark[jj].getY() + jmax);
           //ts.applyTransforms(true);
           //if (maxval > corrThreshold) {
           //	landmark[jj] = new Point2D.Double(landmark[jj].getX() + imax, landmark[jj].getY() + jmax);
           //float[] tmp2 = ts.getSubImagePixels(jj, sx, sx, new Point2D.Double(landmark[jj].getX(), landmark[jj].getY()));
           //new ImagePlus("refined" + jj, new FloatProcessor(sx, sx, tmp2, null)).show();
           //	return true;
           //}
           //return false;
           return maxval;
       }


    public float[] computeAverage(Point2D.Double[] chain, int sx) {
        int halfsize = sx / 2;
        float[] sum = new float[sx * sx];
        int compt = 0;
        for (int i = 0; i < chain.length; i++) {
            Point2D.Double pt = chain[i];
            if (pt != null) {
                if (!tp.isInsideImage(pt, halfsize)) return null;
                float[] tmp = ts.getSubImagePixels(i, sx, sx, pt, false, false);
                //float[] tmp=ts.getPixelsForAlignment(i,pt);
                //new ImagePlus("cA"+i, new FloatProcessor(sx, sx, tmp, null)).show();
                for (int j = 0; j < sum.length; j++) {
                    sum[j] += tmp[j];
                }
                compt++;
            }
        }
        for (int j = 0; j < sum.length; j++) {
            sum[j] /= compt;
        }
        //new ImagePlus("average"+compt+"points", new FloatProcessor(sx, sx, sum, null)).show();
        return sum;
    }

    /**
         * non normalized correlation between to arrays. The arrays have to be normalized before
         *
         * @param array1 Description of the Parameter
         * @param array2 Description of the Parameter
         * @return Description of the Return Value
         */
        public static double correlation(float[] array1, float[] array2) {
            double corr = 0;
            for (int i = 0; i < array1.length; i++) {
                corr += array1[i] * array2[i];
            }
            return corr / array1.length;
        }

    /**
     * refine a landmark position by local crosscorrelation<br>
     * get the image of first image and call the refineLandmark method
     *
     * @param ii        index of first image
     * @param jj        index of second image
     * @param landmark  array containing the landmark at the corresponding indexes
     * @param localSize size in pixel to use in local crosscorrelation
     * @return the minimum correlation score between two landmark images
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#refineLandmark(float[], int, Point2D[], int)
     */
    public double refineLandmark(int ii, int jj, Point2D[] landmark, int localSize, boolean FFT, boolean goldBead) {

        //is it near border?
        int halfSize = localSize / 2;
        if (!tp.isInsideImage(landmark[ii], halfSize) || !tp.isInsideImage(landmark[jj], halfSize)) {
            //System.out.println("ii:"+landmark[ii]+" , jj:"+landmark[jj]+" , halfsize:"+halfSize);
            return -1;
        }

        //select region of interest
        ts.setAlignmentRoi(localSize, localSize);
        float[] pieceii = ts.getSubImagePixels(ii, localSize, localSize, landmark[ii], false, false);
        //float[] pieceii=ts.getPixelsForAlignment(ii,landmark[ii]);
        //new ImagePlus("ref" + ii, new FloatProcessor(sx, sx, pieceii, null)).show();
        //float[] tmp = ts.getSubImagePixels(jj, sx, sx, new Point2D.Double(landmark[jj].getX(), landmark[jj].getY()));
        //new ImagePlus("before" + jj, new FloatProcessor(sx, sx, tmp, null)).show();
        FloatProcessor fp = null;
        ColorModel cm = ts.getProcessor().getCurrentColorModel();
        if (goldBead) {
            fp = new FloatProcessor(localSize, localSize, pieceii, cm);
            ImagePlus impf = new ImagePlus("symmetry", fp);
            ApplySymmetry_Filter filter = new ApplySymmetry_Filter();
            filter.setup("symmetry=12", impf);
            filter.run(fp);
            //IJ.runPlugIn(new ImagePlus("tmp",fp),"tomoj.filters.ApplySymmetry_Filter", "symmetry=12");

        }
        if (FFT) {
            //System.out.println("before refinement FFT landmark "+jj+" "+landmark[jj]);
            float[] pieceiibig = ts.getSubImagePixels(ii, localSize * 2, localSize * 2, landmark[ii], false, false);
            refineLandmarkFHT(pieceiibig, jj, landmark, localSize * 2);
        }
        // new ImagePlus("piecejj"+jj+" before",new FloatProcessor(localSize,localSize,ts.getSubImagePixels(jj, localSize, localSize, landmark[jj]),cm)).show();
        //System.out.println("before refinement landmark "+jj+" "+landmark[jj]);
        double score = refineLandmark(pieceii, jj, landmark, localSize);
        //System.out.println("after refinement landmark "+jj+" "+landmark[jj]+" with score of "+score);
        // new ImagePlus("pieceii"+jj+" before sym",new FloatProcessor(localSize,localSize,ts.getSubImagePixels(ii, localSize, localSize, landmark[ii]),cm)).show();
        //new ImagePlus("pieceii"+jj,new FloatProcessor(localSize,localSize,pieceii,cm)).show();
        //new ImagePlus("piecejj"+jj+" after",new FloatProcessor(localSize,localSize,ts.getSubImagePixels(jj, localSize, localSize, landmark[jj]),cm)).show();
        // try{System.in.read();}catch(Exception e){}
        return score;
    }


    /**
     * refine a landmark position by local crosscorrelation with a reference landmark image<br>
     *
     * @param pieceii   landmark image used as reference
     * @param jj        index of second image
     * @param landmark  array containing the landmark at the corresponding indexes
     * @param localSize size in pixel to use in local crosscorrelation
     * @return the minimum correlation score between two landmark images
     */
    public void refineLandmarkFHT(float[] pieceii, int jj, Point2D[] landmark, int localSize) {
        //try all possible shifts
        DenseDoubleMatrix2D H1 = new DenseDoubleMatrix2D(localSize, localSize);
        H1.assign(pieceii);
        H1.dht2();
        Point2D.Double transl = new Point2D.Double(0, 0);
        Point2D.Double ntr;
        double cls = (localSize - 1) / 2.0;
        float[] piecejj = ts.getSubImagePixels(jj, localSize, localSize, new Point2D.Double(landmark[jj].getX() + transl.getX(), landmark[jj].getY() + transl.getY()), false, false);
        DenseDoubleMatrix2D H2 = new DenseDoubleMatrix2D(localSize, localSize);
        H2.assign(piecejj);
        H2.dht2();
        DenseDoubleMatrix2D res = (DenseDoubleMatrix2D) H1.like();

        MatrixUtils.convolveFD(H1, H2, res, true);
        res.idht2(false);
        double[] max = res.getMaxLocation();
        if (max[1] > cls) max[1] -= localSize;
        if (max[2] > cls) max[2] -= localSize;
        //System.out.println("max found at "+max[1]+", "+max[2]);
        ntr = new Point2D.Double(max[2], max[1]);
        transl = new Point2D.Double(transl.getX() - ntr.getX(), transl.getY() - ntr.getY());
        //System.out.println("before refine landmark was "+landmark[jj]);
        landmark[jj] = new Point2D.Double(landmark[jj].getX() + transl.getX(), landmark[jj].getY() + transl.getY());

    }


}
