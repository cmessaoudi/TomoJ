package fr.curie.gpu.tomoj.tomography;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import fr.curie.gpu.tomoj.tomography.projectors.*;
import fr.curie.gpu.utils.GPUDevice;
import fr.curie.tomoj.tomography.*;
import ij.Prefs;
import org.jocl.Sizeof;
import fr.curie.tomoj.landmarks.AlignmentLandmarkImproved;
import fr.curie.gpu.tomoj.tomography.filters.FFTWeightingGPU;
import fr.curie.tomoj.tomography.projectors.*;
import fr.curie.utils.Chrono;
import fr.curie.utils.MatrixUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.curie.tomoj.tomography.AdvancedReconstructionParameters.*;
//import static fr.curie.tomoj.tomography.ReconstructionParameters.*;

/**
 * Created by cedric on 16/12/2015.
 */
public class ReconstructionThreadManagerGPU extends ReconstructionThreadManager {

    ProjectorGPU[] projectors;
    GPUDevice[] devices;
    FFTWeightingGPU[] weigthingGPU;



    Boolean[] use=null;
    int recNbRaysPerPixels=1;
    boolean saveErrorVol=false;
    boolean saveForAll=false;
    double[] dissimilarity;
    float[][] thprojs=null;
    ExecutorService exec;
    double numberofsteps;
    ArrayList<Double> errors=null;

    public ReconstructionThreadManagerGPU(Component parentWindow, TiltSeries ts) {
        super(parentWindow,ts);
        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
    }

    public ReconstructionThreadManagerGPU(ReconstructionThreadManagerGPU other, TiltSeries ts){
        super(other,ts);
        this.devices=other.devices;
        this.use=other.use;
    }

    public Projector getProjector(){
        if(use==null) return projectors[0];
        for(int i=0;i<use.length;i++){
            if(use[i]) return projectors[i];
        }
        return null;
    }

    public boolean isGpu(){
        for(boolean b:use) if(b) return true;
        return false;
    }
    public ArrayList<Double> doRec(ReconstructionParameters parameters, boolean saveErrorVol, boolean saveForAll, Chrono time){
        if(isGpu()) return  doGPUrec(parameters,time);
        return doCPUrec(parameters,saveErrorVol,saveForAll);
    }





