package fr.curie.eftemtomoj;/*
 * Copyright 2010 Nick Aschman.
 */

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.awt.*;
import java.io.File;
import java.util.Arrays;

/**
 * @author Nick Aschman
 */
public class EftemDataset {

    private final TiltSeries[] tiltSeries;
    private final int width;
    private final int height;
    private final int windowCount;
    private int mappingWindowCount;    // hyperstack channels
    private final int tiltCount;        // hyperstack slices
    private final float[] tiltAngles;
    private final float[] energyShifts;
    private final float[] exposureTimes;
    private final float[] slitWidths;
    private final boolean[] usedForMapping;
    private Mapper.Model lawUsed = Mapper.Model.Power;

    private Rectangle[] masks;
    private int previewTiltIndex;
    private double zeroOffset = 0;

    public EftemDataset(TiltSeries[] series) throws InvalidInputException {

        // Validate data
        validateDataset(series);

        // Define dimensions
        width = series[0].getWidth();
        height = series[0].getHeight();
        windowCount = series.length;
        tiltCount = series[0].getSize();

        energyShifts = new float[windowCount];
        exposureTimes = new float[windowCount];
        slitWidths = new float[windowCount];
        usedForMapping = new boolean[windowCount];

        tiltAngles = new float[tiltCount];
        Arrays.fill(tiltAngles, 0.0f);
        previewTiltIndex = (tiltCount == 1) ? 0 : Math.round(tiltCount / 2.0f);
        masks = new Rectangle[tiltCount];

        // Set acquisition parameters and count mapping windows
        int mappingCount = 0;
        for (int i = 0; i < windowCount; i++) {
            energyShifts[i] = series[i].getEnergyShift();
            exposureTimes[i] = series[i].getExposureTime();
            slitWidths[i] = series[i].getSlitWidth();
            usedForMapping[i] = series[i].isUsedForMapping();

            if (usedForMapping[i]) mappingCount++;
        }

        mappingWindowCount = mappingCount;
        tiltSeries = series;
        if (series.length == 2) lawUsed = Mapper.Model.Ratio;

        // Create output folder
//	path = new File("EFTEMTomoJ_out_" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
//	path.mkdirs();
        File path = new File(System.getProperty("user.dir"));
    }

    public boolean save(String desc) {
//	IJ.save(createHyperStack(), new File(path, "stack_" + desc + ".tif").getAbsolutePath());
        return true;
    }

    public boolean saveImageAs(ImagePlus image, String fileName) {
//	IJ.save(image, new File(path, fileName).getAbsolutePath());
        return true;
    }

//    public static eftemtomoj.EftemDataset load(String fileName) {
//	// Read hyperstack
//	ImagePlus hyperStack = IJ.openImage(fileName);
//
//	int nchannels = hyperStack.getNChannels();
//	int nslices = hyperStack.getNSlices();
//
//	if(nchannels == 0 || nslices == 0) {
//	    throw new Exception("Invalid ");
//	}
//
//	// Process into TiltSeries array
//	TiltSeries[] tiltSeries = new TiltSeries[nchannels];
//	for(int c = 0; c < nchannels; c++) {
//
//	}
//    }

    public ImagePlus createHyperStack() {
        ImageStack hyperStack = new ImageStack(getWidth(), getHeight());
        for (int i = 0; i < tiltCount; i++) {
            for (int j = 0; j < windowCount; j++) {
                hyperStack.addSlice(String.format("%1$.2f eV / %2$.2f deg", energyShifts[j], tiltAngles[i]), tiltSeries[j].getProcessor(i + 1));
            }
        }

        ImagePlus image = new ImagePlus("EFTEMTomoJ Dataset", hyperStack);
        image.setDimensions(windowCount, tiltCount, 1);
        image.setOpenAsHyperStack(true);

        return image;
    }

