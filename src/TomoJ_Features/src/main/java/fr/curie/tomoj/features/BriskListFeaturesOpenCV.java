package fr.curie.tomoj.features;

import ij.process.ImageProcessor;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.indexer.UByteBufferIndexer;
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
public class BriskListFeaturesOpenCV extends ListFeaturesOpenCV {
    opencv_features2d.BRISK brisk;
    opencv_core.KeyPointVector kpointsbrisk;
    opencv_core.Mat descriptorsbrisk;
    ArrayList<Point2D> points;
    int threshold=60;
    int octave=4;
    float patternScale =1.0f;
    double ransacPrecision=2.0;

    public BriskListFeaturesOpenCV(){
        //Brisk params
        int threshold=60;
        int octave=4;
        float patterScale=1.0f;
        brisk=opencv_features2d.BRISK.create(threshold,octave,patterScale);

    }

    public BriskListFeaturesOpenCV(int threshold, int octave, float patternScale, double ransacPrecision){
        this.threshold=threshold;
        this.octave=octave;
        this.patternScale = patternScale;
        this.ransacPrecision=ransacPrecision;
        brisk=opencv_features2d.BRISK.create(threshold,octave, patternScale);

    }

    public ListFeature createListFeature(){
        return new BriskListFeaturesOpenCV(threshold,octave, patternScale,ransacPrecision);
    }

    public void setParameters(int threshold, int octave, double patternScale, double ransacPrecision){
        this.threshold=threshold;
        this.octave=octave;
        this.patternScale = (float)patternScale;
        this.ransacPrecision=ransacPrecision;
        brisk=opencv_features2d.BRISK.create(threshold,octave, this.patternScale);

    }
    public String getParametersAsString(){
        String params="BRISK\n"+
                "Nb Octaves: "+octave+"\tthreshold: "+threshold+"\n" +
                "pattern scale: "+patternScale+"\n" ;
        params+="\nhomography ransac precision: "+ransacPrecision;
        return params;
    }

    protected void setKpoints(opencv_core.KeyPointVector kpoints){
        this.kpointsbrisk=kpoints;
        points=new ArrayList<Point2D>((int)kpointsbrisk.size());
        for(int i=0;i<kpointsbrisk.size();i++){
            float x=kpointsbrisk.get(i).pt().x();
            float y=kpointsbrisk.get(i).pt().y();
            Point2D p1=new Point2D.Double(x,y);
            points.add(p1);
        }
    }

    public Object detect(ImageProcessor ip){
        ImageProcessor ip1=ip.convertToByte(true);
        kpointsbrisk=new opencv_core.KeyPointVector();
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        brisk.detect(data,kpointsbrisk);
        setKpoints(kpointsbrisk);
        return kpointsbrisk;
    }

    public void compute(ImageProcessor ip, Object kpoints){
        setKpoints((opencv_core.KeyPointVector)kpoints);
        descriptorsbrisk=new opencv_core.Mat();
        ImageProcessor ip1=ip.convertToByte(true);
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        brisk.compute(data,kpointsbrisk,descriptorsbrisk);
    }

    public  void detectAndCompute(ImageProcessor ip){
        //ip.resetMinAndMax();
        ImageProcessor ip1=ip.convertToByte(true);
        kpointsbrisk=new opencv_core.KeyPointVector();
        descriptorsbrisk=new opencv_core.Mat();
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        brisk.detectAndCompute(data,new opencv_core.Mat(),kpointsbrisk,descriptorsbrisk,false);

        setKpoints(kpointsbrisk);
        System.out.println("keypoints:"+kpointsbrisk.size()+" briskdescriptor:"+descriptorsbrisk.rows()+", "+descriptorsbrisk.cols());

    }
    public  HashMap<Point2D,Point2D> matchWith(ListFeature other , boolean validateWithHomography){
        //if(validateWithHomography) return matchWithOld(other,validateWithHomography);
        if(this.kpointsbrisk.size()==0||((BriskListFeaturesOpenCV)other).kpointsbrisk.size()==0) {
            System.out.println("no features : no matching possible!!!");
            return new HashMap<Point2D, Point2D>();
        }
        //opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(NORM_HAMMING,true);
        return matchingWithHomography(this.kpointsbrisk,this.descriptorsbrisk,this.points,((BriskListFeaturesOpenCV)other).kpointsbrisk,((BriskListFeaturesOpenCV)other).descriptorsbrisk,((BriskListFeaturesOpenCV)other).points,NORM_HAMMING,ransacPrecision,5);
    }

    public  HashMap<Point2D,Point2D> matchWithOld(ListFeature other , boolean validateWithHomography){
        opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(NORM_HAMMING,true);
        opencv_core.DMatchVector briskmatches=new opencv_core.DMatchVector();
        matcher.match(descriptorsbrisk,((BriskListFeaturesOpenCV)other).descriptorsbrisk,briskmatches);
        System.out.println("brisk matches:"+briskmatches.size());
        HashMap<Point2D,Point2D> result=new HashMap<Point2D, Point2D>((int)briskmatches.size());
        if(!validateWithHomography) {
            for(int i=0;i<briskmatches.size();i++){
                result.put(points.get(briskmatches.get(i).queryIdx()),((BriskListFeaturesOpenCV) other).points.get(briskmatches.get(i).trainIdx()));
            }
            return result;
        }
        opencv_core.Mat inlier=getInliers(briskmatches,this.kpointsbrisk,((BriskListFeaturesOpenCV) other).kpointsbrisk,ransacPrecision);
        System.out.println("create indexer "+inlier+"\nrows: "+inlier.rows());
        System.out.flush();
        UByteBufferIndexer inlierIndexer  = (inlier.rows()>0)? (UByteBufferIndexer)inlier.createIndexer():null;
        int count=0;
        for(int i=0;i<briskmatches.size();i++){
            if((inlierIndexer!=null&&inlierIndexer.get(i)!=0)||(inlierIndexer==null)){
                count++;
                result.put(points.get(briskmatches.get(i).queryIdx()),((BriskListFeaturesOpenCV) other).points.get(briskmatches.get(i).trainIdx()));
            }
        }
        System.out.println("after RANSAC : "+count);
        return result;
    }


    public ArrayList<Point2D> getFeatures(){
        return points;
    }
}
