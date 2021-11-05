package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.plotj.PlotWindow2;
import ij.IJ;
import ij.Prefs;
import ij.measure.ResultsTable;
import fr.curie.tomoj.tomography.*;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.workflow.UserAction;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by cedric on 27/03/2017.
 */
public class WBPReconstructionApplication extends ReconstructionApplication {
    private JCheckBox weightingCheckBox;
    private JSpinner spinnerWeighting;
    private JCheckBox elongationCorrectionCheckBox;
    private JPanel panelRoot;
    boolean weighting = true;
    boolean elongationCorrection = false;
    double weightinDiameter = 0.5;
    TiltSeries ts;
    boolean firstDisplay;

    public WBPReconstructionApplication(TiltSeries ts) {
        this.ts = ts;
        firstDisplay = true;
    }

    public boolean run() {
        if (width == 0) width = ts.getWidth();
        if (height == 0) height = ts.getHeight();
        if (depth == 0) depth = ts.getWidth();

        final OutputStreamCapturer capture = new OutputStreamCapturer();
        final int fillingType = ts.getFillType();
        if (fillingType == TiltSeries.FILL_NaN) ts.setFillType(TiltSeries.FILL_AVG);

        String title = (rec != null) ? rec.getTitle() : ts.getTitle();
        UserAction ua;
        //final ReconstructionParameters recParams;

        if (weighting) {
            title += "WBP";
            ua = new UserAction("WBP", "diameter=" + weightinDiameter, "WBP", false);
            ua.setCurrentReconstruction("WBP");
            recParams = ReconstructionParameters.createWBPParameters(width, height, depth, weightinDiameter);
        } else {
            title += "BP";
            ua = new UserAction("BP", "", "BP", false);
            ua.setCurrentReconstruction("BP");
            recParams = ReconstructionParameters.createWBPParameters(width, height, depth, Double.NaN);
            recParams.setRescaleData(rescaleData);

        }

        final String ftitle = title;
        if (computeOnGPU) {
            ResolutionEstimationGPU resolutionComputation = new ResolutionEstimationGPU(ts, recParams);
            resolutionComputation.setUse(use);
            resolutionComputation.setDevices(gpuDevices);
            this.resolutionComputation = resolutionComputation;
        } else {
            resolutionComputation = new ResolutionEstimation(ts, recParams);
        }

        if (ts.isShowInIJ()) IJ.log(getParametersValuesAsString());

        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
        final Chrono time = new Chrono();

        if (computeSSNR) {
            System.out.println("compute SSNR (with reconstruction)");
            double[][] result = resolutionComputation.SSNR(computeVSSNR);
            double[] x = new double[ts.getWidth() / 2];
            double[] y = new double[ts.getWidth() / 2];
            ResultsTable rt = new ResultsTable();
            for (int i = 0; i < ts.getWidth() / 2; i++) {
                x[i] = result[1][i];
                y[i] = result[2][i];
                System.out.println(result[0][i] + " " + result[1][i] + " " + result[2][i] + " " + result[3][i] + " " + result[4][i] + " " + result[5][i] + " " + result[6][i] + " " + result[7][i] + " " + result[8][i] + " ");
                rt.incrementCounter();
                rt.addValue("index", result[0][i]);
                rt.addValue("frequence", result[1][i]);
                rt.addValue("SSNR", result[2][i]);
            }
            rt.setPrecision(5);
            rt.showRowNumbers(true);
            rt.show("SSNR");

            PlotWindow2 pw = new PlotWindow2();
            pw.removeAllPlots();
            pw.addPlot(x, y, Color.RED, "SSNR");
            pw.resetMinMax();
            pw.setVisible(true);
            rec = resolutionComputation.getReconstructionSignal();

        } else if (!fscOnly) {
            System.out.println("compute reconstruction");
            /*final ReconstructionThreadManager recthman = new ReconstructionThreadManager(ts.getWindow(), ts);
            recthman.setUse(use);
            if (rec2 != null) recthman.setRec2(rec2);
            recthman.reconstruct(recParams);
            rec2 = recthman.getRec2();*/
            resolutionComputation.doSignalReconstruction();
            rec = resolutionComputation.getReconstructionSignal();
        }
        if (computeFSC) {
            System.out.println("compute FSC");
            double[][] resultfsc = resolutionComputation.fsc();
            System.out.println("fsc formating results");
            double[] x = resultfsc[0];
            double[] y = resultfsc[1];
            double[] half = new double[x.length];
            Arrays.fill(half, 0.5);
            ResultsTable rt = new ResultsTable();
            for (int i = 0; i < x.length; i++) {
                System.out.println("" + x[i] + "\t" + y[i]);
                rt.incrementCounter();
                rt.addValue("index", i);
                rt.addValue("frequence", x[i]);
                rt.addValue("FSC", y[i]);
            }
            rt.setPrecision(5);
            rt.showRowNumbers(true);
            rt.show("FSC");

            PlotWindow2 pw = new PlotWindow2();
            pw.removeAllPlots();
            pw.addPlot(x, y, Color.RED, "FSC");
            pw.addPlot(x, resultfsc[2], Color.BLUE, "FSC_noise");
            pw.addPlot(x, resultfsc[3], Color.GREEN, "error L2");
            pw.addPlot(x, half, Color.BLACK, "0.5 threshold");
            pw.resetMinMax();
            pw.setVisible(true);
        }

        //finalisation
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        if (ts.isShowInIJ()) IJ.log("total time to compute : " + time.delayString());
        if (rec != null) {
            rec.show();
            rec.setTitle(ftitle);
            rec.setSlice(rec.getImageStackSize() / 2);
        }
        Prefs.set("TOMOJ_Thickness.int", depth);
        int recChoiceIndex = (computeOnGPU) ? 10 : 0;
        if (weighting) recChoiceIndex += 1;
        Prefs.set("TOMOJ_wbp_diameter.double", weightinDiameter);

        ts.setThresholdHisteresis(Double.MIN_VALUE, Double.MIN_VALUE, false);
        ts.setFillType(fillingType);

        return true;
    }

