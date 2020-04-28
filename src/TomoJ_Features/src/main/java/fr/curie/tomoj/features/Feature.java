package fr.curie.tomoj.features;

/**
 * Scale Invariant Feature Transform as described by David Lowe.
 *
 * inspired on code from Stephan Saalfeld <saalfeld@mpi-cbg.de> (c)
 *
 * Amandine Verguet <amandine.verguet@curie.fr> (c) 2015
 *
 */

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * SIFT feature container
 */
public class Feature extends Point2D.Float implements Comparable<Feature>, Serializable {
    public float _scale;
    public float _orientation;
    public float[] _location;
    public float[] _descriptor;

    /**
     * Dummy constructor for Serialization to work properly.
     */
    public Feature() {
        super(0,0);
        _scale=1;
        _orientation=0;
        _location=new float[2];
        _descriptor=new float[128];
    }

    /**
     * Create a new feature with specific value
     * @param s the scale
     * @param o the orientation
     * @param l an array of position x, y
     * @param d an array of 128 values
     */
    public Feature(float s, float o, float[] l, float[] d) {
        super(l[0],l[1]);
        _scale = s;
        _orientation = o;
        _location = l;
        _descriptor = d;
    }
    /**
     * Create a new feature with Harris Corner specific value
     * @param p1 x position
     * @param p2 y position
     */
    public Feature(float p1, float p2, float[] d ) {
        super(p1,p2);
        _scale = 1;
        _orientation = 0;
        _descriptor = d;
    }
    /**
     * Create a new feature with specific value
     * @param s the scale
     * @param o the orientation
     * @param l an array of position x, y
     * @param d an array of 128 values
     */
    public Feature(float s, float o, float[] l, ArrayList<java.lang.Float> d) {
        super(l[0], l[1]);
        _scale = s;
        _orientation = o;
        _location = l;
        _descriptor = new float[128];
        for (int i = 0; i < 128; i++)
        {
            _descriptor[i] = d.get(i);
        }
    }
    /**
     * Create a copy of a feature
     * @param other the original feature caracteristics
     */
    public Feature(Feature other)
    {
        super((float)other.getX(), (float)other.getY());
        _scale = other._scale;
        _orientation = other._orientation;
        _location = Arrays.copyOf(other._location, 2);
        _descriptor = Arrays.copyOf(other._descriptor, 128);

    }
    /**
     *
     * @param param float array of size 132  (scale, orientation, position x, position y, descriptors array of size 128 )
     */
    public Feature(float[] param)
    {
        super(param[2], param[3]);
        _scale = param[0];
        _orientation = param[1];
        _location =  Arrays.copyOfRange(param, 2, 4);
        _descriptor = Arrays.copyOfRange(param, 4, 132);
    }

    /**
     * comparator for making Features sortable
     * please note, that the comparator returns -1 for
     * this.scale &gt; o.scale, to sort the features in a descending order
     */
    public int compareTo(Feature f) {
        return _scale < f._scale ? 1 : _scale == f._scale ? 0 : -1;
    }

    /**
     *
     * Compute sum of difference 
     **/
    public float descriptorDistance(Feature f) {
        float d = 0;
        for (int i = 0; i < _descriptor.length; ++i) {
            float a = _descriptor[i] - f._descriptor[i];
            d += a * a;
        }
        return (float) Math.sqrt(d);
    }

    /**
     * Visulization of the content feature by printing
     * @return string of the content feature
     */
    public String toString() {
        String res = " ";
        res += "position(" + _location[0] + ", " + _location[1] + ")\n";
        res += " scale:" + _scale + ", angle :" + _orientation + ", \n";
        res += "descripteur :";
        for (float d : _descriptor) {
            res += " " + d;
        }
        return res;

    }

    public float getScale()
    {
        return _scale;
    }

    public float getOrientation()
    {
        return _orientation;
    }

    public float getLocation(int i)
    {
        return _location[i];
    }

    public Point2D getLocation()
    {
        float p1 = _location[0];
        float p2 = _location[1];
        return new Float(p1, p2);

    }

    public float getDescriptor(int i)
    {
        return _descriptor[i];
    }

    public void setScale(float scale) {
        this._scale = scale;
    }

    public void setOrientation(float orientation) {
        this._orientation = orientation;
    }

    public void setLocation(float[] location) {
        this._location = location;
    }

    public void setDescriptor(float[] descriptor) {
        this._descriptor = descriptor;
    }
}
