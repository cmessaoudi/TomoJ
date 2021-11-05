# TomoJ

TomoJ is an ImageJ plug-in  for tomographic reconstruction.
It was designed for electron tomography, however any acquisition with parallel beam should work.
TomoJ proposes the preprocessing and the registration of tilt-series prior to 3D reconstructions (WBP, ART, SIRT, OS-SART, compressed sensing). 
TomoJ is written in Java, the computations are multithreaded and reconstruction can be performed on CPU or GPU (OpenCL).

registration is described in:
Journal of Structural Biology: X. 2020, Volume 4. "Improvements on marker-free images alignment for electron tomography" C.O.S. Sorzano et al. https://doi.org/10.1016/j.yjsbx.2020.100037. 
BMC Bioinformatics. 2009 Apr 27;10:124."Marker-free image registration of electron tomography tilt-series." C.O.S. Sorzano et al.

reconstruction part was described in:
BMC Bioinformatics. 2007 Aug 6;8:288. "TomoJ: tomography software for three-dimensional reconstruction in transmission electron microscopy."Messaoudi C et al
