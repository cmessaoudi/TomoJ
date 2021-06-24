package fr.curie.tomoj.landmarks;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import fr.curie.tomoj.align.AffineAlignment;
import fr.curie.tomoj.align.Alignment;
import fr.curie.tomoj.align.CenterLandmarks;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.NewImage;
import ij.io.FileInfo;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.MatrixUtils;
import fr.curie.utils.StudentStatisitics;
import ij.process.ImageStatistics;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.Math.toRadians;

/**
 * Created by cedric on 15/10/2014.
 */
public class AlignmentLandmarkImproved implements Alignment {
    int nLandmarks;
    int nImages;
    ArrayList<ProjectionMatrix> currentAi;
    ArrayList<ProjectionMatrix> previousAi;
    ArrayList<DoubleMatrix2D> current_di;
    ArrayList<DoubleMatrix2D> previous_di;
    ArrayList<DoubleMatrix2D> current_rj;
    ArrayList<DoubleMatrix2D> previous_rj;
    ArrayList<DoubleMatrix2D> Wj;
    ArrayList<ArrayList<DoubleMatrix2D>> eij;
    ArrayList<DoubleMatrix1D> landmarkErrorsj;
    //double psiMax = 0;
    ExecutorService exec;
    //ArrayList<DoubleMatrix2D> diaxis;
    private TomoJPoints tp;
    private AlignmentLandmarksOptions options;
    double z0 = 0;
    boolean interrupt = false;
    private boolean verbose = false;
    public BufferedWriter out;

    public AlignmentLandmarkImproved(String path, TomoJPoints tp){
        this.tp=tp;
        current_di=new ArrayList<DoubleMatrix2D>();
        currentAi=new ArrayList<ProjectionMatrix>();
        try {
            loadFromFile(path,1);
        }catch (Exception e){
            e.printStackTrace();
        }
        nImages=current_di.size();

    }
    public AlignmentLandmarkImproved(String dir,String filename, TomoJPoints tp, double binning)throws IOException{
        this.tp=tp;
        current_di=new ArrayList<DoubleMatrix2D>();
        currentAi=new ArrayList<ProjectionMatrix>();
        nImages=tp.getTiltSeries().getImageStackSize();
        try {
            loadFromFile(dir + filename, binning);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
    public AlignmentLandmarkImproved(TomoJPoints tp, AlignmentLandmarksOptions options) {
        this.tp = tp;
        this.options = options;
        nLandmarks = tp.getNumberOfPoints();
        nImages = tp.getTiltSeries().getImageStackSize();
        current_di = new ArrayList<DoubleMatrix2D>(nImages);
        previous_di = new ArrayList<DoubleMatrix2D>(nImages);
        currentAi = new ArrayList<ProjectionMatrix>(nImages);
        previousAi = new ArrayList<ProjectionMatrix>(nImages);
        //diaxis=new ArrayList<DoubleMatrix2D>(nImages);
        for (int i = 0; i < nImages; i++) {
            current_di.add(DoubleFactory2D.dense.make(2, 1));
            previous_di.add(DoubleFactory2D.dense.make(2, 1));
            currentAi.add(new ProjectionMatrix(tp.getTiltSeries().getTiltAngle(i)));
            previousAi.add(new ProjectionMatrix(tp.getTiltSeries().getTiltAngle(i)));
            //System.out.println("create Alignment Lanmark : Ai("+i+") : theta:"+currentAi.get(i).theta);
            //diaxis.add(DoubleFactory2D.dense.make(2,1));
        }
        eij = new ArrayList<ArrayList<DoubleMatrix2D>>(nLandmarks);
        current_rj = new ArrayList<DoubleMatrix2D>(nLandmarks);
        previous_rj = new ArrayList<DoubleMatrix2D>(nLandmarks);
        Wj = new ArrayList<DoubleMatrix2D>(nLandmarks);
        landmarkErrorsj = new ArrayList<DoubleMatrix1D>(nLandmarks);
        for (int j = 0; j < nLandmarks; j++) {
            current_rj.add(DoubleFactory2D.dense.make(3, 1));
            previous_rj.add(DoubleFactory2D.dense.make(3, 1));
            Wj.add(DoubleFactory2D.dense.identity(2));
            eij.add(new ArrayList<DoubleMatrix2D>(nImages));
            for (int i = 0; i < nImages; i++) {
                eij.get(j).add(DoubleFactory2D.dense.make(2, 1));
            }
            landmarkErrorsj.add(DoubleFactory1D.dense.make(4));
        }
    }

    public void setOptions(AlignmentLandmarksOptions options){
        this.options = options;
    }

    /**
     * clear current parameters to default ones
     */
    public void clear() {
        AlignmentLandmarkImproved tmp = new AlignmentLandmarkImproved(tp, options);
        this.copyFrom(tmp);
    }

    /**
     * copies alignment parameters from another
     *
     * @param other alignment to copy
     */
    public void copyFrom(AlignmentLandmarkImproved other) {
        this.tp = other.tp;
        this.options = options;
        this.nLandmarks = other.nLandmarks;
        this.nImages = other.nImages;
        MatrixUtils.getCopy2D(other.current_di, current_di);
        MatrixUtils.getCopy2D(other.previous_di, previous_di);
        currentAi.clear();
        previousAi.clear();
        for (ProjectionMatrix pm : other.currentAi) this.currentAi.add(pm.copy());
        for (ProjectionMatrix pm : other.previousAi) this.previousAi.add(pm.copy());
        MatrixUtils.getCopyArrayList(other.eij, this.eij);
        MatrixUtils.getCopy2D(other.current_rj, this.current_rj);
        MatrixUtils.getCopy2D(other.previous_rj, this.previous_rj);
        MatrixUtils.getCopy2D(other.Wj, this.Wj);
        MatrixUtils.getCopy1D(other.landmarkErrorsj, this.landmarkErrorsj);
    }

    /**
     * interrupt current computation
     */
    public void interrupt() {
        interrupt = true;
    }

    public void initOutputFile(String filename){
        try{
            out=new BufferedWriter(new FileWriter(filename));
        }  catch (Exception e){e.printStackTrace();}
    }
    public void outputLine(String s){
        if(out!=null){
            try{
                out.write(s+"\n");
            } catch (Exception e){e.printStackTrace();}
        }
    }

    public int getnLandmarks() {
        return nLandmarks;
    }

    public ArrayList<ProjectionMatrix> getCurrentAi() {
        return currentAi;
    }

    public ArrayList<DoubleMatrix2D> getCurrent_di() {
        return current_di;
    }

    public void setCurrent_di(ArrayList<DoubleMatrix2D> current_di) {
        this.current_di = current_di;
    }

    public ArrayList<DoubleMatrix2D> getCurrent_rj() {
        return current_rj;
    }

    public void setCurrent_rj(ArrayList<DoubleMatrix2D> current_rj) {
        this.current_rj = current_rj;
    }

    public ArrayList<DoubleMatrix1D> getLandmarkErrorsj() {
        return landmarkErrorsj;
    }

    public ArrayList<DoubleMatrix2D> getWj() {
        return Wj;
    }

    public ArrayList<ArrayList<DoubleMatrix2D>> getEij() {
        return eij;
    }

    /**
     * set the tilt axis parameters
     *
     * @param alpha angle to put tilt axis vertical (Y axis)
     * @param beta  perpendicularity of beam
     */
    public void setUaxis(double alpha, double beta) {
        //verbose=true;
        if(verbose)System.out.println("set uaxis(" + alpha + ", " + beta + ")");
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).setUaxis(alpha, beta);
        }
    }

//    public void setPsiMax(double psiMax) {
//        this.psiMax = psiMax;
//    }

    /**
     * @deprecated
     * @param allowDeformation
     * @param allowInPlaneRotation
     * @return
     */
    public double optimizeSequentially(boolean allowDeformation, boolean allowInPlaneRotation) {
        interrupt = false;
        //compute Ai  //projection Matrix with current parameters = HRpsiRaxisDi
        //computeAis();
        copyCurrentToPrevious();

        //outputLine("first update of dis");
        //firstdis();
        //outputLine("first update of Rjs");
        //firstRjs();
        //saveRjs("D:\\images_test\\phantom\\testCOSS\\new_with_deformation_alignment_rjs.txt");
        if (interrupt) return Double.MAX_VALUE;

        outputLine("compute eijs");
        compute_eijs();
        if (interrupt) return Double.MAX_VALUE;
        boolean finish = false;
        int iteration = 1;
        double bestError = Double.MAX_VALUE;
        double previousError = Double.MAX_VALUE;
        int consecutiveIncrease = 0;
        outputLine("start loop");
        boolean doShift=options.isAllowShifts();
        boolean doMag=options.isDeformMagnification();
        boolean doShrink=false;
        boolean doScalex=false;
        boolean doDelta=false;
        boolean doPsi=false;

        do {
            if (interrupt) return Double.MAX_VALUE;
            //compute Wj
            outputLine("compute Wjs");
            computeWjs(options.getMahalanobisWeight());
            if (interrupt) return Double.MAX_VALUE;
            // copy stuff from iteration k as k-1
            copyCurrentToPrevious();
            if (interrupt) return Double.MAX_VALUE;
            //update rj // 3D landmarks

            outputLine(" update of Rjs");
            updateRjs();
            //saveRjs("D:\\images_test\\phantom\\testCOSS\\new_with_deformation_alignment_rjs"+iteration+".txt");
            copyCurrentToPrevious();
            if (interrupt) return Double.MAX_VALUE;
            outputLine("compute eijs");
            compute_eijs();
            outputLine("compute Wjs");
            computeWjs(options.getMahalanobisWeight());
            //update di // shifts in plane
            if (options.isAllowShifts()) {
                outputLine("update dis");
                update_dis();
            }
            //if correction of deformation
            if (interrupt) return Double.MAX_VALUE;
            if (allowDeformation && iteration > 10) {
                computeDeformation(doMag,doShrink,doScalex,doDelta,false);
                outputLine("compute deformation");
            }
            //update Rpsi // in plane rotation
            if (interrupt) return Double.MAX_VALUE;
            outputLine(" update of Rjs");
            updateRjs();
            copyCurrentToPrevious();
            outputLine("compute eijs After Rjs and before Ai");
            compute_eijs();
            if (allowInPlaneRotation && options.isAllowInPlaneRotation()) update_psiis();

            if (interrupt) return Double.MAX_VALUE;
            outputLine("compute Ais");
            computeAis();
            if (interrupt) return Double.MAX_VALUE;
            outputLine("compute eijs after Ai");
            compute_eijs();
            double currentError = computeGlobalError();
            if (verbose) System.out.println("error : " + currentError);
            //finish= (iteration>20||currentError>previousError);
            finish = ((consecutiveIncrease > 2) || (iteration > 1000) || (Math.abs((currentError - previousError) / previousError) < 0.001)) && iteration > 20;
            if(finish){
                if(doShrink!=options.isDeformShrinkage()){
                    doShrink=options.isDeformShrinkage();
                    finish=false;
                }else if(doScalex!=options.isDeformScalingX()){
                    doScalex=options.isDeformScalingX();
                    finish=false;
                }else if(doDelta!=options.isDeformDelta()){
                    doDelta=options.isDeformDelta();
                    finish=false;
                }
            }
            if (currentError < bestError) bestError = currentError;

            if (currentError < previousError) consecutiveIncrease = 0;
            else {
                consecutiveIncrease++;
                if (consecutiveIncrease > 2) {
                    copyPreviousToCurrent();
                    finish = true;
                }
            }
            previousError = currentError;
            outputLine("loop end of iteration "+iteration);
            iteration++;
        } while (!finish);
        return bestError;
    }

