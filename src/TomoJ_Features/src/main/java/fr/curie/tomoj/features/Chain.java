package fr.curie.tomoj.features;

import ij.IJ;
import ij.Prefs;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * Created by amandine on 13/04/15.
 * Amandine Verguet <amandine.verguet@curie.fr> (c) 2015
 */
public class Chain {
    private ArrayList<HashMap> _cs;
    private Feature _f;

    Chain() {
        _cs = new ArrayList<>();
    }

    Chain(int size) {
        _cs = new ArrayList<>(size);
    }

   /* public static HashMap<Point2D, Point2D> matchBetween2List(List<Feature> l1, List<Feature> l2, float thresholdMatch, float maxSd) {
        HashMap<Point2D, Point2D> couple = FloatArray2DSIFT.createMatchesThread(l1, l2, maxSd, thresholdMatch);
        for (Map.Entry<Point2D, Point2D> entry : couple.entrySet()) {
            Point2D key = entry.getKey();
            Point2D value = entry.getValue();
            //System.out.println("key : "+ key + "- value : " + value);
        }
        return couple;
    }

    public static ArrayList<HashMap<Point2D, Point2D>> matchListFeature(ArrayList<ArrayList<Feature>> listFeature, float thresholdMatch, float maxSd) {
        //Compare 2 list of features together
        ArrayList<HashMap<Point2D, Point2D>> chains = new ArrayList<HashMap<Point2D, Point2D>>(listFeature.size() - 1);
        for (int i = 0; i < listFeature.size() - 1; i++) {
            List<Feature> f1 = listFeature.get(i);
            List<Feature> f2 = listFeature.get(i + 1);
            HashMap<Point2D, Point2D> couple = matchBetween2List(f1, f2, thresholdMatch, maxSd);
            chains.add(couple);
            //IJ.showProgress(i, listFeature.size());
        }
        return chains;
    }

    public static ArrayList<HashMap<Point2D, Point2D>> matchListFeatureWithJump(ArrayList<ArrayList<Feature>> listFeature, float thresholdMatch, int jump, float maxSd) {
        //Compare 2 list of features together
        ArrayList<HashMap<Point2D, Point2D>> chains = new ArrayList<HashMap<Point2D, Point2D>>(listFeature.size() - 1);
        for (int i = 0; i < listFeature.size() - jump - 1; i++) {
            List<Feature> f1 = listFeature.get(i);
            List<Feature> f2 = listFeature.get(i + jump + 1);
            HashMap<Point2D, Point2D> couple = matchBetween2List(f1, f2, thresholdMatch, maxSd);
            chains.add(couple);
            //IJ.showProgress(i, listFeature.size());
        }
        return chains;
    }

    public static ArrayList<HashMap<Point2D, Point2D>> matchListFeatureThread(final ArrayList<ArrayList<Feature>> listFeature, final float thresholdMatch, final float maxSd) {
        ArrayList<Future> futures = new ArrayList<Future>(listFeature.size());
        ExecutorService exec = Executors.newFixedThreadPool(Prefs.getThreads());
        //Compare 2 list of features together
        final ArrayList<HashMap<Point2D, Point2D>> chains = new ArrayList<HashMap<Point2D, Point2D>>(listFeature.size() - 1);
        for (int i = 0; i < listFeature.size() - 1; i++) {
            chains.add(new HashMap<Point2D, Point2D>());
        }

        for (int i = 0; i < listFeature.size() - 1; i++) {
            final int ii = i;
            futures.add(exec.submit(new Thread() {
                public void run() {
                    List<Feature> f1 = listFeature.get(ii);
                    List<Feature> f2 = listFeature.get(ii + 1);
                    HashMap<Point2D, Point2D> couple = matchBetween2List(f1, f2, thresholdMatch, maxSd);
                    chains.set(ii, couple);
                    //IJ.showProgress(ii, listFeature.size());
                }
            }));
        }
        try {
            for (Future f : futures) f.get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return chains;
    }       */

    public static ArrayList<ArrayList<Point2D>> createChain(ArrayList<HashMap<Point2D, Point2D>> chains, int img, int chainSize) {
        return createChain(chains, null, img, chainSize);
    }

