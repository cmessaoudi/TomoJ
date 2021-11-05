/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.gui;

import fr.curie.eftemtomoj.EftemDataset;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nick Aschman
 */
public class WizardDialog extends JDialog {
    private JPanel contentPane;
    private JButton cancelButton;
    private JButton nextButton;
    private JButton backButton;
    private JPanel cardPanel;
    private JButton viewStackButton;
    private ProgressDialog progressDialog = null;

    public static final int FINISHED = 0;
    public static final int CANCELLED = 1;

    private Map<String, WizardPage> wizardPages = new HashMap<String, WizardPage>();
    private String currentPageId = null;
    private int returnCode = CANCELLED;
    private final String mainTitle;
    private EftemDataset currentDataset;

    // Singleton
//    public static final WizardDialog TheWizard = new WizardDialog("EFTEMTomoJ");

    private WizardDialog(String title) {
        mainTitle = title;

        $$$setupUI$$$();
        setContentPane(contentPane);
        getRootPane().setDefaultButton(nextButton);

        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onNext();
            }
        });

        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                onBack();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        viewStackButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if (currentDataset != null) {
                    setCursor(new Cursor(Cursor.WAIT_CURSOR));
                    currentDataset.createHyperStack().show();
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        });

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        /*addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                super.componentResized(e);
                //cardPanel.setMinimumSize(contentPane.getSize());
                //cardPanel.setPreferredSize(contentPane.getSize());
                cardPanel.setBackground(Color.red);
                System.out.println("window changed size");
                ((JDialog)e.getSource()).validate();
            }
        });  */

        setCurrentDataset(null);
    }

    public EftemDataset getCurrentDataset() {
        return currentDataset;
    }

    public void setCurrentDataset(EftemDataset ds) {
        currentDataset = ds;
        viewStackButton.setEnabled(ds != null);
    }

    private void onBack() {
        if (currentPageId != null) {
            WizardPage currentPage = wizardPages.get(currentPageId);

            if (currentPage.hasPreviousPage()) {
                setCurrentPage(currentPage.getPreviousId());
            }
        }
    }

    private void onNext() {
        if (currentPageId != null) {
            WizardPage currentPage = wizardPages.get(currentPageId);
            if (!currentPage.validate()) {
                return;
            }

            if (currentPage.hasNextPage()) {
                setCurrentPage(currentPage.getNextId());
            } else {
                returnCode = FINISHED;
                dispose();
            }
        }
    }

    private void onCancel() {
        if (currentPageId != null) {
            WizardPage currentPage = wizardPages.get(currentPageId);
            if (!currentPage.abort()) {
                return;
            }

            returnCode = CANCELLED;
            dispose();
        }
    }

    public static WizardDialog create() {
        WizardDialog dlg = new WizardDialog("EFTEMTomoJ");

        final DatasetPanel datasetPage = new DatasetPanel(dlg);
        final HotspotRemovalPanel hotspotPage = new HotspotRemovalPanel(dlg);
        final AlignmentPanel alignmentPage = new AlignmentPanel(dlg);
        final DenoisingPanel denoisingPage = new DenoisingPanel(dlg);
        final MappingPanel2 mappingPage1 = new MappingPanel2(dlg);
        final MappingPanel2 mappingPage2 = new MappingPanel2(dlg);
        final GeneralAlignmentPanel ali = new GeneralAlignmentPanel(dlg);
        final GeneralDenoisingPanel denoise = new GeneralDenoisingPanel(dlg);

        dlg.addPage(datasetPage);
        dlg.addPage(ali);
        dlg.addPage(hotspotPage);
        dlg.addPage(alignmentPage);
        dlg.addPage(mappingPage1);
        dlg.addPage(denoisingPage);
        dlg.addPage(mappingPage2);
        dlg.addPage(denoise);

        datasetPage.setNextId(ali.id);
        //ali.setNextId(denoisingPage.id);
        ali.setNextId(denoise.id);
        denoise.setPreviousId(ali.id);


        hotspotPage.setNextId(alignmentPage.id);
        alignmentPage.setNextId(mappingPage1.id);
        alignmentPage.setPreviousId(hotspotPage.id);
        mappingPage1.setPreviousId(alignmentPage.id);
        mappingPage1.setNextId(denoisingPage.id);
        //denoisingPage.setPreviousId(mappingPage1.id);
        denoisingPage.setNextId(mappingPage2.id);

        //mappingPage.setPreviousId(denoisingPage.id);

        dlg.setCurrentPage(datasetPage.id);

        return dlg;
    }

    public int showWizard() {
        setModal(false);
        pack();
        //setSize(new Dimension(600, 500));
        setVisible(true);
        return returnCode;
    }

    // Progress related

    public void showProgress(String msg) {
        progressDialog = new ProgressDialog(this, getTitle());
        progressDialog.show(msg);
    }

    public void hideProgress() {
        if (progressDialog != null)
            progressDialog.dispose();
    }

    public void updateProgress(double p) {
        if (progressDialog != null)
            progressDialog.setValue(p);
    }

    public void updateProgressMessage(String msg) {
        if (progressDialog != null)
            progressDialog.setMessage(msg);
    }

    // Wizard page management

    public void addPage(WizardPage page) {
        cardPanel.add(page.getComponent(), page.id);
        wizardPages.put(page.id, page);
    }

    public void setCurrentPage(String id) {
        if (wizardPages.containsKey(id)) {
            currentPageId = id;

            WizardPage currentPage = wizardPages.get(id);
            currentPage.activate();

            CardLayout layout = (CardLayout) cardPanel.getLayout();
            layout.show(cardPanel, id);
            //pack();

            // Update title bar
            if (currentPage.hasTitle())
                setTitle(mainTitle + " - " + currentPage.getTitle());
            else
                setTitle(mainTitle);

            // Update button texts
            if (currentPage.hasNextPage()) {
                this.nextButton.setText("Next >");
            } else {
                this.nextButton.setText("Finish");
            }

            if (currentPage.hasPreviousPage()) {
                this.backButton.setEnabled(true);
            } else {
                this.backButton.setEnabled(false);
            }
        }
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
        cardPanel = new JPanel();
        cardPanel.setLayout(new CardLayout(0, 0));
        contentPane.add(cardPanel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        cancelButton = new JButton();
        cancelButton.setText("Cancel");
        panel1.add(cancelButton, new com.intellij.uiDesigner.core.GridConstraints(0, 4, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        nextButton = new JButton();
        nextButton.setText("Next >");
        panel1.add(nextButton, new com.intellij.uiDesigner.core.GridConstraints(0, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        backButton = new JButton();
        backButton.setText("< Back");
        panel1.add(backButton, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panel1.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        viewStackButton = new JButton();
        viewStackButton.setText("View Stack");
        panel1.add(viewStackButton, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JSeparator separator1 = new JSeparator();
        contentPane.add(separator1, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }
}
