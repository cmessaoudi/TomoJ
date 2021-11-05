/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.gui;

import fr.curie.eftemtomoj.TaskObserver;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * @author Nick Aschman
 */
public abstract class WizardApprentice<T> extends SwingWorker<T, Double> implements TaskObserver {

    private final WizardDialog dialog;
    private String message;
    protected double progress = 0.0;

    protected WizardApprentice(WizardDialog dlg, String message) {
        this.dialog = dlg;
        this.message = message;

        addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                if (propertyChangeEvent.getPropertyName().equals("state") && propertyChangeEvent.getNewValue() == StateValue.DONE) {
                    dialog.hideProgress();
                }
            }
        });
    }

    public void go() {
        execute();
        dialog.showProgress(message);
    }

    public void updateProgressBy(double p) {
        progress += p;
        publish(progress);
    }

    public void updateProgress(double p) {
        progress = p;
        publish(progress);
    }

    public void updateMessage(String msg) {
        dialog.updateProgressMessage(msg);
    }

    @Override
    protected void process(List<Double> doubles) {
        dialog.updateProgress(doubles.get(doubles.size() - 1));
    }
}
