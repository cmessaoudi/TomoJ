package fr.curie.tomoj.tomography;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.process.ImageProcessor;
import fr.curie.tomoj.tomography.TiltSeries;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class AnglesForTiltSeries {

    static public double[] getAngles(ImagePlus imp){
        double[] angles = labels2Angles(imp);
        if(angles==null) angles=ask4Angles(imp);
        return angles;
    }


    static public double[] labels2Angles(ImagePlus imp) {
        return labels2Angles(imp.getImageStack());
    }
    /**
     * gets the labels of slices in the given stackImage<BR>
     * tries to convert them to tilt angles information
     *
     * @param stack ImageStack containing the tilt series
     * @return the tilt angles for each image in the tilt series, null if
     * labels are not tilt angles
     */
    static public double[] labels2Angles(ImageStack stack) {
        double[] angles = new double[stack.getSize()];
        int i = 0;
        while (i < stack.getSize()) {
            String str = stack.getSliceLabel(i + 1);
            try {

                double tmp = Double.parseDouble(str);
                angles[i] = tmp;
                System.out.print(" " + tmp);
            } catch (Exception e) {
                return null;
            }
            i++;
        }
        System.out.println();

        return angles;
    }



    static public double[] ask4Angles(ImagePlus imp) {
        double[]angles = new double[imp.getStackSize()];
        if(imp instanceof TiltSeries) angles=((TiltSeries)imp).getTiltAngles();
        double[] saxtonangles;
        int startimg = imp.getCurrentSlice() - 1;

        GenericDialog gd2 = new GenericDialog("TOMOJ");
        double Adeb = Prefs.get("TOMOJ_Astart.double", -61.0);
        double Ainc = Prefs.get("TOMOJ_Ainc.double", 2.0);
        boolean saxton = false;
        boolean file = false;
        boolean startAtFirst = true;

        gd2.addNumericField("Start Angle:", Adeb, 2);
        gd2.addNumericField("Increment Angle:", Ainc, 2);
        gd2.addCheckbox("saxton scheme", saxton);
        gd2.addCheckbox("load angles from file", file);
        gd2.addCheckbox("starts from first image", startAtFirst);
        gd2.showDialog();
        if (gd2.wasCanceled()) {
            return null;
        }
        Adeb = gd2.getNextNumber();
        Ainc = gd2.getNextNumber();
        Prefs.set("TOMOJ_Astart.double", Adeb);
        Prefs.set("TOMOJ_Ainc.double", Ainc);
        saxton = gd2.getNextBoolean();
        file = gd2.getNextBoolean();
        startAtFirst = gd2.getNextBoolean();


        if (startAtFirst) startimg = 0;

        if (file) {
            OpenDialog od = new OpenDialog("Load rawtlt File...", "");
            angles = readAnglesFromFile(od.getDirectory(), od.getFileName(), imp);
        } else {
            if (saxton) {
                saxtonangles = new double[500];
                double angle;
                double a = 0;
                int index = 0;
                saxtonangles[index] = a;
                int sindex = 0;
                double mindiff = (a - Adeb) * (a - Adeb);
                for (index = 1; a <= +80 && index < saxtonangles.length; index++) {
                    angle = a + Math.toDegrees(Math.asin(Math.sin(Math.toRadians(Ainc)) * Math.cos(Math.toRadians(a))));
                    a = Math.round(angle * 100) / 100.0;
                    saxtonangles[index] = a;
                    angle = a - Math.abs(Adeb);
                    angle *= angle;
                    if (angle < mindiff) {
                        mindiff = angle;
                        sindex = index;
                    }
                    IJ.log("" + a);
                }
                IJ.log("starting at index " + sindex + " angle=" + saxtonangles[sindex]);
                int inc = 1;
                if (Adeb < 0) {
                    inc = -1;
                }
                for (int i = 0; i < angles.length; i++) {
                    angles[i] = inc * saxtonangles[sindex];
                    sindex += inc;
                    if (inc == -1) {
                        if (sindex == 0) {
                            inc = 1;
                        }
                    }
                }
            } else {
                for (int i = 0; i < angles.length; i++) {
                    double angle;
                    if (i == startimg) {
                        angle = Adeb;
                    } else if (i > startimg) {
                        angle = angles[i - 1] + Ainc;
                    } else {
                        angle = angles[i];
                    }
                    //serie.setAngle(imp.getCurrentSlice() - 1 + i, angle);
                    angles[i] = angle;

                }
            }
        }

        //ts.setSlice(ts.getCurrentSlice());
        //ts.updateAndDraw();
        return angles;
    }

    /**
     * read the angle from a file
     *
     * @param directory directory of the file
     * @param filename  name of the file. It may be a parameter file from JEOL or a rawtlt file (text file with one value of tilt angle per image separated by space or one per line)
     * @param ts        TiltSeries where angles will be put
     * @return array containing the angles read in the file
     */
    public static double[] readAnglesFromFile(String directory, String filename, ImagePlus ts) {
        double[] angles = null;
        try {
            BufferedReader bf = new BufferedReader(new FileReader(directory + filename));
            if (filename.endsWith("dm3")) {
                System.out.println("dm3 angle file");
                angles = readFromDm3(directory+filename, ts);
            } else if (filename.endsWith("RecParam.txt")) {
                System.out.println("Jeol angle file");
                angles = readFromJeol(bf, ts);
            } else {
                System.out.println("rawtlt angle file");
                angles = readFromRawtlt(bf, ts);

            }
            bf.close();
        } catch (Exception ex) {
            IJ.error("" + ex);
        }
        //System.out.println("nb angles"+angles.length);
        return angles;
    }

    /**
     * reads angles from a dm3 image (S. Trepout acquisition script)
     *
     * @param path path to dma3 file
     * @param ts   the TiltSeries where Tilt Axis will be updated (not Tilt Angles)
     * @return the angles inside the file
     * @throws IOException if errors occur while parsing
     */
    protected static double[] readFromDm3(String path, ImagePlus ts) throws IOException {
        double[] angles = null;
        ImagePlus tmp=IJ.openImage(path);
        ImageProcessor ip=tmp.getProcessor();
        angles=new double[tmp.getWidth()];
        for(int i=0;i<ip.getWidth();i++){
            angles[i]=ip.getf(i);
        }
        return angles;
    }

    /**
     * reads angles from a jeol parameter file
     *
     * @param bf the already opened file
     * @param ts the TiltSeries where Tilt Axis will be updated (not Tilt Angles)
     * @return the angles inside the file
     * @throws IOException if errors occur while parsing
     */
    protected static double[] readFromJeol(BufferedReader bf, ImagePlus ts) throws IOException {
        double[] angles = null;
        String line = bf.readLine();
        do {
            if (line.startsWith("AxisRotation")) {
                System.out.println("tilt axis detected");
                double tiltaxis = Double.parseDouble(line.split(":")[1]);
                if(ts instanceof TiltSeries) ((TiltSeries)ts).setTiltAxis(tiltaxis);
            }
            if (line.startsWith("TiltXSerise")) {
                System.out.println("tilt angles detected");
                String[] split = line.split(" ");
                angles = new double[split.length - 1];
                for (int i = 0; i < angles.length; i++) {
                    angles[i] = -Double.parseDouble(split[i + 1]);
                }
            }
        } while ((line = bf.readLine()) != null);
        return angles;
    }

    /**
     * reads angles from a rawtlt file
     *
     * @param bf the already opened file
     * @param ts TiltSeries needed to know the number of file to read
     * @return the angles contained in the file
     * @throws IOException if error occurs while parsing the file
     */
    protected static double[] readFromRawtlt(BufferedReader bf, ImagePlus ts) throws IOException {
        //System.out.println("enter read from rawtlt");
        double[] angles = new double[ts.getStackSize()];
        String line = bf.readLine();
        //System.out.println("first line is :" + line + ":");
        String[] split = line.trim().split("\\s+");
        boolean l = split.length > 2;
        //System.out.println("the split length is " + split.length);
        if (!l) angles[0] = Double.parseDouble(line);
        else angles[0] = Double.parseDouble(split[0]);
        for (int i = 1; i < angles.length; i++) {
            if (!l) {
                line = bf.readLine();
                angles[i] = Double.parseDouble(line.trim());
            } else {
                angles[i] = Double.parseDouble(split[i]);
            }
        }
        /*System.out.println("angles read ");
          for (int i = 0; i < angles.length; i++) {
              System.out.print(" / " + angles[i]);
          } */
        return angles;
    }
}
