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
import static org.bytedeco.javacpp.opencv_core.NORM_L2;

/**
 * Created by cedric on 03/05/2016.
 */
public class KazeListFeaturesOpenCV extends ListFeaturesOpenCV {
    opencv_features2d.KAZE kaze;
    opencv_core.KeyPointVector kpointskaze;
    opencv_core.Mat descriptorskaze;
    ArrayList<Point2D> points;
    boolean  extended=false;
    boolean upright=false;
    double threshold=0.001;
    int maxOctaves=4;
    int nbOctaveLayers =4;
    int diffusivityType=opencv_features2d.KAZE.DIFF_PM_G2;
    double ransacPrecision=2.0;

    public KazeListFeaturesOpenCV(){

        extended=false;
        upright=false;
        threshold=0.001;
        maxOctaves=4;
        nbOctaveLayers =4;
        diffusivityType=opencv_features2d.KAZE.DIFF_PM_G2;

        kaze=opencv_features2d.KAZE.create(extended,upright,(float)threshold,maxOctaves, nbOctaveLayers,diffusivityType);

    }

    public KazeListFeaturesOpenCV(boolean extended, boolean upright, double threshold, int maxOctaves, int nbOctaveLayers, int diffusivityType, double ransacPrecision){
        this.extended=extended;
        this.upright=upright;
        this.threshold=threshold;
        this.maxOctaves=maxOctaves;
        this.nbOctaveLayers = nbOctaveLayers;
        this.diffusivityType=diffusivityType;
        this.ransacPrecision=ransacPrecision;
        kaze=opencv_features2d.KAZE.create(extended,upright,(float)threshold,maxOctaves, nbOctaveLayers,diffusivityType);

    }

    public ListFeature createListFeature(){
        return new KazeListFeaturesOpenCV(extended,upright,threshold,maxOctaves, nbOctaveLayers,diffusivityType,ransacPrecision);
    }

    public String getParametersAsString(){
        String params="KAZE\n"+
                "Nb Octaves: "+maxOctaves+"\tlayers: "+nbOctaveLayers+"\n" +
                "threshold: "+threshold+"\n" ;
        if(extended) params+="extended descriptor\n";
        if(upright) params+= "upright\n";
        switch (diffusivityType){
            case opencv_features2d.KAZE.DIFF_PM_G1:
                params+="DIFF_PM_G1";
                break;
            case opencv_features2d.KAZE.DIFF_PM_G2:
                params+="DIFF_PM_G2";
                break;
            case opencv_features2d.KAZE.DIFF_WEICKERT:
                params+="DIFF_WEICKERT";
                break;
            case opencv_features2d.KAZE.DIFF_CHARBONNIER:
                params+="DIFF_CHARBONNIER";
                break;
        }
        params+="\nhomography ransac precision: "+ransacPrecision;
        return params;
    }

    public void setParameters(boolean extended,boolean upright,double threshold,int maxOctaves, int nbOctaveLayers,int diffusivityType,double ransacPrecision){
        this.extended=extended;
        this.upright=upright;
        this.threshold=threshold;
        this.maxOctaves=maxOctaves;
        this.nbOctaveLayers = nbOctaveLayers;
        this.diffusivityType=diffusivityType;
        this.ransacPrecision=ransacPrecision;
        kaze.setExtended(extended);
        kaze.setUpright(upright);
        kaze.setThreshold(threshold);
        kaze.setNOctaves(maxOctaves);
        kaze.setNOctaveLayers(nbOctaveLayers);
        kaze.setDiffusivity(diffusivityType);
    }

    protected void setKpoints(opencv_core.KeyPointVector kpoints){
        this.kpointskaze=kpoints;
        points=new ArrayList<Point2D>((int)kpointskaze.size());
        for(int i=0;i<kpointskaze.size();i++){
            float x=kpointskaze.get(i).pt().x();
            float y=kpointskaze.get(i).pt().y();
            Point2D p1=new Point2D.Double(x,y);
            points.add(p1);
        }
    }

    public Object detect(ImageProcessor ip){
        ImageProcessor ip1=ip.convertToByte(true);
        kpointskaze=new opencv_core.KeyPointVector();
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        kaze.detect(data,kpointskaze);
        setKpoints(kpointskaze);
        return kpointskaze;
    }

    public void compute(ImageProcessor ip, Object kpoints){
        setKpoints((opencv_core.KeyPointVector)kpoints);
        descriptorskaze=new opencv_core.Mat();
        ImageProcessor ip1=ip.convertToByte(true);
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        kaze.compute(data,kpointskaze,descriptorskaze);
    }

    public  void detectAndCompute(ImageProcessor ip){
        //ip.resetMinAndMax();
        ImageProcessor ip1=ip.convertToByte(true);
        kpointskaze=new opencv_core.KeyPointVector();
        descriptorskaze=new opencv_core.Mat();
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        kaze.detectAndCompute(data,new opencv_core.Mat(),kpointskaze,descriptorskaze,false);

        setKpoints(kpointskaze);
        System.out.println("keypoints:"+kpointskaze.size()+" kazedescriptor:"+descriptorskaze.rows()+", "+descriptorskaze.cols());

    }
    public  HashMap<Point2D,Point2D> matchWith(ListFeature other , boolean validateWithHomography){
        //if(validateWithHomography) return matchWithOld(other,validateWithHomography);
        if(this.kpointskaze.size()==0||((KazeListFeaturesOpenCV)other).kpointskaze.size()==0) {
            System.out.println("no features : no matching possible!!!");
            return new HashMap<Point2D, Point2D>();
        }
        //opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(NORM_HAMMING,true);
        return matchingWithHomography(this.kpointskaze,this.descriptorskaze,this.points,((KazeListFeaturesOpenCV)other).kpointskaze,((KazeListFeaturesOpenCV)other).descriptorskaze,((KazeListFeaturesOpenCV)other).points,NORM_L2,ransacPrecision,5);
    }



    public ArrayList<Point2D> getFeatures(){
        return points;
    }
}
