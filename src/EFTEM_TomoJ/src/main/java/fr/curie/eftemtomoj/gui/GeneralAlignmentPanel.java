package fr.curie.eftemtomoj.gui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.eftemtomoj.*;
import fr.curie.eftemtomoj.utils.SubImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.HistogramWindow;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.process.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import fr.curie.eftemtomoj.utils.Chrono;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 14/10/11
 * Time: 11:43
 * To change this template use File | Settings | File Templates.
 */
public class GeneralAlignmentPanel extends WizardPage implements KeyListener, MouseInputListener, MouseWheelListener {
    private JSplitPane splitPaneImagesMenu;
    private JPanel menuPanel;
    private JSpinner spinnerBining;
    private JPanel translationPanel;
    private JSpinner spinnerTx;
    private JSpinner spinnerTy;
    private JButton tyUpButton;
    private JButton txRightButton;
    private JSpinner spinnerTranslationIncrement;
    private JButton txLeftButton;
    private JButton tyDownButton;
    private JPanel rotationPanel;
    private JSpinner spinnerRotationValue;
    private JButton rot1Button;
    private JSpinner spinnerRotationIncrement;
    private JButton rot2Button;
    private JPanel tiltImagePanel;
    private JSpinner spinnerTiltImage;
    private JButton previousImageButton;
    private JButton NextImageButton;
    private JPanel EnergyImagePanel;
    private JSpinner EnergyImageSpinner;
    private JButton EnergyImagePreviousButton;
    private JButton EnergyImageNextButton;
    private JCheckBox normalizeImagesCheckBox;
    private JCheckBox invertReferenceCheckBox;
    private JRadioButton radioButtonSuperpose;
    private JRadioButton radioButtonDifference;
    private JLabel histogramLabel;
    private JSplitPane splitPaneImages;
    private JSplitPane splitPaneAliMap;
    private JPanel jPanelAli;
    private JLabel aliLabel;
    private JPanel jPanelMap;
    private JLabel mapLabel;
    private JSplitPane splitPaneRefComb;
    private JPanel jPanelRef;
    private JLabel refLabel;
    private JPanel jPanelCombine;
    private JLabel combineLabel;
    private JComboBox backgroundModelComboBox;
    private JButton createMapsButton;
    private JButton regressionMapButton;
    private JPanel parametersPanel;
    private JTabbedPane tabbedPaneAli;
    private JTabbedPane tabbedPane2;
    private JButton aberrantPixelsRemovalButton;
    private JComboBox comboBoxRefImage;
    private JComboBox comboBoxEvaluator;
    private JSpinner spinnerAutoAliRadius;
    private JCheckBox allCheckBoxAutoAli;
    private JButton alignButton;
    private JPanel Panel1;
    private JPanel plotPanel;
    private JSplitPane generalSplitPane;
    private JCheckBox allCheckBoxAberrantPixels;
    private JPanel aliAuto;
    private JPanel aliManual;
    private JPanel aliPre;
    private JButton saveTransformsButton;
    private JButton loadTransformsButton;
    private JPanel applyPanel;
    private JButton applySameOnAllButton1;
    private JPanel mappingPanel;
    private JButton saveAlignedImagesButton;
    private JSpinner scalexSpinner;
    private JSpinner scaleySpinner;
    private JCheckBox constrainAspectRatioCheckBox;
    private JButton saveProjectButton;
    private PlotPanel2 plotPanel2;

    private EftemDataset ds;
    private Double[] possibleIncrements;
    private FilteredImage[] mappingImages;
    private int tiltindex;
    private int refindex = 0;
    private ArrayList<String> energies;
    private ArrayList<String> tilts;
    HistogramWindow hw;
    private Mapper mapper;

    private int displayWidth = 256;
    private int displayHeight = 256;
    private ImageRegistration.Transform[][] transf;

    private Point mouseOrigin = null;
    private Point2D.Double ImageOrigin = new Point2D.Double(0, 0);
    private Point2D.Double bkpImgOri = null;
    private Point pointV = new Point(128, 128);
    private Point pointH = new Point(128, 128);
    private Roi roiMap;

