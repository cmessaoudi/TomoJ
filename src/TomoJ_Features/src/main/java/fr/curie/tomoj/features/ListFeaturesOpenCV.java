package fr.curie.tomoj.features;

import ij.Prefs;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.UByteRawIndexer;
import org.bytedeco.javacpp.opencv_calib3d;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_features2d;
import org.bytedeco.javacpp.opencv_flann;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;

import static org.bytedeco.javacpp.opencv_core.CV_32FC2;
import static org.bytedeco.javacpp.opencv_core.NORM_L2;

/**
 * Created by cedric on 03/05/2016.
 */
public abstract class ListFeaturesOpenCV implements ListFeature {

    /**
     * Current homography matrix
     */
    opencv_core.Mat homography = null;


    protected opencv_core.Mat toMat(opencv_core.Point2fVector points) {
        // Create Mat representing a vector of Points2f
        //System.out.println("points size: "+points.size());
        opencv_core.Mat dest = new opencv_core.Mat(1, (int) points.size(), CV_32FC2);
        //System.out.println(dest);
        FloatIndexer indx = dest.createIndexer();
        for (int i = 0; i < points.size(); i++) {
            opencv_core.Point2f p = points.get(i);
            indx.put(0, i, 0, p.x());
            indx.put(0, i, 1, p.y());
            //System.out.println("("+p.x()+", "+p.y()+") --> ("+indx.get(0,i,0)+", "+indx.get(0,i,1)+")");
        }
        return dest;
    }

    protected opencv_core.Mat getInliers(opencv_core.DMatchVector matches, opencv_core.KeyPointVector keypoints1, opencv_core.KeyPointVector keypoints2, double ransacPrecision) {
        //validation of matches
        int[] pointIndexes1 = new int[(int) matches.size()];
        int[] pointIndexes2 = new int[(int) matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            pointIndexes1[i] = matches.get(i).queryIdx();
            pointIndexes2[i] = matches.get(i).trainIdx();
        }

        opencv_core.Point2fVector points1 = new opencv_core.Point2fVector();
        opencv_core.Point2fVector points2 = new opencv_core.Point2fVector();
        opencv_core.KeyPoint.convert(keypoints1, points1, pointIndexes1);
        opencv_core.KeyPoint.convert(keypoints2, points2, pointIndexes2);
        opencv_core.Mat inlier = new opencv_core.Mat();
        //new opencv_core.Mat(points1);
        //System.out.println("compute homography"+ points1.size()+" "+points2.size());
        System.out.flush();
        opencv_core.Mat homography = opencv_calib3d.findHomography(toMat(points1), toMat(points2), inlier, opencv_calib3d.FM_RANSAC, ransacPrecision);
        //opencv_core.Mat homography = opencv_calib3d.findFundamentalMat(toMat(points1),toMat(points2), inlier,opencv_calib3d.FM_RANSAC,5.0,0.90);
        return inlier;
    }

    protected HashMap<Point2D, Point2D> matchingWithHomography(opencv_core.KeyPointVector keypoints1, opencv_core.Mat descriptors1, ArrayList<Point2D> points1, opencv_core.KeyPointVector keypoints2, opencv_core.Mat descriptors2, ArrayList<Point2D> points2, int matcherNormType, double ransacPrecision, int maxRecup) {
        //return matchingFlannWithHomography(keypoints1,descriptors1,points1,keypoints2,descriptors2,points2,matcherNormType,ransacPrecision,maxRecup);
        return matchingBFWithHomography(keypoints1, descriptors1, points1, keypoints2, descriptors2, points2, matcherNormType, ransacPrecision, maxRecup);
    }

