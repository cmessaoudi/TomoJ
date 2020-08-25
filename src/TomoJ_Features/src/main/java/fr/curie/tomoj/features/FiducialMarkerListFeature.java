package fr.curie.tomoj.features;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import delaunay.DelaunayTriangulation;
import delaunay.Pnt;
import delaunay.Simplex;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.CurveFitter;
import ij.plugin.filter.Convolver;
import ij.plugin.filter.MaximumFinder;
import ij.process.Blitter;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import fr.curie.filters.FFTFilter_TomoJ;
import fr.curie.tomoj.tomography.TiltSeries;

import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by cedric on 31/01/2017.
 */
public class FiducialMarkerListFeature implements ListFeature {
    boolean useMinima;
    double percentageExcludeX;
    double percentageExcludeY;
    int critical_FilterSmall;
    int critical_FilterLarge;
    int critical_MinimaRadius;
    int critical_SeedNumber;
    static int patchSize;
    boolean darkOnWhiteBG;
    double gaussianFitThreshold = 0.6;
    ArrayList<Point2D> points;
    TiltSeries ts;
    DelaunayTriangulation delaunay;
    int circleIndex;
    double amplitude,base;
    static float[] gaussianImage=null;
    static ArrayList<float[]> circles;

    public FiducialMarkerListFeature(TiltSeries ts, boolean useMinima, double percentageExcludeX, double percentageExcludeY, int critical_FilterSmall, int critical_FilterLarge, int critical_MinimaRadius, int critical_SeedNumber, int patchSize, boolean darkOnWhiteBG, double gaussianFitThreshold) {
        this.ts = ts;
        this.useMinima = useMinima;
        this.percentageExcludeX = percentageExcludeX;
        this.percentageExcludeY = percentageExcludeY;
        this.critical_FilterSmall = critical_FilterSmall;
        this.critical_FilterLarge = critical_FilterLarge;
        this.critical_MinimaRadius = critical_MinimaRadius;
        this.critical_SeedNumber = critical_SeedNumber;
        this.darkOnWhiteBG = darkOnWhiteBG;
        this.gaussianFitThreshold = gaussianFitThreshold;
        /*if(this.patchSize!=patchSize||gaussianImage==null) {
            for (int i = 1; i < ts.getImageStackSize(); i++) {
                ts.setSlice(i + 1);
                ImageProcessor fp = ts.getProcessor().duplicate();
                ImageStatistics stats = fp.getStatistics();
                amplitude += stats.max - stats.min;
                base += useMinima ? stats.max : stats.min;
            }
            amplitude /= ts.getImageStackSize();
            base /= ts.getImageStackSize();
            gaussianImage = createGaussianImage(patchSize, patchSize / 4.0, useMinima);
            correctGaussianAmplitude(gaussianImage, amplitude, base);
            new ImagePlus("gaussian used", new FloatProcessor(patchSize, patchSize, gaussianImage)).show();
        }  */
        if(this.patchSize!=patchSize||circles==null){
            if(circles!=null) circles.clear();
            else circles=new ArrayList<>();
            for (int i = 1; i < ts.getImageStackSize(); i++) {
                ts.setSlice(i + 1);
                ImageProcessor fp = ts.getProcessor().duplicate();
                ImageStatistics stats = fp.getStatistics();
                amplitude += useMinima ? stats.min : stats.max;
                base += stats.max;
            }
            amplitude /= ts.getImageStackSize();
            base /= ts.getImageStackSize();
            ImageStack is=new ImageStack(patchSize,patchSize);
            for(int r=1;r<patchSize/2;r++){
                float[] tmp=createCircleImage(patchSize,r,base,amplitude) ;
                circles.add(tmp);
                is.addSlice("r="+r,tmp);
            }
            new ImagePlus("circles",is).show();
            ts.setSlice(ts.getZeroIndex()+1);
            Convolver convolv=new Convolver();
            circleIndex=-1;
            double bestRange=0;
            for(int index=0;index<circles.size();index++) {
                ImageProcessor conv = ts.getProcessor().duplicate();
                convolv.convolve(conv, circles.get(index), patchSize, patchSize);
                new FileSaver(new ImagePlus("", conv)).saveAsTiff("conv" + index + ".tif");
                if (circleIndex <0) {
                    circleIndex=index;
                    ImageStatistics stats = conv.getStatistics();
                    bestRange = stats.max - stats.min;
                    System.out.println("#" + index + " range:" + bestRange);
                } else {
                    ImageStatistics stats = conv.getStatistics();
                    double range = stats.max - stats.min;
                    System.out.println("#" + index + " range:" + range);
                    if (range > bestRange) {
                        System.out.println("keep image " + index + " range:" + range);
                        bestRange = range;
                        circleIndex = index;
                    }
                }
            }

        }
        this.patchSize = patchSize;
        
    }

