/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.curie.eftemtomoj.testing;

import fr.curie.eftemtomoj.ImageRegistration;
import fr.curie.eftemtomoj.ImageRegistration.Algorithm;
import fr.curie.eftemtomoj.ImageRegistration.Transform;
import fr.curie.eftemtomoj.Metrics.Metric;
import ij.IJ;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.Random;

/**
 * @author nick
 */
public class RegistrationTests {

    private static void testShiftCorrection(File file1, File file2, boolean withNoise) throws Exception {

        if (file2 == null) file2 = file1;

        ImageProcessor ripIn = IJ.openImage(file1.getAbsolutePath()).getProcessor();
        ImageProcessor mipIn = IJ.openImage(file2.getAbsolutePath()).getProcessor();

        int R = 15;
        int minT = 5;
        int maxT = 55;
        int stepT = 5;
        int minN = withNoise ? 5 : 0;
        int maxN = withNoise ? 75 : 0;
        int stepN = 10;

        System.out.println("# SHIFT CORRECTION TEST");
        System.out.println(String.format("# %1$-20s%2$-40s", "File 1", file1.getName()));
        System.out.println(String.format("# %1$-20s%2$-40s", "File 2", file2.getName()));
        System.out.println(String.format("# %1$-20s%2$-40d", "Repeats", R));

        ImageRegistration irCC = new ImageRegistration(Algorithm.Multiresolution, Metric.NCC);
        ImageRegistration irMI = new ImageRegistration(Algorithm.Multiresolution, Metric.NMI);
        Transform tCC, tMI;

        ImageProcessor rip, mip;
        ImageStack stackOut = new ImageStack(ripIn.getWidth(), ripIn.getHeight());
        Random rand = new Random();
        double ccD, miD, noise;
        int prevT, tx, ty, ccTx, ccTy, miTx, miTy;

        System.out.println(String.format("%1$s,%2$s,%3$s,%4$s,%5$s,%6$s,%7$s,%8$s,%9$s,%10$s,%11$s", "maxShift", "rep", "noise", "realTx", "realTy", "ccTx", "ccTy", "miTx", "miTy", "ccD", "miD"));

        prevT = 0;
        for (int T = minT; T <= maxT; T += stepT) {
            System.out.println(String.format("%1$-20s%2$-40s", "# Shift range", "[" + prevT + ";" + T + "["));

            for (int N = minN; N <= maxN; N += stepN) {
                noise = (withNoise ? 2.55 * N : 0);

                for (int rep = 0; rep < R; rep++) {

                    rip = ripIn.duplicate();
                    mip = mipIn.duplicate();

                    // Generate new shift so that prevT <= |t| < T
                    do {
                        tx = rand.nextInt(T);
                    } while (tx < prevT);

                    do {
                        ty = rand.nextInt(T);
                    } while (ty < prevT);

                    tx = rand.nextBoolean() ? -tx : tx;
                    ty = rand.nextBoolean() ? -ty : ty;

                    // Apply shift
                    mip.translate(tx, ty);

                    // Apply noise
                    if (withNoise) {
                        whiteNoise(rip, noise);
                        whiteNoise(mip, noise);
                    }

                    // Align
                    tCC = irCC.align(rip, mip);
                    tMI = irMI.align(rip, mip);

                    ccTx = (int) Math.round(tCC.getTranslateX());
                    ccTy = (int) Math.round(tCC.getTranslateY());
                    miTx = (int) Math.round(tMI.getTranslateX());
                    miTy = (int) Math.round(tMI.getTranslateY());

                    // Calculate Euclidean distances -- returned values are corrected translations, so opposite sign!!
                    ccD = Math.sqrt((tx + ccTx) * (tx + ccTx) + (ty + ccTy) * (ty + ccTy));
                    miD = Math.sqrt((tx + miTx) * (tx + miTx) + (ty + miTy) * (ty + miTy));

                    System.out.println(String.format("%1$d,%2$d,%3$.4f,%4$d,%5$d,%6$d,%7$d,%8$d,%9$d,%10$.4f,%11$.4f", T, rep, noise, tx, ty, ccTx, ccTy, miTx, miTy, ccD, miD));
                }
            }

            System.out.println("#");

            prevT = T;
        }

    }

    private static void whiteNoise(ImageProcessor ip, double sd) {
        Random rand = new Random();
        double v;
        for (int j = 0; j < ip.getPixelCount(); j++) {
            v = ip.getf(j);
            v += sd * rand.nextGaussian();
            ip.setf(j, (float) v);
        }
    }

    public static void main(String[] args) throws Exception {
        testShiftCorrection(new File("lena-lc2.tif"), new File("lena-lc2.tif"), true);
    }
}
