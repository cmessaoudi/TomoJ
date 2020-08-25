package fr.curie.tomoj.application;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import fr.curie.tomoj.features.AkazeListFeaturesOpenCV;
import fr.curie.tomoj.features.BriskListFeaturesOpenCV;
import fr.curie.tomoj.features.FeatureTrackingChaining;
import fr.curie.tomoj.features.KazeListFeaturesOpenCV;
import fr.curie.tomoj.features.ListFeature;
import fr.curie.tomoj.features.ListFeaturesOpenCV;
import fr.curie.tomoj.features.OrbListFeaturesOpenCV;
import fr.curie.tomoj.features.SIFTListFeaturesOpenCV;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.bytedeco.javacpp.opencv_features2d;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.tomoj.tomography.TomoJPoints;
import fr.curie.utils.Chrono;
import fr.curie.utils.HighPrecisionDisplaySpinner;
import fr.curie.utils.OutputStreamCapturer;
import fr.curie.tomoj.workflow.CommandWorkflow;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by cedric on 29/09/2016.
 */
public class FeatureTrackingGenerator implements Application {

    private final int SIFT = 0;
    private final int KAZE = 1;
    private final int BRISK = 2;
    private final int ORB = 3;
    private final int AKAZE = 4;

    protected TiltSeries ts;
    protected TomoJPoints tp;

    private ListFeaturesOpenCV fmodelDetect;
    private ListFeaturesOpenCV fmodelDescript;
    private FeatureTrackingChaining ftc;

    private int chainSize = 15;
    private int jumpMax = 5;
    private boolean validateWithHomography = true;
    private double homographyRansacPrecision;
    private boolean tryTofuseLandmarks = true;
    String resultString;
//    //ORB
//    int orbNbFeatures = 200;
//    double orbScaleFactor = 1.6;
//    int orbNbLevels = 8;
//    int orbFirstLevel = 0;
//    int orbEdgeThreshold = 64;
//    int orbWta_k = 2;
//    int orbScoreType = opencv_features2d.ORB.HARRIS_SCORE;
//    int orbPatchSize = 32;
//    int orbFastThreshold = 20;
//    //BRISK
//    int briskThreshold=60;
//    int briskOctave=6;
//    double briskPatternScale=1.0;
//    //SIFT
//    int 	siftNbfeatures = 0;
//    int 	siftNbOctaves = 3;
//    double 	siftContrastThreshold = 0.04;
//    double 	siftEdgeThresholdSIFT = 10;
//    double 	siftSigma = 1.6;
//    boolean siftCrossvalidation=true;
//    double siftValidationThreshold=0.75;
//    //KAZE
//    boolean kazeExtended=false;
//    boolean kazeUpright=false;
//    double kazeThreshold=0.001;
//    int kazeOctave=4;
//    int kazeLayers=4;
//    int kazeDiffusivity=opencv_features2d.KAZE.DIFF_PM_G2;

    private SIFTListFeaturesOpenCV siftGenerator;
    private OrbListFeaturesOpenCV orbGenerator;
    private BriskListFeaturesOpenCV briskGenerator;
    private KazeListFeaturesOpenCV kazeGenerator;
    private AkazeListFeaturesOpenCV akazeGenerator;

    boolean firstDisplay = true;
    private ImagePlus previewFeature = null;
    private PreviewFeatureThread workingPreviewThread;
    private int previewIndex;
    private int detectorIndex = SIFT;
    private int descriptorIndex = SIFT;

    private JPanel basePanel;
    private JComboBox comboBoxDetector;
    private JComboBox comboBoxDescriptor;
    private JCheckBox checkBoxPreValidateWithHomography;
    private JSpinner spinnerChainLength;
    private JSpinner spinnerMaxJump;
    private JSpinner spinnerHomographyDistance;
    private JCheckBox checkBoxFuseLandmarksChainsWhen;
    private JPanel orbParametersPanel;
    private JPanel briskParametersPanel;
    private JPanel siftParametersPanel;
    private JPanel kazeParametersPanel;
    private JSpinner spinnerORBNbFeatures;
    private JSpinner spinnerORBScaleFactor;
    private JSpinner spinnerORBNbLevels;
    private JSpinner spinnerORBFirstLevel;
    private JSpinner spinnerORBEdgeThreshold;
    private JSpinner spinnerORBwta_k;
    private JComboBox comboBoxORBDetectionMethod;
    private JSpinner spinnerORBPatchSize;
    private JSpinner spinnerORBFASTThreshold;
    private JSpinner spinnerBRISKThreshold;
    private JSpinner spinnerBRISKNbOctaves;
    private JSpinner spinnerBRISKPatternScale;
    private JCheckBox checkBoxSIFTCrossValidation;
    private JSpinner spinnerSIFTValidationThreshold;
    private JSpinner spinnerSIFTNbFeatures;
    private JSpinner spinnerSIFTNbOctaves;
    private JSpinner spinnerSIFTContrastThreshold;
    private JSpinner spinnerSIFTEdgeThreshold;
    private JSpinner spinnerSIFTSigma;
    private JCheckBox checkBoxKAZEExtended;
    private JCheckBox checkBoxKAZEUpright;
    private JComboBox comboBoxKAZEDiffusivity;
    private JSpinner spinnerKAZENbOctaves;
    private JSpinner spinnerKAZENbLayers;
    private JSpinner spinnerKAZEThreshold;
    private JPanel akazePanel;
    private JSpinner spinnerAKAZEThreshold;
    private JSpinner spinnerAKAZENbOctaves;
    private JSpinner spinnerAKAZENbLayers;
    private JComboBox comboBoxAKAZEDiffusivity;
    private JComboBox comboBoxAKAZEType;
    private JSpinner spinnerAKAZESize;
    private JSpinner spinnerAKAZEChannels;
    private JPanel optionsPanel;
    private JPanel displayOptionPanel;
    private JPanel allPanel;

    private Future initGenerators;
    private boolean isDisplayed = false;
    private ExecutorService exec;

    public FeatureTrackingGenerator(TiltSeries ts, TomoJPoints tp) {
        this.ts = ts;
        this.tp = tp;
        $$$setupUI$$$();
        homographyRansacPrecision = Math.max(2 * (ts.getWidth() / 512), 2);
        exec = Executors.newFixedThreadPool(Prefs.getThreads());
        initGenerators = exec.submit(new Thread() {
            public void run() {
                siftGenerator = new SIFTListFeaturesOpenCV();
                orbGenerator = new OrbListFeaturesOpenCV();
                briskGenerator = new BriskListFeaturesOpenCV();
                kazeGenerator = new KazeListFeaturesOpenCV();
                akazeGenerator = new AkazeListFeaturesOpenCV();

                fmodelDetect = siftGenerator;
                fmodelDescript = siftGenerator;
                ftc = new FeatureTrackingChaining();
            }
        });

    }

    private void removeAllParametersPanels() {
        displayOptionPanel.removeAll();
//        Component[] components = basePanel.getComponents();
//        for (Component c : components) {
//            if (c == siftParametersPanel || c == kazeParametersPanel || c == briskParametersPanel || c == orbParametersPanel || c == akazePanel) {
//                basePanel.remove(c);
//            }
//        }
    }

