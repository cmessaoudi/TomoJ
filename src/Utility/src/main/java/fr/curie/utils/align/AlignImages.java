package fr.curie.utils.align;

import cern.colt.function.tfcomplex.FComplexRealFunction;
import cern.colt.matrix.tdcomplex.impl.DenseDComplexMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.jet.math.tdcomplex.DComplexFunctions;
import ij.ImagePlus;
import ij.Prefs;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 17 mai 2010
 * Time: 12:23:12
 * To change this template use File | Settings | File Templates.
 */
public class AlignImages {


    public static AffineTransform align2Images(float[] iRef, float[] img, int sx, int sy) {
        //new ImagePlus("ref", new FloatProcessor(sx, sy, iRef, null)).show();
        //new ImagePlus("mov", new FloatProcessor(sx, sy, img, null)).show();
//        int nbIteration = 1;
        //shift then rotate
        float[] res = new float[img.length];
        float[] resTmp = new float[img.length];
        System.arraycopy(img, 0, res, 0, img.length);
        System.arraycopy(img, 0, resTmp, 0, img.length);
        AffineTransform SR = new AffineTransform();
        AffineTransform SRtmp = new AffineTransform();
        boolean cont = true;
        int compt = 0;
        double scoreSR = 0;
        while (cont && compt < 15) {
            double scoreTmp;

            AffineTransform T = computeCrossCorrelationFFT(iRef, resTmp, sx, sy, true);
            SRtmp.preConcatenate(T);
            System.out.println(compt + " translation detected first (" + SRtmp.getTranslateX() + ", " + SRtmp.getTranslateY() + ")");
            resTmp = applyTransform(img, SRtmp, sx, sy);
            double r = computeRotationFFT(iRef, resTmp, sx, sy);
            System.out.println(compt + " rotation detected second " + r);
            AffineTransform Rt = new AffineTransform();
            Rt.rotate(StrictMath.toRadians(r));
            SRtmp.preConcatenate(Rt);
            resTmp = applyTransform(img, SRtmp, sx, sy);
            double dist = T.getTranslateX() * T.getTranslateX() + T.getTranslateY() * T.getTranslateY();
            if (StrictMath.sqrt(dist) < 1.5) cont = false;
            scoreTmp = correlation(iRef, resTmp);
            System.out.println("Score SR : " + scoreTmp);
            if (scoreTmp > scoreSR) {
                scoreSR = scoreTmp;
                System.arraycopy(resTmp, 0, res, 0, img.length);
                SR = new AffineTransform(SRtmp);
            } else {
                cont = false;
            }
            compt++;
        }
        scoreSR = correlation(iRef, res);
        System.out.println("score translation first then rotation : " + scoreSR);
        double[] mat = new double[6];
        SR.getMatrix(mat);
        double ang = StrictMath.toDegrees(StrictMath.atan2(mat[1], mat[3]));
        System.out.println("rot=" + ang + " transl=(" + SR.getTranslateX() + ", " + SR.getTranslateY() + ")");

        //rotate then shift
        AffineTransform RS = new AffineTransform();
        AffineTransform RStmp = new AffineTransform();
        System.arraycopy(img, 0, res, 0, img.length);
        System.arraycopy(img, 0, resTmp, 0, img.length);
        cont = true;
        compt = 0;
        double scoreRS = 0;
        while (cont && compt < 15) {
            double scoreTmp;
            double r = computeRotationFFT(iRef, resTmp, sx, sy);
            AffineTransform Rt = new AffineTransform();
//            if (compt < 1) Rt.rotate(StrictMath.toRadians(-90));
            Rt.rotate(StrictMath.toRadians(r));
            System.out.println(compt + " rotation detected first " + r);
            RStmp.preConcatenate(Rt);
            resTmp = applyTransform(img, RStmp, sx, sy);
            AffineTransform Tr = computeCrossCorrelationFFT(iRef, resTmp, sx, sy, true);
            System.out.println(compt + " translation detected second (" + Tr.getTranslateX() + ", " + Tr.getTranslateY() + ")");
            RStmp.preConcatenate(Tr);
            resTmp = applyTransform(img, RStmp, sx, sy);
            double dist = Tr.getTranslateX() * Tr.getTranslateX() + Tr.getTranslateY() * Tr.getTranslateY();
            if (StrictMath.sqrt(dist) < 1.5) cont = false;
            scoreTmp = correlation(iRef, resTmp);
            System.out.println(compt + " Score tmp : " + scoreTmp);
            System.out.println(compt + " Score RS : " + scoreRS);
            if (scoreTmp > scoreRS) {
                scoreRS = scoreTmp;
                System.arraycopy(resTmp, 0, res, 0, img.length);
                RS = new AffineTransform(RStmp);
            } else {
                cont = false;
            }
            compt++;
        }
        scoreRS = correlation(iRef, res);
        System.out.println("score rotation first then translation : " + scoreRS);
        mat = new double[6];
        RS.getMatrix(mat);
        ang = StrictMath.toDegrees(StrictMath.atan2(mat[1], mat[3]));
        System.out.println("rot=" + ang + " transl=(" + RS.getTranslateX() + ", " + RS.getTranslateY() + ")");

        //keep the best
        AffineTransform T = RS;
        if (scoreSR > scoreRS) {
            T = SR;
            System.out.println("SR is the best one");
        } else {
            System.out.println("RS is the best one");
        }
        return T;
    }

