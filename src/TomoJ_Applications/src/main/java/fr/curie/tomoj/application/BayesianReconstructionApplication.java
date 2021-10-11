package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.tomoj.tomography.AdvancedReconstructionParameters;
import fr.curie.tomoj.tomography.ReconstructionParameters;
import fr.curie.tomoj.tomography.ResolutionEstimation;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BayesianReconstructionApplication extends ReconstructionApplication {
    TiltSeries ts;
    protected boolean firstDisplay;
    private JPanel rootPanel;
    private JCheckBox resinOrCryoSampleCheckBox;
    private JSpinner spinnerIterations;
    private JSpinner spinnerrelaxation;
    private JCheckBox fistaOptimizationCheckBox;
    private JSpinner spinnerregulWeight;


    protected int nbiterations = 10;
    protected double relaxationCoefficient = 1;
    protected double bayesianWeight = 0.1;
    protected boolean longObjectCompensation = true;
    protected boolean fista = true;

    public BayesianReconstructionApplication(TiltSeries ts) {
        this.ts = ts;
        firstDisplay = true;
    }

    @Override
    public boolean run() {
        if (width == 0) width = ts.getWidth();
        if (height == 0) height = ts.getHeight();
        if (depth == 0) depth = ts.getWidth();

        final Chrono time = new Chrono();
        final OutputStreamCapturer capture = new OutputStreamCapturer();
        final ReconstructionParameters params = AdvancedReconstructionParameters.createBayesianParameters(width, height, depth, nbiterations, relaxationCoefficient, bayesianWeight);
        params.setLongObjectCompensation(longObjectCompensation);
        params.setFista(fista);
        System.out.println(getParametersValuesAsString());
        System.out.println("*******\n" + params.asString());

        if (computeOnGPU) {
            ResolutionEstimationGPU resolutionComputation = new ResolutionEstimationGPU(ts, params);
            resolutionComputation.setUse(use);
            resolutionComputation.setDevices(gpuDevices);
            this.resolutionComputation = resolutionComputation;
        } else {
            resolutionComputation = new ResolutionEstimation(ts, params);
        }

        resolutionComputation.doSignalReconstruction();
        rec = resolutionComputation.getReconstructionSignal();
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        if (rec != null) {
            rec.show();
            String title = (rec != null) ? rec.getTitle() : ts.getTitle();
            rec.setTitle(title + getParametersValuesAsShortString());
            rec.setSlice(rec.getImageStackSize() / 2);
        }
        return true;
    }

    @Override
    public JPanel getJPanel() {
        if (firstDisplay) {
            addListeners();
            initValues();
        }
        return rootPanel;
    }

    public void initValues() {

        spinnerIterations.setValue(nbiterations);
        spinnerrelaxation.setValue(relaxationCoefficient);
        spinnerregulWeight.setValue(bayesianWeight);
        resinOrCryoSampleCheckBox.setSelected(longObjectCompensation);
        fistaOptimizationCheckBox.setSelected(fista);

    }

    private void addListeners() {
        spinnerIterations.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbiterations = ((Number) spinnerIterations.getValue()).intValue();
            }
        });
        spinnerrelaxation.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                relaxationCoefficient = ((Number) spinnerrelaxation.getValue()).doubleValue();

            }
        });
        resinOrCryoSampleCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                longObjectCompensation = resinOrCryoSampleCheckBox.isSelected();

            }
        });
        fistaOptimizationCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fista = fistaOptimizationCheckBox.isSelected();
            }
        });
        spinnerregulWeight.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                bayesianWeight = ((Number) spinnerregulWeight.getValue()).doubleValue();
            }
        });

    }

    public String getParametersValuesAsString() {
        String text = super.getParametersValuesAsString();
        text += "\nReconstruction " + nbiterations + " iterations, relaxation" + relaxationCoefficient;
        text += "\nBayesian weight=" + bayesianWeight;
        return text;
    }

    public String getParametersValuesAsShortString() {
        String text = "RecBayesian_" + nbiterations + "ite_" + relaxationCoefficient + "relaxation_" + bayesianWeight + "_bayesianWeight_";
        return text;
    }

    private void createUIComponents() {
        spinnerrelaxation = new JSpinner(new SpinnerNumberModel(0.1, 0.001, 2, 0.01));
        spinnerregulWeight = new JSpinner(new SpinnerNumberModel(0.1, 0.001, 2, 0.01));
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
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "reconstruction parameters", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label1 = new JLabel();
        label1.setText("number of iterations");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("relaxation coefficient");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resinOrCryoSampleCheckBox = new JCheckBox();
        resinOrCryoSampleCheckBox.setText("resin or cryo sample");
        panel1.add(resinOrCryoSampleCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerIterations = new JSpinner();
        panel1.add(spinnerIterations, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerrelaxation, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fistaOptimizationCheckBox = new JCheckBox();
        fistaOptimizationCheckBox.setText("Fista optimization");
        panel1.add(fistaOptimizationCheckBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Bayesian parameter", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label3 = new JLabel();
        label3.setText("weight");
        panel2.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(spinnerregulWeight, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }
}
