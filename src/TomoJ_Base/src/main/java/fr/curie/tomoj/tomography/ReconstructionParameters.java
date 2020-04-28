package fr.curie.tomoj.tomography;

import fr.curie.tomoj.tomography.filters.FFTWeighting;
import fr.curie.tomoj.tomography.projectors.Projector;
import fr.curie.tomoj.tomography.projectors.VoxelProjector3D;
import ij.Prefs;

/**
 * Created by cedric on 16/12/2015.
 */
public class ReconstructionParameters {
    public static final int BP=0;
    public static final int WBP=1;
    public static final int OSSART=2;

    protected int width,height,depth;

    protected int recNbRaysPerPixels=1;

    protected int type;
    protected int nbIterations=0;
    protected double relaxationCoefficient;
    protected int updateNb=0;
    protected boolean positivityConstraint=false;
    protected double weightingRadius;
    protected boolean rescaleData=true;
    protected boolean longObjectCompensation=false;
    protected boolean elongationCorrection=false;
    protected int fscType=TomoReconstruction2.ALL_PROJECTIONS;
    protected double[] reconstructionCenterModifiers;




    public ReconstructionParameters(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }


    public int getNbIterations() {
        return nbIterations;
    }

    public void setNbIterations(int nbIterations) {
        this.nbIterations = nbIterations;
    }

    public double getRelaxationCoefficient() {
        return relaxationCoefficient;
    }

    public void setRelaxationCoefficient(double relaxationCoefficient) {
        this.relaxationCoefficient = relaxationCoefficient;
    }

    public int getUpdateNb() {
        return updateNb;
    }

    public void setUpdateNb(int updateNb) {
        this.updateNb = updateNb;
    }

    public boolean isPositivityConstraint() {
        return positivityConstraint;
    }

    public void setPositivityConstraint(boolean positivityConstraint) {
        this.positivityConstraint = positivityConstraint;
    }

    public double getWeightingRadius() {
        return weightingRadius;
    }

    public void setWeightingRadius(double weightingRadius) {
        this.weightingRadius = weightingRadius;
    }

    public boolean isRescaleData() {
        return rescaleData;
    }

