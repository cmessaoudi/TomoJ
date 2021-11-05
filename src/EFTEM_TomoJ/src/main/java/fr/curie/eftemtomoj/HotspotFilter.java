/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Nick Aschman
 */
public class HotspotFilter {

    private final ImageProcessor image;
    private final List<Integer[]> list;

    private HotspotFilter(ImageProcessor image, List<Integer[]> list) {
        this.image = image;
        this.list = list;
    }

    public int getHotspotCount() {
        return list.size();
    }

    public Overlay getImageOverlay() {
        Overlay overlay = new Overlay();
        Integer[] item;
        for (int i = 0; i < list.size(); i++) {
            item = list.get(i);
            overlay.add(new Roi(item[0] - item[2], item[1] - item[2], 2 * item[2] + 1, 2 * item[2] + 1));
        }
        return overlay;
    }

    public static HotspotFilter detectHot(ImageProcessor ip) {
        int[] hist;
        int nbins, bin, zeroes;
        double value, threshold, min, max, binwidth;
        List<Integer[]> list;

        list = new ArrayList<Integer[]>();

        // Calculate histogram and set intensity threshold
        nbins = 256;
        hist = new int[nbins];
        Arrays.fill(hist, 0);

        min = Double.NaN;
        max = Double.NaN;
        for (int j = 0; j < ip.getPixelCount(); j++) {
            value = ip.getf(j);
            if (Double.isNaN(min) || value < min) min = value;
            if (Double.isNaN(max) || value > max) max = value;
        }
        binwidth = Math.abs(max - min) / (double) nbins;

        for (int j = 0; j < ip.getPixelCount(); j++) {
            bin = (int) (Math.abs(ip.getf(j) - min) / binwidth);
            if (bin >= nbins) bin = nbins - 1;

            hist[bin]++;
        }

        threshold = max;
        zeroes = 0;
        bin = 0;
        while (bin < nbins) {
            if (hist[bin] == 0) {
                zeroes++;

                if (zeroes == 3) {
                    threshold = min + bin * binwidth;
                    break;
                }
            }
            bin++;
        }

        if (threshold < max) {
            // Detect hotspots and store in list
            for (int x = 0; x < ip.getWidth(); x++) {
                for (int y = 0; y < ip.getHeight(); y++) {
                    value = ip.getf(x, y);

                    if (value > threshold) {
                        //TODO: Calculate spot radius (r=1 results in 3x3 kernel)
                        list.add(new Integer[]{x, y, 1});
                    }
                }
            }
        }

        return new HotspotFilter(ip, list);
    }

    public void apply() {
        Iterator<Integer[]> it = list.iterator();
        Integer[] item;
        int spotX, spotY, spotR;
        double sum;

        while (it.hasNext()) {
            item = it.next();
            spotX = item[0];
            spotY = item[1];
            spotR = item[2];

            if (spotR < 1) spotR = 1;

            sum = 0.0;
            for (int x = spotX - spotR; x <= spotX + spotR; x++) {
                for (int y = spotY - spotR; y <= spotY + spotR; y++) {
                    if (x != spotX && y != spotY) {
                        sum += image.getf(x, y);
                    }
                }
            }
            sum /= (4 * (spotR * spotR + spotR));

            image.setf(spotX, spotY, (float) sum);
        }
    }
}
