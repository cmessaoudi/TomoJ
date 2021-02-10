package fr.curie.tomoj.tomography;

import fr.curie.tomoj.tomography.filters.FFTWeighting;
import fr.curie.tomoj.tomography.projectors.FistaProjector3D;
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

    public static final int ALL_PROJECTIONS=10;
    public static final int EVEN_PROJECTIONS=11;
    public static final int ODD_PROJECTIONS=12;
    public static final int DEFINED_PROJECTIONS=13;


    protected int width,height,depth;

    protected int recNbRaysPerPixels=1;

    protected int reconstructionType;
    protected int projectionType;
    protected int nbIterations=0;
    protected double relaxationCoefficient;
    protected int updateNb=0;
    protected boolean positivityConstraint=false;
    protected boolean fista=false;
    protected double weightingRadius;
    protected boolean rescaleData=true;
    protected boolean longObjectCompensation=false;
    protected boolean elongationCorrection=false;
    protected double[] reconstructionCenterModifiers;
    protected boolean saveErrorVolume=false;
    protected boolean SaveErrorVolumeAll=false;

    protected int[] availableIndexes=null;
    protected int tempNbCall=0;


    public ReconstructionParameters(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public ReconstructionParameters(ReconstructionParameters other){
        this.width = other.width;
        this.height = other.height;
        this.depth = other.depth;

        this.recNbRaysPerPixels = other.recNbRaysPerPixels;
        this.reconstructionType = other.reconstructionType;
        this.nbIterations = other.nbIterations;
        this.relaxationCoefficient = other.relaxationCoefficient;
        this.updateNb = other.updateNb;
        this.positivityConstraint = other.positivityConstraint;
        this.fista = other.fista;
        this.weightingRadius = other.weightingRadius;
        this.rescaleData = other.rescaleData;
        this.longObjectCompensation = other.longObjectCompensation;
        this.elongationCorrection = other.elongationCorrection;
        this.reconstructionCenterModifiers = other.reconstructionCenterModifiers;
    }

    public int getReconstructionType() {
        return reconstructionType;
    }

    public void setReconstructionType(int reconstructionType) {
        this.reconstructionType = reconstructionType;
    }


    public int getNbIterations() {
        return nbIterations;
    }

    public void setNbIterations(int nbIterations) {
        this.nbIterations = nbIterations;
    }

    public double getRelaxationCoefficient() {
        if(relaxationCoefficient<0) return 1./(++tempNbCall);
        return relaxationCoefficient;
    }

    public void setRelaxationCoefficient(double relaxationCoefficient) {
        this.relaxationCoefficient = relaxationCoefficient;
    }

    public void resetTmpNbCall(){
        tempNbCall=0;
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
        return projectionType;
    }

    public void setFscType(int fscType) {
        this.projectionType = fscType;
        availableIndexes=null;
    }

    public int getProjectionType() {
        return projectionType;
    }

    public void setProjectionType(int projectionType) {
        this.projectionType = projectionType;
        availableIndexes=null;
    }

    public boolean isSaveErrorVolume() {
        return saveErrorVolume;
    }

    public void setSaveErrorVolume(boolean saveErrorVolume) {
        this.saveErrorVolume = saveErrorVolume;
    }
    public void setSaveErrorVolume(boolean saveErrorVolume, boolean saveErrorVolumeAll) {
        this.saveErrorVolume = saveErrorVolume;
        this.SaveErrorVolumeAll = saveErrorVolumeAll;
    }

    public boolean isSaveErrorVolumeAll() {
        return SaveErrorVolumeAll;
    }

    public void setSaveErrorVolumeAll(boolean saveErrorVolumeAll) {
        SaveErrorVolumeAll = saveErrorVolumeAll;
    }

    public int[] getAvailableIndexes(TiltSeries ts) {
        if(this.availableIndexes==null){
            int nbproj=ts.getImageStackSize();
            boolean[] atraiter = new boolean[nbproj];
            nbproj = 0;
            for (int i = 0; i < atraiter.length; i++) {
                switch (projectionType) {
                    case ALL_PROJECTIONS:
                    default:
                        atraiter[i] = true;
                        nbproj++;
                        break;
                    case EVEN_PROJECTIONS:
                        boolean even = (i % 2 == 0);
                        atraiter[i] = even;
                        if (even) nbproj++;
                        if (nbproj > atraiter.length / 2) {
                            atraiter[i] = false;
                            nbproj--;
                        }
                        break;
                    case ODD_PROJECTIONS:
                        boolean odd = (i % 2 == 1);
                        atraiter[i] = odd;
                        if (odd) nbproj++;
                        if (nbproj > atraiter.length / 2) {
                            atraiter[i] = false;
                            nbproj--;
                        }
                        break;
                }
            }
            System.out.println("nb proj:" + nbproj);
            availableIndexes = new int[nbproj];
            int cc=0;
            for(int im=0;im<ts.getImageStackSize();im++) {
                if (atraiter[im]) {
                    availableIndexes[cc] = im;
                    atraiter[im] = false;
                    cc++;
                }
            }
        }
        return availableIndexes;
    }

    public void setAvailableIndexes(int[] availableIndexes) {
        this.availableIndexes = availableIndexes;
        reconstructionType =DEFINED_PROJECTIONS;
    }

    public double[] getReconstructionCenterModifiers() {
        return reconstructionCenterModifiers;
    }

    public void setReconstructionCenterModifiers(double[] reconstructionCenterModifiers) {
        this.reconstructionCenterModifiers = reconstructionCenterModifiers;
    }

    public boolean isFista() {
        return fista;
    }

    public void setFista(boolean fista) {
        this.fista = fista;
    }

    public static ReconstructionParameters createOSSARTParameters(int width, int height, int depth, int nbIterations, double relaxationCoefficient, int updateNb){
        ReconstructionParameters result=new ReconstructionParameters(width, height, depth);
        result.reconstructionType =OSSART;
        result.nbIterations=nbIterations;
        result.relaxationCoefficient=relaxationCoefficient;
        result.updateNb=updateNb;
        result.weightingRadius=Double.NaN;
        return result;
    }

    public static ReconstructionParameters createOSSARTParameters(int width, int height, int depth, int nbIterations, double relaxationCoefficient, int updateNb, double[] reconstructionCenterModifiers){
        ReconstructionParameters result=new ReconstructionParameters(width, height, depth);
        result.reconstructionCenterModifiers=reconstructionCenterModifiers;
        result.reconstructionType =OSSART;
        result.nbIterations=nbIterations;
        result.relaxationCoefficient=relaxationCoefficient;
        result.updateNb=updateNb;
        result.weightingRadius=Double.NaN;
        result.setReconstructionCenterModifiers(reconstructionCenterModifiers);
        return result;
    }



    public static ReconstructionParameters createWBPParameters(int width,int height, int depth,double weightingRadius){
        ReconstructionParameters result=new ReconstructionParameters(width, height, depth);
        result.reconstructionType =(Double.isNaN(weightingRadius)||weightingRadius<=0)?BP:WBP;
        result.weightingRadius=weightingRadius;
        result.setUpdateNb(0);
        result.setNbIterations(0);
        result.setRelaxationCoefficient(Double.NaN);
        return result;
    }

    public Projector getProjector(TiltSeries ts, TomoReconstruction2 rec){
        VoxelProjector3D proj;
        switch (reconstructionType){
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
        if(fista) return new FistaProjector3D(proj);
        return proj;
    }

    public String asString(){
        String result= "reconstruction, width:"+width+", height:"+height+", thickness:"+depth+"\n";
        switch (reconstructionType){
            case OSSART:
                result+="OSSART : NbIterations:"+nbIterations+", relaxationCoefficient:"+relaxationCoefficient+", updateNb:"+updateNb+"\n";
                break;
            case WBP:
                result+="WBP : weighting radius:"+weightingRadius+", elongationCorrection:"+isElongationCorrection()+"\n";
                break;
            case BP:
                result+="BP : elongationCorrection"+isElongationCorrection()+"\n";
                break;

        }
        result+="longObjectCompensation:"+isLongObjectCompensation()+", rescale:"+isRescaleData()+", positivityConstraint:"+isPositivityConstraint()+", Fista:"+isFista()+"\n";
        return result;

    }
    public String asCompressedString(){
        String result= "W"+width+"_H"+height+"_T"+depth;
        switch (reconstructionType){
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
        result+="_lObjComp"+isLongObjectCompensation()+"_rescale"+isRescaleData()+"_posConstraint"+isPositivityConstraint()+"_fista"+isFista();
        return result;

    }

    public void savePrefs(TiltSeries ts){
        Prefs.set("TOMOJ_Thickness.int", getDepth());
        int recChoiceIndex =  0;
        if (reconstructionType ==WBP) recChoiceIndex += 1;
        else if (reconstructionType ==OSSART && updateNb==1) recChoiceIndex += 2;
        else if (reconstructionType ==OSSART && updateNb==ts.getImageStackSize()) recChoiceIndex += 3;
        else if (reconstructionType ==OSSART) recChoiceIndex += 4;
        Prefs.set("TOMOJ_ReconstructionType.int", recChoiceIndex);
        Prefs.set("TOMOJ_SampleType.bool", longObjectCompensation);
        if(reconstructionType ==WBP)Prefs.set("TOMOJ_wbp_diameter.double", weightingRadius);
        if(nbIterations!=0)Prefs.set("TOMOJ_IterationNumber.int", getNbIterations());
        if(updateNb!=0) Prefs.set("TOMOJ_updateOSART.int", getUpdateNb());
        if(!Double.isNaN(getRelaxationCoefficient())) Prefs.set("TOMOJ_relaxationCoefficient.double", getRelaxationCoefficient());
        if(!Double.isNaN(getRelaxationCoefficient()))Prefs.set("TOMOJ_Regul_Alpha.double", getRelaxationCoefficient());
        if(getNbIterations()!=0)Prefs.set("TOMOJ_Regul_IterationNumber.int", getNbIterations());

    }



}