    /**
     * optimization of model/alignment parameters with current tilt axis
     *
     * @param allowDeformation
     * @return
     */
    public double optimize(boolean allowDeformation, boolean allowInPlaneRotation) {
       /* Chrono totaltime= new Chrono(); totaltime.start();
        Chrono stepTime = new Chrono();
        long timeEij=0;
        long timeRjs= 0;
        long timeWj= 0;
        long timeDeform= 0;
        long timeAi= 0;
        long timeCopy= 0;
        long timeDis= 0;
        long timePsis= 0;
        long timeErrors= 0;*/
        interrupt = false;
        //compute Ai  //projection Matrix with current parameters = HRpsiRaxisDi
        //computeAis();
        copyCurrentToPrevious();
        //outputLine("Ais:"+currentAi.get(0).toString());

        //outputLine("first update of dis");
        //firstdis();
        //outputLine("first update of Rjs");
        //firstRjs();
        //saveRjs("D:\\images_test\\phantom\\testCOSS\\new_with_deformation_alignment_rjs.txt");
        if (interrupt) return Double.MAX_VALUE;

        //outputLine("compute eijs");
        //stepTime.start();
        compute_eijs();
        //stepTime.stop(); timeEij+=(stepTime.delay());
        if (interrupt) return Double.MAX_VALUE;
        boolean finish = false;
        int iteration = 1;
        double bestError = Double.MAX_VALUE;
        double previousError = Double.MAX_VALUE;
        int consecutiveIncrease = 0;
        //outputLine("start loop");
        do {
            if (interrupt) return Double.MAX_VALUE;
            //compute Wj
            //outputLine("compute Wjs");
           // stepTime.start();
            computeWjs(options.getMahalanobisWeight());
            //stepTime.stop(); timeWj+=(stepTime.delay());
            if (interrupt) return Double.MAX_VALUE;
            // copy stuff from iteration k as k-1
            //stepTime.start();
            copyCurrentToPrevious();
            //stepTime.stop(); timeCopy+=(stepTime.delay());
            if (interrupt) return Double.MAX_VALUE;
            //update rj // 3D landmarks

            outputLine(" update of Rjs");
            //@TODO removed for debug
            //stepTime.start();
            updateRjs();
            //stepTime.stop(); timeRjs+=(stepTime.delay());

            //saveRjs("D:\\images_test\\phantom\\testAlignDebug\\new_with_deformation_alignment_rjs"+iteration+".txt");
            //stepTime.start();
            copyCurrentToPrevious();
            //stepTime.stop(); timeCopy+=(stepTime.delay());
            if (interrupt) return Double.MAX_VALUE;
            //outputLine("compute eijs");
            //stepTime.start();
            compute_eijs();
            //stepTime.stop(); timeEij+=(stepTime.delay());
            //outputLine("compute Wjs");
            //stepTime.start();
            computeWjs(options.getMahalanobisWeight());
            //stepTime.stop(); timeWj+=(stepTime.delay());
            //update di // shifts in plane
            if (options.isAllowShifts()) {
                //outputLine("update dis");
                //stepTime.start();
                update_dis();
                //stepTime.stop(); timeDis+=(stepTime.delay());
            }
            if (interrupt) return Double.MAX_VALUE;
            //if correct tilt
            if(allowDeformation && options.isAllowTiltCorrection() && iteration > 10){
                System.out.println("update theta");
                updateRjs();
                copyCurrentToPrevious();
                compute_eijs();
                update_thetais();
            }
            //if correction of deformation
            if (interrupt) return Double.MAX_VALUE;
            if (allowDeformation && iteration > 10) {
                //stepTime.start();
                computeDeformation(options.isDeformMagnification(),options.isDeformShrinkage(),options.isDeformScalingX(),options.isDeformDelta(),false);
                //stepTime.stop(); timeDeform+=(stepTime.delay());
                //outputLine("compute deformation");
            }
            //update Rpsi // in plane rotation
            if (interrupt) return Double.MAX_VALUE;
            //outputLine(" update of Rjs");
            //@TODO removed for debug
            //stepTime.start();
            updateRjs();
            //stepTime.stop(); timeRjs+=(stepTime.delay());
            //stepTime.start();
            copyCurrentToPrevious();
            //stepTime.stop(); timeCopy+=(stepTime.delay());
            //outputLine("compute eijs After Rjs and before Ai");
            //stepTime.start();
            compute_eijs();
            //stepTime.stop(); timeEij+=(stepTime.delay());
            //stepTime.start();
            if (allowInPlaneRotation && options.isAllowInPlaneRotation()) update_psiis();
            //stepTime.stop(); timePsis+=stepTime.delay();
            if (interrupt) return Double.MAX_VALUE;
            //outputLine("compute Ais");
            //stepTime.start();
            computeAis();
            //stepTime.stop(); timeAi+=(stepTime.delay());
            if (interrupt) return Double.MAX_VALUE;
            //outputLine("compute eijs after Ai");
            //stepTime.start();
            compute_eijs();
            //stepTime.stop(); timeEij+=(stepTime.delay());
            //stepTime.start();
            double currentError = computeGlobalError();
            //stepTime.stop(); timeErrors+=stepTime.delay();
            if (verbose) System.out.println("error : " + currentError);
            //finish= (iteration>20||currentError>previousError);
            finish = ((consecutiveIncrease > 2) || (iteration > 1000) || (Math.abs((currentError - previousError) / previousError) < 0.001)) && iteration > 20;
            if (currentError < bestError) bestError = currentError;

            if (currentError < previousError) consecutiveIncrease = 0;
            else {
                consecutiveIncrease++;
                if (consecutiveIncrease > 2) {
                    if(allowDeformation&&iteration<=10){
                        iteration=10;
                    }else {
                        copyPreviousToCurrent();
                        finish = true;
                    }
                }
            }
            previousError = currentError;
            //outputLine("Ais:"+currentAi.get(0).toString());
            //for(ProjectionMatrix tmp:currentAi){ outputLine(tmp.toString());}
            //outputLine("loop end of iteration "+iteration);
            iteration++;
        } while (!finish);
        /*totaltime.stop();
        System.out.println("optimize total time : "+totaltime.delayString());
        System.out.println("eijs : "+Chrono.timeString(timeEij));
        System.out.println("Wjs : "+Chrono.timeString(timeWj));
        System.out.println("Rjs : "+Chrono.timeString(timeRjs));
        System.out.println("Ais : "+Chrono.timeString(timeAi));
        System.out.println("Deforms : "+Chrono.timeString(timeDeform));
        System.out.println("Dis : "+Chrono.timeString(timeDis));
        System.out.println("copy : "+Chrono.timeString(timeCopy));
        System.out.println("psi : "+Chrono.timeString(timePsis));
        System.out.println("error : "+Chrono.timeString(timeErrors));
        System.out.println("estimated total : "+Chrono.timeString(timeEij+timeWj+timeRjs+timeAi+timeDeform+timeDis+timeCopy+timePsis+timeErrors));
        */
        return bestError;
    }

    /**
     * computes Ai for all images
     */
    public void computeAis() {
        /*for (int i = 0; i < nImages; i++) {
            currentAi.get(i).computeAi();
        } */
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    currentAi.get(ii).computeAi();
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //*/
    }

    /**
     * storing current parameters (Ai, di and rj) to previous
     */
    public void copyCurrentToPrevious() {
        for (int i = 0; i < nImages; i++) {
            previousAi.get(i).copy(currentAi.get(i));
            previous_di.get(i).assign(current_di.get(i));
        }
        for (int j = 0; j < nLandmarks; j++) {
            previous_rj.get(j).assign(current_rj.get(j));
        }
    }

