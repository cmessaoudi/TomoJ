package fr.curie.gpu.plugins;

import fr.curie.gpu.utils.GPUDevice;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.jocl.cl_event;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.gpu.utils.GPUDevice;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by cedric on 18/06/2014.
 */
public class FFT_OCL implements ExtendedPlugInFilter, DialogListener {

    ImagePlus myimp;
    int flags = DOES_32;
    PlugInFilterRunner pfr;
    boolean preview;
    GPUDevice[] gpus = null;
    int[] indexes;
    int kernelOffset = 0;

    int radius1, radius2;
    int fftWidth, fftHeight;
    /*public FFT_OCL(GPUDevice[] devices){
        System.out.println("fft_ocl constructor with devices");
        initCL(devices);
    }  */

    public int setup(String arg, ImagePlus imp) {
        System.out.println("fft_ocl setup");
        myimp = imp;
        //initCL();
        return flags;
    }

    /**
     * Main processing method for the HotSpot_Detection object
     *
     * @param ip Description of the Parameter
     */
    public void run(ImageProcessor ip) {
        if (gpus == null) initCL();
        if (indexes == null) indexes = initOnGpu(gpus[0], ip);
        copyOriginalAsComplex(gpus[0], (float[]) ip.getPixels(), ip.getWidth(), ip.getHeight());
        computeFFT(gpus[0], indexes, fftWidth);
        highpass(gpus[0], indexes, fftWidth, radius2);
        computeIFFT(gpus[0], indexes, fftWidth);
        getBackFromGpu(gpus[0], indexes, (float[]) ip.getPixels(), ip.getWidth(), ip.getHeight());
        //for(int b:indexes) gpus[0].removeBuffer(b);
    }

    private void initCL() {
        System.out.println("fft_ocl initcl");
        initCL(GPUDevice.getGPUDevices());
    }

    /**
     * init stuff on devices
     * @param devices   list of devices on which init fft computation
     */
    public void initCL(GPUDevice[] devices) {
           System.out.println("fft_ocl initcl(devices");
           gpus = devices;
           String programSource = getSourceCode();
           System.out.println(gpus.length + " gpu devices detected");
           for (int d = 0; d < gpus.length; d++) {
               kernelOffset = gpus[d].getNbKernels();
               System.out.println("device #" + d);
               gpus[d].printDeviceInfo();
               gpus[d].compileProgram(programSource, (gpus[d].getSupportImage3DWrite()) ? "-D IMAGE3D_WRITE" : null);
               gpus[d].compileKernel(kernelOffset + 0, "spinFact");
               gpus[d].compileKernel(kernelOffset + 1, "bitReverse");
               gpus[d].compileKernel(kernelOffset + 2, "norm");
               gpus[d].compileKernel(kernelOffset + 3, "butterfly");
               gpus[d].compileKernel(kernelOffset + 4, "transpose");
               gpus[d].compileKernel(kernelOffset + 5, "hiPassFilter");
               gpus[d].compileKernel(kernelOffset + 6, "bandPassFilter");
               gpus[d].compileKernel(kernelOffset + 7, "electronTomographyWeighting");
               gpus[d].compileKernel(kernelOffset + 8, "convertComplexToImage2D");
               gpus[d].compileKernel(kernelOffset + 9, "applyMask");
               gpus[d].compileKernel(kernelOffset + 10, "createWeightingMask");
               gpus[d].compileKernel(kernelOffset + 11, "convertImage2DToComplex");

           }

       }

    /**
     * init the different needed buffers in the GPU device
     * @param device     device to use
     * @param ip  image the will be processed
     * @return  indexes of the different buffers
     */
    public int[] initOnGpu(GPUDevice device, ImageProcessor ip) {
        fftWidth = 2;
        fftHeight = 2;
        while (fftWidth < ip.getWidth()) fftWidth = fftWidth << 1;
        while (fftHeight < ip.getHeight()) fftHeight = fftHeight << 1;

        fftWidth = Math.max(fftWidth, fftHeight);
        fftHeight = fftWidth;

        float[] pixels = (float[]) ip.convertToFloat().getPixels();
        float[] xm = new float[fftWidth * fftHeight * 2];
        float[] rm = new float[xm.length];
        float[] wm = new float[fftWidth];
        //convert pixels to complex
        //for(int i=0;i<pixels.length;i++) xm[2*i]=pixels[i];
        for (int j = 0; j < ip.getHeight(); j++) {
            for (int i = 0; i < ip.getWidth(); i++) {
                xm[j * fftWidth * 2 + i * 2] = pixels[j * ip.getWidth() + i];
            }
        }

        int xmobj = device.addBuffer(xm, true);
        int rmobj = device.addBuffer(rm, true);
        int wmobj = device.addBuffer(wm, true);

        return indexes = new int[]{xmobj, rmobj, wmobj};
    }

