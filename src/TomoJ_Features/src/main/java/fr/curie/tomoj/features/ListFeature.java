package fr.curie.tomoj.features;

import ij.process.ImageProcessor;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by cmessaoudi on 22/01/2018.
 */
public interface ListFeature {
    public Object detect(ImageProcessor ip);
    public void compute(ImageProcessor ip, Object kpoints);
    public void detectAndCompute(ImageProcessor ip);
    public HashMap<Point2D,Point2D> matchWith(ListFeature other, boolean validateWithHomography);
    public ArrayList<Point2D> getFeatures();
    public ListFeature createListFeature();
    public String getParametersAsString();
}
