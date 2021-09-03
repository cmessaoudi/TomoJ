package fr.curie.tomoj.landmarks;

import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import fr.curie.utils.MatrixUtils;

import static java.lang.Math.toRadians;

public class ProjectionMatrix {
    double theta;
    DoubleMatrix2D Ai;
    public double psii;
    DoubleMatrix2D Rthetauaxis;
    double rot, tilt;
    DoubleMatrix2D Di;
    DoubleMatrix2D Ri;
    public double mi, ti, si, deltai;
    boolean computeAi = true;
    DoubleMatrix2D RiDi;
    DoubleMatrix2D RiDi_inv;
    DoubleMatrix2D euler;
    DoubleMatrix2D Xi;
    DoubleMatrix2D XiL;
    DoubleMatrix2D XiR;
    DoubleMatrix1D axis;

    boolean setTiltAxisVertical = true;

    /**
     * @param theta tilt angle of image corresponding to this projection Matrix (image i)
     */
    ProjectionMatrix(double theta) {
        this.theta = theta;
        mi = 1;
        ti = 1;
        si = 1;
        deltai = 0;
        //computeAi();
        Ai = DoubleFactory2D.dense.make(2, 3);
        Di = DoubleFactory2D.dense.make(3, 3);
        Ri = DoubleFactory2D.dense.make(3, 3);
        Rthetauaxis = DoubleFactory2D.dense.make(3, 3);
    }

    public ProjectionMatrix copy() {
        ProjectionMatrix result = new ProjectionMatrix(this.theta);
        result.copy(this);
        return result;
    }

    public void copy(ProjectionMatrix other) {
        theta = other.theta;
        mi = other.mi;
        ti = other.ti;
        si = other.si;
        deltai = other.deltai;
        psii = other.psii;
        Rthetauaxis.assign(other.Rthetauaxis);
        axis = other.axis;
        Ai.assign(other.Ai);
        Di.assign(other.Di);
        Ri.assign(other.Ri);
    }

    public void setUaxis(double uaxis_alpha, double uaxis_beta) {
        rot = uaxis_alpha;
        tilt = uaxis_beta;
        axis = MatrixUtils.eulerDirection(toRadians(uaxis_alpha), toRadians(uaxis_beta));
        //Rthetauaxis = MatrixUtils.rotation3DMatrix(-theta, axis);      //modified version
        Rthetauaxis = MatrixUtils.rotation3DMatrix(theta, axis);
        euler = null;
        //System.out.println("axis ("+uaxis_alpha+", "+uaxis_beta+") : "+axis+"\ntheta: "+theta+"\nRthetauaxis: "+Rthetauaxis);
    }

    public double getTheta() {
        return theta;
    }

    public void setTheta(double theta) {
        this.theta = theta;
        Rthetauaxis = MatrixUtils.rotation3DMatrix(theta, axis);
        //Xi=null;
        computeAi = true;
        euler = null;
    }

    public boolean isSetTiltAxisVertical() {
        return setTiltAxisVertical;
    }

    public void setTiltAxisVertical(boolean setTiltAxisVertical) {
        if(setTiltAxisVertical!=this.setTiltAxisVertical) {
            euler=null;
            this.setTiltAxisVertical = setTiltAxisVertical;
        }
    }

    public void clearAi() {
        Xi = null;
        XiR = null;
        XiL = null;
        computeAi = true;
    }

    public DoubleMatrix2D getAi() {
        if (computeAi) computeAi();
        return Ai;
    }


    public DoubleMatrix2D getRiDi() {
        if (computeAi) computeAi();
        if (RiDi == null) {
            computeRiDi();
        }

        return RiDi;
    }

    public void computeRiDi() {
        if (computeAi) computeAi();
        DoubleMatrix2D Ri = DoubleFactory2D.dense.make(3, 3);
        DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
        Rpsi.zMult(Rthetauaxis, Ri);
        RiDi = Ri.zMult(Di, null);

    }

    public void resetEuler() {
        euler = null;
    }

    public DoubleMatrix2D getEuler() {
        if (euler != null) return euler;
        DoubleMatrix1D axis = MatrixUtils.eulerDirection(toRadians(rot), toRadians(tilt));
        DoubleMatrix2D Rthetauaxis = MatrixUtils.rotation3DMatrix(-theta, axis);
        //DoubleMatrix2D Rz_inv=new DenseDoubleAlgebra().inverse(Rz);



        //DoubleMatrix2D Ri = DoubleFactory2D.dense.make(3, 3);
        DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
        //Rpsi.zMult(Rthetauaxis, Ri);
        //Ri=new DenseDoubleAlgebra().inverse(Ri);

        if(setTiltAxisVertical) {
            DoubleMatrix2D Rz = MatrixUtils.rotation3DMatrixZ(rot - 90);
            euler = Rpsi.zMult(Rthetauaxis.zMult(getDi().zMult(Rz, null), null), null);
        }else {
            euler = Rpsi.zMult(Rthetauaxis.zMult(getDi(), null), null);
        }


        //euler=Rpsi.zMult(Rthetauaxis.zMult(getDi(),null),null);
        //euler=Rthetauaxis.zMult(getDi(),null);
        //euler=getRiDi();
        //euler=new DenseDoubleAlgebra().inverse(euler);
        return euler;
        /*DoubleMatrix2D Rz=MatrixUtils.rotation3DMatrixZ(rot-90);
        return euler.zMult(Rz,null);*/
    }

