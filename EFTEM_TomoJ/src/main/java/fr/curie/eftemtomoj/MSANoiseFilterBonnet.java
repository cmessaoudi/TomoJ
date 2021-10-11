package fr.curie.eftemtomoj;

import ij.IJ;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import java.text.DecimalFormat;

/**
 * MSA filter derived from a plugin from Noel Bonnet
 * User: C�dric
 * Date: 29 nov. 2010
 * Time: 14:43:42
 * To change this template use File | Settings | File Templates.
 */
public class MSANoiseFilterBonnet {


    private static DecimalFormat df = new DecimalFormat("0.0000");
    // %f default
    private String displ;
    private ImageStack stk_o, stk_n,
            stk_rec;
    // Pile d'images reconstruites
    private double[][] image;
    // UNSIGNED CHAR ?
    private int taillex, tailley, taillez, taille2;
    /*
         Dimension 1D and 2D
       */
    private int NIMA;
    /*
         number of images
       */
    private int nima2;
    /*
         square number of images
       */
    //private static short[] choix = new short[10];         /* Choice of option */
    // private static short[] axe0 = new short[3];           /* Flag for reconstitutions */
    private double[] SI, SJ, S, VP, FI, PSI;
    private double vptot, SITOT;
    private int KVVP;
    /*
         Number of factorial images
       */
    private boolean flagfi;
    private boolean[] axes_rec;


    /*
         Indicates if FI was computed
       */
    //private static short bufmin,bufmax,bufmoy;  /* Statistics for computed images */
    //private static int nbits;			/* Number of bits of images */
    //private static double [] moy;			// Moyenne des images

    public MSANoiseFilterBonnet(ImageStack astk_o, int nbaxes) {
        initialize(astk_o, nbaxes);
    }

    public void initialize(ImageStack astk_o, int nbaxes) {
        displ = "";
        stk_o = astk_o;
        // main
        flagfi = false;

        // fonction charge (ds l'ordre)
        NIMA = stk_o.getSize();
        image = new double[NIMA][];
        nima2 = NIMA * NIMA;
        taillex = stk_o.getWidth();
        tailley = stk_o.getHeight();
        taillez = 1;
        taille2 = taillex * tailley * taillez;

        stk_n = new ImageStack(taillex, tailley);

        for (int i = 0; i < NIMA; i++) {
            image[i] = new double[taille2];
            Object p = (stk_o.getProcessor(i + 1)).getPixels();
            int length = taillex * tailley;
            int type = 0;
            if (stk_o.getProcessor(i + 1) instanceof ShortProcessor) {
                type = 1;
            } else if (stk_o.getProcessor(i + 1) instanceof FloatProcessor) {
                type = 2;
            }
            for (int p_ind = 0; p_ind < length; p_ind++) {
                switch (type) {
                    case 0:
                        image[i][p_ind] = (double) ((((byte[]) p)[p_ind] + 256) % 256);
                        break;
                    // prend la partie >= 0
                    case 1:
                        image[i][p_ind] = (double) (((short[]) p)[p_ind] & 0xffff);
                        break;
                    case 2:
                    default:
                        image[i][p_ind] = (double) ((float[]) p)[p_ind];
                }
            }
        }
        KVVP = nbaxes;
        if (KVVP > NIMA) {
            KVVP = NIMA;
        }

        // function alloc
        SI = new double[taille2];
        SJ = new double[NIMA];
        S = new double[nima2];
        FI = new double[KVVP * taille2];
        PSI = new double[NIMA * KVVP];
        VP = new double[NIMA];

        axes_rec = new boolean[nbaxes];
        for (int i = 0; i < axes_rec.length; i++) axes_rec[i] = true;
    }

//-----------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_calpsi() {
        double prod_scal;
        double norme;
        double norme2;
        double moy1;
        double moy2;
        double sd1;
        double sd2;
        double cc;
        vptot -= 1;
        for (int i = 0; i < NIMA; i++) {
            if (SJ[i] < 1.0e-6) {
                IJ.showStatus("SJ(" + i + ") LOWER THAN 10**(-6) !!! stop PCA computing");
                System.err.println("SJ(" + i + ") LOWER THAN 10**(-6) !!! stop PCA computing");
                //System.exit(-1); 	// why ?
                return;
            } else {
                SJ[i] /= SITOT;
            }
        }