    public void setTiltAngles(float[] tilts) throws InvalidInputException {
        if (tilts.length == tiltAngles.length) {
            System.arraycopy(tilts, 0, tiltAngles, 0, tiltAngles.length);
        } else {
            throw new InvalidInputException("Incorrect number of tilt angles. " + tiltCount + " required.");
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getWindowCount() {
        return windowCount;
    }

    public int getMappingWindowCount() {
        return mappingWindowCount;
    }

    public int getTiltCount() {
        return tiltCount;
    }

    public float getTiltAngle(int i) {
        if (i < tiltCount)
            return tiltAngles[i];
        else
            return tiltAngles[tiltCount - 1];
    }

    public float[] getTiltAngles() {
        return tiltAngles;
    }


    public float getEnergyShift(int i) {
        return energyShifts[i];
    }

    public float getExposureTime(int i) {
        return exposureTimes[i];
    }

    public float getSlitWidth(int i) {
        return slitWidths[i];
    }

    public boolean isUsedForMapping(int i) {
        return usedForMapping[i];
    }

    public boolean isSignal(int i) {
        return tiltSeries[i].isSignal();
    }

    public int getPreviewTiltIndex() {
        return previewTiltIndex;
    }

    public void setPreviewTiltIndex(int previewTiltIndex) {
        if (tiltCount > 1)
            this.previewTiltIndex = previewTiltIndex;
    }

    public boolean hasMask(int i) {
        return (masks[i] != null);
    }

    public Rectangle getMask(int i) {
        return masks[i];
    }

    public Rectangle getMask() {
        return getMask(previewTiltIndex);
    }

    public void setMask(int i, Rectangle mask) {
        masks[i] = mask;
    }

    public void resetMasks() {
        for (int i = 0; i < masks.length; i++) {
            masks[i] = null;
        }
    }

    public void tiltSeriesUpdated() {
        //windowCount=tiltSeries.length;
        int mappingCount = 0;
        for (int i = 0; i < windowCount; i++) {
            usedForMapping[i] = tiltSeries[i].isUsedForMapping();
            if (usedForMapping[i]) mappingCount++;
        }

        mappingWindowCount = mappingCount;
    }

    public FilteredImage[] getMappingImages(int tiltIndex) {
        FilteredImage[] images = new FilteredImage[mappingWindowCount];

        int k = 0;
        for (int i = 0; i < windowCount; i++) {
            if (usedForMapping[i]) {
                images[k++] = getImage(tiltIndex, i);
            }
        }

        return images;
    }

    public FilteredImage[] getMappingImages() {
        return getMappingImages(previewTiltIndex);
    }

    public FilteredImage[] getImages(int tiltIndex) {
        FilteredImage[] images = new FilteredImage[windowCount];

        for (int i = 0; i < windowCount; i++) {
            images[i] = getImage(tiltIndex, i);
        }

        return images;
    }

    public FilteredImage[] getImages() {
        return getImages(previewTiltIndex);
    }

    public FilteredImage getImage(int tiltIndex, int windowIndex) {
        FilteredImage fi = new FilteredImage(tiltSeries[windowIndex].getProcessor(tiltIndex + 1).duplicate(), energyShifts[windowIndex], exposureTimes[windowIndex], slitWidths[windowIndex], tiltAngles[tiltIndex], usedForMapping[windowIndex]);
        fi.setSignal(tiltSeries[windowIndex].isSignal());
        fi.setDisabled(tiltSeries[windowIndex].isDisabled());
        return fi;
    }

    public TiltSeries getTiltSeries(int windowIndex) {
        return tiltSeries[windowIndex];
    }

    public TiltSeries[] getTiltSeries() {
        return tiltSeries;
    }

    public void setLaw(Mapper.Model law) {
        lawUsed = law;
    }

    public Mapper.Model getLaw() {
        return lawUsed;
    }


    /**
     * Validates data set by throwing exceptions
     * 1.   Check all images have the same width & height
     * 2.   Check all series have the same size and the provided tilt angles correspond
     * 3.   Check that there are a minimum of one signal and two background windows
     * 4.   Check that the energy shift and exposure time parameters are set for each series
     *
     * @param series the data to check
     * @throws InvalidInputException if one one description is not correct throw an exception
     */
    private void validateDataset(TiltSeries[] series) throws InvalidInputException {

        checkTiltSeries(series);

        int mappingCount = 0;

        for (TiltSeries sery : series) {
            if (sery.isUsedForMapping()) {
                mappingCount++;
            }

            if (sery.isUsedForMapping()) {
                if (sery.getEnergyShift() < 0.0f) {
                    throw new InvalidInputException("Missing energy shift value(s).");
                }

                if (sery.getExposureTime() <= 0.0f) {
                    throw new InvalidInputException("Missing exposure time value(s).");
                }

                if (sery.getSlitWidth() <= 0.0f) {
                    throw new InvalidInputException("Missing slit width value(s).");
                }
            }
        }

        if (mappingCount < 2) {
            throw new InvalidInputException("At least 2 windows are required for mapping.");
        }
    }

    public static void checkTiltSeries(TiltSeries[] series) throws InvalidInputException {
        int w, h, s;
        boolean first;

        w = 0;
        h = 0;
        s = 0;
        first = true;

        for (TiltSeries sery : series) {

            if (first) {
                w = sery.getWidth();
                h = sery.getHeight();
                s = sery.getSize();
                first = false;
            } else {
                if (sery.getWidth() != w || sery.getHeight() != h) {
                    throw new InvalidInputException("Image dimensions do not match (" + sery.getTitle() + ").");
                }

                if (sery.getSize() != s) {
                    throw new InvalidInputException("Number of tilted images do not match (" + sery.getTitle() + ").");
                }
            }
        }
    }

    public static class InvalidInputException extends Exception {
        InvalidInputException(String s) {
            super(s);
        }
    }

    public void correctNegativeValues() {
        double min = 0;
        for (int i = 0; i < tiltCount; i++) {
            for (int j = 0; j < windowCount; j++) {
                ImageProcessor ip = tiltSeries[j].getProcessor(i + 1);
                ip.resetMinAndMax();
                min = Math.min(ip.getMin(), min);
                //System.out.println("tilt "+i+" nrj "+j+" minVal="+ip.getMin()+" minglobal="+min);
            }
        }
        for (int i = 0; i < tiltCount; i++) {
            if (min < 0) {
                //System.out.println("tilt "+i+" min="+min+" now correcting");
                for (int j = 0; j < windowCount; j++) {
                    tiltSeries[j].getProcessor(i + 1).add(-min);
                }
            }
        }
        zeroOffset = Math.abs(min);
    }

    public double getZeroOffset() {
        return zeroOffset;
    }

}
