/*
 * Copyright 2010 Nick Aschman.
 */

package fr.curie.eftemtomoj;

import ij.IJ;
import ij.ImagePlus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.util.Arrays;

/**
 * @author Nick Aschman
 */
public class JeolBracketIO extends ObservableTask {

    private static class JeolRecParam {
        public float exposureTime = 0.0f;
        public float energyShift = 0.0f;
        public float slitWidth = 0.0f;
        public int width = 0;
        public int height = 0;
        public int binning = 1;
        public float[] tiltAngles = new float[]{};
        public String comment = "";
    }

    private final File[] bracketFolders;
    private final JeolRecParam mainParameters;

    public JeolBracketIO(File folder) throws Exception {
        if (!folder.isDirectory()) {
            throw new Exception("Invalid path: " + folder.getAbsolutePath());
        }

        // List bracket folders by name
        bracketFolders = folder.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return file.getName().startsWith("bracket") && file.isDirectory();
            }
        });

        if (bracketFolders.length == 0) {
            throw new Exception("No data found in " + folder.getAbsolutePath());
        }

        // Find parameter file
        File[] paramFiles = folder.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return file.getName().endsWith("RecParam.txt");
            }
        });

        if (paramFiles.length != 1) {
            throw new Exception("No parameter file found in " + folder.getAbsolutePath());
        }

        mainParameters = this.readParameters(paramFiles[0]);
    }

    public TiltSeries[] readBracketFolders() throws Exception {
        Arrays.sort(bracketFolders);

        // Read folder contents
        TiltSeries[] series = new TiltSeries[bracketFolders.length];
        for (int i = 0; i < bracketFolders.length; i++) {
            setProgress(0.0);
            series[i] = readBracketFolder(i);
        }

        return series;
    }

    public float[] getTiltAngles() {
        return mainParameters.tiltAngles;
    }

    private TiltSeries readBracketFolder(int bracketIndex) throws Exception {
        //System.out.println("read jeol folder "+bracketIndex);
        // We are using the TIFF subfolder since DM3s require a plugin
        File tiffFolder = new File(bracketFolders[bracketIndex], "tiff");
        if (!tiffFolder.isDirectory()) {
            tiffFolder = bracketFolders[bracketIndex];
        }
        System.out.println("opening " + tiffFolder);
        double progStep;

        // Extract acquisition parameters from parameter file
        JeolRecParam bracketParameters;

        File[] paramFiles = tiffFolder.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return file.getName().endsWith("RecParam.txt");
            }
        });

        if (paramFiles.length == 1) {
            bracketParameters = readParameters(paramFiles[0]);
        } else {
            System.out.println("No parameter file found in " + tiffFolder.getAbsolutePath() + ", using defaults.");
            bracketParameters = new JeolRecParam();
        }
        //System.out.println("recParam file found");
        // List TIFF image files
        File[] imageFiles = tiffFolder.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return file.getName().endsWith(".tif");
            }
        });

        if (imageFiles.length == 0) {
            return null;
        }

        // Sort files by name
        Arrays.sort(imageFiles);

        // Initialise tilt series with image dimensions from parameter file (if available)
        TiltSeries series = null;

        if (bracketParameters.width > 0 && bracketParameters.height > 0) {
            series = new TiltSeries(bracketParameters.width / bracketParameters.binning, bracketParameters.height / bracketParameters.binning);
        }

        // Read images and add to tilt series
        ImagePlus tmpImg;

        progStep = 1.0 / (double) imageFiles.length;
        setMessage("Reading images from " + bracketFolders[bracketIndex].getName());

        for (int i = 0; i < imageFiles.length; i++) {
            setProgress(i * progStep);

            tmpImg = IJ.openImage(imageFiles[i].getAbsolutePath());

            if (tmpImg == null) {
                throw new Exception("Unable to read " + imageFiles[i].getAbsolutePath() + ".");
            }

            // Initialise series if necessary
            if (series == null) {
                series = new TiltSeries(tmpImg.getWidth(), tmpImg.getHeight());
            }

            // Convert
            tmpImg.getProcessor();

            series.addSlice("", tmpImg.getProcessor().convertToFloat());
        }

        // Set acquisition parameters
        if (series != null) {
            series.setTitle(bracketFolders[bracketIndex].getName());
            series.setEnergyShift(bracketParameters.energyShift);
            series.setExposureTime(bracketParameters.exposureTime);
            series.setSlitWidth(bracketParameters.slitWidth);
            series.setComment(bracketParameters.comment);
        }

        return series;
    }

    private JeolRecParam readParameters(File paramFile) throws Exception {
        if (!paramFile.canRead() || !paramFile.getName().endsWith("RecParam.txt"))
            throw new Exception("Invalid Jeol parameter file");

        BufferedReader reader = new BufferedReader(new FileReader(paramFile));

        JeolRecParam params = new JeolRecParam();

        String[] split;
        String line;

        while ((line = reader.readLine()) != null) {
            split = line.trim().split(":", 2);

            if (split.length < 2)
                continue;

            split[0] = split[0].trim();
            split[1] = split[1].trim();

            if (split[0].equals("AcqExposure"))
                params.exposureTime = Float.parseFloat(split[1]);

            else if (split[0].equals("EnergyShift"))
                params.energyShift = Float.parseFloat(split[1]);

            else if (split[0].equals("SlitWidth"))
                params.slitWidth = Float.parseFloat(split[1]);

            else if (split[0].equals("AcqWidth"))
                params.width = Integer.parseInt(split[1]);

            else if (split[0].equals("AcqHeight"))
                params.height = Integer.parseInt(split[1]);

            else if (split[0].equals("AcqBinning"))
                params.binning = Integer.parseInt(split[1]);

            else if (split[0].equals("Comment"))
                params.comment = split[1];

            else if (split[0].equals("TiltXSerise")) { // [sic]
                split = split[1].split(" ");

                params.tiltAngles = new float[split.length];
                for (int i = 0; i < split.length; i++) {
                    params.tiltAngles[i] = Float.parseFloat(split[i]);
                }
            }
        }

        return params;
    }
}
