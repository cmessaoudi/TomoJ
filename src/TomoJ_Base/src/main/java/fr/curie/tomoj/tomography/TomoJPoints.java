package fr.curie.tomoj.tomography;

import fr.curie.tomoj.align.AffineAlignment;
import ij.IJ;
import ij.Prefs;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import fr.curie.filters.FFTFilter_TomoJ;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * stores the landmarks for each image.<BR>
 * it is also the place where computation of alignment using 3D landmarks is performed.<BR>
 * User: MESSAOUDI Cedric
 * Date: 3 dec. 2008
 * Time: 10:44:56
 */
public class TomoJPoints {
    protected boolean showInIJ = true;
    protected boolean goldBead = false;
    protected int critical_FilterLarge = 125;
    protected int critical_FilterSmall = 2;
    protected int critical_SeedNumber = 150;
    protected int critical_MinimaRadius = 8;
    protected double percentageExcludedX = 0.1;
    protected double percentageExcludedY = 0.1;
    protected double completion = 0;
    protected ExecutorService exec;
    ArrayList<Point2D[]> landmarks = new ArrayList<Point2D[]>();
    ArrayList<Point2D[]> reprojectedLandmarks = null;
    ArrayList<Boolean> automaticGeneration = new ArrayList<Boolean>();

    /**
     * The first Integer corresponds to the indexes of the common landmarks
     * chain in this TomoJPoints and the second one to the corresponding
     * landmarks chain indexes in the other TomoJPoints.
     */
    private Hashtable<Integer, Integer> commonLandmarks = null;
    /**
     * Contains the indexes of the landmarks chains only present on this series.
     */
    private ArrayList<Boolean> properLandmarks = null;

    TiltSeries ts;
    boolean showAll = false;
    boolean showCommon = false;
    boolean visible = false;
    int currentIndex = -1;
    //AffineTransform[] Aij;
    //AffineTransform[] Aji;
    int nbThreads = Prefs.getThreads();
    FFTFilter_TomoJ fftf;


    public TomoJPoints(TiltSeries ts) {
        this.ts = ts;
        addNewSetOfPoints();
    }

    public ArrayList<Point2D[]> getAllLandmarks() {
        return landmarks;
    }

    public void setAllLandmarks(ArrayList<Point2D[]> landmarks, boolean automaticGeneration) {
        this.landmarks = landmarks;
        currentIndex = landmarks.size() - 1;
        this.automaticGeneration.clear();
        for (int i = 0; i < landmarks.size(); i++) this.automaticGeneration.add(automaticGeneration);
        reprojectedLandmarks = null;
    }

    public ArrayList<Boolean> getAllAutomaticGeneration() {
        return automaticGeneration;
    }


    public int addNewSetOfPoints() {
        return addNewSetOfPoints(false);
    }

    public int addNewSetOfPoints(boolean automaticalyGenerated) {
        addSetOfPoints(new Point2D[ts.getStackSize()], automaticalyGenerated);
        currentIndex = landmarks.size() - 1;
        return currentIndex;
    }

    public void addSetOfPoints(Point2D[] pts, boolean automaticallyGenerated) {
        landmarks.add(pts);
        automaticGeneration.add(automaticallyGenerated);
    }

    public void addSetOfPointsSynchronized(Point2D[] pts, boolean automaticallyGenerated) {
        synchronized (landmarks) {
            landmarks.add(pts);
            automaticGeneration.add(automaticallyGenerated);
        }
    }

    public void addSetOfPoints(ArrayList<Point2D[]> points, boolean automaticallyGenerated) {
        for (Point2D[] pts : points) addSetOfPoints(pts, automaticallyGenerated);
    }

//    public void addCommonLandmarkIndex(int index){
//        if(commonLandmarks==null) commonLandmarks=new ArrayList<Integer>(landmarks.size());
//        commonLandmarks.add(index);
//    }

    /**
     * Add common landmarks chain index from two tilt series.
     * @param index_this This landmarks index.
     * @param index_other The landmarks index of the other TomoJPoints.
     */
    public void addCommonLandmarkIndex(int index_this, int index_other) {
        if (commonLandmarks == null)
            commonLandmarks = new Hashtable<>(landmarks.size());
        if (getPoints(index_this) != null)
            commonLandmarks.put(index_this, index_other);
    }