        displ += "\n Image coordinates";
        displ += "\n------------------";
        df.applyPattern("0.000");
        // "%.3f"
        for (int i = 0; i < NIMA; i++) {
            displ += "\n     " + i + " \t ";
            for (int k = 0; k < KVVP; k++) {
                PSI[i * KVVP + k] = S[i * NIMA + k];
                displ += df.format(PSI[i * KVVP + k]) + " \t ";
            }
        }
        df.applyPattern("0.0000");
        // %f
        // Scalar product (just for tests
        for (int k = 0; k < KVVP; k++) {
            norme = 0;
            moy1 = 0;
            for (int i = 0; i < NIMA; i++) {
                moy1 += S[i * NIMA + k];
                norme += S[i * NIMA + k] * S[i * NIMA + k];
            }
            moy1 /= (double) NIMA;
            sd1 = norme / NIMA - moy1 * moy1;
            for (int j = k; j < KVVP; j++) {
                moy2 = 0;
                norme2 = 0;
                prod_scal = 0;
                for (int i = 0; i < NIMA; i++) {
                    moy2 += S[i * NIMA + j];
                    prod_scal += S[i * NIMA + k] * S[i * NIMA + j];
                    norme2 += S[i * NIMA + j] * S[i * NIMA + j];
                }
                moy2 /= (double) NIMA;
                sd2 = norme2 / NIMA - moy2 * moy2;
                cc = (prod_scal / NIMA - moy1 * moy2) / Math.sqrt(sd1 * sd2);
                // displ+="\n axe "+k+" avec axe "+j+"  Prod_scal= "+df.format(prod_scal)+" prod_scal_norme="+df.format((prod_scal/Math.sqrt(norme*norme2)))+" cc="+df.format(cc);
            }
        }
        ima_matcorr();
    }


//-----------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_centering_calpsi() {
        double prod_scal;
        double norme;
        double norme2;
        double moy1;
        double moy2;
        double sd1;
        double sd2;
        double cc;
        vptot -= 1;
        for (int i = 0; i < NIMA; i++) {
            if (SJ[i] < 1.0e-6) {
                IJ.showMessage("Error", "SJ(" + i + ") LOWER THAN 10**(-6) !!! arret de ImageJ");
                //System.exit(-1); 	// why ?
                return;
            } else {
                SJ[i] /= SITOT;
            }
        }

        displ += "\n Image coordinates";
        displ += "\n------------------";
        df.applyPattern("0.000");
        // "%.3f"
        for (int i = 0; i < NIMA; i++) {
            displ += "\n     " + i + " \t ";
            for (int k = 0; k < KVVP; k++) {
                PSI[i * KVVP + k] = S[i * NIMA + k];
                displ += df.format(PSI[i * KVVP + k]) + " \t ";
            }
        }
        df.applyPattern("0.0000");
        // %f
        // Scalar product (just for tests
        for (int k = 0; k < KVVP; k++) {
            norme = 0;
            moy1 = 0;
            for (int i = 0; i < NIMA; i++) {
                moy1 += S[i * NIMA + k];
                norme += S[i * NIMA + k] * S[i * NIMA + k];
            }
            moy1 /= (double) NIMA;
            sd1 = norme / NIMA - moy1 * moy1;
            for (int j = k; j < KVVP; j++) {
                moy2 = 0;
                norme2 = 0;
                prod_scal = 0;
                for (int i = 0; i < NIMA; i++) {
                    moy2 += S[i * NIMA + j];
                    prod_scal += S[i * NIMA + k] * S[i * NIMA + j];
                    norme2 += S[i * NIMA + j] * S[i * NIMA + j];
                }
                moy2 /= (double) NIMA;
                sd2 = norme2 / NIMA - moy2 * moy2;
                cc = (prod_scal / NIMA - moy1 * moy2) / Math.sqrt(sd1 * sd2);
                // displ+="\n axe "+k+" avec axe "+j+"  Prod_scal= "+df.format(prod_scal)+" prod_scal_norme="+df.format((prod_scal/Math.sqrt(norme*norme2)))+" cc="+df.format(cc);
            }
        }
        ima_matcorr();
    }


