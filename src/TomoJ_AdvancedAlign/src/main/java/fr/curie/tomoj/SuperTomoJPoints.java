package fr.curie.tomoj;

import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.align.CenterLandmarks;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

/**
 * Stores multiple TomoJPoints for multiple tilt series alignment and
 * reconstruction.
 *
 * Warning : for now this only works with two TomoJPoints.
 *
 * @author Antoine Cossa
 * Date: 27/03/2019
 */
public class SuperTomoJPoints {

    /**
     * List of all TomoJPoints
     */
    private ArrayList<TomoJPoints> tpList;
    /**
     * Number of images in the i-th TiltSeries
     */
    private ArrayList<Integer> nImagesi;
    /**
     * Total number of images
     */
    private int nImages;
    /**
     * Number of landmarks chains in the i-th TomoJPoints
     */
    private ArrayList<Integer> nLandmarksi;
    /**
     * Total number of landmarks chains (w/ common landmarks counted just once)
     */
    private int nLandmarks;
    /**
     * Contains all the landmarks chains (with the ones in common fused)
     * The length of each Point2D[] is equal to the total number of images
     * (nImages).
     */
    private ArrayList<Point2D[]> landmarks = new ArrayList<>();

    private ArrayList<Point2D[]> reprojectedLandmarks = null;
//    private ArrayList<Boolean> automaticGeneration = new ArrayList<Boolean>();

    /**
     * If true, fuse landmarks chains between series. Default: true.
     */
    private boolean fuseLandmarks = true;

    // TODO remove this
    private TiltSeries ts;

//    Landmarks3DAlign landmarks3D;
//    Landmarks3DDualAlign landmarks3DDual;

    public SuperTomoJPoints(ArrayList<TomoJPoints> tpList, boolean fuseLandmarks) {
        this.tpList = tpList;
        this.fuseLandmarks = fuseLandmarks;
        init();
    }

    public void init() {
        nImages = 0;
        nImagesi = new ArrayList<>(tpList.size());
        nLandmarks = 0;
        nLandmarksi = new ArrayList<>(tpList.size());

        for (TomoJPoints tp : tpList) {
            // Gets the number of image in the TiltSeries associated with this
            // TomoJPoints and adds it to the total number of images.
            int n = tp.getTiltSeries().getStackSize();
            nImages += n;
            nImagesi.add(n);
        }

        if (fuseLandmarks) {
            fuseLandmarks();
        }
    }

    public void fuseLandmarks() {
        // Remove all existing landmarks from SuperTomoJPoints
        landmarks.clear();

        for (int j = 0; j < tpList.size() - 1; j++) {
            TomoJPoints tp1 = tpList.get(j);
            TomoJPoints tp2 = tpList.get(j + 1);

            // Get common landmarks
            Hashtable<Integer, Integer> commons = tp1.getCommonLandmarksIndex();

            tp1.removeEmptyChains();
            tp2.removeEmptyChains();

            ArrayList<Point2D[]> lm1 = tp1.getAllLandmarks();
            ArrayList<Point2D[]> lm2 = tp2.getAllLandmarks();

            // Stores the number of landmarks chains of each TP
            nLandmarksi.add(lm1.size());
            if (j == tpList.size() - 2) {
                nLandmarksi.add(lm2.size());
            }

            // Add landmarks from TP1 and fuse common landmarks with TP2
            for (int i = 0; i < lm1.size(); i++) { // for each landmarks chain of TP1
                // Create new landmarks chain p
                Point2D[] p = new Point2D[nImages];
                // Copy landmarks from TP1 to p
                System.arraycopy(lm1.get(i), 0, p, 0, nImagesi.get(j));

                if (commons != null) {
                    if (commons.get(i) != null) {
                        // Concatenate the Point2D[] in common after the previous one
                        System.arraycopy(lm2.get(commons.get(i)), 0, p, nImagesi.get(j), nImagesi.get(j + 1));
                        nLandmarksi.set(1, nLandmarksi.get(1) - 1);
                    }
                }
                // Adds this new Point2D[] to the global landmarks chains list
                landmarks.add(p);
            }
            // Add proper landmarks from TP2 which have not been previously added
            for (int i = 0; i < lm2.size(); i++) {
                Point2D[] p = new Point2D[nImages];
                if (tp2.getProperLandmarks() != null) {
                    if (tp2.isProperLandmark(i)) {
                        System.arraycopy(lm2.get(i), 0, p, nImagesi.get(j), nImagesi.get(j));
                        landmarks.add(p);
                    }
                }
            }
        }
    }

    public ArrayList<Point2D[]> getAllLandmarks() {
        return landmarks;
    }

    /**
     * @param index
     * @return the number of landmarks chains from the TP index given as argument
     */
    public int getnLandmarksi(int index) {
        return nLandmarksi.get(index);
    }

    /**
     * @param index number of the TiltSeries/TomoJPoints
     * @return the number of images in the TS/TP corresponding to the index
     * given as argument.
     */
    public int getNumberOfImages(int index) {
        return nImagesi.get(index);
    }

    /**
     * @return the total number of images in this SuperTomoJPoints
     */
    public int getTotalNumberOfImages() {
        return nImages;
    }

    /**
     * @param index of the TomoJPoint (starting at 0)
     * @return the number of images in the corresponding TomoJPoint
     */
    public int getnImagesi(int index) {
        return nImagesi.get(index);
    }

