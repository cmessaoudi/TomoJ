package fr.curie.tomoj.align;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import fr.curie.tomoj.tomography.TiltSeries;
import fr.curie.utils.MatrixUtils;
import ij.process.ImageStatistics;

import java.awt.geom.AffineTransform;
import java.io.*;

public class AffineAlignment implements Alignment {
    TiltSeries ts;
    AffineTransform[] transforms;
    AffineTransform[] invTransforms;
    AffineTransform zeroT;
    boolean combine=true;
    double tiltaxis=0;
    boolean tiltAxisVertical=true;
    int zeroindex=0;
    boolean expand = false;
    protected DoubleMatrix2D[] eulerMatrices;

    public AffineAlignment(TiltSeries ts){
        this(ts,true);
    }
    public AffineAlignment(TiltSeries ts, boolean combine){
        this.ts=ts;
        zeroindex=ts.getZeroIndex();
        transforms= new AffineTransform[ts.getImageStackSize()];
        for(int i=0;i<transforms.length;i++) transforms[i]=new AffineTransform();
        invTransforms = new AffineTransform[ts.getImageStackSize()];
        zeroT=new AffineTransform();
        this.combine=combine;
        eulerMatrices=new DoubleMatrix2D[ts.getImageStackSize()];
    }

    @Override
    public AffineTransform getTransform(int index) {
        return getTransform(index,combine);
    }

    @Override
    public AffineTransform getTranslationTransform(int index) {
        AffineTransform tmp=getTransform(index,combine);
        AffineTransform result=new AffineTransform();
        result.setToTranslation(tmp.getTranslateX(),tmp.getTranslateY());
        return result;
    }

    @Override
    public AffineTransform[] getTransforms() {
        return transforms;
    }


    /**
     * remove an image from the tilt series (beware it is starting from 0)
     * @param index        image number
     * if combining transforms : the alignment of other images is kept identical (so it concatenates the transform of removed image to the previous)
     */
    @Override
    public void removeTransform(int index) {
        AffineTransform[] tmpT = transforms;
        transforms = new AffineTransform[ts.getImageStackSize()];
        invTransforms = new AffineTransform[ts.getImageStackSize()];
        int offset = 0;
        for (int i = 0; i < transforms.length; i++) {
            if (i == index) {
                if (combine && i - 1 > 0) {
                    transforms[i - 1].preConcatenate(tmpT[i]);
                }
                offset++;
            }
            transforms[i] = tmpT[i + offset];
        }
        resetEulerMatrices();
    }

    @Override
    public void setZeroIndex(int zeroIndex) {
        this.zeroindex=zeroIndex;
    }

    public void setTransform(int index, AffineTransform t){
        transforms[index]=t;
        invTransforms[index] = null;
    }

    public void setTransform(int index, AffineTransform T, double binning) {
        double[] mat = new double[6];
        T.getMatrix(mat);
        transforms[index].setTransform(mat[0], mat[1], mat[2], mat[3], mat[4] * binning, mat[5] * binning);
        invTransforms[index] = null;

    }

    public void addTransform(int index, AffineTransform T){
        if(transforms[index]==null) transforms[index]=new AffineTransform();
        transforms[index].concatenate(T);
        resetEulerMatrices();
        invTransforms[index]=null;
    }

    public void setCombineTransforms(boolean value){
        combine=value;
    }

    public void expandForAlignment(boolean value) { expand=value; }

    public AffineTransform getZeroTransform() {
        return zeroT;
    }

    public void setZeroTransform(AffineTransform zeroTransform) {
        this.zeroT = zeroTransform;
    }

    public void addToZeroTransform(AffineTransform T){
        zeroT.concatenate(T);
        resetEulerMatrices();
        for(int i=0;i<ts.getImageStackSize();i++){
            invTransforms[i]=null;
        }
    }

    public void setTiltAxis(double tiltaxis) {
        this.tiltaxis = tiltaxis;
    }

