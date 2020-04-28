package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by cmessaoudi on 17/01/2018.
 */
public class Simulate_Sparsity implements ExtendedPlugInFilter, DialogListener {
    PlugInFilterRunner pfr;
    boolean preview;
    double percentageNaN;
    boolean stemNaN;
    boolean linesNaN;
    boolean linesRowColNaN;
    int regularLines=-1;
    int stemBlockSizeX=16;
    int stemBlockSizeY=16;
    int linesHeight=1;
    int flags = DOES_32 + PARALLELIZE_STACKS;


    public int showDialog(ImagePlus imagePlus, String s, PlugInFilterRunner pfr) {
        this.pfr = pfr;
        preview = true;
        GenericDialog gd=new GenericDialog("Simulate Sparsity option");
        gd.addNumericField("percentage of image to put to NaN",0.5,2);
        gd.addCheckbox("simulation as STEM acquisition",false);
        gd.addNumericField("STEM block size X",stemBlockSizeX,0);
        gd.addNumericField("STEM block size Y",stemBlockSizeY,0);
        gd.addCheckbox("simulation lines",false);
        gd.addNumericField("height of lines",linesHeight,0);
        gd.addNumericField("regular repartition of lines every ",regularLines,0);
        gd.addCheckbox("simulation lines (row/column)",false);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();
        if(gd.wasCanceled()) return DONE;

        percentageNaN=gd.getNextNumber();
        stemNaN=gd.getNextBoolean();
        stemBlockSizeX=(int)gd.getNextNumber();
        stemBlockSizeY=(int)gd.getNextNumber();
        linesNaN=gd.getNextBoolean();
        linesHeight=(int)gd.getNextNumber();
        regularLines=(int)gd.getNextNumber();
        //linesHeight=stemBlockSizeY;
        linesRowColNaN=gd.getNextBoolean();
        System.out.println("stem block "+stemBlockSizeX+"x"+stemBlockSizeY);
        System.out.println("lines height="+linesHeight);
        return IJ.setupDialog(imagePlus, flags);
    }

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        percentageNaN=gd.getNextNumber();
        stemNaN=gd.getNextBoolean();
        stemBlockSizeX=(int)gd.getNextNumber();
        stemBlockSizeY=(int)gd.getNextNumber();
        linesNaN=gd.getNextBoolean();
        linesHeight=(int)gd.getNextNumber();
        regularLines=(int)gd.getNextNumber();
        linesRowColNaN=gd.getNextBoolean();

