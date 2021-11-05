package fr.curie.eftemtomoj;/*
 * Copyright 2010 Nick Aschman.
 */


import fr.curie.eftemtomoj.utils.SubImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * Wrapper class for an energy-filtered image taken at a specific tilt angle and energy window.
 *
 * @author Nick Aschman
 */
public class FilteredImage {

    private final float energyShift;
    private final float exposureTime;
    private final float slitWidth;
    private final float tiltAngle;
    private final boolean useForMapping;
    private ImageProcessor image;

    private double translationX = 0.0;
    private double translationY = 0.0;
    private double rotation = 0.0;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private boolean signal = false;
    private boolean disabled = false;

    public FilteredImage(ImageProcessor image, float energyShift, float exposureTime, float slitWidth, float tiltAngle) {
        this(image, energyShift, exposureTime, slitWidth, tiltAngle, true);
    }

    public FilteredImage(ImageProcessor image, float energyShift, float exposureTime, float slitWidth, float tiltAngle, boolean use) {
        this.energyShift = energyShift;
        this.exposureTime = exposureTime;
        this.slitWidth = slitWidth;
        this.tiltAngle = tiltAngle;
        this.image = image;
        this.useForMapping = use;
    }

    public float getEnergyShift() {
        return energyShift;
    }

    public float getExposureTime() {
        return exposureTime;
    }

    public ImageProcessor getImage() {
        return image;
    }

    public void setImage(ImageProcessor ip) {
        image = ip;
    }

    public ImageProcessor getImageForAlignment(double radius) {
        if (radius > 0) {
            ImageProcessor ip = image.duplicate();
            if (scaleX != 1.0 || scaleY != 1.0) {
                ImageRegistration.Transform T = new ImageRegistration.Transform(0, 0, 0, scaleX, scaleY);
                applyTransform(ip, T);
            }
            ImagePlus imp = new ImagePlus("ip", ip);
            IJ.run(imp, "Gaussian Blur...", "sigma=" + radius);
            IJ.run(imp, "Variance...", "radius=" + radius);
            ip.setRoi(image.getRoi());
            return ip;
        }
        return image;
    }

    public float getSlitWidth() {
        return slitWidth;
    }

    public float getTiltAngle() {
        return tiltAngle;
    }

    public boolean isUsedForMapping() {
        return useForMapping;
    }

    public void setDisabled(boolean isDisabled) {
        disabled = isDisabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public boolean isSignal() {
        return (!disabled) && signal;
    }

    public void setSignal(boolean value) {
        signal = value;
    }

    public void setScale(double scaleX, double scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public double getScaleX() {
        return scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public ImageRegistration.Transform getTransform() {
        ImageRegistration.Transform T = new ImageRegistration.Transform(translationX, translationY, rotation);
        T.setScale(scaleX, scaleY);
        return T;
    }

    public void applyTransform(ImageRegistration.Transform t) {
        applyTransform(image, t);
    }

    public ImageProcessor applyTransform(ImageProcessor image, ImageRegistration.Transform t) {
        if (t == null)
            return image;

        // Apply

//        AffineTransform Tr = new AffineTransform();
//        Tr.scale(t.getScaleX(), t.getScaleY());
//        Tr.rotate(Math.toRadians(t.getRotate()));
//        Tr.translate(t.getTranslateX(), t.getTranslateY());
        AffineTransform Tr = t.getAffineTransform();
        try {
            Tr = Tr.createInverse();
        } catch (Exception e) {
            e.printStackTrace();
        }
        float[] pixs = (float[]) image.getPixels();
        pixs = SubImage.getSubImagePixels(pixs, Tr, image.getStatistics(), image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight(), new Point2D.Double(0, 0), false, true);
        image.setPixels(pixs);

        //image.translate(t.getTranslateX(), t.getTranslateY());
//	image.rotate(transform.getRotate());

        // Store
        translationX += t.getTranslateX();
        translationY += t.getTranslateY();
//	rotation += t.getRotate();
        return image;
    }

    public void resetTransform() {
        image.translate(-translationX, -translationY);
//	image.rotate(-1 * rotation);

        translationX = 0;
        translationY = 0;
        rotation = 0;

    }

    public double calculateMean(Roi roi) {
        double sum;
        int dim, offx, offy;

        if (roi == null || !roi.isArea()) {
            dim = image.getWidth() * image.getHeight();
            sum = 0.0;

            for (int j = 0; j < dim; j++) {
                sum += image.getf(j);
            }
        } else {
            dim = 0;
            sum = 0.0;

            offx = roi.getBounds().x;
            offy = roi.getBounds().y;

            for (int x = offx; x < offx + roi.getBounds().width; x++) {
                for (int y = offy; y < offy + roi.getBounds().height; y++) {
                    if (roi.contains(x, y)) {
                        sum += image.getf(x, y);
                        dim++;
                    }
                }
            }
        }

        return sum / (double) dim;
    }

    public double getMinValue() {
        return image.getMin();
    }

    public FilteredImage getCopy() {
        FilteredImage fi = new FilteredImage(this.image.duplicate(), this.energyShift, this.exposureTime, this.slitWidth, this.tiltAngle, this.useForMapping);
        fi.translationX = this.translationX;
        fi.translationY = this.translationY;
        fi.rotation = this.rotation;
        fi.signal = this.signal;
        return fi;
    }
}

