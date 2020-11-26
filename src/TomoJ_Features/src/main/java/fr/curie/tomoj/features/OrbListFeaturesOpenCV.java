package fr.curie.tomoj.features;

import ij.process.ImageProcessor;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.indexer.UByteBufferIndexer;

import java.awt.geom.Point2D;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import static org.bytedeco.javacpp.opencv_core.CV_8U;
import static org.bytedeco.javacpp.opencv_core.NORM_HAMMING;
import static org.bytedeco.javacpp.opencv_core.NORM_HAMMING2;

/**
 * Created by cedric on 03/05/2016.
 */
public class OrbListFeaturesOpenCV extends ListFeaturesOpenCV {
    opencv_features2d.ORB orb;
    opencv_core.KeyPointVector kpointsorb;
    opencv_core.Mat descriptorsorb;
    ArrayList<Point2D> points;
    double ransacPrecision=2.0;
    int wta_k = 2;

    public static int HARRIS_SCORE=opencv_features2d.ORB.HARRIS_SCORE;
    public static int FAST_SCORE=opencv_features2d.ORB.FAST_SCORE;


    public OrbListFeaturesOpenCV(){
        //Orb params
        int nFeatures = 200;
        float scaleFactor = 1.2f;
        int nLevels = 4;
        int firstLevel = 0;
        int edgeThreshold = 64;
        //int scoreType = cv::ORB::FAST_SCORE; //genere beaucoup de trait
        int scoreType = opencv_features2d.ORB.HARRIS_SCORE;
        int patchSize = 32;
        int fastThreshold = 20;
        //orb=opencv_features2d.ORB.create(nFeatures,scaleFactor,nLevels,edgeThreshold,firstLevel,wta_k,scoreType,patchSize,fastThreshold);
        orb=opencv_features2d.ORB.create();
        orb.setMaxFeatures(nFeatures);
        orb.setScaleFactor(scaleFactor);
        orb.setNLevels(nLevels);
        orb.setEdgeThreshold(edgeThreshold);
        orb.setFirstLevel(firstLevel);
        orb.setWTA_K(wta_k);
        orb.setScoreType(scoreType);
        orb.setPatchSize(patchSize);
        orb.setFastThreshold(fastThreshold);

//        System.out.println("max features: "+nFeatures+"-->"+orb.getMaxFeatures());
//        System.out.println("scale factor: "+scaleFactor+"-->"+orb.getScaleFactor());
//        System.out.println("number of levels: "+nLevels+"-->"+orb.getNLevels());
//        System.out.println("edge threshold: "+edgeThreshold+"-->"+orb.getEdgeThreshold());
//        System.out.println("first level: "+firstLevel+"-->"+orb.getFirstLevel());
//        System.out.println("wta_k: "+wta_k+"-->"+orb.getWTA_K());
//        System.out.println("score type: "+scoreType+"-->"+orb.getScoreType());
//        System.out.println("patch Size: "+patchSize+"-->"+orb.getPatchSize());
//        System.out.println("FAST threshold: "+fastThreshold+"-->"+orb.getFastThreshold());
    }

    public OrbListFeaturesOpenCV(int nFeatures, float scaleFactor, int nLevels, int edgeThreshold, int firstLevel, int wta_k, int scoreType, int patchSize, int fastThreshold, double ransacPrecision){
        //orb=opencv_features2d.ORB.create(nFeatures,scaleFactor,nLevels,edgeThreshold,firstLevel,wta_k,scoreType,patchSize,fastThreshold);
        this.wta_k=wta_k;
        orb=opencv_features2d.ORB.create();
        orb.setMaxFeatures(nFeatures);
        orb.setScaleFactor(scaleFactor);
        orb.setNLevels(nLevels);
        orb.setEdgeThreshold(edgeThreshold);
        orb.setFirstLevel(firstLevel);
        orb.setWTA_K(wta_k);
        orb.setScoreType(scoreType);
        orb.setPatchSize(patchSize);
        orb.setFastThreshold(fastThreshold);
        this.ransacPrecision=ransacPrecision;
//        System.out.println("max features: "+nFeatures+"-->"+orb.getMaxFeatures());
//        System.out.println("scale factor: "+scaleFactor+"-->"+orb.getScaleFactor());
//        System.out.println("number of levels: "+nLevels+"-->"+orb.getNLevels());
//        System.out.println("edge threshold: "+edgeThreshold+"-->"+orb.getEdgeThreshold());
//        System.out.println("first level: "+firstLevel+"-->"+orb.getFirstLevel());
//        System.out.println("wta_k: "+wta_k+"-->"+orb.getWTA_K());
//        System.out.println("score type: "+scoreType+"-->"+orb.getScoreType());
//        System.out.println("patch Size: "+patchSize+"-->"+orb.getPatchSize());
//        System.out.println("FAST threshold: "+fastThreshold+"-->"+orb.getFastThreshold());

    }