//------------------------------------------------------

    /**
     * Description of the Method
     */
    public void CA_calpsi() {
        int i;
        int k;
        int l;
        /*
              Normalization of SJ
            */
        vptot -= 1;
        for (i = 0; i < NIMA; i++) {
            if (SJ[i] < 1.0e-6) {
                IJ.showMessage("Error", "SJ(" + i + ") LOWER THAN 10**(-6) !!! arret de ImageJ");
                //System.exit(-1);
                return;
            } else {
                SJ[i] /= SITOT;
            }
        }
        displ += "\n Image coordinates";
        displ += "\n------------------";
        for (i = 0; i < NIMA; i++) {
            displ += "\n     " + i + " \t ";
            for (k = 0; k < KVVP; k++) {
                l = k + 1;
                PSI[i * KVVP + k] = S[i * NIMA + l] / Math.sqrt((double) SJ[i]) * Math.sqrt((double) VP[l]);
                displ += df.format(PSI[i * KVVP + k]) + " \t ";
            }
        }
    }


//------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_calfi() {
        // displ+="\n CALCUL DE FI";
        for (int k = 0; k < KVVP; k++) {
            // int l=k;
            int indice = k * taille2;
            for (int j = 0; j < taille2; j++) {
                if (SI[j] != 0) {
                    FI[indice + j] = 0.0;
                    for (int i = 0; i < NIMA; i++) {
                        FI[indice + j] += (double) image[i][j] * S[i * NIMA + k];
                    }
                }
            }
        }
        flagfi = true;
    }


//-------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_centering_calfi() {
        // displ+="\n CALCUL DE FI";
        for (int k = 0; k < KVVP; k++) {
            // int l=k;
            int indice = k * taille2;
            for (int j = 0; j < taille2; j++) {
                if (SI[j] != 0) {
                    FI[indice + j] = 0.0;
                    for (int i = 0; i < NIMA; i++) {
                        FI[indice + j] += ((double) image[i][j] - SJ[i]) * S[i * NIMA + k];
                    }
                }
            }
        }
        flagfi = true;
    }


//---------------------------------------------------------

    /**
     * Description of the Method
     */
    public void CA_calfi() {
        int i;
        int j;
        int k;
        int l;
        int indice;
        // displ+="\n CALCUL DE FI";
        for (k = 0; k < KVVP; k++) {
            l = k + 1;
            indice = k * taille2;
            for (j = 0; j < taille2; j++) {
                if (SI[j] != 0) {
                    FI[indice + j] = 0.;
                    for (i = 0; i < NIMA; i++) {
                        if (SJ[i] != 0) {
                            FI[indice + j] += (double) image[i][j] * S[i * NIMA + l] / Math.sqrt(SJ[i]);
                        }
                    }
                    FI[indice + j] /= SI[j];
                }
            }
        }
        flagfi = true;
    }


//------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_project() {
        // char nom[30],nom2[30];
        double val;
        // char nom[30],nom2[30];
        double fimin;
        // char nom[30],nom2[30];
        double fimax;
        // char nom[30],nom2[30];
        double coef;
        int j;
        int k;
        int indice;
        double buffer[];
        // WARNING: UNSIGNED

        buffer = new double[taille2];
        // Computes FI if it wasn't done before
        if (flagfi == false) {
            PCA_calfi();
        }

        displ += "\n PCA: Statistics of principal images (before conversion to 8 bits)";
        displ += "\n -----------------------------------------------------------------";
        for (k = 0; k < KVVP; k++) {
            indice = k * taille2;
            /*
                   Output on 8 bits (19/3/1992)
                 */
            fimin = Double.MAX_VALUE;
            fimax = Double.MIN_VALUE;
            for (j = 0; j < taille2; j++) {
                val = FI[indice + j];
                if (val < fimin) {
                    fimin = val;
                }
                if (val > fimax) {
                    fimax = val;
                }
            }
            displ += "\n" + "Projection " + k + ":";
            displ += " \t min=" + df.format(fimin) + " \t max=" + df.format(fimax);
            coef = 254. / (fimax - fimin);
            for (j = 0; j < taille2; j++) {
                //val = (FI[indice + j] - fimin) * coef;
                //short preca = (short) val;
                //buffer[j] = (short) (preca % 256);
                buffer[j] = FI[indice + j];
            }
            FloatProcessor bpbuff = new FloatProcessor(taillex, tailley);
            for (int imx = 0; imx < taillex; imx++) {
                for (int imy = 0; imy < tailley; imy++) {
                    bpbuff.putPixelValue(imx, imy, buffer[imy * taillex + imx]);
                }
            }
            stk_n.addSlice("Projection " + k, bpbuff);
        }
    }