    public void setRescaleData(boolean rescaleData) {
        this.rescaleData = rescaleData;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isLongObjectCompensation() {
        return longObjectCompensation;
    }

    public void setLongObjectCompensation(boolean longObjectCompensation) {
        this.longObjectCompensation = longObjectCompensation;
    }


    public boolean isElongationCorrection() {
        return elongationCorrection;
    }

    public void setElongationCorrection(boolean elongationCorrection) {
        this.elongationCorrection = elongationCorrection;
    }

    public int getFscType() {
        return fscType;
    }

    public void setFscType(int fscType) {
        this.fscType = fscType;
    }

    public double[] getReconstructionCenterModifiers() {
        return reconstructionCenterModifiers;
    }

    public void setReconstructionCenterModifiers(double[] reconstructionCenterModifiers) {
        this.reconstructionCenterModifiers = reconstructionCenterModifiers;
    }





    public static ReconstructionParameters createOSSARTParameters(int width, int height, int depth, int nbIterations, double relaxationCoefficient, int updateNb){
        ReconstructionParameters result=new ReconstructionParameters(width, height, depth);
        result.type=OSSART;
        result.nbIterations=nbIterations;
        result.relaxationCoefficient=relaxationCoefficient;
        result.updateNb=updateNb;
        result.weightingRadius=Double.NaN;
        return result;
    }

    public static ReconstructionParameters createOSSARTParameters(int width, int height, int depth, int nbIterations, double relaxationCoefficient, int updateNb, double[] reconstructionCenterModifiers){
        ReconstructionParameters result=new ReconstructionParameters(width, height, depth);
        result.reconstructionCenterModifiers=reconstructionCenterModifiers;
        result.type=OSSART;
        result.nbIterations=nbIterations;
        result.relaxationCoefficient=relaxationCoefficient;
        result.updateNb=updateNb;
        result.weightingRadius=Double.NaN;
        result.setReconstructionCenterModifiers(reconstructionCenterModifiers);
        return result;
    }



    public static ReconstructionParameters createWBPParameters(int width,int height, int depth,double weightingRadius){
        ReconstructionParameters result=new ReconstructionParameters(width, height, depth);
        result.type=(Double.isNaN(weightingRadius)||weightingRadius<=0)?BP:WBP;
        result.weightingRadius=weightingRadius;
        result.setUpdateNb(0);
        result.setNbIterations(0);
        result.setRelaxationCoefficient(Double.NaN);
        return result;
    }

    public Projector getProjector(TiltSeries ts, TomoReconstruction2 rec){
        VoxelProjector3D proj;
        switch (type){
            case BP:
                proj=new VoxelProjector3D(ts,rec,null);
                break;
            case WBP:
                proj=  new VoxelProjector3D(ts,rec,new FFTWeighting(ts, weightingRadius * ts.getWidth()));
                break;
            case OSSART:
            default:
                proj = new VoxelProjector3D(ts,rec,null);
                proj.setPositivityConstraint(positivityConstraint);
                break;
        }
        if (rescaleData) proj.setScale(ts.getWidth() / (double)rec.getWidth());
        proj.setLongObjectCompensation(longObjectCompensation);
        proj.setNbRaysPerPixels(recNbRaysPerPixels);
        return proj;
    }

    public String asString(){
        String result= "reconstruction, width:"+width+", height:"+height+", thickness:"+depth+"\n";
        switch (type){
            case OSSART:
                result+="OSSART : NbIterations:"+nbIterations+", relaxationCoefficient:"+relaxationCoefficient+", updateNb:"+updateNb+"\n";
                break;
            case WBP:
                result+="WBP : weighting radius:"+weightingRadius+", elongationCorrection"+isElongationCorrection()+"\n";
                break;
            case BP:
                result+="BP : elongationCorrection"+isElongationCorrection()+"\n";
                break;

        }
        result+="longObjectCompensation:"+isLongObjectCompensation()+", rescale:"+isRescaleData()+", positivityConstraint:"+isPositivityConstraint()+"\n";
        return result;

    }
    public String asCompressedString(){
        String result= "W"+width+"_H"+height+"_T"+depth;
        switch (type){
            case OSSART:
                result+="_OSSART_NbIte"+nbIterations+"_rc"+relaxationCoefficient+"_upNb"+updateNb;
                break;
            case WBP:
                result+="_WBP_weightingRadius"+weightingRadius+"_elongationCorrection"+isElongationCorrection();
                break;
            case BP:
                result+="_BP_elongationCorrection"+isElongationCorrection();
                break;
        }
        result+="_lObjComp"+isLongObjectCompensation()+"_rescale"+isRescaleData()+"_posConstraint"+isPositivityConstraint();
        return result;

    }

    public void savePrefs(TiltSeries ts){
        Prefs.set("TOMOJ_Thickness.int", getDepth());
        int recChoiceIndex =  0;
        if (type==WBP) recChoiceIndex += 1;
        else if (type==OSSART && updateNb==1) recChoiceIndex += 2;
        else if (type==OSSART && updateNb==ts.getImageStackSize()) recChoiceIndex += 3;
        else if (type==OSSART) recChoiceIndex += 4;
        Prefs.set("TOMOJ_ReconstructionType.int", recChoiceIndex);
        Prefs.set("TOMOJ_SampleType.bool", longObjectCompensation);
        if(type==WBP)Prefs.set("TOMOJ_wbp_diameter.double", weightingRadius);
        if(nbIterations!=0)Prefs.set("TOMOJ_IterationNumber.int", getNbIterations());
        if(updateNb!=0) Prefs.set("TOMOJ_updateOSART.int", getUpdateNb());
        if(!Double.isNaN(getRelaxationCoefficient())) Prefs.set("TOMOJ_relaxationCoefficient.double", getRelaxationCoefficient());
        if(!Double.isNaN(getRelaxationCoefficient()))Prefs.set("TOMOJ_Regul_Alpha.double", getRelaxationCoefficient());
        if(getNbIterations()!=0)Prefs.set("TOMOJ_Regul_IterationNumber.int", getNbIterations());

    }



}