    protected HashMap<Point2D, Point2D> matchingBFWithHomography(opencv_core.KeyPointVector keypoints1, opencv_core.Mat descriptors1, ArrayList<Point2D> points1, opencv_core.KeyPointVector keypoints2, opencv_core.Mat descriptors2, ArrayList<Point2D> points2, int matcherNormType, double ransacPrecision, int maxRecup) {
        opencv_core.setNumThreads(Prefs.getThreads());
        double threshold = ransacPrecision;

        //compute best matches on which Homography will be computed
        opencv_core.DMatchVector matches = new opencv_core.DMatchVector();
        opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(matcherNormType, true);
        matcher.match(descriptors1, descriptors2, matches);
        //System.out.println("matches:"+matches.size());
        //System.out.println("matches:"+matches.size());

        //compute all matches up to maxRecup on which matching point will tried to be recuperated
        opencv_features2d.BFMatcher matcher2 = new opencv_features2d.BFMatcher(matcherNormType, false);
        opencv_core.DMatchVectorVector matchesKnn = new opencv_core.DMatchVectorVector();
        matcher2.knnMatch(descriptors1, descriptors2, matchesKnn, maxRecup);
        //System.out.println("Knn matches:"+matchesKnn.size());

        // compute homography
        //validation of matches
        int[] pointIndexes1 = new int[(int) matches.size()];
        int[] pointIndexes2 = new int[(int) matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            pointIndexes1[i] = matches.get(i).queryIdx();
            pointIndexes2[i] = matches.get(i).trainIdx();
        }

        opencv_core.Point2fVector p1 = new opencv_core.Point2fVector();
        opencv_core.Point2fVector p2 = new opencv_core.Point2fVector();
        opencv_core.KeyPoint.convert(keypoints1, p1, pointIndexes1);
        opencv_core.KeyPoint.convert(keypoints2, p2, pointIndexes2);
        opencv_core.Mat inlier = new opencv_core.Mat();
        opencv_core.Mat m1 = toMat(p1);
        opencv_core.Mat m2 = toMat(p2);
        homography = opencv_calib3d.findHomography(m1, m2, inlier, opencv_calib3d.FM_RANSAC, ransacPrecision);
        int count = 0;
        UByteRawIndexer inlierIndexer = (inlier.rows() > 0) ? (UByteRawIndexer) inlier.createIndexer() : null;
        for (int i = 0; i < matches.size(); i++) {
            if ((inlierIndexer != null && inlierIndexer.get(i) != 0) || (inlierIndexer == null)) {
                count++;
            }
        }
        //System.out.println("number of inliers: "+count);
        //apply to all original points
        opencv_core.Mat m1transformed = new opencv_core.Mat(1, (int) keypoints1.size(), CV_32FC2);
        p1 = new opencv_core.Point2fVector();
        opencv_core.KeyPoint.convert(keypoints1, p1);
        m1 = toMat(p1);
        opencv_core.perspectiveTransform(m1, m1transformed, homography);
        //System.out.println("nb points:"+keypoints1.size()+"  <--> "+keypoints2.size());
        HashMap<Point2D, Point2D> result = new HashMap<Point2D, Point2D>((int) matches.size());
        FloatIndexer indx = m1transformed.createIndexer();
        count = 0;
        //System.out.println("matches Knn:"+matchesKnn.size()+" p1:"+p1.size()+" p2:"+p2.size());
        System.out.flush();
        for (int i = 0; i < p1.size(); i++) {
            double x1 = indx.get(0, i, 0);
            double y1 = indx.get(0, i, 1);
            Point2D best = null;
            double bestDst = Double.MAX_VALUE;
            for (int j = 0; j < maxRecup; j++) {
                opencv_core.DMatch p = matchesKnn.get(i).get(j);
                double x2 = points2.get(p.trainIdx()).getX();
                double y2 = points2.get(p.trainIdx()).getY();
                // System.out.println("ori("+points1.get(p.queryIdx()).getX()+", "+points1.get(p.queryIdx()).getY()+")" + "-->("+x1+", "+y1+")" + "vs ("+x2+", "+y2+")");
                double dst = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
                if (dst < threshold) {
                    if (dst < bestDst) {
                        best = points2.get(p.trainIdx());
                    }
                }
            }
            if (best != null) {
                result.put(points1.get(i), best);
                //System.out.println("match!!!");
                count++;
            }
        }
        //System.out.println("number of matching keypoints: "+count);
        return result;

    }