    /**
     * Set common landmarks chain list of indexes from two tilt series.
     * @param common Hashtable containing indexes of common landmarks chains in
     *               this TomoJPoints as keys and from the other TomoJPoints as
     *               values.
     */
    public void setCommonLandmarksIndex(Hashtable<Integer, Integer> common) {
        if (commonLandmarks == null)
            commonLandmarks = new Hashtable<>(landmarks.size());
        commonLandmarks = common;
    }

    public Hashtable<Integer, Integer> getCommonLandmarksIndex() {
        return commonLandmarks;
    }

    /**
     * @param index
     */
    public void removeCommonLandmarks(int index) {
        commonLandmarks.remove(index);
    }

    /**
     * @param index
     * @return true if the landmark at this index is proper to this TiltSeries
     */
    public boolean isProperLandmark(int index) {
        return properLandmarks.get(index);
    }

    /**
     * @return the list of proper landmarks
     */
    public ArrayList<Boolean> getProperLandmarks() {
        return properLandmarks;
    }


    /**
     * Find the landmarks chains only present on this series
     */
    public void setProperLandmarksIndex() {
        if (commonLandmarks != null) {
            properLandmarks = new ArrayList<>(landmarks.size());
            for (int i = 0; i < landmarks.size(); i++) {
                if ((landmarks.get(i) != null) && !commonLandmarks.containsKey(i)) {
                    properLandmarks.add(i, true);
                } else {
                    properLandmarks.add(i, false);
                }
            }
        } else System.out.println("commonLandmarks needs to be set first!");
    }

    public void setShowIJ(boolean value) {
        showInIJ = value;
    }

    public void reset() {
        landmarks.clear();
        automaticGeneration.clear();
        reprojectedLandmarks = null;
        addNewSetOfPoints();
    }

    public void interrupt() {
        completion = -1000;
    }

    public double getCompletion() {
        return completion;
    }

    public void resetCompletion() {
        completion = 0;
    }

    public TiltSeries getTiltSeries() {
        return ts;
    }

    public void addSetOfPoints(Point2D[] pts) {
        addSetOfPoints(pts, false);
    }

    public int removeCurrentSetOfPoints() {
        removeSetOfPoints(currentIndex);
        if (currentIndex >= landmarks.size()) {
            currentIndex = landmarks.size() - 1;
        }
        //bestPreviousAlignmentDeform=null;
        return currentIndex;
    }

    public int removeSetOfPoints(int index) {
        landmarks.remove(index);
        automaticGeneration.remove(index);
        if (reprojectedLandmarks != null) reprojectedLandmarks.remove(index);
        if (currentIndex > index) currentIndex--;
        return currentIndex;
    }

    public int removeAllSetsOfPoints() {
        landmarks.clear();
        //bestPreviousAlignmentDeform=null;
        automaticGeneration.clear();
        if (reprojectedLandmarks != null) reprojectedLandmarks = null;
        if (commonLandmarks != null) {
            commonLandmarks.clear();
        }
        if (properLandmarks != null) {
            properLandmarks.clear();
        }
        return addNewSetOfPoints();
    }

    public void removeImage(int index) {
        for (int i = 0; i < landmarks.size(); i++) {
            Point2D[] tmp = landmarks.get(i);
            Point2D[] res = new Point2D[tmp.length - 1];
            System.arraycopy(tmp, 0, res, 0, index);
            System.arraycopy(tmp, index + 1, res, index, tmp.length - 1 - index);
            landmarks.set(i, res);
        }
    }

    public void removeEmptyChains() {
        for (int j = getNumberOfPoints() - 1; j >= 0; j--) {
            Point2D[] tmppts = getPoints(j);
            int count = 0;
            for (Point2D tmppt : tmppts) {
                if (tmppt != null) count++;
            }
            if (count == 0) {
                System.out.println("remove landmark at index " + j + " : it has no defined points " + tmppts);
                System.out.flush();
                removeSetOfPoints(j);
            }
        }
    }

    public void setPoints(int index, Point2D[] pts) {
        landmarks.set(index, pts);
    }



    public void setReprojectedLandmarks(ArrayList<Point2D[]> reprojected) {
        reprojectedLandmarks = reprojected;
    }