//---------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_centering_project() {
        // char nom[30],nom2[30];
        double val;
        // char nom[30],nom2[30];
        double fimin;
        // char nom[30],nom2[30];
        double fimax;
        // char nom[30],nom2[30];
        double coef;
        int j;
        int k;
        int indice;
        double buffer[];
        // WARNING: UNSIGNED

        buffer = new double[taille2];
        // Computes FI if it wasn't done before
        if (flagfi == false) {
            PCA_centering_calfi();
        }

        displ += "\n PCA_centering: Statistics of principal images (before conversion to 8 bits)";
        displ += "\n -----------------------------------------------------------------";
        for (k = 0; k < KVVP; k++) {
            indice = k * taille2;
            /*
                   Output on 8 bits (19/3/1992)
                 */
            fimin = 32767.;
            fimax = -32768.;
            for (j = 0; j < taille2; j++) {
                val = FI[indice + j];
                if (val < fimin) {
                    fimin = val;
                }
                if (val > fimax) {
                    fimax = val;
                }
            }
            displ += "\n" + "Projection " + k + ":";
            displ += " \t min=" + df.format(fimin) + " \t max=" + df.format(fimax);
            coef = 254. / (fimax - fimin);
            for (j = 0; j < taille2; j++) {
                //val = (FI[indice + j] - fimin) * coef;
                //short preca = (short) val;
                //buffer[j] = (short) (preca % 256);
                buffer[j] = FI[indice + j];
            }
            FloatProcessor bpbuff = new FloatProcessor(taillex, tailley);
            for (int imx = 0; imx < taillex; imx++) {
                for (int imy = 0; imy < tailley; imy++) {
                    bpbuff.putPixelValue(imx, imy, buffer[imy * taillex + imx]);
                }
            }
            stk_n.addSlice("Projection " + k, bpbuff);
        }
    }


//------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void CA_project() {
        int j;
        int k;
        double val;
        double fimin;
        double fimax;
        double coef;
        int indice;
        double buffer[];

        buffer = new double[taille2];
        // Computes FI if it wasn't done previously
        if (flagfi == false) {
            CA_calfi();
        }

        displ += "\n CA: Statistics of factorial images (before conversion to 8 bits)";
        displ += "\n ----------------------------------------------------------------";
        for (k = 0; k < KVVP; k++) {
            indice = k * taille2;
            displ += "\n" + "Projection " + k + ":";
            /*
                   Output on 8 bits (18/3/1993)
                 */
            fimin = 32767.;
            fimax = -32768.;
            for (j = 0; j < taille2; j++) {
                val = FI[indice + j];
                if (val < fimin) {
                    fimin = val;
                }
                if (val > fimax) {
                    fimax = val;
                }
            }
            displ += " \t min=" + df.format(fimin) + " \t max=" + df.format(fimax);
            coef = 254. / (fimax - fimin);
            for (j = 0; j < taille2; j++) {
                //val = (FI[indice + j] - fimin) * coef;
                //short preca = (short) val;
                //buffer[j] = (short) (preca % 256);
                buffer[j] = FI[indice + j];
            }
            FloatProcessor bpbuff = new FloatProcessor(taillex, tailley);
            for (int imx = 0; imx < taillex; imx++) {
                for (int imy = 0; imy < tailley; imy++) {
                    bpbuff.putPixelValue(imx, imy, buffer[imy * taillex + imx]);
                }
            }
            stk_n.addSlice("Projection " + k, bpbuff);
        }
    }


//--------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_variance() {
        double x;
        double y;
        double val;

        // initialiZation
        for (int j = 0; j < taille2; j++) {
            SI[j] = 0;
        }
        //marges, matrice S et de sa trace
        for (int i = 0; i < NIMA; i++) {
            SJ[i] = 0;
            for (int j = 0; j < taille2; j++) {
                x = image[i][j];
                // peut etre j,i ???
                SI[j] += x;
                SJ[i] += x;
            }
        }
        displ += "\n Matrix S :";
        displ += "\n ----------";
        for (int i = 0; i < NIMA; i++) {
            for (int l = 0; l <= i; l++) {
                val = 0;
                for (int j = 0; j < taille2; j++) {
                    x = image[i][j];
                    y = image[l][j];
                    val += x * y;
                }
                S[i * NIMA + l] = val;
            }
        }
        for (int i = 0; i < NIMA; i++) {
            for (int l = 0; l <= i; l++) {
                S[i * NIMA + l] /= taille2;
                S[l * NIMA + i] = S[i * NIMA + l];
            }
        }
        for (int i = 0; i < NIMA; i++) {
            displ += "\n";
            for (int l = 0; l < NIMA; l++) {
                displ += " " + df.format(S[i * NIMA + l]) + " \t ";
            }
        }
        vptot = 0;
        for (int i = 0; i < NIMA; i++) {
            for (int l = 0; l < NIMA; l++) {
                if (i == l) {
                    vptot += S[i * NIMA + l];
                }
            }
        }
        SITOT = 0;
        displ += "\n Before diagonalization : TRACE =" + df.format(vptot);
        for (int i = 0; i < NIMA; i++) {
            SITOT += SJ[i];
        }
        // displ += "\n Sum of intensites="+df.format(SITOT);
    }


