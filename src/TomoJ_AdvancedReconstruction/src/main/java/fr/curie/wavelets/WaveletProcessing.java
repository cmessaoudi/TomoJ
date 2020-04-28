//
// parallelizing wavelets from splinewavelets from EPFL
// From Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package fr.curie.wavelets;

import cern.colt.matrix.tdcomplex.DComplexFactory1D;
import cern.colt.matrix.tdcomplex.DComplexMatrix1D;
import cern.colt.matrix.tdcomplex.impl.DenseDComplexMatrix1D;
import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import fractsplinewavelets.FFT1D;
import fractsplinewavelets.Filters;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import imageware.Builder;
import imageware.ImageWare;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

public class WaveletProcessing extends TestCase{
    private static final int REAL = 0;
    private static final int IMAG = 1;

    public WaveletProcessing() {
    }

    public void test(){
        ImagePlus imp=new ImagePlus("C:\\images_test\\test.tif");
        ImageWare buffer= Builder.create(imp, 4);
        WaveletProcessing.doTransform3D(buffer, new int[]{2,2,0},0,3,0);
        float[] pixs=(float[])imp.getProcessor().getPixels();
        for(int y=0;y<=imp.getHeight();y++){
            for(int x=0;x<imp.getWidth();x++){
                imp.getProcessor().putPixelValue(x,y,buffer.getPixel(x,y,0));
            }
        }
        IJ.save(imp,"C:\\images_test\\test_wave.tif");


        int i=2;


    }