    /**
     * @param index Warning: index starts at zero.
     * @return the i-th TomoJPoints.
     */
    public TomoJPoints getTomoJPoints(int index) {
        return tpList.get(index);
    }

    /**
     * @return the number of TomoJPoits in this SuperTomoJPoints
     */
    public int getSize() {
        return tpList.size();
    }

    /**
     * @return an array containing all the TomoJPoints of this SuperTomoJPoints
     */
    public ArrayList<TomoJPoints> getAllTP() {
        return tpList;
    }


//    public Landmarks3DAlign getLandmarks3D() {
//        return landmarks3D;
//    }
//
//    public void setLandmarks3D(Landmarks3DAlign landmarks3D) {
//        this.landmarks3D = landmarks3D;
//    }
//
//    /**
//     * TEST for Dual Tilt by Antoine
//     * @param landmarks3DDual
//     */
//    public void setLandmarks3DDual(Landmarks3DDualAlign landmarks3DDual) {
//        this.landmarks3DDual = landmarks3DDual;
//    }
//
//    public void setReprojectedLandmarks(ArrayList<Point2D[]> reprojected){
//        reprojectedLandmarks=reprojected;
//    }
//
//    public ArrayList<Point2D[]> getReprojectedLandmarks(){
//        return reprojectedLandmarks;
//    }
//
//

    /**
     * (Not implemented for more than two tilt axis yet)
     *
     * @param index
     * @param imgnb
     * @return
     */
    public Point2D getCenteredPoint(int index, int imgnb) {
        Point2D tmp = landmarks.get(index)[imgnb];
        if (tmp != null)
            if (imgnb < nImagesi.get(0)) {
                return new Point2D.Double(tmp.getX() - getTomoJPoints(0).getTiltSeries().getCenterX(), tmp.getY() - getTomoJPoints(0).getTiltSeries().getCenterY());
            } else {
                return new Point2D.Double(tmp.getX() - getTomoJPoints(1).getTiltSeries().getCenterX(), tmp.getY() - getTomoJPoints(1).getTiltSeries().getCenterY());
            }

        return null;
    }

    /**
     * Get all the 2D points from the image which the index is given as
     * argument. The points coordinates are returned with the upper left corner
     * of the image as origin.
     *
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

//    /**
//     * Get all the 2D points from the image which the index is given as
//     * argument. The points coordinates are returned with the center of the
//     * image as origin.
//     *
//     * @param imgnb Image index number.
//     * @return Point2D Array containing all the points from the image with the
//     * origin set as the center of the image.
//     */
//    public Point2D[] getCenteredPointsOnImage(int imgnb) {
//        int nbpoints = getNumberOfPoints();
//        Point2D[] tmp = new Point2D[nbpoints];
//        for (int i = 0; i < nbpoints; i++) {
//            Point2D p = getPoint(i, imgnb);
//            if (p != null)
//                tmp[i] = new Point2D.Double(p.getX() - ts.getProjectionCenterX(), p.getY() - ts.getProjectionCenterY());
//            else tmp[i] = null;
//        }
//        return tmp;
//    }