    /**
     * Find the affine transformation between two images. Compute rotation first
     * by a step (in degrees) given as argument.
     *
     * @author Antoine Cossa
     *
     * @param iRef image which will be used as reference
     * @param img  image which has to be transformed to match the reference image
     * @param sx   images width
     * @param sy   images height
     * @param step for rotation (in degrees). Commonly: 5, 10 or 90 degrees
     * @return the AffineTransform that needs to be applied to "img" to match "iRef"
     */
    public static AffineTransform align2ImagesImproved(float[] iRef, float[] img, int sx, int sy, double step) {
        float[] res = new float[img.length];
        float[] resTmp = new float[img.length];
        System.arraycopy(img, 0, res, 0, img.length);
//        System.arraycopy(img, 0, resTmp, 0, img.length);
        System.arraycopy(img, 0, resTmp, 0, img.length);

        // Final transformation
        AffineTransform T = new AffineTransform();

        // Step in degrees
        double score;
        double scoreMax = -1;

        AffineTransform RS;    // Best AT for the "for" loop
        AffineTransform RStmp; // Best AT for the "while" loop

        for (int i = 0; i < 360 / step; i++) {
            // Reset temp transformations
            RStmp = new AffineTransform();
            RS = new AffineTransform();

            // Rotate
            RStmp.rotate(StrictMath.toRadians(step * (double) i));
//            System.out.println("Rotation: " + i * step);
//            System.out.println("Rotation: " + StrictMath.toDegrees(StrictMath.atan2(RStmp.getShearY(), RStmp.getScaleY()))); // TEST
            resTmp = applyTransform(img, RStmp, sx, sy);

            boolean cont = true;
            int compt = 0;
            double scoreRS = -1;

            // Search best translation
            while (cont && compt < 5) {
                compt++;
                double scoreTmp;

                AffineTransform Tr = computeCrossCorrelationFFT(iRef, resTmp, sx, sy, true);
//                System.out.println(compt + " translation detected (" + Tr.getTranslateX() + ", " + Tr.getTranslateY() + ")");
                RStmp.preConcatenate(Tr);
                resTmp = applyTransform(img, RStmp, sx, sy);
                double dist = Tr.getTranslateX() * Tr.getTranslateX() + Tr.getTranslateY() * Tr.getTranslateY();
                if (StrictMath.sqrt(dist) < 1.5) cont = false;
                scoreTmp = correlation(iRef, resTmp);
//                System.out.println(compt + " Score tmp : " + scoreTmp);
//                System.out.println(compt + " Score RS : " + scoreRS);

                if (scoreTmp > scoreRS) {
                    scoreRS = scoreTmp;
                    System.arraycopy(resTmp, 0, res, 0, img.length);
                    RS = new AffineTransform(RStmp);
                } else {
                    cont = false;
                }
            }

            score = correlation(iRef, res);
//            System.out.println("score : " + score);

            // Keep the best
            if (score > scoreMax) {
                scoreMax = score;
                T = new AffineTransform(RS);
            }

        }
//        double[] mat = new double[6];
//        T.getMatrix(mat);
//        double ang = StrictMath.toDegrees(StrictMath.atan2(mat[1], mat[3]));
        double ang = StrictMath.toDegrees(StrictMath.atan2(T.getShearY(), T.getScaleY())); // (m10, m11)
        System.out.println("\nRot = " + ang + " ; Transl = (" + T.getTranslateX() + ", " + T.getTranslateY() + ") ; Score = " + scoreMax + "\n");

        return T;
    }