    public static ArrayList<ArrayList<Point2D>> createChainWithJump(ArrayList<ArrayList<HashMap<Point2D, Point2D>>> matches,
                                                                    ArrayList<ArrayList<Point2D>> listFeatures, int img, int chainSize) {
//        System.out.println("Create chain !!!!!!! "+chains.size());
//        System.out.flush();
        ArrayList<ArrayList<Point2D>> listChain = new ArrayList<ArrayList<Point2D>>();
        //ref to hashmap 1
        HashMap<Point2D, Point2D> h1 = matches.get(0).get(img);
        int nbJump;
        Point2D debug1;

        for (Point2D key : h1.keySet()) {
            //avoid create multiple same chain
            if (listFeatures == null || listFeatures.get(img).lastIndexOf(key) >= 0) {
                ArrayList<Point2D> c = new ArrayList<>();
                //add key 1 and value 1
                c.add(key);
                //remove from list of available features
                if (listFeatures != null) {
                    synchronized (listFeatures) {
                        listFeatures.get(img).remove(key);
                    }
                    //System.out.println("match found remove (key) from list feature");
                }
                //add feature
                Point2D feature = h1.get(key);
                c.add(feature);
                // continue chaining using list of matches
                for (int k = img + 1; k < matches.get(0).size() && feature != null; k++) {
                    //features becomes new key
                    Point2D keytmp = feature;
                    //get feature corresponding to the key in next image matching
                    feature = matches.get(0).get(k).get(keytmp);
                    //if feature is null then try to find with a jump
                    nbJump = 0;
                    while (feature == null && nbJump < matches.size() - 1) {
                        nbJump++;
                        //break;
                        //System.out.println(" looking in match +"+nbJump+", k = " + k);
                        if (k >= matches.get(nbJump).size())
                            break;
                        feature = matches.get(nbJump).get(k).get(keytmp);
                    }
                    //System.out.println("k = " + k + " break");
                    //if feature is found then add it to chain
                    if (feature != null) {
                        for (int jump = 0; jump < nbJump; jump++) {
                            c.add(null);
                        }
                        k += nbJump;
                        c.add(feature);
                        if (listFeatures != null) {
                            int nbfeat = listFeatures.get(k).size();
                            //remove from list of available features
                            synchronized (listFeatures) {
                                listFeatures.get(k).remove(keytmp);
                            }
                        }
                    }
                }
                listChain.add(c);
            }
        }


            /*if(listFeatures!=null && listFeatures.get(img).lastIndexOf(key) <0) {
                //System.out.println("feature already found in one chain!");
                continue;
            }else {

                ArrayList c = new ArrayList<Point2D>();

                //add key 1 and value 1
                c.add(key);
                if (listFeatures != null) {
                    listFeatures.get(img).remove(key);
                    //System.out.println("match found remove (key) from list feature");
                }

                Point2D feature = h1.get(key);
                c.add(feature);
                // search chaine between list of matches
                for (int k = img + 1; k < matches.get(0).size(); k++) {
                    Point2D keytmp = feature;
                    feature = matches.get(0).get(k).get(keytmp);
                    //consider nbJump in chain
                    nbJump = 0;
                    while (feature == null && nbJump < matches.size() - 1) {
                        nbJump++;
                        //break;
                        //System.out.println(" looking in match +"+nbJump+", k = " + k);
                        if (k >= matches.get(nbJump).size())
                            break;
                        feature = matches.get(nbJump).get(k).get(keytmp);
                    }
                    //System.out.println("k = " + k + " break");
                    if (feature != null) {
                        for (int jump = 0; jump < nbJump; jump++) {
                            c.add(null);
                        }
                        k += nbJump;
                        c.add(feature);
                        if (listFeatures != null) {
                            //System.out.println(""+keytmp);
                            //System.out.println("to remove " + keytmp + " index " + listFeatures.get(k).lastIndexOf(keytmp));
                            listFeatures.get(k).remove(keytmp);
                            //System.out.println("match found remove from list feature");
                            //System.out.println("removed " + keytmp + " index " + listFeatures.get(k).lastIndexOf(keytmp));
                            //System.out.println(listFeatures.get(k + 1).lastIndexOf(feature));
                            //listFeatures.get(k+1).remove(feature);
                        }
                    }
                    else{
                        break;
                    }
                    //System.out.println(c.size()+" k="+k);
                }
                listChain.add(c);
            }
            //System.out.println("taille chaine  " + c.size());
        }*/
//        for (ArrayList<Point2D> list : listChain) {
//            if (list.size() > chainSize) {
        //System.out.println("new chain created  " + list.size() + "\n");
//                for (Point2D link : list) {
//                    //if(list.size() > 3)
//                    System.out.print(link + "\t");
//                }
//                    System.out.println("\n");
//            }
//        }
        return listChain;
    }