    public GeneralAlignmentPanel(WizardDialog dlg) {
        super(dlg, "MAPPING_PAGE" + Math.random(), "Elemental Mapping");

        $$$setupUI$$$();
        refLabel.addMouseListener(this);
        refLabel.addMouseMotionListener(this);
        refLabel.addMouseWheelListener(this);
        refLabel.addKeyListener(this);
        refLabel.setFocusable(true);

        aliLabel.addMouseListener(this);
        aliLabel.addMouseMotionListener(this);
        aliLabel.addMouseWheelListener(this);
        aliLabel.addKeyListener(this);
        aliLabel.setFocusable(true);

        combineLabel.addMouseListener(this);
        combineLabel.addMouseMotionListener(this);
        combineLabel.addMouseWheelListener(this);
        combineLabel.addKeyListener(this);
        combineLabel.setFocusable(true);

        mapLabel.addMouseListener(this);
        mapLabel.addMouseMotionListener(this);
        mapLabel.addMouseWheelListener(this);
        mapLabel.addKeyListener(this);
        mapLabel.setFocusable(true);

        /*EnergyImageSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                updateViews();
                spinnerTx.setValue(transf[nrjindex][index].getTranslateX());
                spinnerTy.setValue(transf[nrjindex][index].getTranslateY());

            }
        });    */
        spinnerBining.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateViews();
            }
        });

        aberrantPixelsRemovalButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                aberrantPixelsRemoval();
                mappingImages = ds.getImages(tiltindex);
                updateViews();
                //JOptionPane.showMessageDialog(dialog, "aberrant pixel removal",  "removal of aberrant pixels is finished", JOptionPane.OK_OPTION);
                //IJ.showMessage("aberrant pixel removal", "removal of aberrant pixels is finished");
                IJ.showStatus("removal of aberrant pixels is finished");
                System.out.println("removal of aberrant pixels is finished");
            }
        });
        alignButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onAutoAlign();
            }
        });
        spinnerTx.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                transf[nrjindex][index].setTranslate((Double) spinnerTx.getValue(), (Double) spinnerTy.getValue());
                updateViews();
            }
        });
        spinnerTy.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                transf[nrjindex][index].setTranslate((Double) spinnerTx.getValue(), (Double) spinnerTy.getValue());
                updateViews();
            }
        });

        txLeftButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                spinnerTx.setValue(transf[nrjindex][index].getTranslateX() - (Double) spinnerTranslationIncrement.getValue());
            }
        });
        txRightButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                spinnerTx.setValue(transf[nrjindex][index].getTranslateX() + (Double) spinnerTranslationIncrement.getValue());
            }
        });
        tyUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                spinnerTy.setValue(transf[nrjindex][index].getTranslateY() - (Double) spinnerTranslationIncrement.getValue());
            }
        });
        tyDownButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                spinnerTy.setValue(transf[nrjindex][index].getTranslateY() + (Double) spinnerTranslationIncrement.getValue());
            }
        });
        spinnerRotationValue.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                transf[nrjindex][index].setRotate((Double) spinnerRotationValue.getValue());
                updateViews();
            }
        });
        rot1Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                spinnerRotationValue.setValue(transf[nrjindex][index].getRotate() - (Double) spinnerRotationIncrement.getValue());
            }
        });
        rot2Button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                spinnerRotationValue.setValue(transf[nrjindex][index].getRotate() + (Double) spinnerRotationIncrement.getValue());
            }
        });
        spinnerTiltImage.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                tiltindex = tilts.indexOf(spinnerTiltImage.getValue());
                mappingImages = ds.getImages(tiltindex);
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                double tx = transf[nrjindex][tiltindex].getTranslateX();
                double ty = transf[nrjindex][tiltindex].getTranslateY();
                spinnerTx.setValue(tx);
                spinnerTy.setValue(ty);
                spinnerRotationValue.setValue(transf[nrjindex][tiltindex].getRotate());
                updateViews();
            }
        });
        previousImageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (tiltindex > 0)
                    spinnerTiltImage.setValue(tilts.get(tiltindex - 1));
            }
        });
        NextImageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (tiltindex < tilts.size() - 1)
                    spinnerTiltImage.setValue(tilts.get(tiltindex + 1));
            }
        });
        EnergyImageSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                double tx = transf[nrjindex][tiltindex].getTranslateX();
                double ty = transf[nrjindex][tiltindex].getTranslateY();
                spinnerTx.setValue(tx);
                spinnerTy.setValue(ty);
                spinnerRotationValue.setValue(transf[nrjindex][tiltindex].getRotate());
                updateViews();
            }
        });

        EnergyImagePreviousButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                if (nrjindex > 0)
                    EnergyImageSpinner.setValue(energies.get(nrjindex - 1));
            }
        });
        EnergyImageNextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                if (nrjindex < energies.size() - 1)
                    EnergyImageSpinner.setValue(energies.get(nrjindex + 1));
            }
        });
        radioButtonSuperpose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateViews();
            }
        });
        radioButtonDifference.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateViews();
            }
        });
        normalizeImagesCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateViews();
            }
        });
        invertReferenceCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateViews();
            }
        });

        plotPanel2.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent pce) {
                if (pce.getPropertyName().equals(PlotPanel2.SELECTION_PROPERTY) && mapper != null) {
                    System.out.println("Selection");
                    Mapper.ImageType[] imageTypes = plotPanel2.getSelection();
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


                    updateViews();
                }
            }
        });
        Mapper.Model[] mod = Mapper.Model.values();

        String[] models = new String[mod.length];
        for (int i = 0; i < models.length; i++) models[i] = mod[i].name();
        backgroundModelComboBox.setModel(new DefaultComboBoxModel(models));
        backgroundModelComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (backgroundModelComboBox.getItemCount() > 0)
                    mapper.setModel(Mapper.Model.valueOf((String) backgroundModelComboBox.getSelectedItem()));

                updateViews();
            }
        });

        saveTransformsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onSaveTransforms();
            }
        });

        loadTransformsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onLoadTransforms();
            }
        });
        applySameOnAllButton1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                ImageRegistration.Transform tr = transf[nrjindex][tiltindex];

                for (int j = 0; j < transf[nrjindex].length; j++) {
                    transf[nrjindex][j].setTransform(tr);
                }


            }
        });
        createMapsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                createMaps();
            }
        });
        regressionMapButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewMap(true);
            }
        });
        generalSplitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                super.componentResized(e);
                if (ds != null) {
                    updateDisplayValues();
                    updateSpinners();
                    updateViews();
                }
            }
        });

        saveAlignedImagesButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onSaveAlignedImages();
            }
        });
        constrainAspectRatioCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                scaleySpinner.setEnabled(!constrainAspectRatioCheckBox.isSelected());

            }
        });
        scalexSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (constrainAspectRatioCheckBox.isSelected()) {
                    scaleySpinner.setValue(scalexSpinner.getValue());
                }
                //do things
                int index = tilts.indexOf(spinnerTiltImage.getValue());
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                transf[nrjindex][index].setScale((Double) scalexSpinner.getValue(), (Double) scaleySpinner.getValue());
                updateViews();

            }
        });
        scaleySpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (!constrainAspectRatioCheckBox.isSelected()) {
                    //do things
                    int index = tilts.indexOf(spinnerTiltImage.getValue());
                    int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                    transf[nrjindex][index].setScale((Double) scalexSpinner.getValue(), (Double) scaleySpinner.getValue());
                    updateViews();
                }
            }
        });
        saveProjectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onSaveProject();
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
        ds = dialog.getCurrentDataset();
        TiltSeries[] ts = ds.getTiltSeries();
        Mapper.ImageType[] imageTypes = plotPanel2.getSelection();
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
            } else if (imageTypes[i] == Mapper.ImageType.Disabled) {
                ts[i + offset].setUseForMapping(true);
                ts[i + offset].setDisabled(true);
            }
            System.out.println(ts[i + offset].getEnergyShift() + " used for mapping:" + ts[i + offset].isUsedForMapping());
        }
        ds.tiltSeriesUpdated();
        ds.setLaw(Mapper.Model.valueOf((String) backgroundModelComboBox.getSelectedItem()));

        for (int j = 0; j < ds.getTiltCount(); j++) {
            for (int i = 0; i < ds.getWindowCount(); i++) {
                FilteredImage image = ds.getImage(j, i);
                image.applyTransform(transf[i][j]);
                //System.out.println("image "+i+"mapping="+image.isUsedForMapping()+" disabled="+image.isDisabled()+" signal="+image.isSignal());
            }
        }
        plotPanel2.clear();
        mapper = null;
        return true;
    }

    public void actionPerformed(ActionEvent e) {

    }

    @Override
    public void activate() {
        System.out.println("general alignment activated");
        ds = dialog.getCurrentDataset();

        if (ds == null) {
            System.out.println("Error: No dataset found");
            return;
        }

        if (refindex < 0) {
            System.out.println("refindex:" + refindex);
            refindex = 0;
        }
        System.out.println("1_start refindex" + refindex);

        mapper = new Mapper(ds);
        tiltindex = 0;
        mappingImages = ds.getImages(tiltindex);
        energies = new ArrayList<String>();
        for (int i = 0; i < mappingImages.length; i++) {
            String tmp = mappingImages[i].getEnergyShift() + "eV";
            if (i == refindex) tmp = "*" + tmp + "*";
            energies.add(tmp);
        }

        tilts = new ArrayList<String>();
        boolean angle = (ds.getTiltAngle(0) != ds.getTiltAngle(1));
        for (int i = 0; i < ds.getTiltCount(); i++) {
            String tmp = (angle) ? ds.getTiltAngle(i) + "�" : "" + i;
            tilts.add(tmp);
        }
        transf = new ImageRegistration.Transform[ds.getWindowCount()][ds.getTiltCount()];
        for (int i = 0; i < transf.length; i++) {
            for (int j = 0; j < transf[i].length; j++) {
                transf[i][j] = new ImageRegistration.Transform(0, 0, 0);
            }
        }
        System.out.println("2_before updatedisplay refindex:" + refindex);
        updateDisplayValues();
        spinnerBining.setModel(new SpinnerNumberModel(ds.getWidth() / displayWidth, 0.5, ds.getWidth() / 256.0 * 2, 0.1));
        spinnerTx.setModel(new SpinnerNumberModel(0, -ds.getWidth(), ds.getWidth(), 1.0));
        spinnerTy.setModel(new SpinnerNumberModel(0, -ds.getHeight(), ds.getHeight(), 1.0));
        EnergyImageSpinner.setModel(new SpinnerListModel(energies));
        spinnerTiltImage.setModel(new SpinnerListModel(tilts));
        System.out.println("3_before removeAll  refindex:" + refindex);
        if (comboBoxRefImage.getActionListeners().length > 0)
            comboBoxRefImage.removeActionListener(comboBoxRefImage.getActionListeners()[0]);
        comboBoxRefImage.removeAllItems();
        System.out.println("4_after removeAll refcombobox refindex:" + refindex);
        for (String st : energies) {
            comboBoxRefImage.addItem(st);
        }
        comboBoxRefImage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("change ref image");
                refindex = comboBoxRefImage.getSelectedIndex();
                energies.clear();
                for (int i = 0; i < mappingImages.length; i++) {
                    String tmp = mappingImages[i].getEnergyShift() + "eV";
                    if (i == refindex) tmp = "*" + tmp + "*";
                    energies.add(tmp);
                }
                comboBoxRefImage.removeAllItems();
                for (String st : energies) {
                    comboBoxRefImage.addItem(st);
                }
                comboBoxRefImage.setSelectedIndex(refindex);
                EnergyImageSpinner.setModel(new SpinnerListModel(energies));
                updateViews();

            }
        });


        spinnerTranslationIncrement.setValue(possibleIncrements[3]);
        spinnerRotationIncrement.setValue(possibleIncrements[2]);

        comboBoxRefImage.setSelectedItem(energies.get(refindex));
        EnergyImageSpinner.setValue(energies.get(1));

        if (ds.getMappingWindowCount() == 2) {
            backgroundModelComboBox.removeAllItems();
            backgroundModelComboBox.addItem("Ratio");
            backgroundModelComboBox.addItem("Subtract");
        } else if (ds.getMappingWindowCount() == 3) {
            backgroundModelComboBox.removeAllItems();
            backgroundModelComboBox.addItem("Linear");
            backgroundModelComboBox.addItem("Exponential");
            backgroundModelComboBox.addItem("Logarithmic");
            backgroundModelComboBox.addItem("Power");
        }


        updateViews();
        dialog.pack();
        //   ds.correctNegativeValues();
        // Create new mapper if needed and select model
        //else System.out.println("mapper not null");
        backgroundModelComboBox.setSelectedItem(mapper.getModel().toString());
        onFitRegression();

    }

    @Override
    public boolean abort() {

        return true;
    }


    public void keyTyped(KeyEvent e) {
        System.out.println("key typed:" + e.getKeyCode());
        System.out.println("down:" + KeyEvent.VK_DOWN);

    }

    public void keyPressed(KeyEvent e) {
        //System.out.println("key pressed:" + e.getKeyCode());
        //System.out.println("down:" + KeyEvent.VK_DOWN);
    }

    public void keyReleased(KeyEvent e) {
        System.out.println("key released:" + e.getKeyCode());
        //System.out.println("down:" + KeyEvent.VK_DOWN);
    }

    public void mouseClicked(MouseEvent e) {
        if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) == MouseEvent.BUTTON1_MASK) {
            if (e.getSource() == refLabel) {
                pointV = e.getPoint();
                pointH = e.getPoint();
                //System.out.println("pointV=" + pointV);
                //int index = energies.indexOf(EnergyImageSpinner.getValue());
                //setImages(index, refindex);
                updateViews();
            }
        }
    }

    public void mouseEntered(MouseEvent e) {
        if (e.getSource() == aliLabel) {
            aliLabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        } else if (e.getSource() == combineLabel) {
            combineLabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        } else if (e.getSource() == refLabel) {
            refLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else if (e.getSource() == mapLabel) {
            mapLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        }

    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
        mouseOrigin = e.getPoint();
        bkpImgOri = (Point2D.Double) ImageOrigin.clone();
        if (e.getButton() == MouseEvent.BUTTON3) {
            if (e.getSource() == aliLabel) {
                aliLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            /*if (e.getSource() == imageJRightLabel) {
                imageJRightLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            } */
            if (e.getSource() == refLabel) {
                refLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            if (e.getSource() == combineLabel) {
                combineLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        }

    }

    public void mouseReleased(MouseEvent e) {
        if ((e.getModifiers() & MouseEvent.BUTTON3_MASK) == MouseEvent.BUTTON3_MASK) {
            if (e.getSource() == aliLabel) {
                aliLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            }
            /*if (e.getSource() == imageJRightLabel) {
                imageJRightLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            } */
            if (e.getSource() == refLabel) {
                refLabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            }
            if (e.getSource() == combineLabel) {
                combineLabel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            }
        } else if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) == MouseEvent.BUTTON1_MASK) {
            if (e.getSource() == mapLabel || e.getSource() == refLabel) {
                Point p = e.getPoint();
                final double bin = (Double) spinnerBining.getValue();
                int minX = (int) (Math.min(mouseOrigin.getX(), p.getX()) * bin);
                int minY = (int) (Math.min(mouseOrigin.getY(), p.getY()) * bin);
                int width = (int) (Math.abs(mouseOrigin.getX() - p.getX()) * bin);
                int height = (int) (Math.abs(mouseOrigin.getY() - p.getY()) * bin);
                if (width == 0 && height == 0 && e.getSource() == refLabel) roiMap = null;
                else if ((e.getModifiers() & MouseEvent.CTRL_MASK) == MouseEvent.CTRL_MASK)
                    roiMap = new Roi(minX, minY, width, height);
                else roiMap = new OvalRoi(minX, minY, width, height);
                //System.out.println("pointV=" + pointV);
                //int index = energies.indexOf(EnergyImageSpinner.getValue());
                //setImages(index, refindex);
                updateViews();

            }

        }
    }

    public void mouseDragged(MouseEvent e) {

        if (e.getSource() == aliLabel || e.getSource() == combineLabel) {
            Point actualPosition = e.getPoint();
            int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
            if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) == MouseEvent.BUTTON1_MASK) {
                //Point diff = actualPosition - mouseOrigin;
                double dx = (actualPosition.getX() - mouseOrigin.getX()) * (Double) spinnerTranslationIncrement.getValue();
                double dy = (actualPosition.getY() - mouseOrigin.getY()) * (Double) spinnerTranslationIncrement.getValue();
                mouseOrigin = actualPosition;
                spinnerTx.setValue(transf[nrjindex][tiltindex].getTranslateX() + dx);
                spinnerTy.setValue(transf[nrjindex][tiltindex].getTranslateY() + dy);
            } else if (e.getModifiers() == MouseEvent.BUTTON3_MASK) {
                double dist = actualPosition.distance(mouseOrigin);
                int direction = (actualPosition.getX() > mouseOrigin.getX()) ? 1 : -1;
                dist *= direction;
                mouseOrigin = actualPosition;
                //System.out.println("rot " + dist);
                spinnerRotationValue.setValue(transf[nrjindex][tiltindex].getRotate() + dist * (Double) spinnerRotationIncrement.getValue());
            }
            updateViews();
        }
        if (e.getSource() == refLabel) {
            //System.out.println("button " + e.getButton() + " mod " + e.getModifiers() + " modEX " + e.getModifiersEx());
            //System.out.println("button 1 is: " + MouseEvent.BUTTON1 + " mask:" + MouseEvent.BUTTON1_MASK + " downmask:" + MouseEvent.BUTTON1_DOWN_MASK);
            //System.out.println("button 2 is: " + MouseEvent.BUTTON2 + " mask:" + MouseEvent.BUTTON2_MASK + " downmask:" + MouseEvent.BUTTON2_DOWN_MASK);
            //System.out.println("button 3 is: " + MouseEvent.BUTTON3 + " mask:" + MouseEvent.BUTTON3_MASK + " downmask:" + MouseEvent.BUTTON3_DOWN_MASK);
            if ((e.getModifiers() & MouseEvent.BUTTON3_MASK) == MouseEvent.BUTTON3_MASK) {
                Point actualPosition = e.getPoint();
                //Point diff = actualPosition - mouseOrigin;
                double dx = actualPosition.getX() - mouseOrigin.getX();
                double dy = actualPosition.getY() - mouseOrigin.getY();
                ImageOrigin = new Point2D.Double(bkpImgOri.getX() - dx, bkpImgOri.getY() - dy);
                updateViews();
            }
        }
        if (e.getSource() == mapLabel || e.getSource() == refLabel) {
            if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) == MouseEvent.BUTTON1_MASK) {
                Point p = e.getPoint();
                final double bin = (Double) spinnerBining.getValue();
                int minX = (int) (Math.min(mouseOrigin.getX(), p.getX()) * bin);
                int minY = (int) (Math.min(mouseOrigin.getY(), p.getY()) * bin);
                int width = (int) (Math.abs(mouseOrigin.getX() - p.getX()) * bin);
                int height = (int) (Math.abs(mouseOrigin.getY() - p.getY()) * bin);
                if ((e.getModifiers() & MouseEvent.CTRL_MASK) == MouseEvent.CTRL_MASK)
                    roiMap = new Roi(minX, minY, width, height);
                else roiMap = new OvalRoi(minX, minY, width, height);
                //System.out.println("pointV=" + pointV);
                //int index = energies.indexOf(EnergyImageSpinner.getValue());
                //setImages(index, refindex);
                updateViews();

            }
        }


    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int tmp = e.getWheelRotation();
        System.out.println("mouse wheel " + tmp);
        if (tmp < 0) previousImageButton.doClick();
        if (tmp > 0) NextImageButton.doClick();
    }


    void autoAdjust(ImageProcessor ip) {

        ImageStatistics stats = ip.getStatistics(); // get uncalibrated stats
        int limit = stats.pixelCount / 10;
        int[] histogram = stats.histogram;
        int threshold = stats.pixelCount / 5000;
        int i = -1;
        boolean found = false;
        int count;
        do {
            i++;
            count = histogram[i];
            if (count > limit) count = 0;
            found = count > threshold;
        } while (!found && i < 255);
        int hmin = i;
        i = 256;
        do {
            i--;
            count = histogram[i];
            if (count > limit) count = 0;
            found = count > threshold;
        } while (!found && i > 0);
        int hmax = i;
        if (hmax >= hmin) {
            double min = stats.histMin + hmin * stats.binSize;
            double max = stats.histMin + hmax * stats.binSize;
            if (min == max) {
                min = stats.min;
                max = stats.max;
            }
            ip.setMinAndMax(min, max);
        } else {
            ip.resetMinAndMax();
        }

    }

    private void updateDisplayValues() {
        splitPaneImages.setDividerLocation(0.5);
        splitPaneRefComb.setDividerLocation(0.5);
        splitPaneAliMap.setDividerLocation(0.5);
        splitPaneImagesMenu.setResizeWeight(1);
        refLabel.setMinimumSize(new Dimension(50, 50));
        //aliLabel.setMinimumSize(new Dimension(50,50));
        combineLabel.setMinimumSize(new Dimension(50, 50));
        mapLabel.setMinimumSize(new Dimension(50, 50));
        this.Panel1.setSize(this.Panel1.getParent().getSize());
        generalSplitPane.setSize(this.Panel1.getSize());
        displayWidth = (splitPaneImages.getWidth() - 15) / 2;

        //displayWidth = (splitPaneTop.getWidth() - splitPaneTop.getDividerSize()) / 2;
        displayHeight = (splitPaneImages.getHeight() - 15) / 2;

        //System.out.println("update display: new width="+displayWidth+" new height="+displayHeight);
    }

    private void updateViews() {
        updateDisplayValues();
        final double bin = (Double) spinnerBining.getValue();
        int cpus = Runtime.getRuntime().availableProcessors();
        final int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
        ExecutorService pool = Executors.newFixedThreadPool(cpus);
        java.util.List<Future> futures = new ArrayList<Future>();
        //ref
        futures.add(pool.submit(new Runnable() {
            public void run() {
                ImageProcessor imgRef = getImageForDisplay(refindex, bin, false);
                if (invertReferenceCheckBox.isSelected()) imgRef.invert();
                if (normalizeImagesCheckBox.isSelected()) autoAdjust(imgRef);


                ImageProcessor imgAli = getImageForDisplay(nrjindex, bin, true);
                if (normalizeImagesCheckBox.isSelected()) autoAdjust(imgAli);

                ImageProcessor comb = createCombinedImage(imgAli, imgRef);
                combineLabel.setIcon(new ImageIcon(comb.getBufferedImage(), "images combined"));
                if (pointH != null) {
                    imgRef = imgRef.convertToRGB();
                    imgRef.setColor(Color.RED);
                    imgRef.drawLine(0, (int) pointH.getY(), imgRef.getWidth(), (int) pointH.getY());
                    imgAli = imgAli.convertToRGB();
                    imgAli.setColor(Color.RED);
                    imgAli.drawLine(0, (int) pointH.getY(), imgAli.getWidth(), (int) pointH.getY());
                }
                if (pointV != null) {
                    imgRef = imgRef.convertToRGB();
                    imgRef.setColor(Color.GREEN);
                    imgRef.drawLine((int) pointV.getX(), 0, (int) pointV.getX(), imgRef.getHeight());
                    imgAli = imgAli.convertToRGB();
                    imgAli.setColor(Color.GREEN);
                    imgAli.drawLine((int) pointV.getX(), 0, (int) pointV.getX(), imgAli.getHeight());
                }
                if (roiMap != null) {
                    Rectangle rect = roiMap.getBounds();
                    Roi roibin = (roiMap instanceof OvalRoi) ? new OvalRoi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin)) : new Roi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin));
                    imgRef = imgRef.convertToRGB();
                    imgRef.setColor(Color.MAGENTA);
                    imgRef.draw(roibin);
                    imgAli = imgAli.convertToRGB();
                    imgAli.setColor(Color.MAGENTA);
                    imgAli.draw(roibin);
                }
                refLabel.setIcon(new ImageIcon(imgRef.getBufferedImage(), "image Ref"));
                aliLabel.setIcon(new ImageIcon(imgAli.getBufferedImage(), "image Ali"));


            }
        }));


        //map
        futures.add(pool.submit(new Callable<ImageProcessor>() {
            public ImageProcessor call() {
                ImageProcessor map = previewMap(false);
                onFitRegression();
                map.resetMinAndMax();
                System.out.println("before autoadjust min:" + map.getMin() + ", max:" + map.getMax());
                autoAdjust(map);
                System.out.println("after autoadjust min:" + map.getMin() + ", max:" + map.getMax());
                if (roiMap != null) {
                    Rectangle rect = roiMap.getBounds();
                    Roi roibin = (roiMap instanceof OvalRoi) ? new OvalRoi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin)) : new Roi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin));

                    map = map.convertToRGB();
                    map.setColor(Color.MAGENTA);
                    map.draw(roibin);
                }
                mapLabel.setIcon(new ImageIcon(map.getBufferedImage(), "image map"));
                //mapLabel.setIcon(new ImageIcon(map.createImage(), "image map"));
                return map;
            }
        }));

        pool.shutdown();

    }

    public FloatProcessor getImageForDisplay(int nrjindex, double binning, boolean applyTransform) {
        if (nrjindex < 0) return null;
        ImageProcessor ip = mappingImages[nrjindex].getImage();

        float[] pixs = (float[]) ip.getPixels();
        int sx = ip.getWidth();
        int sy = ip.getHeight();
        int nsx = (int) (sx / binning);
        int nsy = (int) (sy / binning);
        AffineTransform T = new AffineTransform();


        if (binning <= 1) {
            if (applyTransform) {
                T.scale(transf[nrjindex][tiltindex].getScaleX(), transf[nrjindex][tiltindex].getScaleY());
                T.rotate(Math.toRadians(transf[nrjindex][tiltindex].getRotate()));
                T.translate(transf[nrjindex][tiltindex].getTranslateX(), transf[nrjindex][tiltindex].getTranslateY());
            }

            try {
                T = T.createInverse();
            } catch (Exception e) {
                e.printStackTrace();
            }
            nsx = (int) (displayWidth * binning);
            nsy = (int) (displayHeight * binning);
            pixs = SubImage.getSubImagePixels(pixs, T, ip.getStatistics(), sx, sy, nsx, nsy, ImageOrigin, false, false);
            sx = nsx;
            sy = nsy;
            nsx = displayWidth;
            nsy = displayHeight;

        }
        //downsampling in fourier
        if (binning != 1) {
            pixs = binning(pixs, sx, sy, nsx, nsy);
        }
        if (binning > 1) {
            if (applyTransform && transf != null) {
                T.scale(transf[nrjindex][tiltindex].getScaleX(), transf[nrjindex][tiltindex].getScaleY());
                T.rotate(Math.toRadians(transf[nrjindex][tiltindex].getRotate()));
                T.translate(transf[nrjindex][tiltindex].getTranslateX() / binning, transf[nrjindex][tiltindex].getTranslateY() / binning);
            }

            try {
                T = T.createInverse();
            } catch (Exception e) {
                e.printStackTrace();
            }
            pixs = SubImage.getSubImagePixels(pixs, T, ip.getStatistics(), nsx, nsy, displayWidth, displayHeight, ImageOrigin, false, false);
        }
        return new FloatProcessor(displayWidth, displayHeight, pixs, ip.getCurrentColorModel());
    }

    private float[] binning(float[] pixs, int sx, int sy, int nsx, int nsy) {
        FloatProcessor fp = new FloatProcessor(sx, sy, pixs);
        fp.setInterpolationMethod(ImageProcessor.BICUBIC);
        return (float[]) fp.resize(nsx, nsy).getPixels();
        /*int nsize = nsx * nsy;
        DenseFloatMatrix2D H1 = new DenseFloatMatrix2D(sy, sx);
        H1.assign(pixs);
        DenseFComplexMatrix2D fft = H1.getFft2();
        float[] fft1 = fft.elements();


        double ncx = (nsx - 1.0) / 2;
        double ncy = (nsy - 1.0) / 2;
        int ind1, ind2;
        //downsampling in fourier
        float[] bfft = new float[nsize * 2];
        for (int y = 0; y < nsy; y++) {
            int yy = y * nsx;
            int posy = (y > ncy) ? (y + sy - nsy) * sx : y * sx;
            for (int x = 0; x < nsx; x++) {
                int posx = (x > ncx) ? (x - nsx + sx) : x;
                ind1 = (yy + x) * 2;
                ind2 = (posy + posx) * 2;
                if (ind1 > 0 && ind1 < bfft.length && ind2 > 0 && ind2 < fft1.length) {
                    bfft[ind1] = fft1[ind2];
                    bfft[ind1 + 1] = fft1[ind2 + 1];
                }
            }
        }

        fft = new DenseFComplexMatrix2D(nsy, nsx);
        fft.assign(bfft);
        fft.ifft2(true);
        bfft = fft.elements();
        pixs = new float[nsize];
        for (int j = 0; j < pixs.length; j++) {
            pixs[j] = bfft[j * 2];
        }
        return pixs; */
    }

    public ImageProcessor createCombinedImage(ImageProcessor moving, ImageProcessor reference) {
        ImageProcessor result = null;
        if (radioButtonSuperpose.isSelected()) {
            ByteProcessor mB = (ByteProcessor) moving.convertToByte(true);
            ByteProcessor rB = (ByteProcessor) reference.convertToByte(true);
            result = new ColorProcessor(moving.getWidth(), moving.getHeight());
            ((ColorProcessor) result).setRGB((byte[]) mB.getPixels(), (byte[]) rB.getPixels(), (byte[]) mB.getPixels());

        } else {
            result = moving.convertToByte(true);
            result.copyBits(reference.convertToByte(true), 0, 0, Blitter.DIFFERENCE);

        }
        if (hw == null) {
            hw = new HistogramWindow(new ImagePlus("", result));
            hw.setVisible(false);
        } else {
            hw.getImagePlus().getProcessor().setValue(255);
            hw.getImagePlus().getProcessor().fill();
            hw.showHistogram(new ImagePlus("", result), 256);
        }
        histogramLabel.setIcon(new ImageIcon(hw.getImagePlus().getBufferedImage(), "histogram"));
        return result;

    }

    private void onFitRegression() {
        if (mapper != null) {
            try {
                /* final eftemtomoj.FilteredImage[] images = new eftemtomoj.FilteredImage[ds.getMappingWindowCount()];
                int index = 0;
                final double binning = (Double) spinnerBining.getValue();
                for (int i = 0; i < ds.getWindowCount(); i++) {
                    if (ds.isUsedForMapping(i)) {
                        images[index] = new eftemtomoj.FilteredImage(getImageForDisplay(i, (Double) spinnerBining.getValue(), true), ds.getEnergyShift(i), ds.getExposureTime(i), ds.getSlitWidth(i), ds.getTiltAngle(tiltindex), ds.isUsedForMapping(i));
                        images[index].setSignal(ds.isSignal(i));
                        index++;
                    }
                }
                mapper.setImages(images);  */
                plotPanel2.setData(mapper, roiMap);
                plotPanel2.setRegression(mapper.computeRegressionCurve(300, roiMap));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    protected void updateSpinners() {
        if (ds != null) {
            double actualValue = ((SpinnerNumberModel) spinnerBining.getModel()).getNumber().doubleValue();
            double min = Math.min(ds.getWidth() / (double) displayWidth, ds.getHeight() / (double) displayHeight);
            if (actualValue > min) {
                spinnerBining.getModel().setValue(min);
            }
        }

    }


    public void aberrantPixelsRemoval() {
        final double radius = IJ.getNumber("radius (pixel)", 1);
        final EftemDataset ds = dialog.getCurrentDataset();
        final TiltSeries[] ts = ds.getTiltSeries();
        System.out.println("hot spot removal with radius of " + radius);
        final WizardApprentice worker = new WizardApprentice<Boolean>(dialog, "remove aberrant pixels") {

            @Override
            protected Boolean doInBackground() throws Exception {
                setProgress(0);
                if (allCheckBoxAberrantPixels.isSelected())
                    for (int i = 0; i < ts.length; i++) {
                        ImagePlus tmp = new ImagePlus("tmp", ts[i]);
                        IJ.run(tmp, "Aberrant_pixel_removal", "radius=" + radius + " stack");
                        updateProgress((i + 1.0) / ts.length);
                    }
                else {
                    int index = energies.indexOf((String) EnergyImageSpinner.getValue());
                    ImagePlus tmp = new ImagePlus("tmp", ts[index]);
                    tmp.setSlice(tiltindex);
                    IJ.run(tmp, "Aberrant_pixel_removal", "radius=" + radius);
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
    }

    private void onAutoAlign() {
        // Get algorithm and metric
        ds = dialog.getCurrentDataset();
        final Metrics.Metric metric = Metrics.Metric.valueOf((String) comboBoxEvaluator.getSelectedItem());
        final ImageRegistration.Algorithm algorithm = ImageRegistration.Algorithm.Multiresolution;
        final FilteredImage[][] alignedImages = new FilteredImage[ds.getTiltCount()][ds.getWindowCount()];

        for (int i = 0; i < ds.getTiltCount(); i++) {
            alignedImages[i] = ds.getImages(i);
            for (int j = 0; j < ds.getWindowCount(); j++) {
                alignedImages[i][j].setScale(transf[j][i].getScaleX(), transf[j][i].getScaleY());
            }
            //System.out.println("roiMap:"+roiMap);
            for (int j = 0; j < alignedImages[i].length; j++) alignedImages[i][j].getImage().setRoi(roiMap);
        }
        //final int refIndex = referenceWindowComboBox.getSelectedIndex();
        final double radius = (Double) spinnerAutoAliRadius.getValue();
        System.out.println("radius used is: " + radius);

        // Align images
        final WizardApprentice worker = new WizardApprentice<Boolean>(dialog, "Aligning images using " + metric) {
            @Override
            protected Boolean doInBackground() throws Exception {
                final ImageRegistration alignator = new ImageRegistration(algorithm, metric);
                alignator.addObserver(this);

                ImageRegistration.Transform[] transforms;
                setProgress(0);
                if (allCheckBoxAutoAli.isSelected()) {
                    ArrayList<Future> jobs = new ArrayList<Future>(alignedImages.length);
                    ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
                    alignator.setExecutorService(exec);
                    ExecutorService exec2 = Executors.newFixedThreadPool(Prefs.getThreads());
                    Chrono time = new Chrono();
                    time.start();
                    for (int i = 0; i < alignedImages.length; i++) {
                        final int ii = i;
                        jobs.add(exec2.submit(new Thread() {
                            public void run() {
                                ImageProcessor fip = ds.getImage(ii, refindex).getImageForAlignment(radius);
                                fip.setRoi(roiMap);
                                try {
                                    ImageRegistration.Transform[] transforms = alignator.alignSeries(fip, alignedImages[ii], radius);
                                    //updateProgress((ii + 1.0) / alignedImages.length);
                                    for (int j = 0; j < alignedImages[ii].length; j++) {
                                        //alignedImages[i][j].applyTransform(transforms[j]);
                                        transf[j][ii] = transforms[j];
                                        transf[j][ii].setScale(alignedImages[ii][j].getScaleX(), alignedImages[ii][j].getScaleY());
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }));

                        /*ImageProcessor fip = ds.getImage(i, refindex).getImageForAlignment(radius);
                        fip.setRoi(roiMap);
                        transforms = alignator.alignSeries(fip, alignedImages[i], radius);
                        updateProgress((i + 1.0) / alignedImages.length);
                        for (int j = 0; j < alignedImages[i].length; j++) {
                            //alignedImages[i][j].applyTransform(transforms[j]);
                            transf[j][i] = transforms[j];
                            transf[j][i].setScale(alignedImages[i][j].getScaleX(),alignedImages[i][j].getScaleY());
                        }   */
                    }
                    int nbFinished = 0;
                    for (Future f : jobs) {
                        f.get();
                        nbFinished++;
                        updateProgress(((double) nbFinished) / alignedImages.length);
                    }
                    time.stop();
                    System.out.println("total time= " + time.delayString());
                } else {
                    ImageProcessor fip = ds.getImage(tiltindex, refindex).getImageForAlignment(radius);
                    fip.setRoi(roiMap);
                    transforms = alignator.alignSeries(fip, alignedImages[tiltindex], radius);
                    for (int j = 0; j < alignedImages[tiltindex].length; j++) {
                        //alignedImages[i][j].applyTransform(transforms[j]);
                        transf[j][tiltindex] = transforms[j];
                        transf[j][tiltindex].setScale(alignedImages[tiltindex][j].getScaleX(), alignedImages[tiltindex][j].getScaleY());
                    }
                }

                return true;
            }
        };
        worker.go();

        try {
            worker.get();
        } catch (InterruptedException ex) {
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        } catch (ExecutionException ex) {
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            return;
        }
        updateViews();


    }


    private ImageProcessor previewMap(boolean showRegression) {
        if (mapper == null) {
            //System.out.println("preview map: mapper is null");
            return new FloatProcessor(displayWidth, displayHeight);
        }
        if (mapper.getSignalCount() <= 0) {
            return new FloatProcessor(displayWidth, displayHeight);
        }
        //final eftemtomoj.FilteredImage[] images = ds.getMappingImages(tiltindex);
        final FilteredImage[] images = new FilteredImage[ds.getMappingWindowCount()];
        int index = 0;
        final double binning = (Double) spinnerBining.getValue();
        for (int i = 0; i < ds.getWindowCount(); i++) {
            if (ds.isUsedForMapping(i)) {
                images[index] = new FilteredImage(getImageForDisplay(i, (Double) spinnerBining.getValue(), true), ds.getEnergyShift(i), ds.getExposureTime(i), ds.getSlitWidth(i), ds.getTiltAngle(tiltindex), ds.isUsedForMapping(i));
                images[index].setSignal(ds.isSignal(i));
                index++;
            }
        }
        // Compute map at zero tilt only

        ImageProcessor[] maps;
        try {
            //maps = mapper.computeMap(images);
            mapper.setImages(images);
            maps = mapper.computeMap();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Process aborted", JOptionPane.ERROR_MESSAGE);
//	    ex.printStackTrace();
            return null;
        }
        if (showRegression)
            new ImagePlus("regression map" + " law " + mapper.getModel() + " tilt" + tiltindex, maps[1]).show();
        return maps[0];
    }

    private void createMaps() {
        if (mapper == null) {
            return;
        }

        final ImageStack is = new ImageStack(ds.getWidth(), ds.getHeight());
        for (int i = 0; i < ds.getTiltCount(); i++) {
            is.addSlice("" + ds.getTiltAngle(i), new FloatProcessor(ds.getWidth(), ds.getHeight()));
        }
        final Chrono time = new Chrono(ds.getTiltCount());
        time.start();
        final int[] completion = {0};


        int cpus = Runtime.getRuntime().availableProcessors();
        final ExecutorService pool = Executors.newFixedThreadPool(cpus);
        java.util.List<Future> futures = new ArrayList<Future>();
        for (int j = 0; j < ds.getTiltCount(); j++) {
            final int jj = j;
            futures.add(pool.submit(new Runnable() {
                public void run() {
                    final FilteredImage[] images = new FilteredImage[ds.getMappingWindowCount()];
                    int index = 0;
                    for (int i = 0; i < ds.getWindowCount(); i++) {
                        if (ds.isUsedForMapping(i)) {
                            images[index] = ds.getImage(jj, i);
                            images[index].applyTransform(transf[i][jj]);
                            index++;
                        }
                    }

                    // Compute map at zero tilt only

                    ImageProcessor[] maps;
                    try {
                        maps = mapper.computeMap(images);
                        is.getProcessor(jj + 1).copyBits(maps[0], 0, 0, Blitter.COPY);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Process aborted", JOptionPane.ERROR_MESSAGE);
                    }
                    completion[0]++;
                }
            }));
        }

        pool.shutdown();


        final Thread progress = new Thread() {
            public void run() {
                ProgressMonitor toto = new ProgressMonitor(mappingPanel, "compute maps", "", 0, ds.getTiltCount());
                System.out.println("pool " + pool.isTerminated());
                while (!pool.isTerminated() && completion[0] >= 0) {
                    if (toto.isCanceled()) {
                        //ConcurrencyUtils.shutdown();
                        pool.shutdownNow();
                        //T.interrupt();
                        toto.close();
                        System.out.println("process interrupted");
                        IJ.showStatus("process interrupted");
                    } else {
                        System.out.println("completion " + completion[0]);
                        toto.setProgress(completion[0]);
                        time.stop();
                        String note = completion[0] + "/" + ds.getTiltCount();
                        if (completion[0] > 0)
                            note += " approximately " + time.remainString(completion[0]) + " left";
                        toto.setNote(note);
                        try {
                            sleep(1000);
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }
                }
                toto.close();
            }
        };
        progress.start();
        new ImagePlus("Elemental maps " + mapper.getModel() + " law " + mapper.usedEnergyAsString(), is).show();


    }

    private void onLoadTransforms() {
        //JOptionPane.showMessageDialog(dialog, "This feature has not been implemented yet", "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
        OpenDialog od = new OpenDialog("open transforms file...", "");
        String dir = od.getDirectory();
        String name = od.getFileName();
        if (dir == null || name == null) {
            return;
        }
        try {
            BufferedReader in = new BufferedReader(new FileReader(dir + name));
            for (int i = 0; i < transf.length; i++) {
                for (int j = 0; j < transf[0].length; j++) {
                    String line = in.readLine();
                    String[] words = line.split("\\s+");
                    ImageRegistration.Transform t = new ImageRegistration.Transform(Double.valueOf(words[0]), Double.valueOf(words[1]));
                    transf[i][j] = t;
                }
            }

            in.close();
        } catch (IOException ioe) {
            System.out.println("error loading transforms: " + ioe);
        }
        int index = tilts.indexOf(spinnerTiltImage.getValue());
        int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
        spinnerTx.setValue(transf[nrjindex][index].getTranslateX());
        spinnerTy.setValue(transf[nrjindex][index].getTranslateY());
        spinnerRotationValue.setValue(transf[nrjindex][index].getRotate());
        updateViews();

    }

    private void onSaveTransforms() {
        //JOptionPane.showMessageDialog(dialog, "This feature has not been implemented yet", "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
        SaveDialog sd = new SaveDialog("save transforms as...", "eftem_transforms", ".txt");
        String dir = sd.getDirectory();
        String name = sd.getFileName();
        if (dir == null || name == null) {
            return;
        }
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(dir + name));
            for (int i = 0; i < transf.length; i++) {
                for (int j = 0; j < transf[0].length; j++) {
                    ImageRegistration.Transform t = transf[i][j];
                    if (t != null)
                        out.write("" + t.getTranslateX() + "\t" + t.getTranslateY() + "\t" + t.getRotate() + "\t" + t.getScaleX() + "\t" + t.getScaleY() + "\n");
                    else
                        out.write("0\t0\n");
                }
            }
            out.close();
        } catch (Exception e) {
            System.out.println("error while saving transforms: " + e);
        }
    }

    private void onSaveAlignedImages() {
        //JOptionPane.showMessageDialog(dialog, "This feature has not been implemented yet", "Not Yet Implemented", JOptionPane.INFORMATION_MESSAGE);
        SaveDialog sd = new SaveDialog("save images as...", ds.getTiltSeries(0).getTitle(), ".tif");
        String dir = sd.getDirectory();
        String name = sd.getFileName();
        if (dir == null || name == null) {
            return;
        }
        for (int j = 0; j < ds.getWindowCount(); j++) {
            TiltSeries ts = ds.getTiltSeries(j);
            ImageStack is = new ImageStack(ds.getWidth(), ds.getHeight());
            for (int i = 0; i < ds.getTiltCount(); i++) {
                ImageProcessor ip = ts.getProcessor(i + 1).duplicate();
                float[] pixs = (float[]) ip.getPixels();
                AffineTransform T = new AffineTransform();
                T.scale(transf[j][i].getScaleX(), transf[j][i].getScaleY());
                T.rotate(Math.toRadians(transf[j][i].getRotate()));
                T.translate(transf[j][i].getTranslateX(), transf[j][i].getTranslateY());
                try {
                    T = T.createInverse();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                pixs = SubImage.getSubImagePixels(pixs, T, ip.getStatistics(), ip.getWidth(), ip.getHeight(), ip.getWidth(), ip.getHeight(), ImageOrigin, false, false);
                ip.setPixels(pixs);
                is.addSlice(ip);
//                eftemtomoj.FilteredImage image = new eftemtomoj.FilteredImage(ts.getProcessor(i + 1).duplicate(), ts.getEnergyShift(), ts.getExposureTime(), ts.getSlitWidth(), ds.getTiltAngle(i));
//                image.applyTransform(transf[j][i]);
//                is.addSlice("" + ds.getTiltAngle(i), image.getImage());
            }
            if (ds.getTiltCount() > 1)
                new FileSaver(new ImagePlus(ts.getTitle(), is)).saveAsTiffStack(dir + name + ts.getEnergyShift() + ".tif");
            else
                new FileSaver(new ImagePlus(ts.getTitle(), is)).saveAsTiff(dir + name + ts.getEnergyShift() + ".tif");
        }


    }

    private void onSaveProject() {
        SaveDialog sd = new SaveDialog("save project as...", "eftem_project", ".xml");
        String dir = sd.getDirectory();
        String name = sd.getFileName();
        if (dir == null || name == null) {
            return;
        }
        //int ok = JOptionPane.showConfirmDialog(saveButton, "do you want to saves the images?", "save images?", JOptionPane.YES_NO_OPTION);
        //boolean saveImages = (ok == JOptionPane.OK_OPTION);
        boolean saveImages = true;
        String nameWithoutExtension = name.substring(0, name.lastIndexOf('.'));
        if (saveImages) {
            //IJ.showMessage("saving of images not yet implemented");
            System.out.println("saving images");
        }

        TiltSeries[] data = ds.getTiltSeries();
        if (data == null || data.length == 0) {
            IJ.showMessage("there is nothing to save!");
            return;
        }
        Element root = new Element("EnergiesImages");
        Document document = new Document(root);

        for (int i = 0; i < data.length; i++) {
            Element nrj = new Element("EnergyImage");
            root.addContent(nrj);

            Element title = new Element("Title");
            title.setText(data[i].getTitle());
            nrj.addContent(title);

            Element nrjshift = new Element("EnergyShift");
            nrjshift.setText("" + data[i].getEnergyShift());
            nrj.addContent(nrjshift);

            Element slitwidth = new Element("SlitWidth");
            slitwidth.setText("" + data[i].getSlitWidth());
            nrj.addContent(slitwidth);

            Element exposure = new Element("ExposureTime");
            exposure.setText("" + data[i].getExposureTime());
            nrj.addContent(exposure);

            Element mapping = new Element("UseForMapping");
            mapping.setText("" + data[i].isUsedForMapping());
            nrj.addContent(mapping);

            Element comment = new Element("Comment");
            comment.setText(data[i].getComment());
            nrj.addContent(comment);

            Element images = new Element("Images");
            if (saveImages) {
                File f = new File(dir + i);
                if (!f.exists() && !f.mkdirs()) {
                    IJ.error("could not create the directory");
                    return;
                }
                for (int j = 1; j <= data[i].getSize(); j++) {
                    Element img = new Element("Image");
                    ImageProcessor ip = data[i].getProcessor(j);
                    ImagePlus imp = new ImagePlus(data[i].getSliceLabel(j), ip);

                    String localpath = "." + System.getProperty("file.separator") + i + System.getProperty("file.separator") + nameWithoutExtension + "_" + i + "_" + j + ".tif";
                    System.out.println("saving image: " + localpath);
                    IJ.save(imp, dir + localpath);
                    img.setText(localpath);
                    images.addContent(img);
                }
            } else {
                images.setText("undefined");
            }
            nrj.addContent(images);
        }
        if (ds.getTiltAngles() != null) {
            Element tilts = new Element("TiltAngles");
            String angles = "";
            for (float tomoTiltAngle : ds.getTiltAngles()) {
                angles += tomoTiltAngle + " ";
            }
            tilts.setText(angles);
            root.addContent(tilts);
        }

        try {
            XMLOutputter output = new XMLOutputter(Format.getPrettyFormat());
            output.output(document, new FileOutputStream(dir + name));
        } catch (IOException e) {
            System.out.println("error while saving xml file : " + e);
        }


    }


    private void createUIComponents() {

        possibleIncrements = new Double[]{0.1, 0.5, 1.0, 2.0, 5.0, 10.0};
        spinnerTranslationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerTranslationIncrement.setValue(possibleIncrements[possibleIncrements.length - 1]);
        spinnerRotationIncrement = new JSpinner(new SpinnerListModel(possibleIncrements));
        spinnerRotationIncrement.setValue(possibleIncrements[possibleIncrements.length - 1]);
        spinnerBining = new JSpinner(new SpinnerNumberModel(1, 0.5, 2, 0.1));
        spinnerTx = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1.0));
        spinnerTy = new JSpinner(new SpinnerNumberModel(0, -10, 10, 1.0));
        spinnerRotationValue = new JSpinner(new SpinnerNumberModel(0, -360.0, 360.0, 0.5));

        EnergyImageSpinner = new JSpinner(new SpinnerListModel(new String[]{"un ", "deux"}));
        spinnerTiltImage = new JSpinner(new SpinnerListModel(new String[]{"un", "deux"}));
        hw = null;
        plotPanel2 = new PlotPanel2();
        plotPanel = plotPanel2;
        backgroundModelComboBox = new JComboBox();

        spinnerAutoAliRadius = new JSpinner(new SpinnerNumberModel(0, 0, 100, 0.1));

        scalexSpinner = new JSpinner(new SpinnerNumberModel(1, 0.00001, 2, 0.001));
        scaleySpinner = new JSpinner(new SpinnerNumberModel(1, 0.00001, 2, 0.001));
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
        Panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        generalSplitPane = new JSplitPane();
        generalSplitPane.setDividerLocation(133);
        generalSplitPane.setDividerSize(5);
        generalSplitPane.setOrientation(0);
        generalSplitPane.setResizeWeight(0.0);
        Panel1.add(generalSplitPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        splitPaneImagesMenu = new JSplitPane();
        splitPaneImagesMenu.setDividerSize(5);
        splitPaneImagesMenu.setResizeWeight(1.0);
        generalSplitPane.setRightComponent(splitPaneImagesMenu);
        splitPaneImages = new JSplitPane();
        splitPaneImages.setDividerLocation(282);
        splitPaneImages.setDividerSize(5);
        splitPaneImages.setOrientation(0);
        splitPaneImages.setResizeWeight(0.5);
        splitPaneImagesMenu.setLeftComponent(splitPaneImages);
        splitPaneAliMap = new JSplitPane();
        splitPaneAliMap.setDividerSize(5);
        splitPaneAliMap.setResizeWeight(0.5);
        splitPaneImages.setRightComponent(splitPaneAliMap);
        jPanelAli = new JPanel();
        jPanelAli.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneAliMap.setLeftComponent(jPanelAli);
        aliLabel = new JLabel();
        aliLabel.setText("");
        jPanelAli.add(aliLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(18, 0), null, 0, false));
        jPanelMap = new JPanel();
        jPanelMap.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneAliMap.setRightComponent(jPanelMap);
        mapLabel = new JLabel();
        mapLabel.setText("");
        jPanelMap.add(mapLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        splitPaneRefComb = new JSplitPane();
        splitPaneRefComb.setDividerSize(5);
        splitPaneRefComb.setResizeWeight(0.5);
        splitPaneImages.setLeftComponent(splitPaneRefComb);
        jPanelRef = new JPanel();
        jPanelRef.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneRefComb.setLeftComponent(jPanelRef);
        refLabel = new JLabel();
        refLabel.setText("");
        refLabel.setVerticalAlignment(0);
        jPanelRef.add(refLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        jPanelCombine = new JPanel();
        jPanelCombine.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneRefComb.setRightComponent(jPanelCombine);
        combineLabel = new JLabel();
        combineLabel.setText("");
        jPanelCombine.add(combineLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        parametersPanel = new JPanel();
        parametersPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPaneImagesMenu.setRightComponent(parametersPanel);
        tabbedPane2 = new JTabbedPane();
        parametersPanel.add(tabbedPane2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(284, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane2.addTab("combine", panel1);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("combination type");
        panel3.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioButtonSuperpose = new JRadioButton();
        radioButtonSuperpose.setSelected(true);
        radioButtonSuperpose.setText("superimpose");
        panel3.add(radioButtonSuperpose, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioButtonDifference = new JRadioButton();
        radioButtonDifference.setText("difference");
        panel3.add(radioButtonDifference, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        normalizeImagesCheckBox = new JCheckBox();
        normalizeImagesCheckBox.setText("normalize images");
        panel4.add(normalizeImagesCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        invertReferenceCheckBox = new JCheckBox();
        invertReferenceCheckBox.setText("invert reference");
        panel4.add(invertReferenceCheckBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        histogramLabel = new JLabel();
        histogramLabel.setText("");
        panel2.add(histogramLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(0, 104), null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane2.addTab("mapping", panel5);
        mappingPanel = new JPanel();
        mappingPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(mappingPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("Linear");
        defaultComboBoxModel1.addElement("Quadratic");
        defaultComboBoxModel1.addElement("Exponential");
        defaultComboBoxModel1.addElement("Power");
        defaultComboBoxModel1.addElement("Logarithmic");
        defaultComboBoxModel1.addElement("LogPolynomial");
        defaultComboBoxModel1.addElement("LogLogPolynomial");
        backgroundModelComboBox.setModel(defaultComboBoxModel1);
        mappingPanel.add(backgroundModelComboBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        createMapsButton = new JButton();
        createMapsButton.setText("Create maps");
        mappingPanel.add(createMapsButton, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        regressionMapButton = new JButton();
        regressionMapButton.setText("Regression map");
        mappingPanel.add(regressionMapButton, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Backgroud subtraction");
        mappingPanel.add(label2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Model");
        mappingPanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        mappingPanel.add(scrollPane1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setViewportView(plotPanel);
        menuPanel = new JPanel();
        menuPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        generalSplitPane.setLeftComponent(menuPanel);
        tiltImagePanel = new JPanel();
        tiltImagePanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(tiltImagePanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(166, 104), null, 0, true));
        tiltImagePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label4 = new JLabel();
        label4.setText("Tilt Image");
        tiltImagePanel.add(label4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltImagePanel.add(spinnerTiltImage, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        previousImageButton = new JButton();
        previousImageButton.setText("Prev");
        tiltImagePanel.add(previousImageButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        NextImageButton = new JButton();
        NextImageButton.setText("Next");
        tiltImagePanel.add(NextImageButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("down sampling");
        tiltImagePanel.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltImagePanel.add(spinnerBining, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePanel = new JPanel();
        EnergyImagePanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(EnergyImagePanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(190, 104), null, 0, true));
        EnergyImagePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label6 = new JLabel();
        label6.setText("Energy Image");
        EnergyImagePanel.add(label6, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePanel.add(EnergyImageSpinner, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePreviousButton = new JButton();
        EnergyImagePreviousButton.setText("Prev");
        EnergyImagePanel.add(EnergyImagePreviousButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImageNextButton = new JButton();
        EnergyImageNextButton.setText("Next");
        EnergyImagePanel.add(EnergyImageNextButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxRefImage = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
        defaultComboBoxModel2.addElement("toto");
        comboBoxRefImage.setModel(defaultComboBoxModel2);
        EnergyImagePanel.add(comboBoxRefImage, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("reference energy");
        EnergyImagePanel.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tabbedPaneAli = new JTabbedPane();
        tabbedPaneAli.setTabPlacement(2);
        menuPanel.add(tabbedPaneAli, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        aliPre = new JPanel();
        aliPre.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPaneAli.addTab("pre", aliPre);
        aberrantPixelsRemovalButton = new JButton();
        aberrantPixelsRemovalButton.setText("Aberrant Pixels removal");
        aliPre.add(aberrantPixelsRemovalButton, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        allCheckBoxAberrantPixels = new JCheckBox();
        allCheckBoxAberrantPixels.setSelected(true);
        allCheckBoxAberrantPixels.setText("all");
        aliPre.add(allCheckBoxAberrantPixels, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        aliPre.add(spacer2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        saveProjectButton = new JButton();
        saveProjectButton.setText("save project");
        aliPre.add(saveProjectButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        aliAuto = new JPanel();
        aliAuto.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPaneAli.addTab("automatic", aliAuto);
        final JLabel label8 = new JLabel();
        label8.setText("evaluation method");
        aliAuto.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxEvaluator = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel3 = new DefaultComboBoxModel();
        defaultComboBoxModel3.addElement("NCC");
        defaultComboBoxModel3.addElement("NMI");
        defaultComboBoxModel3.addElement("MS");
        comboBoxEvaluator.setModel(defaultComboBoxModel3);
        aliAuto.add(comboBoxEvaluator, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("variance filter radius");
        aliAuto.add(label9, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        aliAuto.add(spinnerAutoAliRadius, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        allCheckBoxAutoAli = new JCheckBox();
        allCheckBoxAutoAli.setSelected(true);
        allCheckBoxAutoAli.setText("all");
        aliAuto.add(allCheckBoxAutoAli, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        alignButton = new JButton();
        alignButton.setText("align");
        aliAuto.add(alignButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        aliManual = new JPanel();
        aliManual.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPaneAli.addTab("manual", aliManual);
        translationPanel = new JPanel();
        translationPanel.setLayout(new GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
        aliManual.add(translationPanel, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(257, 104), null, 0, false));
        translationPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        tyUpButton = new JButton();
        tyUpButton.setIcon(new ImageIcon(getClass().getResource("/arrow_up.png")));
        tyUpButton.setText("");
        translationPanel.add(tyUpButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txRightButton = new JButton();
        txRightButton.setIcon(new ImageIcon(getClass().getResource("/arrow_right.png")));
        txRightButton.setText("");
        translationPanel.add(txRightButton, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, 1, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        translationPanel.add(spinnerTranslationIncrement, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txLeftButton = new JButton();
        txLeftButton.setIcon(new ImageIcon(getClass().getResource("/arrow_left.png")));
        txLeftButton.setText("");
        translationPanel.add(txLeftButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tyDownButton = new JButton();
        tyDownButton.setIcon(new ImageIcon(getClass().getResource("/arrow_down.png")));
        tyDownButton.setText("");
        translationPanel.add(tyDownButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("Translation");
        translationPanel.add(label10, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(99, 21), null, 0, false));
        translationPanel.add(spinnerTx, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(99, 20), null, 0, false));
        translationPanel.add(spinnerTy, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(99, 20), null, 0, false));
        rotationPanel = new JPanel();
        rotationPanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        aliManual.add(rotationPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(174, 104), null, 0, false));
        rotationPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label11 = new JLabel();
        label11.setText("rotation");
        rotationPanel.add(label11, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationPanel.add(spinnerRotationValue, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rot1Button = new JButton();
        rot1Button.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_anticlockwise.png")));
        rot1Button.setText("");
        rotationPanel.add(rot1Button, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rotationPanel.add(spinnerRotationIncrement, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rot2Button = new JButton();
        rot2Button.setIcon(new ImageIcon(getClass().getResource("/arrow_rotate_clockwise.png")));
        rot2Button.setText("");
        rotationPanel.add(rot2Button, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(73, 26), null, 0, false));
        applyPanel = new JPanel();
        applyPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        aliManual.add(applyPanel, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        applySameOnAllButton1 = new JButton();
        applySameOnAllButton1.setText("apply same on all tilts angles");
        applyPanel.add(applySameOnAllButton1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridBagLayout());
        aliManual.add(panel6, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("scaling X");
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel6.add(label12, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel6.add(scalexSpinner, gbc);
        final JLabel label13 = new JLabel();
        label13.setText("scaling Y");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel6.add(label13, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel6.add(scaleySpinner, gbc);
        constrainAspectRatioCheckBox = new JCheckBox();
        constrainAspectRatioCheckBox.setSelected(true);
        constrainAspectRatioCheckBox.setText("constrain aspect ratio");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        panel6.add(constrainAspectRatioCheckBox, gbc);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPaneAli.addTab("File", panel7);
        saveTransformsButton = new JButton();
        saveTransformsButton.setText("save Transforms");
        panel7.add(saveTransformsButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveAlignedImagesButton = new JButton();
        saveAlignedImagesButton.setText("save aligned images");
        panel7.add(saveAlignedImagesButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        loadTransformsButton = new JButton();
        loadTransformsButton.setText("load Transforms");
        panel7.add(loadTransformsButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(radioButtonSuperpose);
        buttonGroup.add(radioButtonDifference);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return Panel1;
    }
}