    /**
     * copy an image as a complex buffer on gpu
     * @param device    device to work with
     * @param pixels    2D image data as 1D array
     * @param width  width of image
     * @param height  height of image
     */
    public void copyOriginalAsComplex(GPUDevice device, float[] pixels, int width, int height) {
        float[] xm = new float[fftWidth * fftHeight * 2];
        device.writeBuffer(indexes[1], xm);
        //convert pixels to complex
        //for(int i=0;i<pixels.length;i++) xm[2*i]=pixels[i];
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                xm[j * fftWidth * 2 + i * 2] = pixels[j * width + i];
            }
        }
        device.writeBuffer(indexes[0], xm);
    }

    /**
     * compute the FFT
     * @param device     device to work on
     * @param indexesBuffers  indexes of the different buffer needed for computation (as given by initOnGPU)
     * @param width   width of image
     *                @see #initOnGpu(fr.curie.gpu.utils.GPUDevice, ij.process.ImageProcessor)
     */
    public void computeFFT(GPUDevice device, int[] indexesBuffers, int width) {
        //System.out.println(device.getBuffer(indexesBuffers[2]));
        int m = (int) (Math.log(width) / Math.log(2.0));
        long[] gws = new long[2];
        //spinfactors
        gws[0] = width / 2;
        gws[1] = 1;
        ArrayList<Object> args = new ArrayList<Object>(2);
        args.add(device.getBuffer(indexesBuffers[2]));
        args.add(new int[]{width});
        device.runKernel(kernelOffset + 0, args, gws);
        device.waitWorkFinished();

        //FFT1D
        fftCore(device, indexesBuffers[1], indexesBuffers[0], indexesBuffers[2], m, true);

        //transpose
        gws[0] = width;
        gws[1] = width;
        args = new ArrayList<Object>(3);
        args.add(device.getBuffer(indexesBuffers[0]));
        args.add(device.getBuffer(indexesBuffers[1]));
        args.add(new int[]{width});
        device.runKernel(kernelOffset + 4, args, gws);
        device.waitWorkFinished();

        //FFT1D
        fftCore(device, indexesBuffers[1], indexesBuffers[0], indexesBuffers[2], m, true);

        /* Create spin factor */
        //    ret = clSetKernelArg(sfac, 0, sizeof(cl_mem), (void *)&wmobj);
        //    ret = clSetKernelArg(sfac, 1, sizeof(cl_int), (void *)&n);
        //    setWorkSize(gws, lws, n/2, 1);
        //    ret = clEnqueueNDRangeKernel(queue, sfac, 1, NULL, gws, lws, 0, NULL, NULL);
        //
        //    /* Butterfly Operation */
        //    fftCore(rmobj, xmobj, wmobj, m, forward);
        //
        //    /* Transpose matrix */
        //    ret = clSetKernelArg(trns, 0, sizeof(cl_mem), (void *)&xmobj);
        //    ret = clSetKernelArg(trns, 1, sizeof(cl_mem), (void *)&rmobj);
        //    ret = clSetKernelArg(trns, 2, sizeof(cl_int), (void *)&n);
        //    setWorkSize(gws, lws, n, n);
        //    ret = clEnqueueNDRangeKernel(queue, trns, 2, NULL, gws, lws, 0, NULL, NULL);
        //
        //    /* Butterfly Operation */
        //    fftCore(rmobj, xmobj, wmobj, m, forward);
    }

    /**
     * perform high pass filter
     * @param device  device to work on
     * @param indexesBuffers     indexes of the different buffers needed    @see #ini
     * @param n  width of fft (fft is square)
     * @param radius    radius of the filter in pixel size
     * @see #initOnGpu(fr.curie.gpu.utils.GPUDevice, ij.process.ImageProcessor)
     */
    public void highpass(GPUDevice device, int[] indexesBuffers, int n, int radius) {
        long[] gws = new long[2];
        gws[0] = n;
        gws[1] = n;
        //int radius=n/8;
        ArrayList<Object> args = new ArrayList<Object>(2);
        args.add(device.getBuffer(indexesBuffers[1]));
        args.add(new int[]{n});
        args.add(new int[]{radius});
        device.runKernel(kernelOffset + 5, args, gws);
        device.waitWorkFinished();
        /* Apply high-pass filter */
        //    cl_int radius = n/8;
        //    ret = clSetKernelArg(hpfl, 0, sizeof(cl_mem), (void *)&rmobj);
        //    ret = clSetKernelArg(hpfl, 1, sizeof(cl_int), (void *)&n);
        //    ret = clSetKernelArg(hpfl, 2, sizeof(cl_int), (void *)&radius);
        //    setWorkSize(gws, lws, n, n);
        //    ret = clEnqueueNDRangeKernel(queue, hpfl, 2, NULL, gws, lws, 0, NULL, NULL);
    }

    /**
     * compute inverse fft
     * @param device    device to use
     * @param indexesBuffers       indexes of the different buffer needed for computation (as given by initOnGPU)
     * @param n   width of fft (fft is square)
     * @see #initOnGpu(fr.curie.gpu.utils.GPUDevice, ij.process.ImageProcessor)
     */
    public void computeIFFT(GPUDevice device, int[] indexesBuffers, int n) {
        int m = (int) (Math.log(n) / Math.log(2.0));
        long[] gws = new long[2];

        //FFT1D
        fftCore(device, indexesBuffers[0], indexesBuffers[1], indexesBuffers[2], m, false);
        //transpose
        gws[0] = n;
        gws[1] = n;
        ArrayList<Object> args = new ArrayList<Object>(3);
        args.add(device.getBuffer(indexesBuffers[1]));
        args.add(device.getBuffer(indexesBuffers[0]));
        args.add(new int[]{n});
        device.runKernel(kernelOffset + 4, args, gws);
        device.waitWorkFinished();
        //FFT1D
        fftCore(device, indexesBuffers[0], indexesBuffers[1], indexesBuffers[2], m, false);

        /* Butterfly Operation */
        //    fftCore(xmobj, rmobj, wmobj, m, inverse);
        //
        //    /* Transpose matrix */
        //    ret = clSetKernelArg(trns, 0, sizeof(cl_mem), (void *)&rmobj);
        //    ret = clSetKernelArg(trns, 1, sizeof(cl_mem), (void *)&xmobj);
        //    setWorkSize(gws, lws, n, n);
        //    ret = clEnqueueNDRangeKernel(queue, trns, 2, NULL, gws, lws, 0, NULL, NULL);
        //
        //    /* Butterfly Operation */
        //    fftCore(xmobj, rmobj, wmobj, m, inverse);

    }

    /**
     * read result of inverse fft from gpu
     * @param device   device to read from
     * @param indexesBuffers    indexes of the different buffer needed for computation (as given by initOnGPU)
     * @param pixels   the array to put the result
     * @param width    width of image
     * @param height   height of image
     * @return    the array containing the image
     * @see #initOnGpu(fr.curie.gpu.utils.GPUDevice, ij.process.ImageProcessor)
     */
    public float[] getBackFromGpu(GPUDevice device, int[] indexesBuffers, float[] pixels, int width, int height) {
        return getBackFromGpu(device, indexesBuffers[0], pixels, width, height);
    }

    /**
     *   reads result of inverse fft from gpu
     * @param device    device to read from
     * @param indexesBuffer    index of buffer to read from
     * @param pixels    the array to put the result
     * @param width     width of image
     * @param height   height of image
     * @return    the array containing the image
     */
    public float[] getBackFromGpu(GPUDevice device, int indexesBuffer, float[] pixels, int width, int height) {
           float[] complexdata = new float[fftWidth * fftHeight * 2];

           device.readFromBuffer(indexesBuffer, complexdata);
           for (int j = 0; j < height; j++) {
               for (int i = 0; i < width; i++) {
                   pixels[j * width + i] = complexdata[j * fftWidth * 2 + i * 2];
               }
           }
   //        for(int i=0;i<pixels.length;i++){
   //            pixels[i]=complexdata[2*i];
   //        }

           return pixels;
       }
    /**
     * compute the fft
     * @param device     device to use
     * @param dst        destination buffer
     * @param src        source buffer
     * @param spin
     * @param m
     * @param forward    true for forward fft, false for inverse fft
     * @return
     */
    int fftCore(GPUDevice device, int dst, int src, int spin, int m, boolean forward) {
        //System.out.println("call to FFT1D");
        int ret;

        int iter;
        int flag;

        int n = 1 << m;

        cl_event kernelDone;

        int brev = kernelOffset + 1;
        int bfly = kernelOffset + 3;
        int norm = kernelOffset + 2;


        long[] gws = new long[2];
        gws[0] = n;
        gws[1] = n;
        //long[] lws=new long[2];

        if (forward) {
            flag = 0x00000000;
        } else {
            flag = 0x80000000;
        }



        /* Reverse bit ordering */
        //setWorkSize(gws, lws, n, n);
        //ret = clEnqueueNDRangeKernel(queue, brev, 2, NULL, gws, lws, 0, NULL, NULL);
        ArrayList<Object> args = new ArrayList<Object>(4);
        args.add(device.getBuffer(dst));
        args.add(device.getBuffer(src));
        args.add(new int[]{m});
        args.add(new int[]{n});
        device.runKernel(brev, args, gws);
        device.waitWorkFinished();
        //System.out.println("butterfly");
        /* Perform Butterfly Operations*/
        //setWorkSize(gws, lws, n / 2, n);
        gws[0] = n / 2;
        args = new ArrayList<Object>(6);
        args.add(device.getBuffer(dst));
        args.add(device.getBuffer(spin));
        args.add(new int[]{m});
        args.add(new int[]{n});
        args.add(new int[]{0});
        args.add(new int[]{flag});
        for (iter = 1; iter <= m; iter++) {
            //ret = clSetKernelArg(bfly, 4, sizeof(cl_int), (void *)&iter);
            //ret = clEnqueueNDRangeKernel(queue, bfly, 2, NULL, gws, lws, 0, NULL, &kernelDone);
            args.set(4, new int[]{iter});
            //System.out.println("args length:"+args.size());
            device.runKernel(bfly, args, gws);
            device.waitWorkFinished();
            //ret = clWaitForEvents(1, &kernelDone);
        }

        if (!forward) {
            //setWorkSize(gws, lws, n, n);
            gws[0] = n;
            args = new ArrayList<Object>(2);
            args.add(device.getBuffer(dst));
            args.add(new int[]{n});
            device.runKernel(norm, args, gws);
            device.waitWorkFinished();
            //ret = clEnqueueNDRangeKernel(queue, norm, 2, NULL, gws, lws, 0, NULL, &kernelDone);
            //ret = clWaitForEvents(1, &kernelDone);
        }
        //System.out.println("end fft1D");


        return 0;
    }


    /**
     * reads the source code in FFT.cl
     * @return    the code to compile
     */
    private String getSourceCode() {
        System.out.println(this.getClass().getResource("FFT_OCL.class"));
        System.out.println(this.getClass().getResource("/FFT.cl"));

        String programSource = "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getResource("/FFT.cl").openStream()));

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

    /**
     * Ddialog box for the plugin
     *
     * @param imp     Description of the Parameter
     * @param command Description of the Parameter
     * @param pfr     Description of the Parameter
     * @return Description of the Return Value
     */
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        this.pfr = pfr;
        preview = true;
        GenericDialog gd = new GenericDialog("BandPass");
        gd.addNumericField("radius1", radius1, 2);
        gd.addNumericField("radius2", radius2, 2);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return DONE;
        }
        preview = false;
        radius1 = (int) gd.getNextNumber();
        radius2 = (int) gd.getNextNumber();
        return IJ.setupDialog(imp, flags);
    }

    /**
     * Sets the nPasses attribute of the HotSpot_Detection object
     *
     * @param nPasses The new nPasses value
     */
    public void setNPasses(int nPasses) {
    }

    /**
     * Listener to modifications of the input fields of the dialog
     *
     * @param gd Description of the Parameter
     * @param e  Description of the Parameter
     * @return Description of the Return Value
     */
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        radius1 = (int) gd.getNextNumber();
        radius2 = (int) gd.getNextNumber();
        return true;
    }

    public void setRadius(int value) {
        radius2 = value;
    }

    public void releaseCL() {
        for (GPUDevice d : gpus) d.releaseCL_Memory();
    }

    public void removeBuffers(GPUDevice device) {
        for (int i = indexes.length - 1; i >= 0; i--) device.removeBuffer(indexes[i]);
    }

    public GPUDevice[] getDevices() {
        return gpus;
    }

    /**
     * performs weighting of frequencies as described in radermacher 1992
     * @param device    device to use
     * @param indexOfResultingImage2D    index of result  in gpu (image2D)
     * @param indexMask          index of mask  in gpu (buffer)
     * @param width            width of image
     * @param height           height of image
     */
    public void weighting(GPUDevice device, int indexOfResultingImage2D, int indexMask, int width, int height) {
        //System.out.println("weigthing index Image:" + indexOfResultingImage2D + ", mask (buffer):" + indexMask);
        //for(int i:indexes) System.out.println("indexes (buffer):"+i);
        copyOriginalAsComplex(device, indexOfResultingImage2D, width, height);
        //System.out.println("indexes[0]:"+indexes[0]+" : "+device.getBuffer(indexes[0]));
        //System.out.println("indexes[0]:"+indexes[1]+" : "+device.getBuffer(indexes[1]));
        //System.out.println("indexes[0]:"+indexes[2]+" : "+device.getBuffer(indexes[2]));
        computeFFT(device, indexes, fftWidth);
        applyMask(device, indexes[1], indexMask, fftWidth, fftHeight);

        //highpass(device,indexes,fftWidth,10);
        computeIFFT(device, indexes, fftWidth);
        convertComplexToImage2D(device, indexes[0], indexOfResultingImage2D, width, height);
    }

    /**
     * copy an image in Gpu as complex in the source for fft
     * @param device     device to use
     * @param imageIndex    index of image in gpu (image2D)
     * @param width      width of image       *
     * @param height     height of image
     */
    public void copyOriginalAsComplex(GPUDevice device, int imageIndex, int width, int height) {
        float[] xm = new float[fftWidth * fftHeight * 2];
        device.writeBuffer(indexes[1], xm);
        /*float[] img=new float[width*height];
        device.readFromImage2D(imageIndex,img,width,height);
        for(int j=0;j<height;j++){
            for(int i=0;i<width;i++){
                xm[j*fftWidth*2+i*2]=img[j*width+i];
            }
        }
        device.writeBuffer(indexes[0],xm); */
        long[] gws = new long[2];
        gws[0] = width;
        gws[1] = height;
        ArrayList<Object> args = new ArrayList<Object>(2);
        args.add(device.getImage2D(imageIndex));
        args.add(device.getBuffer(indexes[0]));
        args.add(new int[]{fftWidth, fftHeight});
        device.runKernel(kernelOffset + 11, args, gws);
        device.waitWorkFinished();
    }

    /**
     * apply mask on fft in gpu
     * @param device    device to use
     * @param indexFFT       index of fft buffer in gpu
     * @param indexMask       index of mask buffer in gpu
     * @param width        width of image
     * @param height       height of image
     */
    public void applyMask(GPUDevice device, int indexFFT, int indexMask, int width, int height) {
        long[] gws = new long[2];
        gws[0] = width * height;
        gws[1] = 1;
        ArrayList<Object> args = new ArrayList<Object>(2);
        args.add(device.getBuffer(indexFFT));
        args.add(device.getBuffer(indexMask));
        device.runKernel(kernelOffset + 9, args, gws);
        device.waitWorkFinished();

    }

    /**
     * copy real part of result of inverse fft in gpu to an image2D in gpu
     * @param device  device to work with
     * @param indexBufferComplex      index of result of inveerse fft
     * @param indexImage2DReal       index of image2D to copy in
     * @param width       width of image
     * @param height      height of image
     */
    public void convertComplexToImage2D(GPUDevice device, int indexBufferComplex, int indexImage2DReal, int width, int height) {
        long[] gws = new long[2];
        gws[0] = width;
        gws[1] = height;
        ArrayList<Object> args = new ArrayList<Object>(2);
        args.add(device.getBuffer(indexBufferComplex));
        args.add(device.getImage2D(indexImage2DReal));
        args.add(new int[]{fftWidth, fftHeight});
        device.runKernel(kernelOffset + 8, args, gws);
        device.waitWorkFinished();

    }

    public void weighting(GPUDevice device, int indexOfResultingImage2D, TiltSeries ts, int index, int indexMask) {
        weighting(device, indexOfResultingImage2D, new FloatProcessor(ts.getWidth(), ts.getHeight(), ts.getPixels(index)), indexMask);
    }

    public void weighting(GPUDevice device, int indexOfResultingImage2D, ImageProcessor proj, int indexMask) {
//        if(index==0){
//            FloatProcessor fp3= new FloatProcessor(ts.getWidth(),ts.getHeight());
//            device.readFromBuffer(indexMask,(float[])fp3.getPixels());
//           // new ImagePlus("mask in gpu at start up of weigthing function",fp3).show();
//       }

        if (indexes == null) indexes = initOnGpu(device, proj);
//            if(index==0) for(int i:indexes) System.out.println("indexes : "+i);
//            if(index==0) System.out.println("index resulting image: "+indexOfResultingImage2D+"\nindex of mask: "+indexMask);
        copyOriginalAsComplex(device, (float[]) proj.getPixels(), proj.getWidth(), proj.getHeight());
        computeFFT(device, indexes, fftWidth);

        //weightingFilter(device,indexes[1],diameter,ts, index);
        applyMask(device, indexes[1], indexMask, fftWidth, fftHeight);
//            if(index==0){
//                FloatProcessor fp3= new FloatProcessor(ts.getWidth(),ts.getHeight());
//                device.readFromBuffer(indexMask,(float[])fp3.getPixels());
//                new ImagePlus("mask in gpu",fp3).show();
//           }
        //highpass(device,indexes,ts.getWidth(),20);
        //device.removeBuffer(indexMask);
        computeIFFT(device, indexes, fftWidth);
        convertComplexToImage2D(device, indexes[0], indexOfResultingImage2D, proj.getWidth(), proj.getHeight());
//            //if(index==0) {
//                FloatProcessor fp = new FloatProcessor(ts.getWidth(), ts.getHeight());
//                //getBackFromGpu(device,indexes,(float[])fp.getPixels());
//                device.readFromImage2D(indexOfResultingImage2D, (float[]) fp.getPixels(), ts.getWidth(), ts.getHeight());
//                device.waitWorkFinished();
//                new ImagePlus("filtered " + ts.getTiltAngle(index), fp).show();
//
//            //}
    }

    /**
     *
     * @param device
     * @param indexOfResultingImage2D
     * @param ts
     * @param index
     * @param mask
     */
    public void weighting(GPUDevice device, int indexOfResultingImage2D, TiltSeries ts, int index, float[] mask) {
        if (indexes == null) indexes = initOnGpu(device, ts.getProcessor());
        if (index == 0) for (int i : indexes) System.out.println("indexes : " + i);
        if (index == 0) System.out.println("index resulting image: " + indexOfResultingImage2D);
        copyOriginalAsComplex(device, ts.getPixels(index), ts.getWidth(), ts.getHeight());
        computeFFT(device, indexes, ts.getWidth());
//        if(index==0){
//            FloatProcessor fp=new FloatProcessor(ts.getWidth(),ts.getHeight());
//            getBackFromGpu(device,indexes[1],(float[])fp.getPixels());
//            new ImagePlus("before",fp).show();
//        }
        //weightingFilter(device,indexes[1],diameter,ts, index);
        int indexMask = device.addBuffer(mask, true);
        if (index == 0) System.out.println("index mask" + indexMask);
        device.waitWorkFinished();
        applyMask(device, indexes[1], indexMask, ts.getWidth(), ts.getHeight());
        if (index == 0) {
//           FloatProcessor fp2=new FloatProcessor(ts.getWidth(),ts.getHeight());
//           getBackFromGpu(device,indexes[1],(float[])fp2.getPixels());
//           new ImagePlus("after",fp2).show();

            FloatProcessor fp3 = new FloatProcessor(ts.getWidth(), ts.getHeight());
            device.readFromBuffer(indexMask, (float[]) fp3.getPixels());
            new ImagePlus("mask in gpu", fp3).show();
        }
        //highpass(device,indexes,ts.getWidth(),20);
        device.removeBuffer(indexMask);
        computeIFFT(device, indexes, ts.getWidth());
        convertComplexToImage2D(device, indexes[0], indexOfResultingImage2D, ts.getWidth(), ts.getHeight());
        if (index == 0) {
            FloatProcessor fp = new FloatProcessor(ts.getWidth(), ts.getHeight());
            //getBackFromGpu(device,indexes,(float[])fp.getPixels());
            device.readFromImage2D(indexOfResultingImage2D, (float[]) fp.getPixels(), ts.getWidth(), ts.getHeight());
            device.waitWorkFinished();
            new ImagePlus("filtered " + ts.getTiltAngle(index), fp).show();
            //new ImagePlus("mask",new FloatProcessor(ts.getWidth(),ts.getHeight(),mask)).show();

        }
    }

    /**
     * weights as in Radermacher 1992 FFT do everything copy image in GPU, FFT, weighting, IFFT return as floatProcessor
     * @param device
     * @param indexOfResultingImage2D
     * @param ts
     * @param index
     * @param diameter
     * @return
     */
    public FloatProcessor weighting(GPUDevice device, int indexOfResultingImage2D, TiltSeries ts, int index, float diameter) {
        //if(gpus==null) initCL();
        if (indexes == null) indexes = initOnGpu(device, ts.getProcessor());
        copyOriginalAsComplex(device, ts.getPixels(index), ts.getWidth(), ts.getHeight());
        computeFFT(device, indexes, ts.getWidth());
        //weightingFilter(device,indexes[1],diameter,ts, index);
        int indexMask = createWeightingMask(device, diameter, ts);
        applyMask(device, indexes[1], indexMask, ts.getWidth(), ts.getHeight());
        if (index == 0) {
            //           FloatProcessor fp2=new FloatProcessor(ts.getWidth(),ts.getHeight());
            //           getBackFromGpu(device,indexes[1],(float[])fp2.getPixels());
            //           new ImagePlus("after",fp2).show();

            FloatProcessor fp3 = new FloatProcessor(ts.getWidth(), ts.getHeight());
            device.readFromBuffer(indexMask, (float[]) fp3.getPixels());
            new ImagePlus("mask in gpu", fp3).show();

        }
        device.removeBuffer(indexMask);

        //computeIFFT(device,indexes,ts.getWidth());
        convertComplexToImage2D(device, indexes[1], indexOfResultingImage2D, ts.getWidth(), ts.getHeight());
        FloatProcessor fp = new FloatProcessor(ts.getWidth(), ts.getHeight());
        //getBackFromGpu(device,indexes,(float[])fp.getPixels());
        device.readFromImage2D(indexOfResultingImage2D, (float[]) fp.getPixels(), ts.getWidth(), ts.getHeight());
        device.waitWorkFinished();
        if (index == 0) {
            FloatProcessor fp2 = new FloatProcessor(ts.getWidth(), ts.getHeight());
            //getBackFromGpu(device,indexes,(float[])fp.getPixels());
            device.readFromImage2D(indexOfResultingImage2D, (float[]) fp2.getPixels(), ts.getWidth(), ts.getHeight());
            device.waitWorkFinished();
            new ImagePlus("filtered " + ts.getTiltAngle(index), fp2).show();
        }
        return fp;
    }

    /**
     *  create the weighting mask as described in Radermacher 1992
     * @param device
     * @param diameter
     * @param ts
     * @return   index of mask in the buffer list of the device
     */
    public int createWeightingMask(GPUDevice device, float diameter, TiltSeries ts) {
        //__kernel void createWeightingMask(__global float* mask, int2 dim, float tiltaxis, float* tiltangles, int nbproj, float diameter){

        long[] gws = new long[2];
        gws[0] = fftWidth;
        gws[1] = fftHeight;
        //int radius=n/8;
        float[] tmp = new float[fftWidth * fftHeight];
        int indexMask = device.addBuffer(tmp, true);
        ArrayList<Object> args = new ArrayList<Object>(2);
        args.add(device.getBuffer(indexMask));
        args.add(new int[]{fftWidth, fftHeight});
        args.add(new float[]{(float) ts.getTiltAxis()});
        double[] ta = ts.getTiltAngles();
        float[] tiltangles = new float[ta.length];
        for (int i = 0; i < ta.length; i++) {
            tiltangles[i] = (float) ta[i];
        }
        int indexTiltAngles = device.addBuffer(tiltangles, false);
        args.add((device.getBuffer(indexTiltAngles)));
        args.add(new int[]{ta.length});
        args.add(new float[]{diameter});
        device.runKernel(kernelOffset + 10, args, gws);
        device.waitWorkFinished();
        device.removeBuffer(indexTiltAngles);
        device.readFromBuffer(indexMask, tmp);
//        FloatProcessor fp=new FloatProcessor(fftWidth,fftHeight,tmp);
//        FileSaver fs=new FileSaver(new ImagePlus("",fp));
//        fs.saveAsTiff("mask.tif");
        return indexMask;
    }


    public void weightingFilter(GPUDevice device, int indexBufferOnGPU, float diameter, TiltSeries ts, int index) {
        //__global float2* fft, int2 dim,float diameter, float tiltaxis, float* tiltangle, int nbproj
        long[] gws = new long[2];
        gws[0] = ts.getWidth();
        gws[1] = ts.getHeight();
        //int radius=n/8;
        ArrayList<Object> args = new ArrayList<Object>(2);
        args.add(device.getBuffer(indexBufferOnGPU));
        args.add(new int[]{ts.getWidth(), ts.getHeight()});
        args.add(new float[]{diameter});
        args.add(new float[]{(float) ts.getTiltAxis()});

        double[] ta = ts.getTiltAngles();
        float[] tiltangles = new float[ta.length];
        for (int i = 0; i < ta.length; i++) {
            tiltangles[i] = (float) ta[i];
        }
        int indexTiltAngles = device.addBuffer(tiltangles, false);
        args.add((device.getBuffer(indexTiltAngles)));
        args.add(new int[]{ta.length});
        device.runKernel(kernelOffset + 7, args, gws);
        device.waitWorkFinished();
        device.removeBuffer(indexTiltAngles);

    }

    public void test(ImageProcessor ip) {
        if (gpus == null) {
            System.out.println("initCL");
            initCL();
        }
        if (indexes == null) {
            indexes = initOnGpu(gpus[0], ip);
            System.out.println("init memory");
        }
        copyOriginalAsComplex(gpus[0], (float[]) ip.getPixels(), ip.getWidth(), ip.getHeight());
        computeFFT(gpus[0], indexes, fftWidth);
        // highpass(gpus[0],indexes,fftWidth,radius2);
        computeIFFT(gpus[0], indexes, fftWidth);
        getBackFromGpu(gpus[0], indexes, (float[]) ip.getPixels(), ip.getWidth(), ip.getHeight());
    }

