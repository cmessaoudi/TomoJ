package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.landmarks.LandmarksGenerator;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.application.Application;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

/**
 * Created by cedric on 29/09/2016.
 */
public class GridLandmarksGenerator implements Application {

    protected LandmarksGenerator generator;
    protected TiltSeries ts;
    protected TomoJPoints tp;
    int nbSeeds = 400;
    int chainLength;
    int patchSize;
    double correlationThreshold = 0.9;
    int refinementSteps = 2;
    boolean firstDisplay = true;

    String resultString;

    private JCheckBox expertModeCheckBox;
    private JSpinner spinnerChainLength;
    private JSpinner spinnerNbSeeds;
    private JSpinner spinnerPatchSize;
    private JSpinner spinnerNbRefinement;
    private JSpinner spinnerCorrelationThreshold;
    private JPanel basePanel;

    public GridLandmarksGenerator(TiltSeries ts, TomoJPoints tp) {
        this.ts = ts;
        this.tp = tp;
        generator = new LandmarksGenerator(tp, ts);
        $$$setupUI$$$();
        chainLength = Math.max(ts.getImageStackSize() / 4, 3);
        patchSize = (int) (10 * ts.getWidth() / 256.0 + 1);
    }

    private void addListeners() {
        spinnerNbSeeds.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                nbSeeds = ((SpinnerNumberModel) spinnerNbSeeds.getModel()).getNumber().intValue();

            }
        });
        spinnerChainLength.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                chainLength = ((SpinnerNumberModel) spinnerChainLength.getModel()).getNumber().intValue();
            }
        });
        spinnerPatchSize.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                patchSize = ((SpinnerNumberModel) spinnerPatchSize.getModel()).getNumber().intValue();
            }
        });
        spinnerCorrelationThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                correlationThreshold = ((SpinnerNumberModel) spinnerCorrelationThreshold.getModel()).getNumber().doubleValue();
            }
        });
        expertModeCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean isSelected = expertModeCheckBox.isSelected();
                spinnerNbRefinement.setEnabled(isSelected);
            }
        });


    }

    private void initValues() {

        ((SpinnerNumberModel) spinnerNbSeeds.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerNbSeeds.getModel()).setValue(nbSeeds);
        ((SpinnerNumberModel) spinnerNbSeeds.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerChainLength.getModel()).setMinimum(3);
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setMaximum(ts.getImageStackSize());
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setValue(chainLength);
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setMinimum(3);
        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setMaximum(ts.getWidth());
        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setValue(patchSize);
        ((SpinnerNumberModel) spinnerPatchSize.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerNbRefinement.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerNbRefinement.getModel()).setMaximum(50);
        ((SpinnerNumberModel) spinnerNbRefinement.getModel()).setValue(refinementSteps);
        ((SpinnerNumberModel) spinnerNbRefinement.getModel()).setStepSize(1);
    }

    public boolean run() {
        OutputStreamCapturer capture = new OutputStreamCapturer();
        System.out.println(getParametersValuesAsString());
        /*if(previewCritical!=null) {
            previewCritical.close();
            previewCritical=null;
        }*/

        //generator.generateLandmarkSetWithBackAndForthValidation(chainLength, nbSeeds, patchSize, correlationThreshold, false, false, true);
        Chrono time = new Chrono();
        time.start();
        generator.generateLandmarkSet2(chainLength, nbSeeds, patchSize, refinementSteps, correlationThreshold, false, false, false);
        time.stop();
        System.out.println("generate landmarks grid finished in " + time.delayString());
        System.out.println(tp.getNumberOfPoints() + " landmarks chains created");
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        return true;
    }

    public void setParameters(Object... parameters) {

    }

    public String help() {
        return "Critical Landmarks Generator \n" +
                "\n" +
                "    nbSeeds : number of points in the grid to apply to each images\n" +
                "    chainLength : minimum length of landmarks chains created\n" +
                "    patchSize : size of the patch used in the tracking of landmarks (square patch of size patchSize*patchSize)\n" +
                "    correlationThreshold : threshold used during tracking to tell if tracking should continue or stop\n" +
                "    refinementSteps : number of time the chains are refined by local corelation ";
    }

    public String name() {
        return "Landmarks generation using grid seeds";
    }

    public ArrayList<Object> getResults() {
        ArrayList<Object> result = new ArrayList<Object>();
        result.add(resultString);
        return result;
    }

    public ArrayList<Object> getParametersType() {
        return null;
    }

    public ArrayList<String> getParametersName() {
        return null;
    }

    public JPanel getJPanel() {
        if (firstDisplay) {
            initValues();
            addListeners();
            firstDisplay = false;
        }
        return this.basePanel;
    }

    public void interrupt() {
        generator.interrupt();
    }

    public double getCompletion() {
        return generator.getCompletion();
    }


    public String getParametersValuesAsString() {
        String params = "grid landmarks generation:\n" +
                "number of seeds: " + nbSeeds + "\n" +
                "length of tracked chains: " + chainLength + "\n" +
                "patch size: " + patchSize + "\tcorrelation threshold: " + correlationThreshold + "\n" +
                "refinement steps: " + refinementSteps + "\n";

        return params;
    }

    public void setDisplayPreview(boolean display) {

    }

    private void createUIComponents() {
        spinnerCorrelationThreshold = new JSpinner(new SpinnerNumberModel(correlationThreshold, 0.001, 1, 0.01));
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
        basePanel.setLayout(new GridLayoutManager(7, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Landmarks Generation Using Seeds on Grid", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label1 = new JLabel();
        label1.setText("Number of points in grid");
        basePanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Minimum length of chains");
        basePanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Patch size (pixels)");
        basePanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Number of refinement steps");
        basePanel.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Correlation threshold");
        basePanel.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        expertModeCheckBox = new JCheckBox();
        expertModeCheckBox.setText("Expert mode");
        basePanel.add(expertModeCheckBox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerChainLength = new JSpinner();
        basePanel.add(spinnerChainLength, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbSeeds = new JSpinner();
        basePanel.add(spinnerNbSeeds, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerPatchSize = new JSpinner();
        basePanel.add(spinnerPatchSize, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerNbRefinement = new JSpinner();
        spinnerNbRefinement.setEnabled(false);
        basePanel.add(spinnerNbRefinement, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        basePanel.add(spinnerCorrelationThreshold, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        basePanel.add(spacer1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