//------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_centering_variance() {
        double x;
        double y;
        double val;

        // initialiZation
        for (int j = 0; j < taille2; j++) {
            SI[j] = 0;
        }
        //marges, matrice S et de sa trace
        for (int i = 0; i < NIMA; i++) {
            SJ[i] = 0;
            for (int j = 0; j < taille2; j++) {
                x = image[i][j];
                // peut etre j,i ???
                SI[j] += x;
                SJ[i] += x;
            }
        }
        displ += "\n Matrix S :";
        displ += "\n ----------";
        for (int i = 0; i < NIMA; i++) {
            for (int l = 0; l <= i; l++) {
                val = 0;
                for (int j = 0; j < taille2; j++) {
                    x = image[i][j] - SJ[i] / taille2;
                    // centering
                    y = image[l][j] - SJ[l] / taille2;
                    // centering
                    val += x * y;
                }
                S[i * NIMA + l] = val;
            }
        }
        for (int i = 0; i < NIMA; i++) {
            for (int l = 0; l <= i; l++) {
                S[i * NIMA + l] /= taille2;
                S[l * NIMA + i] = S[i * NIMA + l];
            }
        }
        for (int i = 0; i < NIMA; i++) {
            displ += "\n";
            for (int l = 0; l < NIMA; l++) {
                displ += " " + df.format(S[i * NIMA + l]) + " \t ";
            }
        }
        vptot = 0;
        for (int i = 0; i < NIMA; i++) {
            for (int l = 0; l < NIMA; l++) {
                if (i == l) {
                    vptot += S[i * NIMA + l];
                }
            }
        }
        SITOT = 0;
        displ += "\n Before diagonalization : TRACE =" + df.format(vptot);
        for (int i = 0; i < NIMA; i++) {
            SITOT += SJ[i];
        }
        // displ += "\n Sum of intensites="+df.format(SITOT);
    }


//------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void CA_variance() {
        int i;
        int j;
        int l;
        double x;
        double y;
        double sd;
        double val;

        /*
              INITIALISATIONS
            */
        for (j = 0; j < taille2; j++) {
            SI[j] = 0;
        }

        /*
              CALCUL DES MARGES, DE LA MATRICE S ET DE SA TRACE
            */
        for (i = 0; i < NIMA; i++) {
            SJ[i] = 0;
            for (j = 0; j < taille2; j++) {
                x = image[i][j];
                SI[j] += x;
                SJ[i] += x;
            }
        }
        displ += "\n Matrix S :";
        displ += "\n ----------";
        for (i = 0; i < NIMA; i++) {
            for (l = 0; l <= i; l++) {
                val = 0;
                for (j = 0; j < taille2; j++) {
                    x = image[i][j];
                    y = image[l][j];
                    val += (SI[j] != 0 ? x * y / SI[j] : 0);
                }
                S[i * NIMA + l] = val;
            }
        }
        for (i = 0; i < NIMA; i++) {
            for (l = 0; l <= i; l++) {
                sd = (double) SJ[i] * SJ[l];
                S[i * NIMA + l] /= Math.sqrt(sd);
                S[l * NIMA + i] = S[i * NIMA + l];
            }
        }
        for (i = 0; i < NIMA; i++) {
            displ += "\n";
            for (l = 0; l < NIMA; l++) {
                displ += " " + df.format(S[i * NIMA + l]) + " \t ";
            }
        }
        vptot = 0;
        for (i = 0; i < NIMA; i++) {
            for (l = 0; l < NIMA; l++) {
                if (i == l) {
                    vptot += S[i * NIMA + l];
                }
            }
        }
        displ += "\n Before diagonalization : TRACE =" + df.format(vptot);
        SITOT = 0;
        for (i = 0; i < NIMA; i++) {
            SITOT += SJ[i];
        }
        // displ += "\n Sum of intensites="+df.format(SITOT);
    }