    public ArrayList<Point2D[]> getReprojectedLandmarks() {
        return reprojectedLandmarks;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean val) {
        visible = val;
    }


    public boolean isAutomaticallyGenerated(int index) {
        return automaticGeneration.get(index);
    }

    public int getAutoLength() {
        return automaticGeneration.size();
    }


    /**
     * get the coordinates of the point number index on all images
     *
     * @param index point number
     * @return the points on all images
     */
    public Point2D[] getPoints(int index) {
        return landmarks.get(index);
    }

    public Point2D[] getCenteredPoints(int index) {
        Point2D[] tmp = landmarks.get(index);
        Point2D[] res = new Point2D[tmp.length];
        for (int i = 0; i < tmp.length; i++) {
            if (tmp[i] != null) {
                res[i] = new Point2D.Double(tmp[i].getX() - ts.getProjectionCenterX(), tmp[i].getY() - ts.getProjectionCenterY());
            }
        }
        return res;
    }

    /**
     * Get all the 2D points from the image which the index is given as
     * argument. The points coordinates are returned with the upper left corner
     * of the image as origin.
     * @param imgnb Image index number.
     * @return Point2D Array containing all the points from the image with the
     * origin set as the upper left corner of the image.
     */
    public Point2D[] getPointsOnImage(int imgnb) {
        int nbpoints = getNumberOfPoints();
        Point2D[] tmp = new Point2D[nbpoints];
        for (int i = 0; i < nbpoints; i++) {
            tmp[i] = getPoint(i, imgnb);
        }
        return tmp;
    }

    /**
     * Get all the 2D points from the image which the index is given as
     * argument. The points coordinates are returned with the center of the
     * image as origin.
     * @param imgnb Image index number.
     * @return Point2D Array containing all the points from the image with the
     * origin set as the center of the image.
     */
    public Point2D[] getCenteredPointsOnImage(int imgnb) {
        int nbpoints = getNumberOfPoints();
        Point2D[] tmp = new Point2D[nbpoints];
        for (int i = 0; i < nbpoints; i++) {
            Point2D p = getPoint(i, imgnb);
            if (p != null)
                tmp[i] = new Point2D.Double(p.getX() - ts.getProjectionCenterX(), p.getY() - ts.getProjectionCenterY());
            else tmp[i] = null;
        }
        return tmp;
    }

    /**
     * @return the number of chains
     */
    public int getNumberOfPoints() {
        return landmarks.size();
    }


    public Point2D getPoint(int index, int imgnb) {
        Point2D[] tmp = landmarks.get(index);
        return tmp[imgnb];
    }

    public void updatePoint(int imgnb, PointRoi roi) {
        FloatPolygon fp = roi.getFloatPolygon();
        //Rectangle rec = roi.getBounds();
        //System.out.println("update points: bounds "+rec.getX()+", "+rec.getY());
        Point2D tmp = new Point2D.Double(fp.xpoints[0] - ts.getCenterX(), fp.ypoints[0] - ts.getCenterX());
        AffineTransform T;
        try {
            T = ts.getAlignment().getTransform(imgnb).createInverse();
        } catch (Exception e) {
            System.out.println("error in inversion " + e);
            return;
        }
        //AffineTransform T=ts.getCombinedInverseTransform(imgnb);
        Point2D res = T.transform(tmp, null);
        Point2D fin = new Point2D.Double(res.getX() + ts.getCenterX(), res.getY() + ts.getCenterY());
        //System.out.println("tmp "+tmp.getX()+", "+tmp.getY());
        //System.out.println("res "+res.getX()+", "+res.getY());
        //System.out.println("fin "+fin.getX()+", "+fin.getY());
        setPoint(currentIndex, imgnb, fin);
    }

    public void setPoint(int index, int imgnb, Point2D pt) {
        setPoint(index, imgnb, pt, automaticGeneration.get(index));
    }

