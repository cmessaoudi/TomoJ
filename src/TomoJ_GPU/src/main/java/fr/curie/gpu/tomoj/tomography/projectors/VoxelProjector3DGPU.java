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
import java.util.concurrent.ExecutorService;

import static org.jocl.CL.CL_FLOAT;
import static org.jocl.CL.CL_R;

/**
 * Created by cedric on 24/09/2014.
 */
public class VoxelProjector3DGPU extends ProjectorGPU {

    public VoxelProjector3DGPU(TiltSeries ts, TomoReconstruction2 rec, GPUDevice device, FFTWeightingGPU weightingFilter) {
        super(ts, rec, device, weightingFilter);
    }


    public void backproject(int startY, int endY) {
        //System.out.println("currentProjIndex:" + currentProjIndex + " , currentOri:" + currentOriProjIndex);
        final ArrayList<Integer> usedProjs = (currentProjIndex < 0) ? oriProjsIndex : diffsIndex;
        //System.out.println("volMemBuffer index:"+volMemBufferIndex.get(0));
        //System.out.println("using originals:" + (usedProjs == oriProjsIndex));
        for (int p = 0; p <= currentOriProjIndex; p++) {
            if (weightingFilter != null) {
                //System.out.println("weighting");
                ((FFTWeightingGPU) weightingFilter).weighting(usedProjs.get(p));
            }
        }
        device.waitWorkFinished();
        for (int p = 0; p <= currentOriProjIndex; p++) {
            if (p + 3 <= currentOriProjIndex) {
                backProjectBuffer_Partial_4P(device, volMemBufferIndex.get(currentWorkingBuffer), usedProjs.get(p), eulers.get(p), usedProjs.get(p + 1), eulers.get(p + 1), usedProjs.get(p + 2), eulers.get(p + 2), usedProjs.get(p + 3), eulers.get(p + 3), startY, endY, scale != 1, positivityConstraint);
                device.waitWorkFinished();
                if (errorVolumeIndex != null) {
                    computeErrorImage(usedProjs.get(p), oriProjsIndex.get(p));
                    computeErrorImage(usedProjs.get(p + 1), oriProjsIndex.get(p + 1));
                    computeErrorImage(usedProjs.get(p + 2), oriProjsIndex.get(p + 2));
                    computeErrorImage(usedProjs.get(p + 3), oriProjsIndex.get(p + 3));
                    device.waitWorkFinished();
                    backProjectBuffer_Partial_4P(device, errorVolumeIndex.get(0), oriProjsIndex.get(p), eulers.get(p), oriProjsIndex.get(p + 1), eulers.get(p + 1), oriProjsIndex.get(p + 2), eulers.get(p + 2), oriProjsIndex.get(p + 3), eulers.get(p + 3), startY, endY, scale != 1, positivityConstraint);
                    device.waitWorkFinished();
                }
                p += 3;
            } else if (p + 1 <= currentOriProjIndex) {
                backProjectBuffer_Partial_2P(device, volMemBufferIndex.get(currentWorkingBuffer), usedProjs.get(p), eulers.get(p), usedProjs.get(p + 1), eulers.get(p + 1), startY, endY, scale != 1, positivityConstraint);
                device.waitWorkFinished();
                if (errorVolumeIndex != null) {
                    computeErrorImage(usedProjs.get(p), oriProjsIndex.get(p));
                    computeErrorImage(usedProjs.get(p + 1), oriProjsIndex.get(p + 1));
                    device.waitWorkFinished();
                    backProjectBuffer_Partial_2P(device, errorVolumeIndex.get(0), oriProjsIndex.get(p), eulers.get(p), oriProjsIndex.get(p + 1), eulers.get(p + 1), startY, endY, scale != 1, positivityConstraint);
                    device.waitWorkFinished();
                }
                p++;
            } else {
                backProjectBuffer_Partial_1P(device, volMemBufferIndex.get(currentWorkingBuffer), usedProjs.get(p), eulers.get(p), startY, endY, scale != 1, positivityConstraint);
                device.waitWorkFinished();
                if (errorVolumeIndex != null) {
                    computeErrorImage(usedProjs.get(p), oriProjsIndex.get(p));
                    device.waitWorkFinished();
                    backProjectBuffer_Partial_1P(device, errorVolumeIndex.get(0), oriProjsIndex.get(p), eulers.get(p), startY, endY, scale != 1, positivityConstraint);
                    device.waitWorkFinished();
                }

            }
        }
        volumeChanged = true;

    }

