/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

import ij.process.ImageProcessor;

import java.util.Arrays;

/**
 * @author Nick Aschman
 */
public class Metrics {

    public static enum Metric {
        NCC, NMI, MS
    }

    /**
     * Normalised cross-correlation.
     *
     * @param fip
     * @param mip
     * @return
     */
    public static double ncc(ImageProcessor fip, ImageProcessor mip) {
        int width, height;
        double sum, fval, mval, fmean, mmean, fdev, mdev;

        width = fip.getWidth();
        height = fip.getHeight();

        // Calculate image means
        fmean = 0.0;
        mmean = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                fmean += fip.getf(x, y);
                mmean += mip.getf(x, y);
            }
        }
        fmean /= (height * width);
        mmean /= (height * width);

        // Calculate centered cross correlation and image deviations from mean
        sum = 0.0;
        fdev = 0.0;
        mdev = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                fval = fip.getf(x, y) - fmean;
                mval = mip.getf(x, y) - mmean;

                sum += fval * mval;
                fdev += fval * fval;
                mdev += mval * mval;
            }
        }

        // Anticorrelation
        sum = Math.abs(sum);

        // If sum == 0, return 0, otherwise we get NaNs
        return Double.isInfinite(1 / sum) ? 0.0 : sum / Math.sqrt(fdev * mdev);
    }

    /**
     * Normalised histogram-based mutual information.
     *
     * @param fip
     * @param mip
     * @param nbins
     * @return
     * @throws Exception
     */
    public static double nmi(ImageProcessor fip, ImageProcessor mip, int nbins) throws Exception {
        double fH, mH, jH, fP, mP, jP, log2;
        int[] hist;
        int dim, hdim;

        dim = fip.getWidth() * fip.getHeight();
        log2 = Math.log(2);

        hist = histogram2D(fip, mip, nbins);
        hdim = nbins + 1;

        // Calculate entropies and mutual information
        fH = 0.0;
        mH = 0.0;
        jH = 0.0;
        for (int i = 0; i < nbins; i++) {
            fP = hist[nbins * hdim + i] / (double) dim;
            fP = (fP > 0) ? (fP * Math.log(fP) / log2) : 0;
            fH -= fP;

            mP = hist[i * hdim + nbins] / (double) dim;
            mP = (mP > 0) ? (mP * Math.log(mP) / log2) : 0;
            mH -= mP;

            for (int j = 0; j < nbins; j++) {
                jP = (hist[j * hdim + i] / (double) dim);
                jP = (jP > 0) ? (jP * Math.log(jP) / log2) : 0;
                jH -= jP;
            }
        }

        return (fH + mH) / jH;
    }

    /**
     * Mean squared deviation.
     *
     * @param fip
     * @param mip
     * @return
     */
    public static double ms(ImageProcessor fip, ImageProcessor mip) {
        double sum = 0.0;
        for (int i = 0; i < fip.getPixelCount(); i++) {
            sum += (fip.getf(i) - mip.getf(i)) * (fip.getf(i) - mip.getf(i));
        }
        sum /= fip.getPixelCount();

        return sum;
    }

    /**
     * Computes joint and marginal histograms of two images.
     *
     * @param ip1
     * @param ip2
     * @param nbins
     * @return
     */
    public static int[] histogram2D(ImageProcessor ip1, ImageProcessor ip2, int nbins) {
        int[] h;
        double fMin, fMax, fWidth, mMin, mMax, mWidth, value;
        int fBin, mBin, dim, hdim;

        dim = ip1.getWidth() * ip1.getHeight();
        hdim = nbins + 1;
        h = new int[hdim * hdim];
        Arrays.fill(h, 0);

        // Find ranges
        fMin = Float.NaN;
        fMax = Float.NaN;
        mMin = Float.NaN;
        mMax = Float.NaN;
        for (int i = 0; i < dim; i++) {
            value = ip1.getf(i);
            if (Double.isNaN(fMax) || value > fMax) fMax = value;
            if (Double.isNaN(fMin) || value < fMin) fMin = value;

            value = ip2.getf(i);
            if (Double.isNaN(mMax) || value > mMax) mMax = value;
            if (Double.isNaN(mMin) || value < mMin) mMin = value;
        }

        fWidth = Math.abs(fMax - fMin) / (double) nbins;
        mWidth = Math.abs(mMax - mMin) / (double) nbins;

        // Compute histograms
        for (int i = 0; i < dim; i++) {
            fBin = (int) (Math.abs(ip1.getf(i) - fMin) / fWidth);
            mBin = (int) (Math.abs(ip2.getf(i) - mMin) / mWidth);

            // Add maximum value to last bin
            if (fBin >= nbins) fBin = nbins - 1;
            if (mBin >= nbins) mBin = nbins - 1;

            h[nbins * hdim + fBin]++;
            h[mBin * hdim + nbins]++;
            h[mBin * hdim + fBin]++;
        }

        return h;
    }
}
