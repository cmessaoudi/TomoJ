package fr.curie.tomoj.features;

import ij.process.ImageProcessor;
import org.bytedeco.javacpp.*;

import java.awt.geom.Point2D;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.KeyPointVector;
import static org.bytedeco.javacpp.opencv_core.NORM_L2;

/**
 * Created by cedric on 03/05/2016.
 */
public class SIFTListFeaturesOpenCV extends ListFeaturesOpenCV {
    private opencv_xfeatures2d.SIFT sift;
    private opencv_core.KeyPointVector kpointssift;
    private opencv_core.Mat descriptorssift;
    private ArrayList<Point2D> points;
    private int nfeatures = 0;
    private int nOctaveLayers = 3;
    private double contrastThreshold = 0.04;
    private double edgeThresholdSIFT = 10;
    private double sigma = 1.6;
    private boolean crossValidation = true;
    private double siftMatchThreshold = 0.7;
    private double ransacPrecision = 2.0;

    public SIFTListFeaturesOpenCV() {
        //SIFT
        sift = opencv_xfeatures2d.SIFT.create(nfeatures, nOctaveLayers, contrastThreshold, edgeThresholdSIFT, sigma);

    }

    public SIFTListFeaturesOpenCV(int nfeatures, int nOctaveLayers, double contrastThreshold, double edgeThresholdSIFT, double sigma, boolean crossValidation, double siftMatchThreshold, double ransacPrecision) {
        this.nfeatures = nfeatures;
        this.nOctaveLayers = nOctaveLayers;
        this.contrastThreshold = contrastThreshold;
        this.edgeThresholdSIFT = edgeThresholdSIFT;
        this.sigma = sigma;
        this.ransacPrecision = ransacPrecision;
        this.crossValidation = crossValidation;
        this.siftMatchThreshold = siftMatchThreshold;
        sift = opencv_xfeatures2d.SIFT.create(nfeatures, nOctaveLayers, contrastThreshold, edgeThresholdSIFT, sigma);
    }

    public ListFeature createListFeature() {
        return new SIFTListFeaturesOpenCV(nfeatures, nOctaveLayers, contrastThreshold, edgeThresholdSIFT, sigma, crossValidation, siftMatchThreshold, ransacPrecision);
    }

    public void setParameters(int nfeatures, int nOctaveLayers, double contrastThreshold, double edgeThresholdSIFT, double sigma, boolean crossValidation, double siftMatchThreshold, double ransacPrecision) {
        this.nfeatures = nfeatures;
        this.nOctaveLayers = nOctaveLayers;
        this.contrastThreshold = contrastThreshold;
        this.edgeThresholdSIFT = edgeThresholdSIFT;
        this.sigma = sigma;
        this.crossValidation = crossValidation;
        this.siftMatchThreshold = siftMatchThreshold;
        this.ransacPrecision = ransacPrecision;
        sift = opencv_xfeatures2d.SIFT.create(nfeatures, nOctaveLayers, contrastThreshold, edgeThresholdSIFT, sigma);

    }

    public String getParametersAsString() {
        String params = "SIFT\n" + "Nb of features: " + nfeatures + "\n" +
                "Nb Octaves: " + nOctaveLayers + "\tsigma: " + sigma + "\n" +
                "thresholds --> contrast:" + contrastThreshold + "\tedge: " + edgeThresholdSIFT + "\n";
        params += (crossValidation) ? "cross validation " : "match threshold: " + siftMatchThreshold;
        params += "\nhomography ransac precision: " + ransacPrecision;
        return params;
    }

    protected void setKpoints(opencv_core.KeyPointVector kpoints) {
        this.kpointssift = kpoints;
        points = new ArrayList<Point2D>((int) kpointssift.size());
        for (int i = 0; i < kpointssift.size(); i++) {
            float x = kpointssift.get(i).pt().x();
            float y = kpointssift.get(i).pt().y();
            Point2D p1 = new Point2D.Double(x, y);
            points.add(p1);
        }
    }

