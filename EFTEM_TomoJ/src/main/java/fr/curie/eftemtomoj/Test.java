/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

import fr.curie.eftemtomoj.gui.WizardDialog;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author Nick Aschman
 */
public class Test {
    public static void main(String[] args) throws Exception {
        testGUI();
    }

    private static void testGUI() {

        WizardDialog wiz = WizardDialog.create();
        wiz.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent we) {
                System.exit(0);
            }
        });
        wiz.showWizard();
    }
}