    private void updatePreferredSize() {
        Dimension d = basePanel.getPreferredSize();
        d.height = optionsPanel.getHeight();
        d.height += siftParametersPanel.getHeight();
        d.height += kazeParametersPanel.getHeight();
        d.height += briskParametersPanel.getHeight();
        d.height += orbParametersPanel.getHeight();
        d.height += akazePanel.getHeight();
//        switch (comboBoxDetector.getSelectedIndex()){
//            case SIFT:
//                d.height+=siftParametersPanel.getHeight();
//                break;
//            case KAZE:
//                d.height+=kazeParametersPanel.getHeight();
//                break;
//            case BRISK:
//                d.height+=briskParametersPanel.getHeight();
//                break;
//            case ORB:
//                d.height+=orbParametersPanel.getHeight();
//                break;
//            case AKAZE:
//            default:
//                d.height+=akazePanel.getHeight();
//        }
//        if(comboBoxDescriptor.getSelectedIndex()!=comboBoxDetector.getSelectedIndex()){
//            switch (comboBoxDescriptor.getSelectedIndex()){
//                case SIFT:
//                    d.height+=siftParametersPanel.getHeight();
//                    break;
//                case KAZE:
//                    d.height+=kazeParametersPanel.getHeight();
//                    break;
//                case BRISK:
//                    d.height+=briskParametersPanel.getHeight();
//                    break;
//                case ORB:
//                    d.height+=orbParametersPanel.getHeight();
//                    break;
//                case AKAZE:
//                default:
//                    d.height+=akazePanel.getHeight();
//            }
//        }
        d.height += 100;
        basePanel.setPreferredSize(d);
        System.out.println(d);
    }

    // Changed scope from private to public by Antoine (TEST)
    public void setParametersPanels(int detector, int descriptor) {
        detectorIndex = detector;
        descriptorIndex = descriptor;
        if (descriptor == AKAZE && detector != AKAZE) {
            comboBoxDetector.setSelectedIndex(AKAZE);
            fmodelDetect = akazeGenerator;
            fmodelDescript = akazeGenerator;
            return;
        }
        if (descriptor == KAZE && detector != KAZE) {
            comboBoxDetector.setSelectedIndex(KAZE);
            fmodelDetect = kazeGenerator;
            fmodelDescript = kazeGenerator;
            return;
        }
        switch (detector) {
            case SIFT:
                displayOptionPanel.add(siftParametersPanel);
                fmodelDetect = siftGenerator;
                break;
            case KAZE:
                displayOptionPanel.add(kazeParametersPanel);
                fmodelDetect = kazeGenerator;
                break;
            case BRISK:
                displayOptionPanel.add(briskParametersPanel);
                fmodelDetect = briskGenerator;
                break;
            case ORB:
                displayOptionPanel.add(orbParametersPanel);
                fmodelDetect = orbGenerator;
                break;
            case AKAZE:
            default:
                displayOptionPanel.add(akazePanel);
                fmodelDetect = akazeGenerator;
        }
//        if (descriptor != detector) {
        switch (descriptor) {
            case SIFT:
                displayOptionPanel.add(siftParametersPanel);
                fmodelDescript = siftGenerator;
                break;
            case KAZE:
                displayOptionPanel.add(kazeParametersPanel);
                fmodelDescript = kazeGenerator;
                break;
            case BRISK:
                displayOptionPanel.add(briskParametersPanel);
                fmodelDescript = briskGenerator;
                break;
            case ORB:
                displayOptionPanel.add(orbParametersPanel);
                fmodelDescript = orbGenerator;
                break;
            case AKAZE:
            default:
                displayOptionPanel.add(akazePanel);
                fmodelDescript = akazeGenerator;
        }
//        }
        displayOptionPanel.revalidate();
    }

