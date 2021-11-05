/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.testing;

import fr.curie.eftemtomoj.FilteredImage;
import fr.curie.eftemtomoj.MSANoiseFilter;
import fr.curie.eftemtomoj.MSANoiseFilter.PCA;
import fr.curie.eftemtomoj.Mapper.MapperTask;
import fr.curie.eftemtomoj.Mapper.Model;
import fr.curie.eftemtomoj.Metrics;
import fr.curie.eftemtomoj.TiltSeries;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import javax.swing.*;
import java.io.File;

/**
 * @author Nick Aschman
 */
public class Evaluator {
    private static void compareImageStacks(File file1, File file2) {
        JFileChooser chooser = new JFileChooser();
        if (file1 == null) {
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                return;

            file1 = chooser.getSelectedFile();
        }

        ImageStack is1 = IJ.openImage(file1.getAbsolutePath()).getImageStack();

        if (file2 == null) {
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                return;

            file2 = chooser.getSelectedFile();
        }

        ImageStack is2 = IJ.openImage(file2.getAbsolutePath()).getImageStack();

        if (is1.getSize() != is2.getSize())
            return;

        System.out.println(String.format("%1$-12s%2$-40s", "Stack 1", file1.getName()));
        System.out.println(String.format("%1$-12s%2$-40s", "Stack 2", file2.getName()));
        System.out.println(String.format("%1$-12s%2$-40d", "Size", is1.getSize()));
        System.out.println("");

        ImageStatistics st1, st2;
        ImageProcessor ip1, ip2;
        double ms, cc;

        for (int i = 0; i < is1.getSize(); i++) {
            ip1 = is1.getProcessor(i + 1);
            ip2 = is2.getProcessor(i + 1);

            st1 = ip1.getStatistics();
            st2 = ip2.getStatistics();

            ms = Metrics.ms(ip1, ip2);
            cc = Metrics.ncc(ip1, ip2);

            System.out.println(String.format("%1$-12s%2$-12s%3$-12s%4$-12s%5$-12s%6$-12s%7$-12s", "Slice " + i, "Mean", "StdDev", "Min", "Max", "MSD", "CC"));
            System.out.println(String.format("%1$-12s%2$-12.4f%3$-12.4f%4$-12.4f%5$-12.4f%6$-12.4f%7$-12.4f", "Stack 1", st1.mean, st1.stdDev, st1.min, st1.max, ms, cc));
            System.out.println(String.format("%1$-12s%2$-12.4f%3$-12.4f%4$-12.4f%5$-12.4f%6$-12s%7$-12s", "Stack 2", st2.mean, st2.stdDev, st2.min, st2.max, "-", "-"));
            System.out.println("");
        }
    }

