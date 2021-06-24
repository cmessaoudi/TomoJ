package fr.curie.gpu.utils;

import ij.ImagePlus;
import ij.ImageStack;
import org.jocl.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import static org.jocl.CL.*;

/**
 * Created with IntelliJ IDEA.
 * User: C�dric
 * Date: 17/04/13
 * Time: 08:52
 * To change this template use File | Settings | File Templates.
 */
public class GPUDevice {
    static protected GPUDevice[] DEVICES=null;
    cl_platform_id platform;
    cl_device_id device;
    cl_context context = null;
    cl_command_queue commandQueue = null;
    cl_program program;
    ArrayList<cl_mem> buffers;
    ArrayList<cl_mem> image2Ds;
    ArrayList<cl_mem> image3Ds;
    ArrayList<cl_kernel> kernels;
    long optimumMemoryUse = 0;
    boolean supportImage3DWrite = false;


    private GPUDevice(cl_platform_id platform, cl_device_id device, String sourceCode) {
        this.platform = platform;
        this.device = device;
        compileProgram(sourceCode, null);
    }

    public void compileProgram(String sourceCode, String options) {
        System.out.println("creating context for device " + JOCLDeviceInfo.getName(device));
        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
        // Create a context for the selected device
        if (context == null)
            context = clCreateContext(contextProperties, 1, new cl_device_id[]{device}, null, null, null);

        // Create a command-queue
        System.out.println("Creating command queue...");
        long properties = 0;
        //properties |= CL_QUEUE_PROFILING_ENABLE;
        //properties |= CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        if (commandQueue == null) commandQueue = clCreateCommandQueue(context, device, properties, null);
        // Implementation for OpenCL 2.0 and above
//        if (commandQueue == null) commandQueue = clCreateCommandQueueWithProperties(context, device, new org.jocl.cl_queue_properties(), null);

        program = clCreateProgramWithSource(context,
                1, new String[]{sourceCode}, null, null);

        // Build the program
        System.out.println("Building program...");
        //clBuildProgram(program, 0, null, null, null, null);
        clBuildProgram(program, 0, null, options, null, null);

       /* // Create the kernels
        System.out.println("Creating kernels...");
        int numberReallyCompiled[] = new int[1];
        kernels = new cl_kernel[nbKernels];
        int ciErrNum = clCreateKernelsInProgram(program, nbKernels, kernels, numberReallyCompiled);
        System.out.println("device " + JOCLDeviceInfo.getName(device) + " " + numberReallyCompiled[0] + " kernels compiled.");
        if (ciErrNum != CL_SUCCESS)
            System.out.println("error in compiling kernels on device " + JOCLDeviceInfo.getName(device));
        int[] err=new int[1];
        kernelProjection = clCreateKernel(program, "projectImage3D_Partial", err);
                if (err[0] != CL_SUCCESS) System.out.println("error in compiling kernel projectImage3D");*/
    }

    private GPUDevice(cl_platform_id platform, cl_device_id device) {
        this.platform = platform;
        this.device = device;
    }

    public static GPUDevice[] getGPUDevices(String sourceCode) {
        GPUDevice[] dev = getGPUDevices();
        for (GPUDevice d : dev) {
            d.compileProgram(sourceCode, null);
        }
        return dev;
    }

