package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import ij.IJ;
import ij.io.FileInfo;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.tomoj.landmarks.AlignmentLandmarksOptions;
import fr.curie.tomoj.landmarks.Landmarks3DAlign;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;
//import fr.curie..tomoj.application.Application;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Created by cedric on 23/02/2017.
 */
public class AlignWithLandmarks implements Application {
    protected boolean firstDisplay = true;
    protected boolean oldAlgo = false;

    protected Landmarks3DAlign alignator;
    protected TiltSeries ts;
    protected TomoJPoints tp;
    protected double oldRotation = 0;
    protected boolean oldCorrectHeight = true;


    protected boolean newExhaustiveSearch = true;
    protected double newMahalanobisWeight = 0;
    protected boolean newShift = true;
    protected boolean newMag = false;
    protected boolean newShrink = false;
    protected boolean newScaleX = false;
    protected boolean newShear = false;
    protected boolean newRotation = false;
    protected int newCycleNumber = -1;
    protected double newSelectionThreshold = 8;
    protected boolean newCorrectHeight = true;


    private JPanel basePanel;
    private JComboBox comboBoxAlgo;
    private JPanel panelOld;
    private JSpinner spinnerOldRotationMax;
    private JCheckBox correctOldHeightCheckBox;
    private JPanel panelDeform;
    private JCheckBox exhaustiveSearchCheckBox;
    private JSpinner spinnerNewMahalanobisWeight;
    private JCheckBox shiftsCheckBox;
    private JCheckBox magnificationCheckBox;
    private JCheckBox shrinkageCheckBox;
    private JCheckBox rotationInPlaneCheckBox;
    private JCheckBox scaleXCheckBox;
    private JCheckBox shearCheckBox;
    private JSpinner spinnerNewCycleNumber;
    private JSpinner spinnerNewSelectionThreshold;
    private JCheckBox correctNewHeightCheckBox;
    protected Chrono time;
    String resultString;


    public AlignWithLandmarks(TiltSeries ts, TomoJPoints tp) {
        this.ts = ts;
        this.tp = tp;
        time = new Chrono();
        $$$setupUI$$$();
        correctNewHeightCheckBox.setVisible(false);
        if (alignator == null) {
            alignator = new Landmarks3DAlign(tp);
            System.out.println("create align");
        }
        newExhaustiveSearch = exhaustiveSearchCheckBox.isSelected();
        newMahalanobisWeight = ((Number) spinnerNewMahalanobisWeight.getValue()).doubleValue();
        newShift = shiftsCheckBox.isSelected();
        newMag = magnificationCheckBox.isSelected();
        newShrink = shrinkageCheckBox.isSelected();
        newScaleX = scaleXCheckBox.isSelected();
        newShear = shearCheckBox.isSelected();
        newRotation = rotationInPlaneCheckBox.isSelected();
        newCycleNumber = ((Number) spinnerNewCycleNumber.getValue()).intValue();
        newSelectionThreshold = ((Number) spinnerNewSelectionThreshold.getValue()).doubleValue();
        newCorrectHeight = correctNewHeightCheckBox.isSelected();

        oldCorrectHeight = correctOldHeightCheckBox.isSelected();
        oldRotation = ((Number) spinnerOldRotationMax.getValue()).doubleValue();
        //tp.setLandmarks3D(alignator);
    }

