/**
from opencl programming book
*/

#define PI       3.14159265358979323846
#define PI_2     1.57079632679489661923
__constant sampler_t samplerIn =CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_NEAREST;


__kernel void spinFact(__global float2* w, int n)
{
    unsigned int i = get_global_id(0);

    float2 angle = (float2)(2*i*PI/(float)n,(2*i*PI/(float)n)+PI_2);
    w[i] = cos(angle);
}

__kernel void bitReverse(__global float2 *dst, __global float2 *src, int m, int n)
{
    unsigned int gid = get_global_id(0);
    unsigned int nid = get_global_id(1);

    unsigned int j = gid;
    j = (j & 0x55555555) <<  1 | (j & 0xAAAAAAAA) >>  1;
    j = (j & 0x33333333) <<  2 | (j & 0xCCCCCCCC) >>  2;
    j = (j & 0x0F0F0F0F) <<  4 | (j & 0xF0F0F0F0) >>  4;
    j = (j & 0x00FF00FF) <<  8 | (j & 0xFF00FF00) >>  8;
    j = (j & 0x0000FFFF) << 16 | (j & 0xFFFF0000) >> 16;

    j >>= (32-m);

    dst[nid*n+j] = src[nid*n+gid];
}

__kernel void norm(__global float2 *x, int n)
{
    unsigned int gid = get_global_id(0);
    unsigned int nid = get_global_id(1);

    x[nid*n+gid] = x[nid*n+gid] / (float2)((float)n, (float)n);
}

__kernel void butterfly(__global float2 *x, __global float2* w, int m, int n, int iter, int flag)
{
    unsigned int gid = get_global_id(0);
    unsigned int nid = get_global_id(1);

    int butterflySize      = 1 << (iter-1);
    int butterflyGrpDist   = 1 << iter;
    int butterflyGrpNum    = n >> iter;
    int butterflyGrpBase   = (gid >> (iter-1))*(butterflyGrpDist);
    int butterflyGrpOffset = gid & (butterflySize-1);

    int a = nid * n + butterflyGrpBase + butterflyGrpOffset;
    int b = a + butterflySize;

    int l = butterflyGrpNum * butterflyGrpOffset;

    float2 xa, xb, xbxx, xbyy, wab, wayx, wbyx, resa, resb;

    xa   = x[a];
    xb   = x[b];
    xbxx = xb.xx;
    xbyy = xb.yy;

    wab  = as_float2(as_uint2(w[l])   ^ (uint2)(0x0, flag));
    wayx = as_float2(as_uint2(wab.yx) ^ (uint2)(0x80000000, 0x0));
    wbyx = as_float2(as_uint2(wab.yx) ^ (uint2)(0x0, 0x80000000));

    resa = xa + xbxx*wab + xbyy*wayx;
    resb = xa - xbxx*wab + xbyy*wbyx;

    x[a] = resa;
    x[b] = resb;
}

__kernel void transpose(__global float2 *dst, __global float2* src, int n)
{
    unsigned int xgid = get_global_id(0);
    unsigned int ygid = get_global_id(1);

    unsigned int iid = ygid * n + xgid;
    unsigned int oid = xgid * n + ygid;

    dst[oid] = src[iid];
}

__kernel void hiPassFilter(__global float2* image, int n, int radius)
{
    unsigned int xgid = get_global_id(0);
    unsigned int ygid = get_global_id(1);

    int2 n_2  = (int2)(n>>1, n>>1);
    int2 mask = (int2)(n-1,  n-1);

    int2 gid = ((int2)(xgid, ygid) + n_2) & mask;

    int2 diff  = n_2 - gid;
    int2 diff2 = diff * diff;
    int dist2 = diff2.x + diff2.y;

    int2 window;

    if (dist2 < radius*radius) {
        window = (int2)(0L, 0L);
    } else {
        window = (int2)(-1L, -1L);
    }

    image[ygid*n+xgid] = as_float2(as_int2(image[ygid*n+xgid]) & window);
}