    public void addListeners() {
        weightingCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                weighting = weightingCheckBox.isSelected();
                spinnerWeighting.setEnabled(weighting);
            }
        });
        elongationCorrectionCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                elongationCorrection = elongationCorrectionCheckBox.isSelected();
            }
        });
        spinnerWeighting.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                weightinDiameter = ((Number) spinnerWeighting.getValue()).doubleValue();
            }
        });

    }

    private void initValues() {
        weighting = false;
        elongationCorrection = false;
        weightinDiameter = 0.3;

        weightingCheckBox.setSelected(weighting);
        elongationCorrectionCheckBox.setSelected(elongationCorrection);
        spinnerWeighting.setValue(weightinDiameter);
        spinnerWeighting.setEnabled(weighting);
    }


    public String getParametersValuesAsString() {
        String text = "###   reconstruction   ###";
        if (weighting) text += "\nWBP (weight:" + weightinDiameter + ")";
        else text += "\nBP";
        if (elongationCorrection) text += "\nelongation correction activated";
        text += super.getParametersValuesAsString();
        String type = "";
        switch (ts.getAlignMethodForReconstruction()) {
            case TiltSeries.ALIGN_AFFINE2D:
                type = "Affine 2D";
                break;
            case TiltSeries.ALIGN_NONLINEAR:
                type = "2D Non-linear";
                break;
            case TiltSeries.ALIGN_PROJECTOR:
            default:
                type = "3D projector";
                break;
        }
        text += "\napply alignment as : " + type;
        return text;
    }

    public void setParameters(Object... parameters) {
        super.setParameters(parameters);
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index] instanceof String) {
                if (((String) parameters[index]).toLowerCase().equals("noweighting")) {
                    weighting = false;
                } else if (((String) parameters[index]).toLowerCase().equals("weightdiameter")) {
                    weighting = true;
                    if (parameters[index + 1] instanceof String)
                        weightinDiameter = Double.parseDouble((String) parameters[index + 1]);
                    else weightinDiameter = (Double) parameters[index + 1];
                    index += 1;
                } else if (((String) parameters[index]).toLowerCase().equals("elongationcorrection")) {
                    elongationCorrection = true;
                }
            }
        }
    }

    public static String help() {
        return ReconstructionApplication.help() + "### weighted backprojection algorithm  ###\n" +
                "noweighting: removes the weighting part of algorithm\n" +
                "weightdiameter value: diameter used for weighting\n" +
                "elongationcorrection: attempts to correct the elongation due to missing-wedge\n" +
                "";
    }

    public JPanel getJPanel() {
        if (firstDisplay) {
            addListeners();
            initValues();
        }
        return panelRoot;
    }

    private void createUIComponents() {
        spinnerWeighting = new JSpinner(new SpinnerNumberModel(0.3, 0.0001, 0.5, 0.01));
    }


    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        panelRoot = new JPanel();
        panelRoot.setLayout(new GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
        weightingCheckBox = new JCheckBox();
        weightingCheckBox.setText("weighting");
        panelRoot.add(weightingCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panelRoot.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        panelRoot.add(spinnerWeighting, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("diameter proportion");
        panelRoot.add(label1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panelRoot.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        elongationCorrectionCheckBox = new JCheckBox();
        elongationCorrectionCheckBox.setText("elongation correction");
        panelRoot.add(elongationCorrectionCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panelRoot;
    }

}
