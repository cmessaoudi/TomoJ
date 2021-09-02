package fr.curie.tomoj.landmarks;

import fr.curie.plotj.PlotWindow2;
import fr.curie.utils.powell.Function;
import fr.curie.utils.powell.Powell;
import ij.IJ;
import ij.io.FileInfo;
import ij.measure.ResultsTable;
import fr.curie.tomoj.SuperTomoJPoints;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.Chrono;

import java.awt.geom.AffineTransform;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Antoine on 22/03/2019.
 */
public class Landmarks3DDualAlign {

    private SuperTomoJPoints stp;

    private TomoJPoints tp1;
    private TomoJPoints tp2;

    private double completion;
    private alignmentLandmark bestPreviousAlignment = null; // TODO dual...
    private AlignmentLandmarkDualImproved bestPreviousAlignmentDeform = null;
    private AlignmentLandmarkDualImproved currentWorkingAlignment = null;

    public Landmarks3DDualAlign(TomoJPoints tp1, TomoJPoints tp2) {
        this.tp1 = tp1;
        this.tp2 = tp2;
        //tp1.setLandmarks3DDual(this);
        //tp2.setLandmarks3DDual(this);
    }

    public Landmarks3DDualAlign(SuperTomoJPoints stp) {
        this.stp = stp;

        // ##### To be removed #####
        this.tp1 = stp.getTomoJPoints(0);
        this.tp2 = stp.getTomoJPoints(1);
        // #########################

        for (TomoJPoints tp : stp.getAllTP()) {
            //tp.setLandmarks3DDual(this);
        }
    }

    public void interrupt() {
        completion = -1000;
        if (currentWorkingAlignment != null) currentWorkingAlignment.interrupt();
    }

    public double getCompletion() {
        return completion;
    }

    public void resetCompletion() {
        completion = 0;
    }

