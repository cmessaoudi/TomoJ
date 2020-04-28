package fr.curie.tomoj.align;

import fr.curie.plotj.PlotWindow2;
import fr.curie.tomoj.tomography.projectors.VoxelProjector3D;
import fr.curie.utils.powell.Function;
import fr.curie.utils.powell.Powell;
import cern.colt.matrix.tdcomplex.impl.DenseDComplexMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.jet.math.tdcomplex.DComplexFunctions;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.filter.MaximumFinder;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.utils.align.AlignImages;
import fr.curie.utils.Chrono;
import fr.curie.utils.MatrixUtils;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 17/04/2015.
 */
public class SingleTiltAlign {
    TiltSeries ts;
    double completion;

    public SingleTiltAlign(TiltSeries ts) {
        this.ts=ts;
    }

    /**
     * get the completion of processes
     *
     * @return the completion level (divide by the number of images to get the percentage)
     */
    public double getCompletion() {
        return completion/ts.getImageStackSize()*100.0;
    }

    /**
     * allow interruption of processes
     */
    public void interrupt() {
        completion = -1000;
    }



    public void crossCorrelationFFT(int loop) {
        boolean save = ts.iscombiningTransforms();
        int binning=ts.getBinningFactor();
        //combineTransforms(false);
        //float[] pixs = binning(getFilteredPixels(0, applyT), binning);
        System.out.println("integer translation " + ts.isIntegerTranslation());
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);


        int[] s = ts.getAlignmentImageSize();
        int sx = s[0];
        int sy = s[1];
        double cx = (sx) / 2.0;
        double cy = (sy) / 2.0;

        double[] posx = new double[ts.getStackSize()];
        double[] posy = new double[ts.getStackSize()];

