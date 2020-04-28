package fr.curie.tomoj.landmarks;

import fr.curie.tomoj.align.AffineAlignment;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.ImageStatistics;
import fr.curie.filters.ApplySymmetry_Filter;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.align.AlignImages;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Created by cmessaoudi on 26/09/2017.
 */
public class SeedsToChainTracker implements Callable {
    static final int METHOD_CORRELATION_THRESHOLD=1;
    static final int METHOD_CROSS_VALIDATION=2;
    protected int method=METHOD_CROSS_VALIDATION;
    protected LandmarksChain lc;
    protected TiltSeries ts;
    protected int patchSize;
    protected double correlationThreshold;
    protected int fixedLength;
    boolean goldBead=false;

    public SeedsToChainTracker(int method, LandmarksChain lc, TiltSeries ts, int patchSize, double correlationThreshold, int fixedLength) {
        this.method = method;
        this.lc = lc;
        this.ts = ts;
        this.patchSize = patchSize;
        this.correlationThreshold = correlationThreshold;
        this.fixedLength = fixedLength;
    }

    public LandmarksChain call() throws Exception {
        switch (method){
            case METHOD_CORRELATION_THRESHOLD:
                followSeed(lc.getLandmarkchain(),lc.getImgIndex(),fixedLength,patchSize,correlationThreshold,false,false);
                break;
            case METHOD_CROSS_VALIDATION:
            default:
                followSeedBackAndForth(lc.getLandmarkchain(),lc.getImgIndex(),fixedLength,patchSize,correlationThreshold,false,false);
        }
        return null;
    }


    /**
     * the method to follow a landmark on previous and next images from a seed this method is the one used by generate landmarks <br>
     * <ol>
     * <li>it goes on previous image</li>
     * <li>evaluate theoritical position of landmark using transform between the 2 images</li>
     * <li>refine the landmark position</li>
     * <li>do 1-3 with current image as seed</li>
     * <li>do 1-4 with next images instead of previous</li>
     * </ol>
     *
     * @param landmarkchain     the chain to update
     * @param ii                initial index
     * @param SeqLength         the minimum length of chain or total length of chain (critical point)
     * @param localSize         size of the patch used in refinement by local refinement
     * @param corrThreshold     minimum correlation to follow on next images
     * @param useCriticalPoints if true follow in each direction until SeqLength is attained, if false continue as long as correlation is above corrThreshold
     * @return a landmark chain
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#generateLandmarkSet(int, int, int, int, double, boolean, boolean)
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#refineLandmark(int, int, Point2D[], int, boolean, boolean)
     */