    @Override
    public float[] applyTransformOnImage(TiltSeries ts, int index) {
        AffineTransform T= getTransform(index);
        return applyTransformOnImage(ts,index,T);
    }

    public static float[] applyTransformOnImage(TiltSeries ts, int index, AffineTransform T){
        float[] unaligned=ts.getOriginalPixels(index);
        float[] aligned=new float[unaligned.length];
        ImageStatistics stats=ts.getImageStatistics(index);
        //Chrono time=new Chrono();
        //time.start();
        final double mean = stats.mean;
        final double stdDev = stats.stdDev;
        int width=ts.getWidth();
        int height= ts.getHeight();
        if (T.isIdentity()) {
            if (ts.isNormalized()) {
                aligned = new float[width * height];
                for (int i = 0; i < unaligned.length; i++) {
                    aligned[i] = (float) ((unaligned[i] - stats.mean) / stats.stdDev);
                }
            } else {
                //aligned = unaligned;
                aligned = new float[unaligned.length];
                System.arraycopy(unaligned, 0, aligned, 0, unaligned.length);
            }
        } else {
            aligned = new float[width * height];
            double centerx = (width) / 2.0;
            double centery = (height) / 2.0;
            //ImageStatistics stats = new FloatStatistics(new FloatProcessor(width, height, unaligned, null));

            //double[] TinvMatrix = new double[6];
            final AffineTransform Tinv;
            try {
                //AffineTransform Tinv = affT.createInverse();
                //T.createInverse().getMatrix(TinvMatrix);
                Tinv = T.createInverse();
            } catch (Exception e) {
                System.out.println("error in getting pixels!!!" + e);
                return null;
            }


            float pix;
            double xx;
            double yy;
            int jj;
            int ix0, iy0;
            double dx0, dy0;
            double fac4;// fac1 ,fac2, fac3 ;
            float value1, value2, value3, value4;
            int pos;
            //Point2D tmp;
            //Point2D res;
            double jc;
            final double[] ligne = new double[width * 2];
            final double[] res = new double[width * 2];
            for (int i = 0; i < width; i++) {
                ligne[i * 2] = i - centerx;
            }
            for (int j = 0; j < height; j++) {
                jj = j * width;
                jc = j - centery;
                for (int i = 1; i < ligne.length; i += 2) {
                    ligne[i] = jc;
                }
                Tinv.transform(ligne, 0, res, 0, width);
                //System.out.println("different line");
                for (int i = 0; i < width; i++) {
                    xx = res[i * 2] + centerx;
                    yy = res[i * 2 + 1] + centery;
                    ix0 = (int) xx;
                    iy0 = (int) yy;
                    dx0 = xx - ix0;
                    dy0 = yy - iy0;

                    //System.out.println("dx0="+dx0+" dy0="+dy0);
                    if (ix0 >= 0 && ix0 < width && iy0 >= 0 && iy0 < height) {
                        //en bas a gauche
                        if (ix0 == width - 1 || iy0 == height - 1) {
                            pix = unaligned[ix0 + iy0 * width];
                            if (ts.isNormalized()) {
                                pix = (float) ((pix - stats.mean) / stats.stdDev);
                            }
                        } else {
                            fac4 = (dx0 * dy0);
                            // fac1 = (1 - dx0 - dy0 + fac4);
                            //fac2 = (dx0 - fac4);
                            //fac3 = (dy0 - fac4);

                            /*value1 = unaligned[ix0 + iy0 * width];
                            value2 = unaligned[ix0 + 1 + iy0 * width];
                            value3 = unaligned[ix0 + (iy0 + 1) * width];
                            value4 = unaligned[ix0 + 1 + (iy0 + 1) * width];*/
                            value1 = unaligned[pos = ix0 + iy0 * width];
                            value3 = unaligned[pos + width];
                            value2 = unaligned[++pos];
                            value4 = unaligned[pos + width];
                            /*if (normalize) {

                                value1 = (float) ((value1 - mean) / stdDev);
                                value2 = (float) ((value2 - mean) / stdDev);
                                value3 = (float) ((value3 - mean) / stdDev);
                                value4 = (float) ((value4 - mean) / stdDev);
                            }*/
                            //pix = (float) (value1 * fac1 + value2 * fac2 + value3 * fac3 + value4 * fac4);
                            pix = (float) (value1 * (1 - dx0 - dy0 + fac4) + value2 * (dx0 - fac4) + value3 * (dy0 - fac4) + value4 * fac4);
                            if (ts.isNormalized()) pix = (float) ((pix - mean) / stdDev);
                        }
                        //result.putPixelValue(i, j, pix);
                        aligned[jj + i] = pix;

                        //}else if(ix0==-1||iy0==-1||ix0==width||iy0==height){
                        //    aligned[jj+i]=10000000;
                    } else if (ts.getFillType() == TiltSeries.FILL_AVG) {
                        if (!ts.isNormalized()) {
                            aligned[jj + i] = (float) stats.mean;
                        }
                    } else if (ts.getFillType() == TiltSeries.FILL_NaN) {
                        aligned[jj + i] = Float.NaN;
                    }
                }
            }
        }
        //time.stop();
        //System.out.println("getPixel time "+time.delay());
        return aligned;
    }



