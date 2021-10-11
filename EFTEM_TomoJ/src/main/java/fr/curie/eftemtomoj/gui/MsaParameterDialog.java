/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.gui;

import fr.curie.eftemtomoj.MSANoiseFilterBonnet;
import ij.ImagePlus;
import ij.ImageStack;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;

/**
 * @author Nick Aschman
 */
public class MsaParameterDialog extends JDialog {
    private JPanel contentPane;
    private JButton doneButton;
    private JTabbedPane tabbedPane1;
    private JButton prevTiltButton;
    private JButton nextTiltButton;
    private JLabel tiltLabel;
    private JPanel tiltPanel;

    private ImagePlus image;

    private int selectedTiltIndex = -1;
    private final int axisCount;
    private final int tiltCount;
    private final boolean manualSelection;


    private final MSANoiseFilterBonnet[] pcaBonnet;
    private final MsaParameterTabPanel[] tabs;


    public MsaParameterDialog(Dialog dialog, MSANoiseFilterBonnet[] filter, boolean manualSelection) {
        super(dialog, "Principal component selection");

        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(false);
        setResizable(false);
        getRootPane().setDefaultButton(doneButton);

        this.pcaBonnet = filter;
        this.manualSelection = manualSelection;


        tiltCount = pcaBonnet.length;
        axisCount = pcaBonnet[0].getNumberOfAxes();
        int width = pcaBonnet[0].getWidth();
        int height = pcaBonnet[0].getHeight();

        // Retrieve data


        // Create hyperstack & initialise tabs
        tabs = new MsaParameterTabPanel[axisCount];
        //ImageStack hyperStack;
        // hyperStack = new ImageStack(width, height);
        image = new ImagePlus("eigenImages", pcaBonnet[0].getEigenVectorImages());
        image.show();

        for (int i = 0; i < axisCount; i++) {
            tabs[i] = new MsaParameterTabPanel();

            final int axisIndex = i;
            tabs[i].keepAxisCheckBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    setSelection(axisIndex, tabs[axisIndex].isSelected());
                }
            });
            tabbedPane1.add("PC" + (i), tabs[i]);        // Inversion!!!
        }

        /*for (int j = 0; j < tiltCount; j++) {
            ImageStack is = pcaBonnet[j].getEigenVectorImages();
            for (int i = 0; i < axisCount; i++) {
                hyperStack.addSlice("", is.getPixels(i + 1));
            }
        } */

        doneButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onDone(true);
            }
        });

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onDone(false);
            }
        });