    /**
     * Find the affine transformation between two images using multiple threads. Compute rotation first
     * by a step (in degrees) given as argument.
     *
     * @author Antoine Cossa
     *
     * @param iRef image which will be used as reference
     * @param img  image which has to be transformed to match the reference image
     * @param sx   images width
     * @param sy   images height
     * @param step for rotation (in degrees). Commonly: 0.1, 1, 5, 10 or 90 degrees
     * @return the AffineTransform that needs to be applied to "img" to match "iRef"
     */
    public static AffineTransform align2ImagesImprovedMultiThread(float[] iRef, float[] img, int sx, int sy, double step) {
        // Number of separate operations to do
        int operations = (int) (360 / step);

        final double[] score = new double[operations];
        final AffineTransform[] AT = new AffineTransform[operations];

        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(operations);

        for (int i = 0; i < operations; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread(() -> {
                float[] resTmp = new float[img.length];
                System.arraycopy(img, 0, resTmp, 0, img.length);
                float[] res = new float[img.length];
                System.arraycopy(img, 0, res, 0, img.length);

                // Reset temp transformations
                AffineTransform RStmp = new AffineTransform();
                AffineTransform RS = new AffineTransform();

                // Rotate
                RStmp.rotate(StrictMath.toRadians(step * (double) ii));
//            System.out.println("Rotation: " + i * step);
//            System.out.println("Rotation: " + StrictMath.toDegrees(StrictMath.atan2(RStmp.getShearY(), RStmp.getScaleY()))); // TEST
                resTmp = applyTransform(img, RStmp, sx, sy);

                boolean cont = true;
                int compt = 0;
                double scoreRS = -1;

                // Search best translation
                while (cont && compt < 5) {
                    compt++;
                    double scoreTmp;

                    AffineTransform Tr = computeCrossCorrelationFFT(iRef, resTmp, sx, sy, true);
//                System.out.println(compt + " translation detected (" + Tr.getTranslateX() + ", " + Tr.getTranslateY() + ")");
                    RStmp.preConcatenate(Tr);
                    resTmp = applyTransform(img, RStmp, sx, sy);
                    double dist = Tr.getTranslateX() * Tr.getTranslateX() + Tr.getTranslateY() * Tr.getTranslateY();
                    if (StrictMath.sqrt(dist) < 1.5) cont = false;
                    scoreTmp = correlation(iRef, resTmp);
//                System.out.println(compt + " Score tmp : " + scoreTmp);
//                System.out.println(compt + " Score RS : " + scoreRS);

                    if (scoreTmp > scoreRS) {
                        scoreRS = scoreTmp;
                        System.arraycopy(resTmp, 0, res, 0, img.length);
                        RS = new AffineTransform(RStmp);
                    } else {
                        cont = false;
                    }
                }

                AT[ii] = new AffineTransform(RS);
                score[ii] = correlation(iRef, res);
//            System.out.println("score : " + score);
            })));
        }

        for (Future f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Final transformation
        AffineTransform T = new AffineTransform();

        double scoreMax = -1;

        for (int i = 0; i < score.length; i++) {
            if (score[i] > scoreMax) {
                scoreMax = score[i];
                T = new AffineTransform(AT[i]);
            }
        }

//        double[] mat = new double[6];
//        T.getMatrix(mat);
//        double ang = StrictMath.toDegrees(StrictMath.atan2(mat[1], mat[3]));
        double ang = StrictMath.toDegrees(StrictMath.atan2(T.getShearY(), T.getScaleY())); // (m10, m11)
        System.out.println("\nRot = " + ang + " ; Transl = (" + T.getTranslateX() + ", " + T.getTranslateY() + ") ; Score = " + scoreMax + "\n");

        return T;
    }


    public static AffineTransform computeCrossCorrelationFFT(float[] ref, float[] moving, int sx, int sy, boolean integerTranslation) {
        double cx = (sx) / 2.0;
        double cy = (sy) / 2.0;

        DenseDoubleMatrix2D H1 = new DenseDoubleMatrix2D(sy, sx);
        H1.assign(ref);
        DenseDComplexMatrix2D fft1 = H1.getFft2();

        DenseDoubleMatrix2D H2 = new DenseDoubleMatrix2D(sy, sx);
        H2.assign(moving);
        DenseDComplexMatrix2D fft2 = H2.getFft2();
        fft1.assign(fft2, DComplexFunctions.multConjSecond);
        fft1.ifft2(true);
        DenseDoubleMatrix2D res = (DenseDoubleMatrix2D) fft1.getRealPart();
        double[] max = res.getMaxLocation();
        double avg = res.zSum();
        double posx = max[2];
        double posy = max[1];
        if (posx > cx) {
            posx -= sx;
        }
        if (posy > cy) {
            posy -= sy;
        }
        //System.out.println("#"+i+" translation (" + posx[i] + ", " + posy[i] + ")");
        if (!integerTranslation) {
            //System.out.println("peak");
            //floating point translation computation
            avg /= ref.length;
            double seuil = max[0] - ((max[0] - avg) / 3);
            boolean changement = true;
            int sens = 1;
            int startx = (int) posx;
            int starty = (int) posy;
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
                posx = baryx / total;
                posy = baryy / total;
            } else {
                System.out.println("total of point in peak =0! seuil=" + seuil + " max value=" + max[0] + " (" + max[1] + ", " + max[2] + ")");
                int px = (int) max[2];
                int py = (int) max[1];
                System.out.println("value in image " + res.getQuick(py, px));
            }
        }
        return new AffineTransform(1, 0, 0, 1, posx, posy);
    }

    public static float[] applyTransform(final float[] imgpix, final AffineTransform T, final int width, final int height) {
        //Chrono time=new Chrono();
        //time.start();
        final float[] respix;
        if (T.isIdentity()) {
            respix = imgpix;
        } else {
            respix = new float[width * height];
            double centerx = (width) / 2.0;
            double centery = (height) / 2.0;
            //ImageStatistics stats = new FloatStatistics(new FloatProcessor(width, height, imgpix, null));

            //double[] TinvMatrix = new double[6];
            final AffineTransform Tinv;
            try {
                //AffineTransform Tinv = affT.createInverse();
                //T.createInverse().getMatrix(TinvMatrix);
                Tinv = T.createInverse();
            } catch (Exception e) {
                System.out.println("error in getting pixels!!!" + e);
                return null;
            }


            float pix;
            double xx;
            double yy;
            int jj;
            int ix0, iy0;
            double dx0, dy0;
            double fac4;// fac1 ,fac2, fac3 ;
            float value1, value2, value3, value4;
            int pos;
            //Point2D tmp;
            //Point2D res;
            double jc;
            final double[] ligne = new double[width * 2];
            final double[] res = new double[width * 2];
            for (int i = 0; i < width; i++) {
                ligne[i * 2] = i - centerx;
            }
            for (int j = 0; j < height; j++) {
                jj = j * width;
                jc = j - centery;
                for (int i = 1; i < ligne.length; i += 2) {
                    ligne[i] = jc;
                }
                Tinv.transform(ligne, 0, res, 0, width);
                //System.out.println("different line");
                for (int i = 0; i < width; i++) {
                    xx = res[i * 2] + centerx;
                    yy = res[i * 2 + 1] + centery;
                    ix0 = (int) xx;
                    iy0 = (int) yy;
                    dx0 = xx - ix0;
                    dy0 = yy - iy0;

                    //System.out.println("dx0="+dx0+" dy0="+dy0);
                    if (ix0 >= 0 && ix0 < width && iy0 >= 0 && iy0 < height) {
                        //en bas a gauche
                        if (ix0 == width - 1 || iy0 == height - 1) {
                            pix = imgpix[ix0 + iy0 * width];
                        } else {
                            fac4 = (dx0 * dy0);
                            value1 = imgpix[pos = ix0 + iy0 * width];
                            value3 = imgpix[pos + width];
                            value2 = imgpix[++pos];
                            value4 = imgpix[pos + width];
                            pix = (float) (value1 * (1 - dx0 - dy0 + fac4) + value2 * (dx0 - fac4) + value3 * (dy0 - fac4) + value4 * fac4);
                        }
                        //result.putPixelValue(i, j, pix);
                        respix[jj + i] = pix;

                    }
                }
            }
        }
        //time.stop();
        //System.out.println("getPixel time "+time.delay());
        return respix;
    }

    public static double computeRotationFFT(float[] iRef, float[] img, int sx, int sy) {
        int width = 2 * 180 / 1;
        int height = Math.min(sx, sy) / 2;
        /* float[] polarRef=toPolar(iRef,sx,sy,180,1);
       float[] polarImg=toPolar(img,sx,sy,180,1);
       //new ImagePlus("polar ref",new FloatProcessor(width,height,polarRef,null)).show();
       //new ImagePlus("polar img",new FloatProcessor(width,height,polarImg,null)).show();
       AffineTransform T= computeCrossCorrelationFFT(polarRef,polarImg,width,height,true);
       double r=T.getTranslateX()*1;
       return r;*/
        DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sx, sy);
        H1.assign(iRef);
        DenseFComplexMatrix2D fft1 = H1.getFft2();
        fft1 = getRearrangedDataReferences(fft1);

        // log power spectrum
        FloatMatrix2D powerspectrum1 = fft1.assign(new FComplexRealFunction() {
            public final float apply(float[] a) {
                return (float) Math.log(1.0 + a[0] * a[0] + a[1] * a[1]);
            }
        }).getRealPart();

        DenseFloatMatrix2D H2 = new DenseFloatMatrix2D(sx, sy);
        H2.assign(img);
        DenseFComplexMatrix2D fft2 = H2.getFft2();
        fft2 = getRearrangedDataReferences(fft2);

        // log power spectrum
        FloatMatrix2D powerspectrum2 = fft2.assign(new FComplexRealFunction() {
            public final float apply(float[] a) {
                return (float) Math.log(1.0 + a[0] * a[0] + a[1] * a[1]);
            }
        }).getRealPart();

        float[] ps1 = (float[]) powerspectrum1.elements();
        float[] ps2 = (float[]) powerspectrum2.elements();
        ps1 = toPolar(ps1, powerspectrum1.rows(), powerspectrum1.columns(), 180, 1);
        ps2 = toPolar(ps2, powerspectrum2.rows(), powerspectrum2.columns(), 180, 1);

        // new ImagePlus("polar ps1",new FloatProcessor(width,height,ps1,null)).show();
        // new ImagePlus("polar ps2",new FloatProcessor(width,height,ps2,null)).show();
        AffineTransform T = computeCrossCorrelationFFT(ps1, ps2, width, height, true);
        double r = T.getTranslateX() * 1;

        return r;
    }

    /**
     * non normalized correlation between to arrays. The arrays have to be normalized before
     *
     * @param array1 Description of the Parameter
     * @param array2 Description of the Parameter
     * @return Description of the Return Value
     */
    public static double unNormalizedCorrelation(float[] array1, float[] array2) {
        double corr = 0;
        for (int i = 0; i < array1.length; i++) {
            corr += array1[i] * array2[i];
        }
        return corr / array1.length;
    }

    public static double correlation(float[] array1, float[] array2) {
        double avg1 = 0;
        double avg2 = 0;
        int tot = 0;
        int size = array1.length;
        for (int i = 0; i < size; i++) {
            avg1 += array1[i];
            avg2 += array2[i];
            tot++;
        }
        avg1 /= tot;
        avg2 /= tot;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double val1;
        double val2;
        for (int i = 0; i < size; i++) {
            val1 = (array1[i] - avg1);
            val2 = (array2[i] - avg2);
            sum1 += val1 * val2;
            sum2 += val1 * val1;
            sum3 += val2 * val2;
            tot++;
        }
        return sum1 / Math.sqrt(sum2 * sum3);
    }

    public static double correlation(ImageProcessor img1, ImageProcessor img2) {
        System.out.println("correlation processor");
        double avg1 = 0;
        double avg2 = 0;
        int tot = 0;
        int size = img1.getWidth() * img2.getHeight();
        for (int y = 0; y < img1.getHeight(); y++) {
            for (int x = 0; x < img1.getWidth(); x++) {
                avg1 += img1.getf(x, y);
                avg2 += img2.getf(x, y);
                tot++;
            }
        }
        avg1 /= tot;
        avg2 /= tot;
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double val1;
        double val2;
        for (int y = 0; y < img1.getHeight(); y++) {
            for (int x = 0; x < img1.getWidth(); x++) {
                val1 = (img1.getf(x, y) - avg1);
                val2 = (img2.getf(x, y) - avg2);
                sum1 += val1 * val2;
                sum2 += val1 * val1;
                sum3 += val2 * val2;
                tot++;
            }
        }
        return sum1 / Math.sqrt(sum2 * sum3);
    }

    public static double mse(float[] img1, float[] img2) {
        int tot = 0;
        int size = img1.length;
        double val;
        for (int i = 0; i < size; i++) {
            val = img1[i] - img2[i];
            tot += val * val;
        }
        return tot / size;
    }

    public static double rmse(float[] img1, float[] img2) {
        return Math.sqrt(mse(img1, img2));
    }

    public static double psnr(float[] img1, float[] img2) {
        double rmse = rmse(img1, img2);
        double psnr = 10 * Math.log10((Math.pow(2, 32) - 1) / rmse);
        return psnr;
    }

    public static double mse(ImageProcessor img1, ImageProcessor img2) {
        System.out.println("mse processor");
        double tot = 0;
        double size = img1.getWidth() * img1.getHeight();
        double val;
        for (int y = 0; y < img1.getHeight(); y++) {
            for (int x = 0; x < img1.getWidth(); x++) {
                val = img1.getf(x, y) - img2.getf(x, y);
                tot += val * val;
            }
        }
        return tot / size;
    }

    public static double rmse(ImageProcessor img1, ImageProcessor img2) {
        System.out.println("rmse processor");
        return Math.sqrt(mse(img1, img2));
    }

    public static double psnr(ImageProcessor img1, ImageProcessor img2, double range) {
        System.out.println("psnr processor");
        double rmse = rmse(img1, img2);
        double psnr = 10 * Math.log10(range * range / rmse);
        return psnr;
    }

    /**
     * Do rearrangement (swap quadrants) on a new copy of the data
     * <p/>
     * a b c d e --> c d e a b a b c d e f --> d e f a b c (in all three
     * dimensions)
     * taken from FFTJ
     *
     * @return rearranged data
     */
    private static DenseFComplexMatrix2D getRearrangedDataReferences(DenseFComplexMatrix2D data) {
        int sx = data.rows();
        int sy = data.columns();
        int cr = (int) Math.round(sx / 2d);
        int cc = (int) Math.round(sy / 2d);
        DenseFComplexMatrix2D P1 = new DenseFComplexMatrix2D(sx, sy);
        P1.viewPart(0, 0, sx - cr, sy - cc).assign(data.viewPart(cr, cc, sx - cr, sy - cc));
        P1.viewPart(0, sy - cc, sx - cr, cc).assign(data.viewPart(cr, 0, sx - cr, cc));
        P1.viewPart(sx - cr, 0, cr, sy - cc).assign(data.viewPart(0, cc, cr, sy - cc));
        P1.viewPart(sx - cr, sy - cc, cr, cc).assign(data.viewPart(0, 0, cr, cc));
        return P1;
    }

    /**
     * convert image to the polar coordinate system <BR>
     * all angles are not taken, only those between +/- range
     *
     * @param img            image to convert (not modified)
     * @param range          the rotation taken for new image will be between
     *                       [-range,+range[
     * @param angleprecision the precision wanted (1 for degrees precision, 0.1
     *                       for decidegrees precision)
     * @return converted image
     */
    public static float[] toPolar(float[] img, final int sx, final int sy, double range, double angleprecision) {
        FloatProcessor fp = new FloatProcessor(sx, sy, img, null);
        fp.setInterpolationMethod(FloatProcessor.BICUBIC);
        int width = (int) (2 * range / angleprecision);
        int height = Math.min(sx, sy) / 2;
        int centerx = sx / 2;
        int centery = sy / 2;
        double x;
        double y;
        int jj = 0;
        float[] result = new float[width * height];
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                x = (j * Math.cos(Math.toRadians(i - range)) + centerx);
                y = (j * Math.sin(Math.toRadians(i - range)) + centery);
                //result[jj+i]=img[x+y*sx];
                result[jj + i] = (float) fp.getInterpolatedPixel(x, y);
            }
            jj += width;
        }
        //new ImagePlus("polar representation", new FloatProcessor(width,height,result,null)).show();
        return result;
    }

    /**
     * Description of the Method
     *
     * @param matches list of matches
     * @param centerX of the images, must equal 0 if points already have the image center as origin.
     * @param centerY of the images, must equal 0 if points already have the image center as origin.
     * @return Rigid transform between the matching points
     */
    public static AffineTransform meanSquare(HashMap<Point2D, Point2D> matches, double centerX, double centerY) {
        double moyX1 = 0, moyY1 = 0, moyX2 = 0, moyY2 = 0;
        for (Point2D key : matches.keySet()) {
            Point2D val = matches.get(key);
            moyX1 += key.getX() - centerX;
            moyY1 += key.getY() - centerY;
            moyX2 += val.getX() - centerX;
            moyY2 += val.getY() - centerY;
        }
        moyX1 /= matches.size();
        moyY1 /= matches.size();
        moyX2 /= matches.size();
        moyY2 /= matches.size();

        double k11 = 0, k12 = 0, k21 = 0, k22 = 0;
        for (Point2D key : matches.keySet()) {
            Point2D val = matches.get(key);
            k11 = (key.getX() - centerX - moyX1) * (val.getX() - centerX - moyX2);
            k12 = (key.getY() - centerY - moyY1) * (val.getY() - centerY - moyY2);
            k21 = (val.getX() - centerX - moyX2) * (key.getY() - centerY - moyY1);
            k22 = (key.getX() - centerX - moyX1) * (val.getY() - centerY - moyY2);
        }

        double k1 = k11 + k12;
        double k2 = k21 - k22;
        double cosa = k1 / Math.sqrt((k1 * k1 + k2 * k2));
        double sina = -k2 / Math.sqrt((k1 * k1 + k2 * k2));
        double a = -Math.toDegrees(Math.acos(cosa));
        double a2 = -Math.toDegrees(Math.asin(sina));
        double dx = moyX2 - (moyX1 * cosa - moyY1 * sina);
        double dy = moyY2 - (moyX1 * sina + moyY1 * cosa);

        System.out.println("Fonction mean square dx=" + dx + ", dy=" + dy + ", r(depuis cos)="
                + a + " ou (depuis sin)" + a2);
        if (Double.isNaN(dx)) {
            dx = 0;
        }
        if (Double.isNaN(dy)) {
            dy = 0;
        }
        if (Double.isNaN(a2)) {
            a2 = 0;
        }

        AffineTransform result = new AffineTransform();
        result.translate(dx, dy);
        result.rotate(Math.toRadians(a2));
        return result;
    }


}