    public ListFeature createListFeature(){
        OrbListFeaturesOpenCV tmp=new OrbListFeaturesOpenCV();
        tmp.orb.setMaxFeatures(this.orb.getMaxFeatures());
        tmp.orb.setScaleFactor(this.orb.getScaleFactor());
        tmp.orb.setNLevels(this.orb.getNLevels());
        tmp.orb.setEdgeThreshold(this.orb.getEdgeThreshold());
        tmp.orb.setFirstLevel(this.orb.getFirstLevel());
        tmp.orb.setWTA_K(this.orb.getWTA_K());
        tmp.orb.setScoreType(this.orb.getScoreType());
        tmp.orb.setPatchSize(this.orb.getPatchSize());
        tmp.orb.setFastThreshold(this.orb.getFastThreshold());
        tmp.ransacPrecision=this.ransacPrecision;
        tmp.wta_k=this.wta_k;
        return tmp;
        //return new OrbListFeaturesOpenCV(orb.getMaxFeatures(),(float)orb.getScaleFactor(), orb.getNLevels(),orb.getFirstLevel(),orb.getEdgeThreshold(),orb.getWTA_K(),orb.getScoreType(),orb.getPatchSize(),orb.getFastThreshold());
    }

    public void setParameters(int nFeatures,double scaleFactor, int nLevels, int firstLevel,
                              int edgeThreshold, int wta_k, int scoreType, int patchSize,
                              int fastThreshold, double ransacPrecision){

        this. wta_k=wta_k;
        orb.setMaxFeatures(nFeatures);
        orb.setScaleFactor(scaleFactor);
        orb.setNLevels(nLevels);
        orb.setEdgeThreshold(edgeThreshold);
        orb.setFirstLevel(firstLevel);
        orb.setWTA_K(wta_k);
        orb.setScoreType(scoreType);
        orb.setPatchSize(patchSize);
        orb.setFastThreshold(fastThreshold);
        this.ransacPrecision=ransacPrecision;

    }
    public String getParametersAsString(){
        String params="ORB\n";
        params+="max features: "+orb.getMaxFeatures();
        params+=("\nscale factor: "+orb.getScaleFactor());
        params+=("\nnumber of levels: "+orb.getNLevels());
        params+=("\nedge threshold: "+orb.getEdgeThreshold());
        params+=("\nfirst level: "+orb.getFirstLevel());
        params+=("\nwta_k: "+orb.getWTA_K());
        params+=("\nscore type: "+orb.getScoreType()+"("+((orb.getScoreType()== opencv_features2d.ORB.HARRIS_SCORE)?"HARRIS_CORNER":"FAST")+")");
        params+=("\npatch Size: "+orb.getPatchSize());
        params+=("\nFAST threshold: "+orb.getFastThreshold());
        return params;
    }

    protected void setKpoints(opencv_core.KeyPointVector kpoints){
        this.kpointsorb=kpoints;
        points=new ArrayList<Point2D>((int)kpointsorb.size());
        for(int i=0;i<kpointsorb.size();i++){
            float x=kpointsorb.get(i).pt().x();
            float y=kpointsorb.get(i).pt().y();
            Point2D p1=new Point2D.Double(x,y);
            points.add(p1);
        }
    }
    public Object detect(ImageProcessor ip){
        ImageProcessor ip1=ip.convertToByte(true);
        kpointsorb=new opencv_core.KeyPointVector();
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        orb.detect(data,kpointsorb);
        setKpoints(kpointsorb);
        return kpointsorb;
    }

    public void compute(ImageProcessor ip, Object kpoints){
        setKpoints((opencv_core.KeyPointVector)kpoints);
        descriptorsorb=new opencv_core.Mat();
        ImageProcessor ip1=ip.convertToByte(true);
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        orb.compute(data,kpointsorb,descriptorsorb);
    }

