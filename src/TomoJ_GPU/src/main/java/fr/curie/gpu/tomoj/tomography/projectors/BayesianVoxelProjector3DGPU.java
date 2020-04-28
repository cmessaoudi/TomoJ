package fr.curie.gpu.tomoj.tomography.projectors;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
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
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import static org.jocl.CL.CL_FLOAT;
import static org.jocl.CL.CL_R;

/**
 * Created by cedric on 24/09/2014.
 */
public class BayesianVoxelProjector3DGPU extends ProjectorGPU {
    protected double regularizationLambda=0.0;
    protected double regularizationAlpha=1;
    int convolutionKernelIndex;

    float[] convolutionKernel={ 1,-2,1,-2,4,-2,1,-2,1,
                                    -2,4,-2,4,-8,4,-2,4,-2,
                                    1,-2,1,-2,4,-2,1,-2,1};

    public BayesianVoxelProjector3DGPU(TiltSeries ts, TomoReconstruction2 rec, GPUDevice device) {
        super(ts, rec, device, null);
        convolutionKernelIndex=device.addBuffer(convolutionKernel,false);

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
        device.compileKernel(kernelOffset + 8, "convolve");
        device.compileKernel(kernelOffset + 9, "addImage3DtoBuffer");
        device.compileKernel(kernelOffset + 10, "convolveAdd");
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
       this.regularizationLambda = regularizationLambda;
   }

   public void setRegularizationAlpha(double regularizationAlpha) {
       this.regularizationAlpha = regularizationAlpha;
   }


    public void backproject(int startY, int endY) {
        //System.out.println("currentProjIndex:" + currentProjIndex + " , currentOri:" + currentOriProjIndex);
        final ArrayList<Integer> usedProjs = (currentProjIndex < 0) ? oriProjsIndex : diffsIndex;
        //System.out.println("volMemBuffer index:"+volMemBufferIndex.get(0));
        //System.out.println("using originals:" + (usedProjs == oriProjsIndex));
        for (int p = 0; p <= currentOriProjIndex; p++) {
           /* if (weightingFilter != null) {
                //System.out.println("weighting");
                ((FFTWeightingGPU) weightingFilter).weighting(usedProjs.get(p));
            }  */
        }
        device.waitWorkFinished();
        for (int p = 0; p <= currentOriProjIndex; p++) {
            if (p + 3 <= currentOriProjIndex) {
                //System.out.println("backproject 4P:"+p);
                //System.out.flush();
                backProjectBuffer_Partial_4P(device, volMemBufferIndex.get(currentWorkingBuffer), usedProjs.get(p), eulers.get(p), usedProjs.get(p + 1), eulers.get(p + 1), usedProjs.get(p + 2), eulers.get(p + 2), usedProjs.get(p + 3), eulers.get(p + 3), startY, endY, scale != 1,positivityConstraint);
                device.waitWorkFinished();
                /*if (errorVolumeIndex != null) {
                    computeErrorImage(usedProjs.get(p), oriProjsIndex.get(p));
                    computeErrorImage(usedProjs.get(p + 1), oriProjsIndex.get(p + 1));
                    computeErrorImage(usedProjs.get(p + 2), oriProjsIndex.get(p + 2));
                    computeErrorImage(usedProjs.get(p + 3), oriProjsIndex.get(p + 3));
                    device.waitWorkFinished();
                    backProjectBuffer_Partial_4P(device, errorVolumeIndex.get(0), oriProjsIndex.get(p), eulers.get(p), oriProjsIndex.get(p + 1), eulers.get(p + 1), oriProjsIndex.get(p + 2), eulers.get(p + 2), oriProjsIndex.get(p + 3), eulers.get(p + 3), startY, endY, scale != 1);
                    device.waitWorkFinished();
                } */
                p += 3;
            } else if (p + 1 <= currentOriProjIndex) {
               // System.out.println("backproject 2P:"+p);
                //System.out.flush();
                backProjectBuffer_Partial_2P(device, volMemBufferIndex.get(currentWorkingBuffer), usedProjs.get(p), eulers.get(p), usedProjs.get(p + 1), eulers.get(p + 1), startY, endY, scale != 1,positivityConstraint);
                device.waitWorkFinished();
                /*if (errorVolumeIndex != null) {
                    computeErrorImage(usedProjs.get(p), oriProjsIndex.get(p));
                    computeErrorImage(usedProjs.get(p + 1), oriProjsIndex.get(p + 1));
                    device.waitWorkFinished();
                    backProjectBuffer_Partial_2P(device, errorVolumeIndex.get(0), oriProjsIndex.get(p), eulers.get(p), oriProjsIndex.get(p + 1), eulers.get(p + 1), startY, endY, scale != 1);
                    device.waitWorkFinished();
                }   */
                p++;
            } else {
               // System.out.println("backproject 1P:"+p);
                //System.out.flush();
                backProjectBuffer_Partial_1P(device, volMemBufferIndex.get(currentWorkingBuffer), usedProjs.get(p), eulers.get(p), startY, endY, scale != 1,positivityConstraint);
                device.waitWorkFinished();
                /*if (errorVolumeIndex != null) {
                    computeErrorImage(usedProjs.get(p), oriProjsIndex.get(p));
                    device.waitWorkFinished();
                    backProjectBuffer_Partial_1P(device, errorVolumeIndex.get(0), oriProjsIndex.get(p), eulers.get(p), startY, endY, scale != 1);
                    device.waitWorkFinished();
                } */

            }
        }
        //volumeChanged = true;

    }