    /**
     * Remove the landmarks chain at index number
     *
     * @param index
     * @return 0 if all went well
     */
    public int removeSetOfPoints(int index) {
//        // Remove points in the original TomoJPoints
        if (index < nLandmarksi.get(0)) {
//            getTomoJPoints(0).removeSetOfPoints(index);
//            // If the landmarks chain is common between series, also remove the
//            // corresponding one from the second TP.
//            if (getTomoJPoints(0).getCommonLandmarksIndex().get(index) != null) {
//                getTomoJPoints(1).removeSetOfPoints(getTomoJPoints(0).getCommonLandmarksIndex().get(index));
//            }
//            if (getTomoJPoints(0).getReprojectedLandmarks() != null) {
//                getTomoJPoints(0).getReprojectedLandmarks().remove(index);
            nLandmarksi.set(0, nLandmarksi.get(0) - 1); // update counter
//            }
        } else {
//            getTomoJPoints(1).removeSetOfPoints(index - nLandmarksi.get(0));
//            if (getTomoJPoints(1).getReprojectedLandmarks() != null) {
//                getTomoJPoints(1).getReprojectedLandmarks().remove(index - nLandmarksi.get(0));
            nLandmarksi.set(1, nLandmarksi.get(1) - 1); // update counter
//            }
        }
//
        // Remove points in the SuperTomoJPoint
        landmarks.remove(index);
//        if (index < nLandmarksi.get(0)) {
//            nLandmarksi.set(0, nLandmarksi.get(0) - 1);
//        } else {
//            nLandmarksi.set(1, nLandmarksi.get(1) - 1);
//        }
        if (reprojectedLandmarks != null) reprojectedLandmarks.remove(index);

//        // if landmarks chain is common between series, remove it
//        if (getTomoJPoints(0).getCommonLandmarksIndex().get(index) != null) {
//            int index2 = getTomoJPoints(0).getCommonLandmarksIndex().get(index);
//            getTomoJPoints(0).removeCommonLandmarks(index);
//            getTomoJPoints(1).removeCommonLandmarks(index2);
//        }


//        automaticGeneration.remove(index);

//        if (currentIndex > index) currentIndex--;
//        return currentIndex;

        return 0;
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

    public void centerLandmarks() {
        for (TomoJPoints tp : tpList) {
            CenterLandmarks.centerLandmarks(tp);
        }
    }




    public void setAllLandmarks(ArrayList<Point2D[]> landmarks) {
        this.landmarks = landmarks;
//        currentIndex = landmarks.size()-1;
//        this.automaticGeneration.clear();
//        for(int i=0;i<landmarks.size();i++) this.automaticGeneration.add(automaticGeneration);
        reprojectedLandmarks = null;
    }

//    public ArrayList<Boolean> getAllAutomaticGeneration(){
//        return automaticGeneration;
//    }
//
//    public int addNewSetOfPoints() {
//        return addNewSetOfPoints(false);
//    }
//
//    public int addNewSetOfPoints(boolean automaticalyGenerated) {
//        addSetOfPoints(new Point2D[ts.getStackSize()], automaticalyGenerated);
//        currentIndex = landmarks.size() - 1;
//        return currentIndex;
//    }
//
//    public void addSetOfPoints(Point2D[] pts, boolean automaticallyGenerated) {
//        landmarks.add(pts);
//        automaticGeneration.add(automaticallyGenerated);
//    }
//    public void addSetOfPointsSynchronized(Point2D[] pts, boolean automaticallyGenerated) {
//        synchronized (landmarks) {landmarks.add(pts);}
//        synchronized (automaticGeneration) {automaticGeneration.add(automaticallyGenerated);}
//    }
//
//    public void addSetOfPoints(ArrayList<Point2D[]> points,boolean automaticallyGenerated){
//        for(Point2D[] pts:points) addSetOfPoints(pts,automaticallyGenerated);
//    }
//
//    public void addCommonLandmarkIndex(int index){
//        if(commonLandmarks==null) commonLandmarks=new ArrayList<Integer>(landmarks.size());
//        commonLandmarks.add(index);
//    }
//
//    /**
//     * Add common landmarks chain index from two tilt series.
//     * @param index_this This landmarks index.
//     * @param index_other The landmarks index of the other TomoJPoints.
//     */
//    public void addCommonLandmarkIndex(int index_this, int index_other){
//        if (commonLandmarks == null)
//            commonLandmarks = new Hashtable<>(landmarks.size());
//        if (getPoints(index_this) != null)
//            commonLandmarks.put(index_this, index_other);
//    }
//
//    /**
//     * Set common landmarks chain list of indexes from two tilt series.
//     * @param common Hashtable containing indexes of common landmarks chains in
//     *               this TomoJPoints as keys and from the other TomoJPoints as
//     *               values.
//     */
//    public void setCommonLandmarksIndex(Hashtable<Integer, Integer> common) {
//        if (commonLandmarks == null)
//            commonLandmarks = new Hashtable<>(landmarks.size());
//        commonLandmarks = common;
//    }
//
//    public Hashtable<Integer, Integer> getCommonLandmarksIndex() {
//        return commonLandmarks;
//    }
//
//    /**
//     * @param index
//     * @return true if the landmark at this index is proper to this TiltSeries
//     */
//    public boolean isProperLandmark(int index) {
//        return properLandmarks.get(index);
//    }
//
//    /**
//     * Find the landmarks chains only present on this series
//     */
//    public void setProperLandmarksIndex() {
//        if (commonLandmarks != null) {
//            properLandmarks = new ArrayList<>(landmarks.size());
//            for (int i = 0; i < landmarks.size(); i++) {
//                if ((landmarks.get(i) != null) && !commonLandmarks.containsKey(i)) {
//                    properLandmarks.add(i, true);
//                } else {
//                    properLandmarks.add(i, false);
//                }
//            }
//        } else System.out.println("commonLandmarks needs to be set first!");
//    }
//
//    public void setShowIJ(boolean value) {
//        showInIJ = value;
//    }

    public void reset() {
        landmarks.clear();
//        automaticGeneration.clear();
        reprojectedLandmarks = null;
//        addNewSetOfPoints();
    }

//    public void interrupt() {
//        completion = -1000;
//    }
//
//    public double getCompletion() {
//        return completion;
//    }
//
//    public void resetCompletion() {
//        completion = 0;
//    }


//    public void addSetOfPoints(Point2D[] pts) {
//        addSetOfPoints(pts, false);
//    }

//    public int removeCurrentSetOfPoints() {
//        removeSetOfPoints(currentIndex);
//        if (currentIndex >= landmarks.size()) {
//            currentIndex = landmarks.size() - 1;
//        }
//        //bestPreviousAlignmentDeform=null;
//        return currentIndex;
//    }

    public void removeAllSetsOfPoints() {
        landmarks.clear();
        nImagesi.clear();
        nLandmarksi.clear();
        //bestPreviousAlignmentDeform=null;
//        automaticGeneration.clear();
        if (reprojectedLandmarks != null) reprojectedLandmarks = null;
        //bestPreviousAlignmentDeform=null;
//        landmarks3D = null;

        for (TomoJPoints tp : tpList) {
            tp.removeAllSetsOfPoints();
        }
    }

    public void removeImage(int index) {
        for (int i = 0; i < landmarks.size(); i++) {
            Point2D[] tmp = landmarks.get(i);
            Point2D[] res = new Point2D[tmp.length - 1];
            System.arraycopy(tmp, 0, res, 0, index);
            System.arraycopy(tmp, index + 1, res, index, tmp.length - 1 - index);
            landmarks.set(i, res);

            if (index < nImagesi.get(0)) {
                tpList.get(0).removeImage(index);
                nImagesi.set(0, nImagesi.get(0) - 1);
            } else {
                tpList.get(1).removeImage(index - nImagesi.get(0));
                nImagesi.set(1, nImagesi.get(1) - 1);
            }
            nImages--;

        }
    }

    public void setPoints(int index, Point2D[] pts) {
        landmarks.set(index, pts);
    }

//    public Landmarks3DAlign getLandmarks3D() {
//        return landmarks3D;
//    }
//
//    public void setLandmarks3D(Landmarks3DAlign landmarks3D) {
//        this.landmarks3D = landmarks3D;
//    }

//    /**
//     * TEST for Dual Tilt by Antoine
//     * @param landmarks3DDual
//     */
//    public void setLandmarks3DDual(Landmarks3DDualAlign landmarks3DDual) {
//        this.landmarks3DDual = landmarks3DDual;
//    }

    public void setReprojectedLandmarks(ArrayList<Point2D[]> reprojected) {
        reprojectedLandmarks = reprojected;
    }

    public ArrayList<Point2D[]> getReprojectedLandmarks() {
        return reprojectedLandmarks;
    }

//    public boolean isVisible() {
//        return visible;
//    }

//    public void setVisible(boolean val) {
//        visible = val;
//    }


//    public boolean isAutomaticallyGenerated(int index) {
//        return automaticGeneration.get(index);
//    }

//    public int getAutoLength() {
//        return automaticGeneration.size();
//    }


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

    //  TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
//    /**
//     * Get all the 2D points from the image which the index is given as
//     * argument. The points coordinates are returned with the upper left corner
//     * of the image as origin.
//     * @param imgnb Image index number.
//     * @return Point2D Array containing all the points from the image with the
//     *         origin set as the upper left corner of the image.
//     */
//    public Point2D[] getPointsOnImage(int imgnb) {
//        int nbpoints = getNumberOfPoints();
//        Point2D[] tmp = new Point2D[nbpoints];
//        for (int i = 0; i < nbpoints; i++) {
//            tmp[i] = getPoint(i, imgnb);
//        }
//        return tmp;
//    }
//
//    /**
//     * Get all the 2D points from the image which the index is given as
//     * argument. The points coordinates are returned with the center of the
//     * image as origin.
//     * @param imgnb Image index number.
//     * @return Point2D Array containing all the points from the image with the
//     *         origin set as the center of the image.
//     */
//    public Point2D[] getCenteredPointsOnImage(int imgnb) {
//        int nbpoints = getNumberOfPoints();
//        Point2D[] tmp = new Point2D[nbpoints];
//        for (int i = 0; i < nbpoints; i++) {
//            Point2D p = getPoint(i, imgnb);
//            if(p!=null) tmp[i]=new Point2D.Double(p.getX() - ts.getProjectionCenterX(), p.getY() - ts.getProjectionCenterY());
//            else tmp[i]=null;
//        }
//        return tmp;
//    }
//

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
//
//    public void updatePoint(int imgnb, PointRoi roi) {
//        FloatPolygon fp = roi.getFloatPolygon();
//        //Rectangle rec = roi.getBounds();
//        //System.out.println("update points: bounds "+rec.getX()+", "+rec.getY());
//        Point2D tmp = new Point2D.Double(fp.xpoints[0] - ts.getCenterX(), fp.ypoints[0] - ts.getCenterX());
//        AffineTransform T;
//        try {
//            T = ts.getTransform(imgnb).createInverse();
//        } catch (Exception e) {
//            System.out.println("error in inversion " + e);
//            return;
//        }
//        //AffineTransform T=ts.getCombinedInverseTransform(imgnb);
//        Point2D res =  T.transform(tmp, null);
//        Point2D fin = new Point2D.Double(res.getX() + ts.getCenterX(), res.getY() + ts.getCenterY());
//        //System.out.println("tmp "+tmp.getX()+", "+tmp.getY());
//        //System.out.println("res "+res.getX()+", "+res.getY());
//        //System.out.println("fin "+fin.getX()+", "+fin.getY());
//        setPoint(currentIndex, imgnb, fin);
//    }
//
//    public void setPoint(int index, int imgnb, Point2D pt) {
//        setPoint(index, imgnb, pt, automaticGeneration.get(index));
//    }
//
//    public void setPoint(int index, int imgnb, Point2D pt, boolean automaticallyGenerated) {
//        Point2D[] tmp = landmarks.get(index);
//        if (tmp[imgnb] == null && pt != null) {
//            automaticGeneration.set(index, automaticallyGenerated);
//            tmp[imgnb] = pt;
//            //System.out.println("create marker "+index+" on image "+imgnb+" auto="+automaticGeneration.get(index)+" : "+pt);
//            //System.out.println("total number of markers "+landmarks.size()+" auto size "+automaticGeneration.size());
//        } else if (pt == null) {
//            tmp[imgnb] = pt;
//            //System.out.println("remove marker "+index+" on image "+imgnb);
//        } else if (tmp[imgnb] != null && (tmp[imgnb].getX() != pt.getX() || tmp[imgnb].getY() != pt.getY())) {
//            tmp[imgnb] = pt;
//            //System.out.println("change marker "+index+" on image "+imgnb+" auto="+automaticGeneration.get(index));
//        }
//    }
//
    // TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO

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
        if (tmp != null) {
            if (imgnb < nImagesi.get(0)) {
                return new Point2D.Double((tmp.getX() - tpList.get(0).getTiltSeries().getCenterX()) * scale,
                        (tmp.getY() - tpList.get(0).getTiltSeries().getCenterY()) * scale);
            } else {
                return new Point2D.Double((tmp.getX() - tpList.get(1).getTiltSeries().getCenterX()) * scale,
                        (tmp.getY() - tpList.get(1).getTiltSeries().getCenterY()) * scale);
            }
        }
        return null;
    }

//    public int getCurrentIndex() {
//        return currentIndex;
//    }
//
//    public void setCurrentIndex(int index) {
//        currentIndex = index;
//    }
//
//    public void updateRoiOnTiltSeries() {
//        ts.setRoi(getRoi());
//    }

//    public Roi getRoi() {
//        return getRoi(ts.getCurrentSlice() - 1);
//    }
//
//    public Roi getRoi(int imgnb) {
//        AffineTransform T = ts.getTransform(imgnb);
//        PointRoi roi = null;
//        if (currentIndex >= 0) {
//            if (showAll) {
//                if(showCommon&&commonLandmarks!=null){
//                    //System.out.println("common landmarks : "+commonLandmarks.size());
////                    for (int i:commonLandmarks){
//                    for (int i:commonLandmarks.keySet()){
//                        Point2D pt = getCenteredPoint(i, imgnb);
//                        if (pt != null) {
//                            Point2D ptT = T.transform(pt, null);
//                            double x = ptT.getX() + ts.getCenterX();
//                            double y = ptT.getY() + ts.getCenterY();
//                            if (x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
//                                if (roi == null) {
//                                    roi = new PointRoi(x, y);
//                                } else {
//                                    roi = roi.addPoint(x, y);
//                                }
//                            }
//                        }
//                    }
//
//                } else {
//                    for (int i = 0; i < landmarks.size(); i++) {
//                        Point2D pt = getCenteredPoint(i, imgnb);
//                        if (pt != null) {
//                            Point2D ptT = T.transform(pt, null);
//                            double x = ptT.getX() + ts.getCenterX();
//                            double y = ptT.getY() + ts.getCenterY();
//                            if (x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
//                                if (roi == null) {
//                                    roi = new PointRoi(x, y);
//                                } else {
//                                    roi = roi.addPoint(x, y);
//                                }
//                            }
//                        }
//                    }
//                }
//            } else {
//                Point2D pt = getCenteredPoint(currentIndex, imgnb);
//                //System.out.println("tp.getRoi: pt="+pt);
//                if (pt != null) {
//                    Point2D ptT =  T.transform(pt, null);
//                    double x = ptT.getX() + ts.getCenterX();
//                    double y = ptT.getY() + ts.getCenterY();
//                    //System.out.println("tp.getRoi: ptT="+ptT+" final x="+x+" y="+y);
//                    if (x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
//                        roi = new PointRoi(x, y);
//                    }
//                    Point2D reprojected = (reprojectedLandmarks == null || reprojectedLandmarks.size() != landmarks.size()) ? null : reprojectedLandmarks.get(currentIndex)[imgnb];
//                    //System.out.println("landmark "+pt+" reprojected 3D landmark "+reprojected);
//                    //System.out.println("ori on image "+x+", "+y);
//
//                    if (reprojected != null) {
//                        System.out.print("landmark (" + x + ", " + y + ") ");
//                        Point2D reproT = T.transform(reprojected, null);
//                        x = reproT.getX() + ts.getCenterX();
//                        y = reproT.getY() + ts.getCenterY();
//                        System.out.println("reprojected (" + x + ", " + y + ")");
//                        if (roi != null && x > 0 && x < ts.getWidth() && y > 0 && y < ts.getHeight()) {
//                            roi = roi.addPoint(x, y);
//                        }
//                        //System.out.println("final roi "+roi);
//                    }
//                }
//            }
//        }
//        //System.out.println("tp.getRoi: roi="+roi);
//        return roi;
//    }

//    public Point2D getCenteredPoint(int index, int imgnb) {
//        if (imgnb < nImagesi.get(0)) {
//            return tpList.get(0).getCenteredPoint(index, imgnb);
//        } else {
//            return tpList.get(1).getCenteredPoint(
//                    index - nLandmarksi.get(0),
//                    imgnb - nImagesi.get(0)
//            );
//        }
//
//    }

//    public void showAll(boolean show) {
//        showAll = show;
//    }

//    public void showCommon(boolean show){
//        if(commonLandmarks!=null)showCommon=show;
//    }

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


//    public static int getLandmarkLength(Point2D[] landmark) {
//        int compt = 0;
//        for (int i = 0; i < landmark.length; i++) {
//            if (landmark[i] != null) compt++;
//        }
//        return compt;
//    }