    private void addListeners() {
        comboBoxDetector.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int descriptor = comboBoxDescriptor.getSelectedIndex();
                int detector = comboBoxDetector.getSelectedIndex();
                removeAllParametersPanels();
                setParametersPanels(detector, descriptor);
                /*switch (comboBoxDetector.getSelectedIndex()) {
                    case SIFT:
                        if (descriptor == KAZE || descriptor == AKAZE) {
                            comboBoxDetector.setSelectedIndex(descriptor);
                            return;
                        }
                        fmodelDetect = siftGenerator;
                        siftParametersPanel.setVisible(true);
                        if (descriptor != KAZE) kazeParametersPanel.setVisible(false);
                        if (descriptor != BRISK) briskParametersPanel.setVisible(false);
                        if (descriptor != ORB) orbParametersPanel.setVisible(false);
                        if (descriptor != AKAZE) akazePanel.setVisible(false);
                        break;
                    case KAZE:
                        fmodelDetect = kazeGenerator;
                        if (descriptor != SIFT) siftParametersPanel.setVisible(false);
                        kazeParametersPanel.setVisible(true);
                        if (descriptor != BRISK) briskParametersPanel.setVisible(false);
                        if (descriptor != ORB) orbParametersPanel.setVisible(false);
                        if (descriptor != AKAZE) akazePanel.setVisible(false);
                        break;
                    case BRISK:
                        if (descriptor == KAZE || descriptor == AKAZE) {
                            comboBoxDetector.setSelectedIndex(descriptor);
                            return;
                        }
                        fmodelDetect = briskGenerator;
                        if (descriptor != SIFT) siftParametersPanel.setVisible(false);
                        if (descriptor != KAZE) kazeParametersPanel.setVisible(false);
                        briskParametersPanel.setVisible(true);
                        if (descriptor != ORB) orbParametersPanel.setVisible(false);
                        if (descriptor != AKAZE) akazePanel.setVisible(false);
                        break;
                    case ORB:
                        if (descriptor == KAZE || descriptor == AKAZE) {
                            comboBoxDetector.setSelectedIndex(descriptor);
                            return;
                        }
                        fmodelDetect = orbGenerator;
                        if (descriptor != SIFT) siftParametersPanel.setVisible(false);
                        if (descriptor != KAZE) kazeParametersPanel.setVisible(false);
                        if (descriptor != BRISK) briskParametersPanel.setVisible(false);
                        orbParametersPanel.setVisible(true);
                        if (descriptor != AKAZE) akazePanel.setVisible(false);
                        break;
                    case AKAZE:
                        fmodelDetect = akazeGenerator;
                        if (descriptor != SIFT) siftParametersPanel.setVisible(false);
                        if (descriptor != KAZE) kazeParametersPanel.setVisible(false);
                        if (descriptor != BRISK) briskParametersPanel.setVisible(false);
                        if (descriptor != ORB) orbParametersPanel.setVisible(false);
                        akazePanel.setVisible(true);
                        break;

                }    */
                updatePreview();
                //updatePreferredSize();
            }
        });

        comboBoxDescriptor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int detector = comboBoxDetector.getSelectedIndex();
                int descriptor = comboBoxDescriptor.getSelectedIndex();
                removeAllParametersPanels();
                setParametersPanels(detector, descriptor);
                /*switch (comboBoxDescriptor.getSelectedIndex()) {
                    case SIFT:
                        siftParametersPanel.setVisible(true);
                        if (detector != KAZE) kazeParametersPanel.setVisible(false);
                        if (detector != BRISK) briskParametersPanel.setVisible(false);
                        if (detector != ORB) orbParametersPanel.setVisible(false);
                        if (detector != AKAZE) akazePanel.setVisible(false);
                        break;
                    case KAZE:
                        if (detector != SIFT) siftParametersPanel.setVisible(false);
                        kazeParametersPanel.setVisible(true);
                        if (detector != BRISK) briskParametersPanel.setVisible(false);
                        if (detector != ORB) orbParametersPanel.setVisible(false);
                        if (detector != AKAZE) akazePanel.setVisible(false);
                        if (comboBoxDetector.getSelectedIndex() != KAZE) comboBoxDetector.setSelectedIndex(KAZE);
                        break;
                    case BRISK:
                        if (detector != SIFT) siftParametersPanel.setVisible(false);
                        if (detector != KAZE) kazeParametersPanel.setVisible(false);
                        briskParametersPanel.setVisible(true);
                        if (detector != ORB) orbParametersPanel.setVisible(false);
                        if (detector != AKAZE) akazePanel.setVisible(false);
                        break;
                    case ORB:
                        if (detector != SIFT) siftParametersPanel.setVisible(false);
                        if (detector != KAZE) kazeParametersPanel.setVisible(false);
                        if (detector != BRISK) briskParametersPanel.setVisible(false);
                        orbParametersPanel.setVisible(true);
                        if (detector != AKAZE) akazePanel.setVisible(false);
                        break;
                    case AKAZE:
                        if (detector != SIFT) siftParametersPanel.setVisible(false);
                        if (detector != KAZE) kazeParametersPanel.setVisible(false);
                        if (detector != BRISK) briskParametersPanel.setVisible(false);
                        if (detector != ORB) orbParametersPanel.setVisible(false);
                        akazePanel.setVisible(true);
                        if (comboBoxDetector.getSelectedIndex() != AKAZE) comboBoxDetector.setSelectedIndex(AKAZE);
                        break;
                }  */
                //updatePreferredSize();

            }
        });


        comboBoxDetector.setSelectedIndex(SIFT);
        comboBoxDescriptor.setSelectedIndex(SIFT);

        /*basePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                super.componentShown(e);
                isDisplayed = true;
                updatePreview();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                super.componentHidden(e);
                isDisplayed = false;
                if (previewFeature != null) {
                    previewFeature.close();
                    previewFeature = null;
                }
            }
        });   */

        spinnerChainLength.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                chainSize = ((SpinnerNumberModel) spinnerChainLength.getModel()).getNumber().intValue();
            }
        });
        spinnerMaxJump.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                jumpMax = ((SpinnerNumberModel) spinnerMaxJump.getModel()).getNumber().intValue();
            }
        });
        spinnerHomographyDistance.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                homographyRansacPrecision = ((SpinnerNumberModel) spinnerHomographyDistance.getModel()).getNumber().intValue();
            }
        });
        checkBoxPreValidateWithHomography.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                validateWithHomography = checkBoxPreValidateWithHomography.isSelected();
            }
        });
        checkBoxFuseLandmarksChainsWhen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tryTofuseLandmarks = checkBoxFuseLandmarksChainsWhen.isSelected();
            }
        });


        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex++;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex--;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex -= 10;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex += 10;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex = (previewIndex <= ts.getZeroIndex()) ? 0 : ts.getZeroIndex();
                //previewIndex -= ts.getImageStackSize()/2;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        basePanel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                previewIndex = (previewIndex >= ts.getZeroIndex()) ? ts.getImageStackSize() - 1 : ts.getZeroIndex();
                //previewIndex += ts.getImageStackSize()/2;
                updatePreview();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


        addSIFTListeners();
        addKAZEListeners();
        addBRISKListeners();
        addORBListeners();
        addAKAZEListeners();
    }

    protected void addSIFTListeners() {
        spinnerSIFTNbFeatures.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSIFTParameters();
                updatePreview();
            }
        });
        spinnerSIFTNbOctaves.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSIFTParameters();
                updatePreview();
            }
        });
        spinnerSIFTContrastThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSIFTParameters();
                updatePreview();
            }
        });
        spinnerSIFTEdgeThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSIFTParameters();
                updatePreview();
            }
        });
        spinnerSIFTSigma.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSIFTParameters();
                updatePreview();
            }
        });
        checkBoxSIFTCrossValidation.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                spinnerSIFTValidationThreshold.setEnabled(!checkBoxSIFTCrossValidation.isSelected());
                updateSIFTParameters();
            }
        });
        spinnerSIFTValidationThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateSIFTParameters();
            }
        });

    }

    protected void addKAZEListeners() {
        spinnerKAZEThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateKAZEParameters();
                updatePreview();
            }
        });
        spinnerKAZENbLayers.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateKAZEParameters();
                updatePreview();
            }
        });
        spinnerKAZENbOctaves.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateKAZEParameters();
                updatePreview();
            }
        });
        checkBoxKAZEExtended.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateKAZEParameters();
            }
        });
        checkBoxKAZEUpright.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateKAZEParameters();
            }
        });
        comboBoxKAZEDiffusivity.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateKAZEParameters();
                updatePreview();
            }
        });

    }

    protected void addBRISKListeners() {
        spinnerBRISKThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateBRISKParameters();
                updatePreview();
            }
        });
        spinnerBRISKNbOctaves.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateBRISKParameters();
                updatePreview();
            }
        });
        spinnerBRISKPatternScale.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateBRISKParameters();
            }
        });

    }

    protected void addORBListeners() {
        spinnerORBNbFeatures.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
                updatePreview();
            }
        });
        spinnerORBScaleFactor.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
                updatePreview();
            }
        });
        spinnerORBNbLevels.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
                updatePreview();
            }
        });
        spinnerORBFirstLevel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
                updatePreview();
            }
        });
        spinnerORBEdgeThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
                updatePreview();
            }
        });
        spinnerORBwta_k.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
            }
        });
        comboBoxORBDetectionMethod.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //spinnerORBFASTThreshold.setEnabled(comboBoxORBDetectionMethod.getSelectedIndex() == 1);
                updateORBParameters();
                updatePreview();
            }
        });
        spinnerORBPatchSize.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
                updatePreview();
            }
        });
        spinnerORBFASTThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateORBParameters();
                updatePreview();
            }
        });

    }

    protected void addAKAZEListeners() {
        spinnerAKAZEThreshold.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateAKAZEParameters();
                updatePreview();
            }
        });
        spinnerAKAZENbLayers.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateAKAZEParameters();
                updatePreview();
            }
        });
        spinnerAKAZENbOctaves.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateAKAZEParameters();
                updatePreview();
            }
        });
        comboBoxAKAZEDiffusivity.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAKAZEParameters();
                updatePreview();
            }
        });
        spinnerAKAZESize.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateAKAZEParameters();
                //updatePreview();
            }
        });
        spinnerAKAZEChannels.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateAKAZEParameters();
                //updatePreview();
            }
        });
        comboBoxAKAZEType.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAKAZEParameters();
                updatePreview();
            }
        });

    }

    private void initValues() {


        ((SpinnerNumberModel) spinnerChainLength.getModel()).setMinimum(3);
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setMaximum(ts.getImageStackSize());
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setValue(15);
        ((SpinnerNumberModel) spinnerChainLength.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerMaxJump.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerMaxJump.getModel()).setMaximum(ts.getImageStackSize() / 2);
        ((SpinnerNumberModel) spinnerMaxJump.getModel()).setValue(5);
        ((SpinnerNumberModel) spinnerMaxJump.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerHomographyDistance.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerHomographyDistance.getModel()).setMaximum(ts.getWidth() / 2);
        ((SpinnerNumberModel) spinnerHomographyDistance.getModel()).setValue(homographyRansacPrecision);
        ((SpinnerNumberModel) spinnerHomographyDistance.getModel()).setStepSize(1);

        initSIFTValues();
        initKAZEValues();
        initBRISKValues();
        initORBValues();
        initAKAZEValues();
    }

    private void initSIFTValues() {
        ((SpinnerNumberModel) spinnerSIFTNbFeatures.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerSIFTNbFeatures.getModel()).setValue(0);
        ((SpinnerNumberModel) spinnerSIFTNbFeatures.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerSIFTNbOctaves.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerSIFTNbOctaves.getModel()).setValue(3);
        ((SpinnerNumberModel) spinnerSIFTNbOctaves.getModel()).setStepSize(1);
    }

    private void initKAZEValues() {
        ((SpinnerNumberModel) spinnerKAZENbOctaves.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerKAZENbOctaves.getModel()).setValue(4);
        ((SpinnerNumberModel) spinnerKAZENbOctaves.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerKAZENbLayers.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerKAZENbLayers.getModel()).setValue(4);
        ((SpinnerNumberModel) spinnerKAZENbLayers.getModel()).setStepSize(1);

        comboBoxKAZEDiffusivity.setSelectedIndex(1);

    }

    private void initBRISKValues() {
        ((SpinnerNumberModel) spinnerBRISKNbOctaves.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerBRISKNbOctaves.getModel()).setValue(4);
        ((SpinnerNumberModel) spinnerBRISKNbOctaves.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerBRISKThreshold.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerBRISKThreshold.getModel()).setMaximum(256);
        ((SpinnerNumberModel) spinnerBRISKThreshold.getModel()).setValue(60);
        ((SpinnerNumberModel) spinnerBRISKThreshold.getModel()).setStepSize(1);

    }

    private void initORBValues() {
        ((SpinnerNumberModel) spinnerORBNbFeatures.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerORBNbFeatures.getModel()).setValue(200);
        ((SpinnerNumberModel) spinnerORBNbFeatures.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerORBNbLevels.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerORBNbLevels.getModel()).setValue(8);
        ((SpinnerNumberModel) spinnerORBNbLevels.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerORBFirstLevel.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerORBFirstLevel.getModel()).setValue(0);
        ((SpinnerNumberModel) spinnerORBFirstLevel.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerORBEdgeThreshold.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerORBEdgeThreshold.getModel()).setMaximum(256);
        ((SpinnerNumberModel) spinnerORBEdgeThreshold.getModel()).setValue(64);
        ((SpinnerNumberModel) spinnerORBEdgeThreshold.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerORBwta_k.getModel()).setMinimum(2);
        ((SpinnerNumberModel) spinnerORBwta_k.getModel()).setMaximum(4);
        ((SpinnerNumberModel) spinnerORBwta_k.getModel()).setValue(2);
        ((SpinnerNumberModel) spinnerORBwta_k.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerORBPatchSize.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerORBPatchSize.getModel()).setMaximum(ts.getWidth() / 2);
        ((SpinnerNumberModel) spinnerORBPatchSize.getModel()).setValue(32);
        ((SpinnerNumberModel) spinnerORBPatchSize.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerORBFASTThreshold.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerORBFASTThreshold.getModel()).setMaximum(256);
        ((SpinnerNumberModel) spinnerORBFASTThreshold.getModel()).setValue(20);
        ((SpinnerNumberModel) spinnerORBFASTThreshold.getModel()).setStepSize(1);
    }

    private void initAKAZEValues() {
        ((SpinnerNumberModel) spinnerAKAZENbOctaves.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerAKAZENbOctaves.getModel()).setValue(4);
        ((SpinnerNumberModel) spinnerAKAZENbOctaves.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerAKAZENbLayers.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerAKAZENbLayers.getModel()).setValue(4);
        ((SpinnerNumberModel) spinnerAKAZENbLayers.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerAKAZESize.getModel()).setMinimum(0);
        ((SpinnerNumberModel) spinnerAKAZESize.getModel()).setValue(0);
        ((SpinnerNumberModel) spinnerAKAZESize.getModel()).setStepSize(1);

        ((SpinnerNumberModel) spinnerAKAZEChannels.getModel()).setMinimum(1);
        ((SpinnerNumberModel) spinnerAKAZEChannels.getModel()).setMaximum(3);
        ((SpinnerNumberModel) spinnerAKAZEChannels.getModel()).setValue(3);
        ((SpinnerNumberModel) spinnerAKAZEChannels.getModel()).setStepSize(1);

        comboBoxAKAZEDiffusivity.setSelectedIndex(1);
        comboBoxAKAZEType.setSelectedIndex(2);

    }

    /**
     * TEST by Antoine.
     *
     * @return SIFTListFeaturesOpenCV to get access to its parameters through
     * the FeatureTrackingGenerator.
     */
    public SIFTListFeaturesOpenCV getSiftGenerator() {
        try {
            initGenerators.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return siftGenerator;
    }

    /**
     * TEST by Antoine.
     *
     * @return AKAZEListFeaturesOpenCV to get access to its parameters through
     * the FeatureTrackingGenerator.
     */
    public AkazeListFeaturesOpenCV getAkazeGenerator() {
        try {
            initGenerators.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return akazeGenerator;
    }

    /**
     * TEST
     *
     * @param index
     * @return
     */
    public ListFeature getListFeature(int index) {
        return ftc.getListFeature(index);
    }

    protected void updateSIFTParameters() {
        //SIFT
        int siftNbfeatures = ((SpinnerNumberModel) spinnerSIFTNbFeatures.getModel()).getNumber().intValue();
        int siftNbOctaves = ((SpinnerNumberModel) spinnerSIFTNbOctaves.getModel()).getNumber().intValue();
        double siftContrastThreshold = ((SpinnerNumberModel) spinnerSIFTContrastThreshold.getModel()).getNumber().doubleValue();
        double siftEdgeThreshold = ((SpinnerNumberModel) spinnerSIFTEdgeThreshold.getModel()).getNumber().doubleValue();
        double siftSigma = ((SpinnerNumberModel) spinnerSIFTSigma.getModel()).getNumber().doubleValue();
        boolean siftCrossvalidation = checkBoxSIFTCrossValidation.isSelected();
        double siftValidationThreshold = ((SpinnerNumberModel) spinnerSIFTValidationThreshold.getModel()).getNumber().doubleValue();
        if (siftGenerator == null) {
            try {
                initGenerators.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        siftGenerator.setParameters(siftNbfeatures, siftNbOctaves, siftContrastThreshold, siftEdgeThreshold, siftSigma, siftCrossvalidation, siftValidationThreshold, homographyRansacPrecision);
        //siftGenerator=new SIFTListFeaturesOpenCV(siftNbfeatures,siftNbOctaves,siftContrastThreshold,siftEdgeThreshold,siftSigma,siftCrossvalidation,siftValidationThreshold,homographyRansacPrecision);
    }

    protected void updateKAZEParameters() {
        //KAZE
        boolean kazeExtended = checkBoxKAZEExtended.isSelected();
        boolean kazeUpright = checkBoxKAZEUpright.isSelected();
        double kazeThreshold = ((SpinnerNumberModel) spinnerKAZEThreshold.getModel()).getNumber().doubleValue();
        int kazeOctave = ((SpinnerNumberModel) spinnerKAZENbOctaves.getModel()).getNumber().intValue();
        ;
        int kazeLayers = ((SpinnerNumberModel) spinnerKAZENbLayers.getModel()).getNumber().intValue();

        int kazeDiffusivity = opencv_features2d.KAZE.DIFF_PM_G2;
        switch (comboBoxKAZEDiffusivity.getSelectedIndex()) {
            case 0:
                kazeDiffusivity = opencv_features2d.KAZE.DIFF_PM_G1;
                break;
            case 1:
                kazeDiffusivity = opencv_features2d.KAZE.DIFF_PM_G2;
                break;
            case 2:
                kazeDiffusivity = opencv_features2d.KAZE.DIFF_WEICKERT;
                break;
            case 3:
                kazeDiffusivity = opencv_features2d.KAZE.DIFF_CHARBONNIER;
                break;
        }
        if (kazeGenerator == null) {
            try {
                initGenerators.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        kazeGenerator.setParameters(kazeExtended, kazeUpright, kazeThreshold, kazeOctave, kazeLayers, kazeDiffusivity, homographyRansacPrecision);

        //kazeGenerator=new KazeListFeaturesOpenCV(kazeExtended,kazeUpright,kazeThreshold,kazeOctave,kazeLayers,kazeDiffusivity,homographyRansacPrecision);
    }

    protected void updateBRISKParameters() {
        //BRISK
        int briskThreshold = ((SpinnerNumberModel) spinnerBRISKThreshold.getModel()).getNumber().intValue();
        int briskOctave = ((SpinnerNumberModel) spinnerBRISKNbOctaves.getModel()).getNumber().intValue();
        double briskPatternScale = ((SpinnerNumberModel) spinnerBRISKPatternScale.getModel()).getNumber().doubleValue();
        if (briskGenerator == null) {
            try {
                initGenerators.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        briskGenerator.setParameters(briskThreshold, briskOctave, briskPatternScale, homographyRansacPrecision);
        //briskGenerator=new BriskListFeaturesOpenCV(briskThreshold,briskOctave,(float)briskPatternScale,homographyRansacPrecision);
    }

    protected void updateORBParameters() {
        //ORB
        int orbNbFeatures = ((SpinnerNumberModel) spinnerORBNbFeatures.getModel()).getNumber().intValue();
        double orbScaleFactor = ((SpinnerNumberModel) spinnerORBScaleFactor.getModel()).getNumber().doubleValue();
        int orbNbLevels = ((SpinnerNumberModel) spinnerORBNbLevels.getModel()).getNumber().intValue();
        int orbFirstLevel = ((SpinnerNumberModel) spinnerORBFirstLevel.getModel()).getNumber().intValue();
        int orbEdgeThreshold = ((SpinnerNumberModel) spinnerORBEdgeThreshold.getModel()).getNumber().intValue();
        int orbWta_k = ((SpinnerNumberModel) spinnerORBwta_k.getModel()).getNumber().intValue();
        int orbScoreType = (comboBoxORBDetectionMethod.getSelectedIndex() == 0) ? opencv_features2d.ORB.HARRIS_SCORE : opencv_features2d.ORB.FAST_SCORE;
        int orbPatchSize = ((SpinnerNumberModel) spinnerORBPatchSize.getModel()).getNumber().intValue();
        int orbFastThreshold = ((SpinnerNumberModel) spinnerORBFASTThreshold.getModel()).getNumber().intValue();

        //orbGenerator=new OrbListFeaturesOpenCV(orbNbFeatures,(float)orbScaleFactor,orbNbLevels,orbFirstLevel,orbEdgeThreshold,orbWta_k,orbScoreType,orbPatchSize,orbFastThreshold,homographyRansacPrecision);
        if (orbGenerator == null) {
            try {
                initGenerators.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        orbGenerator.setParameters(orbNbFeatures, orbScaleFactor, orbNbLevels, orbFirstLevel, orbEdgeThreshold, orbWta_k, orbScoreType, orbPatchSize, orbFastThreshold, homographyRansacPrecision);
    }

    protected void updateAKAZEParameters() {
        //AKAZE
        int akaze_type = opencv_features2d.AKAZE.DESCRIPTOR_MLDB;
        switch (comboBoxAKAZEType.getSelectedIndex()) {
            case 0:
                akaze_type = opencv_features2d.AKAZE.DESCRIPTOR_KAZE;
                break;
            case 1:
                akaze_type = opencv_features2d.AKAZE.DESCRIPTOR_KAZE_UPRIGHT;
                break;
            case 2:
                akaze_type = opencv_features2d.AKAZE.DESCRIPTOR_MLDB;
                break;
            case 3:
                akaze_type = opencv_features2d.AKAZE.DESCRIPTOR_MLDB_UPRIGHT;
                break;
        }
        int akaze_size = ((SpinnerNumberModel) spinnerAKAZESize.getModel()).getNumber().intValue();
        int akaze_channels = ((SpinnerNumberModel) spinnerAKAZEChannels.getModel()).getNumber().intValue();
        double akazeThreshold = ((SpinnerNumberModel) spinnerAKAZEThreshold.getModel()).getNumber().doubleValue();
        int akazeOctave = ((SpinnerNumberModel) spinnerAKAZENbOctaves.getModel()).getNumber().intValue();
        int akazeLayers = ((SpinnerNumberModel) spinnerAKAZENbLayers.getModel()).getNumber().intValue();

        int akazeDiffusivity = opencv_features2d.KAZE.DIFF_PM_G2;
        switch (comboBoxAKAZEDiffusivity.getSelectedIndex()) {
            case 0:
                akazeDiffusivity = opencv_features2d.KAZE.DIFF_PM_G1;
                break;
            case 1:
                akazeDiffusivity = opencv_features2d.KAZE.DIFF_PM_G2;
                break;
            case 2:
                akazeDiffusivity = opencv_features2d.KAZE.DIFF_WEICKERT;
                break;
            case 3:
                akazeDiffusivity = opencv_features2d.KAZE.DIFF_CHARBONNIER;
                break;
        }
        if (akazeGenerator == null) {
            try {
                initGenerators.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        akazeGenerator.setParameters(akaze_type, akaze_size, akaze_channels, akazeThreshold, akazeOctave, akazeLayers, akazeDiffusivity, homographyRansacPrecision);

        //kazeGenerator=new KazeListFeaturesOpenCV(kazeExtended,kazeUpright,kazeThreshold,kazeOctave,kazeLayers,kazeDiffusivity,homographyRansacPrecision);
    }

    public boolean run() {
        if (previewFeature != null) {
            previewFeature.close();
            previewFeature = null;
        }
//        System.out.println(getParametersValuesAsString());
        switch (detectorIndex) {
            case SIFT:
            default:
                fmodelDetect = siftGenerator;
                break;
            case KAZE:
                fmodelDetect = kazeGenerator;
                break;
            case BRISK:
                fmodelDetect = briskGenerator;
                break;
            case ORB:
                fmodelDetect = orbGenerator;
                break;
            case AKAZE:
                fmodelDetect = akazeGenerator;
                break;

        }
        switch (descriptorIndex) {
            case SIFT:
            default:
                fmodelDescript = siftGenerator;
                break;
            case KAZE:
                fmodelDescript = kazeGenerator;
                break;
            case BRISK:
                fmodelDescript = briskGenerator;
                break;
            case ORB:
                fmodelDescript = orbGenerator;
                break;
            case AKAZE:
                fmodelDescript = akazeGenerator;
                break;

        }
        System.out.println(getParametersValuesAsString()); // TEST Antoine
        System.out.flush();
        OutputStreamCapturer capture = new OutputStreamCapturer();
        Chrono time = new Chrono();
        time.start();
        //System.out.println("detector: " + fmodelDetect.getParametersAsString() + "\ndescriptor: " + fmodelDescript.getParametersAsString());
        ArrayList<Point2D[]> chains = ftc.computeFeatureChaining(ts, fmodelDetect, fmodelDescript, chainSize, jumpMax, validateWithHomography);
        System.out.println("TP number of points: " + chains.size());
        double avglength = 0;
        int minLength = ts.getImageStackSize();
        int maxLength = 0;
        for (int i = 1; i < chains.size(); i++) {
            int length = TomoJPoints.getLandmarkLength(chains.get(i));
            avglength += length;
            if (minLength > length) {
                minLength = length;
            }
            if (maxLength < length) maxLength = length;
        }
        avglength /= chains.size();
        System.out.println("Landmarks created are seen on " + avglength + " images" + " (min: " + minLength + ", max: " + maxLength + ")");

        System.out.println("TP number of points: " + chains.size());
        tp.addSetOfPoints(chains, true);
        if (tryTofuseLandmarks) {
            CommandWorkflow.saveLandmarks(IJ.getDirectory("current"), "landmarks_Before_Fusion.txt", ts);
            chains = tp.tryToFuseLandmarks(chains, 2);
            tp.removeAllSetsOfPoints();
            tp.addSetOfPoints(chains, true);
            System.out.println("TP number of points after fusion: " + tp.getNumberOfPoints());
            avglength = 0;
            minLength = ts.getImageStackSize();
            maxLength = 0;
            for (int i = 1; i < chains.size(); i++) {
                int length = TomoJPoints.getLandmarkLength(chains.get(i));
                avglength += length;
                if (minLength > length) {
                    minLength = length;
                }
                if (maxLength < length) maxLength = length;
            }
            avglength /= chains.size();
            System.out.println("Landmarks created are seen on average on " + avglength + " images" + " (min: " + minLength + ", max: " + maxLength + ")");

            CommandWorkflow.saveLandmarks(IJ.getDirectory("current"), "landmarks_After_Fusion.txt", ts);
        }

        time.stop();
        System.out.println("\ntotal time to compute : " + time.delayString());


        /*System.out.println("refine chains");
        ChainsGenerator refiner = new ChainsGenerator(ts);
        //@TODO do things correctly!!!!!!!!   Parameters!!!!
        for (Point2D[] chain : chains) {
            Point2D[] chainCentered = new Point2D[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (chain[i] != null) {
                    chainCentered[i] = new Point2D.Double(chain[i].getX() - ts.getCenterX(), chain[i].getY() - ts.getCenterY());
                } else {
                    chainCentered[i] = null;
                }
            }
            refiner.refineChain(chainCentered, 21, 2, 0.9, false, false);
            for (int i = 0; i < chain.length; i++) {
                if (chainCentered[i] != null) {
                    Point2D tmp = new Point2D.Double(chainCentered[i].getX() + ts.getCenterX(), chainCentered[i].getY() + ts.getCenterY());
                    if (tmp.distanceSq(chain[i]) > 1) {
                        System.out.println("refine position " + chain[i] + "-->" + tmp);
                        chain[i] = tmp;
                    }
                } else {
                    chain[i] = null;
                }
            }
        }     */


        avglength = 0;
        minLength = ts.getImageStackSize();
        maxLength = 0;

        for (int i = 1; i < chains.size(); i++) {
            int length = TomoJPoints.getLandmarkLength(chains.get(i));
            avglength += length;
            if (minLength > length) {
                minLength = length;
            }
            if (maxLength < length) maxLength = length;
        }
        avglength /= chains.size();
        System.out.println("landmarks created are seen on " + avglength + " images" + " (" + minLength + ", " + maxLength + ")");
        resultString = capture.stop();
        resultString += "\ntotal time to compute : " + time.delayString();
        tp.showAll(true);
        return true;
    }

    /**
     * @param parameters array of String
     *                   (example : ["maxJump","5","fuselandmarks"])
     */
    public void setParameters(Object... parameters) {
        for (int index = 0; index < parameters.length; index++) {
            if (((String) parameters[index]).toLowerCase().equals("chainsize")) {
                if (parameters[index + 1] instanceof String)
                    chainSize = Integer.parseInt((String) parameters[index + 1]);
                else chainSize = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("jumpmax")) {
                if (parameters[index + 1] instanceof String)
                    jumpMax = Integer.parseInt((String) parameters[index + 1]);
                else jumpMax = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("fuselandmarks")) {
                tryTofuseLandmarks = true;
            } else if (((String) parameters[index]).toLowerCase().equals("ransacprecision")) {
                if (parameters[index + 1] instanceof String)
                    homographyRansacPrecision = Integer.parseInt((String) parameters[index + 1]);
                else homographyRansacPrecision = (Integer) parameters[index + 1];
                index += 1;
            } else if (((String) parameters[index]).toLowerCase().equals("homography")) {
                validateWithHomography = true;
            }
        }
    }

    public String help() {
        return "Feature Tracking Generator\n"
                + "chainsize : Minimum length of landmarks chains to be kept. Default: 15.\n"
                + "jumpmax : Maximum slice jump allowed for landmarks chains to be tracked. Recommended values 0-5.\n"
                + "fuselandmark : Equal true if used. Try to fuse landmarks chains.\n"
                + "ransacprecision : Threshold, in pixels, used by RANSAC algorithm. Values below are kept. Default: 2.\n"
                + "homography : Equal true if used. Validate matched features between images using homography.\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n";
    }

    public String name() {
        return "Landmarks generation using feature tracking algorithm";
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
            siftParametersPanel.setVisible(true);
            kazeParametersPanel.setVisible(true);
            briskParametersPanel.setVisible(true);
            orbParametersPanel.setVisible(true);
            akazePanel.setVisible(true);
            allPanel.revalidate();
            //updatePreferredSize();
            allPanel.setVisible(false);

            addListeners();
            firstDisplay = false;
        }
        updatePreview();
        return this.basePanel;
    }

    public void interrupt() {
        ftc.interrupt();
    }

    public double getCompletion() {
        return ftc.getCompletion() * ts.getImageStackSize();
    }

    protected void updatePreview() {
        if (!isDisplayed) return;
        if (previewFeature == null) previewIndex = ts.getSlice() - 1;
        if (previewIndex < 0) previewIndex = 0;
        if (previewIndex >= ts.getImageStackSize()) previewIndex = ts.getImageStackSize() - 1;
        if (workingPreviewThread != null) workingPreviewThread.interrupt();
        workingPreviewThread = new PreviewFeatureThread(previewIndex);

        workingPreviewThread.start();

        //previewCritical.show();
        //previewCritical.getWindow().toFront();

    }

    public String getParametersValuesAsString() {
        String params = "Feature tracking landmarks generation:\n" +
                "detection method: " + fmodelDetect.getParametersAsString();


        params += "\ndescription method: " + fmodelDescript.getParametersAsString();
        params += "\njump allowed: " + jumpMax + "\tminimum chain length: " + chainSize;

        return params;
    }

    public void setDisplayPreview(boolean display) {
        isDisplayed = display;
        if (!isDisplayed && previewFeature != null && previewFeature.isVisible()) {
            previewFeature.close();
            previewFeature = null;
        }
        updatePreview();
    }

    private void createUIComponents() {
        basePanel = new JPanel();
        basePanel.setLayout(new BoxLayout(basePanel, BoxLayout.Y_AXIS));
        spinnerORBScaleFactor = new JSpinner(new SpinnerNumberModel(1.6, 0.1, 10, 0.1));
        spinnerBRISKPatternScale = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 10, 0.1));
        //spinnerSIFTContrastThreshold = new JSpinner(new SpinnerNumberModel(0.04, 0.001, 1, 0.001));
        spinnerSIFTContrastThreshold = new HighPrecisionDisplaySpinner(0.04, 0.00001, 1.0, 0.00001, 5);
        spinnerSIFTEdgeThreshold = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 10000, 0.1));
        spinnerSIFTSigma = new JSpinner(new SpinnerNumberModel(1.6, 0.1, 100000, 0.1));
        spinnerSIFTValidationThreshold = new JSpinner(new SpinnerNumberModel(0.75, 0.01, 1, 0.01));
        spinnerKAZEThreshold = new HighPrecisionDisplaySpinner(0.001, 0.00001, 1.0, 0.0001, 4);
        spinnerAKAZEThreshold = new HighPrecisionDisplaySpinner(0.001, 0.00001, 1.0, 0.0001, 4);
        displayOptionPanel = new JPanel();
        displayOptionPanel.setLayout(new BoxLayout(displayOptionPanel, BoxLayout.Y_AXIS));

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
        basePanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Landmarks Generation Using Feature Tracking Algorithms"));
        final Spacer spacer1 = new Spacer();
        basePanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        allPanel = new JPanel();
        allPanel.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(allPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        orbParametersPanel = new JPanel();
        orbParametersPanel.setLayout(new GridLayoutManager(9, 2, new Insets(0, 0, 0, 0), -1, -1));
        allPanel.add(orbParametersPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        orbParametersPanel.setBorder(BorderFactory.createTitledBorder("ORB parameters"));
        final JLabel label1 = new JLabel();
        label1.setText("Number of features");
        orbParametersPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Scale factor");
        orbParametersPanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Number of levels");
        orbParametersPanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("First level");
        orbParametersPanel.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Edge threshold");
        orbParametersPanel.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("wta_k");
        orbParametersPanel.add(label6, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("Detection method");
        orbParametersPanel.add(label7, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("Patch size");
        orbParametersPanel.add(label8, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("FAST threshold");
        orbParametersPanel.add(label9, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerORBNbFeatures = new JSpinner();
        orbParametersPanel.add(spinnerORBNbFeatures, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        orbParametersPanel.add(spinnerORBScaleFactor, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerORBNbLevels = new JSpinner();
        orbParametersPanel.add(spinnerORBNbLevels, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerORBFirstLevel = new JSpinner();
        orbParametersPanel.add(spinnerORBFirstLevel, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerORBEdgeThreshold = new JSpinner();
        orbParametersPanel.add(spinnerORBEdgeThreshold, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerORBwta_k = new JSpinner();
        orbParametersPanel.add(spinnerORBwta_k, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxORBDetectionMethod = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("HARRIS CORNER");
        defaultComboBoxModel1.addElement("FAST");
        comboBoxORBDetectionMethod.setModel(defaultComboBoxModel1);
        orbParametersPanel.add(comboBoxORBDetectionMethod, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerORBPatchSize = new JSpinner();
        orbParametersPanel.add(spinnerORBPatchSize, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerORBFASTThreshold = new JSpinner();
        orbParametersPanel.add(spinnerORBFASTThreshold, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        briskParametersPanel = new JPanel();
        briskParametersPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        allPanel.add(briskParametersPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        briskParametersPanel.setBorder(BorderFactory.createTitledBorder("BRISK parameters"));
        final JLabel label10 = new JLabel();
        label10.setText("Threshold");
        briskParametersPanel.add(label10, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setText("Number of octaves");
        briskParametersPanel.add(label11, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("Pattern Scale");
        briskParametersPanel.add(label12, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerBRISKThreshold = new JSpinner();
        briskParametersPanel.add(spinnerBRISKThreshold, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerBRISKNbOctaves = new JSpinner();
        briskParametersPanel.add(spinnerBRISKNbOctaves, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        briskParametersPanel.add(spinnerBRISKPatternScale, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        siftParametersPanel = new JPanel();
        siftParametersPanel.setLayout(new GridLayoutManager(7, 3, new Insets(0, 0, 0, 0), -1, -1));
        allPanel.add(siftParametersPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        siftParametersPanel.setBorder(BorderFactory.createTitledBorder("SIFT parameters"));
        final JLabel label13 = new JLabel();
        label13.setText("Number of Features");
        label13.setToolTipText("The number of best features to retain. The features are ranked by their scores (measured in SIFT algorithm as the local contrast). 0 = infinite features");
        siftParametersPanel.add(label13, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label14 = new JLabel();
        label14.setText("Number of Octaves");
        label14.setToolTipText("The number of layers in each octave. 3 is the value used in D. Lowe paper. The number of octaves is computed automatically from the image resolution.");
        siftParametersPanel.add(label14, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label15 = new JLabel();
        label15.setText("Contrast Threshold");
        label15.setToolTipText("The contrast threshold used to filter out weak features in semi-uniform (low-contrast) regions. The larger the threshold, the less features are produced by the detector.");
        siftParametersPanel.add(label15, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label16 = new JLabel();
        label16.setText("Edge Threshold");
        label16.setToolTipText("The threshold used to filter out edge-like features. Note that the its meaning is different from the contrastThreshold, i.e. the larger the edgeThreshold, the less features are     filtered out (more features are retained)");
        siftParametersPanel.add(label16, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label17 = new JLabel();
        label17.setText("Sigma");
        label17.setToolTipText("The sigma of the Gaussian applied to the input image at the octave. If your image is captured with a weak camera with soft lenses, you might want to reduce the number.");
        siftParametersPanel.add(label17, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        checkBoxSIFTCrossValidation = new JCheckBox();
        checkBoxSIFTCrossValidation.setSelected(true);
        checkBoxSIFTCrossValidation.setText("Cross validation");
        siftParametersPanel.add(checkBoxSIFTCrossValidation, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerSIFTNbFeatures = new JSpinner();
        siftParametersPanel.add(spinnerSIFTNbFeatures, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerSIFTNbOctaves = new JSpinner();
        siftParametersPanel.add(spinnerSIFTNbOctaves, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        siftParametersPanel.add(spinnerSIFTContrastThreshold, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        siftParametersPanel.add(spinnerSIFTEdgeThreshold, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        siftParametersPanel.add(spinnerSIFTSigma, new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label18 = new JLabel();
        label18.setEnabled(true);
        label18.setText("Validation Threshold");
        siftParametersPanel.add(label18, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerSIFTValidationThreshold.setEnabled(false);
        siftParametersPanel.add(spinnerSIFTValidationThreshold, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        kazeParametersPanel = new JPanel();
        kazeParametersPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        allPanel.add(kazeParametersPanel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        kazeParametersPanel.setBorder(BorderFactory.createTitledBorder("KAZE parameters"));
        checkBoxKAZEExtended = new JCheckBox();
        checkBoxKAZEExtended.setText("Extended (128 bits descriptor)");
        kazeParametersPanel.add(checkBoxKAZEExtended, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label19 = new JLabel();
        label19.setText("Threshold");
        kazeParametersPanel.add(label19, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        checkBoxKAZEUpright = new JCheckBox();
        checkBoxKAZEUpright.setText("upright (non rotation invariant)");
        kazeParametersPanel.add(checkBoxKAZEUpright, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label20 = new JLabel();
        label20.setText("Number of Octaves");
        kazeParametersPanel.add(label20, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label21 = new JLabel();
        label21.setText("Number of Layers");
        kazeParametersPanel.add(label21, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxKAZEDiffusivity = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
        defaultComboBoxModel2.addElement("DIFF_PM_G1");
        defaultComboBoxModel2.addElement("DIFF_PM_G2");
        defaultComboBoxModel2.addElement("DIFF_WEICKERT");
        defaultComboBoxModel2.addElement("DIFF_CHARBONNIER");
        comboBoxKAZEDiffusivity.setModel(defaultComboBoxModel2);
        kazeParametersPanel.add(comboBoxKAZEDiffusivity, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label22 = new JLabel();
        label22.setText("Diffusivity");
        kazeParametersPanel.add(label22, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerKAZENbOctaves = new JSpinner();
        kazeParametersPanel.add(spinnerKAZENbOctaves, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerKAZENbLayers = new JSpinner();
        kazeParametersPanel.add(spinnerKAZENbLayers, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        kazeParametersPanel.add(spinnerKAZEThreshold, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        akazePanel = new JPanel();
        akazePanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        allPanel.add(akazePanel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        akazePanel.setBorder(BorderFactory.createTitledBorder("AKAZE parameters"));
        final JLabel label23 = new JLabel();
        label23.setText("Threshold");
        akazePanel.add(label23, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        akazePanel.add(spinnerAKAZEThreshold, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label24 = new JLabel();
        label24.setText("Number of Octaves");
        akazePanel.add(label24, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerAKAZENbOctaves = new JSpinner();
        akazePanel.add(spinnerAKAZENbOctaves, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label25 = new JLabel();
        label25.setText("Number of Layers");
        akazePanel.add(label25, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerAKAZENbLayers = new JSpinner();
        akazePanel.add(spinnerAKAZENbLayers, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label26 = new JLabel();
        label26.setText("Diffusivity");
        akazePanel.add(label26, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxAKAZEDiffusivity = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel3 = new DefaultComboBoxModel();
        defaultComboBoxModel3.addElement("DIFF_PM_G1");
        defaultComboBoxModel3.addElement("DIFF_PM_G2");
        defaultComboBoxModel3.addElement("DIFF_WEICKERT");
        defaultComboBoxModel3.addElement("DIFF_CHARBONNIER");
        comboBoxAKAZEDiffusivity.setModel(defaultComboBoxModel3);
        akazePanel.add(comboBoxAKAZEDiffusivity, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        akazePanel.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder("descriptor"));
        final JLabel label27 = new JLabel();
        label27.setText("type");
        panel1.add(label27, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxAKAZEType = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel4 = new DefaultComboBoxModel();
        defaultComboBoxModel4.addElement("KAZE");
        defaultComboBoxModel4.addElement("KAZE Upright");
        defaultComboBoxModel4.addElement("MLDB");
        defaultComboBoxModel4.addElement("MLDB Upright");
        comboBoxAKAZEType.setModel(defaultComboBoxModel4);
        panel1.add(comboBoxAKAZEType, new GridConstraints(0, 2, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label28 = new JLabel();
        label28.setText("size");
        panel1.add(label28, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerAKAZESize = new JSpinner();
        panel1.add(spinnerAKAZESize, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label29 = new JLabel();
        label29.setText("channels");
        panel1.add(label29, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerAKAZEChannels = new JSpinner();
        panel1.add(spinnerAKAZEChannels, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        basePanel.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, true));
        optionsPanel = new JPanel();
        optionsPanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(optionsPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label30 = new JLabel();
        label30.setText("Detector");
        optionsPanel.add(label30, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxDetector = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel5 = new DefaultComboBoxModel();
        defaultComboBoxModel5.addElement("SIFT");
        defaultComboBoxModel5.addElement("KAZE");
        defaultComboBoxModel5.addElement("BRISK");
        defaultComboBoxModel5.addElement("ORB");
        defaultComboBoxModel5.addElement("AKAZE");
        comboBoxDetector.setModel(defaultComboBoxModel5);
        optionsPanel.add(comboBoxDetector, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label31 = new JLabel();
        label31.setText("Descriptor");
        optionsPanel.add(label31, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        comboBoxDescriptor = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel6 = new DefaultComboBoxModel();
        defaultComboBoxModel6.addElement("SIFT");
        defaultComboBoxModel6.addElement("KAZE");
        defaultComboBoxModel6.addElement("BRISK");
        defaultComboBoxModel6.addElement("ORB");
        defaultComboBoxModel6.addElement("AKAZE");
        comboBoxDescriptor.setModel(defaultComboBoxModel6);
        optionsPanel.add(comboBoxDescriptor, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label32 = new JLabel();
        label32.setText("Minimum chain length");
        optionsPanel.add(label32, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerChainLength = new JSpinner();
        optionsPanel.add(spinnerChainLength, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label33 = new JLabel();
        label33.setText("Maximum jump allowed");
        optionsPanel.add(label33, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerMaxJump = new JSpinner();
        optionsPanel.add(spinnerMaxJump, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        checkBoxPreValidateWithHomography = new JCheckBox();
        checkBoxPreValidateWithHomography.setSelected(true);
        checkBoxPreValidateWithHomography.setText("Pre-validate with homography (distance in pixels)");
        optionsPanel.add(checkBoxPreValidateWithHomography, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerHomographyDistance = new JSpinner();
        optionsPanel.add(spinnerHomographyDistance, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        checkBoxFuseLandmarksChainsWhen = new JCheckBox();
        checkBoxFuseLandmarksChainsWhen.setSelected(true);
        checkBoxFuseLandmarksChainsWhen.setText("Fuse landmarks chains when possible");
        optionsPanel.add(checkBoxFuseLandmarksChainsWhen, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(displayOptionPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return basePanel;
    }

    class PreviewFeatureThread extends Thread {
        int previewIndex;

        public PreviewFeatureThread(int index) {
            this.previewIndex = index;
        }

        public void run() {
            ImageProcessor ip;
            synchronized (ts) {
                ts.setSlice(previewIndex + 1);
                //ip = ts.getProcessor();
                ip = new FloatProcessor(ts.getAlignmentImageSize()[0], ts.getAlignmentImageSize()[1], ts.getPixelsForAlignment(previewIndex));
                ip.setMinAndMax(ts.getDisplayRangeMin(), ts.getDisplayRangeMax());
                //new ImagePlus("", ip).show();
            }
            //ImageProcessor ip = new FloatProcessor(ts.getWidth(), ts.getHeight(), ts.getPixels(previewIndex, false), null);
            IJ.showStatus("looking for seeds...");
            if (fmodelDetect == null) {
                try {
                    initGenerators.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            fmodelDetect.detect(ip);
            //System.out.println(fmodelDetect.getParametersAsString());
            synchronized (fmodelDetect) {
                ArrayList<Point2D> points = fmodelDetect.getFeatures();
                Roi roi = createRoi(points);

                String title = "detector ";
                switch (comboBoxDetector.getSelectedIndex()) {
                    case SIFT:
                        title += "SIFT";
                        break;
                    case KAZE:
                        title += "KAZE";
                        break;
                    case BRISK:
                        title += "BRISK";
                        break;
                    case ORB:
                        title += "ORB";
                        break;
                    case AKAZE:
                        title += "AKAZE";
                        break;
                }
                if (previewFeature == null) {

                    ImagePlus preview2 = new ImagePlus(title + " : " + points.size() + " seeds detected (" + ts.getTiltAngle(previewIndex) + ")", ip);
                    System.out.println("create preview feature tracking");
                    preview2.setRoi(roi);
//                generator.resetCompletion();
//                generator.getFilteredImageMSD(previewCritical2, localMinima, true, percentageToExcludeX, percentageToExcludeY);
                    preview2.updateAndRepaintWindow();
                    if (previewFeature != null) previewFeature.close();
                    previewFeature = preview2;

                    previewFeature.show();
                    previewFeature.getWindow().toFront();

                } else {
//                generator.resetCompletion();
                    previewFeature.setProcessor(title + " : " + points.size() + " seeds detected(" + ts.getTiltAngle(previewIndex) + ")", ip);
                    System.out.println("update preview critical");
                    previewFeature.deleteRoi();
                    previewFeature.setRoi(roi);
//                generator.getFilteredImageMSD(previewCritical, localMinima, true, percentageToExcludeX, percentageToExcludeY);
                    if (!previewFeature.isVisible() && basePanel.isVisible() && ts.getCompletion() >= 0) {
                        previewFeature.show();
                        previewFeature.updateAndRepaintWindow();
                        previewFeature.getWindow().toFront();
                    }
                }
                IJ.showStatus("");
            }
        }

        private Roi createRoi(ArrayList<Point2D> points) {
            PointRoi roi = null;
            synchronized (points) {
                for (Point2D p : points) {
                    if (roi == null) {
                        roi = new PointRoi(p.getX(), p.getY());
                    } else {
                        roi.addPoint(p.getX(), p.getY());
                    }
                }
            }
            if (roi != null) {
                roi.setPointType((int) Prefs.get("point.type", 2));
                roi.setSize((int) Prefs.get("point.size", 2));
            }
            return roi;
        }

        public void interrupt() {
            //previewCritical=null;
            super.interrupt();
        }

    }
}
