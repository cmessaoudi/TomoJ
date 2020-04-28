package fr.curie.gpu.tomoj.tomography.projectors;

import ij.process.FloatProcessor;
import fr.curie.gpu.plugins.TotalVariationDenoising_3D;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoReconstruction2;
import fr.curie.gpu.utils.GPUDevice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by cedric on 24/09/2014.
 */
public class TVMVoxelProjector3DGPU extends VoxelProjector3DGPU {
    protected double regularizationLambda=0.0;
    protected double regularizationAlpha=1;

    double theta=25;
    double g=1;
    double dt=0.02;
    int tvmIteration=5;

    int kernelPIndex;
    int kernelUIndex;
    int indexBufferP=-1;
    int indexBufferU=-1;

    public TVMVoxelProjector3DGPU(TiltSeries ts, TomoReconstruction2 rec, GPUDevice device) {
        super(ts, rec, device, null);
        System.out.println("create TVMProjector gpu");

    }

    protected void initCL() {
        super.initCL();
        int kernelOffset2 = device.getNbKernels();
        kernelPIndex=kernelOffset2+0;
        kernelUIndex=kernelOffset2+1;
        String programSource = getSourceCode()+"\n"+ TotalVariationDenoising_3D.getSourceCode();
        //device.printDeviceInfo();
        //device.printPlatformsInfo();
        device.compileProgram(programSource, (device.getSupportImage3DWrite()) ? "-D IMAGE3D_WRITE" : null);
        device.compileKernel(kernelPIndex, "updateP");
        device.compileKernel(kernelUIndex, "updateU");
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


    public void setRegularizationLambda(double regularizationLambda) {
        System.out.println("set regularization (TVM) lambda to "+regularizationLambda);
       this.regularizationLambda = regularizationLambda;
   }

   public void setRegularizationAlpha(double regularizationAlpha) {
        System.out.println("set regularization (TVM) alpha to "+regularizationAlpha);
        this.regularizationAlpha = regularizationAlpha;
   }

    public void setTvmTheta(double theta) {
        this.theta = (float)theta;
    }

    public void setTvmDt(double dt) {
        this.dt = (float)dt;
    }

    public void setTvmG(double g) {
        this.g = (float)g;
    }

    public void setTvmIteration(int tvmIteration) {
        this.tvmIteration = tvmIteration;
    }




    public void endOfIteration() {
        device.waitWorkFinished();
        currentIteration++;
        // filter tvm
        if(indexBufferP<0) {
            float[] tmp = new float[volumeWidth*3 * Ysize * volumeDepth];
            indexBufferP=device.addBuffer(tmp, true);
            volMemBufferIndex.add(indexBufferP);
        }
        if(indexBufferU<0) {
            float[] tmp = new float[volumeWidth * Ysize * volumeDepth];
            for (int i = 0; i < rec.getImageStackSize(); i++) {
                float[] slice = (float[]) rec.getImageStack().getPixels(i + 1);
                System.arraycopy(slice, volumeWidth * currentStartY, tmp, i * volumeWidth * Ysize, volumeWidth * Ysize);
            }
            indexBufferU=device.addBuffer(tmp, true);
            volMemBufferIndex.add(indexBufferU);
        }
        System.out.println("tvm denoising: theta="+theta+", g="+g+", dt="+dt+", iterations="+tvmIteration);
        TotalVariationDenoising_3D.denoise3DGPU(device,kernelPIndex,kernelUIndex,currentWorkingBuffer,indexBufferU,indexBufferP,new int[]{volumeWidth,Ysize,volumeDepth,0},(float)theta,(float)g,(float)dt,tvmIteration);

        device.waitWorkFinished();
        device.copyBufferToImage3D(volMemBufferIndex.get(indexBufferU), volMemImage3DIndex.get(currentWorkingImage3D), volumeWidth, currentEndY - currentStartY, volumeDepth);
        device.copyImage3DToBuffer(volMemImage3DIndex.get(currentWorkingImage3D),volMemBufferIndex.get(currentWorkingBuffer),volumeWidth,Ysize,volumeDepth);
        volumeChanged=false;
        /*if (errorVolumeIndex != null && errorVolumeIndex.size() > 0) {
            //get Volume
            updateFromGPU(device, errorVolumeIndex.get(0), errorVolume, currentStartY, currentEndY, 0, 0);
            //save it
            FileSaver fs = new FileSaver(errorVolume);
            System.out.println("save error volume : "+savedir + ts.getTitle() + "errorVolume_" + currentIteration + ".tif");
            fs.saveAsTiffStack(savedir + ts.getTitle() + "errorVolume_" + currentIteration + ".tif");
            //clear it
            clearErrorVolume();
        } */
    }

    public void startOfIteration() {

    }


    public void tvm(){

        int YsliceSize = currentEndY - currentStartY;
        long globalWorkSize[] = new long[3];
        globalWorkSize[0] = volumeWidth;
        globalWorkSize[1] = YsliceSize;
        globalWorkSize[2] = volumeDepth;
        ArrayList<Object> args=new ArrayList<Object>();
        args.add(device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D)));
        args.add(device.getBuffer(volMemBufferIndex.get(currentWorkingBuffer)));
        args.add(new float[]{(float)-regularizationLambda});
        device.runKernel(kernelOffset + 8, args, globalWorkSize);
    }


    public void addImage3DtoBuffer(){
        int YsliceSize = currentEndY - currentStartY;
        long globalWorkSize[] = new long[3];
        globalWorkSize[0] = volumeWidth;
        globalWorkSize[1] = YsliceSize;
        globalWorkSize[2] = volumeDepth;
        ArrayList<Object> args=new ArrayList<Object>();
        args.add(device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D)));
        args.add(device.getBuffer(volMemBufferIndex.get(currentWorkingBuffer)));
        args.add(new float[]{(float)regularizationAlpha});
        args.add(new boolean[]{positivityConstraint});
        device.runKernel(kernelOffset + 9, args, globalWorkSize);
    }

    public GPUDevice getDevice() {
        return device;
    }
}