    public int[] getLandmarkLength(Point2D[] landmark) {
        int[] compt = new int[getSize()];
        for (int k = 0; k < getSize(); k++) {
            compt[k] = 0;
            if (k == 0) {
                for (int i = 0; i < getnImagesi(k); i++) {
                    if (landmark[i] != null) compt[k]++;
                }
            } else {
                for (int i = getnImagesi(k - 1); i < getnImagesi(k) + getnImagesi(k - 1); i++) {
                    if (landmark[i] != null) compt[k]++;
                }
            }
//            for (int i = (k == 0) ? 0 : getnImagesi(k - 1); i < getnImagesi(k); i++) {
//                if (landmark[i] != null) compt[k]++;
//            }
        }
        return compt;
    }

//    /**
//     * @param index of the TomoJPoints
//     * @return the length of each landmarks chain of the TomoJPoints given as
//     *         argument.
//     */
//    public int[] getLandmarkLengthi(int index) {
//        int[] compt = new int[getnLandmarksi(index)];
//        for (int j = 0; j < getnLandmarksi(index); j++ )  {
//            compt[j] = 0;
//            for (int i = (index == 0) ? 0 : getnImagesi(k - 1); i < getnImagesi(k); i++) {
//                if (landmark[i] != null) compt[k]++;
//            }
//        }
//
//        return compt;
//    }


