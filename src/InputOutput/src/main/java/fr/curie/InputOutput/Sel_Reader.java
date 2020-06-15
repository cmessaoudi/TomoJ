package fr.curie.InputOutput;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.RandomAccessFile;


public class Sel_Reader extends ImagePlus implements PlugIn {

    /**
     * Main processing method for the SpiderReader_ object
     *
     * @param arg Description of the Parameter
     */
    public void run(String arg) {
        String directory;
        String fileName;
        if ((arg == null) || (arg.compareTo("") == 0)) {
            // Choose a file since none specified
            OpenDialog od = new OpenDialog("Load sel File...", arg);
            fileName = od.getFileName();
            if (fileName == null) {
                return;
            }
            directory = od.getDirectory();
        } else {
            // we were sent a filename to open
            File dest = new File(arg);
            directory = dest.getParent();
            fileName = dest.getName();
        }

        // Load in the image
        ImagePlus imp = load(directory, fileName);
        if (imp == null) {
            return;
        }

        // Attach the Image Processor
        if (imp.getNSlices() > 1) {
            setStack(fileName, imp.getStack());
        } else {
            setProcessor(fileName, imp.getProcessor());
        }
        // Copy the scale info over
        copyScale(imp);

        // Show the image if it was selected by the file
        // chooser, don't if an argument was passed ie
        // some other ImageJ process called the plugin
        if (arg.equals("")) {
            show();
        }
    }


    /**
     * Description of the Method
     *
     * @param directory Description of the Parameter
     * @param fileName  Description of the Parameter
     * @return Description of the Return Value
     */
    public static ImagePlus load(String directory, String fileName) {
        if ((fileName == null) || (fileName.equals(""))) {
            return null;
        }

        if (!directory.endsWith(File.separator)) {
            directory += File.separator;
        }
        ImageStack is = null;
        IJ.showStatus("Loading sel File: " + directory + fileName);
        try {
            RandomAccessFile f = new RandomAccessFile(directory + fileName, "r");
            Opener tr = new Opener();
            String line;
            while ((line = f.readLine()) != null) {
                String[] s = line.split(" ");

                //ImagePlus imptmp = (s[0].endsWith(".xmp"))?Spider_Reader.openSpider(directory,s[0]):tr.openImage(directory, s[0]);
                ImagePlus imptmp = tr.openImage(directory, s[0]);
                if (is == null) {
                    is = new ImageStack(imptmp.getWidth(), imptmp.getHeight());
                }
                is.addSlice(s[0], imptmp.getProcessor());
            }
        } catch (Exception e) {
            IJ.showStatus("");
            IJ.showMessage("Sel_Reader", "Sel_Reader : " + e);
            return null;
        }

        return new ImagePlus(fileName, is);

    }


}