    public void setPoint(int index, int imgnb, Point2D pt, boolean automaticallyGenerated) {
        Point2D[] tmp = landmarks.get(index);
        if (tmp[imgnb] == null && pt != null) {
            automaticGeneration.set(index, automaticallyGenerated);
            tmp[imgnb] = pt;
            //System.out.println("create marker "+index+" on image "+imgnb+" auto="+automaticGeneration.get(index)+" : "+pt);
            //System.out.println("total number of markers "+landmarks.size()+" auto size "+automaticGeneration.size());
        } else if (pt == null) {
            tmp[imgnb] = pt;
            //System.out.println("remove marker "+index+" on image "+imgnb);
        } else if (tmp[imgnb] != null && (tmp[imgnb].getX() != pt.getX() || tmp[imgnb].getY() != pt.getY())) {
            tmp[imgnb] = pt;
            //System.out.println("change marker "+index+" on image "+imgnb+" auto="+automaticGeneration.get(index));
        }
    }

    /**
     * get Point centered with a scaling factor
     *
     * @param index point number
     * @param imgnb image number
     * @param scale scaling factor
     * @return point centered with scaling
     */
    public Point2D getCenteredPointWithScale(int index, int imgnb, double scale) {
        Point2D tmp = landmarks.get(index)[imgnb];
        if (tmp != null)
            return new Point2D.Double((tmp.getX() - ts.getCenterX()) * scale, (tmp.getY() - ts.getCenterY()) * scale);

        return null;

    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        currentIndex = index;
    }

    public void updateRoiOnTiltSeries() {
        ts.setRoi(getRoi());
    }

    public Roi getRoi() {
        return getRoi(ts.getCurrentSlice() - 1);
    }

    public Roi getRoi(int imgnb) {
        AffineTransform T = ts.getAlignment().getTransform(imgnb);
        PointRoi roi = null;

        if (currentIndex >= 0) {
            if (showAll) {
                if (showCommon && commonLandmarks != null) {
                    //System.out.println("common landmarks : "+commonLandmarks.size());
//                    for (int i:commonLandmarks){
                    for (int i : commonLandmarks.keySet()) {
                        Point2D pt = getCenteredPoint(i, imgnb);
                        if (pt != null) {
                            Point2D ptT = T.transform(pt, null);
                            double x = ptT.getX() + ts.getCenterX();
                            double y = ptT.getY() + ts.getCenterY();
                            if (x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
                                if (roi == null) {
                                    roi = new PointRoi(x, y);
                                } else {
                                    roi = roi.addPoint(x, y);
                                }
                            }
                        }
                    }

                } else {
                    for (int i = 0; i < landmarks.size(); i++) {
                        Point2D pt = getCenteredPoint(i, imgnb);
                        if (pt != null) {
                            Point2D ptT = T.transform(pt, null);
                            double x = ptT.getX() + ts.getCenterX();
                            double y = ptT.getY() + ts.getCenterY();
                            if (x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
                                if (roi == null) {
                                    roi = new PointRoi(x, y);
                                } else {
                                    roi = roi.addPoint(x, y);
                                }
                            }
                        }
                    }
                }
            } else {
                Point2D pt = getCenteredPoint(currentIndex, imgnb);
                //System.out.println("tp.getRoi: pt="+pt);
                if (pt != null) {
                    Point2D ptT = T.transform(pt, null);
                    double x = ptT.getX() + ts.getCenterX();
                    double y = ptT.getY() + ts.getCenterY();
                    //System.out.println("tp.getRoi: ptT="+ptT+" final x="+x+" y="+y);
                    if (x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
                        roi = new PointRoi(x, y);
                    }
                    Point2D reprojected = (reprojectedLandmarks == null || reprojectedLandmarks.size() != landmarks.size()) ? null : reprojectedLandmarks.get(currentIndex)[imgnb];
                    //System.out.println("landmark "+pt+" reprojected 3D landmark "+reprojected);
                    //System.out.println("ori on image "+x+", "+y);

                    if (reprojected != null) {
                        System.out.print("landmark (" + x + ", " + y + ") ");
                        Point2D reproT = T.transform(reprojected, null);
                        x = reproT.getX() + ts.getCenterX();
                        y = reproT.getY() + ts.getCenterY();
                        System.out.println("reprojected (" + x + ", " + y + ")");
                        if (roi != null && x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
                            roi = roi.addPoint(x, y);
                        }
                        //System.out.println("final roi "+roi);
                    }
                }
            }
        }
        //System.out.println("tp.getRoi: roi="+roi);
        if(roi!=null) {
            roi.setPointType((int) Prefs.get("point.type", 3));
            roi.setSize((int) Prefs.get("point.size", 2));
        }
        return roi;
    }