    public void displayChainsInfo() {
        System.out.println(getChainsInfo());
    }

    private String getChainsInfo() {
        String info = "Before fusion: \n";;
        for (int i = 0; i < getSize(); i++) {
            info += "TiltSeries " + (i + 1) + ":\n";
            info += getTomoJPoints(i).getChainsInfo();
        }

        int nbTP = getSize();
        int number = getNumberOfPoints();
//        double[] avglength = new double[nbTP + ((nbTP * nbTP - nbTP) / 2)]; // (n²-n)/2 --> 2 by 2 comparisons + nb of TP

        double[] avglength = new double[nbTP + 1];
        int[] minLength = new int[nbTP + 1];
        int[] maxLength = new int[nbTP + 1];
        int[] median = new int[nbTP + 1];

        // initialization
        for (int i = 0; i < nbTP + 1; i++) {
            avglength[i] = 0;
            minLength[i] = getNumberOfPoints();
            maxLength[i] = 0;
            median[i] = 0;
        }

        // ArrayLists for median calculation
        ArrayList<Integer> common = new ArrayList<>();
        ArrayList<Integer> med1 = new ArrayList<>();
        ArrayList<Integer> med2 = new ArrayList<>();

        for (int l = number - 1; l >= 0; l--) {
            int[] length = getLandmarkLength(getPoints(l));
            if (length[0] != 0 && length[1] != 0) {
                int len = length[0] + length[1];
                avglength[2] += len;
                common.add(len);
                if (minLength[2] > len) minLength[2] = len;
                if (maxLength[2] < len) maxLength[2] = len;
            } else if (length[0] != 0 && length[1] == 0) {
                avglength[0] += length[0];
                med1.add(length[0]);
                if (minLength[0] > length[0]) minLength[0] = length[0];
                if (maxLength[0] < length[0]) maxLength[0] = length[0];
            } else if (length[0] == 0 && length[1] != 0){ // length[0] == 0 && length[1] == 0
                avglength[1] += length[1];
                med2.add(length[1]);
                if (minLength[1] > length[1]) minLength[1] = length[1];
                if (maxLength[1] < length[1]) maxLength[1] = length[1];
            }
        }

        Collections.sort(common);
        Collections.sort(med1);
        Collections.sort(med2);

        median[0] = (med1.get(med1.size() / 2) + med1.get(med1.size() / 2 - 1)) / 2;
        median[1] = (med2.get(med2.size() / 2) + med2.get(med2.size() / 2 - 1)) / 2;
        median[2] = (common.get(common.size() / 2) + common.get(common.size() / 2 - 1)) / 2;

        info += "\nAfter fusion: \n";
        for (int i = 0; i < nbTP + 1; i++) {
            if (i == 0) {
                avglength[i] /= med1.size();
                info += "TiltSeries" + (i + 1) + ": \n\tnb landmarks = " + med1.size();
            } else if (i == 1) {
                avglength[i] /= med2.size();
                info += "TiltSeries" + (i + 1) + ": \n\tnb landmarks = " + med2.size();
            } else {
                avglength[i] /= common.size();
                info += "Common Landmarks: \n\tnb landmarks = " + common.size();
            }
            info += "\n\tlandmarks are seen on " + avglength[i] + " images (min: " + minLength[i] + ", max: " + maxLength[i] + ", median: " + median[i] +")\n";
        }

        return info;
    }

//    int number = getNumberOfPoints();
//    double avglength = 0;
//    int minLength = getTotalNumberOfImages();
//    int maxLength = 0;
//        for (int l = number - 1; l >= 0; l--) {
//            int length = getLandmarkLength(getPoints(l));
//            if (length == 0) {
//                removeSetOfPoints(l);
//            } else {
//                avglength += length;
//                if (minLength > length) minLength = length;
//                if (maxLength < length) maxLength = length;
//            }
//        }
//        avglength /= getNumberOfPoints();
//        System.out.println("nb landmarks = " + number);
//        System.out.println("landmarks are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");

//    public void fastAlign() {
//        for (int i = 0; i < ts.getImageStackSize() - 1; i++) {
//            double moyTx = 0;
//            double moyTy = 0;
//            int nb = 0;
//            for (Point2D[] landmark : landmarks) {
//                if (landmark[i] != null && landmark[i + 1] != null) {
//                    moyTx += landmark[i + 1].getX() - landmark[i].getX();
//                    moyTy += landmark[i + 1].getY() - landmark[i].getY();
//                    nb++;
//                }
//            }
//            if (nb == 0) nb = 1;
//            moyTx /= nb;
//            moyTy /= nb;
//            ts.setTransform(i, moyTx, moyTy, 0);
//            IJ.showProgress(i / (ts.getImageStackSize() - 1.0));
//        }
//    }


//    public void refineLandmarksToLocalCritical(final double binningSize, final boolean useMinima) {
//        int bin2 = (int) (binningSize / 2);
//        int Nwidth = bin2 * 2 + 1;
//        float[] kernel = new float[Nwidth];
//        Arrays.fill(kernel, 1);
//        float bestValue;
//        int bestX;
//        int bestY;
//        nbThreads = Prefs.getThreads();
//        exec = Executors.newFixedThreadPool(nbThreads);
//        System.out.println("first recenter to local " + ((useMinima) ? "minima" : "maxima"));
//        for (int index = 0; index < ts.getStackSize(); index++) {
//            System.out.println("image " + index);
//            float[] pixs = ts.getPixels(index, false);
//            FloatProcessor fp = new FloatProcessor(ts.getWidth(), ts.getHeight(), pixs);
//            //fp.convolve(kernel,Nwidth,Nwidth);
//            //System.out.println("filtered");
//            for (Point2D[] chain : landmarks) {
//                Point2D p = chain[index];
//                if (p != null) {
//                    //remove the centering due to binning
//                    int x = (int) (p.getX() - (binningSize) / 2);
//                    int y = (int) (p.getY() - binningSize / 2);
//                    bestValue = pixs[y * ts.getWidth() + x];
//                    bestX = x;
//                    bestY = y;
//                    for (int j = y; j < (int) (y + binningSize); j++) {
//                        for (int i = x; i < (int) (x + binningSize); i++) {
//                            float score = fp.getPixelValue(i, j);
//
//                            if (useMinima) {
//                                if (score <= bestValue) {
//                                    bestValue = score;
//                                    bestX = i;
//                                    bestY = j;
//                                }
//                            } else {
//                                if (score >= bestValue) {
//                                    bestValue = score;
//                                    bestX = i;
//                                    bestY = j;
//                                }
//                            }
//                        }
//                    }
//                    p.setLocation(bestX, bestY);
//                }
//            }
//        }
//
//    }


//    /**
//     * test to know if the landmark is inside the image
//     * in fact it is computing inside the borders given by halfSize
//     *
//     * @param landmark the landmark to test
//     * @param halfSize half the size of the image (for example it can be 90% of the true halfsize of images of the tilt series
//     * @return true if landmark is inside the limits
//     */
//    public boolean isInsideImage(Point2D landmark, int halfSize) {
//        //System.out.println("centerx:"+ts.getCenterX()+" , centery:"+ts.getCenterY());
//        double cx = ts.getCenterX();
//        double cy = ts.getCenterY();
//        if ((landmark.getX() < -cx + halfSize) || (landmark.getX() > cx - halfSize) ||
//                (landmark.getY() < -cy + halfSize) || (landmark.getY() > cy - halfSize)
//        ) {
//            //System.out.println("near a border");
//            return false;
//        }
//        return true;
//    }


//    public void loadAlignmentLandmark(String pathToFile) {
//        final alignmentLandmark align = new alignmentLandmark(this);
//        double rot = 0;
//        double tilt = 0;
//        DoubleMatrix1D psiD = DoubleFactory1D.dense.make(ts.getImageStackSize());
//        ArrayList<DoubleMatrix1D> di = new ArrayList<DoubleMatrix1D>(ts.getImageStackSize());
//        for (int i = 0; i < ts.getImageStackSize(); i++) {
//            di.add(DoubleFactory1D.dense.make(2));
//        }
//        try {
//            BufferedReader in = new BufferedReader(new FileReader(pathToFile));
//            String line;
//            while ((line = in.readLine()) != null) {
//                String[] words = line.split(" ");
//                if (words[0].startsWith("rot=")) {
//                    String[] s = words[0].split("=");
//                    rot = Double.parseDouble(s[1]);
//                }
//                if (words[1].startsWith("tilt")) {
//                    String[] s = words[0].split("=");
//                    tilt = Double.parseDouble(s[1]);
//
//                }
//                if (words[0].equalsIgnoreCase("Image") && words.length > 7) {
//                    int index = Integer.parseInt(words[1]);
//                    psiD.setQuick(index, Double.parseDouble(words[3]));
//                    DoubleMatrix1D tmp = di.get(index);
//                    //attention 2 espaces entre di= et les 2 valeurs
//                    tmp.setQuick(0, Double.parseDouble(words[6]));
//                    tmp.setQuick(1, Double.parseDouble(words[7]));
//                }
//            }
//        } catch (IOException ioe) {
//            System.out.println(ioe);
//        }
//        align.setRotation(rot);
//        align.setTilt(tilt);
//        align.setPsi(psiD);
//        align.setDi(di);
//        align.printAlignment();
//
//        //update the alignment in tilt series
//        //update0degree
//
//        ts.setZeroTransform(align.getTransform(ts.getZeroIndex()));
//        //update forward
//        for (int i = ts.getZeroIndex() + 1; i < ts.getImageStackSize(); i++) {
//            try {
//                AffineTransform T = align.getTransform(i).createInverse();
//                AffineTransform T0 = ts.getTransform(i - 1, true, true);
//                T.concatenate(T0);
//                ts.setTransform(i - 1, T);
//            } catch (Exception E) {
//                System.out.println(E);
//            }
//        }
//        //update backward
//        for (int i = ts.getZeroIndex() - 1; i > 0; i--) {
//            AffineTransform T = align.getTransform(i);
//            try {
//                AffineTransform T0 = ts.getTransform(i + 1, true, true).createInverse();
//                T.preConcatenate(T0);
//                ts.setTransform(i, T);
//            } catch (Exception E) {
//                System.out.println(E);
//            }
//        }
//        /*ts.combineTransforms(false);
//          for(int i=0;i<ts.getImageStackSize();i++){
//              ts.setTransform(i,align.getTransform(i));
//          }*/
//    }

//
//    public static ArrayList<Point2D[]> tryToFuseLandmarks(ArrayList<Point2D[]> chains, double dstThreshold) {
//        ArrayList<Point2D[]> res = new ArrayList<Point2D[]>();
//        boolean[] fused = new boolean[chains.size()];
//        for (int i = 0; i < chains.size() - 1; i++) {
//            si n'a pas deja ete fusionne
//            Point2D[] li = chains.get(i);
//            if (!fused[i]) {
//                int compt = 0;
//                for (int j = i + 1; j < chains.size(); j++) {
    //si n'a pas deja ete fusionne
//                    if (!fused[j]) {
//                        Point2D[] lj = chains.get(j);
//                        int nbIdem = 0;
//                        //check if distance� <=2
//                        for (int k = 0; k < li.length; k++) {
//                            if (li[k] != null && lj[k] != null) {
//                                //double d=(li[k].getX()-lj[k].getX())*(li[k].getX()-lj[k].getX())+(li[k].getY()-lj[k].getY())*(li[k].getY()-lj[k].getY());
//                                if (li[k].distanceSq(lj[k]) <= dstThreshold) {
//                                    nbIdem++;
//                                }
//
//                            }
//                        }
//                        if (nbIdem > 3) {
//                            compt++;
//                            fused[i] = true;
//                            fused[j] = true;
//                            li = fuse2Landmarks(li, lj, compt);
//                        }
//                    }
//                }
//                res.add(li);
//            }
//
//
//        }
//
//        return res;
//    }

//    protected static Point2D[] fuse2Landmarks(Point2D[] l1, Point2D[] l2, int compt) {
//        Point2D[] res = new Point2D[l1.length];
//
//        for (int i = 0; i < res.length; i++) {
//            if (l1[i] != null && l2[i] == null) res[i] = l1[i];
//            if (l1[i] == null && l2[i] != null) res[i] = l2[i];
//            if (l1[i] != null && l2[i] != null)
//                res[i] = new Point2D.Double((l1[i].getX() * compt + l2[i].getX()) / (compt + 1), (l1[i].getY() * compt + l2[i].getY()) / (compt + 1));
//
//        // System.out.println("res: "+landmark2String(l2));
//        return res;
//    }

//    protected static Point2D[] fuseLandmarks(ArrayList<Point2D[]> landmarks){
//        Point2D[] res = new Point2D[landmarks.get(0).length];
//        for (int i = 0; i < res.length; i++) {
//            Point2D sum=null;
//            int total=0;
//            for(int j=0;j<landmarks.size();j++){
//                Point2D ptmp=landmarks.get(0)[i];
//                if(ptmp!=null){
//                    if(sum==null) sum=new Point2D.Double(ptmp.getX(),ptmp.getY());
//                    else sum=new Point2D.Double(sum.getX()+ptmp.getX(),sum.getY()+ptmp.getY());
//                    total++;
//                }
//            }
//            if(total>0){
//                sum=new Point2D.Double(sum.getX()/total,sum.getY()/total);
//            }
//            res[i]=sum;
//        }
//        return res;
//
//    }


//    public String landmark2String(Point2D[] l) {
//        String res = "[" + l[0];
//        for (int i = 1; i < l.length; i++) res += " " + l[i];
//        return res + "]";
//    }

//    public String landmark2StringUncentered(Point2D[] l) {
//        String res = "[";
//        for (int i = 0; i < l.length; i++) {
//            if (l[i] != null)
//                res += " [" + (l[i].getX() + ts.getCenterX()) + ", " + (l[i].getY() + ts.getCenterY()) + "]";
//            else res += " null";
//        }
//        return res + "]";
//    }