    public Object detect(ImageProcessor ip) {
        ImageProcessor ip1 = ip.convertToByte(true);
        kpointssift = new opencv_core.KeyPointVector();
        Pointer pixs = new BytePointer(ByteBuffer.wrap((byte[]) ip1.getPixels()));
        opencv_core.Mat data = new opencv_core.Mat(ip1.getWidth(), ip1.getHeight(), CV_8U, pixs);
        sift.detect(data, kpointssift);
        setKpoints(kpointssift);
        return kpointssift;
    }

    public void compute(ImageProcessor ip, Object kpoints) {
        setKpoints((KeyPointVector) kpoints);
        descriptorssift = new opencv_core.Mat();
        ImageProcessor ip1 = ip.convertToByte(true);
        Pointer pixs = new BytePointer(ByteBuffer.wrap((byte[]) ip1.getPixels()));
        opencv_core.Mat data = new opencv_core.Mat(ip1.getWidth(), ip1.getHeight(), CV_8U, pixs);
        sift.compute(data, kpointssift, descriptorssift);
    }

    public void detectAndCompute(ImageProcessor ip) {
        //ip.resetMinAndMax();
        ImageProcessor ip1 = ip.convertToByte(true);
        kpointssift = new opencv_core.KeyPointVector();
        descriptorssift = new opencv_core.Mat();
        Pointer pixs = new BytePointer(ByteBuffer.wrap((byte[]) ip1.getPixels()));
        opencv_core.Mat data = new opencv_core.Mat(ip1.getWidth(), ip1.getHeight(), CV_8U, pixs);
        sift.detectAndCompute(data, new opencv_core.Mat(), kpointssift, descriptorssift, false);
        setKpoints(kpointssift);
        System.out.println("keypoints:" + kpointssift.size() + " siftdescriptor:" + descriptorssift.rows() + ", " + descriptorssift.cols());

    }

    public HashMap<Point2D, Point2D> matchWith(ListFeature other, boolean validateWithHomography) {
        //if(validateWithHomography) return matchWithOld(other,validateWithHomography);
        if (this.kpointssift.size() == 0 || ((SIFTListFeaturesOpenCV) other).kpointssift.size() == 0) {
            System.out.println("no features : no matching possible!!!");
            return new HashMap<Point2D, Point2D>();
        }
        //opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(NORM_HAMMING,true);
        if (crossValidation)
            return matchingWithHomography(this.kpointssift, this.descriptorssift, this.points, ((SIFTListFeaturesOpenCV) other).kpointssift, ((SIFTListFeaturesOpenCV) other).descriptorssift, ((SIFTListFeaturesOpenCV) other).points, NORM_L2, ransacPrecision, 5);
        else
            return matchWithLoweVersion(this.kpointssift, this.descriptorssift, this.points, ((SIFTListFeaturesOpenCV) other).kpointssift, ((SIFTListFeaturesOpenCV) other).descriptorssift, ((SIFTListFeaturesOpenCV) other).points, NORM_L2, ransacPrecision, 5, siftMatchThreshold);
    }