    /**
     * copy previous prameters (Ai, di and rj) to current
     */
    protected void copyPreviousToCurrent() {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).copy(previousAi.get(i));
            current_di.get(i).assign(previous_di.get(i));
        }
        for (int j = 0; j < nLandmarks; j++) {
            current_rj.get(j).assign(previous_rj.get(j));
        }
    }

    /**
     * computes all new rj
     */
    public void updateRjs() {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        /*for (int j = 0; j < nLandmarks; j++) {
            if (updateRj(j) == null) {
                //removeLandmark(j);
                //System.out.println("should remove Landmark " + j);
                //j--;
            }
        }  //*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        //exec = Executors.newFixedThreadPool(1);
        ArrayList<Future> futures = new ArrayList<Future>(nLandmarks);
        //final int[] j = new int[1];
        for (int j = 0; j < nLandmarks; j++) {
            final int jj = j;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    //System.out.println(jj);
                    updateRj(jj);
                    /*if (updateRj(j[0]) == null) {
                        //removeLandmark(j[0]);
                        //System.out.println("should remove Landmark " + j[0]);
                        //j[0]--;
                    } //*/
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }  //*/
    }


    public DoubleMatrix2D updateRj(int j) {
        DoubleMatrix2D currentRj = current_rj.get(j);
        DoubleMatrix2D sumAitWjAi = DoubleFactory2D.dense.make(3, 3);
        DoubleMatrix2D sumAitWjEij = DoubleFactory2D.dense.make(3, 1);
        for (int i = 0; i < nImages; i++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                DoubleMatrix2D tmp = MatrixUtils.scalarProduct(previousAi.get(i).getAi(), previousAi.get(i).getAi(), Wj.get(j), null);
                /*if(j==0) {
                    System.out.println("theta " + previousAi.get(i).theta);
                    System.out.println("Rthetauaxis " + previousAi.get(i).Rthetauaxis);
                    System.out.println("Ai " + previousAi.get(i).getAi());
                    System.out.println("Wj " + Wj.get(j));
                    System.out.println("AitWjAi " + tmp);
                } */
                sumAitWjAi.assign(tmp, DoubleFunctions.plus);
                sumAitWjEij.assign(MatrixUtils.scalarProduct(previousAi.get(i).getAi(), eij.get(j).get(i), Wj.get(j), null), DoubleFunctions.plus);
            }
        }
        if (new DenseDoubleAlgebra().det(sumAitWjAi) == 0) {
            //System.out.println("!!!!!!!!warning determinant of sumAitWAi is zero : landmark " + j);
            //System.out.println(sumAitWjAi);
            //removeLandmark(j);
            return null;
        }
        currentRj.assign(new DenseDoubleAlgebra().inverse(sumAitWjAi).zMult(sumAitWjEij, null), DoubleFunctions.plus);
        return currentRj;
    }


    public void firstRjs() {
            for (int i = 0; i < nImages; i++) {
                currentAi.get(i).clearAi();
                previousAi.get(i).clearAi();
            }
            /*for (int j = 0; j < nLandmarks; j++) {
                if (updateRj(j) == null) {
                    //removeLandmark(j);
                    //System.out.println("should remove Landmark " + j);
                    //j--;
                }
            }  //*/
            if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
            //exec = Executors.newFixedThreadPool(1);
            ArrayList<Future> futures = new ArrayList<Future>(nLandmarks);
            //final int[] j = new int[1];
            for (int j = 0; j < nLandmarks; j++) {
                final int jj = j;
                futures.add(exec.submit(new Thread() {
                    public void run() {
                        //System.out.println(jj);
                        firstRj(jj);
                        /*if (updateRj(j[0]) == null) {
                            //removeLandmark(j[0]);
                            //System.out.println("should remove Landmark " + j[0]);
                            //j[0]--;
                        } //*/
                    }
                }));
            }
            for (Future fut : futures) {
                try {
                    fut.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }  //*/
        }

    public DoubleMatrix2D firstRj(int j) {
        DoubleMatrix2D currentRj = current_rj.get(j);


        DoubleMatrix2D sumAitAi = DoubleFactory2D.dense.make(3, 3);

        DoubleMatrix2D sumAitPij = DoubleFactory2D.dense.make(3, 1);
        for (int i = 0; i < nImages; i++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                DoubleMatrix2D tmp=currentAi.get(i).getAi().viewDice().zMult(currentAi.get(i).getAi(),null);
                sumAitAi.assign(tmp,DoubleFunctions.plus);
                Point2D pij = tp.getCenteredPoint(j, i);
                DoubleMatrix2D Pij=DoubleFactory2D.dense.make(2,1);
                Pij.setQuick(0,0,pij.getX());
                Pij.setQuick(1,0,pij.getY());
                //Pij.setQuick(0,0,pij.getX()-current_di.get(i).getQuick(0,0));
                //Pij.setQuick(1,0,pij.getY()-current_di.get(i).getQuick(1,0));
                sumAitPij.assign(currentAi.get(i).getAi().viewDice().zMult(Pij,null), DoubleFunctions.plus);
            }
        }
        currentRj.assign(new DenseDoubleAlgebra().inverse(sumAitAi).zMult(sumAitPij, null));
        return currentRj;
    }

    public void firstdis(){
        CenterLandmarks.centerLandmarks(tp);
        for(int i=0;i<nImages;i++){
            DoubleMatrix2D di=DoubleFactory2D.dense.make(2,1);
            AffineTransform t=tp.getTiltSeries().getAlignment().getTransform(i);
            di.setQuick(0,0,t.getTranslateX());
            di.setQuick(1,0,t.getTranslateY());
            current_di.get(i).assign(di);
        }
    }


    protected void compute_eijs() {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        /*for (int j = 0; j < nLandmarks; j++) {
            for (int i = 0; i < nImages; i++) {
                compute_eij(i, j);
            }
        }   //*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
            for (int j = 0; j < nLandmarks; j++) {
                final int jj=j;
                        compute_eij(ii, jj);
                    }
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }  //*/
        if(out!=null){
            try{
                out.write("////////////////////////////////////////////////////////////////////////////\n");
                out.flush();
            }catch (Exception e){e.printStackTrace();}
        }
    }

    protected void compute_eij(int i, int j) {
        Point2D pij = tp.getCenteredPoint(j, i);
        if (pij != null) {
            DoubleMatrix2D error = DoubleFactory2D.dense.make(2, 1);
            error.setQuick(0, 0, pij.getX());
            error.setQuick(0, 1, pij.getY());
            DoubleMatrix2D reprojected = DoubleFactory2D.dense.make(2, 1);
            currentAi.get(i).getAi().zMult(current_rj.get(j), reprojected);
            reprojected.assign(current_di.get(i), DoubleFunctions.plus);
            error.assign(reprojected, DoubleFunctions.minus);
            eij.get(j).get(i).assign(error);
            //if(Math.abs(error.getQuick(0,0))>0.1){
            /*if(out!=null && j==0 && i==0) {
                try {
                    out.write("\n#" + j + ","+i+" rj=" + current_rj.get(j) + "\ndi=" + current_di.get(i) + "\nAi=" + currentAi.get(i).getAi() + "\npij=" + pij + "\nreprojected=" + reprojected + "\neij=" + error+"\n");
                    outputLine("#" + j + ","+i+" Di=" + currentAi.get(i).getDi() + "\nRi=" + currentAi.get(i).getRi());
                    outputLine("#" + j + ","+i+" Rpsii=" + MatrixUtils.rotation3DMatrixZ(currentAi.get(i).psii) + "\nRthetauaxis=" + currentAi.get(i).Rthetauaxis+"\nuaxis_alpha="+currentAi.get(i).rot+"\nuaxis_beta="+currentAi.get(i).tilt+"\ntheta="+currentAi.get(i).theta);
                    out.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            //}//*/

        }
    }


    public void computeWjs(final double lambda) {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        /*for (int j = 0; j < nLandmarks; j++) {
            DoubleMatrix2D tmp = computeWj(j, lambda);
            //System.out.println("size tmp("+j+"): "+tmp);
            //System.out.println("size Wj("+j+"): "+Wj.get(j));
            if (tmp != null) {
                if (Wj.get(j) != null) Wj.get(j).assign(tmp);
                else Wj.set(j, tmp);
            } else {
                //removeLandmark(j);
                System.out.println("should remove Landmark " + j);
                //j--;
            }
        }//*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int j = 0; j < nLandmarks; j++) {
            final int jj = j;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    DoubleMatrix2D tmp = computeWj(jj, lambda);
                    //System.out.println("size tmp("+j+"): "+tmp);
                    //System.out.println("size Wj("+j+"): "+Wj.get(j));
                    if (tmp != null) {
                        if (Wj.get(jj) != null) Wj.get(jj).assign(tmp);
                        else Wj.set(jj, tmp);
                    } else {
                        //removeLandmark(j);
                        //System.out.println("should remove Landmark " + j[0]);
                        //j--;
                    }
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //*/
    }

    public void update_dis() {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    update_di(ii);
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //compute average
        DoubleMatrix2D avg=new DenseDoubleMatrix2D(2,1);
        for(DoubleMatrix2D tmp:current_di){
            avg.assign(tmp, DoubleFunctions.plus);
        }
        avg.assign(DoubleFunctions.div(current_di.size()));
        //correct values
        for(DoubleMatrix2D tmp:current_di){
            tmp.assign(avg, DoubleFunctions.minus);
        }

        /*DoubleMatrix2D di0=current_di.get(tp.getTiltSeries().getZeroIndex()).copy();
        for(DoubleMatrix2D di:current_di){
            di.assign(di0,DoubleFunctions.minus);
        } */
                          //*/
        /*for (int i = 0; i < nImages; i++) {
            update_di2(i);
        } //*/
    }

    public void computeDeformation(boolean doMagnification, boolean doShrinkage, boolean doScalingX, boolean doShearing, boolean smooth) {
        //System.out.println("compute deformation");
        //update mi // global scaling change (change in magnification due to focus change)
        //if (options.isDeformMagnification()) {
        if (doMagnification) {
            updateRjs();
            copyCurrentToPrevious();
            compute_eijs();
            update_mis(smooth);
        }
        //update ti //vertical compression (thinning of sample)
        //if (options.isDeformShrinkage()) {
        if (doShrinkage) {
            updateRjs();
            copyCurrentToPrevious();
            compute_eijs();
            update_tis(smooth);
        }
        //update si // scaling along X (deformation)
        //if (options.isDeformScalingX()) {
        if (doScalingX) {
            updateRjs();
            copyCurrentToPrevious();
            compute_eijs();
            update_sis(smooth);
        }
        //update deltai // shearing
        //if (options.isDeformDelta()) {
        if (doShearing) {
            updateRjs();
            copyCurrentToPrevious();
            compute_eijs();
            update_deltais(smooth);
        }
    }

    public double computeGlobalError() {
        double[] errors = computeErrors();
        double error = 0;
        error += errors[0];
        error += errors[1];
        //error+= computeErrorParameters();
        error /= 2;

        /*double tmp = computeErrorLargeAverageResidual();
        error += tmp;
        //System.out.println("error large average residual" +tmp);
        tmp = computeErrorLargeIsolatedResidual();
        error += tmp;
        //System.out.println("error large isolated residual: "+tmp);
        error /= 2;
        tmp = computeErrorBiasedLandmarks();
//        //System.out.println("error biased landmark" +tmp);
//        error += tmp;
//
        tmp=computeErrorBiasedImage();
       // error+=tmp;

//        error+=computeErrorParameters();
        //error/=3;    */
        return error;
    }


    protected double computeErrorParameters() {
        double error = 0;
        for (ProjectionMatrix Ai : currentAi) {
            if (Ai.mi < 0.5 || Ai.mi > 2) {
                error += 10;
                System.out.println("mi=" + Ai.mi);
            }
            if (Ai.ti < 0.5 || Ai.ti > 2) {
                error += 10;
                System.out.println("ti=" + Ai.ti);
            }
            if (Ai.si < 0.5 || Ai.si > 2) error += 10;
            if (Ai.psii < -10 || Ai.psii > 10) error += 10;
            if (Ai.deltai < -10 || Ai.deltai > 10) error += 10;
        }
        return error;
    }

    /**
     * computes errors  for optimization concern [2]and [3] are removed
     * @return        array with errors [0] is isolated residual (worst error on chains) [1] is average residual  (average distance error) [2] mahalanobis of average error (not distance) [3] average error on images
     */
    public double[] computeErrors() {
        double[] result = new double[4];
        final ArrayList<Double> errors1 = new ArrayList<Double>(nLandmarks);
        final ArrayList<Double> errors2 = new ArrayList<Double>(nLandmarks);
        /*final ArrayList<Double> errors3 = new ArrayList<Double>(nLandmarks);
        final ArrayList<Double> errors4 = new ArrayList<Double>(nLandmarks);*/

        /*final DoubleMatrix2D[] error4i = new DoubleMatrix2D[nImages];
        for (int i = 0; i < nImages; i++) {
            DoubleMatrix2D sumeij = DoubleFactory2D.dense.make(2, 1);
            int count = 0;
            for (int j = 0; j < nLandmarks; j++) {
                if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                    sumeij.assign(eij.get(j).get(i), DoubleFunctions.plus);
                    count++;
                }
            }
            sumeij.assign(DoubleFunctions.div(count));
            error4i[i] = sumeij;
            //System.out.println("error image "+i+" : "+errori[i]);
        }*/
        /*for (int j = 0; j < nLandmarks; j++) {
            double worst = Double.MIN_VALUE;
            DoubleMatrix2D sumeij = DoubleFactory2D.dense.make(2, 1);
            DoubleMatrix2D sumErrorImage = DoubleFactory2D.dense.make(2, 1);
            int count = 0;
            double total = 0;
            for (int i = 0; i < nImages; i++) {
                if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                    DoubleMatrix2D tmp = MatrixUtils.norm(eij.get(j).get(i), Wj.get(j), null);
                    count++;
                    total += tmp.getQuick(0, 0);
                    if (tmp.getQuick(0, 0) > worst) worst = tmp.getQuick(0, 0);
                    sumeij.assign(eij.get(j).get(i), DoubleFunctions.plus);
                    sumErrorImage.assign(error4i[i], DoubleFunctions.plus);
                }
            }
            landmarkErrorsj.get(j).setQuick(0, worst);
            errors1.add(worst);
            landmarkErrorsj.get(j).setQuick(1, total / count);
            errors2.add(landmarkErrorsj.get(j).getQuick(1));
            //error += total / count;
            sumeij.assign(DoubleFunctions.div(count));
            double tmp = MatrixUtils.norm(sumeij, Wj.get(j), null).getQuick(0, 0);
            landmarkErrorsj.get(j).setQuick(2, tmp);
            errors3.add(tmp);
            sumErrorImage.assign(DoubleFunctions.div(count));
            tmp = MatrixUtils.norm(sumErrorImage, Wj.get(j), null).getQuick(0, 0);
            landmarkErrorsj.get(j).setQuick(3, tmp);
            errors4.add(tmp);
        }  //*/

        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int jj = 0; jj < nLandmarks; jj++) {
            final int j = jj;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    double worst = Double.MIN_VALUE;
                    /*DoubleMatrix2D sumeij = DoubleFactory2D.dense.make(2, 1);
                    DoubleMatrix2D sumErrorImage = DoubleFactory2D.dense.make(2, 1);*/
                    int count = 0;
                    double total = 0;
                    for (int i = 0; i < nImages; i++) {
                        if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                            DoubleMatrix2D tmp = MatrixUtils.norm(eij.get(j).get(i), Wj.get(j), null);
                            count++;
                            total += tmp.getQuick(0, 0);
                            if (tmp.getQuick(0, 0) > worst) worst = tmp.getQuick(0, 0);
                            /*sumeij.assign(eij.get(j).get(i), DoubleFunctions.plus);
                            sumErrorImage.assign(error4i[i], DoubleFunctions.plus);*/
                        }
                    }
                    landmarkErrorsj.get(j).setQuick(0, worst);
                    errors1.add(worst);
                    landmarkErrorsj.get(j).setQuick(1, total / count);
                    errors2.add(landmarkErrorsj.get(j).getQuick(1));
                    //error += total / count;
                    /*sumeij.assign(DoubleFunctions.div(count));
                    double tmp = MatrixUtils.norm(sumeij, Wj.get(j), null).getQuick(0, 0);
                    landmarkErrorsj.get(j).setQuick(2, tmp);
                    errors3.add(tmp);
                    sumErrorImage.assign(DoubleFunctions.div(count));
                    tmp = MatrixUtils.norm(sumErrorImage, Wj.get(j), null).getQuick(0, 0);
                    landmarkErrorsj.get(j).setQuick(3, tmp);
                    errors4.add(tmp);*/
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }   //*/
        double[] stats = StudentStatisitics.getStatistics(errors1, 0.05);
        result[0] = stats[4];
        stats = StudentStatisitics.getStatistics(errors2, 0.05);
        result[1] = stats[4];
        /*stats = StudentStatisitics.getStatistics(errors3, 0.05);
        result[2] = stats[4];
        stats = StudentStatisitics.getStatistics(errors4, 0.05);
        result[3] = stats[4];*/
        return result;
    }

    public double computeErrorLargeAverageResidual() {
        ArrayList<Double> errors = new ArrayList<Double>(nLandmarks);
        for (int j = 0; j < nLandmarks; j++) {
            int count = 0;
            double total = 0;
            for (int i = 0; i < nImages; i++) {
                if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                    DoubleMatrix2D tmp = MatrixUtils.norm(eij.get(j).get(i), Wj.get(j), null);
                    count++;
                        /*if(j==0){
                           System.out.println("eij ("+i+","+j+") : "+eij.get(j).get(i));
                           System.out.println("Wj :"+Wj.get(j));
                           System.out.println("eijtWjeij: "+tmp);
                            System.out.println("total: "+total);
                       }      */
                    total += tmp.getQuick(0, 0);
                }
            }
            landmarkErrorsj.get(j).setQuick(1, total / count);
            errors.add(landmarkErrorsj.get(j).getQuick(1));
            //error += total / count;
        }
        double[] stats = StudentStatisitics.getStatistics(errors, 0.05);
        return stats[4];
        //return error / nLandmarks;
    }

    public double computeErrorLargeIsolatedResidual() {
        ArrayList<Double> errors = new ArrayList<Double>(nLandmarks);
        for (int j = 0; j < nLandmarks; j++) {
            double worst = Double.MIN_VALUE;
            for (int i = 0; i < nImages; i++) {
                if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                    DoubleMatrix2D tmp = MatrixUtils.norm(eij.get(j).get(i), Wj.get(j), null);
                    /*if(j==0){
                        System.out.println("eij ("+i+","+j+") : "+eij.get(j).get(i));
                        System.out.println("Wj :"+Wj.get(j));
                        System.out.println("eijtWjeij: "+tmp);
                    }    */
                    if (tmp.getQuick(0, 0) > worst) worst = tmp.getQuick(0, 0);
                }
            }
            landmarkErrorsj.get(j).setQuick(0, worst);
            errors.add(worst);
            //error += worst;
        }
        double[] stats = StudentStatisitics.getStatistics(errors, 0.05);
        return stats[4];
        //return error / nLandmarks;
    }

    public double computeErrorBiasedLandmarks() {
        ArrayList<Double> errors = new ArrayList<Double>(nLandmarks);
        for (int j = 0; j < nLandmarks; j++) {
            if (Wj.get(j) != null) {
                DoubleMatrix2D sumeij = DoubleFactory2D.dense.make(2, 1);
                int count = 0;
                for (int i = 0; i < nImages; i++) {
                    if (tp.getPoint(j, i) != null) {
                        count++;
                        sumeij.assign(eij.get(j).get(i), DoubleFunctions.plus);
                    }
                }
                sumeij.assign(DoubleFunctions.div(count));
                double tmp = MatrixUtils.norm(sumeij, Wj.get(j), null).getQuick(0, 0);
                landmarkErrorsj.get(j).setQuick(2, tmp);
                errors.add(tmp);
                //error += tmp;
            }
        }
        //return error / nLandmarks;
        double[] stats = StudentStatisitics.getStatistics(errors, 0.05);
        return stats[4];
    }

    public double computeErrorBiasedImage() {
        double error = 0;
        ArrayList<Double> errors = new ArrayList<Double>(nLandmarks);
        DoubleMatrix2D[] errori = new DoubleMatrix2D[nImages];
        for (int i = 0; i < nImages; i++) {
            DoubleMatrix2D sumeij = DoubleFactory2D.dense.make(2, 1);
            int count = 0;
            for (int j = 0; j < nLandmarks; j++) {
                if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                    sumeij.assign(eij.get(j).get(i), DoubleFunctions.plus);
                    count++;
                }
            }
            sumeij.assign(DoubleFunctions.div(count));
            errori[i] = sumeij;
            //System.out.println("error image "+i+" : "+errori[i]);
        }
        for (int j = 0; j < nLandmarks; j++) {
            if (Wj.get(j) != null) {
                DoubleMatrix2D sumErrorImage = DoubleFactory2D.dense.make(2, 1);
                int count = 0;
                for (int i = 0; i < nImages; i++) {
                    if (tp.getPoint(j, i) != null) {
                        count++;
                        sumErrorImage.assign(errori[i], DoubleFunctions.plus);
                    }
                }
                sumErrorImage.assign(DoubleFunctions.div(count));
                double tmp = MatrixUtils.norm(sumErrorImage, Wj.get(j), null).getQuick(0, 0);
                landmarkErrorsj.get(j).setQuick(3, tmp);
                errors.add(tmp);
                //error += tmp;
            }
        }
        double[] stats = StudentStatisitics.getStatistics(errors, 0.05);
        return stats[4];
        //return error / nLandmarks;
    }


    public DoubleMatrix2D getReprojectedLandmark(int i, int j) {
        DoubleMatrix2D reprojected = DoubleFactory2D.dense.make(2, 1);
        currentAi.get(i).getAi().zMult(current_rj.get(j), reprojected);
        reprojected.assign(current_di.get(i), DoubleFunctions.plus);
        return reprojected;
    }

    public DoubleMatrix2D computeWj(int j, double lambda) {
        DoubleMatrix2D W = DoubleFactory2D.dense.identity(2);
        //computes eij
        DoubleMatrix2D ej = DoubleFactory2D.dense.make(2, 1);
        int count = 0;
        for (int i = 0; i < nImages; i++) {
            if (tp.getPoint(j, i) != null) {
                count++;
                ej.assign(eij.get(j).get(i), DoubleFunctions.plus);
            }
        }
        ej.assign(DoubleFunctions.div(count));
        DoubleMatrix2D Sj = DoubleFactory2D.dense.make(2, 2);
        for (int i = 0; i < nImages; i++) {
            if (tp.getPoint(j, i) != null) {
                DoubleMatrix2D tmp = eij.get(j).get(i).copy().assign(ej, DoubleFunctions.minus);
                DoubleMatrix2D tmp2 = tmp.zMult(tmp.viewDice(), null);
                Sj.assign(tmp2, DoubleFunctions.plus);
            }
        }
        Sj.assign(DoubleFunctions.div(count - 1));
        //this is to prevent too low values in the covariance matrix
        // we add identity matrix to the covariance matrix
        // this identity matrix could be with a factor different from 1
        Sj.assign(DoubleFactory2D.dense.identity(2), DoubleFunctions.plus);
        //System.out.println("before division sj determinant:"+new DenseDoubleAlgebra().det(Sj));
        if (new DenseDoubleAlgebra().det(Sj) == 0) {
            //System.out.println("!!!!!!!!warning determinant of Sj is zero : landmark " + j);
            //System.out.println("landmark length : " + tp.getLandmarkLength(tp.getCenteredPoints(j)));
            //System.out.println(Sj);
            return DoubleFactory2D.dense.identity(2);
        }
        //System.out.println("after sj determinant:"+new DenseDoubleAlgebra().det(Sj));
        DoubleMatrix2D iSj = new DenseDoubleAlgebra().inverse(Sj);
        outputLine("#"+j+"\nSj="+Sj+"\niSj="+iSj);
        iSj.assign(DoubleFunctions.mult(lambda));
        W.assign(iSj, DoubleFunctions.plus);
        return W;
    }

    public void update_di(int i) {
        if (interrupt) return;
        DoubleMatrix2D current_di = this.current_di.get(i);
        DoubleMatrix2D sumWj = DoubleFactory2D.dense.make(2, 2);
        DoubleMatrix2D sumWjeij = DoubleFactory2D.dense.make(2, 1);
        //int count=0;
        for (int j = 0; j < nLandmarks; j++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                sumWj.assign(Wj.get(j), DoubleFunctions.plus);
                sumWjeij.assign(Wj.get(j).zMult(eij.get(j).get(i), null), DoubleFunctions.plus);
                //count++;
            }
        }
        //System.out.println("sumWj : "+sumWj+"\ninverse sumWj : "+new DenseDoubleAlgebra().inverse(sumWj));
        //current_di.assign(DoubleFunctions.mult(-1));
        current_di.assign(new DenseDoubleAlgebra().inverse(sumWj).zMult(sumWjeij, null), DoubleFunctions.plus);
        //current_di.assign(new DenseDoubleAlgebra().inverse(sumWj).zMult(sumWjeij, null));
        //current_di.assign(previous_di.get(i), DoubleFunctions.minus);
    }

    /**
     *
     * @param i
     * @deprecated
     */
    public void update_di2(int i) {
        if (interrupt) return;
        DoubleMatrix2D current_di = this.current_di.get(i);
        DoubleMatrix2D sumWj = DoubleFactory2D.dense.make(2, 2);
        DoubleMatrix2D sumWjpij = DoubleFactory2D.dense.make(2, 1);
        DoubleMatrix2D sumWjAirj = DoubleFactory2D.dense.make(2, 1);
        DoubleMatrix2D pij = DoubleFactory2D.dense.make(2, 1);
        //DoubleMatrix2D sumpij=DoubleFactory2D.dense.make(2,1);
        //DoubleMatrix2D sumrj=DoubleFactory2D.dense.make(3,1);
        int count = 0;
        for (int j = 0; j < nLandmarks; j++) {
            Point2D point = tp.getCenteredPoint(j, i);
            if (point != null && Wj.get(j) != null) {
                pij.setQuick(0, 0, point.getX());
                pij.setQuick(0, 1, point.getY());
                sumWj.assign(Wj.get(j), DoubleFunctions.plus);
                sumWjpij.assign(Wj.get(j).zMult(pij, null), DoubleFunctions.plus);
                sumWjAirj.assign(Wj.get(j).zMult(currentAi.get(i).getAi(), null).zMult(current_rj.get(j), null), DoubleFunctions.plus);
                //sumpij.assign(pij,DoubleFunctions.plus);
                //sumrj.assign(current_rj.get(j),DoubleFunctions.plus);
                count++;
            }
        }

        sumWjpij.assign(sumWjAirj, DoubleFunctions.minus);
        current_di.assign(new DenseDoubleAlgebra().inverse(sumWj).zMult(sumWjpij, null));
        /*sumpij.assign(DoubleFunctions.div(count));
        if(i==0)System.out.println("#i sumpij:"+sumpij);
        sumrj.assign(DoubleFunctions.div(count));
        if(i==0)System.out.println("#i sumrj:"+sumrj);
        sumpij.assign(currentAi.get(i).getAi().zMult(sumrj,null),DoubleFunctions.minus);
        if(i==0)System.out.println("#i sumpij-Ai*sumrj:"+sumrj);
        current_di.assign(sumpij); */

    }

    public void update_tis(boolean smooth) {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        /*for (int i = 0; i < nImages; i++) {
            update_ti(i);
        } //*/
        //System.out.println("update tis");
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());

        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    update_ti(ii);
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //*/

        //for problems around 0°
        /*copyCurrentToPrevious();
        int i_10=0;
        int i10=nImages-1;
        for(int i=0;i<nImages;i++){
            if(Math.abs(currentAi.get(i10).theta-10)>Math.abs(currentAi.get(i).theta-10)) i10=i;
            if(Math.abs(currentAi.get(i_10).theta+10)>Math.abs(currentAi.get(i).theta+10)) i_10=i;
        }
        for(int i=i_10+1;i<i10;i++){
            double ti= (currentAi.get(i10).theta-currentAi.get(i).theta)/(currentAi.get(i10).theta-currentAi.get(i_10).theta)*currentAi.get(i_10).ti+(currentAi.get(i).theta-currentAi.get(i_10).theta)/(currentAi.get(i10).theta-currentAi.get(i_10).theta)*currentAi.get(i10).ti ;
            currentAi.get(i).ti=ti;
            //currentAi.get(i).ti=avgti;
        }*/
        int width=tp.getTiltSeries().getWidth();
        double angle=Math.toDegrees(Math.acos(width/(width+1.0)));
        for(ProjectionMatrix tmp:currentAi){
            if(Math.abs(tmp.theta)<angle) tmp.ti=1;
        }

        //compute average
        double avgti=0;
        for(ProjectionMatrix tmp:currentAi){
            //if(tmp.theta==tp.getTiltSeries().getZeroIndex()) tmp.ti=1;
            avgti+=tmp.ti;
        }
        avgti/=currentAi.size();
        //correct values
        for(ProjectionMatrix tmp:currentAi){tmp.ti+=1 - avgti;}
        if(smooth) {
            //smoothing the result curves with moving average
            double[] meantis = new double[nImages];
            for (int i = 0; i < nImages - 1; i++) {
                double sumti = 0;
                int count = 0;
                //if(i-3>=0) {sumti+=currentAi.get(i-3).ti; count++;}
                if(i-2>=0) {sumti+=currentAi.get(i-2).ti; count++;}
                if (i - 1 >= 0) {
                    sumti += currentAi.get(i - 1).ti;
                    count++;
                }
                sumti += currentAi.get(i).ti;
                count++;
                if (i + 1 < nImages) {  sumti += currentAi.get(i + 1).ti;count++;}
                if(i+2<nImages){sumti+=currentAi.get(i+2).ti;  count++;}
                //if(i+3<nImages){sumti+=currentAi.get(i+3).ti;   count++;}
                meantis[i] = sumti / count;
            }
            for (int i = 0; i < nImages - 1; i++) {
                currentAi.get(i).ti = meantis[i];
            }
        }

    }

    public void update_mis(boolean smooth) {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        /*for (int i = 0; i < nImages; i++) {
            update_mi(i);
        }  //*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    update_mi(ii);
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } //**/
        //compute average
        double avgmi=0;
        for(ProjectionMatrix tmp:currentAi){avgmi+=tmp.mi;}
        avgmi/=currentAi.size();
        //correct values
        for(ProjectionMatrix tmp:currentAi){tmp.mi+=1 - avgmi;}
        if (smooth) {
            //smoothing the result curves with moving average
            double[] meanmis = new double[nImages];
            for (int i = 0; i < nImages - 1; i++) {
                double summi = 0;
                int count = 0;
                //if(i-3>=0) {summi+=currentAi.get(i-3).mi; count++;}
                if(i-2>=0) {summi+=currentAi.get(i-2).mi; count++;}
                if (i - 1 >= 0) {
                    summi += currentAi.get(i - 1).mi;
                    count++;
                }
                summi += currentAi.get(i).mi;
                count++;
                if (i + 1 < nImages) {summi += currentAi.get(i + 1).mi; count++;}
                if(i+2<nImages){summi+=currentAi.get(i+2).mi;  count++;}
                //if(i+3<nImages){summi+=currentAi.get(i+3).mi;   count++;}
                meanmis[i] = summi / count;
            }
            for (int i = 0; i < nImages - 1; i++) {
                currentAi.get(i).mi = meanmis[i];
            }
        }
    }

    public void update_sis(boolean smooth) {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        /*for (int i = 0; i < nImages; i++) {
            update_si(i);
        }//*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    update_si(ii);
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } //*/

        //compute average
        double avgsi=0;
        for(ProjectionMatrix tmp:currentAi){avgsi+=tmp.si;}
        avgsi/=currentAi.size();
        //correct values
        for(ProjectionMatrix tmp:currentAi){tmp.si+=1 - avgsi;}

        if(smooth) {
            //smoothing the result curves with moving average
            double[] meansis = new double[nImages];
            for (int i = 0; i < nImages - 1; i++) {
                double sumsi = 0;
                int count = 0;
                //if(i-3>=0) {sumsi+=currentAi.get(i-3).si; count++;}
                if(i-2>=0) {sumsi+=currentAi.get(i-2).si; count++;}
                if (i - 1 >= 0) {
                    sumsi += currentAi.get(i - 1).si;
                    count++;
                }
                sumsi += currentAi.get(i).si;
                count++;
                if (i + 1 < nImages) {sumsi += currentAi.get(i + 1).si;count++;}
                if(i+2<nImages){sumsi+=currentAi.get(i+2).si;  count++;}
                //if(i+3<nImages){sumsi+=currentAi.get(i+3).ti;   count++;}
                meansis[i] = sumsi / count;
            }
            for (int i = 0; i < nImages - 1; i++) {
                currentAi.get(i).si = meansis[i];
            }
        }


    }

    public void update_deltais(boolean smooth) {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
           /*for (int i = 0; i < nImages; i++) {
               update_si(i);
           }//*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    update_deltai(ii);
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } //*/

        //compute average
        double avgdeltai=0;
        for(ProjectionMatrix tmp:currentAi){avgdeltai+=tmp.deltai;}
        avgdeltai/=currentAi.size();
        //correct values
        for(ProjectionMatrix tmp:currentAi){tmp.deltai -= avgdeltai;}

        if(smooth) {
            //smoothing the result curves with moving average
            double[] meandeltais = new double[nImages];
            for (int i = 0; i < nImages - 1; i++) {
                double sumdeltai = 0;
                int count = 0;
                //if(i-3>=0) {sumdeltai+=currentAi.get(i-3).deltai; count++;}
                if(i-2>=0) {sumdeltai+=currentAi.get(i-2).deltai; count++;}
                if (i - 1 >= 0) {
                    sumdeltai += currentAi.get(i - 1).deltai;
                    count++;
                }
                sumdeltai += currentAi.get(i).deltai;
                count++;
                if (i + 1 < nImages) { sumdeltai += currentAi.get(i + 1).deltai; count++;}
                if(i+2<nImages){sumdeltai+=currentAi.get(i+2).deltai;  count++;}
                //if(i+3<nImages){sumdeltai+=currentAi.get(i+3).deltai;   count++;}
                meandeltais[i] = sumdeltai / count;
            }
            for (int i = 0; i < nImages - 1; i++) {
                currentAi.get(i).deltai = meandeltais[i];
            }
        }
    }

    public void update_psiis() {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
               /*for (int i = 0; i < nImages; i++) {
                   update_si(i);
               }//*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    update_psii(ii);
                }
            }));
        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //compute average
        double avgpsii=0;
        for(ProjectionMatrix tmp:currentAi){avgpsii+=tmp.psii;}
        avgpsii/=currentAi.size();
        //correct values
        for(ProjectionMatrix tmp:currentAi){tmp.psii -= avgpsii;}
        //*/
    }

    public void update_thetais() {
        for (int i = 0; i < nImages; i++) {
            currentAi.get(i).clearAi();
            previousAi.get(i).clearAi();
        }
        /*for (int i = 0; i < nImages; i++) {
            update_mi(i);
        }  //*/
        if (exec == null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        ArrayList<Future> futures = new ArrayList<Future>(nImages);
        for (int i = 0; i < nImages; i++) {
            final int ii = i;
            //if(ii==0)System.out.println("update thetais("+ii+") before : current:"+currentAi.get(i).getTheta()+" previous:"+previousAi.get(i).getTheta());
            futures.add(exec.submit(new Thread() {
                public void run() {
                    update_thetai(ii);
                }
            }));
            if(ii==0)System.out.println("update thetais("+ii+") after : current:"+currentAi.get(i).getTheta()+" previous:"+previousAi.get(i).getTheta());

        }
        for (Future fut : futures) {
            try {
                fut.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } //**/
        //compute average

    }

    public void update_ti(int i) {
        if (interrupt) return;
        double current_ti = previousAi.get(i).ti;
        //System.out.println("currenti : "+current_ti);
        double sumHRirjzZeijWj = 0;
        DoubleMatrix2D HRirjzZ = DoubleFactory2D.dense.make(2, 1);
        double sumHRirjzZ = 0;
        DoubleMatrix2D Zaxis = DoubleFactory2D.dense.make(3, 1);
        Zaxis.setQuick(0, 2, 1);
        ArrayList<DoubleMatrix2D> tmp = null;
        for (int j = 0; j < nLandmarks; j++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                //System.out.println("landmark i:"+i+" , j: "+j);
                HRirjzZ = previousAi.get(i).getHRi().copy().assign(DoubleFunctions.mult(current_rj.get(j).getQuick(0, 2))).zMult(Zaxis, null);
                //System.out.println("before scalar" + HRirjzZ);
                tmp = MatrixUtils.scalarAndNorm2(HRirjzZ, Wj.get(j), eij.get(j).get(i), tmp);
                //System.out.println("after scalar "+tmp.size());
                sumHRirjzZeijWj += tmp.get(0).getQuick(0, 0);
                sumHRirjzZ += tmp.get(1).getQuick(0, 0);
                //System.out.println("sumHRirjzZeijWj= "+sumHRirjzZeijWj+", sumHRirjzZ= "+sumHRirjzZ);
                //sumHRirjzZeijWj += MatrixUtils.scalarProduct(HRirjzZ, eij.get(j).get(i), Wj.get(j), null).getQuick(0, 0);
                //sumHRirjzZ += MatrixUtils.norm(HRirjzZ, Wj.get(j), null).assign(DoubleFunctions.square).getQuick(0, 0);
            }
        }
        double tmp2 = 1 / previousAi.get(i).mi * sumHRirjzZeijWj / sumHRirjzZ;
        if (sumHRirjzZ == 0 || Math.abs(tmp2) > 1) tmp2 = 0;
        current_ti += tmp2;
        /*if(i==30){
            System.out.println("update ti:  previous ti:"+previousAi.get(i).ti+" previous mi:"+previousAi.get(i).mi+"  sumHRirjzZeijWj:"+sumHRirjzZeijWj+" sum HRirjzz:"+sumHRirjzZ+" modification:"+tmp2+" current ti:"+current_ti);
        }*/
        if (!Double.isNaN(current_ti)) {
            if (current_ti < 0.5) {
                //System.out.println("#"+i+" ti  is too small : "+current_ti);
                current_ti = 0.5;
            }
            if (current_ti > 2) {
                //System.out.println("#"+i+" ti  is too big : "+current_ti);
                current_ti = 2;
            }
            currentAi.get(i).ti = current_ti;
        }
        //System.out.println("modified ti : "+current_ti +"      in storage: "+currentAi.get(i).ti );
        //if(i==0) System.out.println("update_ti before ti="+previousAi.get(i).ti+" after ti="+current_ti);
    }

    public void update_mi(int i) {
        if (interrupt) return;
        double current_mi = previousAi.get(i).mi;
        double sumAirjeiWj = 0;
        DoubleMatrix2D Airj = DoubleFactory2D.dense.make(2, 1);
        double sumAirj = 0;
        ArrayList<DoubleMatrix2D> tmp = null;
        for (int j = 0; j < nLandmarks; j++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                previousAi.get(i).Ai.zMult(current_rj.get(j), Airj);
                tmp = MatrixUtils.scalarAndNorm2(Airj, Wj.get(j), eij.get(j).get(i), tmp);
                sumAirjeiWj += tmp.get(0).getQuick(0, 0);
                sumAirj += tmp.get(1).getQuick(0, 0);
                //sumAirjeiWj += MatrixUtils.scalarProduct(Airj, eij.get(j).get(i), Wj.get(j), null).getQuick(0, 0);
                //sumAirj += MatrixUtils.norm(Airj, Wj.get(j), null).assign(DoubleFunctions.square).getQuick(0, 0);
            }
        }
        current_mi += current_mi * (sumAirjeiWj / sumAirj);
        if (!Double.isNaN(current_mi)) currentAi.get(i).mi = current_mi;
        //if(i==0) System.out.println("update_mi before mi="+previousAi.get(i).mi+" after mi="+current_mi);
    }

    public void update_si(int i) {
        if (interrupt) return;
        double current_si = previousAi.get(i).si;
        double sumAirjxXeiWj = 0;
        DoubleMatrix2D AirjxX = DoubleFactory2D.dense.make(2, 1);
        double sumAirjxX = 0;
        DoubleMatrix2D Xaxis = DoubleFactory2D.dense.make(3, 1);
        Xaxis.setQuick(0, 0, 1);
        ArrayList<DoubleMatrix2D> tmp = null;
        for (int j = 0; j < nLandmarks; j++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                AirjxX = previousAi.get(i).Ai.copy().assign(DoubleFunctions.mult(current_rj.get(j).getQuick(0, 0))).zMult(Xaxis, null);
                tmp = MatrixUtils.scalarAndNorm2(AirjxX, Wj.get(j), eij.get(j).get(i), tmp);
                sumAirjxXeiWj += tmp.get(0).getQuick(0, 0);
                sumAirjxX += tmp.get(1).getQuick(0, 0);
                //sumAirjxXeiWj += MatrixUtils.scalarProduct(AirjxX, eij.get(j).get(i), Wj.get(j), null).getQuick(0, 0);
                //sumAirjxX += MatrixUtils.norm(AirjxX, Wj.get(j), null).assign(DoubleFunctions.square).getQuick(0, 0);
            }
        }
        current_si *= 1 + sumAirjxXeiWj / sumAirjxX;
        if (!Double.isNaN(current_si)) currentAi.get(i).si = current_si;
    }



    private double smallErrorComputation(int i){
        double error1=0;
        for(int j=0;j<nLandmarks;j++){
            Point2D pij = tp.getCenteredPoint(j, i);
            if (pij != null) {
                DoubleMatrix2D error = DoubleFactory2D.dense.make(2, 1);
                error.setQuick(0, 0, pij.getX());
                error.setQuick(0, 1, pij.getY());
                DoubleMatrix2D reprojected = DoubleFactory2D.dense.make(2, 1);
                currentAi.get(i).getAi().zMult(current_rj.get(j), reprojected);
                reprojected.assign(current_di.get(i), DoubleFunctions.plus);
                error.assign(reprojected, DoubleFunctions.minus);
                error1+=error.getQuick(0,0)*error.getQuick(0,0)+error.getQuick(1,0)*error.getQuick(1,0);
            }
        }
        return error1;
    }

    public void update_deltai(int i) {
        if (interrupt) return;
        double current_deltai = previousAi.get(i).deltai;
        double A = 0;
        double B = 0;
        double C = 0;
        DoubleMatrix2D P = DoubleFactory2D.dense.make(3, 3);
        P.setQuick(1, 0, 1);
        P.setQuick(0, 1, -1);
        P.setQuick(2, 2, 1);
        DoubleMatrix2D Xaxis = DoubleFactory2D.dense.make(3, 1);
        Xaxis.setQuick(0, 0, 1);
        DoubleMatrix2D D1 = DoubleFactory2D.dense.make(3, 3);
        D1.setQuick(0, 0, 1);
        DoubleMatrix2D D2 = DoubleFactory2D.dense.make(3, 3);
        D2.setQuick(1, 0, 1);
        DoubleMatrix2D HRiPDi = previousAi.get(i).getHRi().zMult(P, null).zMult(previousAi.get(i).getDi(), null);
        DoubleMatrix2D HRiD1 = previousAi.get(i).getHRi().zMult(D1, null);
        DoubleMatrix2D HRiD2 = previousAi.get(i).getHRi().zMult(D2, null);
        for (int j = 0; j < nLandmarks; j++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                DoubleMatrix2D HRiPDirjxx = HRiPDi.copy().assign(DoubleFunctions.mult(current_rj.get(j).getQuick(0, 0))).zMult(Xaxis, null);
                DoubleMatrix2D HRiD1rjxx = HRiD1.copy().assign(DoubleFunctions.mult(current_rj.get(j).getQuick(0, 0))).zMult(Xaxis, null);
                DoubleMatrix2D HRiD2rjxx = HRiD2.copy().assign(DoubleFunctions.mult(current_rj.get(j).getQuick(0, 0))).zMult(Xaxis, null);
                DoubleMatrix2D tmp = MatrixUtils.scalarProduct(HRiPDirjxx, HRiD1rjxx, Wj.get(j), null);
                A += tmp.getQuick(0, 0);
                tmp = MatrixUtils.scalarProduct(HRiPDirjxx, HRiD2rjxx, Wj.get(j), null);
                B += tmp.getQuick(0, 0);
                tmp = MatrixUtils.scalarProduct(HRiPDirjxx, eij.get(j).get(i), Wj.get(j), null);
                C += tmp.getQuick(0, 0);
            }
        }
        double D = C + Math.cos(Math.toRadians(current_deltai)) * A + Math.sin(Math.toRadians(current_deltai)) * B;
        double sindeltai1=(B*D+A*Math.sqrt(A*A+B*B-D*D))/(A*A+B*B);
        double sindeltai2=(B*D-A*Math.sqrt(A*A+B*B-D*D))/(A*A+B*B);
        double cosdelta = (A * D + B * Math.sqrt(A * A + B * B - D * D)) / (A * A + B * B);
        double cosdelta2 = (A * D - B * Math.sqrt(A * A + B * B - D * D)) / (A * A + B * B);
        double angle1=Math.toDegrees(Math.atan2(sindeltai1, cosdelta));
        currentAi.get(i).deltai=angle1;
        currentAi.get(i).computeAi();
        double error1=smallErrorComputation(i);

        double angle2=Math.toDegrees(Math.atan2(sindeltai1, cosdelta2));
        currentAi.get(i).deltai=angle2;
        currentAi.get(i).computeAi();
        double error2=smallErrorComputation(i);

        double angle3=Math.toDegrees(Math.atan2(sindeltai2, cosdelta));
        currentAi.get(i).deltai=angle3;
        currentAi.get(i).computeAi();
        double error3=smallErrorComputation(i);

        double angle4=Math.toDegrees(Math.atan2(sindeltai2, cosdelta2));
        currentAi.get(i).deltai=angle4;
        currentAi.get(i).computeAi();
        double error4=smallErrorComputation(i);
        double angle = angle1;
        double error=error1;
        if(error>error2) {
            angle=angle2;
            error=error2;
        }
        if(error>error3) {
            angle=angle3;
            error=error3;
        }
        if(error>error4) {
            angle=angle4;
            error=error4;
        }

        currentAi.get(i).deltai = angle;
        if(i==15){
            outputLine("sin1="+sindeltai1+"\tsin2="+sindeltai2+"\ncos1="+cosdelta+"\tcos2="+cosdelta2+"\nA="+A+"\nB="+B+"\nD="+D);
            outputLine("\nangle1="+angle1+"\terror1="+error1+"\nangle2="+angle2+"\terror2="+error2+"\nangle3="+angle3+"\terror3="+error3+"\nangle4="+angle4+"\terror4="+error4);
        }

        /*cosdelta = Math.toDegrees(Math.acos(cosdelta));
        if (!Double.isNaN(cosdelta)) {
            if (cosdelta > 90) cosdelta -= 180;
            currentAi.get(i).deltai = cosdelta;
        } else {
            currentAi.get(i).deltai = previousAi.get(i).deltai;
            System.out.println("NaN in compute deltai (" + i + ")");
        }  */

    }

    public void update_psii(int i) {
        if (interrupt) return;
        //   System.out.println("compute psii("+i+")");
        double current_psii = previousAi.get(i).psii;
        double A = 0;
        double B = 0;
        double C = 0;
        DoubleMatrix2D P = DoubleFactory2D.dense.make(3, 3);
        P.setQuick(1, 0, 1);
        P.setQuick(0, 1, -1);
        P.setQuick(2, 2, 1);
        DoubleMatrix2D HP = P.viewPart(0, 0, 2, 3);
        //System.out.println("P:"+P);
        DoubleMatrix2D HPRiDi = HP.zMult(previousAi.get(i).getRi(), null);
        //System.out.println("PRi:"+HPRiDi);
        HPRiDi = HPRiDi.zMult(previousAi.get(i).getDi(), null);
        //System.out.println("PRiDi:"+HPRiDi);
        DoubleMatrix2D RthetaDi = previousAi.get(i).Rthetauaxis.zMult(previousAi.get(i).getDi(), null);
        //System.out.println("RthetaDi:"+RthetaDi);
        for (int j = 0; j < nLandmarks; j++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                //System.out.println("point "+i+", "+j+" exists");
                DoubleMatrix2D HPRiDirj = HPRiDi.zMult(current_rj.get(j), null);
                //System.out.println("HPPRiDirj:"+HPRiDi);
                DoubleMatrix2D RthetaDirj = RthetaDi.zMult(current_rj.get(j), null);
                //System.out.println("RthetaDirj:"+RthetaDirj);
                DoubleMatrix2D HRthetaDirj = RthetaDirj.viewPart(0, 0, 2, 1);
                //System.out.println("HRthetaDirj:"+HRthetaDirj);
                DoubleMatrix2D tmp = MatrixUtils.scalarProduct(HPRiDirj, HRthetaDirj, Wj.get(j), null);
                //System.out.println("tmp:"+tmp);
                A += tmp.getQuick(0, 0);
                //System.out.println("A:"+A);
                DoubleMatrix2D HPRthetaDirj = HP.zMult(RthetaDirj, null);
                tmp = MatrixUtils.scalarProduct(HPRiDirj, HPRthetaDirj, Wj.get(j), null);
                B += tmp.getQuick(0, 0);
                //System.out.println("B:"+B);
                tmp = MatrixUtils.scalarProduct(HPRiDirj, eij.get(j).get(i), Wj.get(j), null);
                C += tmp.getQuick(0, 0);
                //System.out.println("C:"+C);
            }
        }
        //System.out.println("psii end of loop");
        double D = C + Math.cos(Math.toRadians(current_psii)) * A + Math.sin(Math.toRadians(current_psii)) * B;
        /*//double sinpsii1=(B*D+A*Math.sqrt(A*A+B*B-D*D))/(A*A+B*B);
        //double sinpsii2=(B*D-A*Math.sqrt(A*A+B*B-D*D))/(A*A+B*B);
        double cospsi = (A * D + B * Math.sqrt(A * A + B * B - D * D)) / (A * A + B * B);
        //double cospsi2=(A*D-B*Math.sqrt(A*A+B*B-D*D))/(A*A+B*B);
        //if(i==0)System.out.println("#i"+i+" cospsi="+cospsi+" cospsi2="+cospsi2+" sinpsi1="+sinpsii1+" sinpsi2="+sinpsii2);
        cospsi = Math.toDegrees(Math.acos(cospsi));
        //cospsi2=Math.toDegrees(Math.acos(cospsi2));
        //if(i==0)System.out.println("#i"+i+" psi(via cos)="+cospsi+" psi(via cos2)="+cospsi2 +" psi (via sin1)="+Math.toDegrees(Math.asin(sinpsii1))+" psi(via sin2)="+Math.toDegrees(Math.asin(sinpsii2)));
        if (!Double.isNaN(cospsi)) {
            if (cospsi > 90) cospsi -= 180;
            //if(cospsi2>90) cospsi2-=180;
            //currentAi.get(i).psii=(Math.abs(cospsi-previousAi.get(i).psii)<Math.abs(cospsi2-previousAi.get(i).psii))?cospsi:cospsi2;
            currentAi.get(i).psii = cospsi;
        } else {
            currentAi.get(i).psii = previousAi.get(i).psii;
            System.out.println("NaN in compute psii (" + i + ")");
        }                    */

        double sinpsii1=(B*D+A*Math.sqrt(A*A+B*B-D*D))/(A*A+B*B);
               double sinpsii2=(B*D-A*Math.sqrt(A*A+B*B-D*D))/(A*A+B*B);
               double cospsii1 = (A * D + B * Math.sqrt(A * A + B * B - D * D)) / (A * A + B * B);
               double cospsii2 = (A * D - B * Math.sqrt(A * A + B * B - D * D)) / (A * A + B * B);
               double angle1=Math.toDegrees(Math.atan2(sinpsii1,cospsii1));
               currentAi.get(i).psii=angle1;
               currentAi.get(i).computeAi();
               double error1=smallErrorComputation(i);

               double angle2=Math.toDegrees(Math.atan2(sinpsii1,cospsii2));
               currentAi.get(i).psii=angle2;
               currentAi.get(i).computeAi();
               double error2=smallErrorComputation(i);

               double angle3=Math.toDegrees(Math.atan2(sinpsii2,cospsii1));
               currentAi.get(i).psii=angle3;
               currentAi.get(i).computeAi();
               double error3=smallErrorComputation(i);

               double angle4=Math.toDegrees(Math.atan2(sinpsii2,cospsii2));
               currentAi.get(i).psii=angle4;
               currentAi.get(i).computeAi();
               double error4=smallErrorComputation(i);
               double angle = angle1;
               double error=error1;
               if(error>error2) {
                   angle=angle2;
                   error=error2;
               }
               if(error>error3) {
                   angle=angle3;
                   error=error3;
               }
               if(error>error4) {
                   angle=angle4;
                   error=error4;
               }

               currentAi.get(i).psii = angle;

        // System.out.println("end compute psii("+i+")");
    }

    public void update_thetai(int i) {
        if (interrupt) return;
        DoubleMatrix2D P = DoubleFactory2D.dense.make(3, 3);
        P.setQuick(1, 0, 1);
        P.setQuick(0, 1, -1);
        P.setQuick(2, 2, 1);
        ProjectionMatrix Ai=currentAi.get(i);
        if(Ai==null){
            System.out.println("update theta : Ai is null!!!!");
            return;
        }
        DoubleMatrix2D HXi=Ai.getXi().viewPart(0,0,2,3);
        DoubleMatrix2D HXiLXiR = Ai.getXiL().zMult(Ai.getXiR(),null).viewPart(0,0,2,3);
        DoubleMatrix2D HXiLPXiR = Ai.getXiL().zMult(P,null).zMult(Ai.getXiR(),null).viewPart(0,0,2,3);


        double current_thetai = Ai.theta;
        double A= 0;
        DoubleMatrix2D HXirj = DoubleFactory2D.dense.make(2, 1);
        DoubleMatrix2D HXiLXiRrj = DoubleFactory2D.dense.make(2, 1);
        double B = 0;
        DoubleMatrix2D HXiLPXiRrj  = DoubleFactory2D.dense.make(2, 1);
        ArrayList<DoubleMatrix2D> tmp = null;
        for (int j = 0; j < nLandmarks; j++) {
            if (tp.getPoint(j, i) != null && Wj.get(j) != null) {
                HXi.zMult(current_rj.get(j),HXirj);
                HXiLXiR.zMult(current_rj.get(j),HXiLXiRrj);
                HXiLPXiR.zMult(current_rj.get(j),HXiLPXiRrj);
                tmp=MatrixUtils.scalarAndNorm2(HXirj,Wj.get(j),HXiLXiRrj,tmp);
                A+=tmp.get(0).getQuick(0,0);
                tmp=MatrixUtils.scalarAndNorm2(HXirj,Wj.get(j),HXiLPXiRrj,tmp);
                B+=tmp.get(0).getQuick(0,0);
            }
        }
        double K= A*Math.cos(Math.toRadians(current_thetai)) + B*Math.sin(Math.toRadians(current_thetai));
        double num=Math.sqrt(A*A + B*B - (K*K));
        double denom=A*A + B*B;
        double x1= (A*K + B*num )/denom;
        double x2= (A*K - B*num )/denom;
        double y1= (B*K - A*num )/denom;
        double y2= (B*K + A*num )/denom;

        double theta1=Math.toDegrees(Math.atan2(y1,x1));
        double theta2=Math.toDegrees(Math.atan2(y2,x2));
        if(i==0) System.out.println("update thetai ("+current_thetai+"): compute errors "+theta1+"  "+theta2);
        if(Double.isNaN(theta1)||Double.isNaN(theta2)) return;
        double error1 = Math.abs(theta1 - current_thetai);
        double error2 = Math.abs(theta2 - current_thetai);

        if(error1<error2) {
            currentAi.get(i).setTheta(theta1);
        }else{
            currentAi.get(i).setTheta(theta2);
        }
    }

    public void removeLandmark(int j) {
        tp.removeSetOfPoints(j);
        current_rj.remove(j);
        previous_rj.remove(j);
        Wj.remove(j);
        eij.remove(j);
        landmarkErrorsj.remove(j);
        nLandmarks = tp.getNumberOfPoints();
    }

    public void saveLandmarksErrors() {
        FileInfo fi = tp.getTiltSeries().getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = tp.getTiltSeries().getFileInfo();
        }
        String savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        savedir += tp.getTiltSeries().getTitle() + "_LandmarksErrors.txt";
        saveLandmarksErrors(savedir);
    }

    public void saveLandmarksErrors(String path) {
        System.out.println("save in " + path);
        try {
            //System.in.read();
            BufferedWriter out = new BufferedWriter(new FileWriter(path));
            int nbpoint = 0;
            for (int j = 0; j < landmarkErrorsj.size(); j++) {
                DoubleMatrix1D error = landmarkErrorsj.get(j);
                out.write("" + j + "\t" + error.get(0) + "\t" + error.get(1) + "\t" + error.get(2) + "\t" + error.get(3) + "\n");
            }
            out.flush();
            out.close();
        } catch (IOException ioe) {
            System.out.println(ioe);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DoubleMatrix1D computeThresholds(double k) {
        int n = 0;
        DoubleMatrix1D delta = DoubleFactory1D.dense.make((int) landmarkErrorsj.get(0).size());
        DoubleMatrix1D mean = DoubleFactory1D.dense.make((int) landmarkErrorsj.get(0).size());
        DoubleMatrix1D M2 = DoubleFactory1D.dense.make((int) landmarkErrorsj.get(0).size());
        for (DoubleMatrix1D d : landmarkErrorsj) {
            n++;
            delta.assign(d);
            delta.assign(mean, DoubleFunctions.minus);
            delta.assign(DoubleFunctions.div(n));
            mean.assign(delta, DoubleFunctions.plus);
            delta.assign(DoubleFunctions.square);
            M2.assign(delta, DoubleFunctions.plus);
        }
        M2.assign(DoubleFunctions.div(n - 1));
        M2.assign(DoubleFunctions.sqrt);
        M2.assign(DoubleFunctions.mult(k));
        mean.assign(M2, DoubleFunctions.plus);
        return mean;
    }

    public int removeOutLiers() {
        int nbOuliers=0;
        saveLandmarksErrors();
        //get thresholds
        DoubleMatrix1D thresholds = computeThresholds(options.getK());
        System.out.println("thresholds are : " + thresholds);
        System.out.println("before removing ouliers : " + tp.getNumberOfPoints() + " landmarks");
        //remove outliers
        for (int j = tp.getNumberOfPoints() - 1; j >= 0; j--) {
            DoubleMatrix1D error = landmarkErrorsj.get(j);
            if (error.getQuick(0) > thresholds.getQuick(0) || error.getQuick(1) > thresholds.getQuick(1)) {
                System.out.println("remove landmark : " + j + " errors: (" + error.getQuick(0) + ", " + error.getQuick(1) + ") thresholds: (" + thresholds.getQuick(0) + ", " + thresholds.getQuick(1) +")");
                removeLandmark(j);
                nbOuliers++;
            }
        }
        //System.out.println("after removing outliers : " + tp.getNumberOfPoints() + " landmarks");
        return nbOuliers;
    }

    public int removeOutliersMahalanobis(){
        int removedNb=0;
        DoubleMatrix1D mean = DoubleFactory1D.dense.make(2);
        DoubleMatrix2D dCentered=DoubleFactory2D.dense.make(nLandmarks,2);
        int n=0;
        for (DoubleMatrix1D d : landmarkErrorsj) {
            n++;
            mean.assign(d.viewPart(0,2), DoubleFunctions.plus);
        }
        mean.assign(DoubleFunctions.div(n));
        for (int j=0;j<nLandmarks;j++) {
            DoubleMatrix1D d=landmarkErrorsj.get((j));
            dCentered.setQuick(j,0,d.getQuick(0)-mean.getQuick(0));
            dCentered.setQuick(j,1,d.getQuick(1)-mean.getQuick(1));
        }

        DoubleMatrix2D S= dCentered.viewDice().zMult(dCentered,null);
        S.assign(DoubleFunctions.div(n-1));
        S =DoubleFactory2D.dense.identity(2);
        DoubleMatrix2D iS=new DenseDoubleAlgebra().inverse(S);
        DoubleMatrix1D z=DoubleFactory1D.dense.make(nLandmarks);
        for (int j=nLandmarks-1;j>=0;j--) {
            DoubleMatrix2D centered=dCentered.viewPart(j,0,1,2);
            z.setQuick(j,centered.zMult(iS,null).zMult(centered.viewDice(),null).getQuick(0,0));
        }
        outputLine("remove outliers: \nS="+S+"\nz="+z);
        for (int j=nLandmarks-1;j>=0;j--) {
            if(z.getQuick(j)>options.getK()){
                System.out.println("remove landmark (Mahalanobis) : " + j + " z: " + z.getQuick(j)+" threshold: "+options.getK());
                removeLandmark(j);
                removedNb++;
            }
        }
        return removedNb;

    }

    /**
     * get the final transform of image i
     *
     * @param i image number
     * @return transform to align(and deform) image with current parameters of Ai
     */

    public AffineTransform getTransform(int i) {

        DoubleMatrix1D Yaxis=DoubleFactory1D.dense.make(3);
        Yaxis.setQuick(1,1);
        DoubleMatrix2D Ry=MatrixUtils.rotation3DMatrix(currentAi.get(i).theta,Yaxis);
        //DoubleMatrix2D Ry_1=new DenseDoubleAlgebra().inverse(Ry);
        DoubleMatrix2D Di=currentAi.get(i).getDi();
        //DoubleMatrix2D Di_inv = new DenseDoubleAlgebra().inverse(currentAi.get(i).getDi());
        DoubleMatrix2D Ri=currentAi.get(i).getRi();
        //DoubleMatrix2D Ri_inv= new DenseDoubleAlgebra().inverse(Ri);
        //DoubleMatrix2D Rpsi=currentAi.get(i).getRpsi();
        //DoubleMatrix2D Rthetauaxis=currentAi.get(i).getRthetauaxis();
        DoubleMatrix2D Rz=MatrixUtils.rotation3DMatrixZ(currentAi.get(i).rot-90);
        //DoubleMatrix2D Rz_inv=new DenseDoubleAlgebra().inverse(Rz);
        //DoubleMatrix2D RiDi=currentAi.get(i).getRiDi();
        //DoubleMatrix2D RiDi_inv=new DenseDoubleAlgebra().inverse(RiDi);
        //DoubleMatrix2D tr=Ri.zMult(Di.zMult(Rz.zMult(Ry_1,null),null),null);
        //if(currentAi.get(i).theta==60) System.out.println("\n\n############################\n"+tr+"\n###############");


        DoubleMatrix2D RyZ0=Ry.viewPart(0,0,2,2);
        DoubleMatrix2D AiRzZ0 = Ri.zMult(Di.zMult(Rz,null),null).viewPart(0,0,2,2);
        DoubleMatrix2D G = AiRzZ0.zMult(new DenseDoubleAlgebra().inverse(RyZ0),null) ;
        G=new DenseDoubleAlgebra().inverse(G);
        AffineTransform result  = new AffineTransform();
        AffineTransform deformT = new AffineTransform(G.getQuick(0, 0), G.getQuick(1, 0), G.getQuick(0, 1), G.getQuick(1, 1),0,0);
        result.translate(-current_di.get(i).getQuick(0, 0), -current_di.get(i).getQuick(1, 0));
        result.preConcatenate(deformT);


        return result;
    }

    public AffineTransform getTranslationTransform(int i){
        AffineTransform result  = new AffineTransform();
        //result.rotate(-Math.toRadians(currentAi.get(i).getRot()-90));
        result.translate(-current_di.get(i).getQuick(0, 0), -current_di.get(i).getQuick(1, 0));
        return result;
    }

    @Override
    public float[] applyTransformOnImage(TiltSeries ts, int index) {

        ImageStatistics stats=ts.getImageStatistics(index);
        if(ts.getAlignMethodForReconstruction()== TiltSeries.ALIGN_NONLINEAR){
            float[] res;
            res=produceAlignedImage(index,ts.getFillType() == TiltSeries.FILL_NaN ? Float.NaN : 0);
            if (ts.isNormalized()) {
                res =   ts.normalizeImage(res, stats);
            }
            return res;
        } else {
            AffineTransform T= (ts.getAlignMethodForReconstruction()==TiltSeries.ALIGN_AFFINE2D)?getTransform(index):getTranslationTransform(index);
            return AffineAlignment.applyTransformOnImage(ts,index,T);

        }
    }


    /** Non linear application of alignment on images*/
    public float[] produceAlignedImage(int i, float fillValue){
        TiltSeries ts=tp.getTiltSeries();
        ImageProcessor ori=ts.getImageStack().getProcessor(i+1);
        float[] result=new float[ts.getWidth()*ts.getHeight()];
        DoubleMatrix1D Yaxis=DoubleFactory1D.dense.make(3);
        Yaxis.setQuick(1,1);
        DoubleMatrix2D Rthetai=MatrixUtils.rotation3DMatrix(currentAi.get(i).theta,Yaxis);
        DoubleMatrix2D Rthetai_inv=new DenseDoubleAlgebra().inverse(Rthetai);
        DoubleMatrix2D r= new DenseDoubleMatrix2D(3,1);
        DoubleMatrix2D Di=currentAi.get(i).getDi();
        DoubleMatrix2D Ri=currentAi.get(i).getRi();
        DoubleMatrix2D Ralpha_inv=MatrixUtils.rotation3DMatrixZ(currentAi.get(i).rot-90);

        double x,y;
        for(int yp=0;yp<ts.getHeight();yp++){
            double yc=yp-ts.getCenterY();
            int yy=yp*ts.getWidth();
            for(int xp=0;xp<ts.getWidth();xp++){
                double xc=xp-ts.getCenterX();
                double z0p=-(xc*Rthetai.getQuick(0,2)+yc*Rthetai.getQuick(1,2))/Rthetai.getQuick(2,2);
                r.setQuick(0,0,xc);
                r.setQuick(1,0,yc);
                r.setQuick(2,0,z0p);
                DoubleMatrix2D r0p=Rthetai_inv.zMult(r,null);
                DoubleMatrix2D s=Ri.zMult(Di.zMult(Ralpha_inv.zMult(r0p,null),null),null);
                x=s.getQuick(0,0) + current_di.get(i).getQuick(0,0)+ts.getCenterX();
                y=s.getQuick(1,0) + current_di.get(i).getQuick(1,0)+ts.getCenterY();
                if(x>=0&&x<ts.getWidth()&&y>=0&&y<ts.getHeight()) {
                    result[xp + yy] = (float) ori.getInterpolatedPixel(x, y);
                }else{
                    result[xp+yy] = fillValue;
                }
            }
        }
        return result;


    }

    public String toString() {
        String result = "Alignment parameters=============================\n";
        result += "rot=" + currentAi.get(0).rot + " tilt=" + currentAi.get(0).tilt + "\n";
        result += "axis height=" + z0 + "\n";
        //result += "Images-----------------------------\n";
        for (int i = 0; i < nImages; i++) {
            result += "Image " + i + " " + tp.getTiltSeries().getTiltAngle(i) + "\u00b0\ndi=" + toString(current_di.get(i)) + " \npsi=" + currentAi.get(i).psii + "\n" +
                    "ti=" + currentAi.get(i).ti + " \t mi=" + currentAi.get(i).mi + "\nsi=" + currentAi.get(i).si + " \t deltai=" + currentAi.get(i).deltai + "\n";
            //result+="Ai="+Ai.get(i)+"\n";
            //result+="Ait="+Ait.get(i)+"\n";
        }
        /*result += "Landmarks-----------------------------\n";
        for (int j = 0; j < nLandmarks; j++) {
            //result += "landmark " + j + " rj=" + current_rj.get(j) + "\n";
        }
        //*/
        return result;
    }


    public void loadFromFileold(String path, double binning, boolean... options) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(path));
        String line=in.readLine();
        while ((line=in.readLine())!=null){
            String[] words=line.split("\t");
            DoubleMatrix2D di=new DenseDoubleMatrix2D(2,1);
            di.setQuick(0,0,Double.parseDouble(words[1]));
            di.setQuick(1,0,Double.parseDouble(words[2]));
            current_di.add(di);
            ProjectionMatrix Ai=new ProjectionMatrix(tp.getTiltSeries().getTiltAngle(Integer.parseInt(words[0])));
            Ai.psii=Double.parseDouble(words[3]);
            Ai.mi=Double.parseDouble(words[4]);
            Ai.ti=Double.parseDouble(words[5]);
            Ai.si=Double.parseDouble(words[6]);
            Ai.deltai=Double.parseDouble(words[7]);
            Ai.computeAi();
            currentAi.add(Ai);
        }
        in.close();
    }

    @Override
    public void loadFromFile(String path,double binning, boolean... options) throws IOException{

        ResultsTable rt = ResultsTable.open(path);
        if(rt==null) return;
        rt.show("test");
        for (int i = 0; i < nImages; i++) {
            DoubleMatrix2D di = new DenseDoubleMatrix2D(2, 1);
            di.setQuick(0, 0, rt.getValue("di.x",i)*binning);
            di.setQuick(1, 0, rt.getValue("di.y",i)*binning);
            current_di.add(di);
            ProjectionMatrix Ai = new ProjectionMatrix(tp.getTiltSeries().getTiltAngle(i));
            Ai.setUaxis(rt.getValue("tilt axis (rot)",i),rt.getValue("tilt axis (tilt)",i));
            Ai.psii =rt.getValue("psii",i);
            Ai.mi = rt.getValue("mi",i);
            Ai.ti = rt.getValue("ti",i);
            Ai.si = rt.getValue("si",i);
            Ai.deltai = rt.getValue("deltai",i);
            Ai.computeAi();
            currentAi.add(Ai);
        }

        System.out.println("loading deformation finished : "+currentAi.size());
    }

    @Override
    public void saveToFile(String path, boolean... options) throws IOException {
        try {
           ResultsTable rt=getAsResultTable();
            rt.saveAs(path);
            if(options!=null) {
                System.out.println("save transform options length "+options.length);
                if(options.length>1 && options[1]){
                    FileWriter fw=new FileWriter(path+"_ais.txt");
                    fw.write(getMatricesAsString());
                    fw.close();

                    FileWriter fw2=new FileWriter(path+"_Transforms.txt");
                    for(int i=0;i<nImages;i++){
                        fw2.write(getTransform(i).toString()+"\n");
                    }
                    fw2.close();

                    FileWriter fw3=new FileWriter(path+"_Eulers.txt");
                    for(int i=0;i<nImages;i++){
                        fw3.write(getEulerMatrix(i)+"\n");
                    }
                    fw3.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMatricesAsString(){
        String result="";
        for (int i = 0; i < nImages; i++) {
            result+=toString(currentAi.get(i).getAi())+"\n";
        }
        return result;
    }

    public ResultsTable getAsResultTable() {
        ResultsTable rt = new ResultsTable();
        Double[] dix=new Double[nImages];
        Double[] diy=new Double[nImages];
        Double[] psii=new Double[nImages];
        Double[] mi=new Double[nImages];
        Double[] ti=new Double[nImages];
        Double[] si=new Double[nImages];
        Double[] deltai=new Double[nImages];
        for (int i = 0; i < nImages; i++) {
            rt.incrementCounter();
            rt.addValue("image", i);
            rt.addValue("tilt axis (rot)", currentAi.get(i).getRot());
            rt.addValue("tilt axis (tilt)", currentAi.get(i).getTilt());
            rt.addValue("tilt angle", currentAi.get(i).getTheta());
            DoubleMatrix2D di=current_di.get(i);
            rt.addValue("di.x",di.get(0,0));
            rt.addValue("di.y",di.get(1,0));
            //rt.addValue("di", toString(current_di.get(i)));
            rt.addValue("psii", currentAi.get(i).psii);
            rt.addValue("mi", currentAi.get(i).mi);
            rt.addValue("ti", currentAi.get(i).ti);
            rt.addValue("si", currentAi.get(i).si);
            rt.addValue("deltai", currentAi.get(i).deltai);

            dix[i]= di.get(0,0);
            diy[i] = di.get(1,0);
            psii[i]=currentAi.get(i).psii;
            mi[i]=currentAi.get(i).mi;
            ti[i]=currentAi.get(i).ti;
            si[i]=currentAi.get(i).si;
            deltai[i]=currentAi.get(i).deltai;
        }
        rt.setPrecision(5);
        rt.showRowNumbers(true);

        double[] result=StudentStatisitics.getStatistics(dix,0.05);
        System.out.println("di.x : avg:"+result[0]+" stddev:"+result[1]+" min:"+result[5]+" max:"+result[6]);
        result=StudentStatisitics.getStatistics(diy,0.05);
        System.out.println("di.y : avg:"+result[0]+" stddev:"+result[1]+" min:"+result[5]+" max:"+result[6]);
        result=StudentStatisitics.getStatistics(mi,0.05);
        System.out.println("mi : avg:"+result[0]+" stddev:"+result[1]+" min:"+result[5]+" max:"+result[6]);
        result=StudentStatisitics.getStatistics(ti,0.05);
        System.out.println("ti : avg:"+result[0]+" stddev:"+result[1]+" min:"+result[5]+" max:"+result[6]);
        result=StudentStatisitics.getStatistics(si,0.05);
        System.out.println("si : avg:"+result[0]+" stddev:"+result[1]+" min:"+result[5]+" max:"+result[6]);
        result=StudentStatisitics.getStatistics(deltai,0.05);
        System.out.println("deltai : avg:"+result[0]+" stddev:"+result[1]+" min:"+result[5]+" max:"+result[6]);
        result=StudentStatisitics.getStatistics(psii,0.05);
        System.out.println("psii : avg:"+result[0]+" stddev:"+result[1]+" min:"+result[5]+" max:"+result[6]);

        return rt;
    }

    private String toString(DoubleMatrix2D mat) {
        String result = "" + mat;
        String[] lines = result.split("\n");
        result = lines[0] + "[";
        for (int i = 1; i < lines.length; i++) {
            result += lines[i];
            if (i < lines.length - 1) result += ", ";

        }
        result += "]";
        return result;
    }

    public void saveRjs(String filename) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(filename));
            for(int j=0;j<current_rj.size();j++){
                out.write("#"+j+"\t"+current_rj.get(j).getQuick(0,0)+"\t"+current_rj.get(j).getQuick(1,0)+"\t"+(current_rj.get(j).getQuick(2,0))+"\n");
            }
            /*for (DoubleMatrix2D r : current_rj) {
                out.write(r + "\n");
            }
                 */
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public DoubleMatrix2D getRj(int index) {
        return current_rj.get(index);
    }

    public void correctRjsPositionforTomogram(double zheight){
        DoubleMatrix2D Rmin = MatrixUtils.rotation2DMatrix(90 - currentAi.get(0).rot + currentAi.get(tp.getTiltSeries().getZeroIndex()).psii);
        Rmin=new DenseDoubleAlgebra().inverse(Rmin);
        for(DoubleMatrix2D rj:current_rj) {
            DoubleMatrix1D rjr = Rmin.zMult(rj.vectorize(), null);
            rjr.setQuick(2,rjr.getQuick(2)-zheight);
            rj.setQuick(0,0,rjr.getQuick(0));
            rj.setQuick(1,0,rjr.getQuick(1));
            rj.setQuick(2,0,rjr.getQuick(2));
        }

    }

    public double correctAxisHeight() {
        //compute the average height of the 3D landmarks seen at 0�
        DoubleMatrix1D axis = MatrixUtils.eulerDirection(Math.toRadians(currentAi.get(0).rot), Math.toRadians(currentAi.get(0).tilt));
        z0 = 0;
        int z0N = 0;
        TiltSeries ts = tp.getTiltSeries();
        int zeroindex = ts.getZeroIndex();
        double zeroTiltAngle = ts.getTiltAngle(zeroindex);
        //System.out.println("nlandmarks "+Nlandmark);
        for (int j = 0; j < nLandmarks; j++) {
            //System.out.println("j="+j);
            Point2D p = tp.getCenteredPoint(j, zeroindex);
            if (p != null) {
                DoubleMatrix2D Raxismin = MatrixUtils.rotation3DMatrix(zeroTiltAngle, axis);
                //System.out.println("p not null at zero index("+zeroindex);
                DoubleMatrix2D Rmin = MatrixUtils.rotation2DMatrix(90 - currentAi.get(0).rot + currentAi.get(zeroindex).psii);
                DoubleMatrix2D RtiltYmin = MatrixUtils.rotation3DMatrixY(-zeroTiltAngle);
                DoubleMatrix1D rjp = RtiltYmin.zMult(Rmin.zMult(Raxismin.zMult(current_rj.get(j).vectorize(), null), null), null);
                z0 += rjp.getQuick(2);
                z0N++;
            }
        }
        if (z0N == 0) {
            System.out.println("no landmarks at 0!!! could not compute the height");
            z0 = 0;
        } else {
            z0 /= z0N;
        }
        System.out.println("correct axis height: z0=" + z0);
        return z0;
    }

    public ImagePlus create3DLandmarksImage(int width, int height, int thickness) {
        ImagePlus res = NewImage.createFloatImage("3D landmarks", width, height, thickness, NewImage.FILL_BLACK + NewImage.CHECK_AVAILABLE_MEMORY);
        int cx = width / 2;
        int cy = height / 2;
        int cz = thickness / 2;
        if (res != null) {
            for (int j = 0; j < current_rj.size(); j++) {
                DoubleMatrix2D landmark3D = current_rj.get(j);
                int x = (int) landmark3D.getQuick(0,0) + cx;
                int y = (int) landmark3D.getQuick(1,0) + cy;
                int z = (int) (landmark3D.getQuick(2,0)) + cz;
                for (int xx = -1; xx < 2; xx++) {
                    for (int yy = -1; yy < 2; yy++) {
                        for (int zz = -1; zz < 2; zz++) {
                            res.getImageStack().getProcessor(z + zz+1).putPixelValue(x + xx, y + yy, j * 10);
                        }
                    }
                }
            }
        } else {
            System.out.println("not enough memory!");
        }
        return res;
    }

    public ArrayList<Point2D[]> getReprojectedLandmarks() {
       ArrayList<Point2D[]> res = new ArrayList<Point2D[]>(nLandmarks);
       for (int j = 0; j < nLandmarks; j++) {
           res.add(new Point2D.Double[nImages]);
       }
       for (int i = 0; i < nImages; i++) {
           for (int j = 0; j < nLandmarks; j++) {
               Point2D p = tp.getCenteredPoint(j, i);
               if (p != null) {
                   DoubleMatrix2D reprojected = DoubleFactory2D.dense.make(2, 1);
                   currentAi.get(i).getAi().zMult(current_rj.get(j), reprojected);
                   reprojected.assign(current_di.get(i), DoubleFunctions.plus);
                   res.get(j)[i] = new Point2D.Double(reprojected.getQuick(0,0), reprojected.getQuick(1,0));
               }
           }
       }

       return res;
   }




    public class ProjectionMatrix {
        double theta;
        DoubleMatrix2D Ai;
        public double psii;
        DoubleMatrix2D Rthetauaxis;
        double rot, tilt;
        DoubleMatrix2D Di;
        DoubleMatrix2D Ri;
        public double mi, ti, si, deltai;
        boolean computeAi = true;
        DoubleMatrix2D RiDi;
        DoubleMatrix2D RiDi_inv;
        DoubleMatrix2D euler;
        DoubleMatrix2D Xi;
        DoubleMatrix2D XiL;
        DoubleMatrix2D XiR;
        DoubleMatrix1D axis;

        /**
         * @param theta tilt angle of image corresponding to this projection Matrix (image i)
         */
        ProjectionMatrix(double theta) {
            this.theta = - theta;
            mi = 1;
            ti = 1;
            si = 1;
            deltai = 0;
            //computeAi();
            Ai = DoubleFactory2D.dense.make(2, 3);
            Di = DoubleFactory2D.dense.make(3, 3);
            Ri = DoubleFactory2D.dense.make(3, 3);
            Rthetauaxis = DoubleFactory2D.dense.make(3, 3);
        }

        public ProjectionMatrix copy() {
            ProjectionMatrix result = new ProjectionMatrix(this.theta);
            result.copy(this);
            return result;
        }

        public void copy(ProjectionMatrix other) {
            theta=other.theta;
            mi = other.mi;
            ti = other.ti;
            si = other.si;
            deltai = other.deltai;
            psii = other.psii;
            Rthetauaxis.assign(other.Rthetauaxis);
            axis= other.axis;
            Ai.assign(other.Ai);
            Di.assign(other.Di);
            Ri.assign(other.Ri);
        }

        public void setUaxis(double uaxis_alpha, double uaxis_beta) {
            rot = uaxis_alpha;
            tilt = uaxis_beta;
            axis = MatrixUtils.eulerDirection(toRadians(uaxis_alpha), toRadians(uaxis_beta));
            //Rthetauaxis = MatrixUtils.rotation3DMatrix(-theta, axis);      //modified version
            Rthetauaxis = MatrixUtils.rotation3DMatrix(theta, axis);
            euler=null;
            //System.out.println("axis ("+uaxis_alpha+", "+uaxis_beta+") : "+axis+"\ntheta: "+theta+"\nRthetauaxis: "+Rthetauaxis);
        }

        public double getTheta() {
            return theta;
        }

        public void setTheta(double theta) {
            this.theta = theta;
            Rthetauaxis = MatrixUtils.rotation3DMatrix(theta, axis);
            //Xi=null;
            //computeAi=true;
            euler=null;
        }

        public void clearAi() {
            Xi=null;
            XiR=null;
            XiL=null;
            computeAi = true;
        }

        public DoubleMatrix2D getAi() {
            if (computeAi) computeAi();
            return Ai;
        }


        public DoubleMatrix2D getRiDi(){
            if(computeAi) computeAi();
            if(RiDi==null){
                computeRiDi();
            }

            return RiDi;
        }

        public void computeRiDi(){
            if(computeAi) computeAi();
            DoubleMatrix2D Ri = DoubleFactory2D.dense.make(3, 3);
            DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
            Rpsi.zMult(Rthetauaxis, Ri);
            RiDi = Ri.zMult(Di, null);

        }

        public void resetEuler(){
            euler=null;
        }
        public DoubleMatrix2D getEuler(){
            if(euler!=null) return euler;
            //DoubleMatrix1D axis = MatrixUtils.eulerDirection(toRadians(rot), toRadians(tilt));
            //DoubleMatrix2D Rthetauaxis = MatrixUtils.rotation3DMatrix(theta, axis);

            DoubleMatrix2D Rz=MatrixUtils.rotation3DMatrixZ(rot-90);
            //DoubleMatrix2D Rz_inv=new DenseDoubleAlgebra().inverse(Rz);

            DoubleMatrix2D Ri = DoubleFactory2D.dense.make(3, 3);
            DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
            Rpsi.zMult(Rthetauaxis, Ri);
            //Ri=new DenseDoubleAlgebra().inverse(Ri);
            euler=Rpsi.zMult(Rthetauaxis.zMult(getDi().zMult(Rz,null),null),null);
            //euler=Rpsi.zMult(Rthetauaxis.zMult(getDi(),null),null);
            //euler=Rthetauaxis.zMult(getDi(),null);
            //euler=getRiDi();
            //euler=new DenseDoubleAlgebra().inverse(euler);
            return euler;
            /*DoubleMatrix2D Rz=MatrixUtils.rotation3DMatrixZ(rot-90);
            return euler.zMult(Rz,null);*/
        }

        public DoubleMatrix2D getEulerInverse(){
            if(computeAi) computeAi();
            if(RiDi_inv==null) RiDi_inv=new DenseDoubleAlgebra().inverse(Di);
            return RiDi_inv;
        }

        synchronized public void computeAi() {
            //if (Ai == null) Ai = DoubleFactory2D.dense.make(2, 3);
            computeDi();
            computeRi();
            RiDi = Ri.zMult(Di, null);
            Ai.assign(RiDi.viewPart(0, 0, 2, 3));
            computeAi = false;
            Xi=null;
            XiL=null;
            XiR=null;

            //RiDi=null;
            //RiDi_inv=null;
        }


        public void computeDi() {
            if (Di == null) Di = DoubleFactory2D.dense.make(3, 3);
            Di.assign(0);
            Di.setQuick(0, 0, mi * si * Math.cos(Math.toRadians(deltai)));
            Di.setQuick(1, 0, mi * si * Math.sin(Math.toRadians(deltai)));
            Di.setQuick(1, 1, mi);
            Di.setQuick(2, 2, mi * ti);
            RiDi_inv=null;
            RiDi=null;
        }

        public void computeRi() {
            //System.out.println("computeRi");
            if (Ri == null) Ri = DoubleFactory2D.dense.make(3, 3);
            DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
            Rpsi.zMult(Rthetauaxis, Ri);
            RiDi_inv=null;
            RiDi=null;
            //System.out.println("Rpsi="+Rpsi+" \nRThetaUxais="+Rthetauaxis+" \nRi="+Ri);
        }

        public void computeXi(){
            if (Xi == null) {
                Xi = DoubleFactory2D.dense.make(3, 3);
                XiL = DoubleFactory2D.dense.make(3, 3);
                XiR = DoubleFactory2D.dense.make(3, 3);
            }
            DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
            DoubleMatrix2D Rbeta=MatrixUtils.alignWithZ(axis);
            Rpsi.zMult(Rbeta.viewDice(), XiL);
            Rbeta.zMult(getDi(),XiR);
            DoubleMatrix2D P = DoubleFactory2D.dense.make(3, 3);
            P.setQuick(1, 0, 1);
            P.setQuick(0, 1, -1);
            P.setQuick(2, 2, 1);
            DoubleMatrix2D HtH = DoubleFactory2D.dense.make(3, 3);
            HtH.setQuick(0, 0, 1);
            HtH.setQuick(1, 1, 1);
            DoubleMatrix2D tmp = XiL.zMult(P,null).zMult(MatrixUtils.rotation3DMatrixZ(theta),null).zMult(HtH,null).zMult(XiR,null);
            Xi.assign(tmp);
        }

        public DoubleMatrix2D getRpsi(){
            return MatrixUtils.rotation3DMatrixZ(psii);
        }

        public DoubleMatrix2D getRthetauaxis(){
            return Rthetauaxis;
        }

        public DoubleMatrix2D getRi() {
            if (Ri == null) computeRi();
            return Ri;
        }

        public DoubleMatrix2D getHRi() {
            if (Ri == null) computeRi();
            return Ri.viewPart(0, 0, 2, 3);

        }

        public DoubleMatrix2D getDi() {
            if (Di == null) computeDi();
            return Di;
        }

        public double getRot() {
            return rot;
        }

        public double getTilt() {
            return tilt;
        }

        public DoubleMatrix2D getXi() {
            if(Xi==null) computeXi();
            return Xi;
        }

        public DoubleMatrix2D getXiL() {
            if(XiL==null) computeXi();
            return XiL;
        }

        public DoubleMatrix2D getXiR() {
            if(XiR==null) computeXi();
            return XiR;
        }

        public String toString(){
            return "theta="+theta+" psii="+psii+"\nmi="+mi+" ti="+ti+"\nsi="+si+" deltai="+deltai+"\nuaxis["+rot+", "+tilt+"]";
        }
    }

    @Override
    public AffineTransform[] getTransforms() {
        AffineTransform[] result = new AffineTransform[tp.getTiltSeries().getImageStackSize()];
        for(int i=0;i<result.length;i++) result[i]=getTransform(i);
        return result;
    }

    @Override
    public void setZeroIndex(int zeroIndex) {

    }

    @Override
    public void removeTransform(int index) {
        currentAi.remove(index);

    }

    @Override
    public AffineTransform getZeroTransform() {
        return getTransform(tp.getTiltSeries().getZeroIndex());
    }

    @Override
    public void resetEulerMatrices() {

    }

    @Override
    public DoubleMatrix2D getEulerMatrix(int index) {
        if(tp.getTiltSeries().getAlignMethodForReconstruction()==TiltSeries.ALIGN_PROJECTOR)return currentAi.get(index).getEuler();
        return MatrixUtils.eulerAngles2Matrix(0, tp.getTiltSeries().getTiltAngle(index), 0);
    }

    @Override
    public void setEulerMatrix(int index, DoubleMatrix2D eulerMatrix) {

    }

    public AffineAlignment convertToAffine(){
        AffineAlignment result=new AffineAlignment(tp.getTiltSeries());
        for(int i=0;i<tp.getTiltSeries().getImageStackSize();i++){
            result.setTransform(i, getTransform(i));
        }
        result.convertTolocalTransform();
        return result;
    }

}