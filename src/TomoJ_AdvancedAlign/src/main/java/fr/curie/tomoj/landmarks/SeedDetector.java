package fr.curie.tomoj.landmarks;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.CurveFitter;
import ij.process.*;
import fr.curie.filters.FFTFilter_TomoJ;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.utils.Chrono;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 17/01/2017.
 */
public class SeedDetector {
    double completion;
    TiltSeries ts;
    int nbThreads;
    double cx,cy;
    protected ExecutorService exec;


    public SeedDetector(TiltSeries ts){
        this.ts=ts;     completion = 0;
        nbThreads = Prefs.getThreads();
        exec = Executors.newFixedThreadPool(nbThreads);

        if(ts!=null){
            cx = ts.getCenterX();
            cy = ts.getCenterY();
        }
    }

    public void interrupt(){
        completion=-1000;
    }

    public double getCompletion() {
        return completion;
    }

    public void resetCompletion() {
        completion = 0;
    }

    public ArrayList<LandmarksChain> createsSeedsGrid(final int gridSamplesX, final int gridSamplesY, final double percentageExcludeX, final double percentageExcludeY){

        final ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>();
        Future[] seedFuture = new Future[ts.getImageStackSize()];
        completion=0;
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            final int ii = i;
            seedFuture[i] = exec.submit(new Thread() {
                public void run() {
                    if (completion < 0) return;
                    ArrayList<LandmarksChain> tmp = createsSeedsGrid(ii,gridSamplesX,gridSamplesY,percentageExcludeX,percentageExcludeY);
                    System.out.println("there are " + tmp.size() + " seeds on image " + ii + " ");
                    IJ.showStatus("create seeds " + (ii + 1) + "/" + ts.getImageStackSize());
                    IJ.showProgress(ii + 1, ts.getImageStackSize());
                    synchronized (Q) {Q.addAll(tmp);}
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

        return Q;
    }

    public ArrayList<LandmarksChain> createsSeedsGrid(int index, int gridSamplesX, int gridSamplesY, double percentageExcludeX, double percentageExcludeY){

        ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>();

        int deltaShiftx = (int)(ts.getWidth()*(1-percentageExcludeX) / gridSamplesX);
        int deltaShifty = (int)(ts.getHeight()*(1-percentageExcludeY) / gridSamplesY);
        int startx=(int)Math.round(ts.getWidth()*(percentageExcludeX) / 2);
        int starty=(int)Math.round(ts.getHeight()*(percentageExcludeY) / 2);

        for (int x = 0; x < gridSamplesX; x++) {
            double xx = Math.round(deltaShiftx * (0.5 + x)+startx) - cx;
            for (int y = 0; y < gridSamplesY; y++) {
                double yy = Math.round(deltaShifty * (0.5 + y)+starty) - cy;
                Point2D.Double pt = new Point2D.Double(xx, yy);
                Point2D.Double[] landmarkchain = new Point2D.Double[ts.getImageStackSize()];
                landmarkchain[index] = pt;
                Q.add(new LandmarksChain(landmarkchain, 0, index));
                //System.out.println("seed added on image "+ii+" "+pt);
            }
        }


        return Q;
    }

    public ArrayList<LandmarksChain> createsSeedsLocalExtrema(final boolean useMinima, final double percentageExcludeX, final double percentageExcludeY, final int critical_FilterSmall, final int critical_FilterLarge, final int critical_MinimaRadius, final int critical_SeedNumber){

        final ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>();
        Future[] seedFuture = new Future[ts.getImageStackSize()];
        completion=0;
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            final int ii = i;
            seedFuture[i] = exec.submit(new Thread() {
                public void run() {
                    if (completion < 0) return;
                    ArrayList<LandmarksChain> tmp = createsSeedsLocalExtrema(ii, useMinima, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, critical_MinimaRadius, critical_SeedNumber);
                    System.out.println("there are " + tmp.size() + " seeds on image " + ii + " ");
                    IJ.showStatus("create seeds " + (ii + 1) + "/" + ts.getImageStackSize());
                    IJ.showProgress(ii + 1, ts.getImageStackSize());
                    synchronized (Q) {Q.addAll(tmp);}
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

        return Q;
    }

    public ArrayList<LandmarksChain> createsSeedsLocalExtrema(int index, boolean useMinima, double percentageExcludeX, double percentageExcludeY, int critical_FilterSmall,int critical_FilterLarge, int critical_MinimaRadius,int critical_SeedNumber){
        ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>();
        ImageProcessor ip = new FloatProcessor(ts.getWidth(), ts.getHeight());
        ImagePlus imptmp = new ImagePlus("", ip);
        ip.setPixels(ts.getOriginalPixels(index));
        ip.resetRoi();
        imptmp.setTitle("" + index);
        //filter the image
        System.out.println("Filtering image " + index);
        getFilteredImageMSD(imptmp, useMinima, false, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, critical_MinimaRadius, critical_SeedNumber);
        if (completion < 0) return null;
        FloatPolygon r = ((PointRoi) imptmp.getRoi()).getFloatPolygon();
        float[] xcoord = r.xpoints;
        float[] ycoord = r.ypoints;
        for (int i = 0; i < xcoord.length; i++) {
            if (completion < 0) return Q;
            Point2D.Double pt = new Point2D.Double(xcoord[i] - cx, ycoord[i] - cy);
            //ChainsGenerator.refineLandmarkPositionFlipFlap(pt,ts,index,21);
            Point2D.Double[] landmarkchain = new Point2D.Double[ts.getImageStackSize()];
            landmarkchain[index] = pt;
            //chainsGenerator.refineLandmark(index,index,landmarkchain,21,true);
            //chainsGenerator.refineLandmarkPositionGaussian(landmarkchain,ts, index,21);
            //chainsGenerator.refineLandmarkPositionDiscus(landmarkchain,ts, index,21,4,true);
            Q.add(new LandmarksChain(landmarkchain, 0, index));
        }


        return Q;
    }

    public ArrayList<Point2D> createsSeedsLocalExtrema(ImagePlus imptmp, boolean useMinima, double percentageExcludeX, double percentageExcludeY, int critical_FilterSmall,int critical_FilterLarge, int critical_MinimaRadius,int critical_SeedNumber){
        ArrayList<Point2D> Q = new ArrayList<Point2D>();
        //filter the image
        System.out.println("Filtering image ");
        getFilteredImageMSD(imptmp, useMinima, true, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, critical_MinimaRadius, critical_SeedNumber);
        if (completion < 0) return null;
        FloatPolygon r = ((PointRoi) imptmp.getRoi()).getFloatPolygon();
        float[] xcoord = r.xpoints;
        float[] ycoord = r.ypoints;
        cx=(imptmp.getWidth()-1.0)/2.0;
        cy=(imptmp.getHeight()-1.0)/2.0;

        for (int i = 0; i < xcoord.length; i++) {
            if (completion < 0) return Q;
            Point2D.Double pt = new Point2D.Double(xcoord[i] - cx, ycoord[i] - cy);

            Q.add( pt);
        }


        return Q;
    }



    public ImageProcessor getFilteredImageMSDOld(ImagePlus input, boolean useMinima, boolean show, double removepercentageX, double removepercentageY, int critical_FilterSmall,int critical_FilterLarge, int critical_MinimaRadius,int critical_SeedNumber) {
        ImageProcessor ip = input.getProcessor().duplicate();
        //System.out.println("getfilteredImage: ip width:"+ip.getWidth()+", height:"+ip.getHeight());
        int startx = (int) (removepercentageX / 2 * ip.getWidth());
        int starty = (int) (removepercentageY / 2 * ip.getHeight());
        int endx = (int) ((1 - removepercentageX / 2) * ip.getWidth());
        int endy = (int) ((1 - removepercentageY / 2) * ip.getHeight());

        if (completion < 0) return ip;
        FFTFilter_TomoJ fftf = new FFTFilter_TomoJ();
        fftf.setup("filter_large=" + critical_FilterLarge + " filter_small=" + critical_FilterSmall + " suppress=None tolerance=5", input);
        fftf.run(ip);
        if (completion < 0) return ip;
        ImageProcessor ipfilter = null;
        if (show) {
            ipfilter = ip.duplicate();
        }
        //Identify low valued points and perform dilatation
        Chrono time=new Chrono();
        time.start();
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
                }
                if (!localMinima) ip.putPixelValue(x, y, 0);
                else {
                    ip.putPixelValue(x, y, msd);
                }
            }
        }
        time.stop();
        System.out.println("msd part : "+time.delayString());
        if (completion < 0) return ip;
        ip.setRoi(startx, starty, (int) ((1 - removepercentageX) * ip.getWidth()), (int) ((1 - removepercentageY) * ip.getHeight()));
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
                if (value > th) {
                    if (r == null) {
                        r = new PointRoi(x, y);
                        //System.out.println("create point roi "+x+", "+y);
                    } else {
                        r = r.addPoint(x, y);
                        //System.out.println("add point "+x+", "+y);
                    }
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

    public ImageProcessor getFilteredImage(ImagePlus input, double removepercentageX, double removepercentageY, int critical_FilterSmall, int critical_FilterLarge){
        ImageProcessor ip = input.getProcessor().duplicate().convertToFloat();
        //System.out.println("getfilteredImage: ip width:"+ip.getWidth()+", height:"+ip.getHeight());
        int startx = (int) (removepercentageX / 2 * ip.getWidth());
        int starty = (int) (removepercentageY / 2 * ip.getHeight());
        int endx = (int) ((1 - removepercentageX / 2) * ip.getWidth());
        int endy = (int) ((1 - removepercentageY / 2) * ip.getHeight());

        int sx=ip.getWidth();
        int sy=ip.getHeight();
        Chrono time=new Chrono();
        if (completion < 0) return ip;
        time.start();
        float[] pixsfftcomputation=(float[])ip.getPixels();
        DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sy, sx);
        H1.assign(pixsfftcomputation);
        DenseFComplexMatrix2D fft = H1.getFft2();
        float[] fft1 = fft.elements();
        double cx = (sx) / 2;
        double cy = (sy) / 2;
        //doing filtering
        double bandpassLowkeep=critical_FilterSmall;
        double bandpassLowCut=critical_FilterSmall/2;
        double bandpassHighCut=critical_FilterLarge*2;
        double bandpassHighKeep=critical_FilterLarge;
        double auxmin = Math.PI / (bandpassLowkeep - bandpassLowCut);
        double auxmax = Math.PI / (bandpassHighCut - bandpassHighKeep);
        for (int j = 0; j < sy; j++) {
            int jj = j * sx;
            double cj = (j > cy) ? j - sy : j;
            for (int i = 0; i < sx; i++) {
                double ci = (i > cx) ? i - sx : i;
                double dist = Math.sqrt(ci * ci + cj * cj);
                if (dist >= bandpassLowCut && dist < bandpassLowkeep) {
                    double tmp = (1 + Math.cos((dist - bandpassLowkeep) * auxmin)) * .5f;
                    fft1[(jj + i) * 2] *= tmp;
                    fft1[(jj + i) * 2 + 1] *= tmp;
                } else if (dist >= bandpassLowkeep && dist <= bandpassHighKeep) {
                } else if (dist > bandpassHighKeep && dist <= bandpassHighCut) {
                    double tmp = (1 + Math.cos((dist - bandpassHighKeep) * auxmax)) * .5f;
                    fft1[(jj + i) * 2] *= tmp;
                    fft1[(jj + i) * 2 + 1] *= tmp;
                } else {
                    fft1[(jj + i) * 2] = 0;
                    fft1[(jj + i) * 2 + 1] = 0;
                }
            }
        }

        fft = new DenseFComplexMatrix2D(sy, sx);
        fft.assign(fft1);
        fft.ifft2(true);
        fft1 = fft.elements();
        for (int j = 0; j < pixsfftcomputation.length; j++) {
            pixsfftcomputation[j] = fft1[j * 2];
        }
        return ip;
    }

    public ImageProcessor getFilteredImageMSD(ImagePlus input, boolean useMinima, boolean show, double removepercentageX, double removepercentageY, int critical_FilterSmall,int critical_FilterLarge, int critical_MinimaRadius,int critical_SeedNumber) {
        return getFilteredImageMSDNew(input,useMinima,show,removepercentageX,removepercentageY,critical_FilterSmall,critical_FilterLarge,critical_MinimaRadius,critical_SeedNumber);
    }
    public ImageProcessor getFilteredImageMSDNew(ImagePlus input, boolean useMinima, boolean show, double removepercentageX, double removepercentageY, int critical_FilterSmall,int critical_FilterLarge, int critical_MinimaRadius,int critical_SeedNumber) {
        ImageProcessor ip = input.getProcessor().duplicate().convertToFloat();
        //System.out.println("getfilteredImage: ip width:"+ip.getWidth()+", height:"+ip.getHeight());
        int startx = (int) (removepercentageX / 2 * ip.getWidth());
        int starty = (int) (removepercentageY / 2 * ip.getHeight());
        int endx = (int) ((1 - removepercentageX / 2) * ip.getWidth());
        int endy = (int) ((1 - removepercentageY / 2) * ip.getHeight());

        int sx=ip.getWidth();
        int sy=ip.getHeight();
        Chrono time=new Chrono();
        if (completion < 0) return ip;
        time.start();
        float[] pixsfftcomputation=(float[])ip.getPixels();
        DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sy, sx);
        H1.assign(pixsfftcomputation);
        DenseFComplexMatrix2D fft = H1.getFft2();
        float[] fft1 = fft.elements();
        double cx = (sx) / 2;
        double cy = (sy) / 2;
        //doing filtering
        double bandpassLowkeep=critical_FilterSmall;
        double bandpassLowCut=critical_FilterSmall/2;
        double bandpassHighCut=critical_FilterLarge*2;
        double bandpassHighKeep=critical_FilterLarge;
        double auxmin = Math.PI / (bandpassLowkeep - bandpassLowCut);
        double auxmax = Math.PI / (bandpassHighCut - bandpassHighKeep);
        for (int j = 0; j < sy; j++) {
            int jj = j * sx;
            double cj = (j > cy) ? j - sy : j;
            for (int i = 0; i < sx; i++) {
                double ci = (i > cx) ? i - sx : i;
                double dist = Math.sqrt(ci * ci + cj * cj);
                if (dist >= bandpassLowCut && dist < bandpassLowkeep) {
                    double tmp = (1 + Math.cos((dist - bandpassLowkeep) * auxmin)) * .5f;
                    fft1[(jj + i) * 2] *= tmp;
                    fft1[(jj + i) * 2 + 1] *= tmp;
                } else if (dist >= bandpassLowkeep && dist <= bandpassHighKeep) {
                } else if (dist > bandpassHighKeep && dist <= bandpassHighCut) {
                    double tmp = (1 + Math.cos((dist - bandpassHighKeep) * auxmax)) * .5f;
                    fft1[(jj + i) * 2] *= tmp;
                    fft1[(jj + i) * 2 + 1] *= tmp;
                } else {
                    fft1[(jj + i) * 2] = 0;
                    fft1[(jj + i) * 2 + 1] = 0;
                }
            }
        }

        fft = new DenseFComplexMatrix2D(sy, sx);
        fft.assign(fft1);
        fft.ifft2(true);
        fft1 = fft.elements();
        for (int j = 0; j < pixsfftcomputation.length; j++) {
            pixsfftcomputation[j] = fft1[j * 2];
        }

//        FFTFilter_TomoJ fftf = new FFTFilter_TomoJ();
//        fftf.setup("filter_large=" + critical_FilterLarge + " filter_small=" + critical_FilterSmall + " suppress=None tolerance=5", input);
//        fftf.run(ip);
        if (completion < 0) return ip;
        ImageProcessor ipfilter = null;
        if (show) {
            ipfilter = ip.duplicate();
        }
        time.stop();
        //System.out.println("FFT part : "+time.delayString());
        //Identify low valued points and perform dilatation
        time.start();
        float[] pixs = (float[]) ip.duplicate().getPixels();
        float[] msdpixs=(float[])ip.getPixels();
        Arrays.fill(msdpixs,0);
        int msdy=starty*ip.getWidth();
        for (int y = starty; y < endy; y++) {
            for (int x = startx; x < endx; x++) {
                //float value = ip.getPixelValue(x, y);
                //float value = pixs[y * ip.getWidth() + x];
                float value = pixs[msdy+x];
                double msd = 0;
                boolean localMinima = true;
                if (completion < 0) return ip;
                for (int yy = y - critical_MinimaRadius; yy <= y + critical_MinimaRadius && localMinima; yy++) {
                    int yyy = yy * ip.getWidth();
                    for (int xx = x - critical_MinimaRadius; xx <= x + critical_MinimaRadius && localMinima; xx++) {
                        if (completion < 0) return ip;
                        if (xx > startx && xx < endx && yy > starty && yy < endy) {
                            //if ((useMinima && value > ip.getPixelValue(xx, yy)) || (!useMinima && value < ip.getPixelValue(xx, yy))) {

                            msd += Math.abs(value - pixs[yyy + xx]);
                            if ((useMinima && value > pixs[yyy + xx]) || (!useMinima && value < pixs[yyy + xx])) {
                                localMinima = false;
                                //break;
                            }

                        }
                    }
                }
                if (!localMinima) {
                    //ip.putPixelValue(x, y, 0);
                    msdpixs[msdy+x]=0;
                }
                else {
                    //ip.putPixelValue(x, y, msd);
                    msdpixs[msdy+x]=(float)msd;
                    x+=critical_MinimaRadius-1;
                }
            }
            msdy+=ip.getWidth();
        }
        time.stop();
        //System.out.println("msd part : "+time.delayString());
        time.start();
        if (completion < 0) return ip;
        ip.setRoi(startx, starty, (int) ((1 - removepercentageX) * ip.getWidth()), (int) ((1 - removepercentageY) * ip.getHeight()));
        ip.resetMinAndMax();
        ImageStatistics stat = new FloatStatistics(ip);
        int[] hist = stat.histogram;
        int sum = 0;
        double th = 0;
        int maxCount = 0;
        if (completion < 0) return ip;
        for (int aHist : hist) maxCount += aHist;
        if (completion < 0) return ip;
        for (int i = hist.length -1; i >0; i--) {
            sum += hist[i];
            if (sum > critical_SeedNumber) {
                th = i;
                break;
            }
            //System.out.println("sum="+sum+"threshold ="+th);
            //System.out.println("hist["+i+"]="+hist[i]);
        }
        //System.out.println("threshold ="+th);
        th /= 256;
        th *= (stat.max - stat.min);
        th += (stat.min);
        PointRoi r = null;
        time.stop();
        //System.out.println("threshold computation : "+time.delayString() +" : threshold: "+th);
        time.start();
        //System.out.println("threshold ="+th);
        if (completion < 0) return ip;
        int count=0;
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
                if (completion < 0) return ip;
                float value = ip.getPixelValue(x, y);
                if (!Float.isNaN(value)&&value > th) {
                    count++;
                    if (r == null) {
                        r = new PointRoi(x, y);
                        //System.out.println("create point roi "+x+", "+y);
                    } else {
                        r = r.addPoint(x, y);
                        //System.out.println("add point "+x+", "+y +"      count: "+count);
                    }
                }
            }
        }
        if(r!=null) {
            r.setPointType((int) Prefs.get("point.type", 2));
            r.setSize((int) Prefs.get("point.size", 2));
        }
        input.setRoi(r);
        time.stop();
        //System.out.println("create roi : "+time.delayString()+" : nb points: "+count+" ("+critical_SeedNumber+")");
        if (show) {
            input.getProcessor().copyBits(ipfilter, 0, 0, Blitter.COPY);
            //input.show();
        }
        return ip;
    }

    public ImagePlus previewDetection(int index, boolean localMinima, double percentageExcludeX, double percentageExcludeY, int critical_FilterSmall, int critical_FilterLarge, int extremaNeighborhoodRadius,int nbSeeds,int patchSize, double thresholdFitGoldBead){
        ArrayList<LandmarksChain> seedstmp = new ArrayList<LandmarksChain>();
        ImageProcessor ip = new FloatProcessor(ts.getWidth(), ts.getHeight());
        ImagePlus imptmp = new ImagePlus("", ip);
        ip.setPixels(ts.getOriginalPixelsCopy(index));
        ip.resetRoi();
        imptmp.setTitle("" + index);
        //filter the image
        System.out.println("Filtering image " + index);
        getFilteredImageMSD(imptmp, localMinima, true, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, extremaNeighborhoodRadius, nbSeeds);
        if (completion < 0) return null;
        FloatPolygon r = ((PointRoi) imptmp.getRoi()).getFloatPolygon();
        float[] xcoord = r.xpoints;
        float[] ycoord = r.ypoints;
        for (int i = 0; i < xcoord.length; i++) {
            if (completion < 0) return imptmp;
            Point2D.Double pt = new Point2D.Double(xcoord[i] - cx, ycoord[i] - cy);
            //ChainsGenerator.refineLandmarkPositionFlipFlap(pt,ts,index,21);
            Point2D.Double[] landmarkchain = new Point2D.Double[ts.getImageStackSize()];
            landmarkchain[index] = pt;
            //chainsGenerator.refineLandmark(index,index,landmarkchain,21,true);
            //chainsGenerator.refineLandmarkPositionGaussian(landmarkchain,ts, index,21);
            //chainsGenerator.refineLandmarkPositionDiscus(landmarkchain,ts, index,21,4,true);
            seedstmp.add(new LandmarksChain(landmarkchain, 0, index));
        }

        int nbBefore = seedstmp.size();
        //double s = seedDetector.isGoldBeadPresent(seedstmp, ii, patchSize, localMinima);

        if(thresholdFitGoldBead>0) {
            double s = isGoldBeadPresent(seedstmp, index, patchSize, localMinima, thresholdFitGoldBead);
            System.out.println("preview " + index + " do bead selection : " + nbBefore + " --> " + seedstmp.size());
        }
        PointRoi roi=null;
        for(LandmarksChain landmarkchain:seedstmp){
            Point2D p=landmarkchain.getLandmarkchain()[index];
            if(roi==null) roi=new PointRoi(p.getX()+ts.getCenterX(),p.getY()+ts.getCenterY());
            else roi.addPoint(p.getX()+ts.getCenterX(),p.getY()+ts.getCenterY());
        }
        System.out.println("roi nb point "+roi.getNCoordinates());
        imptmp.setRoi(roi);
        //ip.setRoi(roi);

        return imptmp;


    }

    public double isGoldBeadPresent(ArrayList<LandmarksChain> points, int indexImage,int patchSize, boolean darkOnWhiteBG, double thresholdFit){
        double radius=-1;
        int patchSize2=patchSize/2;
        //PlotWindow2 pw=new PlotWindow2();
        //pw.removeAllPlots();
        //int[] xs=new int[patchSize];
        //for(int x=0;x<patchSize;x++) xs[x]=x;

        int count=0;
        for(int i=points.size()-1;i>=0;i--){
            LandmarksChain lc=points.get(i);
            if(lc.getLandmarkchain()[indexImage]!=null){
                double tmp;
                double[] paramsX;
                double[] paramsY;
                int nbloop=0;
                do {
                    float[] patch = ts.getSubImagePixels(indexImage, patchSize, patchSize, lc.getLandmarkchain()[indexImage], false, false);
                    float[] row=new float[patchSize];
                    int offset=0;
                    for(int r=0;r<patchSize;r++){
                        for(int c=0;c<patchSize;c++){
                            row[c]+=patch[offset+c];
                        }
                        offset+=patchSize;
                    }
                    row = smooth(row, 1);
                    //row = reduce(row, 1);
                    paramsX = fitGaussian(row, darkOnWhiteBG);
                    //if(Math.abs(paramsX[2])<patchSize2)lc.getLandmarkchain()[indexImage].setLocation(lc.getLandmarkchain()[indexImage].getX() + paramsX[2], lc.getLandmarkchain()[indexImage].getY());
                    //System.out.println("#"+i+" centerX:" + paramsX[2] + ", sigma: " + paramsX[3]+ ", R=" + paramsX[4]);
                    //pw.addPlot(xs,row, Color.BLACK,"row point "+i);

                    patch = ts.getSubImagePixels(indexImage, patchSize, patchSize, lc.getLandmarkchain()[indexImage], false, false);
                    float[] column=new float[patchSize];
                    for(int c=0;c<patchSize;c++){
                        for(int r=0;r<patchSize;r++){
                            column[r]+=patch[r*patchSize+c];
                        }
                    }
                    column = smooth(column, 1);
                    //column = reduce(column, 1);
                    paramsY = fitGaussian(column, darkOnWhiteBG);
                    //if(Math.abs(paramsY[2])<patchSize2)lc.getLandmarkchain()[indexImage].setLocation(lc.getLandmarkchain()[indexImage].getX(), lc.getLandmarkchain()[indexImage].getY() + paramsY[2]);
                    //System.out.println("#"+i+" centerY:" + paramsY[2] + ", sigma: " + paramsY[3] + ", R=" + paramsY[4]);
                    //pw.addPlot(xs,column, Color.RED,"col point "+i);
                    nbloop++;
                }while (nbloop<0&&(Math.abs(paramsX[2])>0.5||Math.abs(paramsY[2])>0.5));
                if (paramsY[4] > thresholdFit && paramsY[3] < 2 * patchSize && paramsX[4] > thresholdFit && paramsX[3] < 2 * patchSize) {
                    radius += (paramsY[3]+paramsX[3])/2;
                    count++;
                }  else{
                    points.remove(i);
                    //System.out.println("remove "+i);
                }
            }
        }
        //pw.setVisible(true);
        radius/=count;
        System.out.println("#"+indexImage+" average sigma:"+radius+" with "+count+" beads");
        return radius;
    }

    /**
     * fit gaussian on an image
     * @param patch
     * @param width
     * @param height
     * @param darkOnWhiteBG
     * @return array containing [0] centerX of gaussian [1] centerY of gaussian [2] radius of gaussian [3] fit
     */
    public static double[] fitGaussianOnImage(float[] patch, int width, int height, boolean darkOnWhiteBG){
        float[] row=new float[width];
        int offset=0;
        for(int r=0;r<height;r++){
            for(int c=0;c<width;c++){
                row[c]+=patch[offset+c];
            }
            offset+=width;
        }
        row = smooth(row, 1);
        //row = reduce(row, 1);
        double [] paramsX = fitGaussian(row, darkOnWhiteBG);
        float[] column=new float[height];
        for(int c=0;c<width;c++){
            for(int r=0;r<height;r++){
                column[r]+=patch[r*width+c];
            }
        }
        column = smooth(column, 1);
        //column = reduce(column, 1);
        double[] paramsY = fitGaussian(column, darkOnWhiteBG);
        double[] result=new double[6];
        result[0]=paramsX[2];
        result[1]=paramsY[2];
        result[2]= (paramsY[3]+paramsX[3])/2;
        result[3]= Math.min(paramsX[4],paramsY[4]);
        return result;
    }

    protected static  float[] smooth(float[] data, int radius){
        float[] result=new float[data.length];
        for(int i=0;i<result.length;i++){
            for(int j=-radius;j<radius;j++){
                int tmp=Math.min(result.length-1,Math.max(0,i+j));
                result[i]+=data[tmp];
            }
        }
        return result;
    }
    protected float[] reduce(float[] data, int radius){
        float[] result=new float[data.length/(radius+1)+1];
        for(int i=0;i<result.length;i++){
            for(int j=i*(radius*2)-radius;j<=i*(radius*2)+radius;j++){
                int tmp=Math.min(result.length-1,Math.max(0,j));
                result[i]+=data[tmp];
            }
        }
        return result;
    }

    /**
     * fit gaussian y= a+(b-a)*exp(-(x-c)^2/(2*d*d))
     * @param data
     * @param darkOnWhiteBG
     * @return array with { offset, amplitude(height of curve), average(center), standard deviation, fit score}
     */
    protected static double[] fitGaussian(float[] data, boolean darkOnWhiteBG){
        double[] xs=new double[data.length];
        double[] ys=new double[data.length];
        int center=data.length/2;
        for(int i=0;i<data.length;i++){
            xs[i]=i-center;
            ys[i]=(darkOnWhiteBG)?-data[i]:data[i];
        }
        CurveFitter cf=new CurveFitter(xs,ys);
        cf.doFit(CurveFitter.GAUSSIAN);
        double[] params=cf.getParams();
        params[4]=cf.getFitGoodness();
        return params;

    }

    public ArrayList<LandmarksChain> pointsCreateGridSeeds(TiltSeries ts, int index, int spacing){
        ArrayList<LandmarksChain> Q = new ArrayList<LandmarksChain>();
        for (int y = spacing; y < ts.getHeight(); y+=spacing) {
            for (int x = spacing; x < ts.getWidth(); x+=spacing) {
                Point2D.Double pt = new Point2D.Double(x - ts.getCenterX(), y - ts.getCenterY()) ;
                Point2D.Double[] landmarkchain = new Point2D.Double[ts.getImageStackSize()];
                landmarkchain[index] = pt;
                Q.add(new LandmarksChain(landmarkchain, 0, index));
            }
        }


        return Q;
    }
}