    public double followSeed(Point2D[] landmarkchain, int ii, int SeqLength, int localSize, double corrThreshold, boolean useCriticalPoints, boolean FFT) {
        int halfSeqLength = SeqLength / 2;
        boolean cont = true;
        //follow landmark backward
        int jjmin = Math.max(0, ii - halfSeqLength);
        int jjmax = ii - 1;
        if (!useCriticalPoints) {
            jjmin = 0;
            jjmax = ts.getImageStackSize() - 1;
        }
        AffineTransform Aij;
        AffineTransform Aji;
        Point2D rcurrent = landmarkchain[ii];
        if (rcurrent == null) return -1;
        Point2D.Double rjj;
        double mincorr = 2;
        double corr;
        int jj_1;
        //for(int jj=jjmax;jj>=jjmin;jj--){
        int jj = ii - 1;
        double[] tx = new double[landmarkchain.length];
        double[] ty = new double[landmarkchain.length];

        while (cont && jj >= jjmin) {
            if (landmarkchain[jj] == null) {
                //compute the affine transformation between jj and jj+1
                jj_1 = jj + 1;
                //int jj_2=jj+2;
                //if(goldBead) refineLandmark(jj_1, jj_1, landmarkchain, localSize, FFT, goldBead);
                Aij = ((AffineAlignment)ts.getAlignment()).getTransform(jj, false);
                try {
                    //System.out.println("getting transform finished");
                    Aji = Aij.createInverse();
                    //System.out.println("computing inverse finished Aji="+Aji+" rcurrent="+rcurrent);
                    rjj = (Point2D.Double) Aji.transform(rcurrent, null);
                    //System.out.println("computing new point finished rjj="+rjj);
                    //landmarkchain[jj]=rjj;
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);

                    //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    //System.out.println("before refine landmark was "+landmarkchain[jj]);
                    corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                    //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                    // tx[jj]=0;
                    // ty[jj]=0;
                    //System.out.println("after refine landmark is "+landmarkchain[jj]);
                    cont = corr > corrThreshold || useCriticalPoints;
                    if (cont) {
                        //System.out.println(""+jj+" rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                        mincorr = Math.min(mincorr, corr);
                        rcurrent = landmarkchain[jj];
                    } else {
                        landmarkchain[jj] = null;
                    }
                } catch (Exception e) {
                    System.out.println("TomoJPoints followseed error backward " + e);
                }
            }
            jj--;

        }
        //follow landmark forward
        if (useCriticalPoints) {
            jjmin = ii + 1;
            jjmax = Math.min(ts.getImageStackSize() - 1, ii + halfSeqLength);
        }
        rcurrent = landmarkchain[ii];
        jj = ii + 1;
        cont=true;
        while (cont && jj <= jjmax) {
            //for(int jj=jjmin;jj<=jjmax;jj++){
            //compute the affine transform between jj-1 and jj
            if (landmarkchain[jj] == null) {
                jj_1 = jj - 1;
                //if(goldBead) refineLandmark(jj_1, jj_1, landmarkchain, localSize, FFT, goldBead);
                Aij = ((AffineAlignment)ts.getAlignment()).getTransform(jj_1, false);
                rjj = (Point2D.Double) Aij.transform(rcurrent, null);
                //landmarkchain[jj] = rjj;
                landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);

                //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                //System.out.println("before refine landmark was "+landmarkchain[jj]);
                corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                //System.out.println("Aij="+Aij);
                //System.out.println(""+jj+" before refine landmark (2) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                // System.out.println("before refine landmark was "+landmarkchain[jj]);
                //corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                //System.out.println("after refine landmark is "+landmarkchain[jj]);
                cont = corr > corrThreshold || useCriticalPoints;
                if (cont) {
                    rcurrent = landmarkchain[jj];
                    mincorr = Math.min(mincorr, corr);
                } else {
                    landmarkchain[jj] = null;
                }
            }
            jj++;
        }
        return mincorr;
    }

    /**
     * the method to follow a landmark on previous and next images from a seed this method is the one used by generate landmarks <br>
     * <ol>
     * <li>it goes on previous image</li>
     * <li>evaluate theoritical position of landmark using transform between the 2 images</li>
     * <li>refine the landmark position</li>
     * <li>do 1-3 with current image as seed</li>
     * <li>do 1-4 with next images instead of previous</li>
     * </ol>
     *
     * @param landmarkchain     the chain to update
     * @param ii                initial index
     * @param SeqLength         the minimum length of chain or total length of chain (critical point)
     * @param localSize         size of the patch used in refinement by local refinement
     * @param corrThreshold     minimum correlation to follow on next images
     * @param useCriticalPoints if true follow in each direction until SeqLength is attained, if false continue as long as correlation is above corrThreshold
     * @return a landmark chain
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#generateLandmarkSet(int, int, int, int, double, boolean, boolean)
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#refineLandmark(int, int, Point2D[], int, boolean, boolean)
     */
    public double followSeedBackAndForth(Point2D.Double[] landmarkchain, int ii, int SeqLength, int localSize, double corrThreshold, boolean useCriticalPoints, boolean FFT) {
        int halfSeqLength = SeqLength / 2;
        boolean cont = true;
        //follow landmark backward
        int jjmin = Math.max(0, ii - halfSeqLength);
        int jjmax = ii - 1;
        if (!useCriticalPoints) {
            jjmin = 0;
            jjmax = ts.getImageStackSize() - 1;
        }
        AffineTransform Aij;
        AffineTransform Aji;
        Point2D.Double rcurrent = landmarkchain[ii];
        if (rcurrent == null) return -1;
        Point2D.Double rjj;
        double mincorr = 2;
        double corr1, corr2;
        int jj_1;
        //for(int jj=jjmax;jj>=jjmin;jj--){
        int jj = ii - 1;
        double[] tx = new double[landmarkchain.length];
        double[] ty = new double[landmarkchain.length];

        while (cont && jj >= jjmin) {
            if (landmarkchain[jj] == null) {
                //compute the affine transformation between jj and jj+1
                jj_1 = jj + 1;
                //int jj_2=jj+2;
                //if(goldBead) refineLandmark(jj_1, jj_1, landmarkchain, localSize, FFT, goldBead);
                Aij = ((AffineAlignment)ts.getAlignment()).getTransform(jj, false);
                try {
                    //System.out.println("getting transform finished");
                    Aji = Aij.createInverse();
                    //System.out.println("computing inverse finished Aji="+Aji+" rcurrent="+rcurrent);
                    Point2D.Double rRef = new Point2D.Double(rcurrent.getX(), rcurrent.getY());
                    rjj = (Point2D.Double) Aji.transform(rcurrent, null);
                    //System.out.println("computing new point finished rjj="+rjj);
                    //landmarkchain[jj]=rjj;
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);

                    //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    //System.out.println("before refine landmark was "+landmarkchain[jj]);
                    corr1 = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    rjj = (Point2D.Double) Aij.transform(landmarkchain[jj], null);
                    landmarkchain[jj_1] = new Point2D.Double(rjj.getX() - tx[jj_1], rjj.getY() - ty[jj_1]);
                    corr2 = refineLandmark(jj, jj_1, landmarkchain, localSize, FFT, goldBead);
                    if (Math.sqrt((landmarkchain[jj_1].getX() - rRef.getX()) * (landmarkchain[jj_1].getX() - rRef.getX()) + (landmarkchain[jj_1].getY() - rRef.getY()) * (landmarkchain[jj_1].getY() - rRef.getY())) > 4) {
                        //the backward correlation is not the same I put a malus
                        corr1 -= 0.5;
                        corr2 -= 0.5;
                        landmarkchain[jj_1].setLocation(rRef);
                    } else {
                        landmarkchain[jj_1].setLocation(rRef);
                    }

                    //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                    //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                    // tx[jj]=0;
                    // ty[jj]=0;
                    //System.out.println("after refine landmark is "+landmarkchain[jj]);
                    cont = Math.min(corr1, corr2) > corrThreshold || useCriticalPoints;
                    if (cont) {
                        //System.out.println(""+jj+" rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                        mincorr = Math.min(mincorr, Math.min(corr1, corr2));
                        rcurrent = landmarkchain[jj];
                    } else {
                        landmarkchain[jj] = null;
                    }
                } catch (Exception e) {
                    System.out.println("TomoJPoints followseed error backward " + e);
                }
            }
            jj--;

        }
        //follow landmark forward
        if (useCriticalPoints) {
            jjmin = ii + 1;
            jjmax = Math.min(ts.getImageStackSize() - 1, ii + halfSeqLength);
        }
        rcurrent = landmarkchain[ii];
        jj = ii + 1;
        while (cont && jj <= jjmax) {
            //for(int jj=jjmin;jj<=jjmax;jj++){
            //compute the affine transform between jj-1 and jj
            if (landmarkchain[jj] == null) {
                jj_1 = jj - 1;
                //if(goldBead) refineLandmark(jj_1, jj_1, landmarkchain, localSize, FFT, goldBead);
                Aij = ((AffineAlignment)ts.getAlignment()).getTransform(jj_1, false);
                try {
                    rjj = (Point2D.Double) Aij.transform(rcurrent, null);
                    //landmarkchain[jj] = rjj;
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);
                    Point2D.Double rRef = new Point2D.Double(rcurrent.getX(), rcurrent.getY());
                    //System.out.println(""+jj+" before refine landmark(1) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    //System.out.println("before refine landmark was "+landmarkchain[jj]);
                    //corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    corr1 = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    Aji = Aij.createInverse();
                    rjj = (Point2D.Double) Aji.transform(landmarkchain[jj], null);
                    landmarkchain[jj_1] = new Point2D.Double(rjj.getX() - tx[jj_1], rjj.getY() - ty[jj_1]);
                    corr2 = refineLandmark(jj, jj_1, landmarkchain, localSize, FFT, goldBead);
                    if (Math.sqrt((landmarkchain[jj_1].getX() - rRef.getX()) * (landmarkchain[jj_1].getX() - rRef.getX()) + (landmarkchain[jj_1].getY() - rRef.getY()) * (landmarkchain[jj_1].getY() - rRef.getY())) > 4) {
                        //the backward correlation is not at the same location I put a malus
                        corr1 -= 0.5;
                        corr2 -= 0.5;
                        landmarkchain[jj_1].setLocation(rRef);
                    } else {
                        landmarkchain[jj_1].setLocation(rRef);
                    }
                    //System.out.println("tx["+jj+"]="+tx[jj]+" proposed="+tx[jj_1]);
                    //System.out.println("ty["+jj+"]="+ty[jj]+" proposed="+ty[jj_1]);
                    //System.out.println("Aij="+Aij);
                    //System.out.println(""+jj+" before refine landmark (2) rcurrent="+landmarkchain[jj_1]+" rjj="+landmarkchain[jj]);
                    //corr=refineLandmark(jj_1,jj,landmarkchain,localSize);
                    // System.out.println("before refine landmark was "+landmarkchain[jj]);
                    //corr = refineLandmark(jj_1, jj, landmarkchain, localSize, FFT, goldBead);
                    //System.out.println("after refine landmark is "+landmarkchain[jj]);
                    //cont = corr > corrThreshold || useCriticalPoints;
                    cont = Math.min(corr1, corr2) > corrThreshold || useCriticalPoints;
                    if (cont) {
                        rcurrent = landmarkchain[jj];
                        mincorr = Math.min(mincorr, Math.min(corr1, corr2));
                    } else {
                        landmarkchain[jj] = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            jj++;
        }
        return mincorr;
    }

    /**
     * refine a landmark position by local crosscorrelation with a reference landmark image<br>
     *
     * @param pieceii   landmark image used as reference
     * @param jj        index of second image
     * @param landmark  array containing the landmark at the corresponding indexes
     * @param localSize size in pixel to use in local crosscorrelation
     * @return the minimum correlation score between two landmark images
     */
    public double refineLandmark(float[] pieceii, int jj, Point2D[] landmark, int localSize) {
        //normalize pieceii (reference)
        TomoJPoints tp=ts.getTomoJPoints();
        FloatProcessor fpii = new FloatProcessor(localSize, localSize, pieceii, null);
        ImageStatistics stat = new FloatStatistics(fpii);
        for (int i = 0; i < pieceii.length; i++) {
            pieceii[i] = (float) ((pieceii[i] - stat.mean) / stat.stdDev);
        }

        //try all possible shifts
        int corrSize = (int) (1.5 * localSize);
        int hcx = corrSize / 2;
        double[] corr = new double[corrSize * corrSize];
        Arrays.fill(corr, -1.1);
        double maxval = -1.1;
        ts.setAlignmentRoi(localSize, localSize);
        double imax = 0;
        double jmax = 0;
        ArrayList<Point2D.Double> Q = new ArrayList<Point2D.Double>(localSize * localSize);
        int halfSize = localSize / 2;
        Point2D.Double tmp1 = new Point2D.Double(0, 0);
        float[] piecejj;
        double corrRef;
        double newshiftx;
        double newshifty;
        int j;
        int step;
        int stepx, stepy;
        Point2D.Double pt;
        FloatProcessor fp = new FloatProcessor(localSize, localSize);
        Q.add(tmp1);
        while (!Q.isEmpty()) {
            //remove first position to evaluate
            tmp1 = Q.remove(0);
            //if not already tested
            if (corr[(int) (tmp1.getX() + hcx + (tmp1.getY() + hcx) * corrSize)] == -1.1) {
                //select region in image jj and normalize
                piecejj = ts.getSubImagePixels(jj, localSize, localSize, new Point2D.Double(landmark[jj].getX() + tmp1.getX(), landmark[jj].getY() + tmp1.getY()), false, false);
                //float[] piecejj=ts.getPixelsForAlignment(jj,new Point2D.Double(landmark[jj].getX() + tmp1.getX(), landmark[jj].getY() + tmp1.getY()));
                fp.setPixels(piecejj);
                stat = new FloatStatistics(fp);
                for (j = 0; j < piecejj.length; j++) {
                    piecejj[j] = (float) ((piecejj[j] - stat.mean) / stat.stdDev);
                }
                //compute correlation
                corrRef = AlignImages.unNormalizedCorrelation(pieceii, piecejj);
                //System.out.println("testing (" + tmp1.getX() + ", " + tmp1.getY() + ") score=" + corrRef + " maxval=" + maxval);
                corr[(int) (tmp1.getX() + hcx + (tmp1.getY() + hcx) * corrSize)] = corrRef;
                if (corrRef > maxval) {
                    maxval = corrRef;
                    imax = tmp1.getX();
                    jmax = tmp1.getY();
                    //System.out.println("updating maxval=" + maxval + " imax=" + imax + " jmax=" + jmax);
                    for (step = 1; step <= 5; step += 2) {
                        for (stepy = -1; stepy <= 1; stepy++) {
                            for (stepx = -1; stepx <= 1; stepx++) {
                                newshiftx = tmp1.getX() + stepx * step;
                                newshifty = tmp1.getY() + stepy * step;
                                if (newshiftx >= -hcx && newshiftx < hcx && newshifty >= -hcx && newshifty < hcx) {
                                    if (corr[(int) (newshiftx + hcx + (newshifty + hcx) * corrSize)] < -1) {
                                        pt = new Point2D.Double(newshiftx, newshifty);
                                        if (tp.isInsideImage(pt, halfSize)) {
                                            //Q.add(new Point2D.Double(newshiftx, newshifty));
                                            Q.add(pt);
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
        landmark[jj] = new Point2D.Double(landmark[jj].getX() + imax, landmark[jj].getY() + jmax);
        //ts.applyTransforms(true);
        //if (maxval > corrThreshold) {
        //	landmark[jj] = new Point2D.Double(landmark[jj].getX() + imax, landmark[jj].getY() + jmax);
        //float[] tmp2 = ts.getSubImagePixels(jj, sx, sx, new Point2D.Double(landmark[jj].getX(), landmark[jj].getY()));
        //new ImagePlus("refined" + jj, new FloatProcessor(sx, sx, tmp2, null)).show();
        //	return true;
        //}
        //return false;
        return maxval;
    }
    /**
     * refine a landmark position by local crosscorrelation<br>
     * get the image of first image and call the refineLandmark method
     *
     * @param ii        index of first image
     * @param jj        index of second image
     * @param landmark  array containing the landmark at the corresponding indexes
     * @param localSize size in pixel to use in local crosscorrelation
     * @return the minimum correlation score between two landmark images
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#refineLandmark(float[], int, Point2D[], int)
     */
    public double refineLandmark(int ii, int jj, Point2D[] landmark, int localSize, boolean FFT, boolean goldBead) {
        TomoJPoints tp=ts.getTomoJPoints();
        //is it near border?
        int halfSize = localSize / 2;
        if (!tp.isInsideImage(landmark[ii], halfSize) || !tp.isInsideImage(landmark[jj], halfSize)) {
            //System.out.println("ii:"+landmark[ii]+" , jj:"+landmark[jj]+" , halfsize:"+halfSize);
            return -1;
        }

        //select region of interest
        ts.setAlignmentRoi(localSize, localSize);
        float[] pieceii = ts.getSubImagePixels(ii, localSize, localSize, landmark[ii], false, false);
        //float[] pieceii=ts.getPixelsForAlignment(ii,landmark[ii]);
        //new ImagePlus("ref" + ii, new FloatProcessor(sx, sx, pieceii, null)).show();
        //float[] tmp = ts.getSubImagePixels(jj, sx, sx, new Point2D.Double(landmark[jj].getX(), landmark[jj].getY()));
        //new ImagePlus("before" + jj, new FloatProcessor(sx, sx, tmp, null)).show();
        FloatProcessor fp = null;
        ColorModel cm = ts.getProcessor().getCurrentColorModel();
        if (goldBead) {
            fp = new FloatProcessor(localSize, localSize, pieceii, cm);
            ImagePlus impf = new ImagePlus("symmetry", fp);
            ApplySymmetry_Filter filter = new ApplySymmetry_Filter();
            filter.setup("symmetry=12", impf);
            filter.run(fp);
            //IJ.runPlugIn(new ImagePlus("tmp",fp),"tomoj.filters.ApplySymmetry_Filter", "symmetry=12");

        }
        if (FFT) {
            //System.out.println("before refinement FFT landmark "+jj+" "+landmark[jj]);
            float[] pieceiibig = ts.getSubImagePixels(ii, localSize * 2, localSize * 2, landmark[ii], false, false);
            //refineLandmarkFHT(pieceiibig, jj, landmark, localSize * 2);
        }
        // new ImagePlus("piecejj"+jj+" before",new FloatProcessor(localSize,localSize,ts.getSubImagePixels(jj, localSize, localSize, landmark[jj]),cm)).show();
        //System.out.println("before refinement landmark "+jj+" "+landmark[jj]);
        double score = refineLandmark(pieceii, jj, landmark, localSize);
        //System.out.println("after refinement landmark "+jj+" "+landmark[jj]+" with score of "+score);
        // new ImagePlus("pieceii"+jj+" before sym",new FloatProcessor(localSize,localSize,ts.getSubImagePixels(ii, localSize, localSize, landmark[ii]),cm)).show();
        //new ImagePlus("pieceii"+jj,new FloatProcessor(localSize,localSize,pieceii,cm)).show();
        //new ImagePlus("piecejj"+jj+" after",new FloatProcessor(localSize,localSize,ts.getSubImagePixels(jj, localSize, localSize, landmark[jj]),cm)).show();
        // try{System.in.read();}catch(Exception e){}
        return score;
    }
}
