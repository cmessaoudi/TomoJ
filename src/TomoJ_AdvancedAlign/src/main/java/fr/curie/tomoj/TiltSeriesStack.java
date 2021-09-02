package fr.curie.tomoj;

import fr.curie.tomoj.align.Alignment;
import fr.curie.tomoj.tomography.TiltSeries;
import ij.io.FileInfo;

import java.util.ArrayList;

public class TiltSeriesStack extends TiltSeries {
    ArrayList<TiltSeries> tsList;
    int nImages;

    public TiltSeriesStack(TiltSeries ts){
        super(ts);
        tsList=new ArrayList<>();
        tsList.add(ts);
        nImages=ts.getImageStackSize();
    }

    public TiltSeriesStack(TiltSeries[] ts){
        super(ts[0]);
        tsList=new ArrayList<>();
        nImages=0;
        for(TiltSeries t:ts) {
            tsList.add(t);
            nImages+=t.getImageStackSize();
        }
    }

    public TiltSeriesStack(ArrayList<TiltSeries> ts){
        super(ts.get(0));
        tsList=ts;

        nImages=0;
        for(TiltSeries t:ts) {
            nImages+=t.getImageStackSize();
        }
        //nImages=tsList.get(0).getImageStackSize();
    }

    @Override
    public String getTitle() {
        String title="stackOf";
        for(TiltSeries ts:tsList){
            title+="_"+ts.getTitle();
        }

        return title;
    }

    @Override
    public Alignment getAlignment() {
        return tsList.get(0).getAlignment();
    }

    public TiltSeries getTiltSeries(int index){
        int offset=0;
        for(TiltSeries ts:tsList){
            if(index >= offset && index < offset+ts.getImageStackSize()){
                return ts;
            }
            offset+=ts.getImageStackSize();
        }

        return tsList.get(0);
    }

    @Override
    public int getImageStackSize() {
        return nImages;
    }

    @Override
    public FileInfo getFileInfo() {
        return tsList.get(0).getFileInfo();
    }

    @Override
    public FileInfo getOriginalFileInfo() {
        return tsList.get(0).getOriginalFileInfo();
    }

    @Override
    public float[] getPixels(int index) {
        int offset=0;
        for(TiltSeries ts:tsList){
            if(index >= offset && index < offset+ts.getImageStackSize()){
                return ts.getPixels(index-offset);
            }
            offset+=ts.getImageStackSize();
        }

        return null;
    }

    @Override
    public int getWidth() {
        return tsList.get(0).getWidth();
    }

    @Override
    public int getHeight() {
        return tsList.get(0).getHeight();
    }

    @Override
    public int getAlignMethodForReconstruction() {
        return tsList.get(0).getAlignMethodForReconstruction();
    }

    @Override
    public void setAlignMethodForReconstruction(int alignMethodForReconstruction) {
        for(TiltSeries ts:tsList) {
            ts.setAlignMethodForReconstruction(alignMethodForReconstruction);
        }
    }
}