    protected HashMap<Point2D, Point2D> matchWithLoweVersion(opencv_core.KeyPointVector keypoints1, opencv_core.Mat descriptors1, ArrayList<Point2D> points1, opencv_core.KeyPointVector keypoints2, opencv_core.Mat descriptors2, ArrayList<Point2D> points2, int matcherNormType, double ransacPrecision, int maxRecup, double siftMatchThreshold) {
        opencv_core.setNumThreads(Prefs.getThreads());
        opencv_features2d.BFMatcher matcher = new opencv_features2d.BFMatcher(matcherNormType, false);
        opencv_core.DMatchVector matches = new opencv_core.DMatchVector();

        opencv_core.DMatchVectorVector matchesKnn = new opencv_core.DMatchVectorVector();
        matcher.knnMatch(descriptors1, descriptors2, matchesKnn, Math.max(2, maxRecup));
        //System.out.println("sift matches:"+matches.size());

        //opencv_core.DMatchVector siftmatchesCorrect=new opencv_core.DMatchVector();
        for (int i = 0; i < matchesKnn.size(); i++) {
            if (matchesKnn.get(i).size() < 2) continue;
            opencv_core.DMatch preums = matchesKnn.get(i).get(0);
            opencv_core.DMatch deuz = matchesKnn.get(i).get(1);
            if (preums.distance() < siftMatchThreshold * deuz.distance()) {
                matches.resize(matches.size() + 1);
                matches.put(matches.size() - 1, preums);
            }
        }
        //System.out.println("sift matches:"+matches.size());


        // compute homography
        //validation of matches
        int[] pointIndexes1 = new int[(int) matches.size()];
        int[] pointIndexes2 = new int[(int) matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            pointIndexes1[i] = matches.get(i).queryIdx();
            pointIndexes2[i] = matches.get(i).trainIdx();
        }

        opencv_core.Point2fVector p1 = new opencv_core.Point2fVector();
        opencv_core.Point2fVector p2 = new opencv_core.Point2fVector();
        opencv_core.KeyPoint.convert(keypoints1, p1, pointIndexes1);
        opencv_core.KeyPoint.convert(keypoints2, p2, pointIndexes2);
        opencv_core.Mat inlier = new opencv_core.Mat();
        opencv_core.Mat m1 = toMat(p1);
        opencv_core.Mat m2 = toMat(p2);
        opencv_core.Mat homography = opencv_calib3d.findHomography(m1, m2, inlier, opencv_calib3d.FM_RANSAC, ransacPrecision);
        int count = 0;
        UByteRawIndexer inlierIndexer = (inlier.rows() > 0) ? (UByteRawIndexer) inlier.createIndexer() : null;
        for (int i = 0; i < matches.size(); i++) {
            if ((inlierIndexer != null && inlierIndexer.get(i) != 0) || (inlierIndexer == null)) {
                count++;
            }
        }
        //System.out.println("number of inliers: "+count);
        //apply to all original points
        opencv_core.Mat m1transformed = new opencv_core.Mat(1, (int) keypoints1.size(), CV_32FC2);
        p1 = new opencv_core.Point2fVector();
        opencv_core.KeyPoint.convert(keypoints1, p1);
        m1 = toMat(p1);
        opencv_core.perspectiveTransform(m1, m1transformed, homography);
        //System.out.println("nb points:"+keypoints1.size()+"  <--> "+keypoints2.size());
        HashMap<Point2D, Point2D> result = new HashMap<Point2D, Point2D>((int) matches.size());
        FloatIndexer indx = m1transformed.createIndexer();
        count = 0;
        //System.out.println("matches Knn:"+matchesKnn.size()+" p1:"+p1.size()+" p2:"+p2.size());
        System.out.flush();
        for (int i = 0; i < p1.size(); i++) {
            double x1 = indx.get(0, i, 0);
            double y1 = indx.get(0, i, 1);
            Point2D best = null;
            double bestDst = Double.MAX_VALUE;
            for (int j = 0; j < maxRecup; j++) {
                opencv_core.DMatch p = matchesKnn.get(i).get(j);
                double x2 = points2.get(p.trainIdx()).getX();
                double y2 = points2.get(p.trainIdx()).getY();
                // System.out.println("ori("+points1.get(p.queryIdx()).getX()+", "+points1.get(p.queryIdx()).getY()+")" + "-->("+x1+", "+y1+")" + "vs ("+x2+", "+y2+")");
                double dst = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
                if (dst < ransacPrecision) {
                    if (dst < bestDst) {
                        best = points2.get(p.trainIdx());
                    }
                }
            }
            if (best != null) {
                result.put(points1.get(i), best);
                //System.out.println("match!!!");
                count++;
            }
        }
        //System.out.println("number of matching keypoints: "+count);
        return result;
    }