        // H1.fft2();
        boolean change = false;
        int nbloop = 0;
        do {
            float[] pixs = ts.getPixelsForAlignment(0);
            completion = 0;
            change = false;
            for (int i = 1; i < ts.getStackSize(); i++) {
                if (completion < 0) return;
                //pixs = binning(getFilteredPixels(i, applyT), binning);
                DenseDoubleMatrix2D H1 = new DenseDoubleMatrix2D(sy, sx);
                H1.assign(pixs);
                DenseDComplexMatrix2D fft1 = H1.getFft2();

                pixs = ts.getPixelsForAlignment(i);
                DenseDoubleMatrix2D H2 = new DenseDoubleMatrix2D(sy, sx);
                H2.assign(pixs);
                DenseDComplexMatrix2D fft2 = H2.getFft2();
                fft1.assign(fft2, DComplexFunctions.multConjSecond);
                fft1.ifft2(true);
                DenseDoubleMatrix2D res = (DenseDoubleMatrix2D) fft1.getRealPart();

                //H2.fft2();
                //DenseFloatMatrix2D res = (DenseFloatMatrix2D) H1.like();
                //convolveFD(H1, H2, res, true);
                //res.idht2(false);
                //res.ifft2(false);
                double[] max = res.getMaxLocation();
                double avg = res.zSum();
                posx[i] = max[2];
                posy[i] = max[1];
                if (posx[i] > cx) {
                    posx[i] -= sx;
                }
                if (posy[i] > cy) {
                    posy[i] -= sy;
                }
                //System.out.println("#"+i+" translation (" + posx[i] + ", " + posy[i] + ")");
                if (completion < 0) return;
                if (!ts.isIntegerTranslation()) {
                    //System.out.println("peak");
                    //floating point translation computation
                    avg /= pixs.length;
                    double seuil = max[0] - ((max[0] - avg) / 3);
                    boolean changement = true;
                    int sens = 1;
                    int startx = (int) posx[i];
                    int starty = (int) posy[i];
                    int endx = startx + 2;
                    int endy = starty + 2;
                    double baryx = 0;
                    double baryy = 0;
                    double total = 0;
                    while (changement) {
                        changement = false;
                        for (int x = sens == 1 ? startx : endx; ((sens == 1 && x <= endx) || (sens == -1 && x >= startx)); x += sens) {
                            //System.out.println("enter x loop");
                            int px = (x < 0) ? x + sx : x;
                            for (int y = sens == 1 ? starty : endy; ((sens == 1 && y <= endy) || (sens == -1 && y >= starty)); y += sens) {
                                //System.out.println("enter y loop");
                                int py = (y < 0) ? y + sy : y;
                                if (res.getQuick(py, px) > seuil) {
                                    for (int l = x - 1; l < x + 2; l++) {
                                        int pl = (l < 0) ? l + sx : l;
                                        for (int m = y - 1; m < y + 2; m++) {
                                            int pm = (m < 0) ? m + sy : m;
                                            double tmpval = res.getQuick(pm, pl);
                                            if (pl < 0 || pl >= sx || pm < 0 || pm >= sy) {
                                                System.out.println("error in the peak loop (" + pl + "," + pm + ")");
                                            }
                                            if (tmpval > seuil) {
                                                res.setQuick(pm, pl, Float.MIN_VALUE);
                                                baryx += l;
                                                baryy += m;
                                                total++;
                                                if (l < startx) {
                                                    startx--;
                                                }
                                                if (l > endx) {
                                                    endx++;
                                                }
                                                if (m < starty) {
                                                    starty--;
                                                }
                                                if (m > endy) {
                                                    endy++;
                                                }
                                                changement = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (total != 0) {
                        posx[i] = baryx / total;
                        posy[i] = baryy / total;
                    } else {
                        System.out.println("total of point in peak =0! seuil=" + seuil + " max value=" + max[0] + " (" + max[1] + ", " + max[2] + ")");
                        int px = (int) max[2];
                        int py = (int) max[1];
                        System.out.println("value in image " + res.getQuick(py, px));
                    }
                    //FloatProcessor fp = new FloatProcessor(sx, sy, (float[])res.elements(), null);
                    //new ImagePlus("Xcorr"+i, fp).show();
                    //System.out.println("#"+i+" after peak search translation (" + posx[i] + ", " + posy[i] + ")");
                }
                //H1 = H2;
                //fft1=fft2;
                completion++;
                if (ts.isShowInIJ()) {
                    IJ.showStatus("" + i + "/" + ts.getStackSize() + "  translation of (" + posx[i] + ", " + posy[i] + ")");
                    IJ.showProgress((double) i / ((double) ts.getStackSize() - 1.0));
                }
                change = change || Math.abs(posx[i]) >= 1 || Math.abs(posy[i]) >= 1;
            }

            if (completion < 0) return;
            for (int i = 0; i < ts.getStackSize() - 1; i++) {
                aa.addTransform(i,new AffineTransform(1,0,0,1,-posx[i + 1] * binning, -posy[i + 1] * binning));
                //aa.addTranslation(i, -posx[i + 1] * binning, -posy[i + 1] * binning);

                if (ts.isShowInIJ())
                    IJ.log("#" + i + " apply translation of (" + (posx[i + 1] * binning) + ", " + (posy[i + 1] * binning) + ")");
            }
            nbloop++;
        } while (change && nbloop < loop);

        if (ts.isShowInIJ()) IJ.showStatus("correct translation finished");
        System.out.println("correct translation finished");
        ts.combineTransforms(save);
    }

    public void crossCorrelationDHT() {
        boolean save = ts.iscombiningTransforms();
        int binning=ts.getBinningFactor();
        //combineTransforms(false);
        //float[] pixs = binning(getFilteredPixels(0, applyT), binning);
        System.out.println("integer translation " + ts.isIntegerTranslation());

        float[] pixs = ts.getPixelsForAlignment(0);
        int[] s = ts.getAlignmentImageSize();
        int sx = s[0];
        int sy = s[1];
        double cx = (sx) / 2.0;
        double cy = (sy) / 2.0;

        double[] posx = new double[ts.getStackSize()];
        double[] posy = new double[ts.getStackSize()];
        DenseDoubleMatrix2D H1 = new DenseDoubleMatrix2D(sy, sx);
        H1.assign(pixs);
        H1.dht2();
        // H1.fft2();
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        for (int i = 1; i < ts.getStackSize(); i++) {
            //pixs = binning(getFilteredPixels(i, applyT), binning);
            pixs = ts.getPixelsForAlignment(i);
            DenseDoubleMatrix2D H2 = new DenseDoubleMatrix2D(sy, sx);
            H2.assign(pixs);
            H2.dht2();
            //H2.fft2();
            DenseDoubleMatrix2D res = (DenseDoubleMatrix2D) H1.like();

            MatrixUtils.convolveFD(H1, H2, res, true);
            res.idht2(false);
            //res.ifft2(false);
            double[] max = res.getMaxLocation();
            double avg = res.zSum();
            posx[i] = max[2];
            posy[i] = max[1];
            if (posx[i] > cx) {
                posx[i] -= sx;
            }
            if (posy[i] > cy) {
                posy[i] -= sy;
            }
            //System.out.println("#"+i+" translation (" + posx[i] + ", " + posy[i] + ")");
            if (!ts.isIntegerTranslation()) {
                //System.out.println("peak");
                //floating point translation computation
                avg /= pixs.length;
                double seuil = max[0] - ((max[0] - avg) / 3);
                boolean changement = true;
                int sens = 1;
                int startx = (int) posx[i];
                int starty = (int) posy[i];
                int endx = startx + 2;
                int endy = starty + 2;
                double baryx = 0;
                double baryy = 0;
                double total = 0;
                while (changement) {
                    changement = false;
                    for (int x = sens == 1 ? startx : endx; ((sens == 1 && x <= endx) || (sens == -1 && x >= startx)); x += sens) {
                        //System.out.println("enter x loop");
                        int px = (x < 0) ? x + sx : x;
                        for (int y = sens == 1 ? starty : endy; ((sens == 1 && y <= endy) || (sens == -1 && y >= starty)); y += sens) {
                            //System.out.println("enter y loop");
                            int py = (y < 0) ? y + sy : y;
                            if (res.getQuick(py, px) > seuil) {
                                for (int l = x - 1; l < x + 2; l++) {
                                    int pl = (l < 0) ? l + sx : l;
                                    for (int m = y - 1; m < y + 2; m++) {
                                        int pm = (m < 0) ? m + sy : m;
                                        double tmpval = res.getQuick(pm, pl);
                                        if (pl < 0 || pl >= sx || pm < 0 || pm >= sy) {
                                            System.out.println("error in the peak loop (" + pl + "," + pm + ")");
                                        }
                                        if (tmpval > seuil) {
                                            res.setQuick(pm, pl, Float.MIN_VALUE);
                                            baryx += l;
                                            baryy += m;
                                            total++;
                                            if (l < startx) {
                                                startx--;
                                            }
                                            if (l > endx) {
                                                endx++;
                                            }
                                            if (m < starty) {
                                                starty--;
                                            }
                                            if (m > endy) {
                                                endy++;
                                            }
                                            changement = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (total != 0) {
                    posx[i] = baryx / total;
                    posy[i] = baryy / total;
                } else {
                    System.out.println("total of point in peak =0! seuil=" + seuil + " max value=" + max[0] + " (" + max[1] + ", " + max[2] + ")");
                    int px = (int) max[2];
                    int py = (int) max[1];
                    System.out.println("value in image " + res.getQuick(py, px));
                }
                //FloatProcessor fp = new FloatProcessor(sx, sy, (float[])res.elements(), null);
                //new ImagePlus("Xcorr"+i, fp).show();
                //System.out.println("#"+i+" after peak search translation (" + posx[i] + ", " + posy[i] + ")");
            }
            H1 = H2;
            if (ts.isShowInIJ()) {
                IJ.showStatus("" + i + "/" + ts.getStackSize() + "  translation of (" + posx[i] + ", " + posy[i] + ")");
                IJ.showProgress((double) i / ((double) ts.getStackSize() - 1.0));
            }
        }
        for (int i = 0; i < ts.getStackSize() - 1; i++) {
            aa.addTransform(i,new AffineTransform(1,0,0,1,-posx[i + 1] * binning, -posy[i + 1] * binning));
           // ts.addTranslation(i, -posx[i + 1] * binning, -posy[i + 1] * binning);
            if (ts.isShowInIJ())
                IJ.log("#" + i + " apply translation of (" + (posx[i + 1] * binning) + ", " + (posy[i + 1] * binning) + ")");
        }
        if (ts.isShowInIJ()) IJ.showStatus("correct translation finished");
        System.out.println("correct translation finished");
        ts.combineTransforms(save);

    }

    public void computeCumulativeReferenceCrossCorrelation(boolean expand) {
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        boolean comb = ts.iscombiningTransforms();
        ts.combineTransforms(false);
        aa.expandForAlignment(expand);
        float[] ref = ts.getPixelsForAlignment(ts.getZeroIndex());
        int[] s = ts.getAlignmentImageSize();
        int sx = s[0];
        int sy = s[1];
        int compt = 0;
        int total = ts.getImageStackSize() - 1;
        completion = 0;
        for (int i = ts.getZeroIndex()- 1; i >= 0; i--) {
            float[] moving = ts.getPixelsForAlignment(i);
            AffineTransform T = AlignImages.computeCrossCorrelationFFT(ref, moving, sx, sy, ts.isIntegerTranslation());
            aa.addTransform(i, T);
            moving = ts.getPixelsForAlignment(i);
            for (int p = 0; p < ref.length; p++) {
                ref[p] += moving[p];
            }
            compt++;
            completion++;
            if (ts.isShowInIJ()) {
                IJ.showStatus(compt + "/" + total + " : cumulative reference crosscorrelation");
            }
        }
        ref = ts.getPixelsForAlignment(ts.getZeroIndex());
        for (int i = ts.getZeroIndex()+ 1; i < ts.getImageStackSize(); i++) {
            float[] moving = ts.getPixelsForAlignment(i);
            AffineTransform T = AlignImages.computeCrossCorrelationFFT(ref, moving, sx, sy, ts.isIntegerTranslation());
            aa.addTransform(i, T);
            moving = ts.getPixelsForAlignment(i);
            for (int p = 0; p < ref.length; p++) {
                ref[p] += moving[p];
            }
            compt++;
            completion++;
            if (ts.isShowInIJ()) {
                IJ.showStatus(compt + "/" + total + " : cumulative reference crosscorrelation");
            }
        }
        aa.expandForAlignment(false);
        if (comb) {
            if (ts.isShowInIJ()) {
                IJ.showStatus("converting alignment");
            }
            ts.combineTransforms(true);
            AffineTransform[] TT = aa.getTransforms();
            AffineTransform[] globalT = Arrays.copyOf(TT, TT.length);


            for (int i = ts.getZeroIndex() + 1; i < ts.getImageStackSize(); i++) {
                try {
                    AffineTransform T = globalT[i].createInverse();
                    AffineTransform T0 = globalT[i - 1];
                    T.concatenate(T0);
                    aa.setTransform(i - 1, T);
                } catch (Exception E) {
                    System.out.println(E);
                }
            }
            //update backward
            for (int i = ts.getZeroIndex() - 1; i > 0; i--) {
                try {
                    AffineTransform T = new AffineTransform(globalT[i]);
                    //AffineTransform T0 = globalT[i+1].createInverse();
                    AffineTransform T0 = aa.getTransform(i + 1).createInverse();
                    T.preConcatenate(T0);
                    aa.setTransform(i, T);
                } catch (Exception E) {
                    System.out.println(E);
                }
            }
        }
    }

    /**
     * compute the affine transforms between consecutive images
     *
     * @param maxShift the maximum shift allowed
     */
    public void computeAffineTransformations(final double maxShift) {
        System.out.println("compute Affine transformations");
        final int nbcpu = Prefs.getThreads();
        completion = 0;
        final Chrono time = new Chrono(ts.getStackSize() - 1);
        final int[] count = new int[1];
        time.start();
        final int N = ts.getStackSize() - 1;
        Future[] ftab = new Future[N];
        ExecutorService exec = Executors.newFixedThreadPool(nbcpu);
        for (int im = 0; im < N; im++) {
            if (completion < 0) return;
            final int im2 = im;
            ftab[im] = exec.submit(new Thread() {
                public void run() {
                    if (completion < 0) return;
                    System.out.println("computing between " + im2 + " and " + (im2 + 1));
                    computeAffineTransformBetweenTwo(im2, im2 + 1, maxShift);
                    count[0]++;
                    completion++;
                    time.stop();
                    //if(showInIJ) IJ.showProgress();
                    System.out.println("elapsed time " + time.delayString() + " / " + time.totalTimeEstimateString(count[0]) + " finishes in " + time.remainString(count[0]));
                }
            });
        }
        try {
            for (Future f : ftab) {
                f.get();
            }
        } catch (Exception e1) {
            System.err.println(e1);
        }
        time.stop();
        System.out.println("affine transformation finished in " + time.delayString());

    }

    public void computeAffineTransformBetweenTwo(int index1, int index2, double maxShift) {
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        int binning=ts.getBinningFactor();
        Rectangle roi=ts.getAlignmentRoi();
        int sx = (roi.getWidth() > 0) ? (int)roi.getWidth() : ts.getWidth();
        int sy = (roi.getWidth() > 0) ? (int)roi.getHeight() : ts.getHeight();
        AffineFitness func = new AffineFitness(ts.getPixelsForAlignment(index1), ts.getPixelsForAlignment(index2), sx / binning, sy / binning, maxShift);
        double[] p = new double[6];
        new AffineTransform().getMatrix(p);
        Powell pow = new Powell(func, p, 0.001);
        p = pow.getP();
        if (binning > 1) {
            p[5] *= binning;
            p[4] *= binning;
        }

        System.out.println("finished in " + pow.getIter() + "iteration with score: " + pow.getFret()+ "aff:"+new AffineTransform(p));
        //ts.getTransform(index1,true,false).concatenate(new AffineTransform(p));
        aa.getTransforms()[index1].concatenate(new AffineTransform(p));

        //transform[index1].concatenate(new AffineTransform(p));
        //invTransform[index1] = null;
    }



    public void centerImages() {
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            ImageProcessor ip = ts.getImageStack().getProcessor(i + 1).convertToByte(true);
            if (ts.getRoi() != null) ip.setRoi(ts.getRoi());
            ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.CENTER_OF_MASS, null);
            double avgx = stats.xCenterOfMass - ts.getCenterX();
            double avgy = stats.yCenterOfMass - ts.getCenterY();

            System.out.println("#" + i + ": centerOfMass (" + stats.xCenterOfMass + ", " + stats.yCenterOfMass + ")\n centerOf Image (" + ts.getCenterX() + ", " + ts.getCenterY() + ")");
            System.out.println("corresponding shift: (" + (-avgx) + ", " + (-avgy) + ")");
            aa.setTransform(i,new AffineTransform(1,0,0,1, -avgx, -avgy));
        }
        aa.convertTolocalTransform();
    }

    /**
        * evaluate Y with a sine function of type y = d + a* sin(x*b +c)
        *
        * @param a
        * @param b
        * @param c
        * @param d
        * @param x in radians
        * @return
        */
       public static double evaluateSine(double a, double b, double c, double d, double x) {
           return (d + a * Math.sin(x * b + c));
       }


    public void alignBySinogramFitting(int nbLines, Point2D.Double posCentered, boolean invert, int radiusCM, int radiusMinMax) {
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        int usedWidth = ts.getWidth() * 3 / 4;
        final Point2D.Double[] centersOfMass = new Point2D.Double[ts.getImageStackSize()];
        FloatProcessor sinogramOri = new FloatProcessor(usedWidth, ts.getImageStackSize());
        MaximumFinder maxfind = new MaximumFinder();
        //Point2D.Double posCentered=new Point2D.Double(0,0);
        //first line
        float[] line = ts.getSubImagePixels(0, usedWidth, nbLines, posCentered, true, ts.iscombiningTransforms());
        FloatProcessor fp = new FloatProcessor(usedWidth, nbLines, line);
        fp.filter(ImageProcessor.BLUR_MORE);
        if (invert) fp.invert();
        fp.add(-fp.getMin());
        //fp.sqrt();
        Polygon poly = maxfind.getMaxima(fp, 0.5, false);
        System.out.println("number of valid points " + poly.npoints);
        int[] maxs = poly.xpoints;
        int bestX = maxs[0];
        if (maxs.length > 1) ;
        for (int x = 1; x < poly.npoints; x++) {
            System.out.println("maxs[" + x + "]:" + maxs[x]);
            if (Math.abs(maxs[x] - usedWidth / 2) < Math.abs(bestX - usedWidth / 2)) bestX = maxs[x];
        }
        fp.setRoi(bestX - radiusCM, 0, 2 * radiusCM + 1, nbLines);
        ImageStatistics stats = ImageStatistics.getStatistics(fp, ImageStatistics.CENTER_OF_MASS, null);
        centersOfMass[0] = new Point2D.Double(Math.toRadians(ts.getTiltAngle(0)), stats.xCenterOfMass);
        for (int x = 0; x < usedWidth; x++) {
            sinogramOri.setf(x, 0, line[x]);
        }


        for (int i = 1; i < ts.getImageStackSize(); i++) {
            //read lines
            line = ts.getSubImagePixels(i, usedWidth, nbLines, posCentered, true, ts.iscombiningTransforms());
            fp = new FloatProcessor(usedWidth, nbLines, line);
            fp.filter(ImageProcessor.BLUR_MORE);
            if (invert) fp.invert();
            fp.add(-fp.getMin());
            //fp.sqrt();
            double T = (i > 2) ? centersOfMass[i - 1].getY() - centersOfMass[i - 2].getY() : 0;
            int startX = (int) (centersOfMass[i - 1].getY() + T);
            bestX = startX;
            for (int dx = 0; dx <= radiusMinMax; dx++) {
                if (startX + dx < usedWidth && line[startX + dx] > line[bestX]) bestX = startX + dx;
                if (startX - dx >= 0 && line[startX - dx] > line[bestX]) bestX = startX - dx;
            }
            fp.setRoi(bestX - radiusCM, 0, 2 * radiusCM + 1, nbLines);
            stats = ImageStatistics.getStatistics(fp, ImageStatistics.CENTER_OF_MASS, null);
            //new ImagePlus(""+i,fp).show();
            centersOfMass[i] = new Point2D.Double(Math.toRadians(ts.getTiltAngle(i)), stats.xCenterOfMass);
            for (int x = 0; x < usedWidth; x++) sinogramOri.setf(x, i, line[x]);
            //System.out.println("#"+i+" center of mass: "+centersOfMass[i].getY());
        }
        ImagePlus sinOri = new ImagePlus("sinogram original", sinogramOri);
        sinOri.show();

        int[] xindex = new int[centersOfMass.length];
        int[] yindex = new int[centersOfMass.length];
        for (int i = 0; i < centersOfMass.length; i++) {
            xindex[i] = i;
            yindex[i] = (int) Math.round(centersOfMass[i].getY());
        }

        sinOri.setRoi(new PolygonRoi(yindex, xindex, xindex.length, Roi.POLYGON));
        sinOri.updateAndRepaintWindow();
        //fit sine
        double[] params = fitSine(centersOfMass);

        System.out.println("sine found : " + params[3] + " + " + params[0] + "*sin(" + params[1] + "x + " + params[2] + ")");
        //align with fitting
        AffineTransform[] transfs = new AffineTransform[ts.getImageStackSize()];
        double[] shifts = new double[ts.getImageStackSize()];
        for (int i = 0; i < transfs.length; i++) {
            transfs[i] = aa.getTransform(i);
            double shift = evaluateSine(params[0], params[1], params[2], params[3], centersOfMass[i].getX()) - centersOfMass[i].getY();
            //System.out.println("#"+i+" shift:"+shift);
            //transfs[i].translate(Math.round(shift), 0);
            transfs[i].translate(shift, 0);
            shifts[i] = shift;
        }
        for (int i = 0; i < transfs.length; i++) {
            aa.setTransform(i, transfs[i]);
        }
        aa.convertTolocalTransform();
        ts.setProjectionCenter(params[3] - (usedWidth / 2.0) + (ts.getWidth() / 2.0), ts.getProjectionCenterY());
        FloatProcessor sinogramAli = new FloatProcessor(usedWidth, ts.getImageStackSize());
        final Point2D.Double[] centersOfMassAfter = new Point2D.Double[ts.getImageStackSize()];
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            //read lines
            line = ts.getSubImagePixels(i, usedWidth, nbLines, posCentered, true, ts.iscombiningTransforms());
            for (int x = 0; x < usedWidth; x++) sinogramAli.setf(x, i, line[x]);
            fp = new FloatProcessor(usedWidth, nbLines, line);
            fp.add(-fp.getMin());
            stats = ImageStatistics.getStatistics(fp, ImageStatistics.CENTER_OF_MASS, null);
            //new ImagePlus(""+i,fp).show();
            centersOfMassAfter[i] = new Point2D.Double(Math.toRadians(ts.getTiltAngle(i)), stats.xCenterOfMass);
        }
        ImagePlus sinAfter = new ImagePlus("sinogram aligned", sinogramAli);
        sinAfter.show();
        PlotWindow2 pw = new PlotWindow2();
        pw.removeAllPlots();
        double[] xs = new double[centersOfMass.length];
        double[] ys = new double[centersOfMass.length];
        double[] ySine = new double[centersOfMass.length];
        double[] yAli = new double[centersOfMass.length];
        double[] yshift = new double[centersOfMass.length];
        for (int i = 0; i < centersOfMass.length; i++) {
            xs[i] = centersOfMass[i].getX();
            ys[i] = centersOfMass[i].getY();
            ySine[i] = evaluateSine(params[0], params[1], params[2], params[3], xs[i]);
            yAli[i] = centersOfMassAfter[i].getY();
            yshift[i] = ys[i] + shifts[i];
        }
        pw.addPlot(xs, ys, Color.BLUE, "centersOfMass before");
        pw.addPlot(xs, yshift, Color.GREEN, "centersOfMass shifted");
        pw.addPlot(xs, yAli, Color.MAGENTA, "centersOfMass after");
        pw.addPlot(xs, ySine, Color.RED, "sine found");
        pw.resetMinMax();
        pw.setVisible(true);

    }

    /**
     * fit a sine function of type d + a* sin(x*b +c)
     *
     * @param points the points with which fitting a sine function
     * @return array containing { a, b, c, d}
     */
    public static double[] fitSine(final Point2D.Double[] points) {
        double[] params = new double[4];
        Function sineEvaluator = new Function() {
            @Override
            protected double eval(double[] xt) {
                double score = 0;
                double tmp;
                for (int i = 0; i < points.length; i++) {
                    tmp = evaluateSine(xt[0], xt[1], xt[2], xt[3], points[i].getX()) - points[i].getY();
                    score += tmp * tmp;
                }
                score /= points.length;
                score = Math.sqrt(score);
                return score;
            }

            @Override
            protected int length() {
                return 4;
            }
        };
        Powell pow = new Powell(sineEvaluator, params, 0.01);
        return params;
    }



    public void refineAlignment(TomoReconstruction2 rec, double rangeDegree, double incDegree) {
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);

        refineAlignmentFindTiltAxis(rec, 10, incDegree);

        AffineTransform[] tmp = new AffineTransform[ts.getImageStack().getSize()];
        for (int i = 0; i < ts.getImageStack().getSize(); i++) {
            tmp[i] = refineAlignment(i, rec, rangeDegree, incDegree);
        }
        for(int i=0;i<ts.getImageStack().getSize();i++) aa.setTransform(i,tmp[i]);
        //transform = tmp;
        aa.convertTolocalTransform();


    }

    public void refineAlignmentFindTiltAxis(TomoReconstruction2 rec, double rangeDegree, double incDegree) {
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        ImageProcessor proj = new FloatProcessor(ts.getWidth(), ts.getHeight());
        ImageProcessor norm = new FloatProcessor(ts.getWidth(), ts.getHeight());
        VoxelProjector3D projector=new VoxelProjector3D(ts,rec,null);
        projector.addProjection(ts.getZeroIndex());
        projector.project();
        proj= projector.getProjectionImage(0);
        norm= projector.getNormImage(0);
        projector.clearAllProjections();


        double bestRot = 0;
        double bestScore = Double.MIN_VALUE;
        double corr;

        for (int i = 0; i < ts.getNSlices(); i++) {
            if (Math.abs(ts.getTiltAngle(i)) < rangeDegree) {
                float[] exp = ts.getPixels(i);
                double rot = AlignImages.computeRotationFFT((float[]) proj.getPixels(), exp, ts.getWidth(), ts.getHeight());
                AffineTransform T = new AffineTransform();
                T.rotate(Math.toRadians(rot));
                double corr1 = AlignImages.correlation((float[]) proj.getPixels(), AlignImages.applyTransform(exp, T, ts.getWidth(), ts.getHeight()));
                T.setToIdentity();
                T.rotate(Math.toRadians(rot + 180));
                double corr2 = AlignImages.correlation((float[]) proj.getPixels(), AlignImages.applyTransform(exp, T, ts.getWidth(), ts.getHeight()));

                if (corr1 > corr2) {
                    corr = corr1;
                    bestRot = rot;
                } else {
                    corr = corr2;
                    System.out.println("add 180�");
                }
                if (bestScore < corr) {
                    bestScore = corr;
                    bestRot = rot + 180;
                }
                System.out.println("tilt " + ts.getTiltAngle(i) + " rotation found " + rot + " score " + corr);
            }
        }
        //float[] exp=getPixelsDual(zeroindex);
        //bestRot=AlignImages.computeRotationFFT((float[])proj.getPixels(),exp,width,height);
        AffineTransform T = new AffineTransform();
        T.rotate(Math.toRadians(bestRot));
        //double corr=AlignImages.correlation((float[])proj.getPixels(),AlignImages.applyTransform(exp,T,width,height));

        System.out.println(" rotation found " + bestRot + " score " + bestScore);

        aa.addToZeroTransform(T);
        ts.setTiltAxis(ts.getTiltAxis() + bestRot);

    }

    public AffineTransform refineAlignment(int index, TomoReconstruction2 rec, double rangeDegree, double incDegree) {
        AffineAlignment aa=(ts.getAlignment()!=null && ts.getAlignment() instanceof AffineAlignment)? (AffineAlignment)ts.getAlignment():new AffineAlignment(ts);
        ts.setAlignment(aa);
        final double tiltAngleOriginal = ts.getTiltAngle(index);
        final float[] exp = ts.getPixels(index);
        ImageProcessor proj = new FloatProcessor(ts.getWidth(), ts.getHeight());
        ImageProcessor norm = new FloatProcessor(ts.getWidth(), ts.getHeight());
        double score = Double.MIN_VALUE;
        double bestAngle = tiltAngleOriginal;
        AffineTransform bestT = new AffineTransform();
        VoxelProjector3D projector=new VoxelProjector3D(ts,rec,null);

        for (double i = rangeDegree; i >= -rangeDegree; i -= incDegree) {
            ts.setTiltAngle(index, tiltAngleOriginal + i);
            //proj.setColor(0);
            //proj.fill();
            projector.addProjection(index);
            projector.project();
            proj= projector.getProjectionImage(0);
            norm= projector.getNormImage(0);
            projector.clearAllProjections();

            //rec.projectBasic(aa.getEulerMatrix(index), proj, norm, 1, true, true, ts.getProjectionCenterX(), ts.getProjectionCenterY());
            //AffineTransform T=AlignImages.align2Images((float[])proj.getPixels(),exp,width,height);
            // new ImagePlus("proj",proj).show();
            AffineTransform T = AlignImages.computeCrossCorrelationFFT((float[]) proj.getPixels(), exp, ts.getWidth(), ts.getHeight(), false);
            double corr = AlignImages.correlation((float[]) proj.getPixels(), AlignImages.applyTransform(exp, T, ts.getWidth(), ts.getHeight()));
            //System.out.println("#"+tiltAngles[index]+" current best "+score+" corr "+corr);
            if (score < corr) {
                score = corr;
                bestAngle = ts.getTiltAngle(index);
                bestT = T;
            }
            /*try{
                System.in.read();
            }catch (Exception e){
                e.printStackTrace();
            }  */

        }
        ts.setTiltAngle(index,bestAngle);
        //addTransform(index,bestT);
        System.out.println("refine alignment " + index + " tilt " + tiltAngleOriginal + " was refined to " + bestAngle);
        if (!bestT.isIdentity()) {
            System.out.println("added Transform " + bestT);
        }
        AffineTransform finalT = aa.getTransform(index);
        finalT.concatenate(bestT);
        return finalT;


    }


}
