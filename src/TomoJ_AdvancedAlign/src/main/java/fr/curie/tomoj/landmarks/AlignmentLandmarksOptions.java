package fr.curie.tomoj.landmarks;

/**
 * Created by cedric on 17/12/2014.
 */

public class AlignmentLandmarksOptions{
    private boolean exhaustiveSearch = true;
    private double exhaustiveSearchIncrementRotation = 5;
    private double mahalanobisWeight = 0;
    private boolean deformShrinkage = true;
    private boolean deformMagnification = true;
    private boolean deformScalingX = false;
    private boolean deformDelta = false;
    private  boolean allowInPlaneRotation = false;
    private  int numberOfCycles = 1;
    private double k = 10;
    private boolean correctForHeight = true;
    private boolean allowShifts = true;

    public AlignmentLandmarksOptions(){}

    public boolean isExhaustiveSearch() {
        return exhaustiveSearch;
    }

    public void setExhaustiveSearch(boolean exhaustiveSearch) {
        this.exhaustiveSearch = exhaustiveSearch;
    }

    public boolean isDeformShrinkage() {
        return deformShrinkage;
    }

    public void setDeformShrinkage(boolean deformShrinkage) {
        this.deformShrinkage = deformShrinkage;
    }

    public boolean isDeformMagnification() {
        return deformMagnification;
    }

    public void setDeformMagnification(boolean deformMagnification) {
        this.deformMagnification = deformMagnification;
    }

    public boolean isDeformScalingX() {
        return deformScalingX;
    }

    public void setDeformScalingX(boolean deformScalingX) {
        this.deformScalingX = deformScalingX;
    }

    public boolean isDeformDelta() {
        return deformDelta;
    }

    public void setDeformDelta(boolean deformDelta) {
        this.deformDelta = deformDelta;
    }

    public int getNumberOfCycles() {
        return numberOfCycles;
    }

    public void setNumberOfCycles(int numberOfCycles) {
        this.numberOfCycles = numberOfCycles;
    }

    public double getK() {
        return k;
    }

    public void setK(double k) {
        this.k = k;
    }

    public boolean isCorrectForHeight() {
        return correctForHeight;
    }

    public void setCorrectForHeight(boolean correctForHeight) {
        this.correctForHeight = correctForHeight;
    }

    public double getMahalanobisWeight() {
        return mahalanobisWeight;
    }

    public void setMahalanobisWeight(double mahalanobisWeight) {
        this.mahalanobisWeight = mahalanobisWeight;
    }

    public double getExhaustiveSearchIncrementRotation() {
        return exhaustiveSearchIncrementRotation;
    }

    public void setExhaustiveSearchIncrementRotation(double exhaustiveSearchIncrementRotation) {
        this.exhaustiveSearchIncrementRotation = exhaustiveSearchIncrementRotation;
    }

    public boolean isAllowInPlaneRotation() {
        return allowInPlaneRotation;
    }

    public void setAllowInPlaneRotation(boolean allowInPlaneRotation) {
        this.allowInPlaneRotation = allowInPlaneRotation;
    }

    public boolean isAllowShifts() {
        return allowShifts;
    }

    public void setAllowShifts(boolean allowShifts) {
        this.allowShifts = allowShifts;
    }

    public String toString(){
        return "exhaustiveSearch: "+exhaustiveSearch+" \nexhaustiveSearchIncrement: "+exhaustiveSearchIncrementRotation+"\n" +
                "MahalanobisWeighting: "+mahalanobisWeight+"\nallow in plane rotation: "+allowInPlaneRotation+"\n" +
                "deformations:\nshrinkage: "+deformShrinkage+"\nmagnification: "+deformMagnification+"\n" +
                "scaling X: "+deformScalingX+"\nshearing (delta): "+deformDelta+"\n" +
                "number of cycles of removal of landmarks: "+numberOfCycles+"\nk: "+k+"\ncorrect for height: "+correctForHeight;
    }
    public String toCompressedString(){
        return "Mahalanobis_"+mahalanobisWeight+"_psi_"+allowInPlaneRotation+"_" +
                "shrink_"+deformShrinkage+"_mag_"+deformMagnification+"_" +
                "scaleX_"+deformScalingX+"_shear_"+deformDelta+"_";
    }
}