    /**
     * Update originals TomoJPoints with aligned landmarks
     */
    public void updateTPs() {
        // Remove all landmarks already present
        for (TomoJPoints tp : tpList) {
            tp.removeAllSetsOfPoints();
        }

        for (Point2D[] landmark : landmarks) {
//            for (int k = 0; k < tpList.size(); k++) {
//
//            }
            // Control whether this landmarks chain contains points from one/both
            // TomoJPoint or not
            boolean isTP1 = false;
            for (int i = 0; i < nImagesi.get(0); i++) {
                if (landmark[i] != null) {
                    isTP1 = true;
                    break;
                }
            }
            boolean isTP2 = false;
            for (int i = nImagesi.get(0); i < nImagesi.get(1); i++) {
                if (landmark[i] != null) {
                    isTP2 = true;
                    break;
                }
            }

            if (isTP1) {
                Point2D[] p1 = new Point2D[nImagesi.get(0)];
                System.arraycopy(landmark, 0, p1, 0, nImagesi.get(0));
                getTomoJPoints(0).addSetOfPoints(p1);
            }
            if (isTP2) {
                Point2D[] p2 = new Point2D[nImagesi.get(1)];
                System.arraycopy(landmark, nImagesi.get(0), p2, 0, nImagesi.get(1));
                getTomoJPoints(1).addSetOfPoints(p2);
            }
        }
    }

}