    private static void comparePhantomSpots(File file1, File file2) {
        JFileChooser chooser = new JFileChooser();
        if (file1 == null) {
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                return;

            file1 = chooser.getSelectedFile();
        }

        ImageProcessor ip1 = IJ.openImage(file1.getAbsolutePath()).getProcessor();

        if (file2 == null) {
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                return;

            file2 = chooser.getSelectedFile();
        }

        ImageProcessor ip2 = IJ.openImage(file2.getAbsolutePath()).getProcessor();

        System.out.println(String.format("%1$-12s%2$-40s", "Image 1", file1.getName()));
        System.out.println(String.format("%1$-12s%2$-40s", "Image 2", file2.getName()));
        System.out.println("");

        ImageStatistics st1, st2;
        ImageProcessor m1, m2;
        double rmsd, psnr;
        int hOff, vOff;

        System.out.println("<<< CSV output below <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        System.out.println("r,c,mean_1,mean_2,stdDev_1,stdDev_2,min_1,min_2,max_1,max_2,rmsd,psnr");

        for (int r = 4; r < 7; r++) {
            vOff = Phantoms.vMargin + r * (Phantoms.spotHeight + Phantoms.vGap);

            for (int c = 0; c < Phantoms.spotCols; c++) {
                hOff = Phantoms.hMargin + c * (Phantoms.spotWidth + Phantoms.hGap);

                ip1.setRoi(hOff, vOff, Phantoms.spotWidth, Phantoms.spotHeight);
                m1 = ip1.crop();
                st1 = m1.getStatistics();

                ip2.setRoi(hOff, vOff, Phantoms.spotWidth, Phantoms.spotHeight);
                m2 = ip2.crop();
                st2 = m2.getStatistics();

                rmsd = 0.0;
                for (int j = 0; j < m1.getPixelCount(); j++) {
                    rmsd += (m1.getf(j) - m2.getf(j)) * (m1.getf(j) - m2.getf(j));
                }
                rmsd /= m1.getPixelCount();

                psnr = 10 * Math.log10(32000 / rmsd);
                rmsd = Math.sqrt(rmsd);


                System.out.println(String.format("%1$d,%2$d,%3$.4f,%4$.4f,%5$.4f,%6$.4f,%7$.4f,%8$.4f,%9$.4f,%10$.4f,%11$.4f,%12$.4f", r, c, st1.mean, st2.mean, st1.stdDev, st2.stdDev, st1.min, st2.min, st1.max, st2.max, rmsd, psnr));
            }
        }

        System.out.println(">>> CSV output above >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    }

    private static void testNumberOfAxesVersusSNR() throws Exception {
        // Generate phantoms
        TiltSeries[][] phantoms = Phantoms.generateSeries(Phantoms.EXP_FUNCTION, Phantoms.SHARP_PEAK, 10);
        TiltSeries[] noisySeries = phantoms[1];
        TiltSeries[] series = phantoms[0];

        FilteredImage[] images = new FilteredImage[noisySeries.length];
        FilteredImage[] sigImages = new FilteredImage[1];
        FilteredImage[] bgdImages = new FilteredImage[images.length - 1];

        ImageStatistics recstats, oristats;
        ImageProcessor recip, oriip, mapip;
        MapperTask mt;
        PCA pca;
        double rmsd, psnr;

        System.out.println("<<< CSV output below <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        System.out.println("i,k,mean,stdDev,rmsd,psnr");

        // Iterate number of axes
        for (int k = noisySeries.length; k > 0; k--) {
            // Copy images and convert to FilteredImages
            for (int i = 0; i < noisySeries.length; i++) {
                images[i] = new FilteredImage(noisySeries[i].getProcessor(1).duplicate(), noisySeries[i].getEnergyShift(), noisySeries[i].getExposureTime(), noisySeries[i].getSlitWidth(), 0.0f);
            }

            // Decompose
            pca = MSANoiseFilter.decompose(images, null);

            for (int j = 0; j < pca.size(); j++) {
                pca.setSelected(pca.size() - j - 1, (j < k));
            }

            pca.printData();

            // Reconstitute
            MSANoiseFilter.reconstruct(images, null, pca);

            // Calculate and print statistics
            for (int i = 0; i < noisySeries.length; i++) {
                recip = images[i].getImage();
                oriip = series[i].getProcessor(1);
                recstats = recip.getStatistics();
                oristats = oriip.getStatistics();

                rmsd = 0.0;
                for (int j = 0; j < recip.getPixelCount(); j++) {
                    rmsd += (recip.getf(j) - oriip.getf(j)) * (recip.getf(j) - oriip.getf(j));
                }
                rmsd /= recip.getPixelCount();

                psnr = 10 * Math.log10(32000 / rmsd);
                rmsd = Math.sqrt(rmsd);

                System.out.println(String.format("%1$d,%2$d,%3$.4f,%4$.4f,%5$.4f,%6$.4f", (i + 1), k, recstats.mean, recstats.stdDev, rmsd, psnr));
            }
            System.out.println(">>> CSV output above >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

            // Map
            int j = 0;
            for (int i = 0; i < images.length; i++) {
                if (i == 2) {
                    sigImages[0] = images[2];
                } else {
                    bgdImages[j++] = images[i];
                }
            }
            mt = new MapperTask(sigImages, bgdImages, Model.Exponential, null);
            mapip = mt.call()[0];

            IJ.save(new ImagePlus("Map", mapip), "phmap-k" + k + ".tif");
        }
    }

    private static void jointHistogramToCSV(ImageProcessor ip1, ImageProcessor ip2, String filename) {
        int nbins = 256;
        int hdim = nbins + 1;
        int count;

        int[] hist = Metrics.histogram2D(ip1, ip2, nbins);

        ByteProcessor bp = new ByteProcessor(nbins, nbins);

        System.out.println("# 2D histogram (" + nbins + " x " + nbins + " bins)");
        for (int i = 0; i < nbins; i++) {
            for (int j = 0; j < nbins; j++) {
                count = hist[j * hdim + i];
                System.out.print(String.format("%1$d,", count));
                bp.setf(i, j, count);
            }
            System.out.print("\n");
        }

        IJ.save(new ImagePlus("JointHistogram", bp), filename);
    }

    public static void main(String[] args) throws Exception {

        ImageProcessor ip1 = IJ.openImage("/home/nick/Public/lena.tif").getProcessor();
        ImageProcessor ip2 = ip1.duplicate();

        jointHistogramToCSV(ip1, ip2, "/home/nick/Public/hist2d.tif");

        ip1.translate(5, 3);

        jointHistogramToCSV(ip1, ip2, "/home/nick/Public/hist2d-shifted.tif");

//        File path = new File("/Users/nick/Research/Institut Curie/results/PCA_Phantom_Tests/Exp_Sharp_Peak/");
//
//	compareImageStacks(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k1.tif"));
//	compareImageStacks(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k2.tif"));
//	compareImageStacks(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k3.tif"));
//	compareImageStacks(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k4.tif"));
//	compareImageStacks(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k5.tif"));
//	compareImageStacks(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k6.tif"));


//	comparePhantomSpots(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k1.tif"));
//	comparePhantomSpots(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k2.tif"));
//	comparePhantomSpots(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k3.tif"));
//	comparePhantomSpots(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k4.tif"));
//	comparePhantomSpots(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k5.tif"));
//	comparePhantomSpots(new File(path, "phmap-sharp.tif"), new File(path, "phmap-k6.tif"));

//	testNumberOfAxesVersusSNR();
    }
}
