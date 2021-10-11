/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

import fr.curie.eftemtomoj.gui.WizardDialog;
import ij.plugin.PlugIn;

import javax.swing.*;

/**
 * @author Nick Aschman
 */
public class EFTEMTomoJ_Plugin implements PlugIn {

    public void run(String string) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("could not change look and feel");
        }
        WizardDialog wiz = WizardDialog.create();
        wiz.setDefaultCloseOperation(WizardDialog.DISPOSE_ON_CLOSE);
        wiz.showWizard();
    }
}