//-------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void inverse() {
        // invert S
        // This is not "nice" programming, but ... it works
        double epsil;
        // invert S
        // This is not "nice" programming, but ... it works
        double w2;
        // invert S
        // This is not "nice" programming, but ... it works
        double w22;
        // invert S
        // This is not "nice" programming, but ... it works
        double w2a;
        // invert S
        // This is not "nice" programming, but ... it works
        double ww;
        // invert S
        // This is not "nice" programming, but ... it works
        double wwa;
        // invert S
        // This is not "nice" programming, but ... it works
        double ep;
        // invert S
        // This is not "nice" programming, but ... it works
        double a;
        // invert S
        // This is not "nice" programming, but ... it works
        double b;
        // invert S
        // This is not "nice" programming, but ... it works
        double tteta;
        // invert S
        // This is not "nice" programming, but ... it works
        double cteta;
        // invert S
        // This is not "nice" programming, but ... it works
        double steta;
        // invert S
        // This is not "nice" programming, but ... it works
        double ateta;
        // invert S
        // This is not "nice" programming, but ... it works
        double flip;
        int ni;
        int ki;
        int k1;
        int n1;
        int kp;
        int k;
        int l;

        epsil = 0.0000000001;
        w2 = 0.;
        for (l = 0; l < NIMA; l++) {
            for (k = 0; k < NIMA; k++) {
                w2 += S[l * NIMA + k] * S[l * NIMA + k];
            }
        }
        ep = epsil * w2 / NIMA;
        ni = NIMA * (NIMA - 1) / 2;
        ki = ni;
        n1 = NIMA - 1;
        boucle_princ:
        while (ki > 0) {
            for (k = 0; k < n1; k++) {
                k1 = k + 1;
                boucle_inte:
                for (kp = k1; kp < NIMA; kp++) {
                    w2 = 0.;
                    ww = 0.;
                    for (l = 0; l < NIMA; l++) {
                        a = S[l * NIMA + k];
                        b = S[l * NIMA + kp];
                        w2 += a * b;
                        ww += (a + b) * (a - b);
                    }
                    w22 = w2 * 2.;
                    w2a = Math.abs(w22);
                    if (w2a >= ep || ww < 0.) {
                        wwa = Math.abs(ww);
                    } else {
                        ki--;
                        if (ki <= 0) {
                            break boucle_princ;
                        }
                        // get out of 'while'
                        else {
                            continue boucle_inte;
                        }
                    }
                    if (w2a <= wwa) {
                        tteta = w2a / wwa;
                        cteta = 1. / Math.sqrt(1. + tteta * tteta);
                        steta = tteta * cteta;
                    } else {
                        ateta = wwa / w2a;
                        steta = 1. / Math.sqrt(1. + ateta * ateta);
                        cteta = ateta * steta;
                    }
                    cteta = Math.sqrt((1. + cteta) / 2.);
                    steta /= (2. * cteta);
                    if (ww < 0.) {
                        flip = cteta;
                        cteta = steta;
                        steta = flip;
                    }
                    if (w22 < 0.) {
                        steta = -steta;
                    }
                    for (l = 0; l < NIMA; l++) {
                        flip = S[l * NIMA + k];
                        S[l * NIMA + k] = flip * cteta + S[l * NIMA + kp] * steta;
                        S[l * NIMA + kp] = -flip * steta + S[l * NIMA + kp] * cteta;
                    }
                    ki = ni;
                }
            }
        }
        for (k = 0; k < NIMA; k++) {
            VP[k] = 0.;
            for (l = 0; l < NIMA; l++) {
                VP[k] += S[l * NIMA + k] * S[l * NIMA + k];
            }
            VP[k] = Math.sqrt(VP[k]);
        }
        for (k = 0; k < NIMA; k++) {
            for (l = 0; l < NIMA; l++) {
                S[l * NIMA + k] /= VP[k];
            }
        }
        vptot = 0;
        for (k = 0; k < NIMA; k++) {
            vptot += VP[k];
        }
        displ += "\n After diagonalization : TRACE =" + df.format(vptot);
    }


//-----------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_result() {
        double som;
        displ += "\nEigenvalue \t Log(Eigenvalue)      Percentage    Cumulated Percentage";
        displ += "\n---------------------------------------------------------------------";
        som = 0;
        displ += "\n" + df.format(VP[0]);
        vptot -= VP[0];
        for (int k = 0; k < KVVP - 1; k++) {
            int l = k + 1;
            som += VP[l] / vptot;
            displ += "\n" + df.format(VP[l]) + " \t " + df.format(Math.log(VP[l])) + " \t " + df.format(VP[l] / vptot * 100) + " \t " + df.format(som * 100);
        }
    }


