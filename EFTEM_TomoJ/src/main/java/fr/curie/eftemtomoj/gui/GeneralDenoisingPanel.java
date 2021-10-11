package fr.curie.eftemtomoj.gui;

import cern.colt.matrix.tfcomplex.impl.DenseFComplexMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import fr.curie.eftemtomoj.*;
import fr.curie.eftemtomoj.utils.SubImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.SaveDialog;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
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
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 02/02/12
 * Time: 14:44
 * To change this template use File | Settings | File Templates.
 */
public class GeneralDenoisingPanel extends WizardPage implements MouseInputListener, MouseWheelListener {
    private JSplitPane generalSplitPane;
    private JSplitPane splitPaneImagesMenu;
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
    private JPanel parametersPanel;
    private JTabbedPane tabbedPane2;
    private JPanel mappingPanel;
    private JComboBox backgroundModelComboBox;
    private JButton createMapsButton;
    private JButton regressionMapButton;
    private JPanel plotPanel;
    private JPanel menuPanel;
    private JPanel tiltImagePanel;
    private JSpinner spinnerTiltImage;
    private JButton previousImageButton;
    private JButton NextImageButton;
    private JSpinner spinnerBining;
    private JPanel EnergyImagePanel;
    private JSpinner EnergyImageSpinner;
    private JButton EnergyImagePreviousButton;
    private JButton EnergyImageNextButton;
    private JPanel PCAPanel;
    private JButton computeEigenVectorsButton;
    private JCheckBox allTiltCheckbox;
    private JCheckBox keepCheckBox;
    private JLabel PCA_EigenValLabel;
    private JLabel PCA_EnergyContentLabel;
    private JLabel PCA_CumulEnergyLabel;
    private JPanel PCA_EigenImageSelectPanel;
    private JPanel panel1;
    private JButton saveEigenVectorsImagesButton;

    private EftemDataset ds;
    private FilteredImage[] mappingImages;
    private int tiltindex;
    private int refindex;
    private int PCAindex;
    private ArrayList<String> energies;
    private ArrayList<String> tilts;
    private Mapper mapper;
    private MSANoiseFilterBonnet[] msa;
    private boolean[][] axesSelection;
    Roi rois;
    PlotPanel2 plotPanel2;
    private int displayWidth = 256;
    private int displayHeight = 256;
    private Roi roiMap;
    private Point mouseOrigin = null;
    private Point2D.Double ImageOrigin = new Point2D.Double(0, 0);
    private Point2D.Double bkpImgOri = null;
    private int nbaxes;
    private int actualnWindowUsed;

    private Color green = new Color(0, 150, 0);
    private Color red = new Color(175, 0, 0);

