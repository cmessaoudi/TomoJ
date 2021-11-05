/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj.gui;

import javax.swing.*;

/**
 * @author Nick Aschman
 */
public abstract class WizardPage {
    public final String id;
    public final WizardDialog dialog;

    private String previousId;
    private String nextId;
    private String title;

    public WizardPage(WizardDialog dialog, String id, String title) {
        this.id = id;
        this.dialog = dialog;
        this.title = title;
    }

    public WizardPage(WizardDialog dialog, String id) {
        this(dialog, id, null);
    }

    public abstract JComponent getComponent();

    public abstract boolean validate();

    public abstract void activate();

    public boolean abort() {
        // Do nothing
        return true;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean hasTitle() {
        return (title != null);
    }

    public boolean hasPreviousPage() {
        return !(previousId == null);
    }

    public boolean hasNextPage() {
        return !(nextId == null);
    }

    public String getPreviousId() {
        return previousId;
    }

    public String getNextId() {
        return nextId;
    }

    public void setPreviousId(String id) {
        previousId = id;
    }

    public void setNextId(String id) {
        nextId = id;
    }

    public boolean isFinishable() {
        return !hasNextPage();
    }
}
