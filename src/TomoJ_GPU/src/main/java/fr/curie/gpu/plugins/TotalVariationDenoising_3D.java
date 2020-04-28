package fr.curie.gpu.plugins;

import fr.curie.utils.Chrono;
import fr.curie.gpu.utils.GPUDevice;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
     * This denoising method is based on total-variation, originally proposed by
     * Rudin, Osher and Fatemi. In this particular case fixed point iteration
     * is utilized.
     * <p/>
     * For the included image, a fairly good result is obtained by using a
     * theta value around 12-16. A possible addition would be to analyze the
     * residual with an entropy function and add back areas that have a lower
     * entropy, i.e. there are some correlation between the surrounding pixels.
     * <p/>
     * Based on the Matlab code by Philippe Magiera & Carl Löndahl:
     * http://www.mathworks.com/matlabcentral/fileexchange/22410-rof-denoising-algorithm
     */
    public class TotalVariationDenoising_3D extends fr.curie.filters.TotalVariationDenoising_3D implements PlugInFilter {
        protected ImagePlus image;
        int flags = DOES_32 + STACK_REQUIRED;
        float theta = 25;
        float g = 1;
        float dt = 0.25f;
        int ite = 5;
        boolean gpu = true;
        static ExecutorService exec= Executors.newFixedThreadPool(Prefs.getThreads());

        static final String sourceCode = "" +
                "__kernel void updateP(__global float* u, __global float* p, const float theta, const float g, const float dt, const int4 size){\n" +
                "long x=get_global_id(0);\n" +
                "long y=get_global_id(1);\n" +
                "long z=get_global_id(2);\n" +
                "   float uxyz=u[x + y*size.x + z*size.x*size.y];\n" +
                "   float dux= (x<size.x-1)?u[(x+1) + y*size.x + z*size.x*size.y]-uxyz:0;\n" +
                "   float duy= (y<size.y-1)?u[x + (y+1)*size.x + z*size.x*size.y]-uxyz:0;\n" +
                "   float duz= (z<size.z-1)?u[x + y*size.x + (z+1)*size.x*size.y]-uxyz:0;\n" +
                "   float dd1= 1+dt/theta/g*fabs(sqrt(dux*dux+duy*duy+duz*duz));\n" +
                "   long pos = x*3+y*size.x*3+z*size.x*3*size.y;\n" +
                "   p[pos] = (p[pos] - dt / theta * dux) / dd1;\n" +
                "   p[pos+1] = (p[pos+1] - dt / theta * duy) / dd1;\n" +
                "   p[pos+2] = (p[pos+2] - dt / theta * duz) / dd1;\n" +
                "}\n" +
                "" +
                "__kernel void updateU(__global float* f, __global float* u, __global float* p, const float theta, const int4 size){\n" +
                "float div_p = 0;\n" +
                "long x=get_global_id(0);\n" +
                "long y=get_global_id(1);\n" +
                "long z=get_global_id(2);\n" +
                "long pos=x*3+y*size.x*3+z*size.x*3*size.y;\n" +
                "if (x == 0) div_p = p[pos];\n" +
                "else if (x == size.x - 1) div_p = -p[pos];\n" +
                "else div_p = p[pos] - p[pos-3];\n" +
                "if (y == 0) div_p += p[pos+1];\n" +
                "else if (y == size.y - 1) div_p += -p[pos+1];\n" +
                "else div_p += p[pos+1] - p[pos-size.x*3+1];\n" +
                "if (z == 0) div_p += p[pos+2];\n" +
                "else if (z == size.z-1) div_p += -p[pos+2];\n" +
                "else div_p += p[pos+2] - p[pos - size.x*3*size.y +2];\n" +
                "\n" +
                "u[x + y*size.x + z*size.x*size.y] = f[x + y*size.x + z*size.x*size.y] - theta * div_p;\n" +
                "}";


        public static void denoise(ImageProcessor ip, float theta) {
            denoise2D(ip, theta, 1, 0.25f, 5);
        }

        /**
         * This method gets called by ImageJ / Fiji to determine
         * whether the current image is of an appropriate type.
         *
         * @param arg   can be specified in plugins.config
         * @param image is the currently opened image
         */
        public int setup(String arg, ImagePlus image) {
            System.out.println("TVM 3D");
            this.image = image;
            return showDialog(image);
        }

        /**
         * This method is run when the current image was accepted.
         *
         * @param ip is the current slice (typically, plugins use
         *           the ImagePlus set above instead).
         */
        public void run(ImageProcessor ip) {
            Chrono time= new Chrono();
            time.start();
            if(gpu) denoise3DGPU(image.getImageStack(),theta,g,dt,ite);
            else denoise3D(image.getImageStack(),theta,g,dt,ite);
            time.stop();
            System.out.println("TVM3D total time : "+time.delayString());
            image.updateAndDraw();
        }



    public static void denoise3DGPU(ImageStack fstack, float theta, float g, float dt, int iterations) {
        GPUDevice device=GPUDevice.getBestDevice();
        device.compileProgram(sourceCode,null);
        device.compileKernel(0,"updateP");
        device.compileKernel(1,"updateU");
        denoise3DGPU(device,fstack,theta,g,dt,iterations);

    }

    public static void denoise3DGPU(GPUDevice device,ImageStack fstack, float theta, float g, float dt, int iterations){

        int width = fstack.getWidth();
        int height = fstack.getHeight();
        int depth = fstack.getSize();
        //ImageStack ustack = fstack.duplicate();
        ImageStack pstack = new ImageStack(width, height * 3);

        for (int z = 0; z < fstack.size(); z++) {
            pstack.addSlice("", new FloatProcessor(width, height * 3));

        }
        int indexF = device.addBufferFromImageStack(fstack,false);
        int indexU = device.addBufferFromImageStack(fstack,true);
        int indexP = device.addBufferFromImageStack(pstack,true);
        int[] size=new int[]{width,height,depth,0};
        denoise3DGPU(device,0,1,indexF,indexU,indexP,size,theta,g,dt,iterations);


        device.updateImageStackFromGPUBuffer(indexU,fstack,0,height,0,0);
    }

    public static void denoise3DGPU(GPUDevice device, int kernelIndexP, int kernelIndexU,int indexFBuffer, int indexUBuffer, int indexPBuffer, int[] size, float theta, float g, float dt, int iterations){
        for (int iteration = 0; iteration < iterations; iteration++) {
            System.out.println("tvm iteration "+iteration);
            updateP_GPU(device,kernelIndexP, indexUBuffer,indexPBuffer,theta,g,dt,size);
            updateU_GPU(device,kernelIndexU, indexFBuffer,indexUBuffer,indexPBuffer,theta,size);
        }
    }

    protected static void updateP_GPU(GPUDevice device,int kernelIndex, int indexU, int indexP, final float theta,final float g, final float dt, int[] size){
        long[] globalWorkSize = new long[3];
        globalWorkSize[0] = size[0];
        globalWorkSize[1] = size[1];
        globalWorkSize[2] = size[2];
        ArrayList<Object> args= new ArrayList<Object>(6);
        args.add(device.getBuffer(indexU));
        args.add(device.getBuffer(indexP));
        args.add(new float[]{theta});
        args.add(new float[]{g});
        args.add(new float[]{dt});
        args.add(size);

        device.runKernel(kernelIndex, args, globalWorkSize);
    }

    protected static void updateU_GPU(GPUDevice device,int kernelIndex, int indexF, int indexU, int indexP, final float theta, int[] size){
        long[] globalWorkSize = new long[3];
        globalWorkSize[0] = size[0];
        globalWorkSize[1] = size[1];
        globalWorkSize[2] = size[2];
        ArrayList<Object> args= new ArrayList<Object>(5);
        args.add(device.getBuffer(indexF));
        args.add(device.getBuffer(indexU));
        args.add(device.getBuffer(indexP));
        args.add(new float[]{theta});
        args.add(size);

        device.runKernel(kernelIndex, args, globalWorkSize);
    }

    public static String getSourceCode(){
            return sourceCode;
    }

        public static void denoise2D(ImageProcessor ip, float theta, float g, float dt, int iterations) {
            int w = ip.getWidth();
            int h = ip.getHeight();
            float[] pixels = (float[]) ip.getPixels();

            float[] u = new float[w * h];
            float[] p = new float[w * h * 2];
            float[] d = new float[w * h * 2];
            float[] du = new float[w * h * 2];
            float[] div_p = new float[w * h];

            System.arraycopy(pixels, 0, u, 0, w * h);
            for (int iteration = 0; iteration < iterations; iteration++) {
                // Calculate forward derivatives
                for (int j = 0; j < h; j++)
                    for (int i = 0; i < w; i++) {
                        if (i < w - 1)
                            du[i + w * (j + h)] = u[i + 1 + w * j] - u[i + w * j];
                        if (j < h - 1)
                            du[i + w * j] = u[i + w * (j + 1)] - u[i + w * j];
                    }

                // Iterate
                for (int j = 0; j < h; j++)
                    for (int i = 0; i < w; i++) {
                        float du1 = du[i + w * j], du2 = du[i + w * (j + h)];
                        float dd1 = 1 + dt / theta / g * Math.abs((float) Math.sqrt(du1 * du1 + du2 * du2));
                        p[i + w * j] = (p[i + w * j] - dt / theta * du[i + w * j]) / dd1;
                        p[i + w * (j + h)] = (p[i + w * (j + h)] - dt / theta * du[i + w * (j + h)]) / dd1;
                        //                                            d[i + w * j] = 1 + dt / theta / g * Math.abs((float)Math.sqrt(du1 * du1 + du2 * du2));
                        //                                            d[i + w * (j + h)] = 1 + dt / theta / g * Math.abs((float)Math.sqrt(du1 * du1 + du2 * du2));
                        //                                            p[i + w * j] = (p[i + w * j] - dt / theta * du[i + w * j]) / d[i + w * j];
                        //                                            p[i + w * (j + h)] = (p[i + w * (j + h)] - dt / theta * du[i + w * (j + h)]) / d[i + w * (j + h)];
                    }
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        if (i == 0) div_p[w * j] = p[w * (j + h)];
                        else if (i == w - 1) div_p[w - 1 + w * j] = -p[w - 1 + w * (j + h)];
                        else div_p[i + w * j] = p[i + w * (j + h)] - p[i - 1 + w * (j + h)];
                        if (j == 0) div_p[i] += p[i];
                        else if (j == h - 1) div_p[i + w * (h - 1)] += -p[i + w * (h - 1)];
                        else div_p[i + w * j] += p[i + w * j] - p[i + w * (j - 1)];

                    }
                }

                // Update u
                for (int j = 0; j < h; j++)
                    for (int i = 0; i < w; i++)
                        u[i + w * j] = pixels[i + w * j] - theta * div_p[i + w * j];


            }
            System.arraycopy(u, 0, pixels, 0, w * h);
        }

        /**
         * Description of the Method
         *
         * @param imp     Description of the Parameter
         * @return Description of the Return Value
         */
        public int showDialog(ImagePlus imp) {
            GenericDialog gd = new GenericDialog("ROF Denoise");
            gd.addNumericField("Theta", theta, 2);
            gd.addNumericField("g", g, 2);
            gd.addNumericField("dt", dt, 2);
            gd.addNumericField("iteration", ite, 0);
            gd.addCheckbox("gpu", true);
            gd.showDialog();
            if (gd.wasCanceled()) {
                return DONE;
            }
            theta = (float) gd.getNextNumber();
            g = (float) gd.getNextNumber();
            dt = (float) gd.getNextNumber();
            ite = (int) gd.getNextNumber();
            gpu = gd.getNextBoolean();
            return  flags;
        }

    }