    public Object detect(ImageProcessor ip) {
        //return detectGaussian(ip);
        return detectCircles(ip);

    }

    public Object detectCircles(ImageProcessor ip) {
        ArrayList<Point2D> Q = new ArrayList<Point2D>();
        Convolver convolv=new Convolver();
        convolv.setNormalize(true);
        ImageProcessor conv = ip.duplicate();
        convolv.convolve(conv,circles.get(circleIndex), patchSize, patchSize);

        Polygon r = findBestPoints(conv);
        int[] xcoord = r.xpoints;
        int[] ycoord = r.ypoints;
        for (int i = 0; i < xcoord.length; i++) {
            Point2D tmp = (gaussianFitThreshold <= 0) ? new Point2D.Float(xcoord[i], ycoord[i]) : isGoldBeadPresent(ip, xcoord[i], ycoord[i], patchSize, darkOnWhiteBG);
            //Point2D tmp=new Point2D.Float(xcoord[i],ycoord[i]);
            if (tmp != null) Q.add(tmp);
        }

        //System.out.println("number of points detected circle "+index+": " + Q.size());
        points = Q;
        return Q;
    }

    public Object detectGaussian(ImageProcessor ip) {
        System.out.println("detection fiducial patch size : " + patchSize);
        ArrayList<Point2D> Q = new ArrayList<Point2D>();
//        float[] gaussianImage = createGaussianImage(patchSize, patchSize / 4.0, useMinima);
//        ImageStatistics stats=ip.getStatistics();
//        correctGaussianAmplitude(gaussianImage,stats.max-stats.min, useMinima?stats.max:stats.min);
        new FileSaver(new ImagePlus("", new FloatProcessor(patchSize, patchSize, gaussianImage, null))).saveAsTiff("gaussianImage.tif");
        ImageProcessor conv = ip.duplicate();
        conv.convolve(gaussianImage, patchSize, patchSize);
        new FileSaver(new ImagePlus("", conv)).saveAsTiff("conv.tif");

        Polygon r = findBestPoints(conv);
        //ip.setRoi(r);
        //FloatPolygon rpoly = r.getFloatPolygon();
        int[] xcoord = r.xpoints;
        int[] ycoord = r.ypoints;
        for (int i = 0; i < xcoord.length; i++) {
            Point2D tmp = (gaussianFitThreshold <= 0) ? new Point2D.Float(xcoord[i], ycoord[i]) : isGoldBeadPresent(ip, xcoord[i], ycoord[i], patchSize, darkOnWhiteBG);
            //Point2D tmp=new Point2D.Float(xcoord[i],ycoord[i]);
            if (tmp != null) Q.add(tmp);
        }
        points = Q;
        //System.out.println("number of points detected : " + Q.size());

        return Q;

        /*ImagePlus imptmp = new ImagePlus("", ip.duplicate());
        getFilteredImageMSD(imptmp, useMinima, false, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, critical_MinimaRadius, critical_SeedNumber);
        FloatPolygon r = ((PointRoi) imptmp.getRoi()).getFloatPolygon();
        testGaussian(ip,r,patchSize,darkOnWhiteBG);
        float[] xcoord = r.xpoints;
        float[] ycoord = r.ypoints;
        for (int i = 0; i < xcoord.length; i++) {
            Point2D tmp=(gaussianFitThreshold<=0)?new Point2D.Float(xcoord[i],ycoord[i]):isGoldBeadPresent(ip,xcoord[i],ycoord[i],patchSize,darkOnWhiteBG);
            //Point2D tmp=new Point2D.Float(xcoord[i],ycoord[i]);
            if (tmp!=null) Q.add(tmp);
        }
        points=Q;

        return Q;*/
    }

