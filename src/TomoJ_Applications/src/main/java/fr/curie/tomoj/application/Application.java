package fr.curie.tomoj.application;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by cedric on 29/09/2016.
 */
public interface Application {

    /**
     * run the application with previously defined parameters
     * @return     true if the application finished successfully
     */
    public boolean run();

    /**
     * set all the parameters for the application <BR>
     *     the order of parameters can be found using getParameterType and getParametersName
     * @param parameters
     */
    public void setParameters(Object... parameters);

    /**
     * text to display the help / man of the application
     * @return   a String containing the help of the application
     */
    public String help();

    /**
     * text to get the name of application
     * @return  name of application
     */
    public String name();

    /**
     *  if the application gives some results, use this function to get all of them
     * @return   the results of the application
     */
    public ArrayList<Object> getResults();

    /**
     * get the type of parameters
     * @return   an ArrayList containing Objects of the same type as the parameters in the correct order
     */
    public ArrayList<Object> getParametersType();

    /**
     * get the names of the parameters
     * @return    an arrayList containing a string for each parameter corresponding to its name in the correct order
     */
    public ArrayList<String> getParametersName();

    /**
     * converts the parameters as a string to display informations
     * @return
     */
    public String getParametersValuesAsString();

    /**
     * get a JPanel with all the parameters accessible for easy creation of GUI
     * @return
     */
    public JPanel getJPanel();

    public void interrupt();
    public double getCompletion();

    public void setDisplayPreview(boolean display);



}