//------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void PCA_centering_result() {
        double som;
        displ += "\nEigenvalue \t Log(Eigenvalue)      Percentage    Cumulated Percentage";
        displ += "\n---------------------------------------------------------------------";
        som = 0;
        displ += "\n" + df.format(VP[0]);
        vptot -= VP[0];
        for (int k = 0; k < KVVP - 1; k++) {
            int l = k + 1;
            som += VP[l] / vptot;
            displ += "\n" + df.format(VP[l]) + " \t " + df.format(Math.log(VP[l])) + " \t " + df.format(VP[l] / vptot * 100) + " \t " + df.format(som * 100);
        }
    }


//---------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public void CA_result() {
        int k;
        int l;
        double som;
        displ += "\nEigenvalue  \t Log(Eigenvalue)      Percentage    Cumulated Percentage";
        displ += "\n----------------------------------------------------------------------";
        som = 0;
        displ += "\n" + df.format(VP[0]);
        vptot -= 1.;
        for (k = 0; k < KVVP; k++) {
            l = k + 1;
            som += VP[l] / vptot;
            displ += "\n" + df.format(VP[l]) + "           " + df.format(Math.log(VP[l])) + "           " + df.format(VP[l] / vptot * 100) + " \t " + df.format(som * 100);
        }
    }


//------------------------------------------------ still used ???

    /**
     * Description of the Method
     */
    public void ima_matcorr() {
        int i;
        int j;
        int k;
        int m;
        int n;
        int indice;
        double Smin = 1.e30;
        double Smax = -1.e30;
        double coef;
        short val;
        // ATTENTION unsigned
        short[] buffer = new short[65536];
        // ATTENTION unsigned

        for (i = 0; i < NIMA * NIMA; i++) {
            if (S[i] < Smin) {
                Smin = S[i];
            }
            if (S[i] > Smax) {
                Smax = S[i];
            }
        }
        // displ+="\n Valeur mini de S ="+df.format(Smin)+"  Valeur maxi de S = "+df.format(Smax);

        coef = 255. / (Smax - Smin);
        k = 256 / NIMA;
        for (i = 0; i < 65536; i++) {
            buffer[i] = 0;
        }
        for (j = 0; j < NIMA; j++) {
            for (i = 0; i < NIMA; i++) {
                // CAST UNSIGNED CHAR
                short preca = (short) ((S[j * NIMA + i] - Smin) * coef);
                val = (short) (preca % 256);
                for (m = 0; m < k; m++) {
                    indice = (j * k + m) * 256 + i * k;
                    for (n = 0; n < k; n++) {
                        buffer[indice + n] = val;
                    }
                }
            }
        }
    }


//-----------------------------------------------------------------------------

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public String PCA() {

        // method PCA
        PCA_variance();
        inverse();
        PCA_result();
        PCA_calpsi();
        PCA_project();
        return displ;
    }


//----------------------------------------------------------------------------

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public String PCA_centering() {

        // method PCA_centering
        PCA_centering_variance();
        inverse();
        PCA_centering_result();
        PCA_centering_calpsi();
        PCA_centering_project();
        return displ;
    }


//---------------------------------------------------------------------------

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public String CA() {
        // method CA
        CA_variance();
        inverse();
        CA_result();
        CA_calpsi();
        CA_project();
        return displ;
    }


//-------------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public ImageStack PCA_rec() {
        System.out.println("Reconstruction PCA");
        double buffer[];
        buffer = new double[taille2];


        stk_rec = new ImageStack(taillex, tailley);

        for (int i = 0; i < NIMA; i++) {
            String axes = "";
            // for displaying the axes used for reconstruction
            for (int j = 0; j < taille2; j++) {
                buffer[j] = 0;
            }

            for (int k = 0; k < KVVP; k++) {
                // axes
                if (axes_rec[k]) {
                    // axes selected for reconstruction

                    axes = axes + k;
                    for (int j = 0; j < taille2; j++) {
                        buffer[j] += PSI[i * KVVP + k] * FI[k * taille2 + j];
                    }
                }
            }

            FloatProcessor bpbuff = new FloatProcessor(taillex, tailley);
            for (int imx = 0; imx < taillex; imx++) {
                for (int imy = 0; imy < tailley; imy++) {
                    bpbuff.putPixelValue(imx, imy, buffer[imy * taillex + imx]);
                }
            }
            stk_rec.addSlice("Reconstruction " + i + "; axes=" + axes, bpbuff);
        }
        System.out.println("End of Reconstruction PCA");