    /**
     * to get the transform of corresponding image
     * @param index             index of image in tiltSeries
     * @param combineTransforms are the transformations defined as a combination of transforms to align images 2 by 2
     * @return an AffineTransform corresponding to the transformation needed to align image
     */
    public AffineTransform getTransform(int index, boolean combineTransforms) {
        AffineTransform T = new AffineTransform();
        if (tiltAxisVertical) {
            T.rotate(Math.toRadians(-tiltaxis));
        }

        if (combineTransforms) {
            if (index < zeroindex) {
                T.concatenate(getZeroTransform());
                for (int i = zeroindex - 1; i >= index; i--) {
                    T.concatenate(transforms[i]);
                }
            } else if (index > zeroindex) {
                T.concatenate(getZeroTransform());
                for (int i = zeroindex; i < index; i++) {
                    T.concatenate(getInverseTransform(i));
                }
            } else {
                T.concatenate(getZeroTransform());
            }
        } else {
            T.concatenate(transforms[index]);
        }
        if (expand) {
            AffineTransform expandT = new AffineTransform();
            expandT.rotate(Math.toRadians(tiltaxis));
            //expandingFactor = 1 / Math.cos(Math.toRadians(ts.getTiltAngle(index)));
            double expandingFactor = 1 / Math.cos(Math.toRadians(ts.getTiltAngle(index)));
            expandT.scale(expandingFactor, 1);
            expandT.rotate(-Math.toRadians(tiltaxis));
            T.preConcatenate(expandT);
        }

        return T;
    }

    /**
     * get the inverse transform of specified image
     * @param index image number
     * @return inverse transform
     */
    public AffineTransform getInverseTransform(int index) {
        if (invTransforms[index] == null) {
            try {
                invTransforms[index] = transforms[index].createInverse();
            } catch (Exception e) {
                System.out.println("error in creating inverse transform " + e);
                return null;
            }
        }
        return invTransforms[index];
    }

    public void convertToFinalTransform() {
        AffineTransform[] results = new AffineTransform[transforms.length];
        for (int index = 0; index < transforms.length; index++) {
            results[index] = getTransform(index);
        }

        combine = false;
        for (int index = 0; index < transforms.length; index++) {
            transforms[index] = results[index];
        }
        zeroT.setToIdentity();


    }

