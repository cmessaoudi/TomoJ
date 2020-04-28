package fr.curie.tomoj.tomography;

import fr.curie.plotj.PlotWindow2;
import ij.IJ;
import ij.Prefs;
import fr.curie.tomoj.tomography.projectors.*;
import fr.curie.utils.Chrono;

import static fr.curie.tomoj.tomography.ReconstructionParameters.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 16/12/2015.
 */
public class ReconstructionThreadManager {
    protected TomoReconstruction2 rec2;
    protected TiltSeries ts;
    protected Component parentWindow;
    protected int recNbRaysPerPixels=1;
    protected boolean saveErrorVol=false;
    protected boolean saveForAll=false;
    protected double[] dissimilarity;
    protected float[][] thprojs=null;
    protected ExecutorService exec;
    protected double numberofsteps;
    protected ArrayList<Double> errors=null;
    protected Projector projector=null;

    public ReconstructionThreadManager(Component parentWindow,TiltSeries ts) {
        this.ts=ts;
        this.parentWindow = parentWindow;
        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
    }

    public ReconstructionThreadManager(ReconstructionThreadManager other, TiltSeries ts){

        this.parentWindow=other.parentWindow;
        this.recNbRaysPerPixels=other.recNbRaysPerPixels;
        this.saveErrorVol=other.saveErrorVol;
        this.saveForAll=other.saveForAll;
        this.exec=other.exec;

        this.ts=ts;
    }

    public TomoReconstruction2 reconstruct(final  ReconstructionParameters parameters,boolean showProgressWindow){
        System.out.println("###################################");
        System.out.println(parameters.asString());
        String type="";
        switch(ts.getAlignMethodForReconstruction()){
            case TiltSeries.ALIGN_AFFINE2D:
            type="Affine 2D";
            break;
            case TiltSeries.ALIGN_NONLINEAR:
                type="2D Non-linear";
                break;
            case TiltSeries.ALIGN_PROJECTOR:
            default:
                type="3D projector";
                break;
        }
        System.out.println("apply alignment as : "+type);
        System.out.println("###################################");
        createRec2(parameters);
        performReconstruction(parameters,showProgressWindow);
        return rec2;
    }

    public ArrayList<Double> doRec(ReconstructionParameters parameters, boolean saveErrorVol, boolean saveForAll,Chrono time){
        return doCPUrec(parameters,saveErrorVol,saveForAll);
    }

    public void performReconstruction(final ReconstructionParameters parameters, boolean showProgressWindow) {
        thprojs=null;
        rec2.initCompletion();
        ts.getAlignment().resetEulerMatrices();

        numberofsteps = (parameters.getType()>WBP) ? ts.getImageStackSize() * parameters.getNbIterations() *2: ts.getImageStackSize();
        final Chrono time = new Chrono(numberofsteps);
        //start computation
        if(exec==null) exec = Executors.newFixedThreadPool(Prefs.getThreads());
        final Thread T = new Thread() {

            public void run() {
                time.start();
                //errors =  doCPUrec(parameters,saveErrorVol,saveForAll);
                errors = doRec(parameters,saveErrorVol,saveForAll,time);
                if ((parameters.getType()==BP || parameters.getType()==WBP) && parameters.isElongationCorrection()) {
                    IJ.selectWindow(rec2.getID());
                    IJ.run(rec2, "Elongation_Correction_Tomography", "" + Math.max(Math.abs(ts.getTiltAngle(0)), Math.abs(ts.getTiltAngle(ts.getImageStackSize() - 1))));
                }
                time.stop();
                System.out.println("total computation time=" + time.delayString());
                if (rec2.getCompletion() > 0) {
                    rec2.getProcessor().resetMinAndMax();
                    rec2.updateAndRepaintWindow();
                }
                if (errors != null) {
                    double[] ite = new double[errors.size()];
                    for (int i = 0; i < errors.size(); i++) {
                        ite[i] = i + 1;
                    }
                    System.out.println("ite:" + ite.length + " errors:" + errors.size());
                    System.out.flush();
                    PlotWindow2 pw = new PlotWindow2();
                    pw.removeAllPlots();
                    double[] errorsArray=new double[errors.size()];
                    for(int index=0;index<errorsArray.length;index++) errorsArray[index]=errors.get(index);
                    pw.addPlot(ite, errorsArray, Color.BLACK, "errors");
                    pw.resetMinMax();
                    pw.setVisible(true);
                }

            }
        };
        final ArrayList<Future> futures=new ArrayList<Future>();
        futures.add(exec.submit(T));
        //T.start();
        if(showProgressWindow) {
            final Thread progress = new Thread() {
                public void run() {
                    System.out.println("open progress monitor");
                    ProgressMonitor toto = new ProgressMonitor(parentWindow, "reconstruction", "", 0, (int)numberofsteps);
                    while ((!futures.get(0).isDone()) && rec2 != null && rec2.getCompletion() >= 0) {
                        if (toto.isCanceled()) {
                            //ConcurrencyUtils.shutdown();
                            rec2.interrupt();
                            //T.interrupt();
                            toto.close();
                            System.out.println("process interrupted");
                            IJ.showStatus("process interrupted");
                        } else {
                            toto.setProgress((int) rec2.getCompletion());
                            time.stop();
                            String note = (int) rec2.getCompletion() + "/" + numberofsteps;
                            if (rec2.getCompletion() > 0)
                                note += " approximately " + time.remainString((int) rec2.getCompletion()) + " left";
                            toto.setNote(note);
                            try {
                                sleep(1000);
                            } catch (Exception e) {
                                System.out.println(e);
                            }
                        }
                    }
                    System.out.println("close progress monitor");
                    toto.close();
                    System.out.println("close progress monitor");
                }
            };
            futures.add(exec.submit(progress));
        }
        try {
            futures.get(0).get();
            //for(Future f:futures) f.get();
        }catch (Exception e){e.printStackTrace();}
        System.out.println("reconstruct done");
    }