    private void createProjector(int index, ReconstructionParameters parameters) {
        System.out.println("create projector "+index);
        System.out.flush();
        switch (parameters.getReconstructionType()){
            case BP:
            case WBP:
            case OSSART:
                if (weigthingGPU[index] == null && (!Double.isNaN(parameters.getWeightingRadius())))
                    weigthingGPU[index] = new FFTWeightingGPU(devices[index], ts, parameters.getWeightingRadius() * ts.getWidth());
                if (weigthingGPU[index] != null && (!Double.isNaN(parameters.getWeightingRadius())))
                    weigthingGPU[index].setDiameter(parameters.getWeightingRadius() * ts.getWidth());
                projectors[index] = new VoxelProjector3DGPU(ts, rec2, devices[index], (!Double.isNaN(parameters.getWeightingRadius())) ? weigthingGPU[index] : null);

                if (parameters.isRescaleData())
                    ((ProjectorGPU)projectors[index]).setScale(ts.getWidth() / rec2.getWidth());
                ((ProjectorGPU)projectors[index]).setLongObjectCompensation(parameters.isLongObjectCompensation());
                break;
            case TVM:
                TVMVoxelProjector3DGPU tvm = new TVMVoxelProjector3DGPU(ts, rec2, devices[index]);
                tvm.setRegularizationAlpha(parameters.getRelaxationCoefficient());
                //tvm.setRegularizationLambda(parameters.getRegularizationWeight());

                tvm.setTvmTheta(((AdvancedReconstructionParameters)parameters).getTvmTheta());
                tvm.setTvmG(((AdvancedReconstructionParameters)parameters).getTvmG());
                tvm.setTvmDt(((AdvancedReconstructionParameters)parameters).getTvmDt());
                tvm.setTvmIteration(((AdvancedReconstructionParameters)parameters).getTvmIterations());
                tvm.setPositivityConstraint(parameters.isPositivityConstraint());
                tvm.setLongObjectCompensation(parameters.isLongObjectCompensation());
                if (parameters.isRescaleData()) tvm.setScale(ts.getWidth() / rec2.getWidth());
                projectors[index] = tvm;
                break;
            case BAYESIAN:
                BayesianVoxelProjector3DGPU bayproj = new BayesianVoxelProjector3DGPU(ts, rec2, devices[index]);
                bayproj.setRegularizationAlpha(parameters.getRelaxationCoefficient());
                bayproj.setRegularizationLambda(((AdvancedReconstructionParameters)parameters).getRegularizationWeight());
                bayproj.setPositivityConstraint(parameters.isPositivityConstraint());
                bayproj.setLongObjectCompensation(parameters.isLongObjectCompensation());
                if (parameters.isRescaleData()) bayproj.setScale(ts.getWidth() / rec2.getWidth());
                projectors[index] = bayproj;
                break;
            case COMPRESSED_SENSING:
                CompressedSensingProjector3DGPU csproj = new CompressedSensingProjector3DGPU(ts, rec2, devices[index],null);
                csproj.setWaveletType(((AdvancedReconstructionParameters)parameters).getWaveletType());
                csproj.setWaveletDegree(((AdvancedReconstructionParameters)parameters).getWaveletDegree());
                csproj.setWaveletShift(((AdvancedReconstructionParameters)parameters).getWaveletShift());
                csproj.setSoftThresholdingPercentageOfZeros(((AdvancedReconstructionParameters)parameters).getWaveletPercentageOfZeros());
                csproj.setPositivityConstraint(parameters.isPositivityConstraint());
                csproj.setLongObjectCompensation(parameters.isLongObjectCompensation());
                if (parameters.isRescaleData()) csproj.setScale(ts.getWidth() / rec2.getWidth());
                projectors[index] = csproj;
                break;
        }
        if(parameters.isFista()) {
            System.out.println("convert projector to use Fista "+index);
            System.out.flush();
            projectors[index]=new FistaProjector3DGPU(projectors[index]);
        }
    }

//    public Boolean[] askForGPU() {
//        GPUSelectionDialog diag = new GPUSelectionDialog(rec2, (ProjectorGPU[])projectors, false);
//        diag.pack();
//        diag.setVisible(true);
//        use = diag.getUse();
//        if (diag.wasCanceled()) {
//            Arrays.fill(use, false);
//            return use;
//        }
//        return use;
//    }

    public Boolean[] getUse() {
        return use;
    }

    public void setUse(Boolean[] use) {
        this.use = use;
    }

    public void setDevices(GPUDevice[] devices) {
        this.devices = devices;
    }

    public ArrayList<Double> doGPURec(Chrono time, final ProjectorGPU[] projectors, Boolean[] use, ReconstructionParameters params) {

        System.out.println("recType:" + params.getReconstructionType());

        return doGPURec(rec2, ts, projectors, time,  use, params, saveErrorVol, saveForAll, ProjectorGPU.ONE_KERNEL_PROJ_DIFF);

    }


