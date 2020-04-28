package fr.curie.filters;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

public class UpscaleX2_DCCI implements PlugInFilter {
    ImagePlus original;
    final static float[] weightVector = new float[]{-1 / 16f, 9 / 16f, 9 / 16f, -1 / 16f};

    public int setup(String s, ImagePlus imagePlus) {
        original = imagePlus;
        return DOES_32;
    }

    public void run(ImageProcessor imageProcessor) {
        if(original.getNSlices()==1){
            ImageProcessor ipres=DCCi(imageProcessor,5,1.15f);
            new ImagePlus(original.getTitle()+"_upscaledDCCI",ipres).show();
            return;
        }
        ImageStack result = DCCi3D(original.getImageStack(), 5 , 1.15f);
        new ImagePlus(original.getTitle()+"_upscaledDCCI",result).show();
    }

    public double[] directDetectionOfDCC(float[] neightborhood, int weightedExponent, float thresholdGradientRatio, int type){

        float d, d0 = 0f;

        if(type == 1) {

            /*
             *  diagonal detection 45 degree
             *    1   3   5   7
             *  1 * * x * x * x
             *    * * * * * * *
             *  3 x * x * x * x
             *    * * * * * * *
             *  5 x * x * x * x
             *    * * * * * * *
             *  7 x * x * x * *
             *
             * */
            float d1 = Math.abs(neightborhood[2] - neightborhood[14]);
            float d2 = Math.abs(neightborhood[4] - neightborhood[16]) + Math.abs(neightborhood[16] - neightborhood[28]);
            float d3 = Math.abs(neightborhood[6] - neightborhood[18]) + Math.abs(neightborhood[18] - neightborhood[30]) + Math.abs(neightborhood[30] - neightborhood[42]);
            float d4 = Math.abs(neightborhood[20] - neightborhood[32]) + Math.abs(neightborhood[32] - neightborhood[44]);
            float d5 = Math.abs(neightborhood[34] - neightborhood[46]);
            d = d1 + d2 + d3 + d4 + d5;

            /*
             *  diagonal detection 135 degree
             *    1   3   5   7
             *  1 x * x * x * *
             *    * * * * * * *
             *  3 x * x * x * x
             *    * * * * * * *
             *  5 x * x * x * x
             *    * * * * * * *
             *  7 * * x * x * x
             *
             * */
            d1 = Math.abs(neightborhood[28] - neightborhood[44]);
            d2 = Math.abs(neightborhood[14] - neightborhood[30]) + Math.abs(neightborhood[30] - neightborhood[46]);
            d3 = Math.abs(neightborhood[0] - neightborhood[16]) + Math.abs(neightborhood[16] - neightborhood[32]) + Math.abs(neightborhood[32] - neightborhood[48]);
            d4 = Math.abs(neightborhood[2] - neightborhood[18]) + Math.abs(neightborhood[18] - neightborhood[34]);
            d5 = Math.abs(neightborhood[4] - neightborhood[20]);
            d0 = d1 + d2 + d3 + d4 + d5;

        }
        else
        {
            /*
             *  horizontal detection 5x5
             *    1   3   5   7
             *  1 * x * x * * *
             *    x * x * x * *
             *  3 * x * x * * *
             *    x * x * x * *
             *  5 * x * x * * *
             *    * * * * * * *
             *  7 * * * * * * *
             *
             * */
            float h1 = Math.abs(neightborhood[7] - neightborhood[21]) + Math.abs(neightborhood[9] - neightborhood[23]) + Math.abs(neightborhood[11] - neightborhood[25]);
            float h2 = Math.abs(neightborhood[1] - neightborhood[15]) + Math.abs(neightborhood[15] - neightborhood[29]);
            float h3 = Math.abs(neightborhood[3] - neightborhood[17]) + Math.abs(neightborhood[17] - neightborhood[31]);


            d = h1 + h2 + h3;

            /*
             *  vertical detection 5x5
             *    1   3   5   7
             *  1 * x * x * * *
             *    x * x * x * *
             *  3 * x * x * * *
             *    x * x * x * *
             *  5 * x * x * * *
             *    * * * * * * *
             *  7 * * * * * * *
             *
             * */

            float v1 = Math.abs(neightborhood[1] - neightborhood[3]) + Math.abs(neightborhood[15] - neightborhood[17]) + Math.abs(neightborhood[29] - neightborhood[31]);
            float v2 = Math.abs(neightborhood[7] - neightborhood[9]) + Math.abs(neightborhood[9] - neightborhood[11]);
            float v3 = Math.abs(neightborhood[21] - neightborhood[23]) + Math.abs(neightborhood[23] - neightborhood[25]);
            d0 = v1 + v2 + v3;
        }

        //computation of the weight vector
        double w1 = 1+Math.pow(d, weightedExponent);
        double w2 = 1+Math.pow(d0, weightedExponent);


        //compute the directional index
        double n = 3;
        if((1+d)/(1+d0) > thresholdGradientRatio)
            n = 1;
        else if ((1+d0)/(1+d) > thresholdGradientRatio)
            n = 2;

        //return data
        double[] result = new double[]{1/w1, 1/w2, n};
        return result;
    }

    public float changePixelValue(float[] neightborhood, int type, double[] data)
    {
        double w1 = data[0];
        double w2 = data[1];
        double n = data [2];
        // definition of neightborhood
        float[] weightVector = new float[]{-1/16f, 9/16f, 9/16f, -1/16f};
        float[] vector1, vector2;


        if(type == 1)
        {
            //diagonal vectors  v2 \/ v1
            //                     /\
            vector1 = new float[]{neightborhood[6], neightborhood[18], neightborhood[30], neightborhood[42]};
            vector2 = new float[]{neightborhood[0], neightborhood[16], neightborhood[32], neightborhood[48]};
        }
        else
        {
            //horizontal vector
            vector1 = new float[]{neightborhood[3], neightborhood[17], neightborhood[31], neightborhood[45]};
            // vertical vector
            vector2 = new float[]{neightborhood[21], neightborhood[23], neightborhood[25], neightborhood[27]};

        }
        //computation of pixel value
        float pixelValue = 0;
        if( n == 1) {

            for (int i = 0; i < weightVector.length; i++) {
                pixelValue = pixelValue + (vector2[i] * weightVector[i]);
            }
        }
        else if (n == 2)
        {
            for(int i = 0; i < weightVector.length; i++)
            {
                pixelValue = pixelValue + (vector1[i]*weightVector[i]);
            }
        }
        else{
            float p1 = 0, p2 = 0;
            for(int i = 0; i < weightVector.length; i++)
            {
                p1 = p1 + (vector1[i]*weightVector[i]);
                p2 = p2 + (vector2[i]*weightVector[i]);
            }
            pixelValue = (float)(((w1*p1)+(w2*p2))/(w1+w2));

        }

        return pixelValue;
    }

