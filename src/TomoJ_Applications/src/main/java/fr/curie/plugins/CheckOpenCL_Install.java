package fr.curie.plugins;

import ij.IJ;
import ij.Prefs;
import ij.plugin.PlugIn;

import java.io.File;
import java.lang.reflect.Field;

public class CheckOpenCL_Install implements PlugIn {

    @Override
    public void run(String s) {

        boolean gpuAvailable=true;
        IJ.showStatus("check java OpenCL library");
        try {
            Class tmp = Class.forName("org.jocl.CLException");
            if (tmp != null) {
                IJ.showStatus("java OpenCL library OK to create an OpenCL object");
                //gpuAvailable = true;
                System.out.println("opencl java files detected");
                String javaLibraryPath = System.getProperty("java.library.path");
                String systemLibraryPath = System.getProperty("sun.boot.library.path");
                System.out.println("library path" + javaLibraryPath);
                System.out.println("system path:" + systemLibraryPath);
                if (IJ.isLinux()) {
                    IJ.showStatus("check if OpenCL is available from Linux");
                    System.out.println("linux: checking that libOpenCL.so is the path");

                    Field field = ClassLoader.class.getDeclaredField("sys_paths");
                    System.out.println(field.toGenericString());

                    String[] libdirs = systemLibraryPath.split(":");
                    boolean openclExist = false;
                    for (String name : libdirs) {
                        File tmpfile = new File(name + "/libOpenCL.so");
                        openclExist = (tmpfile.exists() || openclExist);
                    }
                    File tmpfile = new File("/usr/lib/x86_64-linux-gnu/libOpenCL.so");
                    openclExist = (tmpfile.exists() || openclExist);

                    System.out.println("opencl library found (libOpenCL.so):" + openclExist);
                    gpuAvailable = openclExist;
                }
            } else {
                IJ.showStatus("java OpenCL library could not create an OpenCL object");
                gpuAvailable = false;
                System.out.println("opencl library not found : reconstruction on GPU is unavailable!");
            }
        } catch (Exception e) {
            IJ.showStatus("java OpenCL library not found");
            System.out.println("opencl library not found : reconstruction on GPU is unavailable!");
            gpuAvailable = false;
        }
        Prefs.set("TOMOJ_GPU.bool", gpuAvailable);
        String msg= "TomoJ has found "+((gpuAvailable)?"":"NO ")+"GPU";
        IJ.showMessage(msg);

    }
}