    public static ArrayList<Double> doGPURec(final TomoReconstruction2 rec2, final TiltSeries ts, final ProjectorGPU[] projectors, Chrono time,  Boolean[] use, final ReconstructionParameters parameters, final boolean saveError, final boolean saveForAll, final int kernelToUse) {
        System.out.println("do gpu:"+parameters.isPositivityConstraint());
//        if(ts.getTomoJPoints()!=null&&ts.getTomoJPoints().getLandmarks3D()!=null&&ts.getTomoJPoints().getLandmarks3D().getBestAlignment()!=null){
//            ts.getTomoJPoints().getLandmarks3D().getBestAlignment().setUaxis(90,90);
//            ts.getTomoJPoints().getLandmarks3D().getBestAlignment().computeAis();
//        }
        final ArrayList<Double> errors = (parameters.getNbIterations() != 0) ? new ArrayList<Double>(parameters.getNbIterations()) : null;
        rec2.initCompletion();
        //get GPU devices
        final int[] Ymax = new int[projectors.length];
        for (int i = 0; i < projectors.length; i++) {
            System.out.println("using device #" + i + " : " + use[i]);
            if (projectors[i].getDevice().getOptimumMemoryUse() > 0) {
                Ymax[i] = (int) (projectors[i].getDevice().getOptimumMemoryUse() / (rec2.getWidth() * rec2.getSizez() * Sizeof.cl_float));
                System.out.println("memory used : " + projectors[i].getDevice().getOptimumMemoryUse());
            } else {
                long volMaxMem = projectors[i].computeMaximumSizeForVolume();
                Ymax[i] = (int) (volMaxMem / (rec2.getWidth() * rec2.getSizez() * Sizeof.cl_float));
                System.out.println("memory used : " + volMaxMem);
            }
        }
        final AtomicInteger atomOffset = new AtomicInteger(0);
        final int[] totalSlices = new int[projectors.length];
        ArrayList<Future> futures = new ArrayList<Future>(projectors.length);
        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
        rec2.setDisplayIterationInfo(false);
        time.start();
        for (int d = 0; d < projectors.length; d++) {
            if (use[d]) {
                final int ii = d;
                /*if(Ymax[ii]<rec2.getHeight()&& ts.getAlignMethodForReconstruction()==TiltSeries.ALIGN_PROJECTOR) {
                    System.out.println("changing method to apply alignment to Affine2D because reconstruction is too big for gpu memory");
                    ts.setAlignMethodForReconstruction(TiltSeries.ALIGN_AFFINE2D);
                }  */
                futures.add(exec.submit(new Thread() {
                    public void run() {
                        System.out.println("align type="+ts.getAlignMethodForReconstruction());
                        int offset = 0;
                        int YsliceSize = Ymax[ii];
                        Chrono time = new Chrono();
                        while (atomOffset.get() < rec2.getHeight()) {
                            if (rec2.getCompletion() < 0) return;
                            int endY=0;
                            int[] usable;
                            synchronized (atomOffset) {
                                offset = atomOffset.get() > 0 ? atomOffset.addAndGet(0) : atomOffset.get();
                                YsliceSize = Math.min(Ymax[ii], rec2.getHeight() - offset);
                                endY = (offset + YsliceSize);
                                usable=computeUsablePart(offset,endY,ts,rec2);
                                atomOffset.set((endY == rec2.getHeight())?endY : usable[2]);
                                //atomOffset.addAndGet(YsliceSize);
                            }
                            System.out.println("working on section " + offset + " -> " + endY + "(" + YsliceSize + ")");
                            totalSlices[ii] += YsliceSize;
                            switch (parameters.getReconstructionType()) {
                                case BP:
                                case WBP:
                                    if (rec2.getCompletion() < 0) return;
                                    projectors[ii].initForBP(offset, endY, 1);
                                    rec2.WBP(ts, projectors[ii], offset, endY);
                                    break;
                                case TVM:
                                case BAYESIAN:
                                    projectors[ii].initForIterative(offset, endY, ts.getImageStackSize(), kernelToUse, false);
                                    projectors[ii].setPositivityConstraint(parameters.isPositivityConstraint());
                                    ArrayList<Double> errorbay = rec2.regularization(ts, projectors[ii], parameters, offset, endY);
                                    synchronized (errors) {
                                        if(errors.size()==0) for(int i=0;i<parameters.getNbIterations();i++) errors.add(0.0);
                                        for (int e = 0; e < errorbay.size(); e++) errors.set(e,errors.get(e)+errorbay.get(e));
                                    }
                                    break;
                                case COMPRESSED_SENSING:
                                    projectors[ii].initForIterative(offset, endY, ts.getImageStackSize(), kernelToUse, false);
                                    projectors[ii].setPositivityConstraint(parameters.isPositivityConstraint());
                                    ArrayList<Double> errorcs = rec2.OSSART(ts, projectors[ii], parameters, offset, endY);
                                    synchronized (errors) {
                                        if(errors.size()==0) for(int i=0;i<parameters.getNbIterations();i++) errors.add(0.0);
                                        for (int e = 0; e < errorcs.size(); e++) errors.set(e,errors.get(e)+errorcs.get(e));
                                    }
                                    break;
                                case OSSART:
                                default:
                                    ArrayList<Double> errortmp = null;
                                    ArrayList<Double> errortmp2 = null;
                                    if (rec2.getCompletion() < 0) return;
                                    projectors[ii].setPositivityConstraint(parameters.isPositivityConstraint());
                                    if (saveError && saveForAll) {
                                        projectors[ii].initForIterative(offset, endY, parameters.getUpdateNb(), kernelToUse, true);
                                        errortmp = rec2.OSSART(ts, projectors[ii], parameters,offset, endY);
                                    } else if (saveError) {
                                        projectors[ii].initForIterative(offset, endY, parameters.getUpdateNb(), kernelToUse, false);
                                        ReconstructionParameters first=new ReconstructionParameters(parameters);
                                        first.setNbIterations(parameters.getNbIterations() - 1);
                                        errortmp = rec2.OSSART(ts, projectors[ii], first, offset, endY);
                                        if (rec2.getCompletion() < 0) return;
                                        projectors[ii].createErrorVolume();
                                        ReconstructionParameters last=new ReconstructionParameters(parameters);
                                        last.setNbIterations( 1);
                                        errortmp2 = rec2.OSSART(ts, projectors[ii], last, offset, endY);
                                    } else {
                                        projectors[ii].initForIterative(offset, endY, parameters.getUpdateNb(), kernelToUse, false);
                                        errortmp = rec2.OSSART(ts, projectors[ii], parameters, offset, endY);
                                    }
                                    synchronized (errors) {
                                        //if(errors.size()==0) for(int i=0;i<parameters.getNbIterations();i++) errors.add(0.0);
                                        for (int e = 0; e < errortmp.size(); e++) {
                                            if(errors.size()<=e) errors.add(errortmp.get(e));
                                            else errors.set(e,errors.get(e)+errortmp.get(e));
                                        }
                                        if (errortmp2 != null){
                                            if(errors.size()<=parameters.getNbIterations()-1)errors.add(errortmp2.get(0));
                                            else errors.set(errors.size()-1,errors.get(errors.size()-1)+errortmp2.get(0));
                                        }
                                    }
                                    break;

                            }
                            System.out.println("update from GPU ("+((offset==0)?0:usable[0])+" to "+((endY==rec2.getHeight())?endY:usable[1]));
                            System.out.flush();
                            projectors[ii].updateFromGPU(offset, endY,(offset==0)?0:usable[0],(endY==rec2.getHeight())?rec2.getHeight():usable[1]);
                            projectors[ii].releaseCL_Memory();
                            //devices[ii].releaseCL_Memory();
                        }
                    }
                }));
            }

        }
        for (Future f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return errors;
    }

    protected static int[] computeUsablePart(int starty,int endy, TiltSeries ts, TomoReconstruction2 rec2){
        System.out.println("compute usable part");
        System.out.println("align type="+ts.getAlignMethodForReconstruction());
        if(!(ts.getAlignment() instanceof AlignmentLandmarkImproved) || ts.getAlignMethodForReconstruction()!= TiltSeries.ALIGN_PROJECTOR) return new int[]{starty,endy,endy};
        double startyc=starty-ts.getProjectionCenterY();
        double endyc=endy-ts.getProjectionCenterY();
        double useStart= startyc;
        double useEnd= endyc;
        double startx=-ts.getProjectionCenterX();
        double endx=ts.getProjectionCenterX();
        double startz=-rec2.getCenterz();
        double endz=rec2.getCenterz();
        AlignmentLandmarkImproved ali=(AlignmentLandmarkImproved)ts.getAlignment();
        DoubleMatrix1D ori1= DoubleFactory1D.dense.make(new double[]{startx,startyc,startz});
        DoubleMatrix1D ori2= DoubleFactory1D.dense.make(new double[]{endx,startyc,startz});
//        DoubleMatrix1D ori1b= DoubleFactory1D.dense.make(new double[]{startx,startyc,endz});
//        DoubleMatrix1D ori2b= DoubleFactory1D.dense.make(new double[]{endx,startyc,endz});
        DoubleMatrix1D ori3= DoubleFactory1D.dense.make(new double[]{startx,endyc,startz});
        DoubleMatrix1D ori4= DoubleFactory1D.dense.make(new double[]{endx,endyc,startz});
//        DoubleMatrix1D ori3b= DoubleFactory1D.dense.make(new double[]{startx,endyc,endz});
//        DoubleMatrix1D ori4b= DoubleFactory1D.dense.make(new double[]{endx,endyc,endz});

        double currentRot=ali.getCurrentAi().get(0).getRot()-90;
        if(currentRot>45) currentRot-=90;
        if(currentRot<-45) currentRot+=90;
        DoubleMatrix2D Rz= MatrixUtils.rotation3DMatrixZ(currentRot);
        for(int i=0;i<ts.getImageStackSize();i++){
            DoubleMatrix2D Di= ali.getCurrentAi().get(i).getDi();
            DoubleMatrix2D Ai=Di.zMult(Rz,null);
            //DoubleMatrix2D Ai=ali.getCurrentAi().get(i).getAi();
            useStart=Math.max(useStart,Math.max(Ai.zMult(ori1,null).getQuick(1),Ai.zMult(ori2,null).getQuick(1)));
//            useStart=Math.max(useStart,Math.max(Ai.zMult(ori1b,null).getQuick(1),Ai.zMult(ori2b,null).getQuick(1)));
            useEnd=Math.min(useEnd,Math.min(Ai.zMult(ori3,null).getQuick(1),Ai.zMult(ori4,null).getQuick(1)));
//            useEnd=Math.min(useEnd,Math.min(Ai.zMult(ori3b,null).getQuick(1),Ai.zMult(ori4b,null).getQuick(1)));
        }
        int useEndInt=(int)Math.floor(useEnd);
        double nextStart=Math.floor(useEnd);




        ori3.setQuick(1, useEndInt);
        ori4.setQuick(1, useEndInt);
//        ori3b.setQuick(1, useEndInt);
//        ori4b.setQuick(1, useEndInt);
        for (int i = 0; i < ts.getImageStackSize(); i++) {
            DoubleMatrix2D Di= ali.getCurrentAi().get(i).getDi();
            DoubleMatrix2D Ai=Di.zMult(Rz,null);

            nextStart = Math.min(nextStart, Math.min(Ai.zMult(ori3, null).getQuick(1), Ai.zMult(ori4, null).getQuick(1)));
//            nextStart = Math.min(nextStart, Math.min(Ai.zMult(ori3b, null).getQuick(1), Ai.zMult(ori4b, null).getQuick(1)));

        }


        int nextUseStart = (int)nextStart;
        int modifier=0;
        while (nextUseStart-useEndInt!=0) {
            nextStart-=modifier;
            ori1.setQuick(1, nextStart);
            ori2.setQuick(1, nextStart);
//            ori1b.setQuick(1, nextStart);
//            ori2b.setQuick(1, nextStart);
            nextUseStart=(int)nextStart;
            for(int i=0;i<ts.getImageStackSize();i++){
                DoubleMatrix2D Di= ali.getCurrentAi().get(i).getDi();
                DoubleMatrix2D Ai=Di.zMult(Rz,null);

                nextUseStart = (int) Math.ceil(Math.max(nextUseStart, Math.max(Ai.zMult(ori1, null).getQuick(1), Ai.zMult(ori2, null).getQuick(1))));
//                nextUseStart = (int) Math.ceil(Math.max(nextUseStart, Math.max(Ai.zMult(ori1b, null).getQuick(1), Ai.zMult(ori2b, null).getQuick(1))));

            }
            modifier=nextUseStart-useEndInt;

        };
        int[] result=new int[]{(int)Math.ceil(useStart+ts.getProjectionCenterY()),(int)Math.floor(useEnd+ts.getProjectionCenterY()),(int)Math.floor(nextStart+ts.getProjectionCenterY())};

        System.out.println("range "+starty+" --> "+endy+ " usable only on "+result[0]+" --> "+result[1] + " next start: "+result[2]);
        return  result ;
    }

    public ArrayList<Double> doGPUrec(ReconstructionParameters parameters, Chrono time) {
        boolean positivityConstraint=false;
        if (devices == null) devices = GPUDevice.getGPUDevices();
        if (projectors == null) {
            //create projectors gpu
            projectors = new ProjectorGPU[devices.length];
            weigthingGPU = new FFTWeightingGPU[devices.length];
            for (int d = 0; d < devices.length; d++) {
                createProjector(d,parameters);
            }

        } else {
            //update values
            updateProjectorsGPU(parameters);
        }


        //if(use==null) askForGPU();
        System.out.println("dorec3:"+positivityConstraint);
        for (ProjectorGPU p : (ProjectorGPU[])projectors) {
            System.out.println(p.getDevice().getDeviceName()+" memory "+p.getDevice().getDeviceGlobalMemory());
        }
        errors = doGPURec(time, (ProjectorGPU[])projectors, use,parameters);
        for (ProjectorGPU p : (ProjectorGPU[])projectors) {
            p.releaseCL_Memory();
            System.out.println(p.getDevice().getDeviceName()+" memory "+p.getDevice().getDeviceGlobalMemory());
        }
        return errors;
    }


    public void savePrefs(ReconstructionParameters params){
        Prefs.set("TOMOJ_Thickness.int", params.getDepth());
        int recChoiceIndex = (isGpu()) ? 10 : 0;
        if (params.getReconstructionType()==BAYESIAN)
            recChoiceIndex += 5;
        else if (params.getReconstructionType()==TVM)
            recChoiceIndex += 6;
        else if (params.getReconstructionType()==WBP) recChoiceIndex += 1;
        else if (params.getReconstructionType()==OSSART && params.getUpdateNb()==1) recChoiceIndex += 2;
        else if (params.getReconstructionType()==OSSART && params.getUpdateNb()==ts.getImageStackSize()) recChoiceIndex += 3;
        else if (params.getReconstructionType()==OSSART) recChoiceIndex += 4;
        Prefs.set("TOMOJ_ReconstructionType.int", recChoiceIndex);
        Prefs.set("TOMOJ_SampleType.bool", params.isLongObjectCompensation());
        if(params.getReconstructionType()==WBP)Prefs.set("TOMOJ_wbp_diameter.double", params.getWeightingRadius());
        if(params.getNbIterations()!=0)Prefs.set("TOMOJ_IterationNumber.int", params.getNbIterations());
        if(params.getUpdateNb()!=0) Prefs.set("TOMOJ_updateOSART.int", params.getUpdateNb());
        if(!Double.isNaN(params.getRelaxationCoefficient())) Prefs.set("TOMOJ_relaxationCoefficient.double", params.getRelaxationCoefficient());
        if(!Double.isNaN(params.getRelaxationCoefficient()))Prefs.set("TOMOJ_Regul_Alpha.double", params.getRelaxationCoefficient());
        if(params instanceof AdvancedReconstructionParameters && !Double.isNaN(((AdvancedReconstructionParameters)params).getRegularizationWeight()))Prefs.set("TOMOJ_Regul_Lambda.double", ((AdvancedReconstructionParameters)params).getRegularizationWeight());
        if(params.getNbIterations()!=0)Prefs.set("TOMOJ_Regul_IterationNumber.int", params.getNbIterations());

    }

    private void computeAllProjections(ProjectorGPU p){
        int Ymax = (int) (p.getDevice().getOptimumMemoryUse() / (rec2.getWidth() * rec2.getSizez() * Sizeof.cl_float));
        for(int i=0;i<ts.getImageStackSize();i++){
            thprojs[i]=new float[ts.getWidth()*ts.getHeight()];
        }
        int y=0;
        do{
            int endY=Math.min(y+Ymax,rec2.getHeight()-1);
            p.initForIterative(y, endY, 1, ProjectorGPU.SEPARATE_PROJ_DIFF, false);
            for(int i=0;i<ts.getImageStackSize();i++){
                p.addProjection(i);
                p.project(y,endY);
                float[] tmp=p.getProjection(y,endY);
                System.arraycopy(tmp,0,thprojs[i],y*ts.getWidth(),(endY-y)*ts.getWidth());
                p.clearAllProjections();
            }
            y+=Ymax;
        }while(y<rec2.getHeight());
    }

    private void updateProjectorsGPU(ReconstructionParameters parameters){
        for (int d = 0; d < devices.length; d++) {
            if (rec2.getWidth() > devices[d].getDeviceMaxWidthImage3D()) {
                ((ProjectorGPU)projectors[d]).setUseImage3D(false);
                ((ProjectorGPU)projectors[d]).setKernelToUse(ProjectorGPU.ONE_KERNEL_PROJ_DIFF);
                System.out.println("size too big for image3D --> use Buffer");
            } else {
                ((ProjectorGPU)projectors[d]).setUseImage3D(true);
            }
            switch(parameters.getReconstructionType()) {
                case BP:
                case WBP:
                case OSSART:
                    if (projectors[d] instanceof VoxelProjector3DGPU) {
                        if (weigthingGPU[d] == null && (!Double.isNaN(parameters.getWeightingRadius())))
                            weigthingGPU[d] = new FFTWeightingGPU(devices[d], ts, parameters.getWeightingRadius() * ts.getWidth());
                        if (weigthingGPU[d] != null && (!Double.isNaN(parameters.getWeightingRadius())))
                            weigthingGPU[d].setDiameter(parameters.getWeightingRadius() * ts.getWidth());

                        projectors[d].setReconstruction(rec2);
                        projectors[d].setWeightingFilter((!Double.isNaN(parameters.getWeightingRadius())) ? weigthingGPU[d] : null);
                        //projectors[d] = new VoxelProjector3DGPU(ts, rec2, devices[d], (comboBoxRecChoice.getSelectedIndex() == 0 && checkBoxWeighting.isSelected()) ? new FFTWeightingGPU(devices[d], ts, ((SpinnerNumberModel) spinnerWBPDiameter.getModel()).getNumber().doubleValue() * ts.getWidth()) : null);
                        if (parameters.isRescaleData())
                            ((ProjectorGPU)projectors[d]).setScale(ts.getWidth() / rec2.getWidth());
                        ((ProjectorGPU)projectors[d]).setLongObjectCompensation(parameters.isLongObjectCompensation());

                        //projectors[d].setNbRaysPerPixels(recNbRaysPerPixels);

                    } else {
                        ((ProjectorGPU)projectors[d]).releaseCL_Memory();
                        createProjector(d,parameters);
                    }
                    break;
                case TVM:
                    if (projectors[d] instanceof TVMVoxelProjector3DGPU) {
                        projectors[d].setReconstruction(rec2);
                        //projectors[d] = new VoxelProjector3DGPU(ts, rec2, devices[d], (comboBoxRecChoice.getSelectedIndex() == 0 && checkBoxWeighting.isSelected()) ? new FFTWeightingGPU(devices[d], ts, ((SpinnerNumberModel) spinnerWBPDiameter.getModel()).getNumber().doubleValue() * ts.getWidth()) : null);
                        if (parameters.isRescaleData())
                            ((ProjectorGPU)projectors[d]).setScale(ts.getWidth() / rec2.getWidth());
                        ((ProjectorGPU)projectors[d]).setLongObjectCompensation(parameters.isLongObjectCompensation());
                        ((ProjectorGPU)projectors[d]).setPositivityConstraint(parameters.isPositivityConstraint());
                        ((TVMVoxelProjector3DGPU) projectors[d]).setRegularizationAlpha(parameters.getRelaxationCoefficient());
                        ((TVMVoxelProjector3DGPU) projectors[d]).setRegularizationLambda(((AdvancedReconstructionParameters)parameters).getRegularizationWeight());
                    } else {
                        ((ProjectorGPU)projectors[d]).releaseCL_Memory();
                        createProjector(d,parameters);
                    }
                    break;
                case BAYESIAN:
                    if (projectors[d] instanceof BayesianVoxelProjector3DGPU) {
                        projectors[d].setReconstruction(rec2);
                        //projectors[d] = new VoxelProjector3DGPU(ts, rec2, devices[d], (comboBoxRecChoice.getSelectedIndex() == 0 && checkBoxWeighting.isSelected()) ? new FFTWeightingGPU(devices[d], ts, ((SpinnerNumberModel) spinnerWBPDiameter.getModel()).getNumber().doubleValue() * ts.getWidth()) : null);
                        if (parameters.isRescaleData())
                            ((ProjectorGPU)projectors[d]).setScale(ts.getWidth() / rec2.getWidth());
                        ((ProjectorGPU)projectors[d]).setLongObjectCompensation(parameters.isLongObjectCompensation());
                        ((ProjectorGPU)projectors[d]).setPositivityConstraint(parameters.isPositivityConstraint());
                        ((BayesianVoxelProjector3DGPU) projectors[d]).setRegularizationAlpha(parameters.getRelaxationCoefficient());
                        ((BayesianVoxelProjector3DGPU) projectors[d]).setRegularizationLambda(((AdvancedReconstructionParameters)parameters).getRegularizationWeight());
                    } else {
                        ((ProjectorGPU)projectors[d]).releaseCL_Memory();
                        createProjector(d, parameters);
                    }
                    break;
                case COMPRESSED_SENSING:
                    if (projectors[d] instanceof CompressedSensingProjector3DGPU) {
                        projectors[d].setReconstruction(rec2);
                        //projectors[d] = new VoxelProjector3DGPU(ts, rec2, devices[d], (comboBoxRecChoice.getSelectedIndex() == 0 && checkBoxWeighting.isSelected()) ? new FFTWeightingGPU(devices[d], ts, ((SpinnerNumberModel) spinnerWBPDiameter.getModel()).getNumber().doubleValue() * ts.getWidth()) : null);
                        if (parameters.isRescaleData())
                            ((ProjectorGPU)projectors[d]).setScale(ts.getWidth() / rec2.getWidth());
                        ((ProjectorGPU)projectors[d]).setLongObjectCompensation(parameters.isLongObjectCompensation());
                        ((ProjectorGPU)projectors[d]).setPositivityConstraint(parameters.isPositivityConstraint());
                        ((CompressedSensingProjector3DGPU) projectors[d]).setWaveletType(((AdvancedReconstructionParameters)parameters).getWaveletType());
                        ((CompressedSensingProjector3DGPU) projectors[d]).setWaveletDegree(((AdvancedReconstructionParameters)parameters).getWaveletDegree());
                        ((CompressedSensingProjector3DGPU) projectors[d]).setWaveletShift(((AdvancedReconstructionParameters)parameters).getWaveletShift());
                        ((CompressedSensingProjector3DGPU) projectors[d]).setSoftThresholdingPercentageOfZeros(((AdvancedReconstructionParameters)parameters).getWaveletPercentageOfZeros());

                    } else {
                        ((ProjectorGPU)projectors[d]).releaseCL_Memory();
                        createProjector(d, parameters);
                    }
            }

        }
    }

}