    /**
     * Dual align using 3D landmarks as described in <BR>
     * Marker-free image registration of electron tomoj.tomography tilt-series.<BR>
     * Sanchez Sorzano CO, Messaoudi C, Eibauer M, Bilbao-Castro J, Hegerl R, Nickell S, Marco S, Carazo J.<BR>
     * BMC Bioinformatics. 2009 Apr 27;10(1):124.
     *
     * Created by Antoine, 22/03/2019.
     *
     * @param options the options of alignment
     */
    public double dualAlign3DLandmarksWithDeformation(final AlignmentLandmarksOptions options) {
        bestPreviousAlignment = null;
        Chrono timeTotal = new Chrono();
        timeTotal.start();
        final Chrono timeStep = new Chrono();
        final AtomicLong timeOptimize = new AtomicLong(0);

        System.out.println("options : " + options.toString());

        int i = 1;
        for (TomoJPoints tp : stp.getAllTP()) {
            tp.removeEmptyChains();
            System.out.println("Number of landmarks " + i + " :" + tp.getNumberOfPoints());
            i++;
        }
//        tp1.removeEmptyChains();
//        tp2.removeEmptyChains();
//        System.out.println("number of landmarks 1:" + tp1.getNumberOfPoints());
//        System.out.println("number of landmarks 2:" + tp2.getNumberOfPoints());

//        stp = new SuperTomoJPoints(new ArrayList<>(Arrays.asList(tp1, tp2)));
        final AlignmentLandmarkDualImproved align = new AlignmentLandmarkDualImproved(stp, options);
        completion = 0;
        int totalSteps = options.isExhaustiveSearch() ? 2 : 1;
        totalSteps += (options.getNumberOfCycles() > 0) ? options.getNumberOfCycles() : 1;
        double completionStep = 1.0 / totalSteps;
        //System.out.println("align created");
        boolean creation = false;
        if (bestPreviousAlignmentDeform == null) {
            System.out.println("creation of alignement ");
            bestPreviousAlignmentDeform = new AlignmentLandmarkDualImproved(stp, options);
            creation = true;
        } else {
            System.out.println("alignement already existing");
            bestPreviousAlignmentDeform.setOptions(options);
        }
        FileInfo fi1 = tp1.getTiltSeries().getOriginalFileInfo();
        if (fi1 == null) {
            //System.out.println("original File Info null");
            fi1 = tp1.getTiltSeries().getFileInfo();
        }
//        FileInfo fi2 = tp2.getTiltSeries().getOriginalFileInfo();
//        if (fi2 == null) {
//            //System.out.println("original File Info null");
//            fi2 = tp2.getTiltSeries().getFileInfo();
//        }

        // We consider that both files are located in the same folder
        String savedir = (fi1 != null && !fi1.directory.equalsIgnoreCase("")) ? fi1.directory : IJ.getDirectory("current");

        //System.out.println("best align created");
        //	ts.combineTransforms(false);

        //exhaustive search for rotation
        double bestError = 0;
        double worstError = 0;

        double bestRot1 = -1;
        double bestRot2 = -1;
        double worstRot1 = -1;
        double worstRot2 = -1;
        double bestTilt1 = 90;
        double bestTilt2 = 90;

        if (options.isExhaustiveSearch()) {
            System.out.println("Exhaustive search for tilt axis:");

            // Get the zero degree transform of the 2nd series
            AffineTransform zeroT = tp2.getTiltSeries().getAlignment().getZeroTransform();
            double r = Math.toDegrees(Math.atan2(zeroT.getShearY(), zeroT.getScaleY()));

            for (double rot1 = 0; rot1 <= 180 - options.getExhaustiveSearchIncrementRotation(); rot1 += options.getExhaustiveSearchIncrementRotation()) {
                if (completion < 0) {
                    return Double.MAX_VALUE;
                }
                double rot2 = rot1 + r;
                align.clear();
                align.setUaxis(rot1, rot2, 90, 90);
                align.firstRjs();
                //System.out.println(align);
                currentWorkingAlignment = align;
                timeStep.start();
                double error = align.optimize(false, true);
                timeStep.stop();
                timeOptimize.addAndGet(timeStep.delay());
                System.out.println("rotation1 = " + IJ.d2s(rot1, 1) +
                        "\trotation2 = " + IJ.d2s(rot2, 1) +
                        "\twith error = " + IJ.d2s(error, 4));
                IJ.showStatus("rotation1 = " + IJ.d2s(rot1, 1) +
                        "\trotation2 = " + IJ.d2s(rot2, 1) +
                        "\twith error = " + IJ.d2s(error, 4));
                //System.out.println("rotation=" + IJ.d2s(rot, 3) + " with error=" + IJ.d2s(error, 4)+ " error images="+align.computeErrorForLandmarks2());
                if (bestRot1 == -1 || bestRot2 == -1 || bestError > error) {
                    bestRot1 = rot1;
                    bestRot2 = rot2;
                    bestError = error;
                    bestPreviousAlignmentDeform.copyFrom(align);
                }
                if (worstRot1 == -1 || worstRot2 == -1 || worstError < error) {
                    worstRot1 = rot1;
                    worstRot2 = rot2;
                    worstError = error;
                }
                completion += completionStep / (180 / options.getExhaustiveSearchIncrementRotation());
//                }
            }
            System.out.println("first search of rotation :\n" +
                    "best rot1 = " + IJ.d2s(bestRot1, 3) +
                    "\tbest rot2 = " + IJ.d2s(bestRot2, 3) +
                    "\twith error = " + IJ.d2s(bestError, 4) +
                    "\n(worst1: " + IJ.d2s(worstRot1, 3) +
                    ",\t worst2: " + IJ.d2s(worstRot2, 3) +
                    "\twith error: " + IJ.d2s(worstError, 4) + ")");
            //System.out.println(bestPreviousAlignment);
            if (completion < 0) return Double.MAX_VALUE;
            //completion+=completionStep;
        } else {
            if (creation) {
                bestRot1 = tp1.getTiltSeries().getTiltAxis() + 90;
                bestRot2 = tp2.getTiltSeries().getTiltAxis() + 90;

                bestPreviousAlignmentDeform.setUaxis(bestRot1, bestRot2, 90, 90);
                bestPreviousAlignmentDeform.firstRjs();
                timeStep.start();
                bestPreviousAlignmentDeform.optimize(false, true);
                timeStep.stop();
                timeOptimize.addAndGet(timeStep.delay());
                System.out.println("init rotation 1 to " + IJ.d2s(bestRot1, 3) + "and rotation 2 to " + IJ.d2s(bestRot2, 3));
            } else {
                bestRot1 = bestPreviousAlignmentDeform.getCurrentAi().get(0).getRot();
                bestTilt1 = bestPreviousAlignmentDeform.getCurrentAi().get(0).getTilt();
                bestRot2 = bestPreviousAlignmentDeform.getCurrentAi().get(stp.getnImagesi(0)).getRot();
                bestTilt2 = bestPreviousAlignmentDeform.getCurrentAi().get(stp.getnImagesi(0)).getTilt();
            }
        }

        //continuous optimization for the axis direction
        final boolean optimizeAngle = false;
        double[] params = new double[((optimizeAngle) ? 4 : 2)];
        params[0] = bestRot1;
        params[1] = bestRot2;

        if (optimizeAngle) {
            params[2] = 90;
            params[3] = 90;
        }
        //double[] params = new double[]{bestRot,bestTilt};

        //System.out.println("error images:"+bestPreviousAlignment.computeErrorForLandmarks2());
        final boolean[] deform = new boolean[]{true};
        //bestPreviousAlignmentDeform.initOutputFile("D:\\images_test\\phantom\\testCOSS\\Wjs.txt");
        timeStep.start();
        bestPreviousAlignmentDeform.optimize(true, true);
        timeStep.stop();
        timeOptimize.addAndGet(timeStep.delay());
        try {
            if (bestPreviousAlignmentDeform.out != null) {
                bestPreviousAlignmentDeform.out.flush();
                bestPreviousAlignmentDeform.out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        bestPreviousAlignmentDeform.out = null;

        if (completion < 0) return Double.MAX_VALUE;
        Powell pow = new Powell(new Function() {
            protected double eval(double[] xt) {
                align.copyFrom(bestPreviousAlignmentDeform);
                //align.clear();
                align.setUaxis(xt[0], xt[1], optimizeAngle ? xt[2] : 90, optimizeAngle ? xt[3] : 90);
                //align.setUaxis(xt[0],xt[1]);
                currentWorkingAlignment = align;
                if (completion < 0) return Double.MAX_VALUE;
                timeStep.start();
                double fit = align.optimize(deform[0], true);
                timeStep.stop();
                timeOptimize.addAndGet(timeStep.delay());
                return fit;
            }

            protected int length() {
                if (optimizeAngle) return 4;
                return 2;
                //return 2;
            }

        }, params, 0.01);
        params = pow.getP();

        if (completion < 0) return Double.MAX_VALUE;
        //outlier removal
        // bestPreviousAlignment.clear();
        //bestPreviousAlignmentDeform.setUaxis(params[0], params[1]);
        bestPreviousAlignmentDeform.setUaxis(params[0], params[1], optimizeAngle ? params[2] : 90, optimizeAngle ? params[3] : 90);
        currentWorkingAlignment = bestPreviousAlignmentDeform;
        timeStep.start();
        double fitness = bestPreviousAlignmentDeform.optimize(true, true);
        timeStep.stop();
        timeOptimize.addAndGet(timeStep.delay());
        //bestPreviousAlignment.printAlignment();
        System.out.println("before computeErrorForLandmarks :" +
                "\nbest rotation 1 = " + IJ.d2s(params[0], 3) +
                "\ttilt 1 = " + (optimizeAngle ? IJ.d2s(params[2]) : 90) +
                "\nbest rotation 2 = " + IJ.d2s(params[1], 3) +
                "\ttilt 2 = " + (optimizeAngle ? IJ.d2s(params[3]) : 90) +
                "\nerror =" + IJ.d2s(fitness, 4));
        bestPreviousAlignmentDeform.saveLandmarksErrors(savedir + tp1.getTiltSeries().getTitle() + "_DUAL_0_LandmarksErrors.txt");

        if (completion < 0) return Double.MAX_VALUE;
        completion += completionStep;
        int cycle = 0;
        int nbOutliers = 10;
        while (cycle < options.getNumberOfCycles() || (options.getNumberOfCycles() < 0 && nbOutliers > 3 && fitness > 0.9)) {
            if (cycle > 0 && options.getNumberOfCycles() < 0) {
                completionStep /= 2;
                completion -= completionStep;
            }

            // Compute the best alignment
            if (completion < 0) return Double.MAX_VALUE;
            bestPreviousAlignmentDeform.computeGlobalError();

            // Remove landmarks that are outliers in the current model
            if (completion < 0) return Double.MAX_VALUE;
            nbOutliers = bestPreviousAlignmentDeform.removeOutliersMahalanobis();
            //nbOutliers=bestPreviousAlignmentDeform.removeOutLiers();
            System.out.println(nbOutliers + " landmarks removed (left : " + stp.getNumberOfPoints() + ")");
            // bestPreviousAlignment.clear();
            if (completion < 0) return Double.MAX_VALUE;
            //bestPreviousAlignmentDeform.setUaxis(params[0],params[1]);
            bestPreviousAlignmentDeform.setUaxis(params[0], params[1], optimizeAngle ? params[2] : 90, optimizeAngle ? params[3] : 90);
            currentWorkingAlignment = bestPreviousAlignmentDeform;
            timeStep.start();
            fitness = bestPreviousAlignmentDeform.optimize(true, true);
            timeStep.stop();
            timeOptimize.addAndGet(timeStep.delay());
            //optimize again
            if (completion < 0) return Double.MAX_VALUE;
            pow = new Powell(new Function() {
                protected double eval(double[] xt) {
                    align.copyFrom(bestPreviousAlignmentDeform);
                    //align.clear();
                    //align.setUaxis(xt[0],xt[1]);
                    align.setUaxis(xt[0], xt[1], optimizeAngle ? xt[2] : 90, optimizeAngle ? xt[3] : 90);
                    currentWorkingAlignment = align;
                    if (completion < 0) return Double.MAX_VALUE;
                    timeStep.start();
                    double fit = align.optimize(deform[0], true);
                    timeStep.stop();
                    timeOptimize.addAndGet(timeStep.delay());
                    return fit;
                }

                protected int length() {
                    return optimizeAngle ? 4 : 2;
                }

            }, params, 0.01);
            params = pow.getP();


            //bestPreviousAlignment.clear();
            //bestPreviousAlignmentDeform.setUaxis(params[0],params[1]);
            bestPreviousAlignmentDeform.setUaxis(params[0], params[1], optimizeAngle ? params[2] : 90, optimizeAngle ? params[3] : 90);

            if (completion < 0) return Double.MAX_VALUE;
            currentWorkingAlignment = bestPreviousAlignmentDeform;
            timeStep.start();
            fitness = bestPreviousAlignmentDeform.optimize(true, true);
            timeStep.stop();
            timeOptimize.addAndGet(timeStep.delay());
            System.out.println("before computeErrorForLandmarks :" +
                    "\nbest rotation 1 = " + IJ.d2s(params[0], 3) +
                    "\ttilt 1 = " + (optimizeAngle ? IJ.d2s(params[2]) : 90) +
                    "\terror 1 =" + IJ.d2s(fitness, 4) +
                    "\nbest rotation 2 = " + IJ.d2s(params[1], 3) +
                    "\ttilt 2 = " + (optimizeAngle ? IJ.d2s(params[3]) : 90) +
                    "\terror 2 =" + IJ.d2s(fitness, 4));
            //completion+=completionStep;
            completion += nbOutliers / (double) stp.getNumberOfPoints();
            cycle++;
            bestPreviousAlignmentDeform.saveLandmarksErrors(savedir + tp1.getTiltSeries().getTitle() + "_DUAL_" + cycle + "_LandmarksErrors.txt");
        }

        //final with tilt for beam
        System.out.println("final computation looking for beam tilt");
        bestRot1 = params[0];
        bestRot2 = params[1];
        params = new double[]{bestRot1, bestRot2, bestTilt1, bestTilt2};
        pow = new Powell(new Function() {
            protected double eval(double[] xt) {
                align.copyFrom(bestPreviousAlignmentDeform);
                //align.clear();
                align.setUaxis(xt[0], xt[1], xt[2], xt[3]);
                currentWorkingAlignment = align;
                if (completion < 0) return Double.MAX_VALUE;
                timeStep.start();
                double fit = align.optimize(deform[0], true);
                timeStep.stop();
                timeOptimize.addAndGet(timeStep.delay());
                return fit;
            }

            protected int length() {
                return 4;
            }

        }, params, 0.01);
        params = pow.getP();


        //bestPreviousAlignment.clear();
        bestPreviousAlignmentDeform.setUaxis(params[0], params[1], params[2], params[3]);

        if (completion < 0) return Double.MAX_VALUE;
        currentWorkingAlignment = bestPreviousAlignmentDeform;
        timeStep.start();
        fitness = bestPreviousAlignmentDeform.optimize(true, true);
        timeStep.stop();
        timeOptimize.addAndGet(timeStep.delay());
        System.out.println(
                "final :\nbest rotation 1 = " + IJ.d2s(params[0], 3) +
                        "\ttilt 1 = " + IJ.d2s(params[2]) +
                        "\terror = " + IJ.d2s(fitness, 4) +
                        "\nbest rotation 2 = " + IJ.d2s(params[1], 3) +
                        "\ttilt 2 = " + IJ.d2s(params[3]) +
                        "\terror = " + IJ.d2s(fitness, 4)
        );
        double[] errors2 = bestPreviousAlignmentDeform.computeErrors();
        System.out.println("average worst error is : " + errors2[0]);
        System.out.println("average of average error is : " + errors2[1]);

        completion += completionStep;double zheight = correctAlignment(options.isCorrectForHeight());
        completion += completionStep;

        //add the reprojections of landmarks
        //reprojectedLandmarks = bestPreviousAlignment.getReprojectedLandmarks();
        //System.out.println(bestPreviousAlignment.toString());
        System.out.println("number of cycles of removing landmarks: " + cycle);
        System.out.println(
                "final :\nbest rotation 1 = " + IJ.d2s(params[0], 3) +
                        "\ttilt 1 = " + IJ.d2s(params[2]) +
                        "\terror = " + IJ.d2s(fitness, 4) +
                        "\nbest rotation 2 = " + IJ.d2s(params[1], 3) +
                        "\ttilt 2 = " + IJ.d2s(params[3]) +
                        "\terror = " + IJ.d2s(fitness, 4)
        );
        System.out.println("modified z height by " + zheight);
        IJ.showStatus("final :\nbest rotation 1 = " + IJ.d2s(params[0], 3) +
                "\ttilt 1 = " + IJ.d2s(params[2]) +
                "\terror = " + IJ.d2s(fitness, 4) +
                "\nbest rotation 2 = " + IJ.d2s(params[1], 3) +
                "\ttilt 2 = " + IJ.d2s(params[3]) +
                "\terror = " + IJ.d2s(fitness, 4)
        );
        ResultsTable rt = bestPreviousAlignmentDeform.getAsResultTable();
        rt.show("results");
        PlotWindow2 pw2 = new PlotWindow2();
        pw2.removeAllPlots();
        pw2.parse(rt);
        pw2.resetMinMax();
        pw2.setVisible(true);

        //rt.saveAs();
        completion = 1;

        stp.getTomoJPoints(0).setReprojectedLandmarks(bestPreviousAlignmentDeform.getReprojectedLandmarks(0));
        stp.getTomoJPoints(1).setReprojectedLandmarks(bestPreviousAlignmentDeform.getReprojectedLandmarks(1));
        stp.getTomoJPoints(0).getTiltSeries().setTomoJPoints(stp.getTomoJPoints(0));
        stp.getTomoJPoints(1).getTiltSeries().setTomoJPoints(stp.getTomoJPoints(1));

        bestPreviousAlignmentDeform.correctRjsPositionforTomogram(zheight);
        timeTotal.stop();
        System.out.println("total time to compute : " + timeTotal.delayString());
        System.out.println("total time in optimize : " + Chrono.timeString(timeOptimize.get()));
        return fitness;
    }


    public AlignmentLandmarkDualImproved getBestAlignment() {
        return bestPreviousAlignmentDeform;
    }

    public void setBestAlignment(AlignmentLandmarkDualImproved best) {
        bestPreviousAlignmentDeform = best;
    }

    public alignmentLandmark getAlignmentOld() {
        return bestPreviousAlignment;
    }

//    /**
//     * align using 3D landmarks as described in <BR>
//     * Marker-free image registration of electron tomoj.tomography tilt-series.<BR>
//     * Sanchez Sorzano CO, Messaoudi C, Eibauer M, Bilbao-Castro J, Hegerl R, Nickell S, Marco S, Carazo J.<BR>
//     * BMC Bioinformatics. 2009 Apr 27;10(1):124.
//     *
//     * @param startingRotation starting rotation, if 0 then a first search of rotation is performed on 0-180� range
//     * @param deltaRot         the angle increment for the first search of rotation
//     * @param optimizeAngle    true if angular refinement of tilt angles
//     */
//    public double align3DLandmarks(double startingRotation, double deltaRot, final boolean optimizeAngle, final double psiMax, final boolean correctHeight) {
//        bestPreviousAlignmentDeform=null;
//        System.out.println("maximum rotation allowed : " + psiMax);
//        tp.removeEmptyChains();
//        System.out.println("number of landmarks:" + tp.getNumberOfPoints());
//        final alignmentLandmark align = new alignmentLandmark(tp);
//        align.setPsiMax(psiMax);
//        align.produceInfoFromLandmarks();
//        //System.out.println("align created");
//        bestPreviousAlignment = new alignmentLandmark(tp);
//        bestPreviousAlignment.setPsiMax(psiMax);
//        bestPreviousAlignment.produceInfoFromLandmarks();
//        //System.out.println("best align created");
//        //	ts.combineTransforms(false);
//
//        //exhaustive search for rotation
//        double bestError = 0;
//        double bestRot = -1;
//        if (startingRotation == 0) {
//            System.out.println("exhaustive search for tilt axis");
//            for (double rot = 0; rot <= 180 - deltaRot; rot += deltaRot) {
//                //for(double rot=0;rot<=10;rot+=deltaRot){
//                //System.out.println("rotation "+rot);
//                align.clear();
//                align.setRotation(rot);
//                //System.out.println(align);
//                double error = align.optimizeGivenAxisDirection();
//                System.out.println("rotation=" + IJ.d2s(rot, 3) + " with error=" + IJ.d2s(error, 4));
//                //System.out.println("rotation=" + IJ.d2s(rot, 3) + " with error=" + IJ.d2s(error, 4)+ " error images="+align.computeErrorForLandmarks2());
//                if (bestRot < 0 || bestError > error) {
//                    bestRot = rot;
//                    bestError = error;
//                    bestPreviousAlignment.copyFrom(align);
//                }
//                if(rot==0){
//                    align.saveRjs();
//                }
//            }
//            System.out.println("first search of rotation :\nbest rotation=" + IJ.d2s(bestRot, 3) + "\twith error=" + IJ.d2s(bestError, 4));
//            //System.out.println(bestPreviousAlignment);
//        } else {
//            bestRot = startingRotation + 90;
//            System.out.println("init rotation to " + IJ.d2s(bestRot, 3));
//        }
//        //continuous optimization for the axis direction
//        /*double[] axisAngles = {bestRot};
//        if (optimizeAngle) {
//            axisAngles = new double[2];
//            axisAngles[0] = bestRot;
//            axisAngles[1] = 90;
//            //axisAngles[1]=0;
//        }    */
//
//        //double[] params=new double[ts.getImageStackSize()+1+((optimizeAngle)?1:0)];
//        //Arrays.fill(params,1);
//        double[] params = new double[((optimizeAngle) ? 2 : 1)];
//        params[0] = bestRot;
//        if (optimizeAngle) params[1] = 90;
//
//        //System.out.println("error images:"+bestPreviousAlignment.computeErrorForLandmarks2());
//        Powell pow = new Powell(new Function() {
//            protected double eval(double[] xt) {
//                align.copyFrom(bestPreviousAlignment);
//                align.setRotation(xt[0]);
//                if (optimizeAngle) {
//                    align.setTilt(xt[1]);
//                }
//                //align.setScale(Arrays.copyOfRange(xt,(optimizeAngle?2:1),xt.length));
//                return align.optimizeGivenAxisDirection();
//            }
//
//            protected int length() {
//                if (optimizeAngle) return 2;
//                //if (optimizeAngle) return ts.getImageStackSize()+2;
//                //return ts.getImageStackSize()+1;
//                return 1;
//            }
//
//        }, params, 0.01);
//        params = pow.getP();
//        //bestPreviousAlignment.setRotation(axisAngles[0]);
//        //if(optimizeAngle){
//        //	bestPreviousAlignment.setTilt(axisAngles[1]);
//        //}
//        //double err=bestPreviousAlignment.optimizeGivenAxisDirection();
//        //System.out.println("refinement of rotation :\nbest rotation="+bestPreviousAlignment.getRotation()+"\ttilt="+bestPreviousAlignment.getTilt()+"\terror="+err);
//        //bestPreviousAlignment.printAlignment();
//        /*try {
//              System.in.read();
//          }catch (Exception e){
//              System.out.println(e);
//          }*/
//        //outlier removal
//        bestPreviousAlignment.setRotation(params[0]);
//        if (optimizeAngle) {
//            bestPreviousAlignment.setTilt(params[1]);
//        }
//        //bestPreviousAlignment.setScale(Arrays.copyOfRange(params,(optimizeAngle?2:1),params.length));
//        double fitness = bestPreviousAlignment.optimizeGivenAxisDirection();
//        //bestPreviousAlignment.printAlignment();
//        System.out.println("before computeErrorForLandmarks :\nbest rotation=" + IJ.d2s(bestPreviousAlignment.getRotation(), 3) + "\ttilt=" + IJ.d2s(bestPreviousAlignment.getTilt()) + "\terror=" + IJ.d2s(fitness, 4));
//
//        for (int i = 0; i < 3; i++) {
//            //compute the best alignment
//            //bestPreviousAlignment.printAlignment();
//            bestPreviousAlignment.computeErrorForLandmarks();
//            //remove landmarks that are outliers in the current model
//            removeOutlierLandmarks(bestPreviousAlignment);
//            bestPreviousAlignment.updateLandmarks();
//            //System.out.println("number of landmark in tp "+getNumberOfPoints());
//            alignmentLandmark altmp = new alignmentLandmark(tp);
//            altmp.produceInfoFromLandmarks();
//            altmp.setPsiMax(psiMax);
//            bestPreviousAlignment.copyFrom(altmp);
//            //bestPreviousAlignment.reset();
//            //bestPreviousAlignment.produceInfoFromLandmarks();
//            bestPreviousAlignment.setRotation(params[0]);
//            if (optimizeAngle) {
//                bestPreviousAlignment.setTilt(params[1]);
//            }
//            //bestPreviousAlignment.setScale(Arrays.copyOfRange(params,(optimizeAngle?2:1),params.length));
//            fitness = bestPreviousAlignment.optimizeGivenAxisDirection();
//            //optimize again
//            pow = new Powell(new Function() {
//                protected double eval(double[] xt) {
//                    align.copyFrom(bestPreviousAlignment);
//                    align.setRotation(xt[0]);
//                    if (optimizeAngle) {
//                        align.setTilt(xt[1]);
//                    }
//                    //bestPreviousAlignment.setScale(Arrays.copyOfRange(xt,(optimizeAngle?2:1),xt.length));
//                    return align.optimizeGivenAxisDirection();
//                }
//
//                protected int length() {
//                    if (optimizeAngle) return 2;
//                    //if (optimizeAngle) return ts.getImageStackSize()+2;
//                    //return ts.getImageStackSize()+1;
//                    return 1;
//                }
//
//            }, params, 0.01);
//            params = pow.getP();
//            bestPreviousAlignment.setRotation(params[0]);
//            if (optimizeAngle) {
//                bestPreviousAlignment.setTilt(params[1]);
//            }
//            //bestPreviousAlignment.setScale(Arrays.copyOfRange(params,(optimizeAngle?2:1),params.length));
//
//            fitness = bestPreviousAlignment.optimizeGivenAxisDirection();
//            System.out.println("after removing outliers :\nbest rotation=" + IJ.d2s(bestPreviousAlignment.getRotation(), 3) + "\ttilt=" + IJ.d2s(bestPreviousAlignment.getTilt()) + "\terror=" + IJ.d2s(fitness, 4));
//
//        }
////correct axis Height
//        double zheight=0;
//        if (correctHeight) {
//            System.out.println("correcting height");
//            zheight=bestPreviousAlignment.correctAxisHeight();
//        }
//
//
////output alignment
//        bestPreviousAlignment.printAlignment();
//
//        //update the alignment in tilt series
//        //update0�
//        tp.getTiltSeries().setTiltAxis(0);
//        tp.getTiltSeries().combineTransforms(false);
//        for (int i = 0; i < tp.getTiltSeries().getImageStackSize(); i++) {
//            try {
//                AffineTransform T = bestPreviousAlignment.getTransform(i);
//                tp.getTiltSeries().setTransform(i, T);
//            } catch (Exception E) {
//                System.out.println(E);
//            }
//        }
//        tp.getTiltSeries().convertTolocalTransform();
//        /*ts.setZeroTransform(bestPreviousAlignment.getTransform(ts.getZeroIndex()));
//        //update forward
//        for (int i = ts.getZeroIndex() + 1; i < ts.getImageStackSize(); i++) {
//            try {
//                AffineTransform T = bestPreviousAlignment.getTransform(i).createInverse();
//                AffineTransform T0 = ts.getTransform(i - 1, true, true);
//                T.concatenate(T0);
//                ts.setTransform(i - 1, T);
//            } catch (Exception E) {
//                E.printStackTrace();
//            }
//        }
//        //update backward
//        for (int i = ts.getZeroIndex() - 1; i > 0; i--) {
//            AffineTransform T = bestPreviousAlignment.getTransform(i);
//            try {
//                AffineTransform T0 = ts.getTransform(i + 1, true, true).createInverse();
//                T.preConcatenate(T0);
//                ts.setTransform(i, T);
//            } catch (Exception E) {
//                E.printStackTrace();
//            }
//        } */
//
////add the reprojections of landmarks
//        tp.setReprojectedLandmarks(bestPreviousAlignment.getReprojectedLandmarks());
//        System.out.println("after removing outliers :\nbest rotation=" + IJ.d2s(bestPreviousAlignment.getRotation(), 3) + "\ttilt=" + IJ.d2s(bestPreviousAlignment.getTilt()) + "\terror=" + IJ.d2s(fitness, 4));
//        IJ.showStatus("after removing outliers :\nbest rotation=" + IJ.d2s(bestPreviousAlignment.getRotation(), 3) + "\ttilt=" + IJ.d2s(bestPreviousAlignment.getTilt()) + "\terror=" + IJ.d2s(fitness, 4));
//        bestPreviousAlignment.correctRjsPositionforTomogram(zheight);
//        return fitness;
//
//    }

    //      public void removeOutlierLandmarks(alignmentLandmark align) {
//        //compute threshold for outliers
//        DoubleMatrix1D errorLandmarks = align.getNormalizedErrorLandmarks();
//        //DoubleMatrix1D errorLandmarks = align.getErrorLandmarks();
//        double min = errorLandmarks.getMinLocation()[0];
//        double max = errorLandmarks.getMaxLocation()[0];
//        //DoubleIHistogram1D hist= DoubleStatistic.histogram(new DoubleHistogram1D("hist",100, min, max),errorLandmarks);
//        int[] histo = new int[100];
//        int total = 0;
//        double step_size = (max - min) / 100;
//        for (int i = 0; i < errorLandmarks.size(); i++) {
//            //System.out.print(" "+errorLandmarks.getQuick(i));
//            //int index=(int)((errorLandmarks.getQuick(i)-min)/(max-min)*99);
//            int index = (int) ((errorLandmarks.getQuick(i) - min) / step_size);
//            if (index >= histo.length) index = histo.length - 1;
//            histo[index]++;
//            total++;
//        }
//        double threshold0 = Double.NaN;
//        double thresholdF = Double.NaN;
//        //int total=hist.allEntries();
//        double sum = 0;
//        for (int i = 0; i < 100; i++) {
//            //sum+=hist.binEntries(i);
//            sum += histo[i];
//            if (Double.isNaN(threshold0) && sum / total > 0.1) {
//                threshold0 = i;
//                //System.out.println("threshold0"+threshold0+" i="+i+" sum="+sum+" total="+total+" sum/total="+(sum/total));
//            }
//            if (Double.isNaN(thresholdF) && sum / total > 0.9) {
//                thresholdF = i;
//                //System.out.println("thresholdF"+thresholdF+" i="+i+" sum="+sum+" total="+total+" sum/total="+(sum/total));
//            }
//        }
//        threshold0 *= step_size;
//        threshold0 += min;
//        thresholdF *= step_size;
//        thresholdF += min;
//        //System.out.println("min="+min+" max="+max);
//        //System.out.println("threshold0="+threshold0);
//        //System.out.println("thresholdF="+thresholdF);
//        int count = 0;
//        for (int i = (int) errorLandmarks.size() - 1; i >= 0; i--) {
//            double currentError = errorLandmarks.getQuick(i);
//            if (!tp.isAutomaticallyGenerated(i)) {
//                System.out.println("could not remove point " + i + " because manually defined");
//            } else if ((currentError > thresholdF)) {
//                //else  if((currentError>thresholdF||currentError<threshold0)){
//                tp.removeSetOfPoints(i);
//                count++;
//                //System.out.println("remove landmark "+i+" error="+currentError+" thresholds:"+threshold0+" "+thresholdF);
//
//            }
//        }
//        System.out.println("remove outliers : " + count + " landmarks removed");
//        align.updateNumberOfLandmarks();
//
//    }
//
//
//    public ImagePlus create3DLandmarksImage(int width, int height, int thickness) {
//        if (bestPreviousAlignment != null) {
//            return bestPreviousAlignment.create3DLandmarksImage(width, height, thickness);
//        }else if(bestPreviousAlignmentDeform !=null){
//            return bestPreviousAlignmentDeform.create3DLandmarksImage(width,height,thickness);
//        }
//
//        return null;
//    }
//
//    public DoubleMatrix2D getLandmark3D(int index){
//        return bestPreviousAlignmentDeform.getRj(index);
//    }
//
    public double correctAlignment(boolean correctZHeight) {
        double zheight = 0;
        if (correctZHeight) {
            System.out.println("correcting height");
            zheight = bestPreviousAlignmentDeform.correctAxisHeight();
        }

        for (int j = 0; j < stp.getSize(); j++) {
            stp.getTomoJPoints(j).getTiltSeries().setTiltAxis(0);
            /*stp.getTomoJPoints(j).getTiltSeries().combineTransforms(false);
            for (int i = (j == 0) ? 0 : stp.getTomoJPoints(j - 1).getTiltSeries().getImageStackSize(); i < stp.getTomoJPoints(j).getTiltSeries().getImageStackSize(); i++) {
                try {
                    bestPreviousAlignmentDeform.getCurrentAi().get(i).computeAi();
                    AffineTransform T = bestPreviousAlignmentDeform.getTransform(i);
                    stp.getTomoJPoints(j).getTiltSeries().setTransform(i, T);
                } catch (Exception E) {
                    System.out.println(E);
                }
            }
            stp.getTomoJPoints(j).getTiltSeries().convertTolocalTransform();
            double[] globalAli = stp.getTomoJPoints(j).getTiltSeries().getGlobalOrientation();
            if (globalAli == null) globalAli = new double[]{0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0};
            globalAli[5] = zheight;
            stp.getTomoJPoints(j).getTiltSeries().setGlobalOrientation(globalAli);             */
        }

        return zheight;
    }

    /**
     * @param index of the TiltSeries (for dual-axis tilt-series, equals 0 or 1)
     * @return
     */
    public double getBestTiltAxisOrientation(int index) {
        return bestPreviousAlignmentDeform.getCurrentAi().get((index == 0) ? 0 : stp.getnImagesi(0)).getRot();
    }

    /**
     * @return SuperTomoJPoints
     */
    public SuperTomoJPoints getSuperTomoJPoints() {
        return stp;
    }

}
