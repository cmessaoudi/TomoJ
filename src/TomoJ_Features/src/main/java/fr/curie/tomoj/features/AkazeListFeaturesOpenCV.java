package fr.curie.tomoj.features;

import ij.process.ImageProcessor;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_features2d;

import java.awt.geom.Point2D;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.NORM_HAMMING;

/**
 * Created by cedric on 03/05/2016.
 */
public class AkazeListFeaturesOpenCV extends ListFeaturesOpenCV {
    opencv_features2d.AKAZE akaze;
    opencv_core.KeyPointVector kpointsakaze;
    opencv_core.Mat descriptorsakaze;
    ArrayList<Point2D> points;
    private int descriptor_type = opencv_features2d.AKAZE.DESCRIPTOR_MLDB;
    private int descriptor_size = 0;
    private int descriptor_channels = 3;
    private double threshold = 0.001;
    private int maxOctaves = 4;
    private int nbOctaveLayers = 4;
    private int diffusivityType = opencv_features2d.KAZE.DIFF_PM_G2;
    private double ransacPrecision = 2.0;

    public AkazeListFeaturesOpenCV() {
        descriptor_type = opencv_features2d.AKAZE.DESCRIPTOR_MLDB;
        descriptor_size = 0;
        descriptor_channels = 3;
        threshold = 0.001;
        maxOctaves = 4;
        nbOctaveLayers = 4;
        diffusivityType = opencv_features2d.KAZE.DIFF_PM_G2;

        akaze = opencv_features2d.AKAZE.create(descriptor_type, descriptor_size, descriptor_channels, (float) threshold, maxOctaves, nbOctaveLayers, diffusivityType);

    }

    public AkazeListFeaturesOpenCV(int descriptor_type, int descriptor_size, int descriptor_channels, double threshold, int maxOctaves, int nbOctaveLayers, int diffusivityType, double ransacPrecision) {
        this.descriptor_type = descriptor_type;
        this.descriptor_size = descriptor_size;
        this.descriptor_channels = descriptor_channels;
        this.threshold = threshold;
        this.maxOctaves = maxOctaves;
        this.nbOctaveLayers = nbOctaveLayers;
        this.diffusivityType = diffusivityType;
        this.ransacPrecision = ransacPrecision;
        akaze = opencv_features2d.AKAZE.create(descriptor_type, descriptor_size, descriptor_channels, (float) threshold, maxOctaves, nbOctaveLayers, diffusivityType);

    }

    public ListFeature createListFeature() {
        return new AkazeListFeaturesOpenCV(descriptor_type, descriptor_size, descriptor_channels, threshold, maxOctaves, nbOctaveLayers, diffusivityType, ransacPrecision);
    }

    public String getParametersAsString() {
        String params = "AKAZE\n" +
                "Nb Octaves: " + maxOctaves + "\tlayers: " + nbOctaveLayers + "\n" +
                "threshold: " + threshold + "\n" +
                "descriptor type";
        switch (descriptor_type) {
            case opencv_features2d.AKAZE.DESCRIPTOR_KAZE:
                params += "AKAZE";
                break;
            case opencv_features2d.AKAZE.DESCRIPTOR_KAZE_UPRIGHT:
                params += "AKAZE Upright";
                break;
            case opencv_features2d.AKAZE.DESCRIPTOR_MLDB:
                params += "MLDB";
                break;
            case opencv_features2d.AKAZE.DESCRIPTOR_MLDB_UPRIGHT:
                params += "MLDB Upright";
                break;

        }
        params += "\tsize: " + descriptor_size + "\tchannels: " + descriptor_channels + "\n";

        switch (diffusivityType) {
            case opencv_features2d.KAZE.DIFF_PM_G1:
                params += "DIFF_PM_G1";
                break;
            case opencv_features2d.KAZE.DIFF_PM_G2:
                params += "DIFF_PM_G2";
                break;
            case opencv_features2d.KAZE.DIFF_WEICKERT:
                params += "DIFF_WEICKERT";
                break;
            case opencv_features2d.KAZE.DIFF_CHARBONNIER:
                params += "DIFF_CHARBONNIER";
                break;
        }
        params += "\nhomography ransac precision: " + ransacPrecision;
        return params;
    }