    public void convertTolocalTransform() {
        combine = true;
        AffineTransform[] old = new AffineTransform[transforms.length];
        //System.out.println("call to convertToLocalTransform");
        for (int i = 0; i < transforms.length; i++) {
            //AffineTransform T = new AffineTransform();
            //setTransform(i, T);
            //old[i]=new AffineTransform(transform[i]);
            old[i] = transforms[i];
            transforms[i] = new AffineTransform();
        }

        setZeroTransform(old[ts.getZeroIndex()]);
        /*for (int i = 0; i < transform.length; i++){
           System.out.println(""+tiltAngles[i]+"old ("+old[i].getTranslateX()+", "+old[i].getTranslateY());
           System.out.println("transform ("+transform[i].getTranslateX()+", "+transform[i].getTranslateY());

       } */
        //update forward
        for (int i = ts.getZeroIndex() + 1; i < transforms.length; i++) {
            try {
                AffineTransform T = old[i].createInverse();
                AffineTransform T0 = getTransform(i - 1);
                T.concatenate(T0);
                transforms[i - 1].setTransform(T);
                invTransforms[i - 1] = null;
                //transform[i - 1]= T;
            } catch (Exception E) {
                E.printStackTrace();
            }
        }
        //update backward
        for (int i = ts.getZeroIndex() - 1; i >= 0; i--) {
            AffineTransform T = old[i];
            try {
                AffineTransform T0 = getTransform(i + 1).createInverse();
                T.preConcatenate(T0);
                setTransform(i, T);
            } catch (Exception E) {
                E.printStackTrace();
            }
        }

    }

    public DoubleMatrix2D getEulerMatrix(int index) {
        if (eulerMatrices[index] == null) {
            double ta = (tiltAxisVertical) ? 0 : tiltaxis;
            eulerMatrices[index] = MatrixUtils.eulerAngles2Matrix(ta, ts.getTiltAngle(index), -ta);
        }
        return eulerMatrices[index];
    }

    public void setEulerMatrix(int index, DoubleMatrix2D eulerMatrix) {
        eulerMatrices[index] = eulerMatrix;
    }

    public void resetEulerMatrices() {
        for (int i = 0; i < eulerMatrices.length; i++) {
            eulerMatrices[i] = null;
        }

    }

