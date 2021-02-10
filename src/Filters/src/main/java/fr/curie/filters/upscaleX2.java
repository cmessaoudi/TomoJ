package fr.curie.filters;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.YesNoCancelDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.Arrays;

public class upscaleX2 implements PlugInFilter {
	ImagePlus myimp;
	boolean do3D=false;
	@Override
	public int setup(String s, ImagePlus imagePlus) {
		myimp=imagePlus;
		if(imagePlus.getImageStackSize()>1) {
			YesNoCancelDialog dialog= new YesNoCancelDialog(IJ.getInstance(),"upscale x2 with NaN", "increase also in Z direction?");
			do3D = dialog.yesPressed();
			if(dialog.cancelPressed()) return DONE;
		}
		return DOES_32;
	}

	@Override
	public void run(ImageProcessor imageProcessor) {
		if(myimp.getImageStackSize()>1){
			ImageStack is=new ImageStack(imageProcessor.getWidth()*2,imageProcessor.getHeight()*2);
			for (int plane=0;plane<myimp.getImageStackSize();plane++){
				is.addSlice(myimp.getImageStack().getSliceLabel(plane+1),upscale2D(myimp.getImageStack().getProcessor(plane+1)));
				if(do3D) {
					ImageProcessor tmp = new FloatProcessor(imageProcessor.getWidth() * 2, imageProcessor.getHeight() * 2);
					float[] pixels = (float[]) tmp.getPixels();
					Arrays.fill(pixels, Float.NaN);
					is.addSlice(myimp.getImageStack().getSliceLabel(plane+1),tmp);
				}
			}
			new ImagePlus(myimp.getTitle()+"upscaleX2",is).show();
		}else{
			new ImagePlus(myimp.getTitle()+"upscaleX2",upscale2D(imageProcessor)).show();
		}




	}

	public ImageProcessor upscale2D(ImageProcessor imageProcessor){
		ImageProcessor result=new FloatProcessor(imageProcessor.getWidth()*2,imageProcessor.getHeight()*2);
		float[] pixels=(float[])result.getPixels();
		Arrays.fill(pixels,Float.NaN);
		for (int y=0;y<imageProcessor.getHeight();y++){
			for(int x=0;x<imageProcessor.getWidth();x++){
				result.putPixelValue(x*2,y*2,imageProcessor.getPixelValue(x,y));
			}
		}
		return result;
	}
}