//return displ;
        return stk_rec;
    }


//----------------------------------------------------------------------

    /**
     * Description of the Method
     *
     * @param axes_rec Description of the Parameter
     */
    public ImageStack PCA_centering_rec(boolean axes_rec[]) {
        System.out.println("Reconstruction PCA_centering");

        double buffer[];
        buffer = new double[taille2];


        stk_rec = new ImageStack(taillex, tailley);

        for (int i = 0; i < NIMA; i++) {
            String axes = "";
            // for displaying the axes used for reconstruction
            for (int j = 0; j < taille2; j++) {
                buffer[j] = 0;
            }

            for (int k = 0; k < KVVP; k++) {
                // axes
                if (axes_rec[k]) {
                    // axes selected for reconstruction

                    axes = axes + k;
                    for (int j = 0; j < taille2; j++) {
                        buffer[j] += PSI[i * KVVP + k] * FI[k * taille2 + j] + SJ[i];
                    }
                }
            }

            FloatProcessor bpbuff = new FloatProcessor(taillex, tailley);
            for (int imx = 0; imx < taillex; imx++) {
                for (int imy = 0; imy < tailley; imy++) {
                    bpbuff.putPixelValue(imx, imy, buffer[imy * taillex + imx]);
                }
            }
            stk_rec.addSlice("Reconstruction " + i + "; axes=" + axes, bpbuff);
        }
        System.out.println("End of Reconstruction PCA_centering");
//return displ;
        return stk_rec;
    }


//-------------------------------------------------------------------------

    /**
     * Description of the Method
     */
    public ImageStack CA_rec() {
        System.out.println("Reconstruction CA");

        double buffer_double[];
        buffer_double = new double[taille2];

        for (int k = 0; k < KVVP; k++) {
            System.out.println("k=" + k + " rec=" + axes_rec[k]);
        }

        stk_rec = new ImageStack(taillex, tailley);
        if (axes_rec[0]) {
            System.out.println("Reconstruction with the 'trivial' axis 0");
        } else {
            System.out.println("Reconstruction without the 'trivial' axis 0");
        }

        for (int i = 0; i < NIMA; i++) {
            // images

            String axes = "";
            // for displaying the axes used for reconstruction

            if (axes_rec[0]) {
                // Initialisation of reconstruction result (buffer)

                for (int j = 0; j < taille2; j++) {
                    buffer_double[j] = 1.;
                }
                axes = axes + "0";
            } else {
                for (int j = 0; j < taille2; j++) {
                    buffer_double[j] = 0.;
                }
            }

            for (int k = 1; k < KVVP; k++) {
                // axes (other than number 0)
                if (axes_rec[k]) {
                    // axes selected for reconstruction

                    axes = axes + k;
                    int l = k - 1;
                    // Cope with the change of numbering in CA
                    for (int j = 0; j < taille2; j++) {
                        // pixels

                        buffer_double[j] += (PSI[i * KVVP] * FI[l * taille2 + j] / Math.sqrt((double) VP[k]));
                    }
                }
            }

            for (int j = 0; j < taille2; j++) {
                buffer_double[j] = (buffer_double[j] * SI[j] * SJ[i]);
            }

            // Filling the stack
            FloatProcessor bpbuff = new FloatProcessor(taillex, tailley);
            for (int imx = 0; imx < taillex; imx++) {
                for (int imy = 0; imy < tailley; imy++) {
                    bpbuff.putPixelValue(imx, imy, buffer_double[imy * taillex + imx]);
                }
            }
            stk_rec.addSlice("Reconstruction " + i + "; axes=" + axes, bpbuff);
        }
        System.out.println("End of Reconstruction CA");
//return displ;
        return stk_rec;
    }


    public double[] getEigenValues() {
        return VP;
    }

    public double getEigenValueTotal() {
        return vptot;
    }

    public ImageStack getEigenVectorImages() {
        return stk_n;
    }

    public int getNumberOfAxes() {
        return KVVP;
    }

    public int getWidth() {
        return stk_o.getWidth();
    }

    public int getHeight() {
        return stk_o.getHeight();
    }

    public void setSelected(int axisIndex, boolean value) {
        axes_rec[axisIndex] = value;
    }

    public boolean isSelected(int axisIndex) {
        return axes_rec[axisIndex];
    }
}
// end of class Compute
