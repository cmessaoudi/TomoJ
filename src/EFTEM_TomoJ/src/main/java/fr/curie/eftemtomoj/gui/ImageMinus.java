/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.gui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

/**
 * @author Nick Aschman
 */
public class ImageMinus extends ImagePlus {

    boolean allowedToClose = false;

    public ImageMinus(String title, ImageStack stack) {
        super(title, stack);
    }

    public ImageMinus(String title, ImageProcessor ip) {
        super(title, ip);
    }

    @Override
    public void close() {
        if (allowedToClose) {
            super.close();
        }
    }

    public boolean isAllowedToClose() {
        return allowedToClose;
    }

    public void setAllowedToClose(boolean allowedToClose) {
        this.allowedToClose = allowedToClose;
    }
}
