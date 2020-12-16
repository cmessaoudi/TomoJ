package fr.curie.gpu.tomoj.tomography.projectors;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import fr.curie.tomoj.tomography.projectors.Projector;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.jocl.cl_image_format;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.gpu.tomoj.tomography.filters.FFTWeightingGPU;
import fr.curie.utils.Chrono;
import fr.curie.gpu.utils.GPUDevice;

import java.awt.geom.AffineTransform;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import static org.jocl.CL.CL_FLOAT;
import static org.jocl.CL.CL_R;


public abstract class ProjectorGPU extends Projector {
    public final static int SEPARATE_PROJ_DIFF = 0;
    int kernelusage = SEPARATE_PROJ_DIFF;
    public final static int ONE_KERNEL_PROJ_DIFF = 1;
    public final static int ONE_KERNEL_PROJ_DIFF_WITH_ALIGNMENT = 2;
    protected static float ACCURACY = 0.0000000001f;
    protected ExecutorService exec;
    protected double scale = 1.0;
    protected boolean longObjectCompensation = false;
    protected boolean volumeChanged = false;
    int nbcpu = Prefs.getThreads();
    GPUDevice device;
    long totalTimeCopy, totalTimeProjection, totalTimeLoad, totalTimeDiff, totalTimeBP;
    ArrayList<Integer> oriProjsIndex;
    ArrayList<Integer> diffsIndex;
    ArrayList<Integer> projsIndex;
    ArrayList<Integer> normsIndex;
    protected ArrayList<Integer> volMemBufferIndex;
    protected ArrayList<Integer> volMemImage3DIndex;
    int currentWorkingBuffer = 0;
    int currentWorkingImage3D = 0;
    ArrayList<DoubleMatrix2D> eulers;
    ArrayList<Float> longObjComp;
    ArrayList<float[]> stats;
    ArrayList<AffineTransform> Tinvs;
    int currentProjIndex;
    int currentOriProjIndex;
    int kernelOffset = 0;
    ArrayList<Integer> errorVolumeIndex;
    int currentIteration = 0;
    String savedir;
    ImagePlus errorVolume;
    int currentStartY;
    int currentEndY;
    protected boolean useImage3D;
    protected boolean positivityConstraint;
    protected int Ysize=0;


    public ProjectorGPU(TiltSeries ts, TomoReconstruction2 rec, GPUDevice device, FFTWeightingGPU weightingFilter) {
        super(ts, rec, weightingFilter);
        this.device = device;
        initCL();
        useImage3D=(rec.getWidth()<=device.getDeviceMaxWidthImage3D());
        if(!useImage3D) System.out.println("size of reconstruction too big for image3D ! --> use buffer");
        currentOriProjIndex = -1;
        currentProjIndex = -1;
        oriProjsIndex = new ArrayList<Integer>();
        projsIndex = new ArrayList<Integer>();
        normsIndex = new ArrayList<Integer>();
        diffsIndex = new ArrayList<Integer>();
        volMemBufferIndex = new ArrayList<Integer>();
        volMemImage3DIndex = new ArrayList<Integer>();
        eulers = new ArrayList<DoubleMatrix2D>();
        longObjComp = new ArrayList<Float>();
        Tinvs = new ArrayList<AffineTransform>();
        stats = new ArrayList<float[]>();
        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts.getFileInfo();
        }
        savedir = (fi != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
    }

    public ProjectorGPU(TiltSeries ts, TomoReconstruction2 rec,  FFTWeightingGPU weightingFilter){
        super(ts,rec,weightingFilter);

    }

    protected void initCL() {
        kernelOffset = device.getNbKernels();
        String programSource = getSourceCode();
        device.printDeviceInfo();
        //device.printPlatformsInfo();
        device.compileProgram(programSource, (device.getSupportImage3DWrite()) ? "-D IMAGE3D_WRITE" : null);
        device.compileKernel(kernelOffset + 0, "_0_projectImage3D_Partial");
        device.compileKernel(kernelOffset + 1, "_1_diff");
        device.compileKernel(kernelOffset + 2, "_2_backProject_Partial_1P");
        device.compileKernel(kernelOffset + 3, "_3_backProject_Partial_2P");
        device.compileKernel(kernelOffset + 4, "_4_backProject_Partial_4P");
        device.compileKernel(kernelOffset + 5, "_5_projectImage3D_Partial_Diff");
        device.compileKernel(kernelOffset + 6, "_6_projectImage3D_Partial_Diff_Ali");
        device.compileKernel(kernelOffset + 7, "squareImage");
        device.compileKernel(kernelOffset + 8, "_7_projectBuffer_Partial_Diff_Ali");
        device.compileKernel(kernelOffset + 9, "_8_projectBuffer_Partial");
        device.compileKernel(kernelOffset + 10, "_10_projectImage3D_deform_Partial_Diff_Ali");
        device.compileKernel(kernelOffset + 11, "_11_projectImage3D_deform_Partial_Diff");
        device.compileKernel(kernelOffset + 12, "_12_projectImage3D_deform_Partial");
    }

