package fr.curie.tomoj.landmarks;

import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import fr.curie.tomoj.align.AffineAlignment;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PointRoi;
import ij.measure.CurveFitter;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import fr.curie.filters.ApplySymmetry_Filter;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.align.AlignImages;
import fr.curie.utils.Chrono;
import fr.curie.utils.MatrixUtils;
import fr.curie.tomoj.workflow.CommandWorkflow;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 16/01/2017.
 */
public class ChainsGenerator {

    double completion;
    TiltSeries ts;
    TomoJPoints tp;
    int nbThreads;
    double cx,cy;
    protected ExecutorService exec;

    ArrayList<Point2D[]> landmarks ;
    ArrayList<Boolean> automaticGeneration = new ArrayList<Boolean>();

    float[] reference;

    public ChainsGenerator(TiltSeries ts) {
        this.ts = ts;
        this.tp=ts.getTomoJPoints();
        landmarks=tp.getAllLandmarks();
        automaticGeneration=tp.getAllAutomaticGeneration();
    }

    public void update(){
        landmarks=tp.getAllLandmarks();
        automaticGeneration=tp.getAllAutomaticGeneration();
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

    public double followSeedWithCrossValidation(){
        return 0;
    }

    public Point2D refineLandmarkPositionFlipFlap(Point2D originalPosition, int imageIndex, int patchSize) {
        return refineLandmarkPositionFlipFlap(originalPosition,ts,imageIndex,patchSize);
    }

        /**
         * modifies position by using flip/flap Xcorrelation
         * @param originalPosition
         * @param imageIndex
         * @param patchSize
         * @return originalPosition object with coordinates modified
         */
    public static Point2D refineLandmarkPositionFlipFlap(Point2D originalPosition,TiltSeries ts, int imageIndex, int patchSize){
        int halfSize = patchSize / 2;
        if (!ts.getTomoJPoints().isInsideImage(originalPosition, halfSize) || !ts.getTomoJPoints().isInsideImage(originalPosition, halfSize)) {
            //System.out.println("ii:"+landmark[ii]+" , jj:"+landmark[jj]+" , halfsize:"+halfSize);
            return originalPosition;
        }

        //select region of interest
        ts.setAlignmentRoi(patchSize, patchSize);
        float[] pieceii = ts.getSubImagePixels(imageIndex, patchSize, patchSize, originalPosition, false, false);
        ImageProcessor patch=new FloatProcessor(patchSize,patchSize,pieceii);

        //flip in X
        ImageProcessor flipX=patch.duplicate();
        flipX.flipHorizontal();
        //compute Xcorr between patch and flipX version
        AffineTransform T= AlignImages.computeCrossCorrelationFFT((float[])flipX.getPixels(),(float[])patch.getPixels(),patch.getWidth(),patch.getHeight(),true);
        double txX= T.getTranslateX()/2;
        double tyX= T.getTranslateY()/2;
        //System.out.println("shift X by "+txX+","+tyX);
        //correct position and take new patch in this position
        Point2D.Double corrected=new Point2D.Double(originalPosition.getX()+txX,originalPosition.getY()+tyX);
        pieceii = ts.getSubImagePixels(imageIndex, patchSize, patchSize, corrected, false, false);
        patch.setPixels(pieceii);

        //flip in Y
        ImageProcessor flipY=patch.duplicate();
        flipY.flipVertical();
        //compute Xcorr between patch and flipY version
        T=AlignImages.computeCrossCorrelationFFT((float[])flipY.getPixels(),(float[])patch.getPixels(),patch.getWidth(),patch.getHeight(),true);
        double txY= T.getTranslateX()/2;
        double tyY= T.getTranslateY()/2;
        //correct position
        originalPosition.setLocation(corrected.getX()+txY,corrected.getY()+tyY);
        //System.out.println("shift Y by "+txY+","+tyY);

        return originalPosition;
    }

    public float[] createGaussianImage(int size,double sigma, boolean darkOnWhiteBG){
        float result[]=new float[size*size];
        double size2=size/2.0;
        double div=2*sigma*sigma;
        int jj;
        for(int j=0;j<size;j++){
            jj=j*size;
            for(int i=0;i<size;i++){
                result[jj+i]=(darkOnWhiteBG)?-(float)Math.exp(-(i-size2)*(i-size2)/div-(j-size2)*(j-size2)/div):(float)Math.exp(-(i-size2)*(i-size2)/div-(j-size2)*(j-size2)/div);
            }
        }
        //new ImagePlus("gaussian",new FloatProcessor(size,size,result)).show();
        reference=result;
        return result;
    }

    public void refineLandmarksPositionGaussian(final ArrayList<Point2D[]> landmarks, final TiltSeries ts, final int patchSize, final boolean darkOnWhiteBG){
        final int[] count={0};
        ArrayList<Future> futures=new ArrayList<Future>();
        for(Point2D[] l:landmarks) {
            final Point2D[] landmark=l;

            futures.add(exec.submit(new Thread(){
                @Override
                public void run() {
                    for (int i = 0; i < ts.getImageStackSize(); i++) {
                        refineLandmarkPositionGaussian(landmark, ts, i, patchSize, darkOnWhiteBG);
                    }
                    count[0]++;
                    IJ.showProgress(count[0]/(double)landmarks.size());
                }
           }) );
        }
        for(Future f:futures){
            try{
                f.get();
            } catch (Exception e){
                e.printStackTrace();
            }
        }


    }
    /**
     * modifies position by using Gaussian curves
     * @param landmark list of points
     * @param ts tilt series with images
     * @param imageIndex  image to refine position
     * @param patchSize  size of the patch
     * @return originalPosition object with coordinates modified
     */
    public void refineLandmarkPositionGaussian(Point2D[] landmark,TiltSeries ts, int imageIndex, int patchSize, boolean darkOnWhiteBG){
        int halfSize = patchSize / 2;
        if (landmark[imageIndex]==null || !tp.isInsideImage(landmark[imageIndex], halfSize)) {
            //System.out.println("ii:"+landmark[ii]+" , jj:"+landmark[jj]+" , halfsize:"+halfSize);
            return ;
        }
        int patchSize2=patchSize/2;
        int[] xs=new int[patchSize];
        for(int x=0;x<patchSize;x++) xs[x]=x;


        if(landmark[imageIndex]!=null){
            double tmp;
            double[] paramsX;
            double[] paramsY;
            int nbloop=0;
            do {
                float[] patch = ts.getSubImagePixels(imageIndex, patchSize, patchSize, landmark[imageIndex], false, false);
                float[] row=new float[patchSize];
                int offset=0;
                for(int r=0;r<patchSize;r++){
                    for(int c=0;c<patchSize;c++){
                        row[c]+=patch[offset+c];
                    }
                    offset+=patchSize;
                }
                row = smooth(row, 1);
                //row = reduce(row, 1);
                paramsX = fitGaussian(row, darkOnWhiteBG);
                if(Math.abs(paramsX[2])<patchSize2)landmark[imageIndex].setLocation(landmark[imageIndex].getX() + paramsX[2], landmark[imageIndex].getY());
                //System.out.println("#"+i+" centerX:" + paramsX[2] + ", sigma: " + paramsX[3]+ ", R=" + paramsX[4]);
                //pw.addPlot(xs,row, Color.BLACK,"row point "+i);

                patch = ts.getSubImagePixels(imageIndex, patchSize, patchSize, landmark[imageIndex], false, false);
                float[] column=new float[patchSize];
                for(int c=0;c<patchSize;c++){
                    for(int r=0;r<patchSize;r++){
                        column[r]+=patch[r*patchSize+c];
                    }
                }
                column = smooth(column, 1);
                //column = reduce(column, 1);
                paramsY = fitGaussian(column, darkOnWhiteBG);
                if(Math.abs(paramsX[2])<patchSize2)landmark[imageIndex].setLocation(landmark[imageIndex].getX(), landmark[imageIndex].getY() + paramsY[2]);
                //System.out.println("#"+i+" centerY:" + paramsY[2] + ", sigma: " + paramsY[3] + ", R=" + paramsY[4]);
                //pw.addPlot(xs,column, Color.RED,"col point "+i);
                nbloop++;
            }while (nbloop<5&&(Math.abs(paramsX[2])>0.5||Math.abs(paramsY[2])>0.5));
            if (paramsY[4] < 0.5 || paramsX[4] < 0.5 ) {

                //landmark[imageIndex]=null;
                System.out.println("should remove ");
            }

        }

        //System.out.println("center:"+params[2]+", sigma: "+params[3]);
        //pw.addPlot(xs,column, Color.RED,"col point "+i);

        /*if (reference==null) return -1;
        float[] gaussian=reference;

        //select region of interest
        ts.setAlignmentRoi(patchSize, patchSize);
        double score = 0;
                refineLandmarkFHT(gaussian, imageIndex, landmark, patchSize);

        return score;   */
    }



    protected float[] smooth(float[] data, int radius){
        float[] result=new float[data.length];
        for(int i=0;i<result.length;i++){
            for(int j=-radius;j<=radius;j++){
                int tmp=Math.min(result.length-1,Math.max(0,i+j));
                result[i]+=data[tmp];
            }
        }
        return result;
    }

    protected float[] reduce(float[] data, int radius){
        float[] result=new float[data.length/(radius+1)+1];
        for(int i=0;i<result.length;i++){
            for(int j=i*(radius*2)-radius;j<=i*(radius*2)+radius;j++){
                int tmp=Math.min(result.length-1,Math.max(0,j));
                result[i]+=data[tmp];
            }
        }
        return result;
    }

    protected double[] fitGaussian(float[] data, boolean darkOnWhiteBG){
        double[] xs=new double[data.length];
        double[] ys=new double[data.length];
        int center=data.length/2;
        for(int i=0;i<data.length;i++){
            xs[i]=i-center;
            ys[i]=(darkOnWhiteBG)?-data[i]:data[i];
        }
        CurveFitter cf=new CurveFitter(xs,ys);
        cf.doFit(CurveFitter.GAUSSIAN);
        return cf.getParams();

    }


    public float[] createDiscusImage(int size,double radius, boolean darkOnWhite){
        float result[]=new float[size*size];
        double size2=(size-1.0)/2.0;
        int jj;
        double ic,jc;
        float valBG=(darkOnWhite)?1:0;
        float valFG=(darkOnWhite)?0:1;
        for(int j=0;j<size;j++){
            jj=j*size;
            jc=j-size2;
            for(int i=0;i<size;i++){
                ic=i-size2;
                if(ic*ic+jc*jc<radius*radius) result[jj+i]=valFG;
                else result[jj+i]=valBG;
            }
        }

        float result2[]=new float[size*size];
        int ii;
        for(int j=0;j<size;j++){
            jj=j*size;
            for(int i=0;i<size;i++){
                result2[jj+i]=result[jj+i];
                ii=Math.max(0,i-1);
                result2[jj+i]+=result[jj+ii];
                ii=Math.min(size-1,i+1);
                result2[jj+i]+=result[jj+ii];

            }
        }
        for(int j=0;j<size;j++){
            jj=j*size;
            for(int i=0;i<size;i++){
                result[jj+i]=result2[jj+i];
                ii=Math.max(0,jj-size);
                result[jj+i]+=result2[ii+i];
                ii=Math.min((size-1)*size,jj+size);
                result[jj+i]+=result2[ii+i];
            }
        }
        new ImagePlus("gaussian",new FloatProcessor(size,size,result)).show();
        reference=result;
        return result;
    }

    public void refineLandmarkPositionDiscus(Point2D[] landmark,TiltSeries ts, int imageIndex, int patchSize, double radius, boolean darkOnWhite){
        int halfSize = patchSize / 2;
        if (!tp.isInsideImage(landmark[imageIndex], halfSize) || !tp.isInsideImage(landmark[imageIndex], halfSize)) {
            //System.out.println("ii:"+landmark[ii]+" , jj:"+landmark[jj]+" , halfsize:"+halfSize);
            return;
        }
        if (reference==null) return;
        //float[] discusImage=createDiscusImage(patchSize,radius,darkOnWhite);
        float[] discusImage=reference;

        //select region of interest
        ts.setAlignmentRoi(patchSize, patchSize);
        double score = refineLandmark(discusImage,imageIndex,landmark,patchSize);
        //refineLandmarkFHT(discusImage, imageIndex, landmark, patchSize);
    }

    public void refineLandmarksPositionDiscus(ArrayList<LandmarksChain> seeds,TiltSeries ts, int patchSize, double radius, boolean darkOnWhite){
        for(LandmarksChain lc:seeds){
            for(int index=0;index<ts.getImageStackSize();index++) {
                if(lc.getLandmarkchain()[index]!=null) refineLandmarkPositionDiscus(lc.getLandmarkchain(), ts, lc.getImgIndex(), patchSize, radius, darkOnWhite);
            }
        }
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
    public void refineLandmarkFHT(float[] pieceii, int jj, Point2D[] landmark, int localSize) {
        //try all possible shifts
        DenseDoubleMatrix2D H1 = new DenseDoubleMatrix2D(localSize, localSize);
        H1.assign(pieceii);
        H1.dht2();
        Point2D.Double transl = new Point2D.Double(0, 0);
        Point2D.Double ntr;
        double cls = (localSize - 1) / 2.0;
        float[] piecejj = ts.getSubImagePixels(jj, localSize, localSize, new Point2D.Double(landmark[jj].getX() + transl.getX(), landmark[jj].getY() + transl.getY()), false, false);
        DenseDoubleMatrix2D H2 = new DenseDoubleMatrix2D(localSize, localSize);
        H2.assign(piecejj);
        H2.dht2();
        DenseDoubleMatrix2D res = (DenseDoubleMatrix2D) H1.like();

        MatrixUtils.convolveFD(H1, H2, res, true);
        res.idht2(false);
        double[] max = res.getMaxLocation();
        if (max[1] > cls) max[1] -= localSize;
        if (max[2] > cls) max[2] -= localSize;
        //System.out.println("max found at "+max[1]+", "+max[2]);
        ntr = new Point2D.Double(max[2], max[1]);
        transl = new Point2D.Double(transl.getX() - ntr.getX(), transl.getY() - ntr.getY());
        //System.out.println("before refine landmark was "+landmark[jj]);
        landmark[jj] = new Point2D.Double(landmark[jj].getX() + transl.getX(), landmark[jj].getY() + transl.getY());

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
     */
    public double refineLandmark(int ii, int jj, Point2D[] landmark, int localSize,  boolean goldBead) {

        //is it near border?
        int halfSize = localSize / 2;
        if (!tp.isInsideImage(landmark[ii], halfSize) || !tp.isInsideImage(landmark[jj], halfSize)) {
            //System.out.println("ii:"+landmark[ii]+" , jj:"+landmark[jj]+" , halfsize:"+halfSize);
            return -1;
        }

        //select region of interest
        ts.setAlignmentRoi(localSize, localSize);
        if(goldBead){
            //refineLandmarkPositionDiscus(landmark,ts,jj,localSize,5,true);
            //refineLandmarkPositionGaussian(landmark,ts, ii,localSize,true);
            //refineLandmarkPositionFlipFlap(landmark[jj],jj,localSize);
        }
        float[] pieceii = ts.getSubImagePixels(ii, localSize, localSize, landmark[ii], false, false);
        FloatProcessor fp = null;
        ColorModel cm = ts.getProcessor().getCurrentColorModel();
        if (goldBead) {
            fp = new FloatProcessor(localSize, localSize, pieceii, cm);
            ImagePlus impf = new ImagePlus("symmetry", fp);
            ApplySymmetry_Filter filter = new ApplySymmetry_Filter();
            filter.setup("symmetry=12", impf);
            filter.run(fp);
            //pieceii=createDiscusImage(localSize,5,true);
            //pieceii=createGaussianImage(localSize);
           // pieceii=reference;
            //if(ii==0)impf.show();
        }
        //refineLandmarkFHT(pieceii,jj,landmark,localSize);
        double score = refineLandmark(pieceii, jj, landmark, localSize);

        if(goldBead){
            //refineLandmarkPositionDiscus(landmark,ts,jj,localSize,5,true);
            //refineLandmarkPositionGaussian(landmark,ts, jj,localSize,true);
            //refineLandmarkPositionFlipFlap(landmark[jj],jj,localSize);
            /*if(landmark[jj]==null) return -1;
            pieceii = ts.getSubImagePixels(ii, localSize, localSize, landmark[ii], false, false);
            float[] piecejj= ts.getSubImagePixels(jj, localSize, localSize, landmark[jj], false, false);
            score=AlignImages.unNormalizedCorrelation(pieceii,piecejj);*/
        }
        return score;
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

        return maxval;
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
     * @param originalIndex                initial index
     * @param SeqLength         the minimum length of chain or total length of chain (critical point)
     * @param localSize         size of the patch used in refinement by local refinement
     * @param corrThreshold     minimum correlation to follow on next images
     * @param fixedLengthChains if true follow in each direction until SeqLength is attained, if false continue as long as correlation is above corrThreshold
     * @param goldBead if true use symetrization of reference to improve tracking and use flip/flap correlation to center particule
     * @return a landmark chain
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#generateLandmarkSet(int, int, int, int, double, boolean, boolean)
     *
     */
    public double followSeedsBackAndForth(Point2D.Double[] landmarkchain, int originalIndex, int SeqLength, int localSize, double corrThreshold, boolean fixedLengthChains, boolean goldBead) {
        int halfSeqLength = SeqLength / 2;
        boolean cont = true;
        //follow landmark backward
        int jjmin = Math.max(0, originalIndex - halfSeqLength);
        int jjmax = Math.min(jjmin+SeqLength,ts.getImageStackSize() - 1);
        if (!fixedLengthChains) {
            jjmin = 0;
            jjmax = ts.getImageStackSize() - 1;
        }

        AffineTransform Aij;
        AffineTransform Aji;
        Point2D.Double rcurrent = landmarkchain[originalIndex];
        if (rcurrent == null) return -1;
        Point2D.Double rjj;
        double mincorr = 2;
        double corr1, corr2;
        int jj_1;
        //for(int jj=jjmax;jj>=jjmin;jj--){
        int jj = originalIndex - 1;
        double[] tx = new double[landmarkchain.length];
        double[] ty = new double[landmarkchain.length];

        while (cont && jj >= jjmin) {
            if (completion < 0) return mincorr;
            if (landmarkchain[jj] == null) {
                //compute the affine transformation between jj and jj+1
                jj_1 = jj + 1;
                Aij = ((AffineAlignment)ts.getAlignment()).getTransform(jj, false);
                try {
                    Aji = Aij.createInverse();
                    //estimate position of point in new image
                    Point2D.Double rRef = new Point2D.Double(rcurrent.getX(), rcurrent.getY());
                    rjj = (Point2D.Double) Aji.transform(rcurrent, null);
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);
                    //refinePosition of landmark
                    corr1 = refineLandmark(jj_1, jj, landmarkchain, localSize, goldBead);
                    if(landmarkchain[jj]==null) continue;
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    rjj = (Point2D.Double) Aij.transform(landmarkchain[jj], null);
                    //is this new point found in the same position in reference image?
                    landmarkchain[jj_1] = new Point2D.Double(rjj.getX() - tx[jj_1], rjj.getY() - ty[jj_1]);
                    corr2 = refineLandmark(jj, jj_1, landmarkchain, localSize, goldBead);
                    if(landmarkchain[jj_1]==null) continue;
                    if (Math.sqrt((landmarkchain[jj_1].getX() - rRef.getX()) * (landmarkchain[jj_1].getX() - rRef.getX()) + (landmarkchain[jj_1].getY() - rRef.getY()) * (landmarkchain[jj_1].getY() - rRef.getY())) > 4) {
                        //the backward correlation is not the same I put a malus
                        corr1 -= 0.5;
                        corr2 -= 0.5;
                        landmarkchain[jj_1].setLocation(rRef);
                    } else {
                        landmarkchain[jj_1].setLocation(rRef);
                    }
                    cont = Math.min(corr1, corr2) > corrThreshold || fixedLengthChains;
                    if (cont) {
                        mincorr = Math.min(mincorr, Math.min(corr1, corr2));
                        rcurrent = landmarkchain[jj];
                    } else {
                        landmarkchain[jj] = null;
                    }
                } catch (Exception e) {
                    System.out.println("TomoJPoints followseed B&F error backward ");
                    e.printStackTrace();
                }
            }
            jj--;

        }
        if (completion < 0) return mincorr;
        //follow landmark forward
        rcurrent = landmarkchain[originalIndex];
        jj = originalIndex + 1;
        cont=true;
        while (cont && jj <= jjmax) {
            if (completion < 0) return mincorr;
            //compute the affine transform between jj-1 and jj
            if (landmarkchain[jj] == null) {
                jj_1 = jj - 1;
                Aij = ((AffineAlignment)ts.getAlignment()).getTransform(jj_1, false);
                try {
                    rjj = (Point2D.Double) Aij.transform(rcurrent, null);
                    //estimate position of point in new image
                    landmarkchain[jj] = new Point2D.Double(rjj.getX() + tx[jj_1], rjj.getY() + ty[jj_1]);
                    Point2D.Double rRef = new Point2D.Double(rcurrent.getX(), rcurrent.getY());
                    //refinePosition of landmark
                    corr1 = refineLandmark(jj_1, jj, landmarkchain, localSize, goldBead);
                    if(landmarkchain[jj]==null) continue;
                    tx[jj] = landmarkchain[jj].getX() - rjj.getX();
                    ty[jj] = landmarkchain[jj].getY() - rjj.getY();
                    Aji = Aij.createInverse();
                    rjj = (Point2D.Double) Aji.transform(landmarkchain[jj], null);
                    //is this new point found in the same position in reference image?
                    landmarkchain[jj_1] = new Point2D.Double(rjj.getX() - tx[jj_1], rjj.getY() - ty[jj_1]);
                    corr2 = refineLandmark(jj, jj_1, landmarkchain, localSize, goldBead);
                    if(landmarkchain[jj_1]==null) continue;
                    if (Math.sqrt((landmarkchain[jj_1].getX() - rRef.getX()) * (landmarkchain[jj_1].getX() - rRef.getX()) + (landmarkchain[jj_1].getY() - rRef.getY()) * (landmarkchain[jj_1].getY() - rRef.getY())) > 4) {
                        //the backward correlation is not at the same location I put a malus
                        corr1 -= 0.5;
                        corr2 -= 0.5;
                        landmarkchain[jj_1].setLocation(rRef);
                    } else {
                        landmarkchain[jj_1].setLocation(rRef);
                    }
                    cont = Math.min(corr1, corr2) > corrThreshold || fixedLengthChains;
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

    public double followSeed(Point2D[] landmarkchain, int ii, int SeqLength, int localSize, double corrThreshold, boolean useCriticalPoints) {
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
            if (completion < 0) return mincorr;
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
                    corr = refineLandmark(jj_1, jj, landmarkchain, localSize,  false);
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
                    System.out.println("TomoJPoints followseed error backward ");
                    e.printStackTrace();
                }
            }
            jj--;

        }
        if (completion < 0) return mincorr;
        //follow landmark forward
        if (useCriticalPoints) {
            jjmin = ii + 1;
            jjmax = Math.min(ts.getImageStackSize() - 1, ii + halfSeqLength);
        }
        rcurrent = landmarkchain[ii];
        jj = ii + 1;
        cont=true;
        while (cont && jj <= jjmax) {
            if (completion < 0) return mincorr;
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
                corr = refineLandmark(jj_1, jj, landmarkchain, localSize, false);
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
     * version of generating landmarks in TomoJ with a back and forth validation in the tracking and no refinement<br>
     * it create threads to generate landmarks chains starting from each image.
     *
     * @param seqLength        desired length of landmark chains
     * @param numberOfSamples  number of chains starting from each images kept
     * @param localSize        size (in pixel) of the local patch used to refine position of landmarks (cross correlation - not FFT)
     * @param corrThreshold    correlation threshold
     * @param fixedLength        for critical points true use local minima or false use local maxima
     * @param goldBead      true to use critical version of algo, false to use grid version
     */
    public void generateLandmarkSetWithBackAndForthValidation(final ArrayList<LandmarksChain> Q,final boolean goldBead,final int seqLength, final boolean fixedLength, final int localSize, final double corrThreshold, final boolean fuseLandmarks, final int numberOfSamples) {

        completion = 0;
        nbThreads = Prefs.getThreads();
        exec = Executors.newFixedThreadPool(nbThreads);
        System.out.println("generate Landmark V3(back and forth) using " + nbThreads + " threads" + " / " + Runtime.getRuntime().availableProcessors());

        if(ts.isShowInIJ()) IJ.log("generate Landmark V3(back and forth) using " + nbThreads + " threads" + " / " + Runtime.getRuntime().availableProcessors());


        final ArrayList<LandmarksChain> candidateChainList = new ArrayList<LandmarksChain>(1000);
        final Chrono time = new Chrono();
        Chrono totaltime = new Chrono();
        totaltime.start();

        System.out.println("now follow and refine all seeds");
        IJ.showStatus("now follow and refine all seeds");
        ArrayList<Future> res = new ArrayList<Future>(Q.size());
        final int[] nbfinished = new int[1];
        for (int q = 0; q < Q.size(); q++) {
            //final int ii=i;
            final int qq = q;
            res.add(exec.submit(new Thread() {
                public void run() {
                    if (completion < 0) return;
                    //initiate a new landmark chain
                    LandmarksChain lc = Q.get(qq);
                    if(lc==null){
                        System.out.println(qq+" landmarksChains is null!!!!!");
                    }
                    Point2D.Double[] landmarkchain = lc.getLandmarkchain();
                    // if gold bead try to center correctly the seed
                    if (goldBead) {
                        //refineLandmark(lc.getImgIndex(), lc.getImgIndex(), landmarkchain, localSize, goldBead);
                        //refineLandmarkPositionFlipFlap(landmarkchain[lc.getImgIndex()],lc.getImgIndex(),localSize);
                    }
                    //follow this landmark
                    //time.start();
                    double corr = followSeedsBackAndForth(landmarkchain, lc.getImgIndex(), seqLength, localSize, corrThreshold, fixedLength, goldBead);
                    lc.setCorrelation(corr);

                    synchronized (candidateChainList){candidateChainList.add(lc);}
                    if (completion < 0) return;
                    nbfinished[0]++;
                    completion = nbfinished[0] * 100.0 / Q.size();
                    IJ.showProgress(qq, Q.size());
                    IJ.showStatus("follow seeds " + (nbfinished[0] * 100 / Q.size()) + "%");
                    if(completion%10<0.1) System.out.print("\r                                        \r"+"follow seeds " + (nbfinished[0] * 100 / Q.size()) + "%");

                }

            }));

        }
        try {
            for (Future f : res) {
                f.get();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        IJ.showProgress(0, 0);


        completion++;


        //validate
        System.out.println("validate chains (number of candidates "+candidateChainList.size()+")");
        IJ.showStatus("validate chains");
        if (completion < 0) return;
        if (candidateChainList.size() <= 0) IJ.error("no chains could be found while tracking");
        else {
            ArrayList<LandmarksChain> tmp = validateChains(candidateChainList, fixedLength, numberOfSamples, corrThreshold, seqLength);
            if(tmp.size()<=0) IJ.error("no chains could be validated as correct");
            else {
                ArrayList<Point2D.Double[]> tmp2 = new ArrayList<Point2D.Double[]>(tmp.size());
                double avgScore = 0;
                double minScore = tmp.get(0).getCorrelation();
                double maxScore = tmp.get(0).getCorrelation();
                double avglength = 0;
                int minLength = ts.getImageStackSize();
                int maxLength = 0;
                for (LandmarksChain lc : tmp) {
                    tmp2.add(lc.getLandmarkchain());
                    double corr = lc.getCorrelation();
                    avgScore += corr;
                    if (corr < minScore) minScore = corr;
                    if (corr > maxScore) maxScore = corr;
                    int length = TomoJPoints.getLandmarkLength(lc.getLandmarkchain());
                    avglength += length;
                    if (minLength > length) minLength = length;
                    if (maxLength < length) maxLength = length;
                }
                avglength /= tmp2.size();
                avgScore /= tmp2.size();
                synchronized (tmp2){landmarks.addAll(tmp2);}
                System.out.println(tmp2.size()+" landmark chains found");
                System.out.println("landmarks length : " + avglength + " (" + minLength + ", " + maxLength + ")");
                System.out.println("landmarks correlation : " + avgScore + " (" + minScore + ", " + maxScore + ")");

                if(ts.isShowInIJ()){
                    IJ.log(tmp2.size()+" landmark chains found");
                    IJ.log("landmarks length : " + avglength + " (" + minLength + ", " + maxLength + ")");
                    IJ.log("landmarks correlation : " + avgScore + " (" + minScore + ", " + maxScore + ")");

                }

                //try to fuse landmarks
                if (fuseLandmarks) {
                    if (completion < 0) return;
                    CommandWorkflow.saveLandmarks(IJ.getDirectory("current"),"Landmarks_before_fusion.txt",landmarks);


                    System.out.println("try to fuse landmarks : before " + landmarks.size() + " landmarks");
                    if(ts.isShowInIJ()) IJ.log("try to fuse landmarks : before " + landmarks.size() + " landmarks");

                    IJ.showStatus("fuse landmarks");
                    landmarks = TomoJPoints.tryToFuseLandmarks(landmarks,2);
                    System.out.println("try to fuse landmarks : after " + landmarks.size() + " landmarks");
                    if(ts.isShowInIJ()) IJ.log("try to fuse landmarks : after " + landmarks.size() + " landmarks");

                    tp.removeAllSetsOfPoints();
                    for(Point2D[] l:landmarks){
                        tp.addSetOfPointsSynchronized(l,true);
                    }

                }

                //try recentering with gaussian fit
                if(goldBead){
                    //refineLandmarksPositionGaussian(landmarks,ts,localSize,true);
                }
                //for(int blabla=0; blabla<1000;blabla++){Math.sqrt(blabla);}

                // landmarks were created but not the corresponding markers saying if they are automatically generated or manually
                //so create the automatic generation markers
                if (completion < 0) return;
                while (automaticGeneration.size() < landmarks.size()) {
                    automaticGeneration.add(true);
                }
                avglength = 0;
                minLength = ts.getImageStackSize();
                maxLength = 0;

                for (int i = 1; i < landmarks.size(); i++) {
                    int length = TomoJPoints.getLandmarkLength(landmarks.get(i));
                    //if(length<seqLength)System.out.println("#"+i+" : "+length);
                    //if(length == 0 )System.out.println("length = 0 !!!! -> "+i);
                    avglength += length;
                    if (minLength > length) {
                        minLength = length;
                        //System.out.println("changing minlength #"+i+" new minlength: "+length);
                    }
                    if (maxLength < length) maxLength = length;
                }
                avglength /= landmarks.size();
                System.out.println("landmarks created are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");
                if(ts.isShowInIJ()) IJ.log("landmarks created are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");
            }
        }


        totaltime.stop();
        System.out.println("total computation time " + totaltime.delayString());
        IJ.showStatus("generate landmarks finished in " + totaltime.delayString());
        if(ts.isShowInIJ())IJ.log("generate landmarks finished in " + totaltime.delayString());
    }

    protected ArrayList<LandmarksChain> validateChains(ArrayList<LandmarksChain> candidateChainList, boolean useCriticalPoints, int numberOfSamples, double corrTh, double seqLength) {
        ArrayList<LandmarksChain> localChainList = new ArrayList<LandmarksChain>(1000);
        int count = 0;
        // ArrayList<Double> finalCorr=new ArrayList<Double>(corrQ.size());
        double cx = ts.getCenterX();
        double cy = ts.getCenterY();
        //PlotWindow2 pw=new PlotWindow2();
        //pw.removeAllPlots();
        if (useCriticalPoints) {
            //System.out.println(ii + " refinement finished, now selecting the bests ones ");
            for (int i = 0; i < ts.getImageStackSize(); i++) {
                if (completion < 0) return localChainList;
                //take chain from current image
                ArrayList<LandmarksChain> tmp = new ArrayList<LandmarksChain>();
                synchronized (candidateChainList) {
                    for (LandmarksChain lc : candidateChainList) if (lc.getImgIndex() == i) tmp.add(lc);
                }
                if (tmp.size() > 0) {
                    double[] sortedcorr = new double[tmp.size()];
                    for (int t = 0; t < sortedcorr.length; t++) sortedcorr[t] = tmp.get(t).getCorrelation();
                    //Double[] sortedcorr = (Double[])corrQ.toArray();
                    Arrays.sort(sortedcorr);


                    Double[] xs=new Double[sortedcorr.length];
                    Double[] ys=new Double[sortedcorr.length];
                    for(int x=0;x<xs.length;x++) {
                        xs[x]=new Double(x);
                        ys[x]=sortedcorr[x];
                    }
                    //pw.addPlot(xs,ys, Color.RED,""+i);

                    double thr = Math.max(sortedcorr[sortedcorr.length - 1 - Math.min(sortedcorr.length - 1, numberOfSamples - 1)], corrTh);

                    for (LandmarksChain lc : tmp) {
                        if (lc.getCorrelation() >= thr) {
                            Point2D.Double[] centeredchain = lc.getLandmarkchain();
                            Point2D.Double[] finalChain = new Point2D.Double[centeredchain.length];
                            for (int p = 0; p < finalChain.length; p++) {
                                Point2D.Double pt = centeredchain[p];
                                if (pt != null) {
                                    finalChain[p] = new Point2D.Double(pt.getX() + cx, pt.getY() + cy);
                                }
                            }
                            lc.setLandmarkchain(finalChain);
                            localChainList.add(lc);
                        }
                    }
                }
            }
        } else {
            for (LandmarksChain lc : candidateChainList) {
                if (completion < 0) return localChainList;
                Point2D.Double[] landmarkchain = lc.getLandmarkchain();
                if (lc.getCorrelation() > corrTh && TomoJPoints.getLandmarkLength(landmarkchain) >= seqLength) {
                    Point2D.Double[] finalChain = new Point2D.Double[landmarkchain.length];
                    for (int p = 0; p < finalChain.length; p++) {
                        Point2D.Double pt = landmarkchain[p];
                        if (pt != null) {
                            finalChain[p] = new Point2D.Double(pt.getX() + cx, pt.getY() + cy);
                        }
                    }
                    lc.setLandmarkchain(finalChain);
                    synchronized (localChainList) {
                        localChainList.add(lc);
                    }
                }

            }

        }
        //pw.resetMinMax();
        //pw.setVisible(true);
        return localChainList;
    }

    /**
     * the actual version of generating landmarks in TomoJ<br>
     * it create threads to generate landmarks chains starting from each image.
     *
     * @param seqLength        desired length of landmark chains
     * @param localSize        size (in pixel) of the local patch used to refine position of landmarks (cross correlation - not FFT)
     * @param refinementCycles number of refinement step
     * @param corrThreshold    correlation threshold
     * @see fr.curie.tomoj.landmarks.LandmarksGenerator#threadGenerateLandmarkSet(int, int, int, int, double, int, boolean, boolean)
     */
    public void generateLandmarkSet2(final ArrayList<LandmarksChain> Q,final int seqLength, final int localSize, final int refinementCycles, final double corrThreshold) {

        completion = 0;
        nbThreads = Prefs.getThreads();
        exec = Executors.newFixedThreadPool(nbThreads);
        System.out.println("generate Landmark V2 using " + nbThreads + " threads" + " / " + Runtime.getRuntime().availableProcessors());
        final ArrayList<LandmarksChain> candidateChainList = new ArrayList<LandmarksChain>(1000);
        final Chrono time = new Chrono();
        Chrono totaltime = new Chrono();
        totaltime.start();

        System.out.println("now follow and refine all seeds");
        IJ.showStatus("now follow and refine all seeds");
        Future[] res = new Future[Q.size()];
        final int[] nbfinished = new int[1];
        for (int q = 0; q < Q.size(); q++) {
            //final int ii=i;
            final int qq = q;
            res[q] = exec.submit(new Thread() {
                public void run() {
                    if (completion < 0) return;
                    //initiate a new landmark chain
                    LandmarksChain lc = Q.get(qq);
                    if(lc==null){
                        System.out.println(qq+" landmarksChains is null!!!!!");
                    }
                    Point2D.Double[] landmarkchain = lc.getLandmarkchain();
                    //follow this landmark
                    //time.start();
                    double corr = followSeed(landmarkchain, lc.getImgIndex(), seqLength, localSize, corrThreshold, false);
                    lc.setCorrelation(corr);
                    //time.stop();
                    //totaltimefollow += time.delay();
                    //refine chain
                    //corrQ[q]=refineChainCriticalPoint(landmarkchain,localSize);
                    if ( (corr > corrThreshold && TomoJPoints.getLandmarkLength(landmarkchain) >= seqLength)) {
                        if (completion < 0) return;
                        //time.start();
                        corr = refineChain(landmarkchain, localSize, refinementCycles, corrThreshold, false, true);
                        lc.setCorrelation(corr);
                    }
                    synchronized (candidateChainList){candidateChainList.add(lc);}
                    if (completion < 0) return;
                    nbfinished[0]++;
                    completion = nbfinished[0] * (double)ts.getImageStackSize() / Q.size();
                    IJ.showProgress(qq, Q.size());
                    IJ.showStatus("follow seeds (test)" + (nbfinished[0] * 100 / Q.size()) + "%");

                }

            });

        }
        try {
            for (Future f : res) {
                f.get();
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        IJ.showProgress(0, 0);

        completion++;


        //validate
        System.out.println("validate chains before:"+candidateChainList.size());
        IJ.showStatus("validate chains");
        ArrayList<LandmarksChain> tmp = validateChains(candidateChainList, false, Q.size()/ts.getImageStackSize(), corrThreshold, seqLength);
        ArrayList<Point2D.Double[]> tmp2 = new ArrayList<Point2D.Double[]>(tmp.size());
        double avgScore = 0;
        double minScore = tmp.get(0).getCorrelation();
        double maxScore = tmp.get(0).getCorrelation();
        double avglength = 0;
        int minLength = ts.getImageStackSize();
        int maxLength = 0;
        for (LandmarksChain lc : tmp) {
            tmp2.add(lc.getLandmarkchain());
            double corr = lc.getCorrelation();
            avgScore += corr;
            if (corr < minScore) minScore = corr;
            if (corr > maxScore) maxScore = corr;
            int length = TomoJPoints.getLandmarkLength(lc.getLandmarkchain());
            avglength += length;
            if (minLength > length) minLength = length;
            if (maxLength < length) maxLength = length;
        }
        avglength /= tmp2.size();
        avgScore /= tmp2.size();
        landmarks.addAll(tmp2);
        System.out.println(tmp2.size()+" landmarks chains found");
        System.out.println("landmarks length : " + avglength + " (" + minLength + ", " + maxLength + ")");
        System.out.println("landmarks correlation : " + avgScore + " (" + minScore + ", " + maxScore + ")");

        //try to fuse landmarks
        /*if (fuseLandmarks) {
            System.out.println("try to fuse landmarks : before " + landmarks.size() + " landmarks");
            IJ.showStatus("fuse landmarks");
            landmarks = TomoJPoints.tryToFuseLandmarks(landmarks,2);
            System.out.println("try to fuse landmarks : after " + landmarks.size() + " landmarks");
            tp.removeAllSetsOfPoints();
            for(Point2D[] l:landmarks){
                tp.addSetOfPoints(l,true);
            }
        }*/
        // landmarks were created but not the corresponding markers saying if they are automatically generated or manually
        //so create the automatic generation markers
        while (automaticGeneration.size() < landmarks.size()) {
            automaticGeneration.add(true);
        }
        avglength = 0;
        minLength = ts.getImageStackSize();
        maxLength = 0;

        for (int i = 1; i < landmarks.size(); i++) {
            int length = TomoJPoints.getLandmarkLength(landmarks.get(i));
            //if(length<seqLength)System.out.println("#"+i+" : "+length);
            //if(length == 0 )System.out.println("length = 0 !!!! -> "+i);
            avglength += length;
            if (minLength > length) {
                minLength = length;
                //System.out.println("changing minlength #"+i+" new minlength: "+length);
            }
            if (maxLength < length) maxLength = length;
        }
        avglength /= landmarks.size();
        System.out.println("landmarks created are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");

        totaltime.stop();
        System.out.println("total computation time " + totaltime.delayString());
        IJ.showStatus("generate landmarks finished in " + totaltime.delayString());
    }

    /**
     * refine the chain of landmark :<BR>
     * <UL><LI>by refining landmarks with the average image of all landmarks in the chain </LI>
     * <LI>by refining all alandmarks in the chain with its neighbor</LI></UL>
     *
     * @param chain         the chain to refine
     * @param localSize     size of local patch fo refinement of landmarks
     * @param nbrefinement  how many time do I do this refinement
     * @param corrThreshold threshold for correlation to keep landmarks In the second case of refinement
     * @param goldbead      true to use refinement with average, false for refinement with neighbor
     * @return correlation score of the chain after refinement
     */
    public double refineChain(Point2D[] chain, int localSize, int nbrefinement, double corrThreshold, boolean goldbead, boolean allowRemoval) {
        //if (showInIJ) IJ.log("refine chain");
        double corrChain = 2; // this is the minimum correlation of the chain
        float[] sum;
        for (int K = 0; K < nbrefinement; K++) {
            if (goldbead) {
                //compute the average piece
                sum = computeAverage(chain, localSize);
                if (sum == null) {
                    return -1;
                }
                //System.out.println("should show the average!");
                //new ImagePlus("avg",new FloatProcessor(localSize,localSize,sum,null)).show();
                //align all images with respect to this average
                corrChain = 2;
                for (int i = 0; i < chain.length; i++) {
                    Point2D pt = chain[i];
                    if (pt != null) {
                        corrChain = Math.min(corrChain, refineLandmark(sum, i, chain, localSize));
                        //System.out.println("correlation image "+i+" with average="+corr);
                    }
                }
            } else {
                //refine every step
                for (int step = 0; step < nbrefinement; step++) {
                    //refine forward
                    int ileft = -1;
                    for (int i = 0; i < chain.length - 1 - step; i++) {
                        Point2D rii = chain[i];
                        if (rii != null) {
                            int j = i + 1;
                            Point2D rjj = chain[j];
                            if (rjj != null) {
                                Point2D oldrjj = (Point2D) rjj.clone();
                                double corr = refineLandmark(i, j, chain, localSize,  goldbead);
                                if (corr < corrThreshold) {
                                    ileft = i;
                                    chain[j] = oldrjj;
                                }
                            }
                        }
                    }
                    //System.out.println("refinement after first step length="+getLandmarkLength(chain)+" ileft="+ileft);
                    //refine backward
                    int iright = chain.length;
                    corrChain = 2;
                    for (int i = chain.length - 1; i > 0; i--) {
                        Point2D rii = chain[i];
                        if (rii != null) {
                            int j = i - 1;
                            Point2D rjj = chain[j];
                            if (rjj != null) {
                                Point2D.Double oldrjj = (Point2D.Double) rjj.clone();
                                double corr = refineLandmark(i, j, chain, localSize,  goldbead);
                                if (corr < corrThreshold) {
                                    iright = i;
                                    chain[j] = oldrjj;
                                } else {
                                    corrChain = Math.min(corrChain, corr);
                                }
                            }
                        }
                    }
//System.out.println("refinement after secod step length="+getLandmarkLength(chain)+" ileft="+ileft);
                    //remove bad landmarks from chain
                    if (allowRemoval) {
                        for (int i = 0; i < chain.length; i++) {
                            Point2D rii = chain[i];
                            if (rii != null) {
                                if (i <= ileft || i >= iright) {
                                    chain[i] = null;
                                }
                            }
                        }
                    }
                }
            }

        }
        if (corrChain > 1.1) corrChain = -1.1;
        return corrChain;
    }

    public float[] computeAverage(Point2D[] chain, int sx) {
        int halfsize = sx / 2;
        float[] sum = new float[sx * sx];
        int compt = 0;
        for (int i = 0; i < chain.length; i++) {
            Point2D pt = chain[i];
            if (pt != null) {
                if (!tp.isInsideImage(pt, halfsize)) return null;
                float[] tmp = ts.getSubImagePixels(i, sx, sx, pt, false, false);
                //float[] tmp=ts.getPixelsForAlignment(i,pt);
                //new ImagePlus("cA"+i, new FloatProcessor(sx, sx, tmp, null)).show();
                for (int j = 0; j < sum.length; j++) {
                    sum[j] += tmp[j];
                }
                compt++;
            }
        }
        for (int j = 0; j < sum.length; j++) {
            sum[j] /= compt;
        }
        //new ImagePlus("average"+compt+"points", new FloatProcessor(sx, sx, sum, null)).show();
        return sum;
    }

    public int[] getNumberOfLandmarksOnEachImage(){
        int[] result=new int[tp.getTiltSeries().getImageStackSize()];
        for(int imgnb=0;imgnb<tp.getTiltSeries().getImageStackSize();imgnb++){
            int count=0;
            for (int i = 0; i < landmarks.size(); i++) {
                if (tp.getPoint(i, imgnb) != null) {
                    count++;
                }
            }
            result[imgnb]=count;
        }
        return result;
    }

    public int[] getNumberCommonWithNext(){
        int[] result=new int[tp.getTiltSeries().getImageStackSize()-1];
        for(int imgnb=0;imgnb<tp.getTiltSeries().getImageStackSize()-1;imgnb++){
            int count=0;
            for (int i = 0; i < landmarks.size(); i++) {
                Point2D pt = tp.getPoint(i, imgnb);
                if (pt != null && tp.getPoint(i, imgnb+1) !=null) {
                    count++;
                }
            }
            result[imgnb]=count;
        }
        return result;
    }





}