    public ImageProcessor DCCi(ImageProcessor ip, int weightedExponent, float thresholdGradientRatio){
        //output image
        ImageProcessor ipDCC = ip.createProcessor(ip.getWidth()*2, ip.getHeight()*2);
        for(int i = 0; i < ipDCC.getWidth(); i=i+2) {
            for (int j = 0; j < ipDCC.getHeight(); j = j+2) {
                ipDCC.putPixelValue(i,j,ip.getPixelValue(i/2,j/2));
            }
        }

        //apply cubic convolution interpolation
        double[] resultDetection = new double[2];
        for(int i = 1; i < ipDCC.getWidth(); i=i+2)
        {
            for(int j = 1; j < ipDCC.getHeight(); j=j+2)
            {
                float[] patch = getNeightborhood(ipDCC,i,j,3);
                resultDetection = directDetectionOfDCC(patch, weightedExponent, thresholdGradientRatio, 1);
                ipDCC.putPixelValue(i,j,changePixelValue(patch, 1, resultDetection));
            }
        }

        for(int i = 0; i < ipDCC.getWidth(); i=i+2)
        {
            for(int j = 1; j < ipDCC.getHeight(); j=j+2)
            {
                float[] patch = getNeightborhood(ipDCC,i,j,3);
                resultDetection = directDetectionOfDCC(patch, weightedExponent, thresholdGradientRatio, 2);
                ipDCC.putPixelValue(i,j,changePixelValue(patch, 2, resultDetection));
            }
        }

        for(int i = 1; i < ipDCC.getWidth(); i=i+2)
        {
            for(int j = 0; j < ipDCC.getHeight(); j=j+2)
            {
                float[] patch = getNeightborhood(ipDCC,i,j,3);
                resultDetection = directDetectionOfDCC(patch, weightedExponent, thresholdGradientRatio, 3);
                ipDCC.putPixelValue(i,j,changePixelValue(patch, 3, resultDetection));
            }
        }
        return ipDCC;

    }
    // determination of image neightborhood patch size with radius = 3 (7x7), the center is the detected pixel
    public float[] getNeightborhood(ImageProcessor ip, int x, int y, int radius) {
        int diameter = radius * 2 + 1;
        float[] result = new float[diameter * diameter];
        int index = 0;
        int xj,yi;
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                xj=x+j;
                if(xj<0) xj=Math.abs(xj);
                if(xj>=ip.getWidth()) xj= ip.getWidth()-1 + xj-ip.getWidth()-1;
                yi=y+i;
                if(yi<0) yi=Math.abs(yi);
                if(yi>=ip.getHeight()) yi= ip.getHeight()-1 + yi-ip.getHeight()-1;
                result[index++] = ip.getPixelValue(xj, yi);
            }
        }
        return result;
    }



    public ImageStack DCCi3D(ImageStack stack, int weightedExponent, float thresholdGradientRatio) {
        //output image
        ImageStack isDCC = new ImageStack(stack.getWidth() * 2, stack.getHeight() * 2);
        float[] patch = new float[343];
        for (int z = 1; z <= stack.getSize(); z++) {
            ImageProcessor ip = stack.getProcessor(z);
            ImageProcessor ipDCC = ip.createProcessor(isDCC.getWidth(), isDCC.getHeight());
            for (int i = 0; i < ipDCC.getWidth(); i = i + 2) {
                for (int j = 0; j < ipDCC.getHeight(); j = j + 2) {
                    ipDCC.putPixelValue(i, j, ip.getPixelValue(i / 2, j / 2));
                }
            }
            isDCC.addSlice(ipDCC);
            isDCC.addSlice(ip.createProcessor(isDCC.getWidth(), isDCC.getHeight()));
        }
		ImagePlus imptmp=new ImagePlus("",isDCC);
        new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step0_.tif");

        //apply cubic convolution interpolation
        double[] resultDetection;
        int ndirectional=0;
        int nblur=0;
        int nbTestPatchFalse=0;
        float changeValue =0;
        long count=0;


        //diagonal 3D
        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue =0;
        count=0;
        System.out.println("first pass diagonal 3D");
        for (int z = 1; z < isDCC.getSize(); z += 2) {
            ImageProcessor ipDCC = isDCC.getProcessor(z + 1);
            for (int j = 1; j < ipDCC.getHeight(); j = j + 2) {
                for (int i = 1; i < ipDCC.getWidth(); i = i + 2) {
                    getNeighborhood3Dflat(isDCC, i, j, z, 3, patch);
                    if(!testPatch(patch,7,0,0,0)) nbTestPatchFalse++;
                    int type=0;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, type);
                    /*type=6;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, type);
                    if(resultDetection[resultDetection.length-1]==5){
                        type=4;
                        resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, type);
                    }
                    if(resultDetection[resultDetection.length-1]==5){
                        type=1;
                        resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, type);
                    }
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;  */
                    changeValue = changePixelValue3D(patch, type, resultDetection);
                    ipDCC.putPixelValue(i, j, changeValue);
                    count++;
                }
            }
        }
        System.gc();
        System.out.println("first pass diagonal 3D finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse);
        System.out.println("number of voxel treated: "+ count+" ("+(isDCC.getSize()/2*isDCC.getHeight()/2*isDCC.getWidth()/2)+")");
        new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step1_diag3D_.tif");



        //diagonal 2D XY
        System.out.println("second pass diagonal XY");

        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue=0;
        for (int z = 0; z < isDCC.getSize(); z += 2) {
            ImageProcessor ipDCC = isDCC.getProcessor(z + 1);
            for (int j = 1; j < ipDCC.getHeight(); j = j + 2) {
                for (int i = 1; i < ipDCC.getWidth(); i = i + 2) {
                    getNeighborhood3Dflat(isDCC, i, j, z, 3, patch);
                    if(!testPatch(patch,7,0,0,1)) nbTestPatchFalse++;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, 1);
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;
                    changeValue=changePixelValue3D(patch, 1, resultDetection);
                    ipDCC.putPixelValue(i, j, changeValue);
                }
            }
        }
        System.gc();
        System.out.println("second pass diagonal XY finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse);
		new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step2_diag2DXY.tif");

        //diagonal 2D XY
        System.out.println("third  pass diagonal XY plane +1");
        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue=0;

        for (int z = 1; z < isDCC.getSize(); z += 2) {
            ImageProcessor ipDCC = isDCC.getProcessor(z + 1);
            for (int j = 0; j < ipDCC.getHeight(); j = j + 2) {
                for (int i = 0; i < ipDCC.getWidth(); i = i + 2) {
                    int type=1;
                    getNeighborhood3Dflat(isDCC, i, j, z, 3, patch);
                    if(!testPatch(patch,7,0,0,1)) nbTestPatchFalse++;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, type);
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;
                    changeValue=changePixelValue3D(patch, type, resultDetection);
                    ipDCC.putPixelValue(i, j, changeValue);
                }
            }
        }
        System.gc();
        System.out.println("second pass diagonal XY plane +1 finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse);
        new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step2_diag2DXY.tif");

        //diagonal 2D XZ
        System.out.println("third pass diagonal XZ");
        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue=0;
        for(int z=1; z<isDCC.getSize();z +=2) {
            ImageProcessor ipDCC = isDCC.getProcessor(z+1);
            for (int j = 0; j < ipDCC.getHeight(); j = j + 2) {
                for (int i = 1; i < ipDCC.getWidth(); i = i + 2) {
                    getNeighborhood3Dflat(isDCC, i, j, z, 3, patch);
                    if(!testPatch(patch,7,1,0,0)) nbTestPatchFalse++;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, 2);
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;
                    //for(int index=0;index<resultDetection.length;index++) System.out.println(" "+resultDetection[index]+" ");
                    //System.out.flush();
                    //ipDCC.putPixelValue(i, j, 2000);
                    changeValue=changePixelValue3D(patch, 2, resultDetection);
                    //ipDCC.putPixelValue(i, j, changeValue);
                }
            }
        }
        System.gc();
        System.out.println("third pass diagonal XZ finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse);
		new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step3_diagXZ.tif");
        //diagonal 2D YZ
        System.out.println("fourth pass diagonal YZ ");
        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue=0;
        for(int z=1; z<isDCC.getSize();z +=2) {
            ImageProcessor ipDCC = isDCC.getProcessor(z+1);
            for (int j = 1; j < ipDCC.getHeight(); j = j + 2) {
                for (int i = 0; i < ipDCC.getWidth(); i = i + 2) {
                    getNeighborhood3Dflat(isDCC, i, j, z, 3, patch);
                    if(!testPatch(patch,7,0,1,0)) nbTestPatchFalse++;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, 3);
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;
                    //for(int index=0;index<resultDetection.length;index++) System.out.println(" "+resultDetection[index]+" ");
                    //System.out.flush();
                    changeValue=changePixelValue3D(patch, 3, resultDetection);
                    //ipDCC.putPixelValue(i, j, changeValue);
                }
            }
        }
        System.gc();
        System.out.println("fourth pass diagonal YZ finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse);
		new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step4_diagYZ.tif");





        //horizontal
        System.out.println("fifth pass horizontal X");
        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue=0;
        for(int z=0; z<isDCC.getSize();z +=1) {
            ImageProcessor ipDCC = isDCC.getProcessor(z+1);
            //System.out.println("z="+z);
            //System.out.flush();
            for (int j = 0; j < ipDCC.getHeight(); j = j + 2) {
                //System.out.println("y="+j);
                //System.out.flush();

                for (int i = 1; i < ipDCC.getWidth(); i = i + 2) {
                    int type=4;
                    getNeighborhood3Dflat(isDCC, i, j, z, 3, patch);
                    if(!testPatch(patch,7,1,0,1)) nbTestPatchFalse++;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, type);
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;
                    //System.out.println("x="+i+" , orientation="+resultDetection[resultDetection.length-1]);
                    //for(int index=0;index<resultDetection.length;index++) System.out.println(" "+resultDetection[index]+" ");
                    //System.out.flush();
                    changeValue=changePixelValue3D(patch, type, resultDetection);
                    ipDCC.putPixelValue(i, j,changeValue );
                    //ipDCC.putPixelValue(i, j, 10);
                }
            }
        }


        System.gc();
        System.out.println("fifth pass horizontal X finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse);
		new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step5_hor_.tif");
        //vertical
        System.out.println("sixth pass vertical Y");
        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue=0;
        for(int z=0; z<isDCC.getSize();z +=1) {
            ImageProcessor ipDCC = isDCC.getProcessor(z+1);
            for (int j = 1; j < ipDCC.getHeight(); j = j + 2) {
                for (int i = 0; i < ipDCC.getWidth(); i = i + 2) {
                    int type=5;
                    getNeighborhood3Dflat(isDCC, i, j, z, 3, patch);
                    if(!testPatch(patch,7,0,1,1)) nbTestPatchFalse++;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, type);
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;
                    changeValue=changePixelValue3D(patch, type, resultDetection) ;
                    ipDCC.putPixelValue(i, j, changeValue);
                }
            }
        }
        System.gc();
        System.out.println("sixth pass vertical Y finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse);
		new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step6_vert.tif");
        //depth
        System.out.println("seventh pass depth Z ");
        ndirectional=0;
        nblur=0;
        nbTestPatchFalse=0;
        changeValue=7;
        for(int z=1; z<isDCC.getSize();z +=1) {
            ImageProcessor ipDCC = isDCC.getProcessor(z+1);
            for (int j = 0; j < ipDCC.getHeight(); j = j + 2) {
                for (int i = 0; i < ipDCC.getWidth(); i = i + 2) {
                    getNeighborhood3Dflat(isDCC, i, j, z, 3,patch);
                    if(!testPatch(patch,7,0,0,0)) nbTestPatchFalse++;
                    resultDetection = directDetectionOfDCC3D(patch, weightedExponent, thresholdGradientRatio, 6);
                    if(resultDetection[resultDetection.length-1]==5) nblur++; else ndirectional++;
                    //resultDetection[resultDetection.length-1]=1;
                    //changeValue=changePixelValue3D(patch, 6, resultDetection);
                    //ipDCC.putPixelValue(i, j, changeValue);
                }
            }
        }
        System.out.println("seventh pass depth Z finished");
        System.out.println("number directional: "+ndirectional+", area: "+nblur);
        System.out.println("number of incorrectPatches: "+ nbTestPatchFalse); /**/
		new FileSaver(imptmp).saveAsTiffStack("C:\\progs\\test_data\\DCCI_step7_depth.tif");

        return isDCC;

    }

    public float[] getNeighborhood3Dflat(ImageStack stack, int x, int y, int z, int radius, float[] result) {
        int diameter = radius * 2 + 1;
        //float[][][] result = new float[diameter][diameter][diameter];
        for (int k = -radius; k <= radius; k++) {
            //int zindex = Math.max(0, Math.min(stack.getSize() - 1, z + k));
            int zindex=Math.abs(z+k);
            if(zindex>=stack.getSize()) zindex=stack.getSize()-1 - (zindex - (stack.getSize()-1));
            ImageProcessor ip = stack.getProcessor(zindex + 1);
            for (int j = -radius; j <= radius; j++) {
                //int yindex = Math.max(0, Math.min(ip.getHeight() - 1, y + j));
                int yindex=Math.abs(y+j);
                if(yindex >= ip.getHeight()) yindex = ip.getHeight() -1 -( yindex -(ip.getHeight()-1));
                for (int i = -radius; i <= radius; i++) {
                    //int xindex = Math.max(0, Math.min(ip.getWidth() - 1, x + i));
                    int xindex = Math.abs(x+i);
                    if (xindex>=ip.getWidth()) xindex= ip.getWidth() -1 - (xindex -(ip.getWidth()-1));
                    try {
                        setValueInPatch(result, diameter, i + radius, j + radius, k + radius, ip.getPixelValue(xindex, yindex));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //result[i+radius][j+radius][k+radius] = ip.getPixelValue(xindex, yindex);
                }
            }
        }
        return result;
    }

    public boolean testPatch(float[] patch, int sizePatch, int startX,int startY, int startZ){
        boolean result=true;
        int indexz,indexy;

        for(int z=startZ;z<sizePatch;z+=2){
            indexz=z*sizePatch*sizePatch;
            for(int y=startY;y<sizePatch;y+=2) {
                indexy = y*sizePatch;
                for (int x = startX; x < sizePatch; x+=2) {
                    if(patch[indexz+indexy+x]==0) return false;
                }
            }

        }
        return result;
    }

    public float getValueInPatch(float[] patch, int sizePatch, int x, int y, int z) {
        return patch[sizePatch * sizePatch * z + sizePatch * y + x];
    }

    public void setValueInPatch(float[] patch, int sizePatch, int x, int y, int z, float value) {
        patch[sizePatch * sizePatch * z + sizePatch * y + x] = value;
    }

    public double[] directDetectionOfDCC3D(float[] patch, int weightedExponent, float thresholdGradientRatio, int type) {
        //type 0 3D diagonal
        double d1 = Double.NaN, d2 = Double.NaN, d3 = Double.NaN, d4 = Double.NaN;
        if (type == 0) {  //diagonal 3D
            // first diagonal   (x+1, y+1, z+1)
            d1 = Math.abs(getValueInPatch(patch, 7, 0, 0, 0) - getValueInPatch(patch, 7, 2, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 2) - getValueInPatch(patch, 7, 4, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 4) - getValueInPatch(patch, 7, 6, 6, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 2, 0) - getValueInPatch(patch, 7, 2, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 2) - getValueInPatch(patch, 7, 4, 6, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 4, 0) - getValueInPatch(patch, 7, 2, 6, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 2, 0, 0) - getValueInPatch(patch, 7, 4, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 2) - getValueInPatch(patch, 7, 6, 4, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 4, 0, 0) - getValueInPatch(patch, 7, 6, 2, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 2, 2, 0) - getValueInPatch(patch, 7, 4, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 2) - getValueInPatch(patch, 7, 6, 6, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 2, 4, 0) - getValueInPatch(patch, 7, 4, 6, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 4, 2, 0) - getValueInPatch(patch, 7, 6, 4, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 4, 4, 0) - getValueInPatch(patch, 7, 6, 6, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 0, 2) - getValueInPatch(patch, 7, 2, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 4) - getValueInPatch(patch, 7, 4, 4, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 0, 4) - getValueInPatch(patch, 7, 2, 2, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 2, 2) - getValueInPatch(patch, 7, 2, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 4) - getValueInPatch(patch, 7, 4, 6, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 2, 4) - getValueInPatch(patch, 7, 2, 4, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 4, 2) - getValueInPatch(patch, 7, 2, 6, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 0, 4, 4) - getValueInPatch(patch, 7, 2, 6, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 2, 0, 2) - getValueInPatch(patch, 7, 4, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 4) - getValueInPatch(patch, 7, 6, 4, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 2, 0, 4) - getValueInPatch(patch, 7, 4, 2, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 4, 0, 2) - getValueInPatch(patch, 7, 6, 2, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 4, 0, 4) - getValueInPatch(patch, 7, 6, 2, 6));

            //second diagonal   (x-1, y+1, z+1)
            d2 = Math.abs(getValueInPatch(patch, 7, 6, 0, 0) - getValueInPatch(patch, 7, 4, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 2) - getValueInPatch(patch, 7, 2, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 4) - getValueInPatch(patch, 7, 0, 6, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 0, 2) - getValueInPatch(patch, 7, 4, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 4) - getValueInPatch(patch, 7, 2, 4, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 0, 4) - getValueInPatch(patch, 7, 4, 2, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 2, 0) - getValueInPatch(patch, 7, 4, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 2) - getValueInPatch(patch, 7, 2, 6, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 2, 2) - getValueInPatch(patch, 7, 4, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 4) - getValueInPatch(patch, 7, 2, 6, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 2, 4) - getValueInPatch(patch, 7, 4, 4, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 4, 0) - getValueInPatch(patch, 7, 4, 6, 2));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 4, 2) - getValueInPatch(patch, 7, 4, 6, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 6, 4, 4) - getValueInPatch(patch, 7, 4, 6, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 0, 0) - getValueInPatch(patch, 7, 2, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 2) - getValueInPatch(patch, 7, 0, 4, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 0, 2) - getValueInPatch(patch, 7, 2, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 4) - getValueInPatch(patch, 7, 0, 4, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 0, 4) - getValueInPatch(patch, 7, 2, 2, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 0, 0) - getValueInPatch(patch, 7, 0, 2, 2));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 0, 2) - getValueInPatch(patch, 7, 0, 2, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 0, 4) - getValueInPatch(patch, 7, 0, 2, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 2, 0) - getValueInPatch(patch, 7, 2, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 2) - getValueInPatch(patch, 7, 2, 6, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 2, 0) - getValueInPatch(patch, 7, 0, 4, 2));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 4, 0) - getValueInPatch(patch, 7, 2, 6, 2));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 4, 0) - getValueInPatch(patch, 7, 0, 6, 2));

            //third diagonal  (x+1, y-1, z+1)
            d3 = Math.abs(getValueInPatch(patch, 7, 0, 6, 0) - getValueInPatch(patch, 7, 2, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 2) - getValueInPatch(patch, 7, 4, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 4) - getValueInPatch(patch, 7, 6, 0, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 4, 0) - getValueInPatch(patch, 7, 2, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 2) - getValueInPatch(patch, 7, 4, 0, 4));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 2, 0) - getValueInPatch(patch, 7, 2, 0, 2));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 6, 2) - getValueInPatch(patch, 7, 2, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 4) - getValueInPatch(patch, 7, 4, 2, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 4, 2) - getValueInPatch(patch, 7, 2, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 4) - getValueInPatch(patch, 7, 4, 0, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 2, 2) - getValueInPatch(patch, 7, 2, 0, 4));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 6, 4) - getValueInPatch(patch, 7, 2, 4, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 4, 4) - getValueInPatch(patch, 7, 2, 2, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 0, 2, 4) - getValueInPatch(patch, 7, 2, 0, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 2, 6, 0) - getValueInPatch(patch, 7, 4, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 2) - getValueInPatch(patch, 7, 6, 2, 4));
            d3 += Math.abs(getValueInPatch(patch, 7, 2, 6, 2) - getValueInPatch(patch, 7, 4, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 4) - getValueInPatch(patch, 7, 6, 2, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 2, 6, 4) - getValueInPatch(patch, 7, 4, 4, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 4, 6, 0) - getValueInPatch(patch, 7, 6, 4, 2));
            d3 += Math.abs(getValueInPatch(patch, 7, 4, 6, 2) - getValueInPatch(patch, 7, 6, 4, 4));
            d3 += Math.abs(getValueInPatch(patch, 7, 4, 6, 4) - getValueInPatch(patch, 7, 6, 4, 6));
            d3 += Math.abs(getValueInPatch(patch, 7, 2, 4, 0) - getValueInPatch(patch, 7, 4, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 2) - getValueInPatch(patch, 7, 6, 0, 4));
            d3 += Math.abs(getValueInPatch(patch, 7, 2, 2, 0) - getValueInPatch(patch, 7, 4, 0, 2));
            d3 += Math.abs(getValueInPatch(patch, 7, 4, 4, 0) - getValueInPatch(patch, 7, 6, 2, 2));
            d3 += Math.abs(getValueInPatch(patch, 7, 4, 2, 0) - getValueInPatch(patch, 7, 6, 0, 2));

            //fourth diagonal  (x-1, y-1, z+1)
            d4 = Math.abs(getValueInPatch(patch, 7, 6, 6, 0) - getValueInPatch(patch, 7, 4, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 2) - getValueInPatch(patch, 7, 2, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 4) - getValueInPatch(patch, 7, 0, 0, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 6, 2) - getValueInPatch(patch, 7, 4, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 4) - getValueInPatch(patch, 7, 2, 2, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 6, 4) - getValueInPatch(patch, 7, 4, 4, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 4, 0) - getValueInPatch(patch, 7, 4, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 2) - getValueInPatch(patch, 7, 2, 0, 4));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 4, 2) - getValueInPatch(patch, 7, 4, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 4) - getValueInPatch(patch, 7, 2, 0, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 4, 4) - getValueInPatch(patch, 7, 4, 2, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 2, 0) - getValueInPatch(patch, 7, 4, 0, 2));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 2, 2) - getValueInPatch(patch, 7, 4, 0, 4));
            d4 += Math.abs(getValueInPatch(patch, 7, 6, 2, 4) - getValueInPatch(patch, 7, 4, 0, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 4, 6, 0) - getValueInPatch(patch, 7, 2, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 2) - getValueInPatch(patch, 7, 0, 2, 4));
            d4 += Math.abs(getValueInPatch(patch, 7, 4, 6, 2) - getValueInPatch(patch, 7, 2, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 4) - getValueInPatch(patch, 7, 0, 2, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 4, 6, 4) - getValueInPatch(patch, 7, 2, 4, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 2, 6, 0) - getValueInPatch(patch, 7, 0, 4, 2));
            d4 += Math.abs(getValueInPatch(patch, 7, 2, 6, 2) - getValueInPatch(patch, 7, 0, 4, 4));
            d4 += Math.abs(getValueInPatch(patch, 7, 2, 6, 4) - getValueInPatch(patch, 7, 0, 4, 6));
            d4 += Math.abs(getValueInPatch(patch, 7, 4, 4, 0) - getValueInPatch(patch, 7, 2, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 2) - getValueInPatch(patch, 7, 0, 0, 4));
            d4 += Math.abs(getValueInPatch(patch, 7, 4, 2, 0) - getValueInPatch(patch, 7, 2, 0, 2));
            d4 += Math.abs(getValueInPatch(patch, 7, 2, 4, 0) - getValueInPatch(patch, 7, 0, 2, 2));
            d4 += Math.abs(getValueInPatch(patch, 7, 2, 2, 0) - getValueInPatch(patch, 7, 0, 0, 2));
        } else if (type == 1) {  //diagonal 2D plane   XY
            //first diagonal
            d1 = Math.abs(getValueInPatch(patch, 7, 2, 0, 3) - getValueInPatch(patch, 7, 0, 2, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 4, 0, 3) - getValueInPatch(patch, 7, 2, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 3) - getValueInPatch(patch, 7, 0, 4, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 6, 0, 3) - getValueInPatch(patch, 7, 4, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 3) - getValueInPatch(patch, 7, 2, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 3) - getValueInPatch(patch, 7, 0, 6, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 6, 2, 3) - getValueInPatch(patch, 7, 4, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 3) - getValueInPatch(patch, 7, 2, 6, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 6, 4, 3) - getValueInPatch(patch, 7, 4, 6, 3));
            d1/=9;
            //second diagonal
            d2 = Math.abs(getValueInPatch(patch, 7, 0, 4, 3) - getValueInPatch(patch, 7, 2, 6, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 0, 2, 3) - getValueInPatch(patch, 7, 2, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 4, 3) - getValueInPatch(patch, 7, 4, 6, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 0, 0, 3) - getValueInPatch(patch, 7, 2, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 2, 3) - getValueInPatch(patch, 7, 4, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 4, 3) - getValueInPatch(patch, 7, 6, 6, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 0, 3) - getValueInPatch(patch, 7, 4, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 2, 3) - getValueInPatch(patch, 7, 6, 4, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 0, 3) - getValueInPatch(patch, 7, 6, 2, 3));
            d2/=9;

            d3=Double.NaN;

            d4 = Double.NaN;
        } else if (type == 2) { //plane 2D XZ
            //first diagonal   row
            d1 = Math.abs(getValueInPatch(patch, 7, 2, 3, 0) - getValueInPatch(patch, 7, 0, 3, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 4, 3, 0) - getValueInPatch(patch, 7, 2, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 2) - getValueInPatch(patch, 7, 0, 3, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 6, 3, 0) - getValueInPatch(patch, 7, 4, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 2) - getValueInPatch(patch, 7, 2, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 4) - getValueInPatch(patch, 7, 0, 3, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 6, 3, 2) - getValueInPatch(patch, 7, 4, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 4) - getValueInPatch(patch, 7, 2, 3, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 6, 3, 4) - getValueInPatch(patch, 7, 4, 3, 6));

            //second diagonal     row
            d2 = Math.abs(getValueInPatch(patch, 7, 0, 3, 4) - getValueInPatch(patch, 7, 2, 3, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 0, 3, 2) - getValueInPatch(patch, 7, 2, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 4) - getValueInPatch(patch, 7, 4, 3, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 0, 3, 0) - getValueInPatch(patch, 7, 2, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 2) - getValueInPatch(patch, 7, 4, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 4) - getValueInPatch(patch, 7, 6, 3, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 3, 0) - getValueInPatch(patch, 7, 4, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 2) - getValueInPatch(patch, 7, 6, 3, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 3, 0) - getValueInPatch(patch, 7, 6, 3, 2));

            d3 = Double.NaN;
            d4 = Double.NaN;

        } else if (type == 3) {// diagonal 2D YZ
            //third diagonal  col
            d1 = Math.abs(getValueInPatch(patch, 7, 3, 2, 0) - getValueInPatch(patch, 7, 3, 0, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 3, 4, 0) - getValueInPatch(patch, 7, 3, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 2) - getValueInPatch(patch, 7, 3, 0, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 3, 6, 0) - getValueInPatch(patch, 7, 3, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 2) - getValueInPatch(patch, 7, 3, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 4) - getValueInPatch(patch, 7, 3, 0, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 3, 6, 2) - getValueInPatch(patch, 7, 3, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 4) - getValueInPatch(patch, 7, 3, 2, 6));
            d1 += Math.abs(getValueInPatch(patch, 7, 3, 6, 4) - getValueInPatch(patch, 7, 3, 4, 6));

            //forth diagonal  col
            d2 = Math.abs(getValueInPatch(patch, 7, 3, 4, 0) - getValueInPatch(patch, 7, 3, 6, 2));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 2, 0) - getValueInPatch(patch, 7, 3, 4, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 2) - getValueInPatch(patch, 7, 3, 6, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 0, 0) - getValueInPatch(patch, 7, 3, 2, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 2) - getValueInPatch(patch, 7, 3, 4, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 4) - getValueInPatch(patch, 7, 3, 6, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 0, 2) - getValueInPatch(patch, 7, 3, 2, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 4) - getValueInPatch(patch, 7, 3, 4, 6));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 0, 4) - getValueInPatch(patch, 7, 3, 2, 6));

            d3 = Double.NaN;
            d4 = Double.NaN;

        } else if (type == 4) {  // horizontal
            //horizontal
            d1 = Math.abs(getValueInPatch(patch, 7, 2, 1, 3) - getValueInPatch(patch, 7, 4, 1, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 3) - getValueInPatch(patch, 7, 4, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 5, 3) - getValueInPatch(patch, 7, 4, 5, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 2, 3) - getValueInPatch(patch, 7, 3, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 3) - getValueInPatch(patch, 7, 5, 2, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 4, 3) - getValueInPatch(patch, 7, 3, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 3) - getValueInPatch(patch, 7, 5, 4, 3));
            //d1 += Math.abs(getValueInPatch(patch, 7, 1, 3, 2) - getValueInPatch(patch, 7, 3, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 2) - getValueInPatch(patch, 7, 5, 3, 2));
            //d1 += Math.abs(getValueInPatch(patch, 7, 2, 3, 1) - getValueInPatch(patch, 7, 4, 3, 1));
            //d1 += Math.abs(getValueInPatch(patch, 7, 1, 3, 4) - getValueInPatch(patch, 7, 3, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 4) - getValueInPatch(patch, 7, 5, 3, 4));
            //d1 += Math.abs(getValueInPatch(patch, 7, 2, 3, 5) - getValueInPatch(patch, 7, 4, 3, 5));
            //vertical
            d2 = Math.abs(getValueInPatch(patch, 7, 1, 2, 3) - getValueInPatch(patch, 7, 1, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 3) - getValueInPatch(patch, 7, 3, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 5, 2, 3) - getValueInPatch(patch, 7, 5, 4, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 1, 3) - getValueInPatch(patch, 7, 2, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 3) - getValueInPatch(patch, 7, 2, 5, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 1, 3) - getValueInPatch(patch, 7, 4, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 3) - getValueInPatch(patch, 7, 4, 5, 3));
            //d2 += Math.abs(getValueInPatch(patch, 7, 3, 1, 2) - getValueInPatch(patch, 7, 3, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 2) - getValueInPatch(patch, 7, 3, 5, 2));
           // d2 += Math.abs(getValueInPatch(patch, 7, 3, 2, 1) - getValueInPatch(patch, 7, 3, 4, 1));
            //d2 += Math.abs(getValueInPatch(patch, 7, 3, 1, 4) - getValueInPatch(patch, 7, 3, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 4) - getValueInPatch(patch, 7, 3, 5, 4));
            //d2 += Math.abs(getValueInPatch(patch, 7, 3, 2, 5) - getValueInPatch(patch, 7, 3, 4, 5));
            d3 = Double.NaN;
            d4 = Double.NaN;
        } else if (type == 5) {  //vertical
            //horizontal
            d1 = Math.abs(getValueInPatch(patch, 7, 2, 1, 3) - getValueInPatch(patch, 7, 4, 1, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 3) - getValueInPatch(patch, 7, 4, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 5, 3) - getValueInPatch(patch, 7, 4, 5, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 2, 3) - getValueInPatch(patch, 7, 3, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 3) - getValueInPatch(patch, 7, 5, 2, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 4, 3) - getValueInPatch(patch, 7, 3, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 3) - getValueInPatch(patch, 7, 5, 4, 3));
            //d1 += Math.abs(getValueInPatch(patch, 7, 1, 3, 2) - getValueInPatch(patch, 7, 3, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 2) - getValueInPatch(patch, 7, 5, 3, 2));
            //d1 += Math.abs(getValueInPatch(patch, 7, 2, 3, 1) - getValueInPatch(patch, 7, 4, 3, 1));
            //d1 += Math.abs(getValueInPatch(patch, 7, 1, 3, 4) - getValueInPatch(patch, 7, 3, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 4) - getValueInPatch(patch, 7, 5, 3, 4));
            //d1 += Math.abs(getValueInPatch(patch, 7, 2, 3, 5) - getValueInPatch(patch, 7, 4, 3, 5));
            //vertical
            d2 = Math.abs(getValueInPatch(patch, 7, 1, 2, 3) - getValueInPatch(patch, 7, 1, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 3) - getValueInPatch(patch, 7, 3, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 5, 2, 3) - getValueInPatch(patch, 7, 5, 4, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 1, 3) - getValueInPatch(patch, 7, 2, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 3) - getValueInPatch(patch, 7, 2, 5, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 1, 3) - getValueInPatch(patch, 7, 4, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 3) - getValueInPatch(patch, 7, 4, 5, 3));
            //d2 += Math.abs(getValueInPatch(patch, 7, 3, 1, 2) - getValueInPatch(patch, 7, 3, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 2) - getValueInPatch(patch, 7, 3, 5, 2));
            //d2 += Math.abs(getValueInPatch(patch, 7, 3, 2, 1) - getValueInPatch(patch, 7, 3, 4, 1));
            //d2 += Math.abs(getValueInPatch(patch, 7, 3, 1, 4) - getValueInPatch(patch, 7, 3, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 4) - getValueInPatch(patch, 7, 3, 5, 4));
            //d2 += Math.abs(getValueInPatch(patch, 7, 3, 2, 5) - getValueInPatch(patch, 7, 3, 4, 5));
            d3 = Double.NaN;
            d4 = Double.NaN;
        } else { //depth
            //horizontal
            d1 = Math.abs(getValueInPatch(patch, 7, 2, 1, 3) - getValueInPatch(patch, 7, 4, 1, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 3) - getValueInPatch(patch, 7, 4, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 5, 3) - getValueInPatch(patch, 7, 4, 5, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 2, 3) - getValueInPatch(patch, 7, 3, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 3) - getValueInPatch(patch, 7, 5, 2, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 4, 3) - getValueInPatch(patch, 7, 3, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 3) - getValueInPatch(patch, 7, 5, 4, 3));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 3, 2) - getValueInPatch(patch, 7, 3, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 2) - getValueInPatch(patch, 7, 5, 3, 2));
            d1 += Math.abs(getValueInPatch(patch, 7, 2, 3, 1) - getValueInPatch(patch, 7, 4, 3, 1));
            d1 += Math.abs(getValueInPatch(patch, 7, 1, 3, 4) - getValueInPatch(patch, 7, 3, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 4) - getValueInPatch(patch, 7, 5, 3, 4));
            d1 += Math.abs(getValueInPatch(patch, 7, 2, 3, 5) - getValueInPatch(patch, 7, 4, 3, 5));
            //vertical
            d2 = Math.abs(getValueInPatch(patch, 7, 1, 2, 3) - getValueInPatch(patch, 7, 1, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 3) - getValueInPatch(patch, 7, 3, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 5, 2, 3) - getValueInPatch(patch, 7, 5, 4, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 2, 1, 3) - getValueInPatch(patch, 7, 2, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 3) - getValueInPatch(patch, 7, 2, 5, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 4, 1, 3) - getValueInPatch(patch, 7, 4, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 3) - getValueInPatch(patch, 7, 4, 5, 3));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 1, 2) - getValueInPatch(patch, 7, 3, 3, 2)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 2) - getValueInPatch(patch, 7, 3, 5, 2));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 2, 1) - getValueInPatch(patch, 7, 3, 4, 1));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 1, 4) - getValueInPatch(patch, 7, 3, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 4) - getValueInPatch(patch, 7, 3, 5, 4));
            d2 += Math.abs(getValueInPatch(patch, 7, 3, 2, 5) - getValueInPatch(patch, 7, 3, 4, 5));
            //depth
            d3 = Math.abs(getValueInPatch(patch, 7, 3, 1, 2) - getValueInPatch(patch, 7, 3, 1, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 3, 2) - getValueInPatch(patch, 7, 3, 3, 4)) + Math.abs(getValueInPatch(patch, 7, 3, 5, 2) - getValueInPatch(patch, 7, 3, 5, 4));
            d3 += Math.abs(getValueInPatch(patch, 7, 3, 2, 1) - getValueInPatch(patch, 7, 3, 2, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 2, 3) - getValueInPatch(patch, 7, 3, 2, 5));
            d3 += Math.abs(getValueInPatch(patch, 7, 3, 4, 1) - getValueInPatch(patch, 7, 3, 4, 3)) + Math.abs(getValueInPatch(patch, 7, 3, 4, 3) - getValueInPatch(patch, 7, 3, 4, 5));
            d3 += Math.abs(getValueInPatch(patch, 7, 2, 3, 1) - getValueInPatch(patch, 7, 2, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 2, 3, 3) - getValueInPatch(patch, 7, 2, 3, 5));
            d3 += Math.abs(getValueInPatch(patch, 7, 1, 3, 2) - getValueInPatch(patch, 7, 1, 3, 4));
            d3 += Math.abs(getValueInPatch(patch, 7, 4, 3, 1) - getValueInPatch(patch, 7, 4, 3, 3)) + Math.abs(getValueInPatch(patch, 7, 4, 3, 3) - getValueInPatch(patch, 7, 4, 3, 5));
            d3 += Math.abs(getValueInPatch(patch, 7, 5, 3, 2) - getValueInPatch(patch, 7, 5, 3, 4));

            //d3 = Double.NaN;
            d4 = Double.NaN;
        }

        //computation of the weight vector
        double w1 = 1 + Math.pow(d1, weightedExponent);
        double w2 = 1 + Math.pow(d2, weightedExponent);
        double w3 = (Double.isNaN(d3)) ? 0 : 1 + Math.pow(d3, weightedExponent);
        double w4 = (Double.isNaN(d4)) ? 0 : 1 + Math.pow(d4, weightedExponent);
        double[] result;

        //compute the directional index
        double n = 5;
        if (Double.isNaN(d3)) {
            if ((1 + d1) / (1 + d2) > thresholdGradientRatio)
                n = 2;
            else if ((1 + d2) / (1 + d1) > thresholdGradientRatio)
                n = 1;
            result = new double[]{1 / w1, 1 / w2, 0, 0, n};
        } else if (Double.isNaN(d4)) {
            double d12 = (1 + d1) / (1 + d2);
            double d13 = (1 + d1) / (1 + d3);
            double d21 = (1 + d2) / (1 + d1);
            double d23 = (1 + d2) / (1 + d3);
            double d31 = (1 + d3) / (1 + d1);
            double d32 = (1 + d3) / (1 + d2);

            double dmax = Math.max(Math.max(d12, d13), Math.max(Math.max(d21, d23), Math.max(d31, d32)));
            if (dmax > thresholdGradientRatio) {
                if (dmax == d12 || dmax == d32) n = 2;
                else if (dmax == d21 || dmax == d31) n = 1;
                else n = 3;
            }
            result = new double[]{1 / w1, 1 / w2, 1 / w3, 0, n};
        } else {
            double d12 = (1 + d1) / (1 + d2);
            double d13 = (1 + d1) / (1 + d3);
            double d14 = (1 + d1) / (1 + d4);
            double d21 = (1 + d2) / (1 + d1);
            double d23 = (1 + d2) / (1 + d3);
            double d24 = (1 + d2) / (1 + d4);
            double d31 = (1 + d3) / (1 + d1);
            double d32 = (1 + d3) / (1 + d2);
            double d34 = (1 + d3) / (1 + d4);
            double d41 = (1 + d4) / (1 + d1);
            double d42 = (1 + d4) / (1 + d2);
            double d43 = (1 + d4) / (1 + d3);

            double dmax = Math.max(Math.max(Math.max(d12, Math.max(d13,d14)), Math.max(Math.max(d21, Math.max(d23,d24)), Math.max(d31, Math.max(d32,d34)))),Math.max(Math.max(d41,d42),d43));
            if (dmax > thresholdGradientRatio) {
                if (dmax > thresholdGradientRatio) {
                    if (dmax == d21 || dmax == d31 || dmax == d41) n = 1;
                    else if (dmax == d12 || dmax == d32 || dmax == d42) n = 2;
                    else if (dmax == d13 || dmax == d23 || dmax == d43) n = 3;
                    else n = 4;
                }
            }
            result = new double[]{1 / w1, 1 / w2, 1 / w3, 1 / w4, n};
        }
        //return data
        return result;
    }

    public float changePixelValue3D(float[] patch, int type, double[] data) {
        double w1 = data[0];
        double w2 = data[1];
        double w3 = data[2];
        double w4 = data[3];
        double n = data[4];
        // definition of neightborhood
        //float[] weightVector = new float[]{-1/16f, 9/16f, 9/16f, -1/16f};
        float[] vector1, vector2, vector3, vector4;

        if (type == 0) {   // diagonal 3D
            vector1 = new float[]{getValueInPatch(patch, 7, 0, 0, 0), getValueInPatch(patch, 7, 2, 2, 2), getValueInPatch(patch, 7, 4, 4, 4), getValueInPatch(patch, 7, 6, 6, 6)};
            vector2 = new float[]{getValueInPatch(patch, 7, 6, 0, 0), getValueInPatch(patch, 7, 4, 2, 2), getValueInPatch(patch, 7, 2, 4, 4), getValueInPatch(patch, 7, 0, 6, 6)};
            vector3 = new float[]{getValueInPatch(patch, 7, 0, 6, 0), getValueInPatch(patch, 7, 2, 4, 2), getValueInPatch(patch, 7, 4, 2, 4), getValueInPatch(patch, 7, 6, 0, 6)};
            vector4 = new float[]{getValueInPatch(patch, 7, 6, 6, 0), getValueInPatch(patch, 7, 4, 4, 2), getValueInPatch(patch, 7, 2, 2, 4), getValueInPatch(patch, 7, 0, 0, 6)};
            if(n!=5) System.out.println("w1="+w1+"w2="+w2+"w3="+w3+"w4="+w4+",n="+n);
            //n=5;
        } else if (type == 1) {// diagonal 2D XY
            //diagonal vectors  v2 \/ v1
            //                     /\
            vector1 = new float[]{getValueInPatch(patch, 7, 6, 0, 3), getValueInPatch(patch, 7, 4, 2, 3), getValueInPatch(patch, 7, 2, 4, 3), getValueInPatch(patch, 7, 0, 6, 3)};
            vector2 = new float[]{getValueInPatch(patch, 7, 0, 0, 3), getValueInPatch(patch, 7, 2, 2, 3), getValueInPatch(patch, 7, 4, 4, 3), getValueInPatch(patch, 7, 6, 6, 3)};
            vector3 = null;
            vector4 = null;
        } else if (type == 2) { //diagonal 2D depth
            vector1 = new float[]{getValueInPatch(patch, 7, 6, 3, 0), getValueInPatch(patch, 7, 4, 3, 2), getValueInPatch(patch, 7, 2, 3, 4), getValueInPatch(patch, 7, 0, 3, 6)};
            vector2 = new float[]{getValueInPatch(patch, 7, 0, 3, 0), getValueInPatch(patch, 7, 2, 3, 2), getValueInPatch(patch, 7, 4, 3, 4), getValueInPatch(patch, 7, 6, 3, 6)};
            vector3 = null;
            vector4 = null;
            //System.out.println("type "+type+" n=" + n);
            //System.out.println("vector1=["+vector1[0]+", "+vector1[1]+", "+vector1[2]+", "+vector1[3]+"]");
            //System.out.println("vector2=["+vector2[0]+", "+vector2[1]+", "+vector2[2]+", "+vector2[3]+"]");
            //System.out.println("vector3=["+vector3[0]+", "+vector3[1]+", "+vector3[2]+", "+vector3[3]+"]");
            //System.out.flush();
        } else if (type == 3) {
            vector1 = new float[]{getValueInPatch(patch, 7, 3, 0, 6), getValueInPatch(patch, 7, 3, 2, 4), getValueInPatch(patch, 7, 3, 4, 2), getValueInPatch(patch, 7, 3, 6, 0)};
            vector2 = new float[]{getValueInPatch(patch, 7, 3, 0, 0), getValueInPatch(patch, 7, 3, 2, 2), getValueInPatch(patch, 7, 3, 4, 4), getValueInPatch(patch, 7, 3, 6, 6)};
            vector3 = null;
            vector4 = null;

        } else if (type == 4) { //horizontal
            //horizontal vector
            vector1 = new float[]{getValueInPatch(patch, 7, 0, 3, 3), getValueInPatch(patch, 7, 2, 3, 3), getValueInPatch(patch, 7, 4, 3, 3), getValueInPatch(patch, 7, 6, 3, 3)};
            // vertical vector
            vector2 = new float[]{getValueInPatch(patch, 7, 3, 0, 3), getValueInPatch(patch, 7, 3, 2, 3), getValueInPatch(patch, 7, 3, 4, 3), getValueInPatch(patch, 7, 3, 6, 3)};
            // depth vector
            //vector3 = new float[]{getValueInPatch(patch, 7, 3, 3, 0), getValueInPatch(patch, 7, 3, 3, 2), getValueInPatch(patch, 7, 3, 3, 4), getValueInPatch(patch, 7, 3, 3, 6)};
            vector3 = null;
            vector4 = null;
            //if (n != 1 && n != 5) n = 5;
            //System.out.println("type "+type+" n=" + n);
            //System.out.println("vector1=["+vector1[0]+", "+vector1[1]+", "+vector1[2]+", "+vector1[3]+"]");
            //System.out.println("vector2=["+vector2[0]+", "+vector2[1]+", "+vector2[2]+", "+vector2[3]+"]");
            //System.out.println("vector3=["+vector3[0]+", "+vector3[1]+", "+vector3[2]+", "+vector3[3]+"]");
            //System.out.flush();
        } else if (type == 5) {// vertical
            //horizontal vector
            vector1 = new float[]{getValueInPatch(patch, 7, 0, 3, 3), getValueInPatch(patch, 7, 2, 3, 3), getValueInPatch(patch, 7, 4, 3, 3), getValueInPatch(patch, 7, 6, 3, 3)};
            // vertical vector
            vector2 = new float[]{getValueInPatch(patch, 7, 3, 0, 3), getValueInPatch(patch, 7, 3, 2, 3), getValueInPatch(patch, 7, 3, 4, 3), getValueInPatch(patch, 7, 3, 6, 3)};
            // depth vector
            //vector3 = new float[]{getValueInPatch(patch, 7, 3, 3, 0), getValueInPatch(patch, 7, 3, 3, 2), getValueInPatch(patch, 7, 3, 3, 4), getValueInPatch(patch, 7, 3, 3, 6)};
            vector3 = null;
            vector4 = null;
            //if (n != 2 && n != 5) n = 5;
            //System.out.println("type "+type+" n=" + n);
            //System.out.println("vector1=["+vector1[0]+", "+vector1[1]+", "+vector1[2]+", "+vector1[3]+"]");
            //System.out.println("vector2=["+vector2[0]+", "+vector2[1]+", "+vector2[2]+", "+vector2[3]+"]");
            //System.out.println("vector3=["+vector3[0]+", "+vector3[1]+", "+vector3[2]+", "+vector3[3]+"]");
            //System.out.flush();
        } else {//depth
            //horizontal vector
            vector1 = new float[]{getValueInPatch(patch, 7, 0, 3, 3), getValueInPatch(patch, 7, 2, 3, 3), getValueInPatch(patch, 7, 4, 3, 3), getValueInPatch(patch, 7, 6, 3, 3)};
            // vertical vector
            vector2 = new float[]{getValueInPatch(patch, 7, 3, 0, 3), getValueInPatch(patch, 7, 3, 2, 3), getValueInPatch(patch, 7, 3, 4, 3), getValueInPatch(patch, 7, 3, 6, 3)};
            // depth vector
            vector3 = new float[]{getValueInPatch(patch, 7, 3, 3, 0), getValueInPatch(patch, 7, 3, 3, 2), getValueInPatch(patch, 7, 3, 3, 4), getValueInPatch(patch, 7, 3, 3, 6)};
            vector4 = null;
            //if (n != 3 && n != 5) n = 5;
            //System.out.println("w1="+w1+"w2="+w2+"w3="+w3+"w4="+w4);
            //n=5;
            //System.out.println("type "+type+" n=" + n);
            //System.out.println("vector1=["+vector1[0]+", "+vector1[1]+", "+vector1[2]+", "+vector1[3]+"]");
            //System.out.println("vector2=["+vector2[0]+", "+vector2[1]+", "+vector2[2]+", "+vector2[3]+"]");
            //System.out.println("vector3=["+vector3[0]+", "+vector3[1]+", "+vector3[2]+", "+vector3[3]+"]");
            //System.out.flush();

        }
        //computation of pixel value
        double pixelValue = 0;
        if (n == 1) {
            for (int i = 0; i < weightVector.length; i++) {
                pixelValue = pixelValue + (vector1[i] * weightVector[i]);
            }
        } else if (n == 2) {
            for (int i = 0; i < weightVector.length; i++) {
                pixelValue = pixelValue + (vector2[i] * weightVector[i]);
            }
        } else if (n == 3 && vector3 != null) {
            for (int i = 0; i < weightVector.length; i++) {
                pixelValue = pixelValue + (vector3[i] * weightVector[i]);
            }
        } else if (n == 4 && vector4 != null) {
            for (int i = 0; i < weightVector.length; i++) {
                pixelValue = pixelValue + (vector4[i] * weightVector[i]);
            }
        } else {
            //System.out.println("creating value" + type);  System.out.flush();
            double p1 = 0, p2 = 0, p3 = 0, p4 = 0;
            for (int i = 0; i < weightVector.length; i++) {
                p1 += (vector1[i] * weightVector[i]);
                //System.out.println("p1 update p1="+p1+", p2="+p2+" , p3="+p3+" , p4="+p4);  System.out.flush();
                p2 += (vector2[i] * weightVector[i]);
                //if(type==2){ System.out.println("p2 update p1="+p1+", p2="+p2+" , p3="+p3+" , p4="+p4);  System.out.flush();  }
                if (vector3 != null) p3 += (vector3[i] * weightVector[i]);
                //if(type==2){ System.out.println("p3 update p1="+p1+", p2="+p2+" , p3="+p3+" , p4="+p4);  System.out.flush();  }
                if (vector4 != null) p4 += (vector4[i] * weightVector[i]);
                //if(type==2){ System.out.println("p4 update p1="+p1+", p2="+p2+" , p3="+p3+" , p4="+p4);  System.out.flush();  }
            }
            //System.out.println("p1="+p1+", p2="+p2+" , p3="+p3+" , p4="+p4+ "w1="+w1+", w2="+w2+" , w3="+w3+" , w4="+w4);  System.out.flush();
            pixelValue = (((w1 * p1) + (w2 * p2) + (w3 * p3) + (w4 * p4)) / (w1 + w2 + w3 + w4));
            //System.out.println("pixel value="+pixelValue);  System.out.flush();
        }
        return (float) pixelValue;
    }

}
