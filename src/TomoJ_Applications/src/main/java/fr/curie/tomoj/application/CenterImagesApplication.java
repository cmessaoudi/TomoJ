package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import ij.gui.GenericDialog;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.align.SingleTiltAlign;
import fr.curie.utils.Chrono;
import fr.curie.utils.OutputStreamCapturer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * Created by cedric on 02/03/2017.
 */
public class CenterImagesApplication implements Application {
    private JRadioButton centerImagesRadioButton;
    private JRadioButton centerLandmarksRadioButton;
    private JTextArea textArea1;
    private JPanel basePanel;

    String resultString;

    protected TiltSeries ts;
    protected boolean firstDisplay;
    protected boolean centerImage;
    protected String textCenterImages = "this method will compute for each image:\n" +
            " - the center of mass of image intensities\n" +
            " - the translation to put this center of mass\n" +
            "\t\t at the center of images";
    protected String textCenterLandmarks = "this method will compute for each image:\n" +
            " - the center of mass of landmarks positions\n" +
            " - the translation to put this center of mass\n" +
            "\t\t at the center of images";

    public CenterImagesApplication(TiltSeries ts) {
        this.ts = ts;
        firstDisplay = true;
        centerImage = true;
    }

    protected void initListeners() {
        centerImagesRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                centerImage = true;
                updateDisplay();
            }
        });
        centerLandmarksRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                centerImage = false;
                updateDisplay();
            }
        });

    }

    protected void updateDisplay() {
        textArea1.setText((centerImage) ? textCenterImages : textCenterLandmarks);
    }

    public boolean run() {
        ts.updatePoint();
        OutputStreamCapturer capture = new OutputStreamCapturer();
        Chrono time = new Chrono();
        time.start();
        if (centerImage) {
            SingleTiltAlign align = new SingleTiltAlign(ts);
            align.centerImages();
        } else {
            ts.getTomoJPoints().fastAlign();
        }
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        ts.setSlice(ts.getCurrentSlice());
        ts.getProcessor().resetMinAndMax();
        ts.updateAndDraw();
        return true;
    }

    public void setParameters(Object... parameters) {

    }

    public String help() {
        return null;
    }

    public String name() {
        if (centerImage) return "center with images intensities";
        else return "center with landmarks center of mass";
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

    public String getParametersValuesAsString() {
        return "";
    }

    public JPanel getJPanel() {
        if (firstDisplay) {
            initListeners();
            updateDisplay();
        }
        return basePanel;
    }

    public void interrupt() {

    }

    public double getCompletion() {
        return 0;
    }

    public void setDisplayPreview(boolean display) {

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
        basePanel = new JPanel();
        basePanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        basePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Center Images", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        centerImagesRadioButton = new JRadioButton();
        centerImagesRadioButton.setSelected(true);
        centerImagesRadioButton.setText("center with images intensities");
        basePanel.add(centerImagesRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        centerLandmarksRadioButton = new JRadioButton();
        centerLandmarksRadioButton.setText("center with landmarks");
        basePanel.add(centerLandmarksRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        textArea1 = new JTextArea();
        textArea1.setBackground(new Color(-1644826));
        textArea1.setEditable(false);
        textArea1.setEnabled(true);
        basePanel.add(textArea1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(centerImagesRadioButton);
        buttonGroup.add(centerLandmarksRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

}
