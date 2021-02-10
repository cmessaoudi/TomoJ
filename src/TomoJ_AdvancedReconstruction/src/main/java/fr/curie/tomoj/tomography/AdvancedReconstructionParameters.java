package fr.curie.tomoj.tomography;

import fr.curie.tomoj.tomography.projectors.*;

/**
 * Created by cedric on 16/12/2015.
 */
public class AdvancedReconstructionParameters extends ReconstructionParameters{

    public static final int TVM=3;
    public static final int BAYESIAN=4;
    public static final int COMPRESSED_SENSING=5;



    protected double regularizationWeight;

    protected double waveletPercentageOfZeros;
    protected int waveletType;
    protected double waveletDegree;
    protected double waveletShift;

    protected double tvmTheta=25;
    protected double tvmG=1;
    protected double tvmDt=0.1;
    protected int tvmIterations=5;

    public AdvancedReconstructionParameters(int width, int height, int depth) {
        super(width,height,depth);
    }



    public double getRegularizationWeight() {
        return regularizationWeight;
    }

    public void setRegularizationWeight(double regularizationWeight) {
        this.regularizationWeight = regularizationWeight;
    }


    public double getWaveletPercentageOfZeros() {
        return waveletPercentageOfZeros;
    }

    public void setWaveletPercentageOfZeros(double waveletPercentageOfZeros) {
        this.waveletPercentageOfZeros = waveletPercentageOfZeros;
    }

    public int getWaveletType() {
        return waveletType;
    }

    public void setWaveletType(int waveletType) {
        this.waveletType = waveletType;
    }

    public double getWaveletDegree() {
        return waveletDegree;
    }

    public void setWaveletDegree(double waveletDegree) {
        this.waveletDegree = waveletDegree;
    }

    public double getWaveletShift() {
        return waveletShift;
    }

    public void setWaveletShift(double waveletShift) {
        this.waveletShift = waveletShift;
    }

    public double getTvmTheta() {
        return tvmTheta;
    }

    public void setTvmTheta(double tvmTheta) {
        this.tvmTheta = tvmTheta;
    }

    public double getTvmG() {
        return tvmG;
    }

    public void setTvmG(double tvmG) {
        this.tvmG = tvmG;
    }

    public double getTvmDt() {
        return tvmDt;
    }

    public void setTvmDt(double tvmDt) {
        this.tvmDt = tvmDt;
    }

    public int getTvmIterations() {
        return tvmIterations;
    }

    public void setTvmIterations(int tvmIterations) {
        this.tvmIterations = tvmIterations;
    }

    public static ReconstructionParameters createTVMParameters(int width,int height, int depth,int nbIterations, double regularizationWeight){
        AdvancedReconstructionParameters result=new AdvancedReconstructionParameters(width, height, depth);
        result.reconstructionType =TVM;
        result.nbIterations=nbIterations;
        result.setWeightingRadius(Double.NaN);
        result.setRelaxationCoefficient(1);
        result.setRegularizationWeight(regularizationWeight);
        result.setUpdateNb(0);
        return result;
    }
    public static ReconstructionParameters createTVMParameters(int width,int height, int depth,int nbIterations, double relaxationCoefficient, double tvmTheta, double tvmG, double tvmDt, int tvmIterations){
        AdvancedReconstructionParameters result=new AdvancedReconstructionParameters(width, height, depth);
        result.reconstructionType =TVM;
        result.nbIterations=nbIterations;
        result.setWeightingRadius(Double.NaN);
        result.setRelaxationCoefficient(relaxationCoefficient);
        result.setTvmTheta(tvmTheta);
        result.setTvmG(tvmG);
        result.setTvmDt(tvmDt);
        result.setTvmIterations(tvmIterations);
        result.setUpdateNb(0);
        return result;
    }
    public static ReconstructionParameters createBayesianParameters(int width,int height, int depth,int nbIterations,double regularizationAlpha, double regularizationWeight){
        AdvancedReconstructionParameters result=new AdvancedReconstructionParameters(width, height, depth);
        result.reconstructionType =BAYESIAN;
        result.nbIterations=nbIterations;
        result.weightingRadius=Double.NaN;
        result.relaxationCoefficient=regularizationAlpha;
        result.setRegularizationWeight(regularizationWeight);
        result.setUpdateNb(0);
        return result;
    }
    public static ReconstructionParameters createCompressedSensingParameters(int width,int height, int depth,int nbIterations,double relaxationCoefficient, double waveletPercentageOfZeros,int waveletType, double waveletDegree, double waveletShift){
        AdvancedReconstructionParameters result=new AdvancedReconstructionParameters(width, height, depth);
        result.reconstructionType =COMPRESSED_SENSING;
        result.nbIterations=nbIterations;
        result.weightingRadius=Double.NaN;
        result.relaxationCoefficient=relaxationCoefficient;
        result.waveletPercentageOfZeros=waveletPercentageOfZeros;
        result.waveletType=waveletType;
        result.waveletDegree=waveletDegree;
        result.waveletShift=waveletShift;
        result.setUpdateNb(0);
        return result;
    }