    protected void addListeners() {
        comboBoxAlgo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switch (comboBoxAlgo.getSelectedIndex()) {
                    case 0:
                        panelOld.setVisible(true);
                        panelDeform.setVisible(false);
                        oldAlgo = true;
                        break;
                    case 1:
                    default:
                        panelOld.setVisible(false);
                        panelDeform.setVisible(true);
                        oldAlgo = false;
                }
            }
        });
        comboBoxAlgo.setSelectedIndex(1);
        spinnerOldRotationMax.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                oldRotation = ((Number) spinnerOldRotationMax.getValue()).doubleValue();
                System.out.println("change old rotation to : " + oldRotation);
            }
        });
        correctOldHeightCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                oldCorrectHeight = correctOldHeightCheckBox.isSelected();
            }
        });
        exhaustiveSearchCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newExhaustiveSearch = exhaustiveSearchCheckBox.isSelected();
            }
        });
        shiftsCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newShift = shiftsCheckBox.isSelected();
            }
        });
        magnificationCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newMag = magnificationCheckBox.isSelected();
            }
        });
        shrinkageCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newShrink = shrinkageCheckBox.isSelected();
            }
        });
        scaleXCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newScaleX = scaleXCheckBox.isSelected();
            }
        });
        shearCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newShear = shearCheckBox.isSelected();
            }
        });
        rotationInPlaneCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newRotation = rotationInPlaneCheckBox.isSelected();
            }
        });
        correctNewHeightCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                newCorrectHeight = correctNewHeightCheckBox.isSelected();
            }
        });
        spinnerNewMahalanobisWeight.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                newMahalanobisWeight = ((Number) spinnerNewMahalanobisWeight.getValue()).doubleValue();
            }
        });
        spinnerNewCycleNumber.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                newCycleNumber = ((Number) spinnerNewCycleNumber.getValue()).intValue();
            }
        });
        spinnerNewSelectionThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                newSelectionThreshold = ((Number) spinnerNewSelectionThreshold.getValue()).doubleValue();
            }
        });
    }

    public boolean run() {
        ts.updatePoint();
        //save();
        for (int j = tp.getNumberOfPoints() - 1; j >= 0; j--) {
            Point2D[] tmppts = tp.getPoints(j);
            int count = 0;
            for (Point2D tmppt : tmppts) {
                if (tmppt != null) count++;
            }
            if (count == 0) {
                System.out.println("remove landmark at index " + j + " : it has no defined points");
                tp.removeSetOfPoints(j);
            }
        }

        final AlignmentLandmarksOptions options = new AlignmentLandmarksOptions();
        options.setExhaustiveSearch(newExhaustiveSearch);
        options.setMahalanobisWeight(newMahalanobisWeight);
        options.setAllowShifts(newShift);
        options.setDeformShrinkage(newShrink);
        options.setDeformMagnification(newMag);
        options.setDeformScalingX(newScaleX);
        options.setDeformDelta(newShear);
        options.setAllowInPlaneRotation(newRotation);
        options.setNumberOfCycles(newCycleNumber);
        options.setK(newSelectionThreshold);
        options.setCorrectForHeight(newCorrectHeight);

        OutputStreamCapturer outputCapture = new OutputStreamCapturer();
        final Chrono time = new Chrono(1);
        time.start();


        double score = (oldAlgo) ? alignator.align3DLandmarks(0, 5, true, oldRotation, oldCorrectHeight) : alignator.align3DLandmarksWithDeformation(options);
        //                double score = ts.align3DLandmarks(maxrot, correctHeight);
        if (!oldAlgo) {
            double[] errors = alignator.getBestAlignment().computeErrors();
            System.out.println("average worst error is : " + errors[0]);
            System.out.println("average of average error is : " + errors[1]);
        }
        time.stop();
        System.out.println("total time: " + time.delayString());
        resultString = outputCapture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        //tp.loadAlignmentLandmark("D:\\images_test\\test TomoJ\\ForCedric\\TomoJ\\scaled_alignment.txt");
        //Roi toto = null;
        //ts.setRoi(toto);
        if (tp.getCurrentIndex() >= tp.getNumberOfPoints()) {
            tp.setCurrentIndex(tp.getNumberOfPoints() - 1);
        }
        //updatePointSpinner();
        ts.setRoi(tp.getRoi(ts.getCurrentSlice() - 1));
        ts.setSlice(ts.getCurrentSlice());
        ts.updateAndDraw();
        ts.threadStats();
        FileInfo fi = ts.getOriginalFileInfo();
        if (fi == null) {
            //System.out.println("original File Info null");
            fi = ts.getFileInfo();
        }
        String imageDir = (fi != null && fi.directory != null && !fi.directory.equalsIgnoreCase("")) ? fi.directory : IJ.getDirectory("current");
        if (!imageDir.endsWith(System.getProperty("file.separator")))
            imageDir += System.getProperty("file.separator");
        if (oldAlgo) {
            alignator.getAlignmentOld().saveRjs(imageDir + ts.getTitle() + "_Old_score_" + score + "_rjs.txt");
        } else {
            alignator.getBestAlignment().saveRjs(imageDir + ts.getTitle() + "_score_" + score + "_rjs.txt");
        }
        IJ.showStatus("align 3D landmarks finished");
        //String param = "maxInPlaneRotation=" + maxrot + " correctHeight=" + correctHeight;
        //ts.getTomoJPoints().setLandmarks3D(alignator);
        ts.setAlignment(oldAlgo ? alignator.getAlignmentOld() : alignator.getBestAlignment());

        return true;
    }

    public void setParameters(Object... parameters) {
        for (int index = 0; index < parameters.length; index++) {
            if (((String) parameters[index]).toLowerCase().equals("noexhaustivesearch")) {
                newExhaustiveSearch = true;
            } else if (((String) parameters[index]).toLowerCase().equals("mahalanobisweight")) {
                if (parameters[index + 1] instanceof String)
                    newMahalanobisWeight = Double.parseDouble((String) parameters[index + 1]);
                else newMahalanobisWeight = (Double) parameters[index + 1];
            } else if (((String) parameters[index]).toLowerCase().equals("noshift")) {
                newShift=false;
            }else if (((String) parameters[index]).toLowerCase().equals("shrink")) {
                newShrink=true;
            }else if (((String) parameters[index]).toLowerCase().equals("magnification")) {
                newMag=true;
            }else if (((String) parameters[index]).toLowerCase().equals("scalex")) {
                newScaleX=true;
            }else if (((String) parameters[index]).toLowerCase().equals("shear")) {
                newShear=true;
            }else if (((String) parameters[index]).toLowerCase().equals("rotation")) {
                newRotation=true;
            }else if (((String) parameters[index]).toLowerCase().equals("all")) {
                newShift=true;
                newMag=true;
                newShrink=true;
                newScaleX=true;
                newShear=true;
                newRotation=true;
            }else if (((String) parameters[index]).toLowerCase().equals("nbcycles")) {
                if (parameters[index + 1] instanceof String)
                    newCycleNumber = Integer.parseInt((String) parameters[index + 1]);
                else newCycleNumber = (Integer) parameters[index + 1];
            }else if (((String) parameters[index]).toLowerCase().equals("selectionthreshold")) {
                if (parameters[index + 1] instanceof String)
                    newSelectionThreshold = Double.parseDouble((String) parameters[index + 1]);
                else newSelectionThreshold = (Double) parameters[index + 1];
            }
        }


    }

    public String name() {
        return "Align using landmarks";
    }

    public static String help() {
        return "align landmarks using modelisation of 3D location\n" +
                "perform a first search on -90° to +90° every 5° for tilt axis estimation before refinement with gradient descent with cycles of removal of landmarks\n" +
                "parameters that can be given\n" +
                "noexhaustivesearch : remove the first exhaustive search\n" +
                "mahalanobisweight value : will use mahalanobis weighting for computation (default 0)\n" +
                "noshift : remove search of shift\n" +
                "magnification : add search of magnification parameter\n" +
                "shrink : add search of shrink parameter\n" +
                "scalex : add search of scalex parameter\n" +
                "shear : add search of shearing parameter\n" +
                "rotation : add search of rotation parameter\n" +
                "all : add search of all parameters (shift,rotation,magnification,shrinkage,scalex,shearing)\n" +
                "nbcycles value : number of removing landmarks cycles (-1 is automatic stop, default value)\n" +
                "selectionthreshold value : threshold to use for selecting landmarks to remove (default value 8)\n" +
                "";
    }

    public ArrayList<Object> getResults() {
        ArrayList<Object> result = new ArrayList<Object>();
        result.add(resultString);
        result.add(alignator);
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
            //initValues();
            addListeners();
            //updatePreview();
            firstDisplay = false;
        }
        return this.basePanel;
    }

    public void interrupt() {
        alignator.interrupt();
    }

    public double getCompletion() {
        return alignator.getCompletion() * 100;
    }

    public String getParametersValuesAsString() {
        String params = "";
        if (oldAlgo) {
            params += "old algorithm:\nmax rotation: " + oldRotation + "\ncorrect height: " + oldCorrectHeight + "\n";
        } else {
            params += "new algorithm:\nexhautive search: " + newExhaustiveSearch +
                    "\nmahalanobis weight: " + newMahalanobisWeight +
                    "\ncorrect \nshifts: " + newShift +
                    "\nmagnification: " + newMag +
                    "\nshrinkage: " + newShrink +
                    "\nscale X:" + newScaleX +
                    "\nshear: " + newShear + "\nrotation: " + newRotation +
                    "\nnumber of cycles: " + newCycleNumber + "\tthreshold: " + newSelectionThreshold +
                    "\ncorrect height: " + newCorrectHeight;
        }
        return params;
    }

    public void setDisplayPreview(boolean display) {

    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        spinnerOldRotationMax = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 360.0, 0.1));
        spinnerNewMahalanobisWeight = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 2.0, 0.01));
        spinnerNewSelectionThreshold = new JSpinner(new SpinnerNumberModel(8.0, 0.0, 360.0, 0.1));
        spinnerNewCycleNumber = new JSpinner(new SpinnerNumberModel(-1, -1, 500, 1));


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
        basePanel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Align Using Landmarks"));
        comboBoxAlgo = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("classic");
        defaultComboBoxModel1.addElement("with deformation");
        comboBoxAlgo.setModel(defaultComboBoxModel1);
        basePanel.add(comboBoxAlgo, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        basePanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        panelOld = new JPanel();
        panelOld.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panelOld, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Maximum in plane rotation (degrees)");
        panelOld.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelOld.add(spinnerOldRotationMax, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        correctOldHeightCheckBox = new JCheckBox();
        correctOldHeightCheckBox.setSelected(true);
        correctOldHeightCheckBox.setText("Correct height");
        panelOld.add(correctOldHeightCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelDeform = new JPanel();
        panelDeform.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panelDeform, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        exhaustiveSearchCheckBox = new JCheckBox();
        exhaustiveSearchCheckBox.setSelected(true);
        exhaustiveSearchCheckBox.setText("Exhaustive search");
        panelDeform.add(exhaustiveSearchCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Mahalanobis weight");
        panelDeform.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Correct:");
        panelDeform.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationInPlaneCheckBox = new JCheckBox();
        rotationInPlaneCheckBox.setSelected(true);
        rotationInPlaneCheckBox.setText("Rotation in plane");
        panelDeform.add(rotationInPlaneCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panelDeform.add(panel1, new GridConstraints(5, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        final JLabel label4 = new JLabel();
        label4.setText("Bad Landmarks selection ");
        panel1.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Cycles number");
        panel1.add(label5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerNewCycleNumber, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Threshold");
        panel1.add(label6, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.add(spinnerNewSelectionThreshold, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelDeform.add(panel2, new GridConstraints(3, 1, 2, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        magnificationCheckBox = new JCheckBox();
        magnificationCheckBox.setSelected(true);
        magnificationCheckBox.setText("Magnification");
        panel2.add(magnificationCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        shrinkageCheckBox = new JCheckBox();
        shrinkageCheckBox.setSelected(true);
        shrinkageCheckBox.setText("Shrinkage");
        panel2.add(shrinkageCheckBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scaleXCheckBox = new JCheckBox();
        scaleXCheckBox.setSelected(true);
        scaleXCheckBox.setText("Scale X");
        panel2.add(scaleXCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        shearCheckBox = new JCheckBox();
        shearCheckBox.setSelected(true);
        shearCheckBox.setText("Shear");
        panel2.add(shearCheckBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        shiftsCheckBox = new JCheckBox();
        shiftsCheckBox.setSelected(true);
        shiftsCheckBox.setText("Shifts");
        panelDeform.add(shiftsCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelDeform.add(spinnerNewMahalanobisWeight, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        correctNewHeightCheckBox = new JCheckBox();
        correctNewHeightCheckBox.setEnabled(false);
        correctNewHeightCheckBox.setSelected(false);
        correctNewHeightCheckBox.setText("Correct height of sample");
        panelDeform.add(correctNewHeightCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panelDeform.add(spacer3, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