    public DoubleMatrix2D getEulerInverse() {
        if (computeAi) computeAi();
        if (RiDi_inv == null) RiDi_inv = new DenseDoubleAlgebra().inverse(Di);
        return RiDi_inv;
    }

    synchronized public void computeAi() {
        //if (Ai == null) Ai = DoubleFactory2D.dense.make(2, 3);
        computeDi();
        computeRi();
        RiDi = Ri.zMult(Di, null);
        Ai.assign(RiDi.viewPart(0, 0, 2, 3));
        computeAi = false;
        Xi = null;
        XiL = null;
        XiR = null;

        //RiDi=null;
        //RiDi_inv=null;
    }


    public void computeDi() {
        if (Di == null) Di = DoubleFactory2D.dense.make(3, 3);
        Di.assign(0);
        Di.setQuick(0, 0, mi * si * Math.cos(Math.toRadians(deltai)));
        Di.setQuick(1, 0, mi * si * Math.sin(Math.toRadians(deltai)));
        Di.setQuick(1, 1, mi);
        Di.setQuick(2, 2, mi * ti);
        RiDi_inv = null;
        RiDi = null;
    }

    public void computeRi() {
        //System.out.println("computeRi");
        if (Ri == null) Ri = DoubleFactory2D.dense.make(3, 3);
        DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
        Rpsi.zMult(Rthetauaxis, Ri);
        RiDi_inv = null;
        RiDi = null;
        //System.out.println("Rpsi="+Rpsi+" \nRThetaUxais="+Rthetauaxis+" \nRi="+Ri);
    }

    public void computeXi() {
        if (Xi == null) {
            Xi = DoubleFactory2D.dense.make(3, 3);
            XiL = DoubleFactory2D.dense.make(3, 3);
            XiR = DoubleFactory2D.dense.make(3, 3);
        }
        DoubleMatrix2D Rpsi = MatrixUtils.rotation3DMatrixZ(psii);
        DoubleMatrix2D Rbeta = MatrixUtils.alignWithZ(axis);
        Rpsi.zMult(Rbeta.viewDice(), XiL);
        Rbeta.zMult(getDi(), XiR);
        DoubleMatrix2D P = DoubleFactory2D.dense.make(3, 3);
        P.setQuick(1, 0, 1);
        P.setQuick(0, 1, -1);
        P.setQuick(2, 2, 1);
        DoubleMatrix2D HtH = DoubleFactory2D.dense.make(3, 3);
        HtH.setQuick(0, 0, 1);
        HtH.setQuick(1, 1, 1);
        DoubleMatrix2D tmp = XiL.zMult(P, null).zMult(MatrixUtils.rotation3DMatrixZ(theta), null).zMult(HtH, null).zMult(XiR, null);
        Xi.assign(tmp);
    }

    public DoubleMatrix2D getRpsi() {
        return MatrixUtils.rotation3DMatrixZ(psii);
    }

    public DoubleMatrix2D getRthetauaxis() {
        return Rthetauaxis;
    }

    public DoubleMatrix2D getRi() {
        if (Ri == null) computeRi();
        return Ri;
    }

    public DoubleMatrix2D getHRi() {
        if (Ri == null) computeRi();
        return Ri.viewPart(0, 0, 2, 3);

    }

    public DoubleMatrix2D getDi() {
        if (Di == null) computeDi();
        return Di;
    }

    public double getRot() {
        return rot;
    }

    public double getTilt() {
        return tilt;
    }

    public DoubleMatrix2D getXi() {
        if (Xi == null) computeXi();
        return Xi;
    }

    public DoubleMatrix2D getXiL() {
        if (computeAi) computeAi();
        if (XiL == null) computeXi();
        return XiL;
    }

    public DoubleMatrix2D getXiR() {
        if (computeAi) computeAi();
        if (XiR == null) computeXi();
        return XiR;
    }

    public String toString() {
        return "theta=" + theta + " psii=" + psii + "\nmi=" + mi + " ti=" + ti + "\nsi=" + si + " deltai=" + deltai + "\nuaxis[" + rot + ", " + tilt + "]";
    }
}