    public void setParameters(int descriptor_type, int descriptor_size, int descriptor_channels, double threshold, int maxOctaves, int nbOctaveLayers, int diffusivityType, double ransacPrecision) {
        this.descriptor_type = descriptor_type;
        this.descriptor_size = descriptor_size;
        this.descriptor_channels = descriptor_channels;
        this.threshold = threshold;
        this.maxOctaves = maxOctaves;
        this.nbOctaveLayers = nbOctaveLayers;
        this.diffusivityType = diffusivityType;
        this.ransacPrecision = ransacPrecision;
        akaze.setDescriptorType(descriptor_type);
        akaze.setDescriptorSize(descriptor_size);
        akaze.setDescriptorChannels(descriptor_channels);
        akaze.setThreshold(threshold);
        akaze.setNOctaves(maxOctaves);
        akaze.setNOctaveLayers(nbOctaveLayers);
        akaze.setDiffusivity(diffusivityType);
    }

    protected void setKpoints(opencv_core.KeyPointVector kpoints) {
        this.kpointsakaze = kpoints;
        points = new ArrayList<Point2D>((int) kpointsakaze.size());
        for (int i = 0; i < kpointsakaze.size(); i++) {
            float x = kpointsakaze.get(i).pt().x();
            float y = kpointsakaze.get(i).pt().y();
            Point2D p1 = new Point2D.Double(x, y);
            points.add(p1);
        }
    }

    public Object detect(ImageProcessor ip) {
        ImageProcessor ip1 = ip.convertToByte(true);
        kpointsakaze = new opencv_core.KeyPointVector();
        Pointer pixs = new BytePointer(ByteBuffer.wrap((byte[]) ip1.getPixels()));
        opencv_core.Mat data = new opencv_core.Mat(ip1.getWidth(), ip1.getHeight(), CV_8U, pixs);
        akaze.detect(data, kpointsakaze);
        setKpoints(kpointsakaze);
        return kpointsakaze;
    }

    public void compute(ImageProcessor ip, Object kpoints) {
        setKpoints((opencv_core.KeyPointVector) kpoints);
        descriptorsakaze = new opencv_core.Mat();
        ImageProcessor ip1 = ip.convertToByte(true);
        Pointer pixs = new BytePointer(ByteBuffer.wrap((byte[]) ip1.getPixels()));
        opencv_core.Mat data = new opencv_core.Mat(ip1.getWidth(), ip1.getHeight(), CV_8U, pixs);
        akaze.compute(data, kpointsakaze, descriptorsakaze);
    }

    public void detectAndCompute(ImageProcessor ip) {
        //ip.resetMinAndMax();
        ImageProcessor ip1 = ip.convertToByte(true);
        kpointsakaze = new opencv_core.KeyPointVector();
        descriptorsakaze = new opencv_core.Mat();
        Pointer pixs = new BytePointer(ByteBuffer.wrap((byte[]) ip1.getPixels()));
        opencv_core.Mat data = new opencv_core.Mat(ip1.getWidth(), ip1.getHeight(), CV_8U, pixs);
        akaze.detectAndCompute(data, new opencv_core.Mat(), kpointsakaze, descriptorsakaze, false);

        setKpoints(kpointsakaze);
        System.out.println("keypoints:" + kpointsakaze.size() + " akazedescriptor:" + descriptorsakaze.rows() + ", " + descriptorsakaze.cols());

    }

    public HashMap<Point2D, Point2D> matchWith(ListFeature other, boolean validateWithHomography) {
        //if(validateWithHomography) return matchWithOld(other,validateWithHomography);
        if (this.kpointsakaze.size() == 0 || ((AkazeListFeaturesOpenCV) other).kpointsakaze.size() == 0) {
            System.out.println("no features : no matching possible!!!");
            return new HashMap<Point2D, Point2D>();
        }
        //opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(NORM_HAMMING,true);
        return matchingWithHomography(this.kpointsakaze, this.descriptorsakaze, this.points, ((AkazeListFeaturesOpenCV) other).kpointsakaze, ((AkazeListFeaturesOpenCV) other).descriptorsakaze, ((AkazeListFeaturesOpenCV) other).points, NORM_HAMMING, ransacPrecision, 5);
    }


    public ArrayList<Point2D> getFeatures() {
        return points;
    }
}
