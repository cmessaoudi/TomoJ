package fr.curie.filters;

import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.Arrays;

public class upscaleX2 implements PlugInFilter {
	ImagePlus myimp;
	@Override
	public int setup(String s, ImagePlus imagePlus) {
		myimp=imagePlus;
		return DOES_32;
	}

	@Override
	public void run(ImageProcessor imageProcessor) {

		ImageProcessor result=new FloatProcessor(imageProcessor.getWidth()*2,imageProcessor.getHeight()*2);
		float[] pixels=(float[])result.getPixels();
		Arrays.fill(pixels,Float.NaN);
		for (int y=0;y<imageProcessor.getHeight();y++){
			for(int x=0;x<imageProcessor.getWidth();x++){
				result.putPixelValue(x*2,y*2,imageProcessor.getPixelValue(x,y));
			}
		}

		new ImagePlus(myimp.getTitle()+"upscaleX2",result).show();

	}
}