    public static ArrayList<ArrayList<Point2D>> createChainReverseWithJump(ArrayList<ArrayList<HashMap<Point2D, Point2D>>> matches,
                                                                           ArrayList<ArrayList<Point2D>> listFeatures, int img, int chainSize) {
//        System.out.println("Create chain !!!!!!! "+chains.size());
//        System.out.flush();
        ArrayList<ArrayList<Point2D>> listChain = new ArrayList<ArrayList<Point2D>>();
        //ref to hashmap 1
        HashMap<Point2D, Point2D> h1 = matches.get(matches.size() - 1).get(img);
        int nbJump;

        for (Point2D key : h1.keySet()) {
            //avoid create multiple same chain
            if (listFeatures != null && listFeatures.get(img).lastIndexOf(key) < 0) continue;

            ArrayList<Point2D> c = new ArrayList<>();

            //add key 1 and value 1
            c.add(key);
            if (listFeatures != null)
                listFeatures.get(img).remove(key);

            Point2D feature = h1.get(key);
            c.add(feature);
            // search chaine between list of matches
            for (int k = img - 1; k > matches.get(0).size(); k--) {
                Point2D keytmp = feature;
                feature = matches.get(matches.size() - 1).get(k).get(keytmp);
                //consider nbJump in chain
                nbJump = 0;
                while (feature == null && nbJump < matches.size() - 1) {
                    nbJump++;
                    //break;
                    //System.out.println(" looking in match +"+nbJump+", k = " + k);
                    if (k >= matches.get(nbJump).size())
                        break;
                    feature = matches.get(nbJump).get(k).get(keytmp);
                }
                //System.out.println("k = " + k + " break");
                if (feature != null) {
                    for (int jump = 0; jump < nbJump; jump++) {
                        c.add(null);
                    }
                    k += nbJump;
                    c.add(feature);
                }
                if (listFeatures != null)
                    listFeatures.get(k).remove(keytmp);
                //System.out.println(c.size()+" k="+k);
            }
            listChain.add(c);
            //System.out.println("taille chaine  " + c.size());
        }
        for (ArrayList<Point2D> list : listChain) {
            if (list.size() > chainSize) {
                System.out.println("new chain created  " + list.size() + "\n");
//                for (Point2D link : list) {
//                    //if(list.size() > 3)
//                    System.out.print(link + "\t");
//                }
//                    System.out.println("\n");
            }
        }
        return listChain;
    }

    public static ArrayList<ArrayList<Point2D>> createChain(ArrayList<HashMap<Point2D, Point2D>> matches, ArrayList<ArrayList<Point2D>> listFeatures, int img, int chainSize) {
//        System.out.println("Create chain !!!!!!! "+chains.size());
//        System.out.flush();
        ArrayList<ArrayList<Point2D>> listChain = new ArrayList<ArrayList<Point2D>>();
        //ref to hashmap 1
        HashMap<Point2D, Point2D> h1 = matches.get(img);

        for (Point2D key : h1.keySet()) {
            //avoid create multiple same chain
            if (listFeatures != null && listFeatures.get(img).lastIndexOf(key) < 0) continue;

            ArrayList<Point2D> c = new ArrayList<>();
            listChain.add(c);
            c.add(key);
            //add key 1 and value 1
            if (listFeatures != null)
                listFeatures.get(img).remove(key);

            Point2D feature = h1.get(key);
            c.add(feature);
            //add value 2 - value n
            for (int k = img + 1; k < matches.size(); k++) {
                Point2D keytmp = feature;
                feature = matches.get(k).get(keytmp);
                if (feature == null) {

                    //System.out.println("k="+k+" break");
                    break;
                }
                if (noNullSize(c) >= chainSize)
                    c.add(feature);
                if (listFeatures != null)
                    listFeatures.get(k).remove(keytmp);
                //System.out.println(c.size()+" k="+k);
            }
        }
        for (ArrayList<Point2D> list : listChain) {
            if (list.size() > chainSize) {
                System.out.println("new chain created  " + list.size() + "\n");
//                for (Point2D link : list) {
//                    //if(list.size() > 3)
//                    System.out.print(link + "\t");
//                }
//                    System.out.println("\n");
            }
        }
        return listChain;
    }

    /**
     * number of non null point in current chain
     */

    public static int noNullSize(ArrayList<Point2D> chain) {
        int count = 0;
        for (Point2D p : chain) {
            if (p != null)
                count++;
        }
        return count;
    }

}