    private String getSourceCode() {
        System.out.println(this.getClass().getResource("TomoReconstructionGPU.class"));
        System.out.println(this.getClass().getResource("/TomoReconstructionGPU.cl"));

        String programSource = "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getResource("/TomoReconstructionGPU.cl").openStream()));

            String line;
            while ((line = br.readLine()) != null) {
                programSource += line + "\n";
                //System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return programSource;
    }

    public void setScale(double value) {
        scale = value;
    }

    public void setLongObjectCompensation(boolean longObjectCompensation) {
        this.longObjectCompensation = longObjectCompensation;
    }

    public void setPositivityConstraint(boolean positivityConstraint) {
        this.positivityConstraint = positivityConstraint;
    }

    public void removeErrorVolume() {
        if (errorVolume != null) errorVolume = null;
        if (errorVolumeIndex != null) {
            for (int i : errorVolumeIndex) device.releaseCL(device.getBuffer(i));
            errorVolumeIndex.clear();
            errorVolumeIndex = null;
        }
    }

    public long computeMaximumSizeForVolume() {
        long mem = device.getDeviceGlobalMemory();
        long maxMemAlloc = device.getDeviceMaxAllocationMemory();
        return (long) Math.min(mem * 0.4, maxMemAlloc);
    }

    public void setKernelToUse(int kernelusage) {
        this.kernelusage = kernelusage;
    }
    public void setUseImage3D(boolean useImage3D) {
        this.useImage3D = useImage3D;
    }

    public void initForBP(int startY, int endY, int updateNb) {
        currentStartY = startY;
        currentEndY = endY;
        Ysize=endY-startY;
        initVolMemBuffer(startY, endY, false);
        cl_image_format imageFormat = new cl_image_format();
        imageFormat.image_channel_order = CL_R;
        imageFormat.image_channel_data_type = CL_FLOAT;
        for (int n = 0; n < updateNb; n++) {
            float[] tmp = new float[projectionWidth * projectionHeight];
            oriProjsIndex.add(device.addImage2D(tmp, projectionWidth, projectionHeight, imageFormat));
            eulers.add(ts.getAlignment().getEulerMatrix(0));
            longObjComp.add(0f);
        }
        kernelusage = SEPARATE_PROJ_DIFF;
    }

    /**
     * Initialize the memory objects for the input and output images
     */
    public void initVolMemBuffer(int startY, int endY, boolean copy) {
        currentStartY = startY;
        currentEndY = endY;
        Ysize = endY - startY;
        float[] tmp = new float[volumeWidth * Ysize * volumeDepth];
        if (copy) {
            for (int i = 0; i < rec.getImageStackSize(); i++) {
                float[] slice = (float[]) rec.getImageStack().getPixels(i + 1);
                System.arraycopy(slice, volumeWidth * startY, tmp, i * volumeWidth * Ysize, volumeWidth * Ysize);
            }
        }
        volMemBufferIndex.add(device.addBuffer(tmp, true));
        currentWorkingBuffer = volMemBufferIndex.size() - 1;

    }

    public void initForIterative(int startY, int endY, int update, int kernelType, boolean errorVolume) {
        initVolMemBuffer(startY, endY, true);
        if(useImage3D) initVolMemImage(startY, endY, true);
        initProjectionsOnGPUs(update, kernelType);
        if (errorVolume) createErrorVolume();
    }
    public void initForIterative(int startY, int endY, int update, int kernelType, boolean errorVolume, ProjectorGPU other) {
        volMemBufferIndex.add(other.volMemBufferIndex.get(other.currentWorkingBuffer));
        currentWorkingBuffer=volMemBufferIndex.size()-1;
        //initVolMemBuffer(startY, endY, true);
        //initVolMemImage(startY, endY, true);
        if(other.useImage3D){
            useImage3D=other.useImage3D;
            volMemImage3DIndex.add(other.volMemImage3DIndex.get(other.currentWorkingImage3D));
            currentWorkingImage3D=volMemImage3DIndex.size()-1;
        } else{
            useImage3D=false;
        }
        initProjectionsOnGPUs(update, kernelType);
        Ysize=endY-startY;
        if (errorVolume) createErrorVolume();
    }

    /**
     * Initialize the memory objects for the input and output images
     */
    public void initVolMemImage(int startY, int endY, boolean copy) {
        currentStartY = startY;
        currentEndY = endY;
        int YSize = endY - startY;
        cl_image_format imageFormat = new cl_image_format();
        imageFormat.image_channel_order = CL_R;
        imageFormat.image_channel_data_type = CL_FLOAT;

        float[] tmp = new float[volumeWidth * YSize * volumeDepth];
        if (copy) {
            for (int i = 0; i < rec.getImageStackSize(); i++) {
                float[] slice = (float[]) rec.getImageStack().getPixels(i + 1);
                System.arraycopy(slice, volumeWidth * startY, tmp, i * volumeWidth * YSize, volumeWidth * YSize);
            }
        }
        volMemImage3DIndex.add(device.addImage3D(tmp, volumeWidth, YSize, volumeDepth, imageFormat));
        currentWorkingImage3D = volMemImage3DIndex.size() - 1;
    }

    public void initProjectionsOnGPUs(int updateNb, int kernelType) {
        kernelusage = kernelType;
        int w = (ts != null) ? ts.getWidth() : projectionWidth;
        int h = (ts != null) ? ts.getHeight() : projectionHeight;
        cl_image_format imageFormat = new cl_image_format();
        imageFormat.image_channel_order = CL_R;
        imageFormat.image_channel_data_type = CL_FLOAT;
        eulers.clear();
        for (int n = 0; n < updateNb; n++) {
            float[] tmp = new float[w * h];
            oriProjsIndex.add(device.addImage2D(tmp, w, h, imageFormat));
            if (kernelusage == SEPARATE_PROJ_DIFF) {
                projsIndex.add(device.addImage2D(tmp, w, h, imageFormat));
                normsIndex.add(device.addImage2D(tmp, w, h, imageFormat));
            }
            diffsIndex.add(device.addImage2D(tmp, w, h, imageFormat));
            eulers.add(ts.getAlignment().getEulerMatrix(0));
            longObjComp.add(0f);
            Tinvs.add(new AffineTransform());
            stats.add(new float[2]);
        }
        currentIteration = 0;
    }

    public void createErrorVolume() {
        if (errorVolume == null) {
            ImageStack is = new ImageStack(volumeWidth, volumeHeight);
            for (int i = 0; i < volumeDepth; i++) {
                is.addSlice(new FloatProcessor(volumeWidth, volumeHeight));
            }
            errorVolume = new ImagePlus("error Volume", is);
        }
        errorVolumeIndex = new ArrayList<Integer>(1);
        float[] tmp = new float[(int) device.getBufferLength(currentWorkingBuffer)];
        errorVolumeIndex.add(0, device.addBuffer(tmp, true));
    }

    public ArrayList<Integer> getVolMemBufferIndex() {
        return volMemBufferIndex;
    }

    public void updateFromGPU(int startY, int endY) {
        updateFromGPUBuffer(device, volMemBufferIndex.get(currentWorkingBuffer), rec, startY, endY, 0, 0);
    }
    public void updateFromGPU(int startY, int endY, int usableStartY, int usableEndY) {
        updateFromGPUBuffer(device, volMemBufferIndex.get(currentWorkingBuffer), rec, startY, endY, usableStartY-startY, endY-usableEndY);
    }

    public void updateFromGPUBuffer(GPUDevice device, int volIndex, ImagePlus volToBeUpdated, int startY, int endY, int YoffsetStart, int YoffsetEnd) {
        int YSliceSize = endY - startY;
        float[] tmp = new float[volumeWidth * YSliceSize * volumeDepth];
        ImageStack data = volToBeUpdated.getImageStack();
        device.readFromBuffer(volIndex, tmp);

        for (int i = 0; i < volToBeUpdated.getImageStackSize(); i++) {
            float[] slice = (float[]) data.getPixels(i + 1);
            System.arraycopy(tmp, i * volumeWidth * YSliceSize + YoffsetStart * volumeWidth, slice, startY * volumeWidth + YoffsetStart * volumeWidth, volumeWidth * (YSliceSize - YoffsetEnd - YoffsetStart));

        }
    }

    public void copyInGPUBuffer(int startY, int endY){
        copyInGPUBuffer(device,volMemBufferIndex.get(currentWorkingBuffer),rec,startY,endY);
    }

    public void copyInGPUBuffer(GPUDevice device, int volIndex, ImagePlus updatingVolume, int startY, int endY){
        int YSliceSize = endY - startY;
        float[] tmp = new float[volumeWidth * YSliceSize * volumeDepth];
        ImageStack data = updatingVolume.getImageStack();
        for (int i = 0; i < updatingVolume.getImageStackSize(); i++) {
            float[] slice = (float[]) data.getPixels(i + 1);
            System.arraycopy(slice, startY * volumeWidth , tmp, i * volumeWidth * YSliceSize ,  volumeWidth * (YSliceSize));
        }
        device.writeBuffer(volIndex,tmp);
    }

    public double getPerformanceTime() {
        System.out.println("doing performance test for " + device.getDeviceName());
        releaseCL_Memory();
        //init things
        int sliceSize = Math.min((int) (computeMaximumSizeForVolume(device) / (volumeWidth * volumeHeight * 4) / 2),volumeHeight/2);
        int startY = 0;
        int endY = startY + sliceSize;
        System.out.println("#" + device.getDeviceName() + " init volumes in memory of size (Y) :" + sliceSize +" ("+startY+", "+ endY+")");
        initVolMemImage(startY, endY, false);
        initVolMemBuffer(startY, endY, false);
        initProjectionsOnGPUs(1, kernelusage);
        int firstProjectionIndex = 0;
        //int lastprojectionIndex = Math.min(ts.getZeroIndex()+10,ts.getImageStackSize());
        int lastprojectionIndex = ts.getImageStackSize();
        //performs first computation to remove first transfert limitation
        System.out.println("#" + device.getDeviceName() + " first run from " + ts.getZeroIndex() + " to " + lastprojectionIndex);
        for (int i = firstProjectionIndex; i < lastprojectionIndex; i++) {
            addProjection(i);
            projectAndDifference(1, startY, endY);
            backproject(startY, endY);
            clearAllProjections();
        }
        //do truely computation test
        System.out.println("#" + device.getDeviceName() + " second run (timed)");
        Chrono time = new Chrono();
        time.start();
        time.startNano();
        for (int i = firstProjectionIndex; i < lastprojectionIndex; i++) {
            addProjection(i);
            projectAndDifference(1, startY, endY);
            backproject(startY, endY);
            clearAllProjections();
        }
        time.stopNano();
        time.stop();
        //free memory
        releaseCL_Memory();
        clearAllProjections();
        System.out.println("device: " + device.getDeviceName().trim() + " performance time: " + time.delay() + " , nano:" + time.delayNano());
        return (time.delay() == 0) ? time.delayNano() / (double) sliceSize : time.delay() / (double) sliceSize;


    }

    public void releaseCL_Memory() {
        //device.releaseCL_Memory();
        for (int i : oriProjsIndex) device.releaseCL(device.getImage2D(i));
        oriProjsIndex.clear();
        currentOriProjIndex=-1;
        for (int i : projsIndex) device.releaseCL(device.getImage2D(i));
        projsIndex.clear();
        currentProjIndex=-1;
        for (int i : normsIndex) device.releaseCL(device.getImage2D(i));
        normsIndex.clear();
        for (int i : diffsIndex) device.releaseCL(device.getImage2D(i));
        diffsIndex.clear();
        eulers.clear();
        Tinvs.clear();
        stats.clear();
        longObjComp.clear();
        for (int i : volMemBufferIndex) device.releaseCL(device.getBuffer(i));
        volMemBufferIndex.clear();
        currentWorkingBuffer=0;
        for (int i : volMemImage3DIndex) device.releaseCL(device.getImage3D(i));
        volMemImage3DIndex.clear();
        currentWorkingImage3D=0;
        if (errorVolumeIndex != null) {
            for (int i : errorVolumeIndex) device.releaseCL(device.getBuffer(i));
            errorVolumeIndex.clear();
            errorVolumeIndex = null;
        }
    }

    public long computeMaximumSizeForVolume(GPUDevice device) {
        long mem = device.getDeviceGlobalMemory();
        long maxMemAlloc = device.getDeviceMaxAllocationMemory();
        return (long) Math.min(mem * 0.4, maxMemAlloc);
    }

    public void addProjection(int index) {
        currentOriProjIndex++;
//        if(ts.getTomoJPoints()!=null&&ts.getTomoJPoints().getLandmarks3D()!=null&&ts.getTomoJPoints().getLandmarks3D().getBestAlignment()!=null){
//            eulers.set(currentOriProjIndex, ts.getTomoJPoints().getLandmarks3D().getBestAlignment().getCurrentAi().get(index).getEuler());
//            kernelusage = ONE_KERNEL_PROJ_DIFF;
//        }else{
//            eulers.set(currentOriProjIndex, ts.getEulerMatrix(index));
//        }
        kernelusage = ONE_KERNEL_PROJ_DIFF;
        eulers.set(currentOriProjIndex, ts.getAlignment().getEulerMatrix(index));
        if (weightingFilter != null) kernelusage = ONE_KERNEL_PROJ_DIFF;
        ImageProcessor proj = (kernelusage == ONE_KERNEL_PROJ_DIFF_WITH_ALIGNMENT) ? ts.getImageStack().getProcessor(index + 1) : new FloatProcessor(ts.getWidth(), ts.getHeight(), ts.getPixels(index));
        device.writeImage2D(oriProjsIndex.get(currentOriProjIndex), (float[]) proj.getPixels(), projectionWidth, projectionHeight);

        longObjComp.set(currentOriProjIndex, this.longObjectCompensation ? (float) Math.min(Math.abs(volumeWidth / Math.cos(Math.toRadians(90 - ts.getTiltAngle(index)))), Math.abs(volumeDepth / Math.cos(Math.toRadians(ts.getTiltAngle(index))))) : 0);
        if (kernelusage == ONE_KERNEL_PROJ_DIFF_WITH_ALIGNMENT) {
            AffineTransform T = ts.getAlignment().getTransform(index);
            AffineTransform Tinv;
            try {
                Tinv = T.createInverse();
            } catch (Exception ex) {
                System.out.println("error in VoxelProjector3DGPU " + ex);
                return;
            }
            Tinvs.set(currentOriProjIndex, Tinv);
            stats.set(currentOriProjIndex, ts.getStatsAsArray(index));
        }
    }

    public void clearAllProjections() {
        currentOriProjIndex = -1;
        currentProjIndex = -1;
    }



    public void clearErrorVolume() {
        device.fillBuffer(errorVolumeIndex.get(0), 0);
    }


    public void differenceOnGPU(GPUDevice device, int experimental, int theoretical, int norm, int diffResult, float factor, float longObjectCompensation) {

        long[] globalWorkSize = new long[2];
        globalWorkSize[0] = device.getImage2DWidth(experimental);
        globalWorkSize[1] = device.getImage2DHeight(experimental);
        Object[] args = new Object[6];
        args[0] = device.getImage2D(experimental);
        args[1] = device.getImage2D(theoretical);
        args[2] = device.getImage2D(norm);
        args[3] = device.getImage2D(diffResult);
        args[4] = new float[]{factor};
        args[5] = new float[]{longObjectCompensation};
        device.runKernel(kernelOffset + 1, args, globalWorkSize);


    }

    public void projectImage3DPartial_Diff(GPUDevice device, int indexExpProj, int indexDiff, DoubleMatrix2D euler, int startY, int endY, float factor, float longObjectCompensation, boolean rescale) {
        int YsliceSize = endY - startY;
        final long psx = device.getImage2DWidth(indexExpProj);
        //final float pcx = (float) ((width - 1.0) / 2.0);
        //final float pcy = (float) ((height - 1.0) / 2.0);
        float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = psx;
        globalWorkSize[1] = Math.min(Math.round(YsliceSize * scale), ts.getHeight());
        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        final DoubleMatrix2D eulert = euler.viewDice();
        //final DoubleMatrix2D eulert = new DenseDoubleAlgebra().inverse(euler);

        final double dirx = (euler.getQuick(2, 0) == 0) ? ACCURACY : euler.getQuick(2, 0);
        final double diry = (euler.getQuick(2, 1) == 0) ? ACCURACY : euler.getQuick(2, 1);
        final double dirz = (euler.getQuick(2, 2) == 0) ? ACCURACY : euler.getQuick(2, 2);
        float[] eulerProj = {(float) eulert.getQuick(0, 0), (float) eulert.getQuick(0, 1), (float) eulert.getQuick(1, 0), (float) eulert.getQuick(1, 1), (float) eulert.getQuick(2, 0), (float) eulert.getQuick(2, 1), 0, 0};
        float[] dir = new float[]{(float) dirx, (float) diry, (float) dirz, 0};
        float[] pCenter = new float[]{(float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
        //System.out.println("project_partial_diff pcx="+pcx);
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};

        Object[] args = new Object[13];
        args[0] = device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D));
        args[1] = device.getImage2D(indexExpProj);
        args[2] = device.getImage2D(indexDiff);
        args[3] = eulerProj;
        args[4] = dir;
        args[5] = pCenter;
        args[6] = vCenter;
        args[7] = dim;
        args[8] = new int[]{startY};
        args[9] = new float[]{factor};
        args[10] = new float[]{longObjectCompensation};
        args[11] = new float[]{(rescale) ? scale : 1, rescale ? 0 : (device.getImage2DHeight(indexExpProj) - volumeHeight) / 2};
        //args[11] = new float[]{(rescale) ? scale : 1, 0};
        //args[12] = (globalDeformation!=null)?new float[]{(float)globalDeformation.getQuick(0,0),(float)globalDeformation.getQuick(1,1),(float)globalDeformation.getQuick(2,2),(float)globalDeformation.getQuick(1,0)}:new float[]{1,1,1,0};
        args[12] = new float[]{1,1,1,0};
        device.runKernel(kernelOffset + 5, args, globalWorkSize);
    }

