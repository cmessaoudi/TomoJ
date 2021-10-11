/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Nick Aschman
 */
public abstract class ObservableTask {
    private List<TaskObserver> observers = new ArrayList<TaskObserver>();

    public void addObserver(TaskObserver obs) {
        if (obs != null) {
            observers.add(obs);
        }
    }

    public void removeObserver(TaskObserver obs) {
        observers.remove(obs);
    }

    public synchronized void setProgress(double p) {
        for (TaskObserver obs : observers) {
            obs.updateProgress(p);
        }
    }

    public synchronized void progressBy(double p) {
        for (TaskObserver obs : observers) {
            obs.updateProgressBy(p);
        }
    }

    public synchronized void setMessage(String msg) {
        for (TaskObserver obs : observers) {
            obs.updateMessage(msg);
        }
    }

    protected ObservableTask() {
        // Do nothing
    }
}
