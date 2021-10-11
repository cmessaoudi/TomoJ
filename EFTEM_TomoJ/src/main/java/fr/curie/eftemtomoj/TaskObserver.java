/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

/**
 * @author Nick Aschman
 */
public interface TaskObserver {
    void updateProgress(double p);

    void updateProgressBy(double p);

    void updateMessage(String msg);
}