    public void projectImage3DPartial_Diff_unalignedProjection(GPUDevice device, int indexExpProj, int indexDiff, DoubleMatrix2D euler, int startY, int endY, float factor, float longObjectCompensation, AffineTransform Tinv, float[] stats, boolean rescale) {
        int YsliceSize = endY - startY;
        final long psx = device.getImage2DWidth(indexExpProj);
        //final float pcx = (float) ((width - 1.0) / 2.0);
        //final float pcy = (float) ((height - 1.0) / 2.0);
        float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = psx;
        globalWorkSize[1] = Math.round((YsliceSize) * scale);

        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        final DoubleMatrix2D eulert = euler.viewDice();
        //final DoubleMatrix2D eulert = new DenseDoubleAlgebra().inverse(euler);

        final double dirx = (euler.getQuick(2, 0) == 0) ? ACCURACY : euler.getQuick(2, 0);
        final double diry = (euler.getQuick(2, 1) == 0) ? ACCURACY : euler.getQuick(2, 1);
        final double dirz = (euler.getQuick(2, 2) == 0) ? ACCURACY : euler.getQuick(2, 2);
        float[] eulerProj = {(float) eulert.getQuick(0, 0), (float) eulert.getQuick(0, 1), (float) eulert.getQuick(1, 0), (float) eulert.getQuick(1, 1), (float) eulert.getQuick(2, 0), (float) eulert.getQuick(2, 1), 0, 0};
        float[] dir = new float[]{(float) dirx, (float) diry, (float) dirz, 0};
        float[] pCenter = new float[]{(float) projectionCenters[0], (float) projectionCenters[1], (float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
        //System.out.println("project_partial_diff_ali ");
        //System.out.println("pcx="+pcx+"\t pcy="+pcy);
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};

        double[] mat = new double[6];
        Tinv.getMatrix(mat);
        float[] matfTinv = new float[8];
        for (int i = 0; i < mat.length; i++) matfTinv[i] = (float) mat[i];
        matfTinv[6] = (float) projectionCenters[0];
        matfTinv[7] = (float) projectionCenters[1];


        Object[] args = new Object[15];
        args[0] = device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D));
        args[1] = device.getImage2D(indexExpProj);
        args[2] = device.getImage2D(indexDiff);
        args[3] = eulerProj;
        args[4] = dir;
        args[5] = pCenter;
        args[6] = vCenter;
        args[7] = dim;
        args[8] = new int[]{startY};
        args[9] = new float[]{factor};
        args[10] = new float[]{longObjectCompensation};
        args[11] = matfTinv;
        args[12] = stats;
        args[13] = new float[]{(rescale) ? scale : 1, rescale ? 0 : (device.getImage2DHeight(indexExpProj) - volumeHeight) / 2};
        //args[14] = (globalDeformation!=null)?new float[]{(float)globalDeformation.getQuick(0,0),(float)globalDeformation.getQuick(1,1),(float)globalDeformation.getQuick(2,2),(float)globalDeformation.getQuick(1,0)}:new float[]{1,1,1,0};
        args[14] = new float[]{1,1,1,0};
        device.runKernel(kernelOffset + 6, args, globalWorkSize);
    }


    public void projectBufferPartial_Diff_unalignedProjection(GPUDevice device, int indexExpProj, int indexDiff, DoubleMatrix2D euler, int startY, int endY, float factor, float longObjectCompensation, AffineTransform Tinv, float[] stats, boolean rescale) {
        int YsliceSize = endY - startY;
        final long psx = device.getImage2DWidth(indexExpProj);
        //final float pcx = (float) ((width - 1.0) / 2.0);
        //final float pcy = (float) ((height - 1.0) / 2.0);
        float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = psx;
        globalWorkSize[1] = Math.round((YsliceSize) * scale);
        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        final DoubleMatrix2D eulert = euler.viewDice();
        //final DoubleMatrix2D eulert = new DenseDoubleAlgebra().inverse(euler);

        final double dirx = (euler.getQuick(2, 0) == 0) ? ACCURACY : euler.getQuick(2, 0);
        final double diry = (euler.getQuick(2, 1) == 0) ? ACCURACY : euler.getQuick(2, 1);
        final double dirz = (euler.getQuick(2, 2) == 0) ? ACCURACY : euler.getQuick(2, 2);
        float[] eulerProj = {(float) eulert.getQuick(0, 0), (float) eulert.getQuick(0, 1), (float) eulert.getQuick(1, 0), (float) eulert.getQuick(1, 1), (float) eulert.getQuick(2, 0), (float) eulert.getQuick(2, 1), 0, 0};
        float[] dir = new float[]{(float) dirx, (float) diry, (float) dirz, 0};
        float[] pCenter = new float[]{(float) projectionCenters[0], (float) projectionCenters[1], (float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
        //System.out.println("project_partial_diff_ali ");
        //System.out.println("pcx="+pcx+"\t pcy="+pcy);
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};

        double[] mat = new double[6];
        Tinv.getMatrix(mat);
        float[] matfTinv = new float[8];
        for (int i = 0; i < mat.length; i++) matfTinv[i] = (float) mat[i];
        matfTinv[6] = (float) projectionCenters[0];
        matfTinv[7] = (float) projectionCenters[1];


        Object[] args = new Object[15];
        //args[0] = device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D));
        args[0] = device.getBuffer(volMemBufferIndex.get(currentWorkingBuffer));
        args[1] = device.getImage2D(indexExpProj);
        args[2] = device.getImage2D(indexDiff);
        args[3] = eulerProj;
        args[4] = dir;
        args[5] = pCenter;
        args[6] = vCenter;
        args[7] = dim;
        args[8] = new int[]{startY};
        args[9] = new float[]{factor};
        args[10] = new float[]{longObjectCompensation};
        args[11] = matfTinv;
        args[12] = stats;
        args[13] = new float[]{(rescale) ? scale : 1, rescale ? 0 : (device.getImage2DHeight(indexExpProj) - volumeHeight) / 2};
        //args[14] = (globalDeformation!=null)?new float[]{(float)globalDeformation.getQuick(0,0),(float)globalDeformation.getQuick(1,1),(float)globalDeformation.getQuick(2,2),(float)globalDeformation.getQuick(1,0)}:new float[]{1,1,1,0};
        args[14] = new float[]{1,1,1,0};
        device.runKernel(kernelOffset + 8, args, globalWorkSize);
    }

    protected double computeError(float[] diffPixels) {
        double error = 0;
        int compt = 0;
        for (float f : diffPixels) {
            if (!Float.isNaN(f) && f != 0) {
                error += f * f;
                compt++;
            }
        }
        return error / compt;
    }

    public void backProjectBuffer_Partial_4P(GPUDevice device, int vol, int proj1, DoubleMatrix2D euler1, int proj2, DoubleMatrix2D euler2, int proj3, DoubleMatrix2D euler3, int proj4, DoubleMatrix2D euler4, int startY, int endY, boolean rescale,boolean positivityConstraint) {
        int YsliceSize = endY - startY;
        long[] globalWorkSize = new long[3];
        globalWorkSize[0] = volumeWidth;
        globalWorkSize[1] = YsliceSize;
        globalWorkSize[2] = volumeDepth;
        if(globalDeformationInv!=null) {
            euler1 = euler1.zMult(globalDeformationInv, null);
            euler2 = euler2.zMult(globalDeformationInv, null);
            euler3 = euler3.zMult(globalDeformationInv, null);
            euler4 = euler4.zMult(globalDeformationInv, null);
        }
        float[] eulerProj = {(float) euler1.getQuick(0, 0), (float) euler1.getQuick(0, 1), (float) euler1.getQuick(0, 2), (float) euler1.getQuick(1, 0), (float) euler1.getQuick(1, 1), (float) euler1.getQuick(1, 2),
                (float) euler2.getQuick(0, 0), (float) euler2.getQuick(0, 1), (float) euler2.getQuick(0, 2), (float) euler2.getQuick(1, 0), (float) euler2.getQuick(1, 1), (float) euler2.getQuick(1, 2),
                (float) euler3.getQuick(0, 0), (float) euler3.getQuick(0, 1), (float) euler3.getQuick(0, 2), (float) euler3.getQuick(1, 0), (float) euler3.getQuick(1, 1), (float) euler3.getQuick(1, 2),
                (float) euler4.getQuick(0, 0), (float) euler4.getQuick(0, 1), (float) euler4.getQuick(0, 2), (float) euler4.getQuick(1, 0), (float) euler4.getQuick(1, 1), (float) euler4.getQuick(1, 2),};

        //float[] projCenter = {(float) ((width - 1.0) / 2.0), (float) ((height - 1.0) / 2.0), width, height};
        float[] projCenter = {(float) projectionCenters[2], (float) projectionCenters[3], device.getImage2DWidth(proj1), device.getImage2DHeight(proj1)};
        if (!rescale) {
            projCenter[0]/=scale;
            projCenter[1]/=scale;
        }
        float[] Vdim = new float[]{volumeWidth, volumeWidth, volumeHeight, volumeWidth * YsliceSize};
//        if (!rescale) {
//            Vdim[1] = device.getImage2DWidth(proj1);
//            Vdim[2] = device.getImage2DHeight(proj1);
//        }
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }

        Object[] args = new Object[13];
        args[0] = device.getBuffer(vol);
        args[1] = device.getImage2D(proj1);
        args[2] = device.getImage2D(proj2);
        args[3] = device.getImage2D(proj3);
        args[4] = device.getImage2D(proj4);
        args[5] = eulerProj;
        args[6] = projCenter;
        args[7] = vCenter;
        args[8] = Vdim;
        args[9] = new int[]{startY};
        //args[10] = (globalDeformationInv!=null)?new float[]{(float)globalDeformationInv.getQuick(0,0),(float)globalDeformationInv.getQuick(1,1),(float)globalDeformationInv.getQuick(2,2),(float)globalDeformation.getQuick(1,0)}:new float[]{1,1,1,0};
        args[10] = new float[]{1,1,1,0};
        args[11] = new boolean[]{positivityConstraint};
        args[12]= new float[]{(float)scale};
        //System.out.println("runKernel");
        device.runKernel(kernelOffset + 4, args, globalWorkSize);
        //device.removeBuffer(1);

    }

    protected void computeErrorImage(int indexOriginal, int indexResult) {
        Object[] args2 = new Object[]{device.getImage2D(indexOriginal), device.getImage2D(indexResult)};
        long[] globalWorkSize = new long[]{projectionWidth, projectionHeight};

        device.runKernel(kernelOffset + 7, args2, globalWorkSize);
    }

    public void backProjectBuffer_Partial_2P(GPUDevice device, int vol, int proj1, DoubleMatrix2D euler1, int proj2, DoubleMatrix2D euler2, int startY, int endY, boolean rescale,boolean positivityConstraint) {
        int YsliceSize = endY - startY;
        long[] globalWorkSize = new long[3];
        globalWorkSize[0] = volumeWidth;
        globalWorkSize[1] = YsliceSize;
        globalWorkSize[2] = volumeDepth;
        //float[] eulerProj1 = {(float) euler1.getQuick(0, 0), (float) euler1.getQuick(0, 1), (float) euler1.getQuick(0, 2), (float) euler1.getQuick(1, 0), (float) euler1.getQuick(1, 1), (float) euler1.getQuick(1, 2), 0, 0};
        if(globalDeformationInv!=null){
            euler1=euler1.zMult(globalDeformationInv,null);
            euler2=euler2.zMult(globalDeformationInv,null);
        }
        float[] eulerProj = {(float) euler1.getQuick(0, 0), (float) euler1.getQuick(0, 1), (float) euler1.getQuick(0, 2), (float) euler1.getQuick(1, 0), (float) euler1.getQuick(1, 1), (float) euler1.getQuick(1, 2), 0, 0, (float) euler2.getQuick(0, 0), (float) euler2.getQuick(0, 1), (float) euler2.getQuick(0, 2), (float) euler2.getQuick(1, 0), (float) euler2.getQuick(1, 1), (float) euler2.getQuick(1, 2), 0, 0};

        //float[] projCenter = {(float) ((width - 1.0) / 2.0), (float) ((height - 1.0) / 2.0), width, height};
        float[] projCenter = {(float) projectionCenters[2], (float) projectionCenters[3], device.getImage2DWidth(proj1), device.getImage2DHeight(proj1)};
        if (!rescale) {
            projCenter[0]/=scale;
            projCenter[1]/=scale;
        }
        float[] Vdim = new float[]{volumeWidth, volumeWidth, volumeHeight, volumeWidth * YsliceSize};
//        if (!rescale) {
//            Vdim[1] = device.getImage2DWidth(proj1);
//            Vdim[2] = device.getImage2DHeight(proj1);
//        }
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }

        Object[] args = new Object[11];
        args[0] = device.getBuffer(vol);
        args[1] = device.getImage2D(proj1);
        args[2] = device.getImage2D(proj2);
        args[3] = eulerProj;
        args[4] = projCenter;
        args[5] = vCenter;
        args[6] = Vdim;
        args[7] = new int[]{startY};
        //args[8] = (globalDeformationInv!=null)?new float[]{(float)globalDeformationInv.getQuick(0,0),(float)globalDeformationInv.getQuick(1,1),(float)globalDeformationInv.getQuick(2,2),(float)globalDeformation.getQuick(1,0)}:new float[]{1,1,1,0};
        args[8] = new float[]{1,1,1,0};
        args[9] = new boolean[]{positivityConstraint};
        args[10]= new float[]{(float)scale};
        device.runKernel(kernelOffset + 3, args, globalWorkSize);

    }

    public void backProjectBuffer_Partial_1P(GPUDevice device, int vol, int proj, DoubleMatrix2D euler, int startY, int endY, boolean rescale, boolean positivityConstraint) {
        int YsliceSize = endY - startY;
        //final long psx = device.getImage2DWidth(proj);
        //float scale = ((float) psx) / (float) volumeWidth;
        long[] globalWorkSize = new long[3];
        globalWorkSize[0] = volumeWidth;
        globalWorkSize[1] = YsliceSize;
        globalWorkSize[2] = volumeDepth;
        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        float[] eulerProj = {(float) euler.getQuick(0, 0), (float) euler.getQuick(0, 1), (float) euler.getQuick(0, 2), (float) euler.getQuick(1, 0), (float) euler.getQuick(1, 1), (float) euler.getQuick(1, 2), 0, 0};

        //System.out.println("bp euler:"+euler);
        //float[] projCenter = {(float) ((width - 1.0) / 2.0), (float) ((height - 1.0) / 2.0), width, height};
        float[] projCenter = {(float) projectionCenters[2], (float) projectionCenters[3], device.getImage2DWidth(proj), device.getImage2DHeight(proj)};
        //System.out.println("backproject_partial_1p proj width="+projCenter[2]+", height="+projCenter[3]);
        if (!rescale) {
            projCenter[0]/=scale;
            projCenter[1]/=scale;
        }

        float[] Vdim = new float[]{volumeWidth, volumeWidth, volumeHeight, volumeWidth * YsliceSize};
//        if (!rescale) {
//            Vdim[1] = device.getImage2DWidth(proj);
//            Vdim[2] = device.getImage2DHeight(proj);
//        }
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }

        Object[] args = new Object[10];
        args[0] = device.getBuffer(vol);
        args[1] = device.getImage2D(proj);
        args[2] = eulerProj;
        args[3] = projCenter;
        args[4] = vCenter;
        args[5] = Vdim;
        args[6] = new int[]{startY};
        //args[7] = (globalDeformationInv!=null)?new float[]{(float)globalDeformationInv.getQuick(0,0),(float)globalDeformationInv.getQuick(2,2),(float)globalDeformation.getQuick(1,0),(float)globalDeformationInv.getQuick(1,1)}:new float[]{1,1,1,0};
        args[7] = new float[]{1,1,1,0};
        args[8] = new boolean[]{positivityConstraint};
        args[9]= new float[]{(float)scale};
        device.runKernel(kernelOffset + 2, args, globalWorkSize);
    }

    public void projectImage3D_Partial(GPUDevice device, int indexProj, int indexNorm, DoubleMatrix2D euler, int startY, int endY, boolean rescale) {
        //System.out.println("pcx="+pcx+"\tpcy="+pcy+"\nold: "+(float) ((width - 1.0) / 2.0)+"\t"+(float) ((height - 1.0) / 2.0));
        int YsliceSize = endY - startY;
        //System.out.println("project partial ");
        //System.out.flush();

        final long psx = device.getImage2DWidth(indexProj);
        //final float pcx = (float) ((width - 1.0) / 2.0);
        //final float pcy = (float) ((height - 1.0) / 2.0);
        float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
        //float scale=((float)psx)/(float)width;
        //System.out.println("scale="+scale);
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = psx;
        //globalWorkSize[1] = (height-device.getImage2DHeight(indexProj))/2+Yoffset;
        globalWorkSize[1] = Math.round(YsliceSize * scale);


        //System.out.println("scale="+scale+", Yworksize="+globalWorkSize[1]);
        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        final DoubleMatrix2D eulert = euler.viewDice();
        //final DoubleMatrix2D eulert = new DenseDoubleAlgebra().inverse(euler);

        final double dirx = (euler.getQuick(2, 0) == 0) ? ACCURACY : euler.getQuick(2, 0);
        final double diry = (euler.getQuick(2, 1) == 0) ? ACCURACY : euler.getQuick(2, 1);
        final double dirz = (euler.getQuick(2, 2) == 0) ? ACCURACY : euler.getQuick(2, 2);
        float[] eulerProj = {(float) eulert.getQuick(0, 0), (float) eulert.getQuick(0, 1), (float) eulert.getQuick(1, 0), (float) eulert.getQuick(1, 1), (float) eulert.getQuick(2, 0), (float) eulert.getQuick(2, 1), 0, 0};
        float[] dir = new float[]{(float) dirx, (float) diry, (float) dirz, 0};
        float[] pCenter = new float[]{(float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
        //System.out.println("project_partial ");
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};

        Object[] args = new Object[11];
        args[0] = device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D));
        args[1] = device.getImage2D(indexProj);
        args[2] = device.getImage2D(indexNorm);
        args[3] = eulerProj;
        args[4] = dir;
        args[5] = pCenter;
        args[6] = vCenter;
        args[7] = dim;
        args[8] = new int[]{startY};
        args[9] = new float[]{(rescale) ? scale : 1, rescale ? 0 : (device.getImage2DHeight(indexProj) - volumeHeight) / 2};
        //args[10] = (globalDeformation!=null)?new float[]{(float)globalDeformation.getQuick(0,0),(float)globalDeformation.getQuick(1,1),(float)globalDeformation.getQuick(2,2),(float)globalDeformation.getQuick(1,0)}:new float[]{1,1,1,0};
        args[10] = new float[]{1,1,1,0};
        device.runKernel(kernelOffset + 0, args, globalWorkSize);

    }

    public void projectBuffer_Partial(GPUDevice device, int indexProj, int indexNorm, DoubleMatrix2D euler, int startY, int endY, boolean rescale) {
            //System.out.println("pcx="+pcx+"\tpcy="+pcy+"\nold: "+(float) ((width - 1.0) / 2.0)+"\t"+(float) ((height - 1.0) / 2.0));
            int YsliceSize = endY - startY;
            //System.out.println("project partial ");
            //System.out.flush();

            final long psx = device.getImage2DWidth(indexProj);
            //final float pcx = (float) ((width - 1.0) / 2.0);
            //final float pcy = (float) ((height - 1.0) / 2.0);
            float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
            //float scale=((float)psx)/(float)width;
            //System.out.println("scale="+scale);
            long globalWorkSize[] = new long[2];
            globalWorkSize[0] = psx;
            //globalWorkSize[1] = (height-device.getImage2DHeight(indexProj))/2+Yoffset;
            globalWorkSize[1] = Math.round(YsliceSize * scale);


            //System.out.println("scale="+scale+", Yworksize="+globalWorkSize[1]);
        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
            final DoubleMatrix2D eulert = euler.viewDice();
            //final DoubleMatrix2D eulert = new DenseDoubleAlgebra().inverse(euler);

            final double dirx = (euler.getQuick(2, 0) == 0) ? ACCURACY : euler.getQuick(2, 0);
            final double diry = (euler.getQuick(2, 1) == 0) ? ACCURACY : euler.getQuick(2, 1);
            final double dirz = (euler.getQuick(2, 2) == 0) ? ACCURACY : euler.getQuick(2, 2);
            float[] eulerProj = {(float) eulert.getQuick(0, 0), (float) eulert.getQuick(0, 1), (float) eulert.getQuick(1, 0), (float) eulert.getQuick(1, 1), (float) eulert.getQuick(2, 0), (float) eulert.getQuick(2, 1), 0, 0};
            float[] dir = new float[]{(float) dirx, (float) diry, (float) dirz, 0};
            float[] pCenter = new float[]{(float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
            //System.out.println("project_partial ");
            float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};

            Object[] args = new Object[11];
            args[0] = device.getBuffer(volMemBufferIndex.get(currentWorkingBuffer));
            args[1] = device.getImage2D(indexProj);
            args[2] = device.getImage2D(indexNorm);
            args[3] = eulerProj;
            args[4] = dir;
            args[5] = pCenter;
            args[6] = vCenter;
            args[7] = dim;
            args[8] = new int[]{startY};
            args[9] = new float[]{(rescale) ? scale : 1, rescale ? 0 : (device.getImage2DHeight(indexProj) - volumeHeight) / 2};
            //args[10] = (globalDeformation!=null)?new float[]{(float)globalDeformation.getQuick(0,0),(float)globalDeformation.getQuick(1,1),(float)globalDeformation.getQuick(2,2),(float)globalDeformation.getQuick(1,0)}:new float[]{1,1,1,0};
            args[10] = new float[]{1,1,1,0};
            device.runKernel(kernelOffset + 9, args, globalWorkSize);

        }


    public GPUDevice getDevice() {
        return device;
    }

    public ImageProcessor getDiffProcessor(){
        float[] tmp = new float[ts.getWidth() * ts.getHeight()];
               ImageProcessor d=  new FloatProcessor(ts.getWidth(),ts.getHeight(),tmp);
               device.readFromImage2D(diffsIndex.get(0), tmp,ts.getWidth(),ts.getHeight());

        return d;
    }
    public ImageProcessor getProjProcessor(){
        float[] tmp = new float[ts.getWidth() * ts.getHeight()];
               ImageProcessor d=  new FloatProcessor(ts.getWidth(),ts.getHeight(),tmp);
               device.readFromImage2D(projsIndex.get(0), tmp,ts.getWidth(),ts.getHeight());

        return d;
    }
    public ImageProcessor getNormProcessor(){
        float[] tmp = new float[ts.getWidth() * ts.getHeight()];
        ImageProcessor d=  new FloatProcessor(ts.getWidth(),ts.getHeight(),tmp);
        device.readFromImage2D(normsIndex.get(0), tmp,ts.getWidth(),ts.getHeight());

        return d;
    }

    public float[] getProjection(int index){
        float[] tmp = new float[ts.getWidth() * ts.getHeight()];
        device.readFromImage2D(projsIndex.get(index), tmp,ts.getWidth(),ts.getHeight());
        return tmp;
    }

    public float[] getProjection(int ystart,int yend){
        float[] tmp=new float[(yend-ystart)*ts.getWidth()];
        device.readFromImage2D(projsIndex.get(0),tmp,ts.getWidth(),yend-ystart);
        return tmp;
    }

    public float[] getNorm(int ystart,int yend){
        float[] tmp=new float[(yend-ystart)*ts.getWidth()];
        device.readFromImage2D(normsIndex.get(0),tmp,ts.getWidth(),yend-ystart);
        return tmp;
    }

    public void projectImage3D_Partial_Deform(GPUDevice device, int indexProj, int indexNorm, DoubleMatrix2D euler, int startY, int endY, boolean rescale) {
        //System.out.println("pcx="+pcx+"\tpcy="+pcy+"\nold: "+(float) ((width - 1.0) / 2.0)+"\t"+(float) ((height - 1.0) / 2.0));
        int YsliceSize = endY - startY;
        //System.out.println("project partial deform");
        //System.out.flush();

        final long psx = device.getImage2DWidth(indexProj);
        //final float pcx = (float) ((width - 1.0) / 2.0);
        //final float pcy = (float) ((height - 1.0) / 2.0);
        float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
        //float scale=((float)psx)/(float)width;
        //System.out.println("scale="+scale);
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = psx;
        //globalWorkSize[1] = (height-device.getImage2DHeight(indexProj))/2+Yoffset;
        globalWorkSize[1] = Math.round(YsliceSize * scale);


        //System.out.println(euler);
        //System.out.println("scale="+scale+", Yworksize="+globalWorkSize[1]);
        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        float[] eulerProj = {(float) euler.getQuick(0, 0), (float) euler.getQuick(0, 1), (float) euler.getQuick(0, 2), (float) euler.getQuick(1, 0), (float) euler.getQuick(1, 1), (float) euler.getQuick(1, 2), 0, 0};

        float[] pCenter = new float[]{(float) projectionCenters[0], (float) projectionCenters[1], (float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
        //System.out.println("project_partial_diff_ali ");
        //System.out.println("pcx="+pcx+"\t pcy="+pcy);
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};

        Object[] args = new Object[9];
        args[0] = device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D));
        args[1] = device.getImage2D(indexProj);
        args[2] = device.getImage2D(indexNorm);
        args[3] = eulerProj;
        args[4] = pCenter;
        args[5] = vCenter;
        args[6] = dim;
        args[7] = new int[]{startY};
        args[8] = new float[]{(rescale) ? scale : 1, rescale ? 0 : (device.getImage2DHeight(indexProj) - volumeHeight) / 2};

        device.runKernel(kernelOffset + 12, args, globalWorkSize);

    }
    public void projectImage3DPartial_Diff_deform(GPUDevice device, int indexExpProj, int indexDiff, DoubleMatrix2D euler, int startY, int endY, float factor, float longObjectCompensation, boolean rescale) {
        if(endY>projectionHeight) endY=projectionHeight;
        int YsliceSize = endY - startY;
        final long psx = device.getImage2DWidth(indexExpProj);
        //final float pcx = (float) ((width - 1.0) / 2.0);
        //final float pcy = (float) ((height - 1.0) / 2.0);
        //float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
        float scale = (rescale) ? (float)this.scale : 1;
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = psx;
        globalWorkSize[1] = Math.min(Math.round((YsliceSize) * scale), projectionHeight);
        //int offset=(ts.getHeight()>rec.getHeight()&& scale==1)? (ts.getHeight()-rec.getHeight())/2 : 0;
        //globalWorkSize[1] =  Math.min(Math.round((endY) * scale), projectionHeight);
        globalWorkSize[1] = projectionHeight;


        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        float[] eulerProj = {(float) euler.getQuick(0, 0), (float) euler.getQuick(0, 1), (float) euler.getQuick(0, 2), (float) euler.getQuick(1, 0), (float) euler.getQuick(1, 1), (float) euler.getQuick(1, 2), 0, 0};
        float[] pCenter = new float[]{(float) projectionCenters[0], (float) projectionCenters[1], (float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
        //System.out.println("project_partial_diff_ali ");
        //System.out.println("pcx="+pcx+"\t pcy="+pcy);
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};




        Object[] args = new Object[11];
        args[0] = device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D));
        args[1] = device.getImage2D(indexExpProj);
        args[2] = device.getImage2D(indexDiff);
        args[3] = eulerProj;
        args[4] = pCenter;
        args[5] = vCenter;
        args[6] = dim;
        args[7] = new int[]{Math.round(startY*scale)};
        args[8] = new float[]{factor};
        args[9] = new float[]{longObjectCompensation};
        //args[10] = new float[]{(rescale) ? scale : 1, rescale || projectionHeight<=volumeHeight ? 0 : (device.getImage2DHeight(indexExpProj) - volumeHeight) / 2};
        args[10] = new float[]{(rescale) ? scale : 1,0};
        device.runKernel(kernelOffset + 11, args, globalWorkSize);
    }
    public void projectImage3DPartial_Diff_unalignedProjection_deform(GPUDevice device, int indexExpProj, int indexDiff, DoubleMatrix2D euler, int startY, int endY, float factor, float longObjectCompensation, AffineTransform Tinv, float[] stats, boolean rescale) {
        int YsliceSize = endY - startY;
        final long psx = device.getImage2DWidth(indexExpProj);
        //final float pcx = (float) ((width - 1.0) / 2.0);
        //final float pcy = (float) ((height - 1.0) / 2.0);
        float scale = (rescale) ? ((float) psx) / (float) volumeWidth : 1;
        long globalWorkSize[] = new long[2];
        globalWorkSize[0] = psx;
        globalWorkSize[1] = Math.round((YsliceSize) * scale);

        if(globalDeformationInv!=null) euler=euler.zMult(globalDeformationInv,null);
        float[] eulerProj = {(float) euler.getQuick(0, 0), (float) euler.getQuick(0, 1), (float) euler.getQuick(0, 2), (float) euler.getQuick(1, 0), (float) euler.getQuick(1, 1), (float) euler.getQuick(1, 2), 0, 0};
        float[] pCenter = new float[]{(float) projectionCenters[0], (float) projectionCenters[1], (float) projectionCenters[2] - 0.5f, (float) projectionCenters[3] - 0.5f};
        //System.out.println("project_partial_diff_ali ");
        //System.out.println("pcx="+pcx+"\t pcy="+pcy);
        float[] vCenter = new float[]{(float) volumeCenters[0], (float) volumeCenters[1], (float) volumeCenters[2], 0};
//        if(globalAli!=null){
//            vCenter[0]+=globalAli.getQuick(0,0);
//            vCenter[1]+=globalAli.getQuick(1,0);
//            vCenter[2]+=globalAli.getQuick(2,0);
//        }
        float[] dim = new float[]{volumeWidth, volumeHeight, volumeDepth, volumeWidth * volumeHeight * volumeDepth};

        double[] mat = new double[6];
        Tinv.getMatrix(mat);
        float[] matfTinv = new float[8];
        for (int i = 0; i < mat.length; i++) matfTinv[i] = (float) mat[i];
        matfTinv[6] = (float) projectionCenters[0];
        matfTinv[7] = (float) projectionCenters[1];


        Object[] args = new Object[13];
        args[0] = device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D));
        args[1] = device.getImage2D(indexExpProj);
        args[2] = device.getImage2D(indexDiff);
        args[3] = eulerProj;
        args[4] = pCenter;
        args[5] = vCenter;
        args[6] = dim;
        args[7] = new int[]{startY};
        args[8] = new float[]{factor};
        args[9] = new float[]{longObjectCompensation};
        args[10] = matfTinv;
        args[11] = stats;
        args[12] = new float[]{(rescale) ? scale : 1, rescale ? 0 : (device.getImage2DHeight(indexExpProj) - volumeHeight) / 2};

        device.runKernel(kernelOffset + 10, args, globalWorkSize);
    }


}
