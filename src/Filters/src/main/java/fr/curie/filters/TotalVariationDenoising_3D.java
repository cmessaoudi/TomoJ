package fr.curie.filters;

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
import fr.curie.utils.Chrono;
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
    public class TotalVariationDenoising_3D  extends TestCase implements PlugInFilter {
        protected ImagePlus image;
        int flags = DOES_32 + STACK_REQUIRED;
        float theta = 25;
        float g = 1;
        float dt = 0.25f;
        int ite = 5;
        static ExecutorService exec= Executors.newFixedThreadPool(Prefs.getThreads());



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
            denoise3D(image.getImageStack(),theta,g,dt,ite);
            time.stop();
            System.out.println("TVM3D total time : "+time.delayString());
            image.updateAndDraw();
        }

        public void testDenoise() {
            ImagePlus imp = IJ.openImage("Z:\\Sanofi\\FIB-SEM_inpaintingTest\\Combo1_ali_resized.tif");
            Total_Variation_Denoising.denoise3D(imp.getImageStack(), 25, 1, 0.25f, 5);
            FileSaver fs = new FileSaver(imp);
            fs.saveAsTiffStack("Z:\\Sanofi\\FIB-SEM_inpaintingTest\\Combo1_ali_resized_TVM3D.tif");

        }


        public static void denoise3D(ImageStack fstack, float theta, float g, float dt, int iterations) {
            int width = fstack.getWidth();
            int height = fstack.getHeight();
            int depth = fstack.getSize();

            ImageStack ustack = fstack.duplicate();
            ImageStack pstack = new ImageStack(width*3, height );
            //ImageStack dustack = new ImageStack(width, height * 3);

            for (int z = 0; z < fstack.size(); z++) {
                pstack.addSlice("", new FloatProcessor(width*3, height));
                //dustack.addSlice("", new FloatProcessor(width, height * 3));
            }


            for (int iteration = 0; iteration < iterations; iteration++) {
                System.out.println("tvm iteration "+iteration);

                updateP(ustack,pstack,theta,g,dt);

                updateU(fstack,ustack,pstack,theta);


            }
            //new ImagePlus("p",pstack).show();
            //new ImagePlus("u",ustack).show();
            for(int z=1;z<= fstack.getSize();z++) {
                System.arraycopy(ustack.getPixels(z), 0, fstack.getPixels(z), 0, width * height);
            }
        }

        protected static void updateP(ImageStack ustack, ImageStack pstack, final float theta,final float g, final float dt ){
            final int width = ustack.getWidth();
            final int height = ustack.getHeight();
            final int depth = ustack.getSize();
            ArrayList<Future> works=new ArrayList<Future>(height*depth);
            for (int z = 1; z <= depth; z++) {
                //System.out.println("processing image " + z);
                final float[] u = (float[]) ustack.getPixels(z);
                final float[] u1 = (float[]) ustack.getPixels(Math.min(z + 1, depth));
                final float[] p = (float[]) pstack.getPixels(z);
                final int zz=z;

                // Iterate

                    works.add(exec.submit(new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            for (int y = 0; y < height; y++) {
                                final int yy = y;
                                for (int x = 0; x < width; x++) {
                                    final int xx = x;
                                    float duy = (yy < height - 1) ? u[xx + width * (yy + 1)] - u[xx + width * yy] : 0;//du[x + width * y];
                                    float dux = (xx < width - 1) ? u[xx + 1 + width * yy] - u[xx + width * yy] : 0;//du[x + width * (y + height)];
                                    float duz = (zz <= depth - 1) ? u1[xx + width * yy] - u[xx + width * yy] : 0;//du[x + width * (y + height * 2)];
                                    float dd1 = 1 + dt / theta / g * Math.abs((float) Math.sqrt(duy * duy + dux * dux + duz * duz));
                                    p[xx*3 + width*3 * yy] = (p[xx*3 + width *3*yy] - dt / theta * dux) / dd1;
                                    p[xx*3 +1 + width*3 * yy] = (p[xx*3+1 + width * 3*yy] - dt / theta * duy) / dd1;
                                    p[xx*3+2 + width*3 * yy] = (p[xx*3+2 + width * 3*yy] - dt / theta * duz) / dd1;
                                }
                            }
                        }
                    }));



            }
            for(Future f:works){
                try{f.get();}catch (Exception e){e.printStackTrace();}
            }

        }

        protected static void updateU(ImageStack fstack, ImageStack ustack, ImageStack pstack, final float theta){
            final int width = ustack.getWidth();
            final int height = ustack.getHeight();
            final int depth = ustack.getSize();
            ArrayList<Future> works=new ArrayList<Future>(height*depth);
            for (int z = 1; z <= fstack.getSize(); z++) {
                //float[] div_p = (float[]) divp.getPixels(z);
                final float[] p = (float[]) pstack.getPixels(z);
                final float[] p_1 = (float[]) pstack.getPixels(Math.max(z - 1, 1));
                final float[] pixels = (float[]) fstack.getPixels(z);
                final float[] u = (float[]) ustack.getPixels(z);
                final int zz=z;


                    works.add(exec.submit(new Thread(){
                        @Override
                        public void run() {
                            super.run();
                            for (int y = 0; y < height; y++) {
                                final int yy = y;
                                for (int x = 0; x < width; x++) {
                                    float div_p = 0;
                                    if (x == 0) div_p = p[x*3 + width *3* yy ];
                                    else if (x == width - 1) div_p = -p[x*3 + width *3*yy];
                                    else div_p = p[x*3 + width * 3*yy] - p[(x - 1)*3 + width *3*yy];
                                    if (yy == 0) div_p += p[x*3+1 + width*3 * yy];
                                    else if (yy == height - 1) div_p += -p[x*3+1 + width*3 * yy];
                                    else div_p += p[x*3+1 + width *3* yy] - p[x*3+1 + width *3* (yy - 1)];
                                    if (zz == 1) div_p += p[x*3+2 + width*3 * yy ];
                                    else if (zz == depth) div_p += -p[x*3+2 + width*3 * yy];
                                    else
                                        div_p += p[x*3+2 + width * 3*yy] - p_1[x*3+2 + width * 3*yy];

                                    u[x + width * yy] = pixels[x + width * yy] - theta * div_p;
                                }
                            }
                        }
                    }));


            }
            for(Future f:works){
                try{f.get();}catch (Exception e){e.printStackTrace();}
            }
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
            gd.showDialog();
            if (gd.wasCanceled()) {
                return DONE;
            }
            theta = (float) gd.getNextNumber();
            g = (float) gd.getNextNumber();
            dt = (float) gd.getNextNumber();
            ite = (int) gd.getNextNumber();
            return  flags;
        }

    }