    public Projector getProjector(TiltSeries ts, TomoReconstruction2 rec){

        VoxelProjector3D proj;
        switch (reconstructionType){
            case BP:
            case WBP:
            case OSSART:
                return super.getProjector(ts,rec);
            case TVM:
                TVMProjector3D tvmproj = new TVMProjector3D(ts, rec, null);
                tvmproj.setRegularizationAlpha(getRelaxationCoefficient());
                //tvmproj.setRegularizationLambda(params.getRegularizationWeight());
                tvmproj.setPositivityConstraint(isPositivityConstraint());
                tvmproj.setLongObjectCompensation(isLongObjectCompensation());
                tvmproj.setTvmTheta(getTvmTheta());
                tvmproj.setTvmG(getTvmG());
                tvmproj.setTvmDt(getTvmDt());
                tvmproj.setTvmIteration(getTvmIterations());
                tvmproj.setNbRaysPerPixels(recNbRaysPerPixels);
                if (isRescaleData()) tvmproj.setScale(ts.getWidth() / (double)rec.getWidth());
                //errors = rec2.regularization(ts, tvmproj, params.getNbIterations(), 0, rec2.getHeight());
                if(fista)return new FistaProjector3D(tvmproj);
                return tvmproj;
            case BAYESIAN:
                BayesianVoxelProjector3D bayproj = new BayesianVoxelProjector3D(ts, rec, null);
                bayproj.setRegularizationAlpha(getRelaxationCoefficient());
                bayproj.setRegularizationLambda(getRegularizationWeight());
                bayproj.setPositivityConstraint(isPositivityConstraint());
                bayproj.setLongObjectCompensation(isLongObjectCompensation());
                bayproj.setNbRaysPerPixels(recNbRaysPerPixels);
                if (isRescaleData()) bayproj.setScale(ts.getWidth() / (double)rec.getWidth());
                //errors = rec2.regularization(ts, bayproj, params.getNbIterations(), 0, rec2.getHeight());
                return bayproj;
            case COMPRESSED_SENSING:
                CompressedSensingProjector csproj=new CompressedSensingProjector(ts,rec,null);
                csproj.setWaveletType(getWaveletType());
                csproj.setWaveletDegree(getWaveletDegree());
                csproj.setWaveletShift(getWaveletShift());
                csproj.setSoftThresholdingPercentageOfZeros(getWaveletPercentageOfZeros());
                if(isRescaleData()) csproj.setScale(ts.getWidth()/(double)rec.getWidth());
                //errors = rec2.OSSART(ts,csproj,params.getNbIterations(), params.getRelaxationCoefficient(), ts.getImageStackSize(), params.getFscType(),0, rec2.getHeight());
                if(fista) return new FistaProjector3D(csproj);
                return csproj;
                
        }
        return null;
    }

    public String asString(){
        String result= "reconstruction, width:"+width+", height:"+height+", thickness:"+depth+"\n";
        switch (reconstructionType){
            case OSSART:
                result+="OSSART : NbIterations:"+nbIterations+", relaxationCoefficient:"+relaxationCoefficient+", updateNb:"+updateNb+"\n";
                break;
            case WBP:
                result+="WBP : weighting radius:"+weightingRadius+", elongationCorrection"+isElongationCorrection()+"\n";
                break;
            case BP:
                result+="BP : elongationCorrection"+isElongationCorrection()+"\n";
                break;
            case BAYESIAN:
                result+="BAYESIAN : NbIterations:"+nbIterations+", regularizationWeight:"+regularizationWeight+"\n";
                break;
            case TVM:
                result+="TVM : NbIterations:"+nbIterations+", regularizationWeight:"+regularizationWeight+"\n";
                break;
            case COMPRESSED_SENSING:
                result+="Compress sensing : NbIterations:"+nbIterations+", relaxation coefficient:"+relaxationCoefficient+", percentage of zeros:"+getWaveletPercentageOfZeros()+", wavelet ("+((waveletType== 3)?"BSPLINE":"ORTHOGONAL")+", degree:"+waveletDegree+", shift:"+waveletShift+")\n";
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
            case BAYESIAN:
                result+="_BAYESIAN_NbIterations"+nbIterations+"_regularizationWeight"+regularizationWeight;
                break;
            case TVM:
                result+="_TVM_NbIterations"+nbIterations+"_regularizationWeight"+regularizationWeight;
                break;
        }
        result+="_lObjComp"+isLongObjectCompensation()+"_rescale"+isRescaleData()+"_posConstraint"+isPositivityConstraint()+"_fista"+isFista();
        return result;

    }



}