        return true;
    }

    public void setNPasses(int i) {

    }

    public int setup(String s, ImagePlus imagePlus) {
        return flags;
    }

    public void run(ImageProcessor imageProcessor) {
        ImageProcessor ipNaN;

        System.out.println("stem block "+stemBlockSizeX+"x"+stemBlockSizeY);
        System.out.println("lines height="+linesHeight);

            if(stemNaN){
                ipNaN=new FloatProcessor(imageProcessor.getWidth(),imageProcessor.getHeight());
                float[] pixels=(float[]) ipNaN.getPixels();
                Arrays.fill(pixels,Float.NaN);
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = pixels.length;
                while (i > nbNaN) {
                    int x = (int) (Math.random() * imageProcessor.getWidth());
                    int y = (int) (Math.random() * imageProcessor.getHeight());
                    x=Math.min(x,imageProcessor.getWidth()-stemBlockSizeX);
                    y=Math.min(y,imageProcessor.getHeight()-stemBlockSizeY);
                    //System.out.println("x:"+x+" , y:"+y);
                    //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));
                    if(Double.isNaN(ipNaN.getPixelValue(x,y))&&Double.isNaN(ipNaN.getPixelValue(x+stemBlockSizeX-1,y))&&Double.isNaN(ipNaN.getPixelValue(x,y+stemBlockSizeY-1))&&Double.isNaN(ipNaN.getPixelValue(x+stemBlockSizeX-1,y+stemBlockSizeY-1))) {
                        for (int yy = y; yy < y + stemBlockSizeY; yy++) {
                            for (int xx = x; xx < x + stemBlockSizeX; xx++) {
                                ipNaN.setf(xx, yy, imageProcessor.getf(xx, yy));
                            }
                        }
                        i -= stemBlockSizeX * stemBlockSizeY;
                    }
                }

            }else if(linesNaN){
                System.out.println("line height="+linesHeight);
                ipNaN=new FloatProcessor(imageProcessor.getWidth(),imageProcessor.getHeight());
                float[] pixels=(float[]) ipNaN.getPixels();
                Arrays.fill(pixels,Float.NaN);
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = pixels.length;
                if(regularLines<=0) {
                    while (i > nbNaN) {
                        int y = (int) (Math.random() * imageProcessor.getHeight());
                        y = Math.min(y, ipNaN.getHeight() - linesHeight);
                        //System.out.println("x:"+x+" , y:"+y);
                        //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));
                        if (Double.isNaN(ipNaN.getPixelValue(0, y)) && Double.isNaN(ipNaN.getPixelValue(0, y + linesHeight - 1))) {
                            for (int h = 0; h < linesHeight; h++) {
                                for (int xx = 0; xx < ipNaN.getWidth(); xx++) {
                                    ipNaN.setf(xx, y + h, imageProcessor.getf(xx, y + h));
                                }
                            }
                            i -= ipNaN.getWidth() * linesHeight;
                        }

                    }
                }else{
                    for(int y=(int)(Math.random() * regularLines);y<ipNaN.getHeight();y+=linesHeight+regularLines) {
                        for (int h = 0; h < linesHeight; h++) {
                            for (int xx = 0; xx < ipNaN.getWidth(); xx++) {
                                ipNaN.putPixelValue(xx, y + h, imageProcessor.getPixelValue(xx, y + h));
                            }
                        }
                    }

                }

            }else if(linesRowColNaN){
                ipNaN=new FloatProcessor(imageProcessor.getWidth(),imageProcessor.getHeight());
                float[] pixels=(float[]) ipNaN.getPixels();
                Arrays.fill(pixels,Float.NaN);
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = pixels.length;
                while (i > nbNaN) {
                    boolean row=Math.random()>0.5;
                    if (row) {
                        int y = (int) (Math.random() * imageProcessor.getHeight());
                        //System.out.println("x:"+x+" , y:"+y);
                        //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));
                        if(Double.isNaN(ipNaN.getPixelValue(0,y))) {
                            for (int xx = 0; xx < ipNaN.getWidth(); xx++) {
                                ipNaN.setf(xx, y, imageProcessor.getf(xx, y));
                            }
                            i -= ipNaN.getWidth();
                        }
                    }else{
                        int x = (int) (Math.random() * imageProcessor.getWidth());
                        //System.out.println("x:"+x+" , y:"+y);
                        //ipNaN.putPixelValue(x,y,imageProcessor.getPixelValue(x,y));
                        if(Double.isNaN(ipNaN.getPixelValue(x,0))) {
                            for (int yy = 0; yy < ipNaN.getHeight(); yy++) {
                                ipNaN.setf(x, yy, imageProcessor.getf(x, yy));
                            }
                            i -= ipNaN.getHeight();
                        }
                    }
                }

            }else{
                ipNaN=imageProcessor.duplicate();
                float[] pixels=(float[]) ipNaN.getPixels();
                int nbNaN= (int) (percentageNaN*pixels.length);
                int i = 0;
                while (i < nbNaN) {
                    int index = (int) (Math.random() * pixels.length);
                    if (!Float.isNaN(pixels[index])) {
                        pixels[index] = Float.NaN;
                        i++;
                    }
                }
            }
        imageProcessor.copyBits(ipNaN, 0, 0, Blitter.COPY);



    }

}
