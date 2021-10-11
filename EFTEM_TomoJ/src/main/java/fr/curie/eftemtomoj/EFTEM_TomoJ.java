package fr.curie.eftemtomoj;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import fr.curie.eftemtomoj.TiltSeries;

/**
 * Created by cedric on 25/04/2016.
 */


public class EFTEM_TomoJ {

    public static void main(String[] args) {
        //System.out.println(args.length);
        if (args.length == 0) {
            System.out.println(help());
        } else {
            macro(args);
        }
        //System.out.println("end main");
        System.exit(0);
    }

    public static String help() {
        String result = "#####################################################\n" +
                "EFTEM_TomoJ usage:\n\n\n";
        result += "EFTEM_TomoJ -tsSignal filePath energyValue energyWidth -tsBg filePath energyValue energyWidth [...] -align metric radius -map law corrMap\n\n";
        result += "\nwhere metric can be (take care of case): \n" +
                "\tNCC\n" +
                "\tNMI\n" +
                "\tMS\n";
        result += "\nand law can be (take care of case): \n" +
                "\tLinear\n" +
                "\tQuadratic\n" +
                "\tExponential\n" +
                "\tPower\n" +
                "\tLogarithmic\n" +
                "\tLogPolynomial\n" +
                "\tLogLogPolynomial\n" +
                "\tRatio\n" +
                "\tSubtract\n";
        result += "#####################################################\n";
        return result;
    }

    public static void macro(String[] args) {
        ArrayList<TiltSeries> tss = new ArrayList<TiltSeries>();
        EftemDataset ds = null;
        for (int a = 0; a < args.length; a++) {
            System.out.println(args[a]);
            if (args[a].startsWith("-ts")) {
                TiltSeries ts = loadTiltSeries(args[a + 1]);
                ts.setEnergyShift(Float.parseFloat(args[a + 2]));
                ts.setSlitWidth(Float.parseFloat(args[a + 3]));
                ts.setSignal(args[a].endsWith("ignal"));
                tss.add(ts);
                System.out.println("loading " + args[a + 1] + ((ts.isSignal()) ? " as signal" : " as bkg"));
                a += 3;
            }
            if (args[a].startsWith("-align")) {
                final Metrics.Metric metric = Metrics.Metric.valueOf(args[a + 1]);
                double radius = Double.parseDouble(args[a + 2]);
                try {
                    TiltSeries[] tsArray = new TiltSeries[tss.size()];
                    ds = new EftemDataset(tss.toArray(tsArray));
                    final ImageRegistration.Algorithm algorithm = ImageRegistration.Algorithm.Multiresolution;
                    final FilteredImage[][] alignedImages = new FilteredImage[ds.getTiltCount()][ds.getWindowCount()];
                    for (int i = 0; i < ds.getTiltCount(); i++) {
                        alignedImages[i] = ds.getImages(i);
                    }
                    ImageRegistration alignator = new ImageRegistration(algorithm, metric);
                    //alignator.addObserver(this);

                    ImageRegistration.Transform[] transforms;
                    for (int i = 0; i < ds.getTiltCount(); i++) {
                        System.out.println("align " + i);
                        transforms = alignator.alignSeries(ds.getImage(i, 0).getImageForAlignment(radius), alignedImages[i], radius);
                        for (int j = 0; j < alignedImages[i].length; j++) {
                            alignedImages[i][j].applyTransform(transforms[j]);
                        }
                    }
                    //save stuff
                    BufferedWriter[] out = new BufferedWriter[ds.getWindowCount()];
                    ImageRegistration.Transform transf;
                    for (int j = 0; j < ds.getWindowCount(); j++) {
                        out[j] = new BufferedWriter(new FileWriter(tss.get(j).getTitle() + "_aligned.transf"));
                    }
                    for (int j = 0; j < ds.getWindowCount(); j++) {
                        ImageStack tmp = new ImageStack(ds.getWidth(), ds.getHeight());
                        for (int i = 0; i < ds.getTiltCount(); i++) {
                            tmp.addSlice(alignedImages[i][j].getImage());
                            transf = alignedImages[i][j].getTransform();
                            out[j].write("" + transf.getTranslateX() + "\t" + transf.getTranslateY() + "\n");
                        }
                        //System.out.println("saving result images");
                        FileSaver fs = new FileSaver(new ImagePlus("", tmp));
                        if (ds.getTiltCount() == 1) fs.saveAsTiff(tss.get(j).getTitle() + "_aligned.tif");
                        else fs.saveAsTiffStack(tss.get(j).getTitle() + "_aligned.tif");
                        //System.out.println("saving result images finished");
                    }
                    for (int j = 0; j < ds.getWindowCount(); j++) {
                        out[j].flush();
                        out[j].close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                a += 2;
            }
            if (args[a].startsWith("-map")) {
                try {
                    if (ds == null) {
                        TiltSeries[] tsArray = new TiltSeries[tss.size()];
                        ds = new EftemDataset(tss.toArray(tsArray));
                    }
                    ds.setLaw(Mapper.Model.valueOf(args[a + 1]));
                    boolean corrmap = Integer.parseInt(args[a + 2]) != 0;
                    ds.correctNegativeValues();
                    // Create new mapper if needed and select model
                    Mapper mapper = new Mapper(ds);
                    ImageStack[] stacks = null;
                    stacks = mapper.computeMaps(ds);
                    // Save and show results
                    ImagePlus mapStack = new ImagePlus("Elemental maps " + mapper.getModel() + " law " + mapper.usedEnergyAsString(), stacks[0]);
                    FileSaver fs = new FileSaver(mapStack);
                    if (ds.getTiltCount() == 1) fs.saveAsTiff(mapStack.getTitle());
                    else fs.saveAsTiffStack(mapStack.getTitle());
                    if (corrmap) {
                        ImagePlus r2MapStack = new ImagePlus("Correlation coefficient maps (" + mapper.getModel() + ")", stacks[1]);
                        fs = new FileSaver(r2MapStack);
                        if (ds.getTiltCount() == 1) fs.saveAsTiff(r2MapStack.getTitle());
                        else fs.saveAsTiffStack(r2MapStack.getTitle());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        //System.out.println("end processing");
    }

    public static fr.curie.eftemtomoj.TiltSeries loadTiltSeries(String path) {
        ImagePlus imp = IJ.openImage(path);
        TiltSeries series = new TiltSeries(imp.getWidth(), imp.getHeight());
        series.setTitle(imp.getTitle());
        if (imp.getNSlices() == 1) {
            series.addSlice(imp.getProcessor().convertToFloat());
        } else {
            ImageStack stack = imp.getImageStack();
            for (int i = 0; i < stack.getSize(); i++) {
                series.addSlice("", stack.getProcessor(i + 1).convertToFloat());
            }
        }


        return series;
    }
}
