/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

import ij.ImageStack;

/**
 * Represents a tomographic tilt series taken at a given energy window.
 *
 * @author Nick Aschman
 */
public class TiltSeries extends ImageStack {

    private float energyShift = 0.0f;
    private float exposureTime = 1.0f;
    private float slitWidth = 10.0f;
    private String comment = "";
    private String title = "";
    private boolean useForMapping = true;
    private boolean signal = false;
    private boolean disable = false;

    public TiltSeries(int width, int height) {
        super(width, height);
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public float getEnergyShift() {
        return energyShift;
    }

    public void setEnergyShift(float energyShift) {
        this.energyShift = energyShift;
    }

    public float getExposureTime() {
        return exposureTime;
    }

    public void setExposureTime(float exposureTime) {
        this.exposureTime = exposureTime;
    }

    public boolean isUsedForMapping() {
        return useForMapping;
    }

    public void setUseForMapping(boolean useForMapping) {
        this.useForMapping = useForMapping;
    }

    public boolean isSignal() {
        return (!disable) && signal;
    }

    public void setSignal(boolean isSignal) {
        signal = isSignal;

    }

    public void setDisabled(boolean isDisable) {
        disable = isDisable;
    }

    public boolean isDisabled() {
        return disable;
    }

    public float getSlitWidth() {
        return slitWidth;
    }

    public void setSlitWidth(float slitWidth) {
        this.slitWidth = slitWidth;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