__kernel void bandPassFilter(__global float2* image, int n, int radius1, int radius2)
{
    unsigned int xgid = get_global_id(0);
    unsigned int ygid = get_global_id(1);

    int2 n_2  = (int2)(n>>1, n>>1);
    int2 mask = (int2)(n-1,  n-1);

    int2 gid = ((int2)(xgid, ygid) + n_2) & mask;

    int2 diff  = n_2 - gid;
    int2 diff2 = diff * diff;
    int dist2 = diff2.x + diff2.y;

    int2 window;

    if (dist2 < radius1*radius1 || dist2 > radius2*radius2) {
        window = (int2)(0L, 0L);
    } else {
        window = (int2)(-1L, -1L);
    }

    image[ygid*n+xgid] = as_float2(as_int2(image[ygid*n+xgid]) & window);
}

__kernel void electronTomographyWeighting(__global float2* fft, int2 dim,float diameter, float tiltaxis,__global float* tiltangle, int nbproj) {
    int x=get_global_id(0);
    int y=get_global_id(1);
    float2 center=as_float2(dim)/2.0f;
    float weight=0;
    float cx=(x > center.x) ? x - dim.x : x-0.0f;
    float cy=(y > center.y) ? y - dim.y : y-0.0f;

    float sinp=sin(radians(tiltaxis));
    float cosp=cos(radians(tiltaxis));
    for(int p=0;p<nbproj;p++){
        float zj = (cx * sin(radians(tiltangle[p])) * cosp + cy * sin(radians(tiltangle[p])) * sinp) / dim.x;
            float value = diameter * PI * zj;
            if (value == 0) {
                value = 1;
            } else {
                value = sin(value) / value;
            }
            weight += value;
    }
    if (weight < 0.005) {
        weight = 0.005f;
    }
    weight *= diameter;
    //fft[x+y*dim.x] /= weight;
    fft[x+y*dim.x] = weight;
}

__kernel void convertComplexToImage2D(__global float2* complexImage, __write_only image2d_t image , int2 dimComplex){
      int x=get_global_id(0);
      int y=get_global_id(1);
      int2 posOuti={x,y};
      write_imagef(image, posOuti, complexImage[(x+y*dimComplex.x)].x);
 }

__kernel void convertImage2DToComplex( __read_only image2d_t image ,__global float2* complexImage, int2 dimComplex){
     int x=get_global_id(0);
     int y=get_global_id(1);
     int2 posOuti={x,y};
     float2 tmp={read_imagef(image, samplerIn, posOuti).x,0};
     complexImage[(x+y*dimComplex.x)]=tmp;
}

__kernel void applyMask(__global float2* fft, __global float* mask){
    int x=get_global_id(0);
    //float2 m={mask[x],mask[x]};
    //fft[x]*= mask[x];
    //fft[x] = fft[x]*m;
    fft[x]*=mask[x];
}

__kernel void createWeightingMask(__global float* mask, int2 dim, float tiltaxis, __global float* tiltangles, int nbproj, float diameter){
    int x=get_global_id(0);
    int y=get_global_id(1);
    float2 center=convert_float2(dim)/2.0f;
    float weight=0;
    float cx=(x > center.x) ? x - dim.x : x-0.0f;
    float cy=(y > center.y) ? y - dim.y : y-0.0f;

    float sinp=sin(radians(tiltaxis));
    float cosp=cos(radians(tiltaxis));
    for(int p=0;p<nbproj;p++){
        float zj = (cx * sin(radians(tiltangles[p])) * cosp + cy * sin(radians(tiltangles[p])) * sinp) / dim.x;
            float value = diameter * PI * zj;
            if (value == 0) {
                value = 1;
            } else {
                value = sin(value) / value;
            }
            weight += value;
    }
    if (weight < 0.005) {
        weight = 0.005f;
    }
    weight *= diameter;
   //mask[x+y*dim.x] = 1/weight;
    //warning the fft is orientated in the other direction (y , x) instead of (x,y) due to computation method  of fft
    mask[y+x*dim.x] = 1/weight;
}