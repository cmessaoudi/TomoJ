package fr.curie.eftemtomoj.gui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.eftemtomoj.EftemDataset;
import fr.curie.eftemtomoj.FilteredImage;
import fr.curie.eftemtomoj.Mapper;
import fr.curie.eftemtomoj.TiltSeries;
import fr.curie.eftemtomoj.gui.WizardPage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 10/10/11
 * Time: 10:38
 * To change this template use File | Settings | File Templates.
 */
public class MappingPanel2 extends WizardPage implements MouseListener {
    private JComboBox backgroundModelComboBox;
    //private JComboBox tiltAnglesComboBox;
    private JButton previewMapButton;
    private JCheckBox createR2PlotCheckBox;
    private JButton createMapButton;
    private JPanel PanelPlot;
    private JPanel Panel1;
    private JSpinner previewTiltAnglesSpinner;
    PlotPanel2 plotPanel;

    private ImageMinus controlImage = null;
    private Mapper mapper;
    private static final int N_REG_POINTS = 300;

    public MappingPanel2(WizardDialog dialog) {
        super(dialog, "MAPPING_PAGE" + Math.random(), "Elemental Mapping");


        $$$setupUI$$$();
        previewMapButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onPreview();
            }
        });
        createMapButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onApply();
            }
        });
        backgroundModelComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if (mapper != null) {
                    mapper.setModel(Mapper.Model.valueOf((String) backgroundModelComboBox.getSelectedItem()));
                    onFitRegression();
                }
            }
        });


        if (PanelPlot == null) System.out.println("plot is null!!!!");
        plotPanel.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent pce) {
                if (pce.getPropertyName().equals(PlotPanel2.SELECTION_PROPERTY) && mapper != null) {
                    System.out.println("Selection");
                    Mapper.ImageType[] imageTypes = plotPanel.getSelection();
                    for (int i = 0; i < imageTypes.length; i++) {
                        System.out.println("#" + i + ": " + imageTypes[i].name());

                        if (imageTypes[i] == Mapper.ImageType.Background) {
                            mapper.setBackground(i);
                        } else if (imageTypes[i] == Mapper.ImageType.Signal) {
                            mapper.setSignal(i);
                        } else {
                            mapper.setDisabled(i);
                        }
                    }

                    onFitRegression();
                }
            }
        });

        // Create list of alignment metrics
        Mapper.Model[] mod = Mapper.Model.values();

        String[] models = new String[mod.length];
        for (int i = 0; i < models.length; i++)
            models[i] = mod[i].name();

        backgroundModelComboBox.setModel(new DefaultComboBoxModel(models));

        final WizardDialog d = dialog;
        /*tiltAnglesComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onChangePreviewIndex();
            }
        }); */
        previewTiltAnglesSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                onChangePreviewIndex();
            }
        });
    }


    @Override
    public JComponent getComponent() {
        return Panel1;
    }

    @Override
    public boolean validate() {
        System.out.println("mapping validated");
        final EftemDataset ds = dialog.getCurrentDataset();
        TiltSeries[] ts = ds.getTiltSeries();
        Mapper.ImageType[] imageTypes = plotPanel.getSelection();
        int offset = 0;
        for (int i = 0; i < imageTypes.length; i++) {
            while (!ts[i + offset].isUsedForMapping()) {
                offset++;
            }
            System.out.println("#" + i + ": " + imageTypes[i].name());

            if (imageTypes[i] == Mapper.ImageType.Background) {
                ts[i + offset].setUseForMapping(true);
            } else if (imageTypes[i] == Mapper.ImageType.Signal) {
                ts[i + offset].setUseForMapping(true);
                ts[i + offset].setSignal(true);
            } else {
                ts[i + offset].setUseForMapping(false);
            }
            System.out.println(ts[i + offset].getEnergyShift() + " used for mapping:" + ts[i + offset].isUsedForMapping());
        }
        ds.tiltSeriesUpdated();
        ds.setLaw(Mapper.Model.valueOf((String) backgroundModelComboBox.getSelectedItem()));

        hideControlImage();
        return true;
    }

    @Override
    public void activate() {
        System.out.println("mapping activated");
        final EftemDataset ds = dialog.getCurrentDataset();

        if (ds == null) {
            System.out.println("Error: No dataset found");//TODO
            return;
        }
        ds.correctNegativeValues();
        // Create new mapper if needed and select model
        if (mapper == null) mapper = new Mapper(ds);
        //else System.out.println("mapper not null");
        backgroundModelComboBox.setSelectedItem(mapper.getModel().toString());

        // Create and show image
        FilteredImage[] img = ds.getMappingImages();
        System.out.println("number of images for mapping:" + img.length);
        ImageStack stack = new ImageStack(ds.getWidth(), ds.getHeight());
        for (FilteredImage anImg : img) {
            stack.addSlice(anImg.getEnergyShift() + "eV", anImg.getImage());

        }

        showControlImage(stack);

        //update tilts preview
        float[] ta = ds.getTiltAngles();
        String[] tilts = new String[ta.length];
        for (int i = 0; i < tilts.length; i++) {
            tilts[i] = Float.toString(ta[i]) + "�";
        }
        previewTiltAnglesSpinner.setModel(new SpinnerListModel(tilts));
        System.out.println("index:" + ds.getPreviewTiltIndex() + "  tilt: " + tilts[ds.getPreviewTiltIndex()]);
        previewTiltAnglesSpinner.setValue(tilts[ds.getPreviewTiltIndex()]);
        System.out.println("index:" + ds.getPreviewTiltIndex() + "  tilt: " + tilts[ds.getPreviewTiltIndex()]);
        //tiltAnglesComboBox.setModel(new DefaultComboBoxModel(tilts));
        //tiltAnglesComboBox.setSelectedIndex(ds.getPreviewTiltIndex());


        // Update plot
        onFitRegression();
    }

    @Override
    public boolean abort() {
        hideControlImage();
        return true;
    }

    private void showControlImage(ImageStack stack) {
        hideControlImage();

        controlImage = new ImageMinus("Preview", stack);
        controlImage.show();
        controlImage.getCanvas().addMouseListener(this);
    }

    private void updateControlImage(ImageStack stack) {
        controlImage.setStack(stack);
        controlImage.resetDisplayRange();
    }

    private void hideControlImage() {
        if (controlImage != null) {
            controlImage.setAllowedToClose(true);
            controlImage.close();
        }
    }

    private void onPreview() {
        if (mapper == null) {
            return; //TODO this shouldn't happen
        }

        // Compute map at zero tilt only
        ImageProcessor[] maps = null;

        final WizardApprentice worker = new WizardApprentice<ImageProcessor[]>(dialog, "Computing elemental map (zero-tilt only)") {
            @Override
            protected ImageProcessor[] doInBackground() throws Exception {
                ImageProcessor[] maps;

                mapper.addObserver(this);
                maps = mapper.computeMap();
                mapper.removeObserver(this);

                return maps;
            }
        };
        worker.go();

        try {
            maps = (ImageProcessor[]) worker.get();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Process aborted", JOptionPane.ERROR_MESSAGE);
//	    ex.printStackTrace();
            return;
        }

        // Save and show results
        dialog.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        ImagePlus mapStack = new ImagePlus("Elemental maps " + mapper.getModel() + " law " + mapper.usedEnergyAsString(), maps[0]);
        ImagePlus r2MapStack = new ImagePlus("Correlation coefficient maps (" + mapper.getModel() + ")", maps[1]);

        dialog.getCurrentDataset().saveImageAs(mapStack, "preview-map.tif");
        mapStack.show();

        if (createR2PlotCheckBox.isSelected()) {
            dialog.getCurrentDataset().saveImageAs(r2MapStack, "preview-r2map.tif");
            r2MapStack.show();
        }
        dialog.setCursor(Cursor.getDefaultCursor());
    }

    private void onApply() {
        if (mapper == null) {
            return; //TODO this shouldn't happen
        }

        // Compute all maps
        final EftemDataset ds = dialog.getCurrentDataset();
        ImageStack[] stacks = null;

        final WizardApprentice worker = new WizardApprentice<ImageStack[]>(dialog, "Computing elemental maps") {
            @Override
            protected ImageStack[] doInBackground() throws Exception {
                ImageStack[] stacks;

                mapper.addObserver(this);
                stacks = mapper.computeMaps(ds);
                mapper.removeObserver(this);

                return stacks;
            }
        };
        worker.go();

        try {
            stacks = (ImageStack[]) worker.get();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Process aborted", JOptionPane.ERROR_MESSAGE);
//	    ex.printStackTrace();
            return;
        }

        // Save and show results
        dialog.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        ImagePlus mapStack = new ImagePlus("Elemental maps " + mapper.getModel() + " law " + mapper.usedEnergyAsString(), stacks[0]);
        ImagePlus r2MapStack = new ImagePlus("Correlation coefficient maps (" + mapper.getModel() + ")", stacks[1]);

        ds.saveImageAs(mapStack, "maps.tif");
        mapStack.show();

        if (createR2PlotCheckBox.isSelected()) {
            ds.saveImageAs(r2MapStack, "r2map.tif");
            r2MapStack.show();
        }
        dialog.setCursor(Cursor.getDefaultCursor());
    }

    private void onFitRegression() {
        if (mapper != null) {

            Roi roi = null;
            if (controlImage != null) {
                roi = controlImage.getRoi();
            }

            try {
                plotPanel.setData(mapper, roi);
                plotPanel.setRegression(mapper.computeRegressionCurve(N_REG_POINTS, roi));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void onChangePreviewIndex() {
        //int newIndex=tiltAnglesComboBox.getSelectedIndex();


        final EftemDataset ds = dialog.getCurrentDataset();
        float[] ta = ds.getTiltAngles();
        int newIndex2 = 0;
        String val = (String) previewTiltAnglesSpinner.getValue();
        for (int i = 0; i < ta.length; i++) {
            if (val.startsWith("" + ta[i])) newIndex2 = i;
        }


        ds.setPreviewTiltIndex(newIndex2);
        //tiltAnglesComboBox.setSelectedIndex(newIndex2);
        Mapper old = mapper;
        mapper = new Mapper(ds);
        mapper.setImageTypes(old.getImageTypes());
        mapper.setModel(old.getModel());

        // Create and show image
        FilteredImage[] img = ds.getMappingImages();
        ImageStack stack = new ImageStack(ds.getWidth(), ds.getHeight());
        for (FilteredImage anImg : img) {
            stack.addSlice(anImg.getEnergyShift() + "eV", anImg.getImage());

        }

        updateControlImage(stack);


        onFitRegression();
    }

    public void mouseClicked(MouseEvent me) {
        onFitRegression();
    }

    public void mousePressed(MouseEvent me) {
        // Do nothing
    }

    public void mouseReleased(MouseEvent me) {
        onFitRegression();
    }

    public void mouseEntered(MouseEvent me) {
        // Do nothing
    }

    public void mouseExited(MouseEvent me) {
        // Do nothing
    }


    private void createUIComponents() {
        backgroundModelComboBox = new JComboBox();
        //PanelPlot=new JPanel();
        plotPanel = new PlotPanel2();
        if (plotPanel == null) System.out.println("plot is null!!!!");
        //PanelPlot.add(plotPanel);
        PanelPlot = plotPanel;
        //tiltAnglesComboBox= new JComboBox(new String[]{"(N/A)"});
        previewTiltAnglesSpinner = new JSpinner(new SpinnerListModel(new String[]{"", ""}));


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
        Panel1 = new JPanel();
        Panel1.setLayout(new GridLayoutManager(4, 5, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setText("Backgroud subtraction");
        Panel1.add(label1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Model");
        Panel1.add(label2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("linear");
        defaultComboBoxModel1.addElement("exponential");
        defaultComboBoxModel1.addElement("logarithmic");
        defaultComboBoxModel1.addElement("power");
        defaultComboBoxModel1.addElement("polynomial 2nd order");
        defaultComboBoxModel1.addElement("log polynomial 2nd order");
        defaultComboBoxModel1.addElement("log-log polynomial 2nd order");
        backgroundModelComboBox.setModel(defaultComboBoxModel1);
        Panel1.add(backgroundModelComboBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        previewMapButton = new JButton();
        previewMapButton.setText("Preview map");
        Panel1.add(previewMapButton, new GridConstraints(2, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        Panel1.add(spacer1, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        createR2PlotCheckBox = new JCheckBox();
        createR2PlotCheckBox.setText("create R2 plot");
        Panel1.add(createR2PlotCheckBox, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        createMapButton = new JButton();
        createMapButton.setText("Create map");
        Panel1.add(createMapButton, new GridConstraints(3, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        Panel1.add(PanelPlot, new GridConstraints(0, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        Panel1.add(previewTiltAnglesSpinner, new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return Panel1;
    }
}