// call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        onDone(false);
                    }
                }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        tabbedPane1.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent ce) {
                selectImage(selectedTiltIndex, tabbedPane1.getSelectedIndex());
            }
        });

        prevTiltButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                selectImage(selectedTiltIndex - 1, tabbedPane1.getSelectedIndex());
            }
        });

        nextTiltButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                selectImage(selectedTiltIndex + 1, tabbedPane1.getSelectedIndex());
            }
        });

        /*image = new ImageMinus("Eigenvectors", hyperStack);
        image.setDimensions(axisCount, tiltCount, 1);
        image.setOpenAsHyperStack(true);
        image.show(); */

        setLocationRelativeTo(null);
        pack();

        selectImage(0, 0);
    }

    private void setSelection(int axisIndex, boolean s) {
        if (axisIndex < 0 || selectedTiltIndex < 0) {
            return;
        }

        if (manualSelection) {
            pcaBonnet[selectedTiltIndex].setSelected(axisIndex, s);
        } else {
            System.out.println((s ? "S" : "Des") + "electing " + axisIndex + " at all angles");
            for (int i = 0; i < this.tiltCount; i++) {
                pcaBonnet[i].setSelected(axisIndex, s);
            }
        }
    }

    private void onDone(boolean apply) {
        //image.setAllowedToClose(true);
        image.close();

        setVisible(false);

        if (apply) {
            onAccepted();
        } else {
            onCancelled();
        }
    }

    protected void onAccepted() {
    }

    protected void onCancelled() {
    }

    private void selectImage(int tiltIndex, int axisIndex) {
        if (tiltIndex < 0 || tiltIndex >= tiltCount) tiltIndex = 0;
        if (axisIndex < 0 || axisIndex >= axisCount) axisIndex = 0;

        if (tiltIndex != selectedTiltIndex || selectedTiltIndex < 0 || selectedTiltIndex >= tiltCount) {
            double ev, ec;
            double cec = 0;
            ImageStack is = pcaBonnet[tiltIndex].getEigenVectorImages();
            //if(image.getWidth()==is.getWidth()&&image.getHeight()==is.getHeight())
            image.setStack(is);
            /*else{
                image.close();
                image=new ImagePlus("eigenImages",is);
                image.show();
            }*/
            for (int i = 0; i < axisCount; i++) {

                //displ += "\n" + df.format(VP[l]) + " \t " + df.format(Math.log(VP[l])) + " \t " + df.format(VP[l] / vptot * 100) + " \t " + df.format(som * 100);
                ev = pcaBonnet[tiltIndex].getEigenValues()[i];
                ec = ev / pcaBonnet[tiltIndex].getEigenValueTotal();
                if (i == 0) {
                    ec = 0;
                }
                cec += ec;
                tabs[i].setValues(ev, ec, cec, pcaBonnet[tiltIndex].isSelected(i));

            }

            selectedTiltIndex = tiltIndex;
            tiltLabel.setText(String.format("Tilt %1$d", selectedTiltIndex + 1));

            prevTiltButton.setEnabled(tiltIndex > 0);
            nextTiltButton.setEnabled(tiltIndex + 1 < tiltCount);
        }

        //int sliceIndex = image.getStackIndex(axisIndex + 1, tiltIndex + 1, 1);
        image.setSlice(axisIndex + 1);
        image.resetDisplayRange();
        tabbedPane1.setSelectedIndex(axisIndex);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(3, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        doneButton = new JButton();
        doneButton.setText("Done");
        panel1.add(doneButton, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panel1.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        tabbedPane1 = new JTabbedPane();
        tabbedPane1.setTabPlacement(1);
        contentPane.add(tabbedPane1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        tiltPanel = new JPanel();
        tiltPanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(tiltPanel, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        prevTiltButton = new JButton();
        prevTiltButton.setIcon(new ImageIcon(getClass().getResource("/eftemtomoj/gui/go-previous.png")));
        prevTiltButton.setText("");
        tiltPanel.add(prevTiltButton, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltLabel = new JLabel();
        tiltLabel.setFont(new Font(tiltLabel.getFont().getName(), Font.BOLD, tiltLabel.getFont().getSize()));
        tiltLabel.setText("Label");
        tiltPanel.add(tiltLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        nextTiltButton = new JButton();
        nextTiltButton.setIcon(new ImageIcon(getClass().getResource("/eftemtomoj/gui/go-next.png")));
        nextTiltButton.setText("");
        tiltPanel.add(nextTiltButton, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    private static class MsaParameterTabPanel extends JPanel {
        private JTextField eigenvalueTextField;
        private JTextField energyContentTextField;
        private JTextField cumulativeTextField;
        public JCheckBox keepAxisCheckBox;
        private JLabel criteriumLabel;

        public MsaParameterTabPanel() {
            $$$setupUI$$$();
        }

        public void setValues(double ev, double ec, double cec, boolean selected) {
            eigenvalueTextField.setText(String.format("%1$.4f", ev));
            energyContentTextField.setText(String.format("%1$.2f %%", 100 * ec));
            cumulativeTextField.setText(String.format("%1$.2f %%", 100 * cec));
            keepAxisCheckBox.setSelected(selected);
        }

        public boolean isSelected() {
            return keepAxisCheckBox.isSelected();
        }

        /**
         * Method generated by IntelliJ IDEA GUI Designer
         * >>> IMPORTANT!! <<<
         * DO NOT edit this method OR call it in your code!
         *
         * @noinspection ALL
         */
        private void $$$setupUI$$$() {
            setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
            final JLabel label1 = new JLabel();
            label1.setText("Eigenvalue:");
            add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            final JLabel label2 = new JLabel();
            label2.setText("Energy content:");
            add(label2, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            eigenvalueTextField = new JTextField();
            eigenvalueTextField.setEditable(false);
            add(eigenvalueTextField, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(200, -1), null, 0, false));
            energyContentTextField = new JTextField();
            energyContentTextField.setEditable(false);
            add(energyContentTextField, new com.intellij.uiDesigner.core.GridConstraints(1, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(200, -1), null, 0, false));
            final JLabel label3 = new JLabel();
            label3.setText("Cumulative:");
            add(label3, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            cumulativeTextField = new JTextField();
            cumulativeTextField.setEditable(false);
            add(cumulativeTextField, new com.intellij.uiDesigner.core.GridConstraints(2, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(200, -1), null, 0, false));
            final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
            add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 3, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
            final JPanel panel2 = new JPanel();
            panel2.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
            add(panel2, new com.intellij.uiDesigner.core.GridConstraints(0, 3, 3, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTH, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
            keepAxisCheckBox = new JCheckBox();
            keepAxisCheckBox.setBorderPaintedFlat(false);
            keepAxisCheckBox.setFont(new Font(keepAxisCheckBox.getFont().getName(), Font.BOLD, keepAxisCheckBox.getFont().getSize()));
            keepAxisCheckBox.setText("Keep axis");
            panel2.add(keepAxisCheckBox, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            criteriumLabel = new JLabel();
            criteriumLabel.setText("");
            panel2.add(criteriumLabel, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
            label1.setLabelFor(eigenvalueTextField);
            label2.setLabelFor(energyContentTextField);
            label3.setLabelFor(cumulativeTextField);
        }
    }
}