    /*
     * public  HashMap<Point2D,Point2D> matchWithLoweVersion(ListFeaturesOpenCV other , boolean validateWithHomography){
     * int maxRecup=5;
     * opencv_features2d.BFMatcher matcher = (crossValidation)?new opencv_features2d.BFMatcher(NORM_L2,true):new opencv_features2d.BFMatcher();
     * opencv_core.DMatchVector matches=new opencv_core.DMatchVector();
     *
     * opencv_core.DMatchVectorVector matchesKnn=new opencv_core.DMatchVectorVector();
     * matcher.knnMatch(descriptorssift,((SIFTListFeaturesOpenCV)other).descriptorssift,matchesKnn,Math.max(2,maxRecup));
     * System.out.println("sift matches:"+matches.size());
     *
     * //opencv_core.DMatchVector siftmatchesCorrect=new opencv_core.DMatchVector();
     * for(int i=0;i<matchesKnn.size();i++){
     * if(matchesKnn.get(i).size()<2) continue;
     * opencv_core.DMatch preums=matchesKnn.get(i).get(0);
     * opencv_core.DMatch deuz=matchesKnn.get(i).get(1);
     * if( preums.distance() < siftMatchThreshold*deuz.distance()) {
     * matches.resize(matches.size()+1);
     * matches.put(matches.size()-1,preums);
     * }
     * }
     * System.out.println("sift matches:"+matches.size());
     *
     *
     * // compute homography
     * //validation of matches
     * int[] pointIndexes1=new int[(int)matches.size()];
     * int[] pointIndexes2=new int[(int)matches.size()];
     * for(int i=0;i<matches.size();i++){
     * pointIndexes1[i]=matches.get(i).queryIdx();
     * pointIndexes2[i]=matches.get(i).trainIdx();
     * }
     *
     * opencv_core.Point2fVector p1= new opencv_core.Point2fVector();
     * opencv_core.Point2fVector p2= new opencv_core.Point2fVector();
     * opencv_core.KeyPoint.convert(kpointssift,p1,pointIndexes1);
     * opencv_core.KeyPoint.convert(((SIFTListFeaturesOpenCV) other).kpointssift,p2,pointIndexes2);
     * opencv_core.Mat inlier=new opencv_core.Mat();
     * opencv_core.Mat m1=toMat(p1) ;
     * opencv_core.Mat m2=toMat(p2) ;
     * opencv_core.Mat homography = opencv_calib3d.findHomography(m1,m2, inlier,opencv_calib3d.FM_RANSAC,ransacPrecision);
     * int count=0;
     * UByteBufferIndexer inlierIndexer  = (inlier.rows()>0)? (UByteBufferIndexer)inlier.createIndexer():null;
     * for(int i=0;i<matches.size();i++){
     * if((inlierIndexer!=null&&inlierIndexer.get(i)!=0)||(inlierIndexer==null)){
     * count++;
     * }
     * }
     * //System.out.println("number of inliers: "+count);
     * //apply to all original points
     * opencv_core.Mat m1transformed = new opencv_core.Mat(1, (int)kpointssift.size(), CV_32FC2);
     * p1= new opencv_core.Point2fVector();
     * opencv_core.KeyPoint.convert(kpointssift,p1);
     * m1=toMat(p1) ;
     * opencv_core.perspectiveTransform(m1,m1transformed,homography);
     * //System.out.println("nb points:"+keypoints1.size()+"  <--> "+keypoints2.size());
     * HashMap<Point2D,Point2D> result=new HashMap<Point2D, Point2D>((int)matches.size());
     * FloatIndexer indx = m1transformed.createIndexer();
     * count=0;
     * //System.out.println("matches Knn:"+matchesKnn.size()+" p1:"+p1.size()+" p2:"+p2.size());
     * System.out.flush();
     * for(int i=0;i<p1.size();i++){
     * double x1=indx.get(0,i,0);
     * double y1=indx.get(0,i,1);
     * Point2D best=null;
     * double bestDst=Double.MAX_VALUE;
     * for(int j=0;j<maxRecup;j++){
     * opencv_core.DMatch p=matchesKnn.get(i).get(j);
     * double x2=((SIFTListFeaturesOpenCV) other).points.get(p.trainIdx()).getX();
     * double y2=((SIFTListFeaturesOpenCV) other).points.get(p.trainIdx()).getY();
     * // System.out.println("ori("+points1.get(p.queryIdx()).getX()+", "+points1.get(p.queryIdx()).getY()+")" + "-->("+x1+", "+y1+")" + "vs ("+x2+", "+y2+")");
     * double dst=(x2-x1)*(x2-x1)+(y2-y1)*(y2-y1) ;
     * if(dst<ransacPrecision){
     * if(dst<bestDst){
     * best=((SIFTListFeaturesOpenCV) other).points.get(p.trainIdx());
     * }
     * }
     * }
     * if(best!=null) {
     * result.put(points.get(i), best);
     * //System.out.println("match!!!");
     * count++;
     * }
     * }
     * System.out.println("number of matching keypoints: "+count);
     * return result;
     * }
     */

    public ArrayList<Point2D> getFeatures() {
        return points;
    }

    /**
     * @return a vector containing all SIFT keypoints (points of interest)
     */
    public KeyPointVector getKeyPoints() {
        return kpointssift;
    }

    /**
     * @return a matrix containing the descriptor of each keypoint
     */
    public opencv_core.Mat getDescriptors() {
        return descriptorssift;
    }
}
