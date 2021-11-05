package fr.curie.eftemtomoj.gui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.eftemtomoj.EftemDataset;
import fr.curie.eftemtomoj.TiltSeries;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * panel containinng preprocessing tools : for now only aberrant pixels removal
 * User: C�dric
 * Date: 26 janv. 2011
 * Time: 13:09:30
 * To change this template use File | Settings | File Templates.
 */
public class HotspotRemovalPanel extends WizardPage {
    private JSlider radiusSlider;
    private JButton hotSpotApplyButton;
    private JTextField radiusTextField;
    private JPanel panel1;

    public HotspotRemovalPanel(WizardDialog dlg) {
        super(dlg, "HotSpot_PAGE", "Pre-processing");

        //$$$setupUI$$$();
        hotSpotApplyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                final EftemDataset ds = dialog.getCurrentDataset();
                final TiltSeries[] ts = ds.getTiltSeries();
                System.out.println("hot spot removal with radius of " + radiusSlider.getValue());
                final WizardApprentice worker = new WizardApprentice<Boolean>(dialog, "remove aberrant pixels") {

                    @Override
                    protected Boolean doInBackground() throws Exception {
                        setProgress(0);
                        for (int i = 0; i < ts.length; i++) {
                            ImagePlus tmp = new ImagePlus("tmp", ts[i]);
                            IJ.run(tmp, "Aberrant_pixel_removal", "radius=" + radiusSlider.getValue() + " stack");
                            updateProgress((i + 1.0) / ts.length);
                        }
                        return true;
                    }
                };
                worker.go();

                try {
                    worker.get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Process aborted", JOptionPane.ERROR_MESSAGE);
                    //	    ex.printStackTrace();
                    return;
                }

                //JOptionPane.showMessageDialog(dialog, "aberrant pixel removal",  "removal of aberrant pixels is finished", JOptionPane.OK_OPTION);
                //IJ.showMessage("aberrant pixel removal", "removal of aberrant pixels is finished");
                IJ.showStatus("removal of aberrant pixels is finished");
                System.out.println("removal of aberrant pixels is finished");

            }
        });
        hotSpotApplyButton.setEnabled(true);

        radiusTextField.setEnabled(true);
        radiusSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                radiusTextField.setText("" + radiusSlider.getValue());
            }
        });
        radiusTextField.setText("" + radiusSlider.getValue());

    }

    private void createUIComponents() {

    }

    @Override
    public JComponent getComponent() {
        return panel1;
    }

    public boolean validate() {
        //if (!dataModified) {
        //    return true;
        // }

        // Save denoised stack
        // dialog.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        //dialog.getCurrentDataset().save("denoised");
        //dialog.setCursor(Cursor.getDefaultCursor());

        return true;
    }

    @Override
    public void activate() {
        //dataModified = false;
        //setMsaFilterResult(null);
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
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
        radiusSlider = new JSlider();
        radiusSlider.setMaximum(10);
        radiusSlider.setMinimum(1);
        radiusSlider.setValue(1);
        panel1.add(radiusSlider, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        hotSpotApplyButton = new JButton();
        hotSpotApplyButton.setText("apply");
        panel1.add(hotSpotApplyButton, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        Font label1Font = this.$$$getFont$$$(null, Font.BOLD, -1, label1.getFont());
        if (label1Font != null) label1.setFont(label1Font);
        label1.setText("aberrant pixels removal");
        panel1.add(label1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("radius");
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radiusTextField = new JTextField();
        radiusTextField.setEditable(false);
        radiusTextField.setEnabled(true);
        panel1.add(radiusTextField, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(70, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        return new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }
}