    public Point2D getCenteredPoint(int index, int imgnb) {
        Point2D tmp = landmarks.get(index)[imgnb];
        if (tmp != null)
            return new Point2D.Double(tmp.getX() - ts.getCenterX(), tmp.getY() - ts.getCenterY());

        return null;
    }

    public void showAll(boolean show) {
        showAll = show;
    }

    public void showCommon(boolean show) {
        if (commonLandmarks != null) showCommon = show;
    }

    /*public boolean checkQualityOfLandmarks(Point2D.Double[] chain, int localSize, double corrThreshold, int seqLength, int maxStep) {
        //check that all images in the stack correlate well with the average of all pieces
        //compute sum
        int halfSize = localSize / 2;
        ts.setAlignmentRoi(localSize, localSize);
        float[] sum = new float[localSize * localSize];
        int compt = 0;
        double avgscore = 0;
        for (int i = 0; i < chain.length; i++) {
            if (chain[i] != null) {
                float[] tmp = ts.getPixelsForAlignment(i, chain[i]);
                for (int j = 0; j < sum.length; j++) {
                    sum[j] += tmp[j];
                }
                compt++;
            }
        }
        for (int j = 0; j < sum.length; j++) {
            sum[j] /= compt;
        }
        //compute correlation with average
        for (int n = 0; n < chain.length; n++) {
            if (chain[n] != null) {
                float[] tmp = ts.getPixelsForAlignment(n, chain[n]);
                double score = correlation(tmp, sum);
                if (showInIJ) IJ.log("correlation with average img " + n + " score:" + score);
                if (score > corrThreshold) {
                    avgscore += score;
                } else {
                    //chain[n]=null;
                    //IJ.write("remove point "+n+" score with average="+score);
                }
            }
        }

        ts.applyTransforms(true);
        int chainlength = getLandmarkLength(chain);
        if (showInIJ) IJ.log("chain length " + chainlength);
        if (chainlength > seqLength) return true;
        return false;
    }     */


    public static int getLandmarkLength(Point2D[] landmark) {
        int compt = 0;
        for (int i = 0; i < landmark.length; i++) {
            if (landmark[i] != null) compt++;
        }
        return compt;
    }

    public String getChainsInfo() {
        String info = "";
        int number = getNumberOfPoints();
        double avglength = 0;
        int minLength = ts.getImageStackSize();
        int maxLength = 0;
        for (int l = number - 1; l >= 0; l--) {
            int length = TomoJPoints.getLandmarkLength(getPoints(l));
            if (length == 0) {
                removeSetOfPoints(l);
            } else {
                avglength += length;
                if (minLength > length) {
                    minLength = length;
                }
                if (maxLength < length) maxLength = length;
            }
        }
        avglength /= getNumberOfPoints();
        info += ("nb landmarks = " + number + "\nlandmarks are seen on " + avglength + " images" + " (min: " + minLength + ", max: " + maxLength + ")\n");

        return info;
    }

    public void displayChainsInfo() {
        System.out.println(getChainsInfo());
    }

    /**
     * outputs some statistics for each chains
     * for each landmarks chains it gives [first index, last index, chain size, number of points in chain, number of jumps of 1, ...., number of jump of size maxJump]
     * @param maxJump
     * @return
     */

    public ArrayList<int[]> getChainsStatistics(int maxJump){
        ArrayList<int[]> result=new ArrayList<int[]>(landmarks.size());
        for(int j=0; j<landmarks.size();j++){
            int[] stat= new int[4+maxJump];
            Point2D[] chain=landmarks.get(j);
            int first=-1;
            int last = -1;
            int jump = 0;
            int nbpoints=0;
            for(int i=0;i<chain.length;i++){
                if(chain[i]!=null){
                    if(first<0) first=i;
                    last=i;
                    nbpoints++;
                    if(jump>0) {
                        stat[3+Math.min(jump,maxJump)]++;
                        jump=0;
                    }
                }else{
                    if(first>=0){
                        jump++;
                    }
                }
            }
            stat[0]=first;
            stat[1]=last;
            stat[2]=last-first+1; //length
            stat[3]=nbpoints;
            result.add(stat);
        }
        return result;
    }