    public static GPUDevice[] getGPUDevices() {
        if (DEVICES != null) return DEVICES;
        try {
            ArrayList<GPUDevice> devices = new ArrayList<GPUDevice>();
            int platformIndex = 0;
            final long deviceType = CL_DEVICE_TYPE_ALL;

            // Enable exceptions and subsequently omit error checks in this sample
            try {
                CL.setExceptionsEnabled(true);
            }catch (Exception e){
                System.out.println("CL init does not work!!!!!!!!");
                e.printStackTrace();
                return null;
            }

            // Obtain the number of platforms
            int[] numPlatformsArray = new int[1];
            clGetPlatformIDs(0, null, numPlatformsArray);
            int numPlatforms = numPlatformsArray[0];
            System.out.println("there are " + numPlatforms + " platforms");

            // Obtain a platform ID
            cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
            clGetPlatformIDs(platforms.length, platforms, null);


            int numDevices = 0;
            // Obtain the number of devices for the platform
            int[] numDevicesArray = new int[1];
            int maxNbDevices = 0;
            for (int p = 0; p < numPlatforms; p++) {
                try {
                    clGetDeviceIDs(platforms[p], deviceType, 0, null, numDevicesArray);
                    numDevices += numDevicesArray[0];
                    System.out.println("there are " + numDevicesArray[0] + " devices on platform " + p);
                    // Obtain a device ID
                    cl_device_id[] devicestmp = new cl_device_id[numDevices];
                    clGetDeviceIDs(platforms[p], deviceType, numDevices, devicestmp, null);
                    for (int d = 0; d < numDevicesArray[0]; d++) {
                        // Check if images are supported
                        int[] imageSupport = new int[1];
                        clGetDeviceInfo(devicestmp[d], CL.CL_DEVICE_IMAGE_SUPPORT,
                                Sizeof.cl_int, Pointer.to(imageSupport), null);

                        System.out.println(JOCLDeviceInfo.getName(devicestmp[d]) + " image supported:" + imageSupport[0]);

                        //if (imageSupport[0] != 0 && !JOCLDeviceInfo.getName(devicestmp[d]).toLowerCase().startsWith("intel")) {
                        if (imageSupport[0] != 0) {
                            System.out.println("adding device " + JOCLDeviceInfo.getName(devicestmp[d]));
                            GPUDevice tmp = new GPUDevice(platforms[p], devicestmp[d]);
                            devices.add(tmp);
                            String support3d = JOCLDeviceInfo.getString(devicestmp[d], CL.CL_DEVICE_EXTENSIONS);


                            tmp.supportImage3DWrite = support3d.contains("cl_khr_3d_image_writes");
                            System.out.println("\nextensions: " + support3d);
                            System.out.println("image3D write enable : " + tmp.getSupportImage3DWrite());
                            System.out.println("image3D write support disabled in current version");
                            tmp.supportImage3DWrite = false;
                        }

                    }

                } catch (CLException e) {
                    System.out.println("platform " + p + " : " + e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            GPUDevice[] tmp = new GPUDevice[devices.size()];
            DEVICES = devices.toArray(tmp);
            return DEVICES;
        } catch (Exception e){
            System.out.println("no OpenCL detected"+e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean getSupportImage3DWrite() {
        return supportImage3DWrite;
    }

    public static GPUDevice getBestDevice() {
        GPUDevice[] devices = getGPUDevices();
        if (devices == null || devices.length == 0) return null;
        long maxmem = 0;
        GPUDevice bestDevice = devices[0];
        for (GPUDevice d : devices) {
            long m = JOCLDeviceInfo.getGlobalMemorySize(d.device);
            if (m > maxmem) {
                maxmem = m;
                bestDevice = d;
            }
        }
        return bestDevice;
    }

    public cl_kernel compileKernel(int index, String kernelName) {
        System.out.println("compiling " + kernelName);
        if (kernels == null) kernels = new ArrayList<cl_kernel>();
        while (kernels.size() <= index) kernels.add(new cl_kernel());
        int[] err = new int[1];
        kernels.set(index, clCreateKernel(program, kernelName, err));
        if (err[0] != CL_SUCCESS) System.out.println("error in compiling kernel projectImage3D");
        return kernels.get(index);
    }

    public cl_kernel getKernel(int index) {
        return kernels.get(index);
        //return kernels[index];
    }

    public int getNbKernels() {
        return (kernels != null) ? kernels.size() : 0;
    }

    public void printDeviceInfo() {
        JOCLDeviceInfo.printInfo(device);
    }

    public void printPlatformsInfo() {
        JOCLDeviceInfo.printPlatformInfo(device);
    }

    public String getDeviceName() {
        return JOCLDeviceInfo.getVendorName(device).trim()+" / "+JOCLDeviceInfo.getName(device).trim() + " " + JOCLDeviceInfo.getClockFrequency(device) + " MHz";
    }

    public long getDeviceGlobalMemory() {
        return JOCLDeviceInfo.getGlobalMemorySize(device);
    }

    public long getDeviceMaxAllocationMemory() {
        return JOCLDeviceInfo.getMaxMemoryAllocationSize(device);
    }

    public long getOptimumMemoryUse() {
        return optimumMemoryUse;
    }

    public void setOptimumMemoryUse(long value) {
        optimumMemoryUse = value;
    }

    public long getDeviceMaxWidthImage3D() {
        return JOCLDeviceInfo.getMaxWidthImage3D(device);
    }

    public void runKernel(cl_kernel kernel, Object[] args, long[] globalWorkSize) {
        int[] ind = new int[args.length];
        for (int a = 0; a < args.length; a++) {
            ind[a] = setKernelArg(kernel, args[a], a);
        }
        clEnqueueNDRangeKernel(commandQueue, kernel, globalWorkSize.length, null, globalWorkSize, null, 0, null, null);
        for (int value : ind) {
            if (value >= 0) removeBuffer(value);
        }
    }

    private int setKernelArg(cl_kernel kernel, Object arg, int argIndex) {
        if (arg instanceof cl_mem) {
            clSetKernelArg(kernel, argIndex, Sizeof.cl_mem, Pointer.to((cl_mem) arg));
        } else if (arg instanceof float[]) {
            if (((float[]) arg).length <= 16)
                clSetKernelArg(kernel, argIndex, Sizeof.cl_float * ((float[]) arg).length, Pointer.to((float[]) arg));
            else {
                int indexBuffer = addBuffer(((float[]) arg), true);
                clSetKernelArg(kernel, argIndex, Sizeof.cl_mem, Pointer.to(getBuffer(indexBuffer)));
                return indexBuffer;
            }
        } else if (arg instanceof int[]) {
            clSetKernelArg(kernel, argIndex, Sizeof.cl_int * ((int[]) arg).length, Pointer.to((int[]) arg));
        } else if (arg instanceof long[]) {
            clSetKernelArg(kernel, argIndex, Sizeof.cl_long * ((long[]) arg).length, Pointer.to((long[]) arg));
        } else if (arg instanceof char[]) {
            clSetKernelArg(kernel, argIndex, Sizeof.cl_char * ((char[]) arg).length, Pointer.to((char[]) arg));
        } else if (arg instanceof boolean[]) {
            short[] tmp = new short[((boolean[]) arg).length];
            for (int i = 0; i < tmp.length; i++) tmp[i] = (((boolean[]) arg)[i]) ? (short) 1 : (short) 0;
            clSetKernelArg(kernel, argIndex, Sizeof.cl_short * ((boolean[]) arg).length, Pointer.to((tmp)));
        }
        return -1;
    }

    public void removeBuffer(int index) {
        if (buffers != null && index < buffers.size()) {
            cl_mem tmp = buffers.remove(index);
            releaseCL(tmp);
        }
    }

    public int addBuffer(float[] array, boolean allowWriting) {
        if (buffers == null) buffers = new ArrayList<cl_mem>();
        buffers.add(clCreateBuffer(context, (allowWriting ? CL_MEM_READ_WRITE : CL_MEM_READ_ONLY) | CL_MEM_COPY_HOST_PTR, array.length * Sizeof.cl_float, Pointer.to(array), null));
        return buffers.size() - 1;
    }

    public cl_mem getBuffer(int index) {
        return buffers.get(index);
    }

    public int getNbBuffers(){ return buffers.size();}

    public void releaseCL(cl_mem objectToRelease) {
        clReleaseMemObject(objectToRelease);
    }

    public void runKernel(int kernelIndex, Object[] args, long[] globalWorkSize) {
        int[] ind = new int[args.length];
        for (int a = 0; a < args.length; a++) {
            //System.out.println("arg "+a+" : "+args[a]);
            ind[a] = setKernelArg(kernels.get(kernelIndex), args[a], a);
        }
        clEnqueueNDRangeKernel(commandQueue, kernels.get(kernelIndex), globalWorkSize.length, null, globalWorkSize, null, 0, null, null);
        for (int value : ind) {
            if (value >= 0) removeBuffer(value);
        }
    }

    public void runKernel(int kernelIndex, ArrayList<Object> args, long[] globalWorkSize) {
        int[] ind = new int[args.size()];
        for (int a = 0; a < args.size(); a++) {
            //System.out.println("arg "+args.get(a));
            ind[a] = setKernelArg(kernels.get(kernelIndex), args.get(a), a);
        }
        clEnqueueNDRangeKernel(commandQueue, kernels.get(kernelIndex), globalWorkSize.length, null, globalWorkSize, null, 0, null, null);
        for (int value : ind) {
            if (value >= 0) removeBuffer(value);
        }
    }

    public void runKernel(int kernelIndex, long[] globalWorkSize) {
        clEnqueueNDRangeKernel(commandQueue, kernels.get(kernelIndex), globalWorkSize.length, null, globalWorkSize, null, 0, null, null);
    }

    public void waitWorkFinished() {
        clFinish(commandQueue);
    }

    /**
     * release every things in openCL even device, kernels...
     */
    public void releaseCL_All() {
        System.out.println("release GPU memory");
        if (buffers != null) for (cl_mem m : buffers) releaseCL(m);
        if (image2Ds != null) for (cl_mem m : image2Ds) releaseCL(m);
        if (image3Ds != null) for (cl_mem m : image3Ds) releaseCL(m);
        if (commandQueue != null) clReleaseCommandQueue(commandQueue);
        if (program != null) clReleaseProgram(program);
        if (context != null) clReleaseContext(context);
        if (device != null) clReleaseDevice(device);
    }

    public void releaseCL_Memory() {
        if (buffers != null) for (cl_mem m : buffers) releaseCL(m);
        if (image2Ds != null) for (cl_mem m : image2Ds) releaseCL(m);
        if (image3Ds != null) for (cl_mem m : image3Ds) releaseCL(m);
    }

    public void releaseCL_Kernels(){
        if (kernels != null) for (cl_kernel k : kernels) clReleaseKernel(k);
    }

    public int addBuffer(int[] array, boolean allowWriting) {
        if (buffers == null) buffers = new ArrayList<cl_mem>();
        buffers.add(clCreateBuffer(context, (allowWriting ? CL_MEM_READ_WRITE : CL_MEM_READ_ONLY) | CL_MEM_COPY_HOST_PTR, array.length * Sizeof.cl_int, Pointer.to(array), null));
        return buffers.size() - 1;
    }

    public void removeAllBuffers() {
        while (buffers != null && buffers.size() > 0) {
            removeBuffer(0);
        }
    }

    public void readFromBuffer(int index, float[] data) {
        clEnqueueReadBuffer(commandQueue, buffers.get(index), true, 0, data.length * Sizeof.cl_float, Pointer.to(data), 0, null, null);
    }

    public void writeBuffer(int index, float[] data) {
        clEnqueueWriteBuffer(commandQueue, buffers.get(index), true, 0, data.length * Sizeof.cl_float, Pointer.to(data), 0, null, null);
    }

    /**
     * copy cl buffer into imageStack (have to be of FloatProcessor)
     */
    public  void updateImageStackFromGPUBuffer( int volIndex, ImageStack volToBeUpdated, int startY, int endY, int YoffsetStart, int YoffsetEnd) {
        int YSliceSize = endY - startY;
        float[] tmp = new float[volToBeUpdated.getWidth() * YSliceSize * volToBeUpdated.getSize()];
        ImageStack data = volToBeUpdated;
        readFromBuffer(volIndex, tmp);
        for (int i = 0; i < volToBeUpdated.getSize(); i++) {
            float[] slice = (float[]) data.getPixels(i + 1);
            System.arraycopy(tmp, i * volToBeUpdated.getWidth() * YSliceSize + YoffsetStart * volToBeUpdated.getWidth(), slice, startY * volToBeUpdated.getWidth() + YoffsetStart * volToBeUpdated.getWidth(), volToBeUpdated.getWidth() * (YSliceSize - YoffsetEnd - YoffsetStart));

        }
    }

    /**
     * copy imageStack (have to be of FloatProcessor) into cl buffer
     */
    public  void copyImageStackInGPUBuffer( int volIndex, ImageStack updatingVolume, int startY, int endY){
        int YSliceSize = endY - startY;
        float[] tmp = new float[updatingVolume.getWidth() * YSliceSize * updatingVolume.getSize()];
        ImageStack data = updatingVolume;
        for (int i = 0; i < updatingVolume.getSize(); i++) {
            float[] slice = (float[]) data.getPixels(i + 1);
            System.arraycopy(slice, startY * updatingVolume.getWidth() , tmp, i * updatingVolume.getWidth() * YSliceSize ,  updatingVolume.getWidth() * (YSliceSize));
        }
        writeBuffer(volIndex,tmp);
    }

    public int addBufferFromImageStack(ImageStack data,boolean allowWriting){
        float[] tmp = new float[data.getWidth() * data.getHeight() * data.getSize()];
        for (int i = 0; i < data.getSize(); i++) {
            float[] slice = (float[]) data.getPixels(i + 1);
            System.arraycopy(slice, 0 , tmp, i * data.getWidth()*data.getHeight() ,  data.getWidth() * data.getHeight());
        }
        return addBuffer(tmp,allowWriting);
    }

    public void fillBuffer(int index, float value) {
        ByteBuffer buffer = ByteBuffer.allocate(Sizeof.cl_float).order(ByteOrder.nativeOrder());
        buffer.putFloat(0, value);
        clEnqueueFillBuffer(commandQueue, buffers.get(index), Pointer.to(buffer), Sizeof.cl_float, 0, getBufferLength(index), 0, null, null);
    }

    public long getBufferLength(int index) {
        ByteBuffer buffer = ByteBuffer.allocate(Sizeof.size_t).order(ByteOrder.nativeOrder());
        clGetMemObjectInfo(buffers.get(index), CL_MEM_SIZE, Sizeof.size_t, Pointer.to(buffer), null);
        long[] values = new long[1];
        if (Sizeof.size_t == 4) {
            values[0] = buffer.getInt(0);
        } else {
            values[0] = buffer.getLong(0);
        }
        return values[0];
    }

    public void copyBufferToImage3D(int indexBuffer, int indexImage3D, int width, int height, int depth) {
        clEnqueueCopyBufferToImage(commandQueue, buffers.get(indexBuffer), image3Ds.get(indexImage3D), 0, new long[3], new long[]{width, height, depth}, 0, null, null);
    }
    public void copyImage3DToBuffer( int indexImage3D, int indexBuffer, int width, int height, int depth) {
        clEnqueueCopyImageToBuffer(commandQueue,  image3Ds.get(indexImage3D), buffers.get(indexBuffer), new long[3],  new long[]{width, height, depth},0, 0, null, null);
    }

    public void copyImage3D(int indexsrc, int indexdest, int width, int height, int depth) {
        clEnqueueCopyImage(commandQueue, image3Ds.get(indexsrc), image3Ds.get(indexdest), new long[3], new long[3], new long[]{width, height, depth}, 0, null, null);
    }

    public int addImage3D(float[] array, int width, int height, int depth, cl_image_format imageFormat) {
        if (image3Ds == null) image3Ds = new ArrayList<cl_mem>();
        image3Ds.add(clCreateImage3D(
                context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                new cl_image_format[]{imageFormat}, width, height, depth,
                width * Sizeof.cl_float, width * height * Sizeof.cl_float, Pointer.to(array), null));

        // Implementation for OpenCL 1.2 and above
//        cl_image_desc img_desc = new cl_image_desc();
//        img_desc.image_width = width;
//        img_desc.image_height = height;
//        img_desc.image_depth = depth;
//        img_desc.image_slice_pitch = width * height * Sizeof.cl_float;
//
//        image3Ds.add(clCreateImage(
//                context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
//                imageFormat, img_desc, Pointer.to(array), null));

        return image3Ds.size() - 1;
    }

    public cl_mem getImage3D(int index) {
        //System.out.println("get image3D "+image3Ds.size());
        return image3Ds.get(index);
    }

    public int getNbOfImage3Ds() {
        return image3Ds.size();
    }

    public void removeImage3D(int index) {
        if (image3Ds != null && index < image3Ds.size()) {
            cl_mem tmp = image3Ds.remove(index);
            releaseCL(tmp);
        }
    }

    public void removeAllImage3Ds() {
        while (image3Ds != null && image3Ds.size() > 0) {
            cl_mem tmp = image3Ds.remove(0);
            releaseCL(tmp);
        }
    }

    public void readFromImage3D(int index, float[] data, int width, int height, int depth) {
        clEnqueueReadImage(commandQueue, image3Ds.get(index), true, new long[3], new long[]{width, height, depth}, width * Sizeof.cl_float, width * height * Sizeof.cl_float, Pointer.to(data), 0, null, null);
    }

    public int addImage2D(float[] array, int width, int height, cl_image_format imageFormat) {
        if (image2Ds == null) image2Ds = new ArrayList<cl_mem>();
        image2Ds.add(clCreateImage2D(
                context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                new cl_image_format[]{imageFormat}, width, height,
                width * Sizeof.cl_float, Pointer.to(array), null));

        // Implementation for OpenCL 1.2 and above
//        cl_image_desc img_desc = new cl_image_desc();
//        img_desc.image_width = width;
//        img_desc.image_height = height;
//        img_desc.image_row_pitch = width * Sizeof.cl_float;
//
//        image2Ds.add(clCreateImage(
//                context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
//                imageFormat, img_desc, Pointer.to(array), null));

        return image2Ds.size() - 1;
    }

    public cl_mem getImage2D(int index) {
        return image2Ds.get(index);
    }

    public long getImage2DWidth(int index) {
        ByteBuffer buffer = ByteBuffer.allocate(Sizeof.size_t).order(ByteOrder.nativeOrder());
        clGetImageInfo(image2Ds.get(index), CL_IMAGE_WIDTH, Sizeof.size_t, Pointer.to(buffer), null);
        long[] values = new long[1];
        if (Sizeof.size_t == 4) {
            values[0] = buffer.getInt(0);
        } else {
            values[0] = buffer.getLong(0);
        }
        return values[0];
    }

    public long getImage2DHeight(int index) {
        ByteBuffer buffer = ByteBuffer.allocate(Sizeof.size_t).order(ByteOrder.nativeOrder());
        clGetImageInfo(image2Ds.get(index), CL_IMAGE_HEIGHT, Sizeof.size_t, Pointer.to(buffer), null);
        long[] values = new long[1];
        if (Sizeof.size_t == 4) {
            values[0] = buffer.getInt(0);
        } else {
            values[0] = buffer.getLong(0);
        }
        return values[0];
    }

    public void removeImage2D(int index) {
        if (image2Ds != null && index < image2Ds.size()) {
            cl_mem tmp = image2Ds.remove(index);
            releaseCL(tmp);
        }
    }

    public void removeAllImage2Ds() {
        while (image2Ds != null && image2Ds.size() > 0) {
            cl_mem tmp = image2Ds.remove(0);
            releaseCL(tmp);
        }
    }

    public void copyImage2D(int indexsrc, int indexdest, int width, int height) {
        clEnqueueCopyImage(commandQueue, image2Ds.get(indexsrc), image2Ds.get(indexdest), new long[3], new long[3], new long[]{width, height, 1}, 0, null, null);
    }

    public void writeImage2D(int index, float[] data, int width, int height) {
        clEnqueueWriteImage(commandQueue, image2Ds.get(index), true, new long[3], new long[]{width, height, 1}, width * Sizeof.cl_float, 0, Pointer.to(data), 0, null, null);

    }

    public void readFromImage2D(int index, float[] data, int width, int height) {
        clEnqueueReadImage(commandQueue, image2Ds.get(index), true, new long[3], new long[]{width, height, 1}, width * Sizeof.cl_float, 0, Pointer.to(data), 0, null, null);
    }

    public void setImage3DWriteSupport(boolean support) {
        supportImage3DWrite = support;
    }


}
