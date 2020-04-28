package fr.curie.tomoj.workflow;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Created by IntelliJ IDEA.
 * User: C�dric
 * Date: 04/11/11
 * Time: 14:11
 * To change this template use File | Settings | File Templates.
 */
public class UserAction extends DefaultMutableTreeNode {

    String cmd;
    String args;
    String transformFile;
    boolean imagej;
    String id;
    String currentImages = null;
    String currentReconstruction = null;

    public UserAction(String command, String args, String transformFile, boolean imagejAction) {
        this.cmd = command;
        this.args = args;
        this.transformFile = transformFile;
        this.imagej = imagejAction;

    }

    public String getCommand() {
        return cmd;
    }

    public String getArguments() {
        return args;
    }


    public String getTransformFileName() {
        return transformFile;
    }

    public void setTransformFileName(String name) {
        transformFile = name;
    }

    public boolean isImageJAction() {
        return imagej;
    }

    public String toString() {
        //if( imagej ) return "run(\""+cmd+"\" , \""+args+"\")";
        //return cmd+" "+args;
        if (imagej) return "run(\"" + cmd + "\")";
        return cmd;
    }

    public String toStringComplete(){
        if( imagej ) return "run(\""+cmd+"\" , \""+args+"\")";
        return cmd+" "+args;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCurrentImages() {
        return currentImages;
    }

    public void setCurrentImages(String currentImages) {
        this.currentImages = currentImages;
    }

    public String getCurrentReconstruction() {
        return currentReconstruction;
    }

    public void setCurrentReconstruction(String currentReconstruction) {
        this.currentReconstruction = currentReconstruction;
    }
}