    protected HashMap<Point2D, Point2D> matchWithLoweVersionFlann(opencv_core.KeyPointVector keypoints1, opencv_core.Mat descriptors1, ArrayList<Point2D> points1, opencv_core.KeyPointVector keypoints2, opencv_core.Mat descriptors2, ArrayList<Point2D> points2, int matcherNormType, double ransacPrecision, int maxRecup, double siftMatchThreshold) {
        opencv_core.DMatchVector matches = new opencv_core.DMatchVector();
        opencv_flann.IndexParams indParams = (matcherNormType == NORM_L2) ? new opencv_flann.KDTreeIndexParams(5) : new opencv_flann.LshIndexParams(6, 12, 1);
        opencv_flann.SearchParams searchParams = new opencv_flann.SearchParams();
        opencv_features2d.FlannBasedMatcher matcher = new opencv_features2d.FlannBasedMatcher(indParams, searchParams);

        //compute all matches up to maxRecup on which matching point will tried to be recuperated
        opencv_core.DMatchVectorVector matchesKnn = new opencv_core.DMatchVectorVector();
        matcher.knnMatch(descriptors1, descriptors2, matchesKnn, Math.max(2, maxRecup));
        //System.out.println("sift matches:"+matches.size());

        //opencv_core.DMatchVector siftmatchesCorrect=new opencv_core.DMatchVector();
        for (int i = 0; i < matchesKnn.size(); i++) {
            if (matchesKnn.get(i).size() < 2) continue;
            opencv_core.DMatch preums = matchesKnn.get(i).get(0);
            opencv_core.DMatch deuz = matchesKnn.get(i).get(1);
            if (preums.distance() < siftMatchThreshold * deuz.distance()) {
                matches.resize(matches.size() + 1);
                matches.put(matches.size() - 1, preums);
            }
        }
        //System.out.println("sift matches:"+matches.size());


        // compute homography
        //validation of matches
        int[] pointIndexes1 = new int[(int) matches.size()];
        int[] pointIndexes2 = new int[(int) matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            pointIndexes1[i] = matches.get(i).queryIdx();
            pointIndexes2[i] = matches.get(i).trainIdx();
        }

        opencv_core.Point2fVector p1 = new opencv_core.Point2fVector();
        opencv_core.Point2fVector p2 = new opencv_core.Point2fVector();
        opencv_core.KeyPoint.convert(keypoints1, p1, pointIndexes1);
        opencv_core.KeyPoint.convert(keypoints2, p2, pointIndexes2);
        opencv_core.Mat inlier = new opencv_core.Mat();
        opencv_core.Mat m1 = toMat(p1);
        opencv_core.Mat m2 = toMat(p2);
        opencv_core.Mat homography = opencv_calib3d.findHomography(m1, m2, inlier, opencv_calib3d.FM_RANSAC, ransacPrecision);
        int count = 0;
        UByteRawIndexer inlierIndexer = (inlier.rows() > 0) ? (UByteRawIndexer) inlier.createIndexer() : null;
        for (int i = 0; i < matches.size(); i++) {
            if ((inlierIndexer != null && inlierIndexer.get(i) != 0) || (inlierIndexer == null)) {
                count++;
            }
        }
        //System.out.println("number of inliers: "+count);
        //apply to all original points
        opencv_core.Mat m1transformed = new opencv_core.Mat(1, (int) keypoints1.size(), CV_32FC2);
        p1 = new opencv_core.Point2fVector();
        opencv_core.KeyPoint.convert(keypoints1, p1);
        m1 = toMat(p1);
        opencv_core.perspectiveTransform(m1, m1transformed, homography);
        //System.out.println("nb points:"+keypoints1.size()+"  <--> "+keypoints2.size());
        HashMap<Point2D, Point2D> result = new HashMap<Point2D, Point2D>((int) matches.size());
        FloatIndexer indx = m1transformed.createIndexer();
        count = 0;
        //System.out.println("matches Knn:"+matchesKnn.size()+" p1:"+p1.size()+" p2:"+p2.size());
        System.out.flush();
        for (int i = 0; i < p1.size(); i++) {
            double x1 = indx.get(0, i, 0);
            double y1 = indx.get(0, i, 1);
            Point2D best = null;
            double bestDst = Double.MAX_VALUE;
            for (int j = 0; j < maxRecup; j++) {
                opencv_core.DMatch p = matchesKnn.get(i).get(j);
                double x2 = points2.get(p.trainIdx()).getX();
                double y2 = points2.get(p.trainIdx()).getY();
                // System.out.println("ori("+points1.get(p.queryIdx()).getX()+", "+points1.get(p.queryIdx()).getY()+")" + "-->("+x1+", "+y1+")" + "vs ("+x2+", "+y2+")");
                double dst = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
                if (dst < ransacPrecision) {
                    if (dst < bestDst) {
                        best = points2.get(p.trainIdx());
                    }
                }
            }
            if (best != null) {
                result.put(points1.get(i), best);
                //System.out.println("match!!!");
                count++;
            }
        }
        //System.out.println("number of matching keypoints: "+count);
        return result;
    }

    public opencv_core.Mat getHomography() {
        return homography;
    }

}