//    #include <stdio.h>
//    #include <stdlib.h>
//    #include <math.h>
//
//    #ifdef __APPLE__
//    #include <OpenCL/opencl.h>
//    #else
//    #include <CL/cl.h>
//    #endif
//
//    #include "pgm.h"
//
//    #define PI 3.14159265358979
//
//    #define MAX_SOURCE_SIZE (0x100000)
//
//    #define AMP(a, b) (sqrt((a)*(a)+(b)*(b)))
//
//    cl_device_id device_id = NULL;
//    cl_context context = NULL;
//    cl_command_queue queue = NULL;
//    cl_program program = NULL;
//
//    enum Mode {
//    forward = 0,
//    inverse = 1
//    };
//
//    int setWorkSize(size_t* gws, size_t* lws, cl_int x, cl_int y)
//    {
//    switch(y) {
//    case 1:
//    gws[0] = x;
//    gws[1] = 1;
//    lws[0] = 1;
//    lws[1] = 1;
//    break;
//    default:
//    gws[0] = x;
//    gws[1] = y;
//    lws[0] = 1;
//    lws[1] = 1;
//    break;
//    }
//
//    return 0;
//    }
//

//    int main()
//    {
//    cl_mem xmobj = NULL;
//    cl_mem rmobj = NULL;
//    cl_mem wmobj = NULL;
//    cl_kernel sfac = NULL;
//    cl_kernel trns = NULL;
//    cl_kernel hpfl = NULL;
//
//    cl_platform_id platform_id = NULL;
//
//    cl_uint ret_num_devices;
//    cl_uint ret_num_platforms;
//
//    cl_int ret;
//
//    cl_float2 *xm;
//    cl_float2 *rm;
//    cl_float2 *wm;
//
//    pgm_t ipgm;
//    pgm_t opgm;
//
//    FILE *fp;
//    const char fileName[] = "./fft.cl";
//    size_t source_size;
//    char *source_str;
//    cl_int i, j;
//    cl_int n;
//    cl_int m;
//
//    size_t gws[2];
//    size_t lws[2];
//
//    /* Load kernel source code */
//    fp = fopen(fileName, "r");
//    if (!fp) {
//    fprintf(stderr, "Failed to load kernel.\n");
//    exit(1);
//    }
//    source_str = (char *)malloc(MAX_SOURCE_SIZE);
//    source_size = fread(source_str, 1, MAX_SOURCE_SIZE, fp);
//    fclose( fp );
//
//    /* Read image */
//    readPGM(&ipgm, "lena.pgm");
//
//    n = ipgm.width;
//    m = (cl_int)(log((double)n)/log(2.0));
//
//    xm = (cl_float2 *)malloc(n * n * sizeof(cl_float2));
//    rm = (cl_float2 *)malloc(n * n * sizeof(cl_float2));
//    wm = (cl_float2 *)malloc(n / 2 * sizeof(cl_float2));
//
//    for (i=0; i < n; i++) {
//    for (j=0; j < n; j++) {
//    ((float*)xm)[(2*n*j)+2*i+0] = (float)ipgm.buf[n*j+i];
//    ((float*)xm)[(2*n*j)+2*i+1] = (float)0;
//    }
//    }
//
//    /* Get platform/device  */
//    ret = clGetPlatformIDs(1, &platform_id, &ret_num_platforms);
//    ret = clGetDeviceIDs( platform_id, CL_DEVICE_TYPE_DEFAULT, 1, &device_id, &ret_num_devices);
//
//    /* Create OpenCL context */
//    context = clCreateContext(NULL, 1, &device_id, NULL, NULL, &ret);
//
//    /* Create Command queue */
//    queue = clCreateCommandQueue(context, device_id, 0, &ret);
//
//    /* Create Buffer Objects */
//    xmobj = clCreateBuffer(context, CL_MEM_READ_WRITE, n*n*sizeof(cl_float2), NULL, &ret);
//    rmobj = clCreateBuffer(context, CL_MEM_READ_WRITE, n*n*sizeof(cl_float2), NULL, &ret);
//    wmobj = clCreateBuffer(context, CL_MEM_READ_WRITE, (n/2)*sizeof(cl_float2), NULL, &ret);
//
//    /* Transfer data to memory buffer */
//    ret = clEnqueueWriteBuffer(queue, xmobj, CL_TRUE, 0, n*n*sizeof(cl_float2), xm, 0, NULL, NULL);
//
//    /* Create kernel program from source */
//    program = clCreateProgramWithSource(context, 1, (const char **)&source_str, (const size_t *)&source_size, &ret);
//
//    /* Build kernel program */
//    ret = clBuildProgram(program, 1, &device_id, NULL, NULL, NULL);
//
//    /* Create OpenCL Kernel */
//    sfac = clCreateKernel(program, "spinFact", &ret);
//    trns = clCreateKernel(program, "transpose", &ret);
//    hpfl = clCreateKernel(program, "highPassFilter", &ret);
//
//    /* Create spin factor */
//    ret = clSetKernelArg(sfac, 0, sizeof(cl_mem), (void *)&wmobj);
//    ret = clSetKernelArg(sfac, 1, sizeof(cl_int), (void *)&n);
//    setWorkSize(gws, lws, n/2, 1);
//    ret = clEnqueueNDRangeKernel(queue, sfac, 1, NULL, gws, lws, 0, NULL, NULL);
//
//    /* Butterfly Operation */
//    fftCore(rmobj, xmobj, wmobj, m, forward);
//
//    /* Transpose matrix */
//    ret = clSetKernelArg(trns, 0, sizeof(cl_mem), (void *)&xmobj);
//    ret = clSetKernelArg(trns, 1, sizeof(cl_mem), (void *)&rmobj);
//    ret = clSetKernelArg(trns, 2, sizeof(cl_int), (void *)&n);
//    setWorkSize(gws, lws, n, n);
//    ret = clEnqueueNDRangeKernel(queue, trns, 2, NULL, gws, lws, 0, NULL, NULL);
//
//    /* Butterfly Operation */
//    fftCore(rmobj, xmobj, wmobj, m, forward);
//
//    /* Apply high-pass filter */
//    cl_int radius = n/8;
//    ret = clSetKernelArg(hpfl, 0, sizeof(cl_mem), (void *)&rmobj);
//    ret = clSetKernelArg(hpfl, 1, sizeof(cl_int), (void *)&n);
//    ret = clSetKernelArg(hpfl, 2, sizeof(cl_int), (void *)&radius);
//    setWorkSize(gws, lws, n, n);
//    ret = clEnqueueNDRangeKernel(queue, hpfl, 2, NULL, gws, lws, 0, NULL, NULL);
//
//    /* Inverse FFT */
//
//    /* Butterfly Operation */
//    fftCore(xmobj, rmobj, wmobj, m, inverse);
//
//    /* Transpose matrix */
//    ret = clSetKernelArg(trns, 0, sizeof(cl_mem), (void *)&rmobj);
//    ret = clSetKernelArg(trns, 1, sizeof(cl_mem), (void *)&xmobj);
//    setWorkSize(gws, lws, n, n);
//    ret = clEnqueueNDRangeKernel(queue, trns, 2, NULL, gws, lws, 0, NULL, NULL);
//
//    /* Butterfly Operation */
//    fftCore(xmobj, rmobj, wmobj, m, inverse);
//
//    /* Read data from memory buffer */
//    ret = clEnqueueReadBuffer(queue, xmobj, CL_TRUE, 0, n*n*sizeof(cl_float2), xm, 0, NULL, NULL);
//
//    /*  */
//    float* ampd;
//    ampd = (float*)malloc(n*n*sizeof(float));
//    for (i=0; i < n; i++) {
//    for (j=0; j < n; j++) {
//    ampd[n*((i))+((j))] = (AMP(((float*)xm)[(2*n*i)+2*j], ((float*)xm)[(2*n*i)+2*j+1]));
//    }
//    }
//    opgm.width = n;
//    opgm.height = n;
//    normalizeF2PGM(&opgm, ampd);
//    free(ampd);
//
//    /* Write out image */
//    writePGM(&opgm, "output.pgm");
//
//    /* Finalizations*/
//    ret = clFlush(queue);
//    ret = clFinish(queue);
//    ret = clReleaseKernel(hpfl);
//    ret = clReleaseKernel(trns);
//    ret = clReleaseKernel(sfac);
//    ret = clReleaseProgram(program);
//    ret = clReleaseMemObject(xmobj);
//    ret = clReleaseMemObject(rmobj);
//    ret = clReleaseMemObject(wmobj);
//    ret = clReleaseCommandQueue(queue);
//    ret = clReleaseContext(context);
//
//    destroyPGM(&ipgm);
//    destroyPGM(&opgm);
//
//    free(source_str);
//    free(wm);
//    free(rm);
//    free(xm);
//
//    return 0;
//    }
//     */
}
