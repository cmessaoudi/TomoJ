package fr.curie.plugins;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.ImageProcessor;
import fr.curie.utils.align.AlignImages;

import java.awt.geom.AffineTransform;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * Created by cedric on 06/04/2016.
 */
public class GoldBead_QualityAssessment implements PlugInFilter {
    ImagePlus iplus;
    ArrayList<double[]> landmarks3D;
    String title;

    /**
     * Description of the Method
     *
     * @param arg Description of the Parameter
     * @param imp Description of the Parameter
     * @return Description of the Return Value
     */
    public int setup(String arg, ImagePlus imp) {
        iplus = imp;
//        Slicer reslicer=new Slicer();
//        iplus=reslicer.reslice(imp);
        title=imp.getTitle();
        return DOES_ALL + STACK_REQUIRED + NO_CHANGES;
    }


    /**
     * Main processing method for the Elongation_Correction_Tomography object
     *
     * @param ip Description of the Parameter
     */
    public void run(ImageProcessor ip) {

        OpenDialog od=new OpenDialog("get landmarks file");
        loadLandmarksFile(od.getPath());
        int patchSize=(int)IJ.getNumber("patchSize",21);
        int patchSize2=patchSize/2;
        boolean centerImages=(int)IJ.getNumber("center images (0=false)",1)!=0;
        double zscore=0;
        double corr=0;
        double corr2=0;
        double corrX=0;
        double corrY=0;
        int count=0;
        for(int i=0;i<landmarks3D.size();i++){
            double[] point=landmarks3D.get(i);
            //iplus.setSlice((int)point[1]+1);
            //ip=iplus.getProcessor().duplicate();
            ImageProcessor patch=getPatch(iplus.getImageStack(),point,patchSize,centerImages);
//            ip.setRoi((int)point[0]-patchSize2,(int)point[2]-patchSize2,patchSize,patchSize);
//            ImageProcessor patch=ip.crop();
//            if(centerImages)patch=centerImage(patch);
            IJ.saveAsTiff(new ImagePlus("",patch),title+"_"+i+"tif");
            zscore+=computeZscore(patch);
            double[] tmp=computeFlipCorrelation(patch);
            corr+=tmp[0];
            corr2+=tmp[1];
            corrX+=tmp[2];
            corrY+=tmp[3];
            count++;
        }
        zscore/=count;
        corr/=count;
        corr2/=count;
        corrX/=count;
        corrY/=count;

        ResultsTable rt=ResultsTable.getResultsTable();
        rt.setPrecision(9);
        rt.showRowNumbers(true);
        rt.incrementCounter();
        rt.addValue("title", title);
        rt.addValue("zscore",zscore);
        rt.addValue("flip correlation",corr);
        rt.addValue("flip correlation 2",corr2);
        rt.addValue("mean corrX",corrX);
        rt.addValue("mean corrY", corrY);
        rt.addValue("count",count);
        rt.addValue("total number landmarks", landmarks3D.size());
        rt.show("Results");
        try {
            rt.saveAs(title+"resultsPatches.xls");
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public void loadLandmarksFile(String path){
        landmarks3D=new ArrayList<double[]>();
        try {
            BufferedReader bf = new BufferedReader(new FileReader(path));
            String line;
            while((line=bf.readLine())!=null){
                if(line.startsWith("#")) {
                    String[] items = line.split("\t");
                    if(items.length==4) {
                        double[] tmp= new double[3];
                        tmp[0]=Double.parseDouble(items[1])+((iplus.getWidth()-1.0)/2.0);
                        tmp[1]=Double.parseDouble(items[2])+((iplus.getHeight()-1.0)/2.0);
                        tmp[2]=Double.parseDouble(items[3])+((iplus.getImageStackSize()-1.0)/2.0);
                        if(tmp[0]>=0&&tmp[0]<iplus.getWidth()&&tmp[1]>=0&&tmp[1]<iplus.getHeight()&&tmp[2]>=0&&tmp[2]<iplus.getImageStackSize())landmarks3D.add(tmp);
                    }
                }

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static ImageProcessor getPatch(ImageStack volume, double[] point, int patchSize, boolean centerImages){
        ImageProcessor xzPlane=new FloatProcessor(volume.getWidth(),volume.getSize());
        float[] pixs=(float[])xzPlane.getPixels();
        for(int i=0;i<volume.getSize();i++){
            float[] pixsVol=(float[])volume.getPixels(i+1);
            System.arraycopy(pixsVol, (int)Math.round(point[1]) * volume.getWidth(), pixs, i * xzPlane.getWidth(), xzPlane.getWidth());

        }
        xzPlane.resetMinAndMax();

        return getPatch(xzPlane,point,patchSize,centerImages);
    }

    public static ImageProcessor getPatch2(ImageStack volume, double[] point, int patchSize, boolean centerImages){
        ImageProcessor xzPlane=new FloatProcessor(patchSize,patchSize);
        int patchSize2=patchSize/2;
        float[] pixs=(float[])xzPlane.getPixels();
        for(int i=0;((i<patchSize)&&(i+point[2]<volume.getSize()));i++){
            float[] pixsVol=(float[])volume.getPixels((int)point[2]-patchSize2+1+i);
            int index=((int)point[1])*volume.getWidth()+(int)point[0]-patchSize2;
            int offset=0;
            if(index<0)  offset=Math.abs(index);
            if(index>pixsVol.length||(xzPlane.getWidth()-offset)<0) continue;
            try {
                System.arraycopy(pixsVol, index + offset, pixs, i * xzPlane.getWidth(), xzPlane.getWidth() - offset);
            }catch (Exception e){
                System.out.println("goldbeadQA::getPatch2 error with arraycopy pixVol length"+pixsVol.length+" index "+(index+offset)+" pixs length "+pixs.length+" index "+(i*xzPlane.getWidth())+" length "+(xzPlane.getWidth()-offset));
                return xzPlane;
            }
        }
        xzPlane.resetMinAndMax();

        if(centerImages){
            ImageProcessor flipX=xzPlane.duplicate();
            flipX.flipHorizontal();
            AffineTransform T=AlignImages.computeCrossCorrelationFFT((float[])flipX.getPixels(),(float[])xzPlane.getPixels(),xzPlane.getWidth(),xzPlane.getHeight(),true);
            double txX= T.getTranslateX()/2;
            double tyX= T.getTranslateY()/2;
            System.out.println("shift X by "+txX+","+tyX);
            for(int i=0;((i<patchSize)&&(i+point[2]<volume.getSize()));i++){
                float[] pixsVol=(float[])volume.getPixels((int)point[2]-patchSize2+1+i);
                int index=((int)(point[1]-tyX))*volume.getWidth()+(int)(point[0]-txX)-patchSize2;
                int offset=0;
                if(index<0)  offset=Math.abs(index);
                if(index>pixsVol.length||xzPlane.getWidth()-offset<0) continue;
                System.arraycopy(pixsVol,index+offset,pixs,i*xzPlane.getWidth(),xzPlane.getWidth()-offset);
            }



            ImageProcessor flipY=xzPlane.duplicate();
            flipY.flipVertical();
            T=AlignImages.computeCrossCorrelationFFT((float[])flipY.getPixels(),(float[])xzPlane.getPixels(),xzPlane.getWidth(),xzPlane.getHeight(),true);
            double txY= T.getTranslateX()/2;
            double tyY= T.getTranslateY()/2;
            System.out.println("shift Y by "+txY+","+tyY);
            for(int i=0;((i<patchSize)&&(i+point[2]<volume.getSize()));i++){
                float[] pixsVol=(float[])volume.getPixels((int)point[2]-patchSize2+1+i);
                int index=((int)(point[1]-tyX-tyY))*volume.getWidth()+(int)(point[0]-txX-txY)-patchSize2;
                int offset=0;
                if(index<0)  offset=Math.abs(index);
                if(index>pixsVol.length||xzPlane.getWidth()-offset<0) continue;
                System.arraycopy(pixsVol,index+offset,pixs,i*xzPlane.getWidth(),xzPlane.getWidth()-offset);

                //System.arraycopy(pixsVol,((int)(point[1]-tyX-tyY))*volume.getWidth()+(int)(point[0]-txX-txY)-patchSize2,pixs,i*xzPlane.getWidth(),xzPlane.getWidth());
            }
        }

        return xzPlane;
    }

    public static ImageProcessor getPatch(ImageProcessor fullPlane, double[] point, int patchSize, boolean centerImages){
        ImageProcessor ip=fullPlane.convertToFloat();
        int patchSize2=patchSize/2;
        fullPlane.setRoi((int)point[0]-patchSize2,(int)point[2]-patchSize2,patchSize,patchSize);
        ImageProcessor patch=ip.crop();
        if(centerImages){
            ImageProcessor flipX=patch.duplicate();
            flipX.flipHorizontal();
            AffineTransform T=AlignImages.computeCrossCorrelationFFT((float[])flipX.getPixels(),(float[])patch.getPixels(),patch.getWidth(),patch.getHeight(),true);
            double txX= T.getTranslateX()/2;
            double tyX= T.getTranslateY()/2;
            //System.out.println("shift X by "+txX+","+tyX);
            fullPlane.setRoi((int)(point[0]-patchSize2-txX),(int)(point[2]-patchSize2-tyX),patchSize,patchSize);
            patch=ip.crop();

            ImageProcessor flipY=patch.duplicate();
            flipY.flipVertical();
            T=AlignImages.computeCrossCorrelationFFT((float[])flipY.getPixels(),(float[])patch.getPixels(),patch.getWidth(),patch.getHeight(),true);
            double txY= T.getTranslateX()/2;
            double tyY= T.getTranslateY()/2;
            //System.out.println("shift Y by "+txY+","+tyY);
            fullPlane.setRoi((int)(point[0]-patchSize2-txX-txY),(int)(point[2]-patchSize2-tyX-tyY),patchSize,patchSize);
            patch=ip.crop();
        }
        return patch;
    }

    public static double computeZscore(ImageProcessor imp){
        ImageProcessor ip=imp.convertToFloat();

        FloatStatistics stats=new FloatStatistics(ip);
        double zscore=(stats.max-stats.min)/stats.stdDev;
        return zscore;
    }

    /**
     * compute correlation of image with image flipped horizontally and vertically
     * @param imp
     * @return array containing different scores
     * <UL><LI> |corrY-corrX|</LI>
     * <LI> |corrY-corrX|/|corrY+corrX|</LI>
     * <LI> corrX</LI>
     * <LI> corrY</LI></UL>
     */
    public static double[] computeFlipCorrelation(ImageProcessor imp){
        ImageProcessor ip=imp.convertToFloat();
        ImageProcessor flipX=ip.duplicate();
        flipX.flipHorizontal();
        double corrX=Math.abs(AlignImages.correlation((float[])ip.getPixels(),(float[])flipX.getPixels()));
        if(Double.isNaN(corrX))corrX=0.000000000001;

        ImageProcessor flipY=ip.duplicate();
        flipY.flipVertical();
        double corrY=Math.abs(AlignImages.correlation((float[])ip.getPixels(),(float[])flipY.getPixels()));
        if(Double.isNaN(corrY))corrY=0.000000000001;
        double[] result=new double[]{Math.abs(corrY-corrX),Math.abs(corrY-corrX)/(Math.abs(corrY)+Math.abs(corrX)),corrX,corrY};
        if(Double.isNaN(result[1]) ) result[1]=result[0]/0.0000000000001;
        return result;

    }

    public static ImageProcessor centerImage(ImageProcessor imp){
        ImageProcessor ip=imp.convertToFloat();
        ImageProcessor flipX=ip.duplicate();
        flipX.flipHorizontal();
        AffineTransform T=AlignImages.computeCrossCorrelationFFT((float[])flipX.getPixels(),(float[])ip.getPixels(),ip.getWidth(),ip.getHeight(),true);
        T.setToTranslation(T.getTranslateX()/2,T.getTranslateY()/2);
        //System.out.println("shift X by "+T);
        float[] ali=AlignImages.applyTransform((float[])ip.getPixels(),T,ip.getWidth(),ip.getHeight());
        ip.setPixels(ali);

        ImageProcessor flipY=ip.duplicate();
        flipY.flipVertical();
        T=AlignImages.computeCrossCorrelationFFT((float[])flipY.getPixels(),(float[])ip.getPixels(),ip.getWidth(),ip.getHeight(),true);
        T.setToTranslation(T.getTranslateX()/2,T.getTranslateY()/2);
        //System.out.println("shift Y by "+T);
        ali=AlignImages.applyTransform((float[])ip.getPixels(),T,ip.getWidth(),ip.getHeight());
        ip.setPixels(ali);

        return ip;

    }
}