    Polygon findBestPoints(ImageProcessor conv) {

        int startx = (int) (percentageExcludeX / 2 * conv.getWidth());
        int starty = (int) (percentageExcludeY / 2 * conv.getHeight());
        int endx = (int) ((1 - percentageExcludeX / 2) * conv.getWidth());
        int endy = (int) ((1 - percentageExcludeY / 2) * conv.getHeight());
        conv.setRoi(startx, starty, (int) ((1 - percentageExcludeX) * conv.getWidth()), (int) ((1 - percentageExcludeY) * conv.getHeight()));

        //Identify low valued points and perform dilatation
        float[] pixs = (float[]) conv.duplicate().getPixels();
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
                //float value = ip.getPixelValue(x, y);
                float value = pixs[y * conv.getWidth() + x];
                double msd = 0;
                boolean localMinima = true;
                for (int yy = y - critical_MinimaRadius; yy <= y + critical_MinimaRadius; yy++) {
                    int yyy = yy * conv.getWidth();
                    for (int xx = x - critical_MinimaRadius; xx <= x + critical_MinimaRadius; xx++) {
                        if (xx > startx && xx < endx && yy > starty && yy < endy) {
                            //if ((useMinima && value > ip.getPixelValue(xx, yy)) || (!useMinima && value < ip.getPixelValue(xx, yy))) {
                            msd += Math.abs(value - pixs[yyy + xx]);
                            if ((!useMinima && value > pixs[yyy + xx]) || (useMinima && value < pixs[yyy + xx])) {
                                localMinima = false;
                                break;
                            }
                        }
                    }
                    if (!localMinima) conv.putPixelValue(x, y, 0);
                    else conv.putPixelValue(x, y, msd);
                }
            }
        }
        //new FileSaver(new ImagePlus("", conv)).saveAsTiff("conv_aftermsd.tif");
        ImageStatistics stat = new FloatStatistics(conv);
        int[] hist = stat.histogram;
        int sum = 0;
        double th = 0;
        int maxCount = 0;
        for (int aHist : hist) maxCount += aHist;
        for (int i = 0; i < hist.length; i++) {
            //if (useMinima) {
            //    sum += hist[i];
            //} else {
            sum += hist[hist.length - 1 - i];
            // }
            if (sum > critical_SeedNumber) {
                //     th = useMinima? i-1 :hist.length - 1 - i;
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

        MaximumFinder mf = new MaximumFinder();
        Polygon res = mf.getMaxima(conv, th, true);

        return res;
        /*//System.out.println("threshold ="+th);
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
                float value = conv.getPixelValue(x, y);
//                if(useMinima){
//                    if (value < th) {
//                        if (r == null) {
//                            r = new PointRoi(x, y);
//                            //System.out.println("create point roi "+x+", "+y);
//                        } else {
//                            r = r.addPoint(x, y);
//                            //System.out.println("add point "+x+", "+y);
//                        }
//                    }
//                }else {
                    if (value > th) {
                        if (r == null) {
                            r = new PointRoi(x, y);
                            //System.out.println("create point roi "+x+", "+y);
                        } else {
                            r = r.addPoint(x, y);
                            //System.out.println("add point "+x+", "+y);
                        }
                    }
//                }
            }
        }
        return r;    */
    }

    public void compute(ImageProcessor ip, Object kpoints) {
        points = (ArrayList<Point2D>) kpoints;
        delaunay = doTriangulation(this.points);
    }

    public void detectAndCompute(ImageProcessor ip) {
        detect(ip);
        compute(ip, points);
    }

    public ArrayList<Point2D> getFeatures() {
        return points;
    }

    public ListFeature createListFeature() {
        return new FiducialMarkerListFeature(ts, useMinima, percentageExcludeX, percentageExcludeY, critical_FilterSmall, critical_FilterLarge, critical_MinimaRadius, critical_SeedNumber, patchSize, darkOnWhiteBG, gaussianFitThreshold);
    }

    public String getParametersAsString() {
        return null;
    }

    public HashMap<Point2D, Point2D> matchWith(ListFeature other, boolean validateWithHomography) {
        FiducialMarkerListFeature otherfmlf = (FiducialMarkerListFeature) other;
        HashMap<Point2D, Point2D> matches = new HashMap<Point2D, Point2D>();
        //find best affine transform between the 2 sets of points using Delaunay triangulation
        DelaunayTriangulation delaunay1 = this.delaunay;
        DelaunayTriangulation delaunay2 = otherfmlf.delaunay;
        AffineTransform T = findAffineTransform(this.points, delaunay1, otherfmlf.points, delaunay2, patchSize / 2);
        //create the matches
        matches = createMatches(T, this.points, otherfmlf.points, patchSize / 2);
        return matches;
    }

    public float[] createGaussianImage(int size, double sigma, boolean darkOnWhiteBG) {
        float result[] = new float[size * size];
        double size2 = size / 2.0;
        double div = 2 * sigma * sigma;
        int jj;
        for (int j = 0; j < size; j++) {
            jj = j * size;
            for (int i = 0; i < size; i++) {
                result[jj + i] = (darkOnWhiteBG) ? -(float) Math.exp(-(i - size2) * (i - size2) / div - (j - size2) * (j - size2) / div) : (float) Math.exp(-(i - size2) * (i - size2) / div - (j - size2) * (j - size2) / div);
            }
        }
        //new ImagePlus("gaussian",new FloatProcessor(size,size,result)).show();
        //reference=result;
        return result;
    }

    public void correctGaussianAmplitude(float[] gaussian, double amplitude, double baseValue){
        for(int i=0;i<gaussian.length;i++){
            gaussian[i]=(float)(gaussian[i]*amplitude+baseValue);
        }
    }

    public float[] createCircleImage(int size, double diameter, double bgValue, double beadValue){
        float result[] = new float[size * size];
        double size2 = size / 2.0;
        double diameter2=diameter*diameter;
        int jj;
        for (int j = 0; j < size; j++) {
            jj = j * size;
            for (int i = 0; i < size; i++) {
                result[jj + i] = ((i-size2)*(i-size2)+(j-size2)*(j-size2))<diameter2?(float) beadValue:(float)bgValue;
            }
        }
        //new ImagePlus("gaussian",new FloatProcessor(size,size,result)).show();
        //reference=result;
        return result;
    }

    public ImageProcessor getFilteredImageMSD(ImagePlus input, boolean useMinima, boolean show, double removepercentageX, double removepercentageY, int critical_FilterSmall, int critical_FilterLarge, int critical_MinimaRadius, int critical_SeedNumber) {
        ImageProcessor ip = input.getProcessor();
        //System.out.println("getfilteredImage: ip width:"+ip.getWidth()+", height:"+ip.getHeight());
        int startx = (int) (removepercentageX / 2 * ip.getWidth());
        int starty = (int) (removepercentageY / 2 * ip.getHeight());
        int endx = (int) ((1 - removepercentageX / 2) * ip.getWidth());
        int endy = (int) ((1 - removepercentageY / 2) * ip.getHeight());

        FFTFilter_TomoJ fftf = new FFTFilter_TomoJ();
        fftf.setup("filter_large=" + critical_FilterLarge + " filter_small=" + critical_FilterSmall + " suppress=None tolerance=5", input);
        fftf.run(ip);
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
                for (int yy = y - critical_MinimaRadius; yy <= y + critical_MinimaRadius; yy++) {
                    int yyy = yy * ip.getWidth();
                    for (int xx = x - critical_MinimaRadius; xx <= x + critical_MinimaRadius; xx++) {
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
        ip.setRoi(startx, starty, (int) ((1 - removepercentageX) * ip.getWidth()), (int) ((1 - removepercentageY) * ip.getHeight()));
        ImageStatistics stat = new FloatStatistics(ip);
        int[] hist = stat.histogram;
        int sum = 0;
        double th = 0;
        int maxCount = 0;
        for (int aHist : hist) maxCount += aHist;
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
        for (int x = startx; x < endx; x++) {
            for (int y = starty; y < endy; y++) {
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

    public void testGaussian(ImageProcessor ip, FloatPolygon roi, int patchSize, boolean darkOnWhiteBG) {
        float[] xcoord = roi.xpoints;
        float[] ycoord = roi.ypoints;
        int patchSize2 = patchSize / 2;
        for (int i = 0; i < xcoord.length; i++) {
            double x = xcoord[i];
            double y = ycoord[i];
            ip.setRoi(new Roi(x - patchSize2, y - patchSize2, patchSize, patchSize));
            ImageProcessor crop = ip.crop();
            if (crop == null || crop.getWidth() < patchSize || crop.getHeight() < patchSize) continue;
            float[] patch = (float[]) crop.getPixels();
            float[] row = new float[patchSize];
            int offset = 0;
            for (int r = 0; r < patchSize; r++) {
                for (int c = 0; c < patchSize; c++) {
                    int index = (c < crop.getWidth()) ? c : crop.getWidth() - 1;
                    if (offset + index < 0) {
                        System.out.println("index -1!!!!!");
                    }
                    row[c] += patch[offset + index];
                }
                offset += (r < crop.getHeight() - 1) ? patchSize : 0;
            }

            //row = smooth(row, 1);
            double[] paramsX = fitGaussian(row, darkOnWhiteBG);
            System.out.println("#i: a=" + paramsX[0] + ", b=" + paramsX[1] + ", c=" + paramsX[2] + ", d=" + paramsX[3] + ", fit=" + paramsX[4]);

        }
    }

    public Point2D isGoldBeadPresent(ImageProcessor ip, float x, float y, int patchSize, boolean darkOnWhiteBG) {
        //System.out.println("test gold bead");
        double radius = -1;
        int patchSize2 = patchSize / 2;
        //PlotWindow2 pw=new PlotWindow2();
        //pw.removeAllPlots();
        int[] xs = new int[patchSize];
        for (int i = 0; i < patchSize; i++) xs[i] = i;

        int count = 0;

        double tmp;
        double[] paramsX;
        double[] paramsY;
        int nbloop = 0;
        do {
            ip.setRoi(new Roi(x - patchSize2, y - patchSize2, patchSize, patchSize));
            ImageProcessor crop = ip.crop();
            if (crop == null || crop.getWidth() < patchSize || crop.getHeight() < patchSize) return null;
            float[] patch = (float[]) crop.getPixels();
            float[] row = new float[patchSize];
            int offset = 0;
            for (int r = 0; r < patchSize; r++) {
                for (int c = 0; c < patchSize; c++) {
                    int index = (c < crop.getWidth()) ? c : crop.getWidth() - 1;
                    if (offset + index < 0) {
                        System.out.println("index -1!!!!!");
                    }
                    row[c] += patch[offset + index];
                }
                offset += (r < crop.getHeight() - 1) ? patchSize : 0;
            }

            row = smooth(row, 1);
            paramsX = fitGaussian(row, darkOnWhiteBG);
            if (Math.abs(paramsX[2]) < patchSize2) x += paramsX[2];
            //System.out.println("#"+i+" centerX:" + paramsX[2] + ", sigma: " + paramsX[3]+ ", R=" + paramsX[4]);
            //pw.addPlot(xs,row, Color.BLACK,"row point "+i);

            //ip.setRoi(new Roi(x-patchSize2,y-patchSize2,patchSize,patchSize));
            //crop=ip.crop();
            //if(crop==null||crop.getWidth()<patchSize||crop.getHeight()<patchSize) return null;
            //patch= (float[]) crop.getPixels();
            float[] column = new float[patchSize];
            for (int c = 0; c < patchSize; c++) {
                for (int r = 0; r < patchSize; r++) {
                    int indexC = (c < crop.getWidth()) ? c : crop.getWidth() - 1;
                    int indexR = (r < crop.getHeight()) ? r : crop.getHeight() - 1;
                    column[r] += patch[indexR * patchSize + indexC];
                }
            }
            column = smooth(column, 1);
            paramsY = fitGaussian(column, darkOnWhiteBG);
            if (Math.abs(paramsX[2]) < patchSize2) y += paramsY[2];
            //System.out.println("#"+i+" centerY:" + paramsY[2] + ", sigma: " + paramsY[3] + ", R=" + paramsY[4]);
            //pw.addPlot(xs,column, Color.RED,"col point "+i);
            nbloop++;
        } while (nbloop < 2);
        //}while (nbloop<2&&(Math.abs(paramsX[2])>gaussianFitThreshold||Math.abs(paramsY[2])>gaussianFitThreshold));
        //if (paramsY[4] > gaussianFitThreshold && paramsY[3] < 2 * patchSize && paramsX[4] > gaussianFitThreshold && paramsX[3] < 2 * patchSize && Math.abs(paramsX[0])<Math.abs(paramsX[1])) {
        if (paramsX[4] > gaussianFitThreshold && paramsY[4] > gaussianFitThreshold && paramsX[1] - paramsX[0] > 0 && paramsY[1] - paramsY[0] > 0) {
            return new Point2D.Float(x, y);
        } else {
            return null;
            //System.out.println("remove "+i);
        }

    }

    protected float[] smooth(float[] data, int radius) {
        float[] result = new float[data.length];
        for (int i = 0; i < result.length; i++) {
            for (int j = -radius; j < radius; j++) {
                int tmp = Math.min(result.length - 1, Math.max(0, i + j));
                result[i] += data[tmp];
            }
        }
        return result;
    }

    protected double[] fitGaussian(float[] data, boolean darkOnWhiteBG) {
        int length = data.length;
        double[] params = null;
        while (length > 5) {
            double[] xs = new double[length];
            double[] ys = new double[length];
            int center = length / 2;
            int start = (data.length - length) / 2;
            for (int i = 0; i < length; i++) {
                xs[i] = i - center;
                ys[i] = (darkOnWhiteBG) ? -data[i + start] : data[i + start];
            }
            CurveFitter cf = new CurveFitter(xs, ys);
            cf.doFit(CurveFitter.GAUSSIAN);
            double[] paramstmp = cf.getParams();
            paramstmp[4] = cf.getFitGoodness();
            if (params == null || params[4] < paramstmp[4]) params = paramstmp;
            length -= 2;
        }
        return params;

    }

    public void drawTriangulation(ImageProcessor ip) {
        DelaunayTriangulation delaunay = doTriangulation(points);
        ip.setColor(1.0);
        for (Iterator iter = delaunay.iterator();
             iter.hasNext(); ) {
            Simplex triangle = (Simplex) iter.next();

            Iterator iter2 = triangle.iterator();
            Pnt a = (Pnt) iter2.next();
            Pnt b = (Pnt) iter2.next();
            Pnt c = (Pnt) iter2.next();
            ip.drawLine((int) Math.round(a.coord(0)), (int) Math.round(a.coord(1)), (int) Math.round(b.coord(0)), (int) Math.round(b.coord(1)));
            ip.drawLine((int) Math.round(a.coord(0)), (int) Math.round(a.coord(1)), (int) Math.round(c.coord(0)), (int) Math.round(c.coord(1)));
            ip.drawLine((int) Math.round(b.coord(0)), (int) Math.round(b.coord(1)), (int) Math.round(c.coord(0)), (int) Math.round(c.coord(1)));

        }

    }

    DelaunayTriangulation doTriangulation(ArrayList<Point2D> points) {
        DelaunayTriangulation delaunay = null;
        double inf = ts.getWidth() + ts.getHeight();
        //TomoJPoints tp = ts.getTomoJPoints();
        Simplex initial = new Simplex(new Pnt[]{
                new Pnt(-inf, -inf),
                new Pnt(-inf, 5 * inf),
                new Pnt(5 * inf, -inf) });
        delaunay = new DelaunayTriangulation(initial);
        for (int i = 0; i < points.size(); i++) {
            Point2D tmp = points.get(i);
            if (tmp != null) delaunay.delaunayPlace(new Pnt(tmp.getX(), tmp.getY()));
        }
        delaunay.size();
        int countTriangles = delaunay.size();

        //System.out.println("triangles:" + countTriangles);
        return delaunay;
    }

    AffineTransform findAffineTransform(ArrayList<Point2D> points1, DelaunayTriangulation delaunay1, ArrayList<Point2D> points2, DelaunayTriangulation delaunay2, double radius) {
        AffineTransform result = new AffineTransform();
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

                    int score = computeScore(t, points1, points2, radius);
                    //System.out.println("compute score" + score);
                    if (score > maxNumberCorrespondences) {
                        maxNumberCorrespondences = score;
                        result = t;
                    }
                }
            }
        }
        //System.out.println("#" + " score : " + maxNumberCorrespondences + "\n" + result);
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
        return findAffineTransformEquations(triangle1, triangle2);
        //return findAffineTransformDirectMatrix(triangle1,triangle2);
    }

    AffineTransform findAffineTransformEquations(Simplex triangle1, Simplex triangle2) {
        Iterator iter = triangle1.iterator();
        Pnt p1 = (Pnt) iter.next();
        Pnt p2 = (Pnt) iter.next();
        Pnt p3 = (Pnt) iter.next();
        iter = triangle2.iterator();
        Pnt p1p = (Pnt) iter.next();
        Pnt p2p = (Pnt) iter.next();
        Pnt p3p = (Pnt) iter.next();

        double xmean = p1.coord(0) + p2.coord(0) + p3.coord(0);
        double xpmean = p1p.coord(0) + p2p.coord(0) + p3p.coord(0);
        double tx = xpmean - xmean;

        double ymean = p1.coord(1) + p2.coord(1) + p3.coord(1);
        double ypmean = p1p.coord(1) + p2p.coord(1) + p3p.coord(1);
        double ty = ypmean - ymean;

        AffineTransform T = new AffineTransform();
        T.translate(tx, ty);
        return T;
    }

    AffineTransform findAffineTransformDirectMatrix(Simplex triangle1, Simplex triangle2) {
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
        double[] mat = new double[]{ result.getQuick(0), result.getQuick(3), result.getQuick(1), result.getQuick(4), result.getQuick(2), result.getQuick(5) };

        return new AffineTransform(mat);

    }

    int computeScore(AffineTransform T, ArrayList<Point2D> p1s, ArrayList<Point2D> p2s, double radius) {
        int nbPoints = 0;
        for (Point2D p1 : p1s) {
            if (p1 != null) {
                Point2D p1t = T.transform(p1, null);
                Point2D best = null;
                for (Point2D p2 : p2s) {
                    if (p2 != null) {
                        if (p2.distance(p1t) < radius) {
                            if (best == null || best.distance(p1t) > p2.distance(p1t)) best = p2;
                        }
                    }
                }
                if (best != null) {
                    nbPoints++;
                }
            }

        }
        return nbPoints;
    }

    HashMap<Point2D, Point2D> createMatches(AffineTransform T, ArrayList<Point2D> p1s, ArrayList<Point2D> p2s, double radius) {
        HashMap<Point2D, Point2D> matches = new HashMap<Point2D, Point2D>();
        int nbPoints = 0;
        for (Point2D p1 : p1s) {
            if (p1 != null) {
                Point2D p1t = T.transform(p1, null);
                Point2D best = null;
                for (Point2D p2 : p2s) {
                    if (p2 != null) {
                        if (p2.distance(p1t) < radius) {
                            if (best == null || best.distance(p1t) > p2.distance(p1t)) best = p2;
                        }
                    }
                }
                if (best != null) {
                    matches.put(p1, best);
                    nbPoints++;
                }
            }

        }
        //System.out.println("matches:" + nbPoints);
        return matches;
    }
}