    /**
     * load from file it accept TomoJ csv or txt file and IMOD xf, prexg, prexf files
     *
     * @param path          path to file
     * @param binning       binning to apply, if no binning put to 1.0
     * @param options       options order:
     *                      final transform (true if the transforms as final transforms applied to each image,
     *                      false if the transforms are between each consecutive images),
     *                      combinewith existing (if true combine the loaded transform with the current one),
     *                      st file created from tiff2mrc (invert Y axis)
     * @throws Exception    if error while reading the file
     */
    public void loadFromFile(String path, double binning,  boolean... options) throws IOException {
        boolean finalTransforms=false;
        boolean combineWithExisting=false;
        boolean tiff2mrc=false;
        if (options != null && options.length > 0){
            finalTransforms = options[0];
            if(options.length>1) combineWithExisting = options[1];
            if(options.length>2) tiff2mrc=options[2];
        }
        BufferedReader in = new BufferedReader(new FileReader(path));
        if (path.endsWith(".xf") || path.endsWith(".prexg")) {
            System.out.println("open imod transform file");
            for (int i = 0; i < ts.getStackSize(); i++) {
                String line = in.readLine().trim();
                System.out.println(line);
                while (line.startsWith(";") || line.startsWith("#")) line = in.readLine().trim();
                String[] words = line.split("\\s+");
                System.out.println("#words:" + words.length);
                double[] tmp = new double[6];
                for (int j = 0; j < tmp.length; j++) {
                    tmp[j] = Double.valueOf(words[j]);
                    if (j > 3) tmp[j] *= binning;
                    if (tiff2mrc) tmp[j] = -tmp[j];
                }
                if (combineWithExisting) {
                    addTransform(i, new AffineTransform(tmp));
                } else setTransform(i, new AffineTransform(tmp));

            }
            convertTolocalTransform();
        } else if (path.endsWith(".prexf")) {
            System.out.println("open imod transform file");
            for (int i = 0; i < ts.getStackSize(); i++) {
                String line = in.readLine().trim();
                System.out.println(line);
                while (line.startsWith(";") || line.startsWith("#")) line = in.readLine().trim();
                String[] words = line.split("\\s+");
                System.out.println("#words:" + words.length);
                double[] tmp = new double[6];
                tmp[0] = Double.valueOf(words[0]);
                tmp[1] = Double.valueOf(words[1]);
                tmp[2] = Double.valueOf(words[2]);
                tmp[3] = Double.valueOf(words[3]);
                tmp[4] = -Double.valueOf(words[4]) * binning;
                if (options != null && options.length > 0 && options[0])
                    tmp[5] = Double.valueOf(words[5]) * binning;
                else tmp[5] = -Double.valueOf(words[5]) * binning;

                if (i == 0) setTransform(ts.getStackSize() - 1, new AffineTransform(tmp));
                else setTransform(i - 1, new AffineTransform(tmp));


            }
        } else {
            String separator = path.toLowerCase().endsWith("csv") ? ";" : "\\s+";
            System.out.println("stack size"+ts.getImageStackSize());
            for (int i = 0; i < ts.getStackSize(); i++) {
                String line = in.readLine();
                while (line.startsWith(";") || line.startsWith("#")) line = in.readLine();
                String[] words = line.split(separator);
                ts.setTiltAngle(i, Double.valueOf(words[0]));
                ts.setTiltAxis(Double.valueOf(words[1]));
                double[] tmp = new double[6];
                for (int j = 0; j < tmp.length; j++) {
                    tmp[j] = Double.valueOf(words[j + 2]);
                    if (j > 3) tmp[j] *= binning;
                }
                if (combineWithExisting) {
                    addTransform(i, new AffineTransform(tmp));
                } else setTransform(i, new AffineTransform(tmp));
            }

            if (finalTransforms) convertTolocalTransform();
            else {
                String line = in.readLine();
                String[] words = line.split(separator);
                double[] tmp = new double[6];
                for (int j = 0; j < tmp.length; j++) {
                    tmp[j] = Double.valueOf(words[j]);
                    if (j > 3) tmp[j] *= binning;
                }
                if (combineWithExisting) addToZeroTransform(new AffineTransform(tmp));
                else setZeroTransform(new AffineTransform(tmp));
            }
        }
        in.close();
    }

    @Override
    public void saveToFile(String path, boolean... options) throws IOException {
        boolean finalTransforms=(options!=null && options.length>0) ? options[0]:false;
        String separator = path.toLowerCase().endsWith("csv") ? ";" : "\t";
        boolean tiltvertical = ts.isPutTiltAxisVertical();
        ts.putTiltAxisVertical(false);
        tiltAxisVertical=false;
        BufferedWriter out = new BufferedWriter(new FileWriter(path));
        for (int i = 0; i < ts.getStackSize(); i++) {
            out.write("" + ts.getTiltAngle(i) + separator + ts.getTiltAxis() + separator);
            double[] t = new double[6];
            if (finalTransforms) getTransform(i, true).getMatrix(t);
            else getTransform(i, false).getMatrix(t);
            for (double aT : t) {
                out.write("" + aT + separator);
                //System.out.println("" + aT + "\t");
            }
            out.write("\n");
        }
        if (!finalTransforms) {
            double[] t = new double[6];
            getZeroTransform().getMatrix(t);
            for (double aT : t) {
                out.write("" + aT + separator);
                //System.out.println("" + aT + "\t");
            }
        }
        ts.putTiltAxisVertical(tiltvertical);
        tiltAxisVertical=tiltvertical;
        out.write("\n");
        out.flush();
        out.close();
    }

    public void setTiltAxisVertical(boolean tiltAxisVertical) {
        this.tiltAxisVertical = tiltAxisVertical;
    }
}
