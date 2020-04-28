package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.gpu.tomoj.tomography.ResolutionEstimationGPU;
import fr.curie.tomoj.tomography.*;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.application.ReconstructionApplication;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class ReconstructionTVMApplication extends ReconstructionApplication {
    private JSpinner spinnerNbIteration;
    private JSpinner spinnerRelaxationCoefficent;
    private JCheckBox resinOrCryoSampleCheckBox;
    private JSpinner spinnerTVMg;
    private JSpinner spinnerTVMdt;
    private JSpinner spinnerTVMtheta;
    private JSpinner spinnerTVMiterations;
    private JPanel basePanel;

    protected double theta = 25;
    protected double g = 1;
    protected double dt = 0.1;
    protected int tvmiterations = 5;
    protected int nbiterations = 10;
    protected double relaxationCoefficient = 1;
    protected boolean longObjectCompensation = true;
    protected TiltSeries ts;
    protected boolean firstDisplay;

    public ReconstructionTVMApplication(TiltSeries ts) {
        this.ts = ts;
        firstDisplay = true;
    }

    public void initValues() {

        spinnerNbIteration.setValue(nbiterations);
        spinnerRelaxationCoefficent.setValue(relaxationCoefficient);
        spinnerTVMtheta.setValue(theta);
        resinOrCryoSampleCheckBox.setSelected(longObjectCompensation);
        spinnerTVMg.setValue(g);
        spinnerTVMdt.setValue(dt);
        spinnerTVMiterations.setValue(tvmiterations);

    }

    private void addListeners() {
        spinnerNbIteration.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbiterations = ((Number) spinnerNbIteration.getValue()).intValue();
            }
        });
        spinnerRelaxationCoefficent.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                relaxationCoefficient = ((Number) spinnerRelaxationCoefficent.getValue()).doubleValue();

            }
        });
        resinOrCryoSampleCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                longObjectCompensation = resinOrCryoSampleCheckBox.isSelected();

            }
        });
        spinnerTVMg.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                g = ((Number) spinnerTVMg.getValue()).doubleValue();
            }
        });
        spinnerTVMtheta.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                theta = ((Number) spinnerTVMtheta.getValue()).doubleValue();
            }
        });
        spinnerTVMdt.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                dt = ((Number) spinnerTVMdt.getValue()).doubleValue();

            }
        });
        spinnerTVMiterations.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                tvmiterations = ((Number) spinnerTVMiterations.getValue()).intValue();
            }
        });

    }

    public boolean run() {
        final Chrono time = new Chrono();
        final OutputStreamCapturer capture = new OutputStreamCapturer();
        final ReconstructionParameters params = AdvancedReconstructionParameters.createTVMParameters(width, height, depth, nbiterations, relaxationCoefficient, theta, g, dt, tvmiterations);
        params.setLongObjectCompensation(longObjectCompensation);
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

    public void setParameters(Object... parameters) {

    }

    public String help() {
        return null;
    }

    public String name() {
        return null;
    }


    public ArrayList<Object> getParametersType() {
        return null;
    }

    public ArrayList<String> getParametersName() {
        return null;
    }

    public String getParametersValuesAsString() {
        String text = super.getParametersValuesAsString();
        text += "\nReconstruction " + nbiterations + " iterations, relaxation" + relaxationCoefficient;
        text += "\nTVM (theta=" + theta + ", g=" + g + ", dt=" + dt + ", nbiterations=" + tvmiterations;
        return text;
    }

    public String getParametersValuesAsShortString() {
        String text = "RecTVM_" + nbiterations + "ite_" + relaxationCoefficient + "rel_TV_theta_" + theta + "_g_" + g + "_dt_" + dt + "_tvmite" + tvmiterations + ")";
        return text;
    }

    public JPanel getJPanel() {
        if (firstDisplay) {
            addListeners();
            initValues();
        }
        return basePanel;

    }

    private void createUIComponents() {
        spinnerRelaxationCoefficent = new JSpinner(new SpinnerNumberModel(0.1, 0.001, 2, 0.01));
        spinnerTVMdt = new JSpinner(new SpinnerNumberModel(dt, 0.0001, 1, 0.001));
        spinnerTVMg = new JSpinner(new SpinnerNumberModel(g, -0.5, 5, 0.01));
        spinnerTVMtheta = new JSpinner(new SpinnerNumberModel(theta, 0, 50000, 0.01));

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
        basePanel = new JPanel();
        basePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JScrollPane scrollPane1 = new JScrollPane();
        basePanel.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        scrollPane1.setViewportView(panel1);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "reconstruction parameters"));
        final JLabel label1 = new JLabel();
        label1.setText("number of iterations");
        panel2.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbIteration = new JSpinner();
        panel2.add(spinnerNbIteration, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("relaxation coefficient");
        panel2.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(spinnerRelaxationCoefficent, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resinOrCryoSampleCheckBox = new JCheckBox();
        resinOrCryoSampleCheckBox.setText("resin or cryo sample");
        panel2.add(resinOrCryoSampleCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "TVM parameters"));
        final JLabel label3 = new JLabel();
        label3.setText("theta");
        panel3.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel3.add(spinnerTVMtheta, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("g");
        panel3.add(label4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel3.add(spinnerTVMg, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("dt");
        panel3.add(label5, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel3.add(spinnerTVMdt, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("number of iterations");
        panel3.add(label6, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerTVMiterations = new JSpinner();
        panel3.add(spinnerTVMiterations, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