    public void fastAlign() {
        for (int i = 0; i < ts.getImageStackSize() - 1; i++) {
            double moyTx = 0;
            double moyTy = 0;
            int nb = 0;
            for (Point2D[] landmark : landmarks) {
                if (landmark[i] != null && landmark[i + 1] != null) {
                    moyTx += landmark[i + 1].getX() - landmark[i].getX();
                    moyTy += landmark[i + 1].getY() - landmark[i].getY();
                    nb++;
                }
            }
            if (nb == 0) nb = 1;
            moyTx /= nb;
            moyTy /= nb;
            if(ts.getAlignment() instanceof AffineAlignment) ((AffineAlignment)ts.getAlignment()).setTransform(i, new AffineTransform(0,0,0,0,moyTx, moyTy));
            else{
                AffineAlignment ali=new AffineAlignment(ts);
                ali.setTransform(i, new AffineTransform(0,0,0,0,moyTx, moyTy));
                ts.setAlignment(ali);
            }
            IJ.showProgress(i / (ts.getImageStackSize() - 1.0));
        }
    }
    /* moved to CenterLandmarks class
    public void centerLandmarks() {
         if(!(ts.getAlignment() instanceof AffineAlignment)) ts.setAlignment(new AffineAlignment(ts));
         AffineAlignment alignment=(AffineAlignment)ts.getAlignment();

        int nbpoints = getNumberOfPoints();
        //0� centering
        AffineTransform Tmp = new AffineTransform();
        double avgx = 0;
        double avgy = 0;
        int nb = 0;
        Point2D tmp;
        //System.out.println("nb points="+nbpoints);
        for (int i = 0; i < nbpoints; i++) {
            tmp = landmarks.get(i)[ts.getZeroIndex()];
            //System.out.println("i="+i+" tmp="+tmp);
            if (tmp != null) {
                avgx += tmp.getX();
                avgy += tmp.getY();
                nb++;
            }
        }
        //System.out.println("avgx="+avgx+" avgy="+avgy+" nb="+nb);
        if (nb != 0) {
            avgx /= nb;
            avgy /= nb;
            Tmp.translate(-avgx + ts.getCenterX(), -avgy + ts.getCenterY());
            ts.getAlignment().setZeroTransform(Tmp);
        }
//forward
        for (int imgnb = ts.getZeroIndex() + 1; imgnb < ts.getStackSize(); imgnb++) {
            avgx = 0;
            avgy = 0;
            nb = 0;
            //System.out.println("nb points="+nbpoints);
            for (int i = 0; i < nbpoints; i++) {
                tmp = landmarks.get(i)[imgnb];
                //System.out.println("i="+i+" tmp="+tmp);
                if (tmp != null) {
                    avgx += tmp.getX();
                    avgy += tmp.getY();
                    nb++;
                }
            }
            //System.out.println("avgx="+avgx+" avgy="+avgy+" nb="+nb);
            if (nb != 0) {
                avgx /= nb;
                avgy /= nb;
                try {
                    Tmp = new AffineTransform();
                    Tmp.translate(avgx - ts.getCenterX(), avgy - ts.getCenterY());
                    Tmp.createInverse();
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    AffineTransform T0 = alignment.getTransform(imgnb - 1);
                    //System.out.println("transform 0 on img "+imgnb+ " "+T0);
                    Tmp.concatenate(T0);
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    alignment.setTransform(imgnb - 1, Tmp);
                } catch (Exception e) {
                    System.out.println("error " + e);
                }
            }
        }

        //backward
        for (int imgnb = ts.getZeroIndex() - 1; imgnb >= 0; imgnb--) {
            avgx = 0;
            avgy = 0;
            nb = 0;
            //System.out.println("nb points="+nbpoints);
            for (int i = 0; i < nbpoints; i++) {
                tmp = landmarks.get(i)[imgnb];
                //System.out.println("i="+i+" tmp="+tmp);
                if (tmp != null) {
                    avgx += tmp.getX();
                    avgy += tmp.getY();
                    nb++;
                }
            }
            //System.out.println("avgx="+avgx+" avgy="+avgy+" nb="+nb);
            if (nb != 0) {
                avgx /= nb;
                avgy /= nb;
                try {
                    Tmp = new AffineTransform();
                    Tmp.translate(-avgx + ts.getCenterX(), -avgy + ts.getCenterY());
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    AffineTransform T0 = alignment.getTransform(imgnb + 1).createInverse();
                    //System.out.println("transform 0 on img "+imgnb+ " "+T0);
                    Tmp.preConcatenate(T0);
                    //System.out.println("transform on img "+imgnb+ " "+Tmp);
                    alignment.setTransform(imgnb, Tmp);
                } catch (Exception e) {
                    System.out.println("" + e);
                }

            }
        }
    }
      */