    public void project(int startY, int endY) {
//        if (volumeChanged) {
//            device.copyBufferToImage3D(volMemBufferIndex.get(0), volMemImage3DIndex.get(0), volumeWidth, endY - startY, volumeDepth);
//            volumeChanged = false;
//        }
        /*for (int p = 0; p <= currentOriProjIndex; p++) {
            currentProjIndex++;
            projectImage3D_Partial(device, projsIndex.get(p), normsIndex.get(p), eulers.get(p), startY, endY, scale != 1);
        }
        device.waitWorkFinished();  */
    }

    public double[] projectAndDifference(double factor, int startY, int endY) {

        double[] errors = new double[eulers.size()];
        /*if (volumeChanged) {
            device.copyBufferToImage3D(volMemBufferIndex.get(currentWorkingBuffer), volMemImage3DIndex.get(currentWorkingImage3D), volumeWidth, endY - startY, volumeDepth);
            volumeChanged = false;
        }  */
        switch (kernelusage) {
            case SEPARATE_PROJ_DIFF:
                project(startY, endY);
                for (int p = 0; p <= currentOriProjIndex; p++) {
                    differenceOnGPU(device, oriProjsIndex.get(p), projsIndex.get(p), normsIndex.get(p), diffsIndex.get(p), (float) (factor*regularizationAlpha), longObjComp.get(p));
                    device.waitWorkFinished();
                }
                break;
            case ONE_KERNEL_PROJ_DIFF:
                for (int p = 0; p <= currentOriProjIndex; p++) {
                    currentProjIndex++;
                    projectImage3DPartial_Diff(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) (factor*regularizationAlpha), longObjComp.get(p), scale != 1);
                    device.waitWorkFinished();
                }
                break;
            case ONE_KERNEL_PROJ_DIFF_WITH_ALIGNMENT:
            default:
                for (int p = 0; p <= currentOriProjIndex; p++) {
                    currentProjIndex++;
                    //System.out.println("projectdiff:"+p+" : "+currentProjIndex);
                    projectImage3DPartial_Diff_unalignedProjection(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) (factor*regularizationAlpha), longObjComp.get(p), Tinvs.get(p), stats.get(p), scale != 1);
                    device.waitWorkFinished();
                }
        }


        for (int p = 0; p <= currentOriProjIndex; p++) {
            FloatProcessor diff = new FloatProcessor(projectionWidth, projectionHeight);
            device.readFromImage2D(diffsIndex.get(p), (float[]) diff.getPixels(), projectionWidth, projectionHeight);
            errors[p] = computeError((float[]) diff.getPixels()) / (factor * factor);
        }

        return errors;

    }

    public void endOfIteration() {
        //System.out.println("end:"+positivityConstraint);
        device.waitWorkFinished();
        currentIteration++;
        System.out.println("add");
        System.out.flush();
        addImage3DtoBuffer();
        device.waitWorkFinished();
        System.out.println("copy back in image3D: width="+volumeWidth+" height="+volumeHeight+" depth:"+volumeDepth+" actual working Ysize="+(currentEndY-currentStartY));
        System.out.flush();
        device.copyBufferToImage3D(volMemBufferIndex.get(currentWorkingBuffer), volMemImage3DIndex.get(currentWorkingImage3D), volumeWidth, currentEndY - currentStartY, volumeDepth);
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
        System.out.println("convolve");
        System.out.flush();
        device.fillBuffer(currentWorkingBuffer,0);
        device.waitWorkFinished();
        //addImage3DtoBuffer();
        //device.copyImage3DToBuffer(volMemImage3DIndex.get(currentWorkingImage3D),volMemBufferIndex.get(currentWorkingBuffer),volumeWidth,currentEndY-currentStartY,volumeDepth);
        convolve();
        //convolveAdd();
        device.waitWorkFinished();
        //System.out.println("start:"+positivityConstraint);
    }


    public void convolve(){
        device.writeBuffer(convolutionKernelIndex,convolutionKernel);
        /*for(float f:convolutionKernel) System.out.println(f);
        device.readFromBuffer(convolutionKernelIndex,convolutionKernel);
        for(float f:convolutionKernel) System.out.println(f); */

        int YsliceSize = currentEndY - currentStartY;
        long globalWorkSize[] = new long[3];
        globalWorkSize[0] = volumeWidth;
        globalWorkSize[1] = YsliceSize;
        globalWorkSize[2] = volumeDepth;
        ArrayList<Object> args=new ArrayList<Object>();
        args.add(device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D)));
        args.add(device.getBuffer(convolutionKernelIndex));
        args.add(device.getBuffer(volMemBufferIndex.get(currentWorkingBuffer)));
        args.add(new float[]{(float)-regularizationLambda});
        device.runKernel(kernelOffset + 8, args, globalWorkSize);
    }
    public void convolveAdd(){
        device.writeBuffer(convolutionKernelIndex,convolutionKernel);
        /*for(float f:convolutionKernel) System.out.println(f);
        device.readFromBuffer(convolutionKernelIndex,convolutionKernel);
        for(float f:convolutionKernel) System.out.println(f);    */
       int YsliceSize = currentEndY - currentStartY;
       long globalWorkSize[] = new long[3];
       globalWorkSize[0] = volumeWidth;
       globalWorkSize[1] = YsliceSize;
       globalWorkSize[2] = volumeDepth;
       ArrayList<Object> args=new ArrayList<Object>();
       args.add(device.getImage3D(volMemImage3DIndex.get(currentWorkingImage3D)));
       args.add(device.getBuffer(convolutionKernelIndex));
       args.add(device.getBuffer(volMemBufferIndex.get(currentWorkingBuffer)));
       args.add(new float[]{(float)-regularizationLambda});
        args.add(new float[]{(float)regularizationAlpha});
       device.runKernel(kernelOffset + 10, args, globalWorkSize);
   }

    public void addImage3DtoBuffer(){

        System.out.println("addimage3Dbuffer:"+positivityConstraint);
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