    public GeneralDenoisingPanel(WizardDialog dlg) {
        super(dlg, "MAPPING_PAGE" + Math.random(), "Elemental Mapping");

        $$$setupUI$$$();
        refLabel.addMouseListener(this);
        refLabel.addMouseMotionListener(this);
        refLabel.addMouseWheelListener(this);
        refLabel.setFocusable(true);

        aliLabel.addMouseListener(this);
        aliLabel.addMouseMotionListener(this);
        aliLabel.addMouseWheelListener(this);
        aliLabel.setFocusable(true);

        combineLabel.addMouseListener(this);
        combineLabel.addMouseMotionListener(this);
        combineLabel.addMouseWheelListener(this);
        combineLabel.setFocusable(true);

        mapLabel.addMouseListener(this);
        mapLabel.addMouseMotionListener(this);
        mapLabel.addMouseWheelListener(this);
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


        spinnerTiltImage.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                tiltindex = tilts.indexOf(spinnerTiltImage.getValue());
                mappingImages = ds.getImages(tiltindex);
                int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
                //computeMSAs();
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


        plotPanel2.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent pce) {
                if (pce.getPropertyName().equals(PlotPanel2.SELECTION_PROPERTY) && mapper != null) {
                    System.out.println("Selection");
                    actualnWindowUsed = 0;
                    Mapper.ImageType[] imageTypes = plotPanel2.getSelection();
                    for (int i = 0; i < imageTypes.length; i++) {
                        System.out.println("#" + i + ": " + imageTypes[i].name());

                        if (imageTypes[i] == Mapper.ImageType.Background) {
                            mapper.setBackground(i);
                            actualnWindowUsed++;
                        } else if (imageTypes[i] == Mapper.ImageType.Signal) {
                            mapper.setSignal(i);
                            actualnWindowUsed++;
                        } else {
                            mapper.setDisabled(i);
                        }
                    }
                    if (actualnWindowUsed != nbaxes) {
                        computeMSAs();
                        updateViews();
                    } else {
                        updateMap();
                    }

                }
            }
        });
        Mapper.Model[] mod = Mapper.Model.values();

        String[] models = new String[mod.length];
        for (int i = 0; i < models.length; i++) models[i] = mod[i].name();
        backgroundModelComboBox.setModel(new DefaultComboBoxModel(models));
        backgroundModelComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mapper.setModel(Mapper.Model.valueOf((String) backgroundModelComboBox.getSelectedItem()));
                updateMap();
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
        panel1.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                super.componentResized(e);
                System.out.println("resizing general splitpane");
                //generalSplitPane.setMinimumSize(panel1.getParent().getSize());
                generalSplitPane.setPreferredSize(panel1.getParent().getSize());
                if (ds != null) {
                    updateDisplayValues();
                    updateSpinners();
                    updateViews();
                }
                panel1.setBackground(Color.green);
                generalSplitPane.setBackground(Color.red);
            }

        });
        computeEigenVectorsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                computeMSAs();
            }
        });
        keepCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("keep checkbox");
                if (allTiltCheckbox.isSelected()) {
                    for (int t = 0; t < axesSelection.length; t++) {
                        axesSelection[t][PCAindex] = keepCheckBox.isSelected();
                    }
                } else {
                    axesSelection[tiltindex][PCAindex] = keepCheckBox.isSelected();
                }
                updateViews();
                updatePCAButtonColors();

            }
        });
        saveEigenVectorsImagesButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onSaveEigenVectors();
            }
        });
    }

    @Override
    public JComponent getComponent() {
        return panel1;
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
            } else {
                ts[i + offset].setUseForMapping(false);
            }
            System.out.println(ts[i + offset].getEnergyShift() + " used for mapping:" + ts[i + offset].isUsedForMapping());
        }
        ds.tiltSeriesUpdated();
        ds.setLaw(Mapper.Model.valueOf((String) backgroundModelComboBox.getSelectedItem()));

        plotPanel2.clear();
        return true;
    }

    @Override
    public void activate() {
        System.out.println("general denoising activated");
        ds = dialog.getCurrentDataset();

        if (ds == null) {
            System.out.println("Error: No dataset found");
            return;
        }
        //System.out.println("clear");
        plotPanel2.clear();
        mapper = null;
        refindex = 0;
        tiltindex = 0;
        mappingImages = ds.getMappingImages(tiltindex);

        // System.out.println("create energies");
        energies = new ArrayList<String>();
        for (int i = 0; i < mappingImages.length; i++) {
            String tmp = mappingImages[i].getEnergyShift() + "eV";
            energies.add(tmp);
        }

        // System.out.println("create tilts");
        tilts = new ArrayList<String>();
        boolean angle = (ds.getTiltAngle(0) != ds.getTiltAngle(1));
        for (int i = 0; i < ds.getTiltCount(); i++) {
            String tmp = (angle) ? ds.getTiltAngle(i) + "�" : "" + i;
            tilts.add(tmp);
        }

        //System.out.println("set spinner binning");
        spinnerBining.setModel(new SpinnerNumberModel(ds.getWidth() / displayWidth, 0.5, ds.getWidth() / 256.0 * 2, 0.1));
        //System.out.println("set spinner energies");
        EnergyImageSpinner.setModel(new SpinnerListModel(energies));
        //System.out.println("set spinner tilt");
        spinnerTiltImage.setModel(new SpinnerListModel(tilts));


        //System.out.println("set spinner energies value");
        EnergyImageSpinner.setValue(energies.get(1));

        System.out.println("create new mapper");
        mapper = new Mapper(ds);
        Mapper.ImageType[] type = mapper.getImageTypes();
        actualnWindowUsed = 0;

        for (int i = 0; i < type.length; i++) {
            if (type[i] == Mapper.ImageType.Background || type[i] == Mapper.ImageType.Signal) {
                actualnWindowUsed++;
            }
        }

        System.out.println("remove all buttons PCA");
        PCA_EigenImageSelectPanel.removeAll();

        System.out.println("create buttons PCA");
        for (int a = 0; a < ds.getMappingWindowCount(); a++) {
            JButton tmp = new JButton("PC " + (a + 1));
            PCA_EigenImageSelectPanel.add(tmp);
            tmp.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    PCA_EigenImageSelectPanel.getComponent(PCAindex).setEnabled(true);
                    String st = ((JButton) e.getSource()).getText();
                    st = st.split(" ")[1];
                    PCAindex = Integer.parseInt(st) - 1;
                    PCA_EigenImageSelectPanel.getComponent(PCAindex).setEnabled(false);
                    //PCA_EigenImageSelectPanel.getComponent(PCAindex).setBackground(Color.DARK_GRAY);


                    //updateDisplayValues();
                    updateEigenImage();
                    //updateViews();
                }
            });
        }

        System.out.println("compute MSAs");
        computeMSAs();
        //updateViews();
        //   ds.correctNegativeValues();
        // Create new mapper if needed and select model
        //else System.out.println("mapper not null");

        System.out.println("set background model");
        backgroundModelComboBox.setSelectedItem(mapper.getModel().toString());

        System.out.println("fit regression");
        onFitRegression();

        System.out.println("activate update views");
        updateViews();
        updatePCAButtonColors();
        dialog.pack();


    }

    @Override
    public boolean abort() {
        plotPanel2.clear();
        return true;
    }

    public void mouseClicked(MouseEvent e) {

    }

    public void mouseEntered(MouseEvent e) {
        if (e.getSource() == aliLabel) {
            aliLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else if (e.getSource() == combineLabel) {
            combineLabel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
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
            final double bin = (Double) spinnerBining.getValue();
            if (e.getSource() == mapLabel || e.getSource() == refLabel) {
                Point p = e.getPoint();
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


        if ((e.getModifiers() & MouseEvent.BUTTON3_MASK) == MouseEvent.BUTTON3_MASK) {
            Point actualPosition = e.getPoint();
            //Point diff = actualPosition - mouseOrigin;
            double dx = actualPosition.getX() - mouseOrigin.getX();
            double dy = actualPosition.getY() - mouseOrigin.getY();
            ImageOrigin = new Point2D.Double(bkpImgOri.getX() - dx, bkpImgOri.getY() - dy);
            updateViews();
        }


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

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int tmp = e.getWheelRotation();
        System.out.println("mouse wheel " + tmp);
        if (tmp < 0) previousImageButton.doClick();
        if (tmp > 0) NextImageButton.doClick();
    }

    private void updateDisplayValues() {

        //refLabel.setMinimumSize(new Dimension(50, 50));
        //aliLabel.setMinimumSize(new Dimension(50,50));
        //combineLabel.setMinimumSize(new Dimension(50, 50));
        //mapLabel.setMinimumSize(new Dimension(50, 50));
        //this.panel1.setSize(this.panel1.getParent().getSize());
        //this.panel1.setMinimumSize(this.panel1.getParent().getSize());
        // this.panel1.setMaximumSize(this.panel1.getParent().getSize());
        //this.panel1.setLocation(0,0);

        //generalSplitPane.setSize(this.panel1.getSize());
        //generalSplitPane.setLocation(0,0);
        splitPaneImages.setDividerLocation(0.5);
        splitPaneRefComb.setDividerLocation(0.5);
        splitPaneAliMap.setDividerLocation(0.5);
        splitPaneImagesMenu.setResizeWeight(1);
        displayWidth = (splitPaneImages.getWidth() - 15) / 2;

        //displayWidth = (splitPaneTop.getWidth() - splitPaneTop.getDividerSize()) / 2;
        displayHeight = (splitPaneImages.getHeight() - 15) / 2;

        System.out.println("update display: new width=" + displayWidth + " new height=" + displayHeight);
    }

    protected void updateSpinners() {
        if (ds != null) {
            double actualValue = ((SpinnerNumberModel) spinnerBining.getModel()).getNumber().doubleValue();
            double min = Math.min(ds.getWidth() / (double) displayWidth, ds.getHeight() / (double) displayHeight);
            System.out.println("update spinners min" + min + " actual " + actualValue);
            if (actualValue > min) {
                spinnerBining.getModel().setValue(min);
            }
        }

    }

    private void updateViews() {
        System.out.println("update views");
        if (mapper == null) return;
        final double bin = (Double) spinnerBining.getValue();
        //ref

        final int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
        ImageProcessor imgRef = ds.getMappingImages(tiltindex)[nrjindex].getImage();
        imgRef = binning(imgRef);
        autoAdjust(imgRef);


        if (roiMap != null) {
            Rectangle rect = roiMap.getBounds();
            Roi roibin = (roiMap instanceof OvalRoi) ? new OvalRoi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin)) : new Roi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin));
            imgRef = imgRef.convertToRGB();
            imgRef.setColor(Color.MAGENTA);
            imgRef.draw(roibin);
        }
        refLabel.setIcon(new ImageIcon(imgRef.getBufferedImage(), "image Ref"));
        //refLabel.setMinimumSize(new Dimension((int) refLabel.getPreferredSize().getWidth()/2,(int)refLabel.getPreferredSize().getHeight()/2));
        //refLabel.setMaximumSize(new Dimension((int) refLabel.getPreferredSize().getWidth()*2,(int)refLabel.getPreferredSize().getHeight()*2));
        updateEigenImage();
        updateReconstructedImage();
        updateMap();

    }

    public void updateEigenImage() {
        final double bin = (Double) spinnerBining.getValue();
        final int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
        if (msa != null && msa[tiltindex] == null) msa[tiltindex] = computeMSA(tiltindex);
        if (msa != null && PCAindex >= msa[tiltindex].getNumberOfAxes()) PCAindex = 0;
        ImageProcessor imgAli = (msa != null) ? msa[tiltindex].getEigenVectorImages().getProcessor(PCAindex + 1) : ds.getMappingImages(tiltindex)[nrjindex].getImage();
        System.out.println("bin eigenimage: width=" + imgAli.getWidth() + ", height=" + imgAli.getHeight());
        imgAli = binning(imgAli);
        autoAdjust(imgAli);
        if (roiMap != null) {
            Rectangle rect = roiMap.getBounds();
            Roi roibin = (roiMap instanceof OvalRoi) ? new OvalRoi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin)) : new Roi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin));

            imgAli = imgAli.convertToRGB();
            imgAli.setColor(Color.MAGENTA);
            imgAli.draw(roibin);
        }

        aliLabel.setIcon(new ImageIcon(imgAli.getBufferedImage(), "Eigen image"));
        //aliLabel.setMinimumSize(new Dimension((int) aliLabel.getPreferredSize().getWidth()/2,(int)aliLabel.getPreferredSize().getHeight()/2));
        //aliLabel.setMaximumSize(new Dimension((int) aliLabel.getPreferredSize().getWidth()*2,(int)aliLabel.getPreferredSize().getHeight()*2));
        if (msa != null) {
            PCA_EigenValLabel.setText("" + msa[tiltindex].getEigenValues()[PCAindex]);
            if (PCAindex == 0) {
                PCA_EnergyContentLabel.setText("Not applicable");
                PCA_CumulEnergyLabel.setText("not applicable");
            } else {
                PCA_EnergyContentLabel.setText("" + (int) (msa[tiltindex].getEigenValues()[PCAindex] / msa[tiltindex].getEigenValueTotal() * 100) + "%");
                double cec = 0;
                for (int i = 1; i <= PCAindex; i++) {
                    double ec = msa[tiltindex].getEigenValues()[i] / msa[tiltindex].getEigenValueTotal();
                    cec += ec;
                }
                PCA_CumulEnergyLabel.setText("" + (int) (cec * 100) + "%");
            }
        }
        //PCA_EnergyContentLabel.setText(""+msa[tiltindex].get);
        if (axesSelection != null) keepCheckBox.setSelected(axesSelection[tiltindex][PCAindex]);
    }

    public void updateReconstructedImage() {
        System.out.println("update reconstruction image");
        final double bin = (Double) spinnerBining.getValue();
        final int nrjindex = energies.indexOf(EnergyImageSpinner.getValue());
        ImageProcessor comb = (msa != null) ? getReconstructionMSA(tiltindex)[nrjindex].getImage() : ds.getMappingImages(tiltindex)[nrjindex].getImage();
        comb = binning(comb);
        autoAdjust(comb);
        if (roiMap != null) {
            Rectangle rect = roiMap.getBounds();
            Roi roibin = (roiMap instanceof OvalRoi) ? new OvalRoi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin)) : new Roi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin));
            comb = comb.convertToRGB();
            comb.setColor(Color.MAGENTA);
            comb.draw(roibin);
        }
        combineLabel.setIcon(new ImageIcon(comb.getBufferedImage(), "reconstructed image"));
        //combineLabel.setMinimumSize(new Dimension((int) combineLabel.getPreferredSize().getWidth()/2,(int)combineLabel.getPreferredSize().getHeight()/2));
        //combineLabel.setMaximumSize(new Dimension((int) combineLabel.getPreferredSize().getWidth()*2,(int)combineLabel.getPreferredSize().getHeight()*2));

    }

    public void updateMap() {
        System.out.println("update map");
        final double bin = (Double) spinnerBining.getValue();
        ImageProcessor map = previewMap(false);
        map = binning(map);
        onFitRegression();
        autoAdjust(map);
        if (roiMap != null) {
            Rectangle rect = roiMap.getBounds();
            Roi roibin = (roiMap instanceof OvalRoi) ? new OvalRoi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin)) : new Roi((int) (rect.getX() / bin), (int) (rect.getY() / bin), (int) (rect.getWidth() / bin), (int) (rect.getHeight() / bin));

            map = map.convertToRGB();
            map.setColor(Color.MAGENTA);
            map.draw(roibin);
        }
        mapLabel.setIcon(new ImageIcon(map.getBufferedImage(), "image map"));
        // mapLabel.setMinimumSize(new Dimension((int) mapLabel.getPreferredSize().getWidth()/2,(int)mapLabel.getPreferredSize().getHeight()/2));
        // mapLabel.setMaximumSize(new Dimension((int) mapLabel.getPreferredSize().getWidth()*2,(int)mapLabel.getPreferredSize().getHeight()*2));
    }


    private ImageProcessor binning(ImageProcessor ip) {
        float[] pixs = (float[]) ip.getPixels();
        int sx = ip.getWidth();
        int sy = ip.getHeight();

        int nsx = (int) (sx / (Double) spinnerBining.getValue());
        int nsy = (int) (sy / (Double) spinnerBining.getValue());
        int nsize = nsx * nsy;
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

        System.out.println("binning nsx=" + nsx + ", nsy=" + nsy + ", display width=" + displayWidth + ", height=" + displayHeight);

        pixs = SubImage.getSubImagePixels(pixs, new AffineTransform(), ip.getStatistics(), nsx, nsy, displayWidth, displayHeight, ImageOrigin, false, false);
        return new FloatProcessor(displayWidth, displayHeight, pixs, null);
        //return new FloatProcessor(nsx, nsy, pixs, null);
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

    private void onFitRegression() {
        if (mapper != null) {
            try {
                System.out.println(roiMap);
                plotPanel2.setData(mapper, roiMap);
                plotPanel2.setRegression(mapper.computeRegressionCurve(300, roiMap));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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
        final FilteredImage[] images = getReconstructionMSA(tiltindex);
        /*int index = 0;
       final double binning = (Double) spinnerBining.getValue();
       for (int i = 0; i < ds.getWindowCount(); i++) {
           if (ds.isUsedForMapping(i)) {
               images[index] = new eftemtomoj.FilteredImage(getImageForDisplay(i, (Double) spinnerBining.getValue(), true), ds.getEnergyShift(i), ds.getExposureTime(i), ds.getSlitWidth(i), ds.getTiltAngle(tiltindex), ds.isUsedForMapping(i));
               images[index].setSignal(ds.isSignal(i));
               index++;
           }
       } */
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
        onFitRegression();
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

                    final FilteredImage[] images = getReconstructionMSA(jj);

                    // Compute map at zero tilt only

                    ImageProcessor[] maps;
                    try {
                        maps = mapper.computeMap(images);
                        is.getProcessor(jj + 1).copyBits(maps[0], 0, 0, Blitter.COPY);
                        String vectorused = "";
                        for (int v = 0; v < nbaxes; v++) {
                            if (axesSelection[jj][v]) vectorused += (v + 1) + "_";
                        }
                        is.setSliceLabel(is.getSliceLabel(jj + 1) + "(PCA " + vectorused + ")", jj + 1);
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
        String vectorused = "";
        for (int v = 0; v < nbaxes; v++) {
            int compt = 0;
            for (int t = 0; t < ds.getTiltCount(); t++) {
                if (axesSelection[t][v]) compt++;
            }
            if (compt > 0) {
                vectorused += (v + 1);
                if (compt < ds.getTiltCount()) vectorused += "p";
                vectorused += "_";
            }
        }
        new ImagePlus("Elemental maps " + "PCA using " + vectorused + mapper.getModel() + " law " + mapper.usedEnergyAsString(), is).show();


    }

    protected void computeMSAs() {
        if (nbaxes != actualnWindowUsed) {
            for (int a = 0; a < ds.getMappingWindowCount(); a++) {
                // System.out.println("a="+a+"  nbWindow="+ds.getMappingWindowCount()+" ");
                PCA_EigenImageSelectPanel.getComponent(a).setVisible(a < actualnWindowUsed);
            }
        }

        nbaxes = actualnWindowUsed;
        if (msa == null) msa = new MSANoiseFilterBonnet[ds.getTiltCount()];
        for (int i = 0; i < msa.length; i++) {
            msa[i] = null;
        }
        axesSelection = new boolean[msa.length][nbaxes];
        for (int i = 0; i < msa.length; i++) {
            for (int j = 0; j < axesSelection[i].length; j++) {
                axesSelection[i][j] = true;
            }
        }

        /*for(int a=0;a<ds.getMappingWindowCount();a++){
           // System.out.println("a="+a+"  nbWindow="+ds.getMappingWindowCount()+" ");
           PCA_EigenImageSelectPanel.getComponent(a).setVisible(a<nbaxes);
       } */

    }


    private MSANoiseFilterBonnet computeMSA(int tiltindex) {

        FilteredImage[] fis = ds.getMappingImages(tiltindex);
        Mapper.ImageType[] type = mapper.getImageTypes();
        rois = getCommonRoi(fis, ds.getZeroOffset());
        ImageStack is = new ImageStack((int) rois.getBounds().getWidth(), (int) rois.getBounds().getHeight());
        for (int j = 0; j < fis.length; j++) {
            if (type[j] == Mapper.ImageType.Background || type[j] == Mapper.ImageType.Signal) {
                ImageProcessor ip = fis[j].getImage();
                ip.setRoi(rois);
                is.addSlice("" + fis[j].getEnergyShift(), ip.crop());
            }
        }
        //final int nbaxes =  type.length ;
        MSANoiseFilterBonnet msa = new MSANoiseFilterBonnet(is, nbaxes);
        String displ = msa.PCA();
        System.out.println("image " + tiltindex + " tilt " + ds.getTiltAngle(tiltindex) + "\n" + displ);
        return msa;
    }

    private FilteredImage[] getReconstructionMSA(int tiltIndex) {
        if (nbaxes != actualnWindowUsed) {
            for (int i = 0; i < ds.getTiltCount(); i++) {
                msa[i] = null;
            }

        }
        //System.out.println("msa="+msa);
        if (msa[tiltIndex] == null) msa[tiltIndex] = computeMSA(tiltIndex);

        FilteredImage[] fis = ds.getMappingImages(tiltIndex);
        Mapper.ImageType[] type = mapper.getImageTypes();
        MSANoiseFilterBonnet msab = msa[tiltIndex];
        for (int i = 0; i < nbaxes; i++) {
            msab.setSelected(i, axesSelection[tiltIndex][i]);
        }
        ImageStack rec = msab.PCA_rec();
        FilteredImage[] result = new FilteredImage[fis.length];
        int size = rec.getWidth() * rec.getHeight();
        int index = 0;
        for (int i = 0; i < fis.length; i++) {
            result[i] = fis[i].getCopy();
            if (type[i] == Mapper.ImageType.Signal || type[i] == Mapper.ImageType.Background) {
                ImageProcessor ip = result[i].getImage();
                ImageProcessor recp = rec.getProcessor(index + 1);
                ip.copyBits(recp, (int) rois.getBounds().getX(), (int) rois.getBounds().getY(), Blitter.COPY);
                index++;
            }
        }
        return result;
    }

    private Roi getCommonRoi(FilteredImage[] fis, double zeroOffset) {
        ImageProcessor ip = fis[0].getImage();
        int xmin = 0;
        int xmax = ip.getWidth();
        int ymin = 0;
        int ymax = ip.getHeight();
        int cx = ip.getWidth() / 2;
        int cy = ip.getHeight() / 2;

        for (FilteredImage fi : fis) {
            ip = fi.getImage();
            int index = 0;
            while (index < ip.getWidth() && ip.getPixelValue(index, cy) == zeroOffset) index++;
            index--;
            xmin = Math.max(xmin, index);

            index = 0;
            while (index < ip.getHeight() && ip.getPixelValue(cx, index) == zeroOffset) index++;
            index--;
            ymin = Math.max(ymin, index);

            index = ip.getWidth() - 1;
            while (index >= 0 && ip.getPixelValue(index, cy) == zeroOffset) index--;
            index++;
            xmax = Math.min(xmax, index);

            index = ip.getHeight() - 1;
            while (index >= 0 && ip.getPixelValue(cx, index) == zeroOffset) index--;
            index++;
            ymax = Math.min(ymax, index);

        }
        /*System.out.println("common roi ["+xmin+", "+ymin+", "+xmax+", "+ymax+"]");
        try{
            System.in.read();
        } catch (Exception e){

        }*/
        return new Roi(xmin, ymin, xmax - xmin, ymax - ymin);
        //return new Roi(0,0,ip.getWidth(),ip.getHeight());
    }

    public void onSaveEigenVectors() {
        if (msa == null) {
            IJ.error("no msa to save!!!");
            return;
        }
        SaveDialog sd = new SaveDialog("save eigen vectors as...", "eigenVector", ".tif");
        String dir = sd.getDirectory();
        String name = sd.getFileName();
        if (dir == null || name == null) {
            return;
        }
        for (int t = 0; t < ds.getTiltCount(); t++) {
            if (msa[t] == null) msa[t] = computeMSA(t);
            ImageStack is = msa[t].getEigenVectorImages();
            ImagePlus imp = new ImagePlus("EigenVectors" + t, is);
            FileSaver fs = new FileSaver(imp);
            if (imp.getImageStackSize() > 1) fs.saveAsTiffStack(dir + name + IJ.pad(t, 4) + "tif");
            else fs.saveAsTiff(dir + name + IJ.pad(t, 4) + "tif");
        }

    }

    public void updatePCAButtonColors() {
        for (int i = 0; i < nbaxes; i++) {
            PCA_EigenImageSelectPanel.getComponent(i).setForeground(axesSelection[tiltindex][i] ? green : red);
        }
    }


    private void createUIComponents() {

        spinnerBining = new JSpinner(new SpinnerNumberModel(1, 0.5, 2, 0.1));

        EnergyImageSpinner = new JSpinner(new SpinnerListModel(new String[]{"un", "deux"}));
        spinnerTiltImage = new JSpinner(new SpinnerListModel(new String[]{"un", "deux"}));
        plotPanel2 = new PlotPanel2();
        plotPanel = plotPanel2;
        backgroundModelComboBox = new JComboBox();

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
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        generalSplitPane = new JSplitPane();
        generalSplitPane.setDividerLocation(151);
        generalSplitPane.setDividerSize(5);
        generalSplitPane.setOrientation(0);
        generalSplitPane.setResizeWeight(0.0);
        panel1.add(generalSplitPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        splitPaneImagesMenu = new JSplitPane();
        splitPaneImagesMenu.setDividerSize(5);
        splitPaneImagesMenu.setResizeWeight(1.0);
        generalSplitPane.setRightComponent(splitPaneImagesMenu);
        splitPaneImages = new JSplitPane();
        splitPaneImages.setDividerLocation(171);
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
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane2.addTab("mapping", panel2);
        mappingPanel = new JPanel();
        mappingPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(mappingPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("linear");
        defaultComboBoxModel1.addElement("exponential");
        defaultComboBoxModel1.addElement("logarithmic");
        defaultComboBoxModel1.addElement("power");
        defaultComboBoxModel1.addElement("polynomial 2nd order");
        defaultComboBoxModel1.addElement("log polynomial 2nd order");
        defaultComboBoxModel1.addElement("log-log polynomial 2nd order");
        backgroundModelComboBox.setModel(defaultComboBoxModel1);
        mappingPanel.add(backgroundModelComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        createMapsButton = new JButton();
        createMapsButton.setText("Create maps");
        mappingPanel.add(createMapsButton, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        regressionMapButton = new JButton();
        regressionMapButton.setText("Regression map");
        mappingPanel.add(regressionMapButton, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Model");
        mappingPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel2.add(scrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane1.setViewportView(plotPanel);
        final JLabel label2 = new JLabel();
        label2.setText("Backgroud subtraction");
        panel2.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        menuPanel = new JPanel();
        menuPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        generalSplitPane.setLeftComponent(menuPanel);
        tiltImagePanel = new JPanel();
        tiltImagePanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(tiltImagePanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(166, 104), null, 0, true));
        tiltImagePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label3 = new JLabel();
        label3.setText("Tilt Image");
        tiltImagePanel.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltImagePanel.add(spinnerTiltImage, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        previousImageButton = new JButton();
        previousImageButton.setText("Prev");
        tiltImagePanel.add(previousImageButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        NextImageButton = new JButton();
        NextImageButton.setText("Next");
        tiltImagePanel.add(NextImageButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("down sampling");
        tiltImagePanel.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tiltImagePanel.add(spinnerBining, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePanel = new JPanel();
        EnergyImagePanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(EnergyImagePanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(190, 104), null, 0, true));
        EnergyImagePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label5 = new JLabel();
        label5.setText("Energy Image");
        EnergyImagePanel.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePanel.add(EnergyImageSpinner, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImagePreviousButton = new JButton();
        EnergyImagePreviousButton.setText("Prev");
        EnergyImagePanel.add(EnergyImagePreviousButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        EnergyImageNextButton = new JButton();
        EnergyImageNextButton.setText("Next");
        EnergyImagePanel.add(EnergyImageNextButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        PCAPanel = new JPanel();
        PCAPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        menuPanel.add(PCAPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        computeEigenVectorsButton = new JButton();
        computeEigenVectorsButton.setText("compute Eigen Vectors");
        PCAPanel.add(computeEigenVectorsButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        allTiltCheckbox = new JCheckBox();
        allTiltCheckbox.setSelected(true);
        allTiltCheckbox.setText("for all tilts");
        PCAPanel.add(allTiltCheckbox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        PCAPanel.add(panel3, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        keepCheckBox = new JCheckBox();
        keepCheckBox.setText("keep");
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(keepCheckBox, gbc);
        final JLabel label6 = new JLabel();
        label6.setText("eigen Value:");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(label6, gbc);
        final JLabel label7 = new JLabel();
        label7.setText("Energy content");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(label7, gbc);
        final JLabel label8 = new JLabel();
        label8.setText("Cumulative Energy");
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(label8, gbc);
        PCA_EigenValLabel = new JLabel();
        PCA_EigenValLabel.setText("...");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(PCA_EigenValLabel, gbc);
        PCA_EnergyContentLabel = new JLabel();
        PCA_EnergyContentLabel.setText("...");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(PCA_EnergyContentLabel, gbc);
        PCA_CumulEnergyLabel = new JLabel();
        PCA_CumulEnergyLabel.setText("...");
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(PCA_CumulEnergyLabel, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(spacer1, gbc);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(spacer2, gbc);
        PCA_EigenImageSelectPanel = new JPanel();
        PCA_EigenImageSelectPanel.setLayout(new GridBagLayout());
        PCAPanel.add(PCA_EigenImageSelectPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        saveEigenVectorsImagesButton = new JButton();
        saveEigenVectorsImagesButton.setText("save Eigen vectors images");
        PCAPanel.add(saveEigenVectorsImagesButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }
}
