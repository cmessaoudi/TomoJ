package fr.curie.filters;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class Box3D_Extractor extends JDialog implements PlugIn, ChangeListener {
    private JPanel contentPane;
    private JButton buttonOK;
    private JSpinner Sizespinner;
    private JCheckBox previewCheckBox;
    private ImagePlus myimp;
    private ImagePlus preview;

    public Box3D_Extractor() {
        setContentPane(contentPane);
        setModal(false);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });
        Sizespinner.addChangeListener(this);
        Sizespinner.setValue(100);
        previewCheckBox.addChangeListener(this);
        myimp = IJ.getImage();
        pack();

        IJ.setTool(Toolbar.POINT);
        setVisible(true);
    }

    private void onOK() {
// add your code here
        int size = ((SpinnerNumberModel) Sizespinner.getModel()).getNumber().intValue();
        PointRoi center = (PointRoi) myimp.getRoi();
        int[] xs = center.getXCoordinates();
        int[] ys = center.getYCoordinates();
        String path = myimp.getOriginalFileInfo().directory + "crop";
        File dir = new File(path);
        System.out.println("output directory is " + path);
        try {
            boolean existAlready = !dir.mkdirs();
            int index = 0;
            if (existAlready) {
                File[] files = dir.listFiles();
                index = files.length;
            }
            for (int p = 0; p < center.getNCoordinates(); p++) {
                int x = (int) (xs[p] + center.getBounds().getX());
                int y = (int) (ys[p] + center.getBounds().getY());
                System.out.println("point " + p + " (" + x + "," + y + ")");
                x -= size / 2;
                y -= size / 2;
                Roi box = new Roi(x, y, size, size);
                ImageStack stack = extractBox(box);
                ImagePlus tmp = new ImagePlus("" + p, stack);
                FileSaver fs = new FileSaver(tmp);
                fs.saveAsTiffStack(path + File.separator + (index + p) + ".tif");
            }


        } catch (Exception e) {
            IJ.error("" + e);
        }
    }

    public ImageStack extractBox(Roi box) {
        int size = (int) box.getBounds().getWidth();
        ImageStack stack = new ImageStack(size, size);
        int currentSlice = myimp.getCurrentSlice();
        int ind = currentSlice - size / 2;
        if (ind < 0) {
            IJ.error("size is too big to obtain a square 3D box from this position");
            return null;
        }
        for (int i = 0; i < size; i++) {
            ImageProcessor tmp = myimp.getStack().getProcessor(ind + i);
            tmp.setRoi(box);
            tmp = tmp.crop();
            stack.addSlice("", tmp);
        }
        return stack;


    }

    public static void main(String[] args) {
        Box3D_Extractor dialog = new Box3D_Extractor();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    public void run(String arg) {

    }

    public void stateChanged(ChangeEvent e) {
        if (previewCheckBox.isSelected()) {
            updatePreview();
        } else {
            if (preview != null) {
                preview.hide();
                preview = null;
            }
        }
    }

    public void updatePreview() {
        int size = ((SpinnerNumberModel) Sizespinner.getModel()).getNumber().intValue();
        if (preview == null || size != preview.getWidth()) {
            if (preview != null) {
                preview.hide();
            }
            PointRoi center = (PointRoi) myimp.getRoi();
            int[] xs = center.getXCoordinates();
            int[] ys = center.getYCoordinates();
            int x = (int) (xs[0] + center.getBounds().getX());
            int y = (int) (ys[0] + center.getBounds().getY());
            System.out.println("point " + 0 + " (" + x + "," + y + ")");
            x -= size / 2;
            y -= size / 2;
            Roi box = new Roi(x, y, size, size);
            ImageStack stack = extractBox(box);
            if (stack == null) return;
            preview = new ImagePlus("preview", stack);
            preview.show();
            preview.setSlice(size / 2 + 1);
            preview.getWindow().setLocationRelativeTo(myimp.getWindow());
            preview.resetDisplayRange();
            preview.getWindow().getCanvas().zoomIn(0, 0);
            preview.getWindow().getCanvas().zoomIn(0, 0);
            preview.updateAndRepaintWindow();
        }
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
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(1, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("size of box");
        panel2.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        Sizespinner = new JSpinner();
        panel2.add(Sizespinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        previewCheckBox = new JCheckBox();
        previewCheckBox.setText("preview");
        panel3.add(previewCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("extract all");
        panel3.add(buttonOK, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