    public  void detectAndCompute(ImageProcessor ip){
        //init
        //ip.resetMinAndMax();
        ImageProcessor ip1=ip.convertToByte(true);
        kpointsorb=new opencv_core.KeyPointVector();
        descriptorsorb=new opencv_core.Mat();
        Pointer pixs=new BytePointer(ByteBuffer.wrap((byte[])ip1.getPixels()));
        opencv_core.Mat data=new opencv_core.Mat(ip1.getWidth(),ip1.getHeight(),CV_8U,pixs);
        //compute things
        orb.detectAndCompute(data,new opencv_core.Mat(),kpointsorb,descriptorsorb,false);

        setKpoints(kpointsorb);
        System.out.println("keypoints:"+kpointsorb.size()+" orbdescriptor:"+descriptorsorb.rows()+", "+descriptorsorb.cols()+", "+descriptorsorb.depth());

    }
    public  HashMap<Point2D,Point2D> matchWith(ListFeature other , boolean validateWithHomography){
        //if(validateWithHomography) return matchWithOld(other,validateWithHomography);
        if(this.kpointsorb.size()==0||((OrbListFeaturesOpenCV)other).kpointsorb.size()==0) {
            System.out.println("no features : no matching possible!!!");
            return new HashMap<Point2D, Point2D>();
        }
        //opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(NORM_HAMMING,true);
        return matchingWithHomography(this.kpointsorb,this.descriptorsorb,this.points,((OrbListFeaturesOpenCV)other).kpointsorb,((OrbListFeaturesOpenCV)other).descriptorsorb,((OrbListFeaturesOpenCV)other).points,(wta_k==2)?NORM_HAMMING:NORM_HAMMING2,ransacPrecision,5);
    }

    public  HashMap<Point2D,Point2D> matchWithOld(ListFeature other , boolean validateWithHomography){
        HashMap<Point2D,Point2D> result=new HashMap<Point2D, Point2D>();
        if(this.kpointsorb.size()==0||((OrbListFeaturesOpenCV)other).kpointsorb.size()==0) {
            System.out.println("no features : no matching possible!!!");
            return result;
        }
        opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(NORM_HAMMING,true);
        opencv_core.DMatchVector orbmatches=new opencv_core.DMatchVector();
        matcher.match(descriptorsorb,((OrbListFeaturesOpenCV)other).descriptorsorb,orbmatches);
        System.out.println("orb matches:"+orbmatches.size());

        //validation of matches
//        int[] pointIndexes1=new int[(int)orbmatches.size()];
//        int[] pointIndexes2=new int[(int)orbmatches.size()];
//        for(int i=0;i<orbmatches.size();i++){
//            pointIndexes1[i]=orbmatches.get(i).queryIdx();
//            pointIndexes1[i]=orbmatches.get(i).trainIdx();
//        }
//
//        opencv_core.Point2fVector points1= new opencv_core.Point2fVector();
//        opencv_core.Point2fVector points2= new opencv_core.Point2fVector();
//        opencv_core.KeyPoint.convert(kpointsorb,points1,pointIndexes1);
//        opencv_core.KeyPoint.convert(((OrbListFeaturesOpenCV) other).kpointsorb,points2,pointIndexes2);
//        opencv_core.Mat inlier=new opencv_core.Mat();
//        //new opencv_core.Mat(points1);
//        System.out.println("compute homography"+ points1.size()+" "+points2.size());
//        System.out.flush();
//        opencv_core.Mat homography = opencv_calib3d.findHomography(toMat(points1),toMat(points2), inlier,opencv_calib3d.FM_RANSAC,5.0);
        //opencv_core.Mat homography = opencv_calib3d.findFundamentalMat(toMat(points1),toMat(points2), inlier,opencv_calib3d.FM_RANSAC,5.0,0.90);

        opencv_core.Mat inlier=(validateWithHomography)?getInliers(orbmatches,this.kpointsorb,((OrbListFeaturesOpenCV) other).kpointsorb,ransacPrecision):new opencv_core.Mat();
        System.out.println("create indexer "+inlier+"\nrows: "+inlier.rows());
        System.out.flush();
        UByteBufferIndexer inlierIndexer  = (inlier.rows()>0)? (UByteBufferIndexer)inlier.createIndexer():null;
        int count=0;
        for(int i=0;i<orbmatches.size();i++){
            if((!validateWithHomography)||(validateWithHomography&&((inlierIndexer!=null&&inlierIndexer.get(i)!=0)||(inlierIndexer==null)))){
                count++;
                result.put(points.get(orbmatches.get(i).queryIdx()),((OrbListFeaturesOpenCV) other).points.get(orbmatches.get(i).trainIdx()));
            }
        }
        System.out.println("after RANSAC : "+count);
        return result;
    }


    public ArrayList<Point2D> getFeatures(){
        return points;
    }






}
