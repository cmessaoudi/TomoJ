package fr.curie.tomoj.features;

import org.bytedeco.javacpp.opencv_core;

import java.awt.geom.Point2D;

public class FeaturePoint extends Point2D.Double {

    private opencv_core.KeyPoint keypoint;
    private opencv_core.Mat descriptors;

    public FeaturePoint() {

    }

    public FeaturePoint(opencv_core.KeyPoint keypoint, opencv_core.Mat descriptors, double x, double y) {
        super(x, y);
        this.keypoint = keypoint;
        this.descriptors = descriptors;
    }

    public opencv_core.KeyPoint getKeypoint() {
        return keypoint;
    }

    public opencv_core.Mat getDescriptors() {
        return descriptors;
    }

}