    public static void doTransform3D(final ImageWare data, int[] iterations, int waveletType, double waveletDegrees, double waveletShift) {
        int iterX = iterations[0];
        int iterY = iterations[1];
        int iterZ = iterations[2];
        int currentSizeX = data.getSizeX();
        int currentSizeY = data.getSizeY();
        int currentSizeZ = data.getSizeZ();
        int reducedSizeX = currentSizeX;
        int reducedSizeY = currentSizeY;
        int reducedSizeZ = currentSizeZ;
        Filters filterWaveX = new Filters(currentSizeX, waveletType, waveletDegrees, waveletShift);
        filterWaveX.generateAnalysisFilters();
        final double[] realHighpassX = filterWaveX.getRealHighpassFilter();
        final double[] imaginaryHighpassX = filterWaveX.getImaginaryHighpassFilter();
        final double[] realLowpassX = filterWaveX.getRealLowpassFilter();
        final double[] imaginaryLowpassX = filterWaveX.getImaginaryLowpassFilter();
        Filters filterWaveY = new Filters(currentSizeY, waveletType, waveletDegrees, waveletShift);
        filterWaveY.generateAnalysisFilters();
        final double[] realHighpassY = filterWaveY.getRealHighpassFilter();
        final double[] imaginaryHighpassY = filterWaveY.getImaginaryHighpassFilter();
        final double[] realLowpassY = filterWaveY.getRealLowpassFilter();
        final double[] imaginaryLowpassY = filterWaveY.getImaginaryLowpassFilter();
        Filters filterWaveZ = new Filters(currentSizeZ, waveletType, waveletDegrees, waveletShift);
        filterWaveZ.generateAnalysisFilters();
        final double[] realHighpassZ = filterWaveZ.getRealHighpassFilter();
        final double[] imaginaryHighpassZ = filterWaveZ.getImaginaryHighpassFilter();
        final double[] realLowpassZ = filterWaveZ.getRealLowpassFilter();
        final double[] imaginaryLowpassZ = filterWaveZ.getImaginaryLowpassFilter();
        int maxIteration = Math.max(iterX, Math.max(iterY, iterZ));
        ArrayList<Future> futures=new ArrayList<Future>();
        ExecutorService exec= Executors.newFixedThreadPool(Prefs.getThreads());

        for(int iteration = 1; iteration <= maxIteration; ++iteration) {
            FFT1D fft1D_currentSize;
            FFT1D fft1D_reducedSize;
            final int fcurrentSizeX=currentSizeX;
            final int fcurrentSizeY=currentSizeY;
            final int fcurrentSizeZ=currentSizeZ;
            final int fiteration=iteration;
            if(iteration <= iterX) {
                reducedSizeX = currentSizeX / 2;
                final int freducedSizeX=reducedSizeX;

                for(int z = 0; z < currentSizeZ; z += 2) {
                    final int zz=z;
                    for(int y = 0; y < currentSizeY; ++y) {
                        final int yy=y;
                        futures.add(exec.submit(new Thread(){
                            @Override
                            public void run() {
                                double[] row = new double[fcurrentSizeX];
                                double[] rowNextPlane = new double[fcurrentSizeX];
                                data.getX(0, yy, zz, row);
                                data.getX(0, yy, zz + 1 < fcurrentSizeZ?zz + 1:zz, rowNextPlane);
                                FFT1D fft1D_currentSize = new FFT1D(fcurrentSizeX);
                                FFT1D fft1D_reducedSize = new FFT1D(freducedSizeX);
                                fft1D_currentSize.transform(row, rowNextPlane, fcurrentSizeX, 0);
                                double[] var34 = new double[fcurrentSizeX];
                                double[] var37 = new double[fcurrentSizeX];
                                double[] var40 = new double[fcurrentSizeX];
                                double[] var43 = new double[fcurrentSizeX];
                                multiply(row, rowNextPlane, realHighpassX, imaginaryHighpassX, var34, var37, fcurrentSizeX, fiteration);
                                multiply(row, rowNextPlane, realLowpassX, imaginaryLowpassX, var40, var43, fcurrentSizeX, fiteration);
                                downsampling(row, var40, var34, freducedSizeX);
                                downsampling(rowNextPlane, var43, var37, freducedSizeX);
                                fft1D_reducedSize.inverse(row, rowNextPlane, freducedSizeX, freducedSizeX);
                                fft1D_reducedSize.inverse(row, rowNextPlane, freducedSizeX, 0);
                                data.putX(0, yy, zz, row);
                                data.putX(0, yy, zz + 1 < fcurrentSizeZ?zz + 1:zz, rowNextPlane);
                            }
                        }));
                    }
                }
                try {
                    for(Future f:futures) f.get();
                }catch (Exception e){e.printStackTrace();}
            }

            if(iteration <= iterY) {
                reducedSizeY = currentSizeY / 2;
                final int freducedSizeY=reducedSizeY;

                for(int z = 0; z < currentSizeZ; ++z) {
                    final int zz=z;
                    for(int x = 0; x < currentSizeX; x += 2) {
                        final int xx=x;
                        futures.add(exec.submit(new Thread(){
                            public void run(){
                                double[] var18 = new double[fcurrentSizeY];
                                double[] var19 = new double[fcurrentSizeY];
                                data.getY(xx, 0, zz, var18);
                                data.getY(xx + 1 < fcurrentSizeX?xx + 1:xx, 0, zz, var19);
                                FFT1D fft1D_currentSize = new FFT1D(fcurrentSizeY);
                                FFT1D fft1D_reducedSize = new FFT1D(freducedSizeY);
                                fft1D_currentSize.transform(var18, var19, fcurrentSizeY, 0);
                                double[] var35 = new double[fcurrentSizeY];
                                double[] var38 = new double[fcurrentSizeY];
                                double[] var41 = new double[fcurrentSizeY];
                                double[] var44 = new double[fcurrentSizeY];
                                multiply(var18, var19, realHighpassY, imaginaryHighpassY, var35, var38, fcurrentSizeY, fiteration);
                                multiply(var18, var19, realLowpassY, imaginaryLowpassY, var41, var44, fcurrentSizeY, fiteration);
                                downsampling(var18, var41, var35, freducedSizeY);
                                downsampling(var19, var44, var38, freducedSizeY);
                                fft1D_reducedSize.inverse(var18, var19, freducedSizeY, freducedSizeY);
                                fft1D_reducedSize.inverse(var18, var19, freducedSizeY, 0);
                                data.putY(xx, 0, zz, var18);
                                data.putY(xx + 1 < fcurrentSizeX?xx + 1:xx, 0, zz, var19);

                            }
                        }));
                    }
                }
                try {
                    for(Future f:futures) f.get();
                }catch (Exception e){e.printStackTrace();}
            }

            if(iteration <= iterZ) {
                reducedSizeZ = currentSizeZ / 2;
                final int freducedSizeZ=reducedSizeZ;

                for(int x = 0; x < currentSizeX; ++x) {
                    final int xx=x;
                    for(int y = 0; y < currentSizeY; y += 2) {
                        final int yy=y;
                        futures.add(exec.submit(new Thread(){
                            public void run(){
                                double[] var20 = new double[fcurrentSizeZ];
                                double[] var21 = new double[fcurrentSizeZ];
                                double[] var36 = new double[fcurrentSizeZ];
                                double[] var39 = new double[fcurrentSizeZ];
                                double[] var42 = new double[fcurrentSizeZ];
                                double[] var45 = new double[fcurrentSizeZ];

                                data.getZ(xx, yy, 0, var20);
                                data.getZ(xx, yy + 1 < fcurrentSizeY?yy + 1:yy, 0, var21);
                                FFT1D fft1D_currentSize = new FFT1D(fcurrentSizeZ);
                                FFT1D fft1D_reducedSize = new FFT1D(freducedSizeZ);
                                fft1D_currentSize.transform(var20, var21, fcurrentSizeZ, 0);
                                multiply(var20, var21, realHighpassZ, imaginaryHighpassZ, var36, var39, fcurrentSizeZ, fiteration);
                                multiply(var20, var21, realLowpassZ, imaginaryLowpassZ, var42, var45, fcurrentSizeZ, fiteration);
                                downsampling(var20, var42, var36, freducedSizeZ);
                                downsampling(var21, var45, var39, freducedSizeZ);
                                fft1D_reducedSize.inverse(var20, var21, freducedSizeZ, freducedSizeZ);
                                fft1D_reducedSize.inverse(var20, var21, freducedSizeZ, 0);
                                data.putZ(xx, yy, 0, var20);
                                data.putZ(xx, yy + 1 < fcurrentSizeY?yy + 1:yy, 0, var21);
                            }
                        }));
                    }
                }

                try {
                    for(Future f:futures) f.get();
                }catch (Exception e){e.printStackTrace();}
            }

            if(iteration <= iterX) {
                currentSizeX = reducedSizeX;
            }

            if(iteration <= iterY) {
                currentSizeY = reducedSizeY;
            }

            if(iteration <= iterZ) {
                currentSizeZ = reducedSizeZ;
            }
        }

    }