    public void refineLandmarksToLocalCritical(final double binningSize, final boolean useMinima) {
        int bin2 = (int) (binningSize / 2);
        int Nwidth = bin2 * 2 + 1;
        float[] kernel = new float[Nwidth];
        Arrays.fill(kernel, 1);
        float bestValue;
        int bestX;
        int bestY;
        nbThreads = Prefs.getThreads();
        exec = Executors.newFixedThreadPool(nbThreads);
        System.out.println("first recenter to local " + ((useMinima) ? "minima" : "maxima"));
        for (int index = 0; index < ts.getStackSize(); index++) {
            System.out.println("image " + index);
            float[] pixs = ts.getOriginalPixels(index);
            FloatProcessor fp = new FloatProcessor(ts.getWidth(), ts.getHeight(), pixs);
            //fp.convolve(kernel,Nwidth,Nwidth);
            //System.out.println("filtered");
            for (Point2D[] chain : landmarks) {
                Point2D p = chain[index];
                if (p != null) {
                    //remove the centering due to binning
                    int x = (int) (p.getX() - (binningSize) / 2);
                    int y = (int) (p.getY() - binningSize / 2);
                    bestValue = pixs[y * ts.getWidth() + x];
                    bestX = x;
                    bestY = y;
                    for (int j = y; j < (int) (y + binningSize); j++) {
                        for (int i = x; i < (int) (x + binningSize); i++) {
                            float score = fp.getPixelValue(i, j);

                            if (useMinima) {
                                if (score <= bestValue) {
                                    bestValue = score;
                                    bestX = i;
                                    bestY = j;
                                }
                            } else {
                                if (score >= bestValue) {
                                    bestValue = score;
                                    bestX = i;
                                    bestY = j;
                                }
                            }
                        }
                    }
                    p.setLocation(bestX, bestY);
                }
            }
        }

    }


    /**
     * test to know if the landmark is inside the image
     * in fact it is computing inside the borders given by halfSize
     *
     * @param landmark the landmark to test
     * @param halfSize half the size of the image (for example it can be 90% of the true halfsize of images of the tilt series
     * @return true if landmark is inside the limits
     */
    public boolean isInsideImage(Point2D landmark, int halfSize) {
        //System.out.println("centerx:"+ts.getCenterX()+" , centery:"+ts.getCenterY());
        double cx = ts.getCenterX();
        double cy = ts.getCenterY();
        if ((landmark.getX() < -cx + halfSize) || (landmark.getX() > cx - halfSize) ||
                (landmark.getY() < -cy + halfSize) || (landmark.getY() > cy - halfSize)
        ) {
            //System.out.println("near a border");
            return false;
        }
        return true;
    }

    public static ArrayList<Point2D[]> tryToFuseLandmarks(ArrayList<Point2D[]> chains, double dstThreshold) {
        ArrayList<Point2D[]> res = new ArrayList<Point2D[]>();
        boolean[] fused = new boolean[chains.size()];
        for (int i = 0; i < chains.size() - 1; i++) {
            //si n'a pas deja ete fusionne
            Point2D[] li = chains.get(i);
            if (!fused[i]) {
                int compt = 0;
                for (int j = i + 1; j < chains.size(); j++) {
                    //si n'a pas deja ete fusionne
                    if (!fused[j]) {
                        Point2D[] lj = chains.get(j);
                        int nbIdem = 0;
                        //check if distance� <=2
                        for (int k = 0; k < li.length; k++) {
                            if (li[k] != null && lj[k] != null) {
                                //double d=(li[k].getX()-lj[k].getX())*(li[k].getX()-lj[k].getX())+(li[k].getY()-lj[k].getY())*(li[k].getY()-lj[k].getY());
                                if (li[k].distanceSq(lj[k]) <= dstThreshold) {
                                    nbIdem++;
                                }

                            }
                        }
                        if (nbIdem > 3) {
                            compt++;
                            fused[i] = true;
                            fused[j] = true;
                            li = fuse2Landmarks(li, lj, compt);
                        }
                    }
                }
                res.add(li);
            }


        }

        return res;
    }