    public void createRec2(ReconstructionParameters parameters) {
        System.out.println("create rec2");
        System.out.flush();
        thprojs=null;
        int width = parameters.getWidth();
        int height = parameters.getHeight();
        int thickness = parameters.getDepth();
        if (rec2 == null || rec2.getWidth() != width || rec2.getHeight() != height || rec2.getImageStackSize() != thickness || !rec2.isVisible()|| rec2.getImageStackSize()==0) {
            System.out.println("create Reconstruction with size (" + width + ", " + height + ", " + thickness + ")");
            rec2 = new TomoReconstruction2(width, height, thickness);
            double[]centermodif=parameters.getReconstructionCenterModifiers();
            if(centermodif!=null){
                rec2.setCenter(rec2.getCenterx()+centermodif[0],rec2.getCentery()+centermodif[1], rec2.getCenterz()+centermodif[2]);
            }
        }else{
            System.out.println("reconstruction exists, add some iterations");
        }
        if (rec2 == null) {
            IJ.freeMemory();
            long neededMemory = width * height * thickness * 4;
            long freeMemory = Runtime.getRuntime().freeMemory();
            long missing = neededMemory - freeMemory;
            IJ.outOfMemory("allocation of memory for reconstruction failed missing " + missing);
        }
        if (!rec2.isVisible()) {
            rec2.show();
        }
    }

    public void setRec2(TomoReconstruction2 rec2){
        thprojs=null;
        System.out.println("set reconstruction : "+rec2.getTitle());
        this.rec2=rec2;
    }

    public void resetRec2(){
        setRec2(null);
    }

    public TomoReconstruction2 getRec2() {
        return rec2;
    }

    public void setTiltSeries(TiltSeries ts){ this.ts=ts;}





    public ArrayList<Double> doCPUrec(ReconstructionParameters params, final boolean saveError, final boolean saveForAll) {

        System.out.println("do reconstruction on CPU");

        ArrayList<Double> errors;
        rec2.initCompletion();
        projector = params.getProjector(ts,rec2);
        switch (params.getType()) {
            case BP:
            case WBP:
                rec2.WBP(ts, projector, 0, rec2.getHeight());
                errors = null;
                break;
            case OSSART:
                if (saveError&&saveForAll) {
                    if(projector instanceof VoxelProjector3D) ((VoxelProjector3D)projector).createErrorVolume();
                    errors = rec2.OSSART(ts, projector, params.getNbIterations(), params.getRelaxationCoefficient(), params.getUpdateNb(),params.getFscType(), 0, rec2.getHeight());
                } else if (saveError) {
                    errors = new ArrayList<Double>(params.getNbIterations());
                    ArrayList<Double> etmp = rec2.OSSART(ts, projector, params.getNbIterations() - 1, params.getRelaxationCoefficient(), params.getUpdateNb(),params.getFscType(), 0, rec2.getHeight());
                    for (int e = 0; e < etmp.size(); e++) errors.add(etmp.get(e));
                    if(projector instanceof VoxelProjector3D) ((VoxelProjector3D)projector).createErrorVolume();
                    etmp = rec2.OSSART(ts, projector, 1, params.getRelaxationCoefficient(), params.getUpdateNb(),params.getFscType(), 0, rec2.getHeight());
                    errors.add(etmp.get(0));
                } else
                    errors = rec2.OSSART(ts, projector, params.getNbIterations(), params.getRelaxationCoefficient(), params.getUpdateNb(),params.getFscType(), 0, rec2.getHeight());
                break;
            default:
                errors = rec2.regularization(ts, projector, params.getNbIterations(), 0, rec2.getHeight());
        }
        return errors;
    }

    public float[] getProjection(int index){
        if(thprojs==null){
            thprojs=new float[ts.getImageStackSize()][];
        }
        if(thprojs[index]==null){
            projector.addProjection(index);
            projector.project();
            thprojs[index]=projector.getProjection(0);
            projector.clearAllProjections();

        }

        return thprojs[index];
    }





    public double getNumberofsteps() {
        return numberofsteps;
    }

    public ArrayList<Double> getReconstructionDissimilarities() {
        return errors;
    }

    public double getCompletion(){
        if(rec2!=null) return rec2.getCompletion()/numberofsteps*100;
        else return -123456789;
    }
}