    public static void doInverse3D(final ImageWare data, int[] iterations, int waveletType, double waveletDegrees, double waveletShift) {
        int iterX = iterations[0];
        int iterY = iterations[1];
        int iterZ = iterations[2];
        final int maxSizeX = data.getSizeX();
        final int maxSizeY = data.getSizeY();
        final int maxSizeZ = data.getSizeZ();
        int minSizeX = maxSizeX / (int)Math.pow(2.0D, (double)iterX);
        int minSizeY = maxSizeY / (int)Math.pow(2.0D, (double)iterY);
        int minSizeZ = maxSizeZ / (int)Math.pow(2.0D, (double)iterZ);
        Filters filterWaveletX = new Filters(maxSizeX, waveletType, waveletDegrees, waveletShift);
        filterWaveletX.generateSynthesisFilters();
        final double[] realHighpassX = filterWaveletX.getRealHighpassFilter();
        final double[] imaginaryHighpassX = filterWaveletX.getImaginaryHighpassFilter();
        final double[] realLowpassX = filterWaveletX.getRealLowpassFilter();
        final double[] imaginaryLowpassX = filterWaveletX.getImaginaryLowpassFilter();
        Filters filterWaveletY = new Filters(maxSizeY, waveletType, waveletDegrees, waveletShift);
        filterWaveletY.generateSynthesisFilters();
        final double[] realHighpassY = filterWaveletY.getRealHighpassFilter();
        final double[] imaginaryHighpassY = filterWaveletY.getImaginaryHighpassFilter();
        final double[] realLowpassY = filterWaveletY.getRealLowpassFilter();
        final double[] imaginaryLowpassY = filterWaveletY.getImaginaryLowpassFilter();
        Filters filterWaveletZ = new Filters(maxSizeZ, waveletType, waveletDegrees, waveletShift);
        filterWaveletZ.generateSynthesisFilters();
        final double[] realHighpassZ = filterWaveletZ.getRealHighpassFilter();
        final double[] imaginaryHighpassZ = filterWaveletZ.getImaginaryHighpassFilter();
        final double[] realLowpassZ = filterWaveletZ.getRealLowpassFilter();
        final double[] imaginaryLowpassZ = filterWaveletZ.getImaginaryLowpassFilter();
        int currentSizeX = minSizeX == maxSizeX?maxSizeX:2 * minSizeX;
        int currentSizeY = minSizeY == maxSizeY?maxSizeY:2 * minSizeY;
        int currentSizeZ = minSizeZ == maxSizeZ?maxSizeZ:2 * minSizeZ;
        int maxIter = Math.max(iterX, Math.max(iterY, iterZ));

        ArrayList<Future> futures=new ArrayList<Future>();
        ExecutorService exec= Executors.newFixedThreadPool(Prefs.getThreads());

        for(int iteration = maxIter; iteration >= 1; --iteration) {
            FFT1D fft1D_minSize;
            FFT1D fft1D_currentSize;
            int x;
            if(iteration <= iterZ) {
                fft1D_minSize = new FFT1D(minSizeZ);
                fft1D_currentSize = new FFT1D(currentSizeZ);
                final int sx = iteration <= iterX?currentSizeX:minSizeX;
                final int sy = iteration <= iterY?currentSizeY:minSizeY;
                x = 0;

                while(true) {
                    if(x >= sx) {
                        minSizeZ = currentSizeZ;
                        currentSizeZ = currentSizeZ == maxSizeZ?maxSizeZ:2 * currentSizeZ;
                        break;
                    }
                    final int xx=x;
                    final int fminSizeZ=minSizeZ;
                    final int fcurrentSizeZ=currentSizeZ;
                    final int fmaxSizeZ=maxSizeZ;
                    final int fiteration=iteration;
                    for(int y = 0; y < sy; y += 2) {
                        final int yy=y;
                        futures.add(exec.submit(new Thread(){
                            public void run(){
                                FFT1D fft1D_minSize = new FFT1D(fminSizeZ);
                                FFT1D fft1D_currentSize = new FFT1D(fcurrentSizeZ);

                                double[] var23 = new double[maxSizeZ];
                                double[] var24 = new double[maxSizeZ];
                                double[] var39 = new double[maxSizeZ];
                                double[] var42 = new double[maxSizeZ];
                                double[] var45 = new double[maxSizeZ];
                                double[] var48 = new double[maxSizeZ];
                                double[] var51 = new double[maxSizeZ];
                                double[] var54 = new double[maxSizeZ];
                                double[] var57 = new double[maxSizeZ];
                                double[] var60 = new double[maxSizeZ];
                                data.getZ(xx, yy, 0, var23);
                                data.getZ(xx, yy + 1 < sy?yy + 1:yy, 0, var24);
                                fft1D_minSize.transform(var23, var24, fminSizeZ, 0);
                                fft1D_minSize.transform(var23, var24, fminSizeZ, fminSizeZ);
                                upsampling(var23, var45, var39, fminSizeZ);
                                upsampling(var24, var48, var42, fminSizeZ);
                                multiplyAndConjugate(var39, var42, realHighpassZ, imaginaryHighpassZ, var51, var54, fcurrentSizeZ, fiteration);
                                multiplyAndConjugate(var45, var48, realLowpassZ, imaginaryLowpassZ, var57, var60, fcurrentSizeZ, fiteration);
                                add(var57, var51, var23, fcurrentSizeZ);
                                add(var60, var54, var24, fcurrentSizeZ);
                                fft1D_currentSize.inverse(var23, var24, fcurrentSizeZ, 0);
                                data.putZ(xx, yy, 0, var23);
                                data.putZ(xx, yy + 1 < sy?yy + 1:yy, 0, var24);
                            }
                        }));
                    }
                    try {
                        for(Future f:futures) f.get();
                    }catch (Exception e){e.printStackTrace();}


                    ++x;
                }
            }

            if(iteration <= iterY) {
                fft1D_minSize = new FFT1D(minSizeY);
                fft1D_currentSize = new FFT1D(currentSizeY);
                final int sx = iteration <= iterX?currentSizeX:minSizeX;
                int sy = 0;

                while(true) {
                    if(sy >= minSizeZ) {
                        minSizeY = currentSizeY;
                        currentSizeY = currentSizeY == maxSizeY?maxSizeY:2 * currentSizeY;
                        break;
                    }
                    final int fminSizeY=minSizeY;
                    final int fsy=sy;
                    final int fcurrentSizeY=currentSizeY;
                    final int fiteration=iteration;

                    for(x = 0; x < sx; x += 2) {
                        final int xx=x;
                        futures.add(exec.submit(new Thread(){
                            public void run(){
                                FFT1D fft1D_minSize = new FFT1D(fminSizeY);
                                FFT1D fft1D_currentSize = new FFT1D(fcurrentSizeY);

                                double[] var21 = new double[maxSizeY];
                                double[] var22 = new double[maxSizeY];
                                double[] var38 = new double[maxSizeY];
                                double[] var41 = new double[maxSizeY];
                                double[] var44 = new double[maxSizeY];
                                double[] var47 = new double[maxSizeY];
                                double[] var50 = new double[maxSizeY];
                                double[] var53 = new double[maxSizeY];
                                double[] var56 = new double[maxSizeY];
                                double[] var59 = new double[maxSizeY];
                                data.getY(xx, 0, fsy, var21);
                                data.getY(xx + 1 < sx?xx + 1:xx, 0, fsy, var22);
                                fft1D_minSize.transform(var21, var22, fminSizeY, 0);
                                fft1D_minSize.transform(var21, var22, fminSizeY, fminSizeY);
                                upsampling(var21, var44, var38, fminSizeY);
                                upsampling(var22, var47, var41, fminSizeY);
                                multiplyAndConjugate(var38, var41, realHighpassY, imaginaryHighpassY, var50, var53, fcurrentSizeY, fiteration);
                                multiplyAndConjugate(var44, var47, realLowpassY, imaginaryLowpassY, var56, var59, fcurrentSizeY, fiteration);
                                add(var56, var50, var21, fcurrentSizeY);
                                add(var59, var53, var22, fcurrentSizeY);
                                fft1D_currentSize.inverse(var21, var22, fcurrentSizeY, 0);
                                data.putY(xx, 0, fsy, var21);
                                data.putY(xx + 1 < sx?xx + 1:xx, 0, fsy, var22);
                            }
                        }));
                    }
                    try {
                        for(Future f:futures) f.get();
                    }catch (Exception e){e.printStackTrace();}

                    ++sy;
                }
            }

            if(iteration <= iterX) {
                final int fminSizeX=minSizeX;
                final int fcurrentSizeX=currentSizeX;
                final int fminSizeZ=minSizeZ;
                final int fiteration=iteration;
                for(int sx = 0; sx < minSizeZ; sx += 2) {
                    final int fsx=sx;
                    for(int sy = 0; sy < minSizeY; ++sy) {
                        final int fsy=sy;
                        futures.add(exec.submit(new Thread(){
                            public void run(){

                                double[] var19 = new double[maxSizeX];
                                double[] var20 = new double[maxSizeX];
                                double[] var37 = new double[maxSizeX];
                                double[] var40 = new double[maxSizeX];
                                double[] var43 = new double[maxSizeX];
                                double[] var46 = new double[maxSizeX];
                                double[] var49 = new double[maxSizeX];
                                double[] var52 = new double[maxSizeX];
                                double[] var55 = new double[maxSizeX];
                                double[] var58 = new double[maxSizeX];
                                FFT1D fft1D_minSize = new FFT1D(fminSizeX);
                                FFT1D fft1D_currentSize = new FFT1D(fcurrentSizeX);
                                data.getX(0, fsy, fsx, var19);
                                data.getX(0, fsy, fsx + 1 < fminSizeZ?fsx + 1:fsx, var20);
                                fft1D_minSize.transform(var19, var20, fminSizeX, 0);
                                fft1D_minSize.transform(var19, var20, fminSizeX, fminSizeX);
                                upsampling(var19, var43, var37, fminSizeX);
                                upsampling(var20, var46, var40, fminSizeX);
                                multiplyAndConjugate(var37, var40, realHighpassX, imaginaryHighpassX, var49, var52, fcurrentSizeX, fiteration);
                                multiplyAndConjugate(var43, var46, realLowpassX, imaginaryLowpassX, var55, var58, fcurrentSizeX, fiteration);
                                add(var55, var49, var19, fcurrentSizeX);
                                add(var58, var52, var20, fcurrentSizeX);
                                fft1D_currentSize.inverse(var19, var20, fcurrentSizeX, 0);
                                data.putX(0, fsy, fsx, var19);
                                data.putX(0, fsy, fsx + 1 < fminSizeZ?fsx + 1:fsx, var20);
                            }
                        }));
                    }
                }
                try {
                    for(Future f:futures) f.get();
                }catch (Exception e){e.printStackTrace();}

                minSizeX = currentSizeX;
                currentSizeX = currentSizeX == maxSizeX?maxSizeX:2 * currentSizeX;
            }
        }

    }