    protected static Point2D[] fuse2Landmarks(Point2D[] l1, Point2D[] l2, int compt) {
        //compt=1;
        //System.out.println("before fusion: ");
        //System.out.println("l1: "+landmark2String(l1));
        //System.out.println("l2: "+landmark2String(l2));
        Point2D[] res = new Point2D[l1.length];

        for (int i = 0; i < res.length; i++) {
            if (l1[i] != null && l2[i] == null) res[i] = l1[i];
            if (l1[i] == null && l2[i] != null) res[i] = l2[i];
            if (l1[i] != null && l2[i] != null)
                res[i] = new Point2D.Double((l1[i].getX() * compt + l2[i].getX()) / (compt + 1), (l1[i].getY() * compt + l2[i].getY()) / (compt + 1));
            // if(l1[i]!=null && l2[i]!=null)  res[i]=l1[i];
            //if(l1[i]!=null && l2[i]!=null)  res[i]=new Point2D.Double((l1[i].getX()+l2[i].getX())/2,(l1[i].getY()+l2[i].getY())/2);
        }

        // System.out.println("res: "+landmark2String(l2));
        return res;
    }

    protected static Point2D[] fuseLandmarks(ArrayList<Point2D[]> landmarks) {
        Point2D[] res = new Point2D[landmarks.get(0).length];
        for (int i = 0; i < res.length; i++) {
            Point2D sum = null;
            int total = 0;
            for (int j = 0; j < landmarks.size(); j++) {
                Point2D ptmp = landmarks.get(0)[i];
                if (ptmp != null) {
                    if (sum == null) sum = new Point2D.Double(ptmp.getX(), ptmp.getY());
                    else sum = new Point2D.Double(sum.getX() + ptmp.getX(), sum.getY() + ptmp.getY());
                    total++;
                }
            }
            if (total > 0) {
                sum = new Point2D.Double(sum.getX() / total, sum.getY() / total);
            }
            res[i] = sum;
        }
        return res;

    }

/*
    public Map<InterestPoint, InterestPoint> getCommonPointsMapping(int index1, int index2, int octave, int layer, double threshold, int step, boolean fastSURF) {
        IntegralImage img1 = new IntegralImage(ts.getImageStack().getProcessor(index1 + 1), false);
        IntegralImage img2 = new IntegralImage(ts.getImageStack().getProcessor(index2 + 1), false);

        Params pparams = new Params(octave, layer, (float) threshold, step, fastSURF, false, false, 1, false);//octaves, layers, threshold, initialStep, upright, ...
        Params params = new Params(pparams);


        java.util.List<InterestPoint> pipoints = detectAndDescribeInterestPoints(img1, pparams);
        java.util.List<InterestPoint> ipoints = detectAndDescribeInterestPoints(img2, params);


        Map<InterestPoint, InterestPoint> matchedpoints = Matcher.findMathes(pipoints, ipoints);
        Map<InterestPoint, InterestPoint> revmatchedpoints = Matcher.findMathes(ipoints, pipoints);
        // take only those points that matched in the reverse comparison too
        Map<InterestPoint, InterestPoint> commonpoints = new HashMap<InterestPoint, InterestPoint>();
        for (InterestPoint ppoint : matchedpoints.keySet()) {
            InterestPoint point = matchedpoints.get(ppoint);
            if (ppoint == revmatchedpoints.get(point))
                commonpoints.put(ppoint, point);
        }

        return commonpoints;
    }
*/

    public String landmark2String(Point2D[] l) {
        String res = "[" + l[0];
        for (int i = 1; i < l.length; i++) res += " " + l[i];
        return res + "]";
    }

    public String landmark2StringUncentered(Point2D[] l) {
        String res = "[";
        for (int i = 0; i < l.length; i++) {
            if (l[i] != null)
                res += " [" + (l[i].getX() + ts.getCenterX()) + ", " + (l[i].getY() + ts.getCenterY()) + "]";
            else res += " null";
        }
        return res + "]";
    }


}