    public void project(int startY, int endY) {
        if (volumeChanged && useImage3D) {
            device.copyBufferToImage3D(volMemBufferIndex.get(0), volMemImage3DIndex.get(0), volumeWidth, endY - startY, volumeDepth);
            volumeChanged = false;
        }
        for (int p = 0; p <= currentOriProjIndex; p++) {
            currentProjIndex++;
            if (useImage3D) {
                if (globalDeformationInv != null)
                    projectImage3D_Partial_Deform(device, projsIndex.get(p), normsIndex.get(p), eulers.get(p), startY, endY, scale != 1);
                else
                    projectImage3D_Partial(device, projsIndex.get(p), normsIndex.get(p), eulers.get(p), startY, endY, scale != 1);
            } else
                projectBuffer_Partial(device, projsIndex.get(p), normsIndex.get(p), eulers.get(p), startY, endY, scale != 1);
        }
        device.waitWorkFinished();
    }

    public double[] projectAndDifference(double factor, int startY, int endY) {
        /*switch (kernelusage){
            case SEPARATE_PROJ_DIFF:
                System.out.println("kernel used separate proj diff");
                break;
            case ONE_KERNEL_PROJ_DIFF:
                System.out.println("kernel used one kernel proj diff");
                break;
            case ONE_KERNEL_PROJ_DIFF_WITH_ALIGNMENT:
                System.out.println("kernel used one kernel proj diff with ali");
                break;

        }*/
        double[] errors = new double[eulers.size()];
        if (useImage3D) {
            if (volumeChanged) {
                device.copyBufferToImage3D(volMemBufferIndex.get(currentWorkingBuffer), volMemImage3DIndex.get(currentWorkingImage3D), volumeWidth, endY - startY, volumeDepth);
                volumeChanged = false;
            }
            switch (kernelusage) {
                case SEPARATE_PROJ_DIFF:
                    project(startY, endY);
                    for (int p = 0; p <= currentOriProjIndex; p++) {
                        differenceOnGPU(device, oriProjsIndex.get(p), projsIndex.get(p), normsIndex.get(p), diffsIndex.get(p), (float) factor, longObjComp.get(p));
                        device.waitWorkFinished();
                    }
                    break;
                case ONE_KERNEL_PROJ_DIFF:
                    for (int p = 0; p <= currentOriProjIndex; p++) {
                        currentProjIndex++;
                        /*if(globalDeformationInv==null) projectImage3DPartial_Diff(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) factor, longObjComp.get(p), scale != 1);
                        else */
                            projectImage3DPartial_Diff_deform(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) factor, longObjComp.get(p), scale != 1);
                        try{
                            device.waitWorkFinished();
                        }catch (Exception e){
                            System.out.println("globaldeformationInv:"+globalDeformationInv);
                            e.printStackTrace();
                        }
                    }
                    break;
                case ONE_KERNEL_PROJ_DIFF_WITH_ALIGNMENT:
                default:
                    for (int p = 0; p <= currentOriProjIndex; p++) {
                        currentProjIndex++;
//                        projectImage3DPartial_Diff_unalignedProjection(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) factor, longObjComp.get(p), Tinvs.get(p), stats.get(p), scale != 1);
                        if (globalDeformationInv == null)
                            projectImage3DPartial_Diff_unalignedProjection(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) factor, longObjComp.get(p), Tinvs.get(p), stats.get(p), scale != 1);
                        else
                            projectImage3DPartial_Diff_unalignedProjection_deform(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) factor, longObjComp.get(p), Tinvs.get(p), stats.get(p), scale != 1);
                        device.waitWorkFinished();
                    }
            }
        } else {
            switch (kernelusage) {
                case SEPARATE_PROJ_DIFF:
                    project(startY, endY);
                    for (int p = 0; p <= currentOriProjIndex; p++) {
                        differenceOnGPU(device, oriProjsIndex.get(p), projsIndex.get(p), normsIndex.get(p), diffsIndex.get(p), (float) factor, longObjComp.get(p));
                        device.waitWorkFinished();
                    }

                    break;
                case ONE_KERNEL_PROJ_DIFF:
                case ONE_KERNEL_PROJ_DIFF_WITH_ALIGNMENT:
                    for (int p = 0; p <= currentOriProjIndex; p++) {
                        currentProjIndex++;
                        projectBufferPartial_Diff_unalignedProjection(device, oriProjsIndex.get(p), diffsIndex.get(p), eulers.get(p), startY, endY, (float) factor, longObjComp.get(p), Tinvs.get(p), stats.get(p), scale != 1);
                        device.waitWorkFinished();
                    }

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
        device.waitWorkFinished();
        currentIteration++;
        if (errorVolumeIndex != null && errorVolumeIndex.size() > 0) {
            //get Volume
            updateFromGPUBuffer(device, errorVolumeIndex.get(0), errorVolume, currentStartY, currentEndY, 0, 0);
            //save it
            FileSaver fs = new FileSaver(errorVolume);
            System.out.println("save error volume : " + savedir + ts.getTitle() + "errorVolume_" + currentIteration + ".tif");
            fs.saveAsTiffStack(savedir + ts.getTitle() + "errorVolume_" + currentIteration + ".tif");
            //clear it
            clearErrorVolume();
        }
    }

    public void startOfIteration() {
    }

}