    public static void multiply(double[] var0, double[] var1, double[] var2, double[] var3, double[] var4, double[] var5, int var6, int var7) {
        int var9 = (int)Math.pow(2.0D, (double)(var7 - 1));

        for(int var10 = 0; var10 < var6; ++var10) {
            int var8 = var10 * var9;
            var4[var10] = var0[var10] * var2[var8] - var1[var10] * var3[var8];
            var5[var10] = var0[var10] * var3[var8] + var1[var10] * var2[var8];
        }

    }

    public static void multiplyAndConjugate(double[] var0, double[] var1, double[] var2, double[] var3, double[] var4, double[] var5, int var6, int var7) {
        int var9 = (int)Math.pow(2.0D, (double)(var7 - 1));

        for(int var10 = 0; var10 < var6; ++var10) {
            int var8 = var10 * var9;
            var4[var10] = var0[var10] * var2[var8] + var1[var10] * var3[var8];
            var5[var10] = -var0[var10] * var3[var8] + var1[var10] * var2[var8];
        }

    }

    public static void downsampling(double[] var0, double[] var1, double[] var2, int var3) {
        for(int var4 = 0; var4 < var3; ++var4) {
            var0[var4] = 0.5D * (var1[var4] + var1[var4 + var3]);
            var0[var4 + var3] = 0.5D * (var2[var4] + var2[var4 + var3]);
        }

    }

    public static void upsampling(double[] var0, double[] var1, double[] var2, int var3) {
        for(int var4 = 0; var4 < var3; ++var4) {
            var1[var4] = var0[var4];
            var1[var4 + var3] = var0[var4];
            var2[var4] = var0[var4 + var3];
            var2[var4 + var3] = var0[var4 + var3];
        }

    }

    public static void add(double[] var0, double[] var1, double[] var2, int var3) {
        for(int var4 = 0; var4 < var3; ++var4) {
            var2[var4] = var0[var4] + var1[var4];
        }

    }
}

