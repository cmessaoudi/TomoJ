//#pragma OPENCL EXTENSION all : enable
__constant sampler_t samplerIn = CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP | CLK_FILTER_LINEAR;
__constant sampler_t samplerInClampEdge = CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;
__constant sampler_t samplerInNotNormalized =CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;
__constant sampler_t samplerInNearestNeightbor =CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_NEAREST;
__constant sampler_t samplerProj =CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;
__constant float ACCURACY = 0.0000001f;
__constant float4 zeros={0,0,0,0};
__constant bool DOCLAMPEDGE=false;

__kernel void _0_projectImage3D_Partial(
    __read_only image3d_t rec,
    __write_only image2d_t proj,
    __write_only image2d_t norm,
    const float8 eulerT,
    const float4 dir,
    const float2 Pcenter,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float2 scale,
    const float4 deform
) {
    __private int2 posOuti={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    //__private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset};
    __private float2 r_p=posOut-Pcenter;
    //express r_p in universal coordinate system
    //float4 dir2=dir*dir;
    //__private float normVector=sqrt(dir2.x+dir2.y+dir2.z);
    //__private float normVector=length(dir);
    //dir2=dir/normVector;
    float4 dir2=normalize(dir);

    __private float4 pl={(eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x))*deform.x,(eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x))*deform.y,(eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x))*deform.z,0};
    //compute min and max alpha for the ray intersecting the volume
    //float4 alpha_min={-0.5f-pl.x,-0.5f-pl.y,-0.5f-pl.z,0};
    //alpha_min=(alpha_min)/dir;
    float4 alpha_min=(-pl-0.5f)/dir;
    float4 alpha_max=(Vsize*scale.x+0.5f-pl)/dir;

   float4 minmax=fmin(alpha_min,alpha_max);
    float galpha_min= fmax(fmax(minmax.x,minmax.y),minmax.z);
    minmax=fmax(alpha_min,alpha_max);
    float galpha_max= fmin(fmin(minmax.x,minmax.y),minmax.z);
    //float galpha_min= fmax(fmax(fmin(alpha_min.x,alpha_max.x),fmin(alpha_min.y,alpha_max.y)),fmin(alpha_min.z,alpha_max.z));
    //float galpha_max= fmin(fmin(fmax(alpha_min.x,alpha_max.x),fmax(alpha_min.y,alpha_max.y)),fmax(alpha_min.z,alpha_max.z));

    float4 idx=dir*galpha_min+pl;
    //float4 idx=mad(dir,galpha_min,pl);

    idx=min(max(idx,zeros),Vsize*scale.x-1);
    //follow the ray
    //__private float alpha=galpha_min;
    float4 tmp=(sign(dir)/2-pl)/dir;
    __private float ray_sum=0;
    __private float norm_sum=0;
    float4 pixel={0,0,0,1};
    //float4 alpha_xyz;
    //float4 diff;
    //float diff_alpha;
    //int index;
    float val;
    //float tmp2;
    float4 signum=sign(dir);
    if((galpha_max-galpha_min)>ACCURACY){
    //idx.y=idx.y-Yoffset;
        while(idx.x>=0&&idx.x<Vsize.x*scale.x&&idx.y>=0&&idx.y<Vsize.y*scale.x&&idx.z>=0&&idx.z<Vsize.z*scale.x){
            float4 idxF={(idx.x)/(get_image_width(rec)*scale.x),(idx.y-Yoffset*scale.x)/(get_image_height(rec)*scale.x), idx.z/(get_image_depth(rec)*scale.x),0};
            //float4 idxF={idx.x/(get_image_width(rec)*scale),idx.y/(get_image_height(rec)*scale), idx.z/(get_image_depth(rec)*scale),0};
            pixel= read_imagef(rec, samplerIn, idxF);
           // pixel= read_imagef(rec, samplerInNotNormalized, idx+0.5f);
            val=pixel.x;
            //if(isnan(val)) val=0;
            //if(isinf(val)) val=0;
            ray_sum+=val;
            norm_sum+=1;
            //idx+=dir*josephStep;
            idx+=dir2;
            //idx+=dir;

        }
    } else{
    //ray_sum=5000;
    ray_sum=NAN;
    norm_sum=-1;
    }
    pixel.x=ray_sum;
    write_imagef(proj, posOuti, pixel);
    pixel.x=norm_sum;
    write_imagef(norm, posOuti, pixel);
}

__kernel void _1_diff(
    __read_only image2d_t exp,
    __read_only image2d_t th,
    __read_only image2d_t norm,
    __write_only image2d_t res ,
    const float factor,
    const float longObjectCompensation
) {
    __private int2 pos={get_global_id(0),get_global_id(1)}  ;
    __private float2 posin={get_global_id(0)+0.5f,get_global_id(1)+0.5f}  ;
    //posin.x=posin.x/get_image_width(exp);
    //posin.y=posin.y/get_image_height(exp);
    __private float4 pixelexp= read_imagef(exp, samplerInNotNormalized, posin);
    __private float4 pixelth= read_imagef(th, samplerInNotNormalized, posin);
    __private float4 pixeln= read_imagef(norm, samplerInNotNormalized, posin);
    if(longObjectCompensation!=0) pixeln.x=longObjectCompensation;
    __private float4 val={(pixelexp.x-pixelth.x)/pixeln.x*factor,0,0,1} ;
    if(fabs(pixeln.x)<0.00001f)val.x=0;
    if(isnan(val.x)) val.x=NAN;
    write_imagef(res, pos, val);

}

//float4 Vsize={volume width, scaled width, scaled height, volume width*volume GPU height}
__kernel void _2_backProject_Partial_1P(
    __global float* rec,
    __read_only image2d_t proj,
    const float8 euler,
    const float4 Pcenter,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float4 deformInv,
    const short positivityConstraint,
    const float scale
) {
   __private float result=rec[get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w)] ;
   if(isnan(result)) result=0;
   if(isinf(result)) result=0;

    __private float4 r_p={((get_global_id(0)-Vcenter.x)*deformInv.x),((get_global_id(1)+Yoffset-Vcenter.y)*deformInv.y),((get_global_id(2)-Vcenter.z)*deformInv.z),0};
    //__private float xx=r_p.x*euler.s0 +r_p.y*euler.s1+r_p.z*euler.s2+Pcenter.x;
    //__private float yy=r_p.x*euler.s3 +r_p.y*euler.s4+r_p.z*euler.s5+Pcenter.y;
     //__private float2 posproj={(xx +f)/Pcenter.z, (yy+0.5f)/Pcenter.w};
      __private float xx=(r_p.x*euler.s0 +r_p.y*euler.s1+r_p.z*euler.s2)*scale + Pcenter.x;
     __private float yy=(r_p.x*euler.s3 +r_p.y*euler.s4+r_p.z*euler.s5)*scale + Pcenter.y;
     //take care Vsize.y= scaled X, Vsize.z= scaled Y
      __private float2 posproj={(xx +0.5f)/Pcenter.z, (yy+0.5f)/Pcenter.w};
      //posproj+=Pcenter.lo/Pcenter.hi;
      if(DOCLAMPEDGE){
           int2 test=posproj>=0&&posproj<=1;
           if(test.x && test.y){
               __private float4 value=read_imagef(proj,samplerInClampEdge,posproj);
               if(isnan(value.x)==0){
                   result+=value.x;
                  rec[get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w)] =result;
               }
           }
       }else{
          __private float4 value=read_imagef(proj,samplerIn,posproj);

           if(isnan(value.x)==0){
                result+=value.x;
                //if(positivityConstraint && result<0) result=0;
               //rec[get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w)] =result;
                rec[get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w)] =(positivityConstraint && result<0)? 0:result;
                                         }
      }

}

__kernel void _3_backProject_Partial_2P(
    __global float* rec,
    __read_only image2d_t proj1,
    __read_only image2d_t proj2,
    const float16 euler,
    const float4 Pcenter,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float4 deformInv,
    const short positivityConstraint,
    const float scale
) {
    __private int posrec=get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w);
    __private float result=rec[posrec] ;
    if(isnan(result)) result=0;
    if(isinf(result)) result=0;
    private float tmp=0;
    //proj1
    __private float4 r_p={((get_global_id(0)-Vcenter.x)*deformInv.x),((get_global_id(1)+Yoffset-Vcenter.y)*deformInv.y),((get_global_id(2)-Vcenter.z)*deformInv.z),0};
    //__private float4 r_p={get_global_id(0)-Vcenter.x,get_global_id(1)+Yoffset-Vcenter.y,get_global_id(2)-Vcenter.z,0};
    //__private float4 r_p={get_global_id(0)-Vcenter.x,get_global_id(1)-Vcenter.y,get_global_id(2)-Vcenter.z,0};
//    __private float xx=r_p.x*euler.s0 +r_p.y*euler.s1+r_p.z*euler.s2+Pcenter.x;
//    __private float yy=r_p.x*euler.s3 +r_p.y*euler.s4+r_p.z*euler.s5+Pcenter.y;
//    __private float2 posproj={(xx+0.5f )/(float)Pcenter.z, (yy+0.5f)/(float)Pcenter.w};
    __private float xx=(r_p.x*euler.s0 +r_p.y*euler.s1+r_p.z*euler.s2)*scale + Pcenter.x;
     __private float yy=(r_p.x*euler.s3 +r_p.y*euler.s4+r_p.z*euler.s5)*scale +Pcenter.y;
     //take care Vsize.y= scaled X, Vsize.z= scaled Y
      __private float2 posproj={(xx +0.5f)/Pcenter.z, (yy+0.5f)/Pcenter.w};
      //posproj+=Pcenter.lo/Pcenter.hi;
    //express r_p in universal coordinate system
    __private float4 value=read_imagef(proj1,samplerIn,posproj);
    if(isnan(value.x)==0) tmp=value.x;

     //proj2
//    __private float xx2=r_p.x*euler.s8 +r_p.y*euler.s9 +r_p.z*euler.sa +Pcenter.x;
//    __private float yy2=r_p.x*euler.sb +r_p.y*euler.sc +r_p.z*euler.sd +Pcenter.y;
//    __private float2 posproj2={(xx2+0.5f )/(float)Pcenter.z, (yy2+0.5f)/(float)Pcenter.w};
     __private float xx2=(r_p.x*euler.s8 +r_p.y*euler.s9 +r_p.z*euler.sa)*scale + Pcenter.x;
     __private float yy2=(r_p.x*euler.sb +r_p.y*euler.sc +r_p.z*euler.sd)*scale + Pcenter.y;
     //take care Vsize.y= scaled X, Vsize.z= scaled Y
      __private float2 posproj2={(xx2 +0.5f)/Pcenter.z, (yy2+0.5f)/Pcenter.w};
//          posproj2+=Pcenter.lo/Pcenter.hi;
    //express r_p in universal coordinate system
    value=read_imagef(proj2,samplerIn,posproj2);
    if(isnan(value.x)==0) tmp+=value.x;

    result+=tmp;
    rec[posrec] =(positivityConstraint && result<0)? 0:result;

}
__kernel void _4_backProject_Partial_4P(
    __global float* rec,
    __read_only image2d_t proj1,
    __read_only image2d_t proj2,
    __read_only image2d_t proj3,
    __read_only image2d_t proj4,
    __global float* euler,
    const float4 Pcenter,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float4 deformInv,
    const short positivityConstraint,
    const float scale
) {
    /*int lid=get_local_id(0);
    if(lid<24){
        temp[lid]=euler[lid];
    }
    barrier(CLK_LOCAL_MEM_FENCE); */

    
    __private int posrec=get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w);
    __private float result=rec[posrec] ;
    if(isnan(result)) result=0;
    if(isinf(result)) result=0;
    private float tmp=0;
    //proj1
    //__private float4 r_p={get_global_id(0)-Vcenter.x,get_global_id(1)-Vcenter.y,get_global_id(2)-Vcenter.z,0};
    __private float4 r_p={(get_global_id(0)-Vcenter.x)*deformInv.x,(get_global_id(1)+Yoffset-Vcenter.y)*deformInv.y,(get_global_id(2)-Vcenter.z)*deformInv.z,0};
   //  __private float4 r_p={get_global_id(0)-Vcenter.x,get_global_id(1)+Yoffset-Vcenter.y,get_global_id(2)-Vcenter.z,0};
//    __private float xx=r_p.x*euler[0] +r_p.y*euler[1]+r_p.z*euler[2]+Pcenter.x;
//    __private float yy=r_p.x*euler[3] +r_p.y*euler[4]+r_p.z*euler[5]+Pcenter.y;
//    __private float2 posproj={(xx+0.5f )/(float)Pcenter.z, (yy+0.5f)/(float)Pcenter.w};
    __private float xx=(r_p.x*euler[0] +r_p.y*euler[1]+r_p.z*euler[2])*scale + Pcenter.x;
    __private float yy=(r_p.x*euler[3] +r_p.y*euler[4]+r_p.z*euler[5])*scale + Pcenter.y;
    //take care Vsize.y= scaled X, Vsize.z= scaled Y
__private float2 posproj={(xx +0.5f)/Pcenter.z, (yy+0.5f)/Pcenter.w};
//      posproj+=Pcenter.lo/Pcenter.hi;
    //express r_p in universal coordinate system
   __private float4 value=read_imagef(proj1,samplerIn,posproj);
    if(isnan(value.x)==0)tmp=value.x;

     //proj2
//    __private float xx2=r_p.x*euler[6] +r_p.y*euler[7] +r_p.z*euler[8] +Pcenter.x;
//    __private float yy2=r_p.x*euler[9] +r_p.y*euler[10] +r_p.z*euler[11] +Pcenter.y;
//    __private float2 posproj2={(xx2+0.5f )/(float)Pcenter.z, (yy2+0.5f)/(float)Pcenter.w};
    __private float xx2=(r_p.x*euler[6] +r_p.y*euler[7] +r_p.z*euler[8])*scale + Pcenter.x;
    __private float yy2=(r_p.x*euler[9] +r_p.y*euler[10] +r_p.z*euler[11])*scale  + Pcenter.y;
    //take care Vsize.y= scaled X, Vsize.z= scaled Y
    __private float2 posproj2={(xx2 +0.5f)/Pcenter.z, (yy2+0.5f)/Pcenter.w};
//          posproj2+=Pcenter.lo/Pcenter.hi;
    //express r_p in universal coordinate system
    value=read_imagef(proj2,samplerIn,posproj2);
    if(isnan(value.x)==0)tmp+=value.x;

    //proj3
//    __private float xx3=r_p.x*euler[12] +r_p.y*euler[13] +r_p.z*euler[14] +Pcenter.x;
//    __private float yy3=r_p.x*euler[15] +r_p.y*euler[16] +r_p.z*euler[17] +Pcenter.y;
//    __private float2 posproj3={(xx3+0.5f )/(float)Pcenter.z, (yy3+0.5f)/(float)Pcenter.w};
    __private float xx3=(r_p.x*euler[12] +r_p.y*euler[13] +r_p.z*euler[14])*scale  + Pcenter.x;
    __private float yy3=(r_p.x*euler[15] +r_p.y*euler[16] +r_p.z*euler[17])*scale + Pcenter.y;
    //take care Vsize.y= scaled X, Vsize.z= scaled Y
    __private float2 posproj3={(xx3 +0.5f)/Pcenter.z, (yy3+0.5f)/Pcenter.w};
//    posproj3+=Pcenter.lo/Pcenter.hi;
    //express r_p in universal coordinate system
    value=read_imagef(proj3,samplerIn,posproj3);
    if(isnan(value.x)==0)tmp+=value.x;

    //proj4
//    __private float xx4=r_p.x*euler[18] +r_p.y*euler[19] +r_p.z*euler[20] +Pcenter.x;
//    __private float yy4=r_p.x*euler[21] +r_p.y*euler[22] +r_p.z*euler[23] +Pcenter.y;
//    __private float2 posproj4={(xx4+0.5f )/(float)Pcenter.z, (yy4+0.5f)/(float)Pcenter.w};
    __private float xx4=(r_p.x*euler[18] +r_p.y*euler[19] +r_p.z*euler[20])*scale + Pcenter.x;
    __private float yy4=(r_p.x*euler[21] +r_p.y*euler[22] +r_p.z*euler[23])*scale + Pcenter.y;
    //take care Vsize.y= scaled X, Vsize.z= scaled Y
    __private float2 posproj4={(xx4 +0.5f)/Pcenter.z, (yy4+0.5f)/Pcenter.w};
          posproj4+=Pcenter.lo/Pcenter.hi;
    //express r_p in universal coordinate system
    value=read_imagef(proj4,samplerIn,posproj4);
    if(isnan(value.x)==0)tmp+=value.x;

    result+=tmp;
    rec[posrec] =(positivityConstraint && result<0)? 0:result;

     /*__private float4 value1=read_imagef(proj1,samplerIn,posproj);
        __private float4 value2=read_imagef(proj2,samplerIn,posproj2);
        __private float4 value3=read_imagef(proj3,samplerIn,posproj3);
        __private float4 value4=read_imagef(proj4,samplerIn,posproj4);
        rec[posrec] = result + value1.x + value2.x + value3.x + value4.x; */

}


__kernel void _5_projectImage3D_Partial_Diff(
    __read_only image3d_t rec,
    __read_only image2d_t exp_proj,
    __write_only image2d_t diff_image,
    const float8 eulerT,
    const float4 dir,
    const float2 Pcenter,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float factor,
    const float longObjectCompensation,
    const float2 scale,
    const float4 deform
) {
    __private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private int2 posOuti={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private float2 posOutProj={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};

    //prefecthing read exp_prj value;
    __private float4 pixelexp=read_imagef(exp_proj, samplerInNotNormalized, posOutProj+0.5f);

    //express r_p in universal coordinate system
    __private float2 r_p=posOut-Pcenter;

    float4 dir2=dir*dir;
    __private float normVector=sqrt(dir2.x+dir2.y+dir2.z);
    dir2=dir/normVector;
    __private float josephStep = fmin(fmin(fabs(1.0f / dir.x), fabs(1.0f / dir.y)),fabs(1.0f / dir.z));

   // __private float4 pl={eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x),eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x),eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x),0};

    __private float4 pl={(eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x))*deform.x,(eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x))*deform.y,(eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x))*deform.z,0};

    //compute min and max alpha for the ray intersecting the volume
    float4 alpha_min=(-pl-0.5f)/dir;
    float4 alpha_max=(Vsize*scale.x+0.5f-pl)/dir;

   float4 minmax=fmin(alpha_min,alpha_max);
    float galpha_min= fmax(fmax(minmax.x,minmax.y),minmax.z);
    minmax=fmax(alpha_min,alpha_max);
    float galpha_max= fmin(fmin(minmax.x,minmax.y),minmax.z);

    float4 idx=dir*galpha_min+pl;

    idx=min(max(idx,zeros),Vsize*scale.x-1);
    //follow the ray
    //__private float alpha=galpha_min;
    float4 tmp=(sign(dir)/2-pl)/dir;
    __private float ray_sum=0;
    __private float norm_sum=0;
    float4 pixel={0,0,0,1};
    //float4 alpha_xyz;
    //float4 diff;
    //float diff_alpha;
    //int index;
    float val;
    //float tmp2;
    float4 signum=sign(dir);
    if((galpha_max-galpha_min)>ACCURACY){
    //idx.y=idx.y-Yoffset;
        while(idx.x>=0&&idx.x<Vsize.x*scale.x&&idx.y>=0&&idx.y<Vsize.y*scale.x&&idx.z>=0&&idx.z<Vsize.z*scale.x){
            float4 idxF={idx.x/(get_image_width(rec)*scale.x),(idx.y-Yoffset*scale.x)/(get_image_height(rec)*scale.x), idx.z/(get_image_depth(rec)*scale.x),0};
            pixel= read_imagef(rec, samplerIn, idxF);
            val=pixel.x;
            //if(isnan(val)) val=0;
            //if(isinf(val)) val=0;
            ray_sum+=val;
            norm_sum+=1;
            idx+=dir2;
        }
    }
    //compute diff
    if(longObjectCompensation!=0) norm_sum=longObjectCompensation;
    __private float4 valp={(pixelexp.x-ray_sum)/norm_sum*factor,0,0,1} ;
    //if(isnan(valp.x)) valp.x=10;
    //if(isinf(valp.x)) valp.x=10;
    if(norm_sum==0)valp.x=0;
    write_imagef(diff_image, posOuti, valp);

}

__kernel void _6_projectImage3D_Partial_Diff_Ali(
    __read_only image3d_t rec,
    __read_only image2d_t exp_proj,
    __write_only image2d_t diff_image,
    const float8 eulerT,
    const float4 dir,
    const float4 Pcenters,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float factor,
    const float longObjectCompensation,
    const float8 Tinv,
    const float2 stats,
    const float2 scale,
    const float4 deform
) {
    __private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private int2 posOuti={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private float2 posOutProj={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};

    //prefecthing read exp_prj value;
    int2 dim=get_image_dim(exp_proj);

    float8 inPos={posOutProj.x-Pcenters.x,posOutProj.x-Pcenters.x,posOutProj.y-Pcenters.y,posOutProj.y-Pcenters.y,1,1,1,1};
    float8 in=Tinv*inPos;
    float2 posIn = {in.s0+in.s2+in.s4+in.s6, in.s1+in.s3+in.s5+in.s7};
    //posIn.x=posOut.x;
    //posIn.y=posOut.y;
    //posIn+=0.5f;
    __private float4 valp={0,0,0,1} ;

    bool defined= posIn.x>=0&&posIn.x<dim.x&&posIn.y>=0&&posIn.y<dim.y;
    if(defined){
        posIn+=0.5f;
         __private float4 pixelexp=read_imagef(exp_proj, samplerProj, posIn);
         if(stats.x!=0) pixelexp=(pixelexp-stats.x)/stats.y;

       // __private float4 pixelexp=read_imagef(exp_proj, samplerInNotNormalized, posOut+0.5f);

        //express r_p in universal coordinate system
        __private float2 r_p=posOut-Pcenters.hi;
        float4 dir2=normalize(dir);
        //float4 dir2=dir*dir;
        //__private float normVector=sqrt(dir2.x+dir2.y+dir2.z);
        //dir2=dir/normVector;
        //__private float josephStep = fmin(fmin(fabs(1.0f / dir.x), fabs(1.0f / dir.y)),fabs(1.0f / dir.z));

       // __private float4 pl={eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x),eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x),eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x),0};

    __private float4 pl={(eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x))*deform.x,(eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x))*deform.y,(eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x))*deform.z,0};

        //compute min and max alpha for the ray intersecting the volume
        float4 alpha_min=(-pl-0.5f)/dir;
        float4 alpha_max=(Vsize*scale.x+0.5f-pl)/dir;

       float4 minmax=fmin(alpha_min,alpha_max);
        float galpha_min= fmax(fmax(minmax.x,minmax.y),minmax.z);
        minmax=fmax(alpha_min,alpha_max);
        float galpha_max= fmin(fmin(minmax.x,minmax.y),minmax.z);

        float4 idx=dir*galpha_min+pl;

        idx=min(max(idx,zeros),Vsize*scale.x-1);
        //follow the ray
        //__private float alpha=galpha_min;
        float4 tmp=(sign(dir)/2-pl)/dir;
        __private float ray_sum=0;
        __private float norm_sum=0;
        float4 pixel={0,0,0,1};
        //float4 alpha_xyz;
        //float4 diff;
        //float diff_alpha;
        //int index;
        float val;
        //float tmp2;
        float4 signum=sign(dir);
        if((galpha_max-galpha_min)>ACCURACY){
            //idx.y=idx.y-Yoffset;
            while(idx.x>=0&&idx.x<Vsize.x*scale.x&&idx.y>=0&&idx.y<Vsize.y*scale.x&&idx.z>=0&&idx.z<Vsize.z*scale.x){
                float4 idxF={idx.x/(get_image_width(rec)*scale.x),(idx.y-Yoffset*scale.x)/(get_image_height(rec)*scale.x), idx.z/(get_image_depth(rec)*scale.x),0};
                pixel= read_imagef(rec, samplerIn, idxF);
                val=pixel.x;
                //if(isnan(val)) val=0;
                //if(isinf(val)) val=0;
                ray_sum+=val;
                norm_sum+=1;
                idx+=dir2;
            }
            //compute diff
            if(longObjectCompensation!=0) norm_sum=longObjectCompensation;

            if(norm_sum!=0) valp.x=(pixelexp.x-ray_sum)/norm_sum*factor ;

            //if(norm_sum==0)valp.x=0;
        }

    }
    write_imagef(diff_image, posOuti, valp);
}


float getInterpolatedValue(__global float* rec, float4 pos, float4 Vsize, int Ysize){
    //int4 ipos=as_int4(pos);
    float4 d={pos.x-(int)pos.x,pos.y-(int)pos.y,pos.z-(int)pos.z,pos.w-(int)pos.w};
    int index= (int)pos.x+((int)pos.y)*Vsize.x+((int)pos.z)*Vsize.x*(Ysize);
    if(index<0) return 50000;
    float xyz=rec[index];
    //return xyz;
    if(pos.x<0||(int)pos.x>=Vsize.x-2 ||pos.y<0||(int)pos.y>=Ysize-2||pos.z<0||(int)pos.z>=Vsize.z-2)    return xyz;
    if((int) pos.x==Vsize.x-1 || (int) pos.y==Vsize.y-1 || (int) pos.y==Vsize.y-1) return  xyz;
    float x1yz=rec[index+1];
    index+=Vsize.x;
    float xy1z=rec[index];
    float x1y1z=rec[index+1];
    float upperPlane=xy1z+d.x*(x1y1z-xy1z);
    float lowerPlane=xyz+d.x*(x1yz-xyz);
    float plane=lowerPlane+d.y*(upperPlane-lowerPlane);

    index= (int)pos.x+((int)pos.y)*Vsize.x+((int)pos.z+1)*Vsize.x*Ysize;
    xyz=rec[index];
    x1yz=rec[index+1];
    index+=Vsize.x;
    xy1z=rec[index];
    x1y1z=rec[index+1];
    upperPlane=xy1z+d.x*(x1y1z-xy1z);
    lowerPlane=xyz+d.x*(x1yz-xyz);
    float plane1=lowerPlane+d.y*(upperPlane-lowerPlane);

    return plane +d.z*(plane1-plane);   //*/
}

__kernel void _7_projectBuffer_Partial_Diff_Ali(
    __global float* rec,
    __read_only image2d_t exp_proj,
    __write_only image2d_t diff_image,
    const float8 eulerT,
    const float4 dir,
    const float4 Pcenters,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float factor,
    const float longObjectCompensation,
    const float8 Tinv,
    const float2 stats,
    const float2 scale,
    const float4 deform
) {
    __private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private int2 posOuti={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private float2 posOutProj={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};

    //prefecthing read exp_prj value;
    int2 dim=get_image_dim(exp_proj);
    int Ysize= get_global_size(1)/scale.x;

    float8 inPos={posOutProj.x-Pcenters.x,posOutProj.x-Pcenters.x,posOutProj.y-Pcenters.y,posOutProj.y-Pcenters.y,1,1,1,1};
    float8 in=Tinv*inPos;
    float2 posIn = {in.s0+in.s2+in.s4+in.s6, in.s1+in.s3+in.s5+in.s7};
    //posIn.x=posOut.x;
    //posIn.y=posOut.y;
    //posIn+=0.5f;
    __private float4 valp={0,0,0,1} ;

    float4 offset={0, Yoffset*scale.x,0,0};

    bool defined= posIn.x>=0&&posIn.x<dim.x&&posIn.y>=0&&posIn.y<dim.y;
    if(defined){
        posIn+=0.5f;
         __private float4 pixelexp=read_imagef(exp_proj, samplerProj, posIn);
         if(stats.x!=0) pixelexp=(pixelexp-stats.x)/stats.y;

       // __private float4 pixelexp=read_imagef(exp_proj, samplerInNotNormalized, posOut+0.5f);

        //express r_p in universal coordinate system
        __private float2 r_p=posOut-Pcenters.hi;
        float4 dir2=normalize(dir);
        //float4 dir2=dir*dir;
        //__private float normVector=sqrt(dir2.x+dir2.y+dir2.z);
        //dir2=dir/normVector;
        //__private float josephStep = fmin(fmin(fabs(1.0f / dir.x), fabs(1.0f / dir.y)),fabs(1.0f / dir.z));

        //__private float4 pl={eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x),eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x),eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x),0};

        __private float4 pl={(eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x))*deform.x,(eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x))*deform.y,(eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x))*deform.z,0};
        //compute min and max alpha for the ray intersecting the volume
        float4 alpha_min=(-pl-0.5f)/dir;
        float4 alpha_max=(Vsize*scale.x+0.5f-pl)/dir;

       float4 minmax=fmin(alpha_min,alpha_max);
        float galpha_min= fmax(fmax(minmax.x,minmax.y),minmax.z);
        minmax=fmax(alpha_min,alpha_max);
        float galpha_max= fmin(fmin(minmax.x,minmax.y),minmax.z);

        float4 idx=dir*galpha_min+pl;

        idx=min(max(idx,zeros),Vsize*scale.x-1);
        //follow the ray
        //__private float alpha=galpha_min;
        float4 tmp=(sign(dir)/2-pl)/dir;
        __private float ray_sum=0;
        __private float norm_sum=0;
        //float4 pixel={0,0,0,1};
        //float4 alpha_xyz;
        //float4 diff;
        //float diff_alpha;
        //int index;
        float val;
        //float tmp2;
        float4 signum=sign(dir);
        if((galpha_max-galpha_min)>ACCURACY){
            //idx.y=idx.y-Yoffset;
            while(idx.x/scale.x>=0&&idx.x<Vsize.x*scale.x&&(idx.y-Yoffset*scale.x)/scale.x>=-0.5f&&(idx.y-Yoffset*scale.x)<Vsize.y*scale.x&&idx.z/scale.x>=0&&idx.z<Vsize.z*scale.x){
                float4 idxF={idx.x/scale.x,(idx.y-Yoffset*scale.x)/scale.x, idx.z/scale.x,0};
                //pixel= read_imagef(rec, samplerIn, idxF);
                //val=pixel.x;
                //val=getInterpolatedValue(rec,idx-offset,Vsize);
                val=getInterpolatedValue(rec,(idxF-offset)/scale.x,Vsize,Ysize);
                //if(isnan(val)) val=0;
                //if(isinf(val)) val=0;
                ray_sum+=val;
                norm_sum+=1;
                idx+=dir2;
            }
            //compute diff
            if(longObjectCompensation!=0) norm_sum=longObjectCompensation;

            if(norm_sum!=0) valp.x=(pixelexp.x-ray_sum)/norm_sum*factor ;
            //if(norm_sum==0)valp.x=0;
        }

    }
    write_imagef(diff_image, posOuti, valp);
}

__kernel void _8_projectBuffer_Partial(
    __global float* rec,
    __write_only image2d_t proj,
    __write_only image2d_t norm,
    const float8 eulerT,
    const float4 dir,
    const float2 Pcenter,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset,
    const float2 scale,
    const float4 deform
) {
    __private int2 posOuti={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    //__private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset};
    __private float2 r_p=posOut-Pcenter;
    //express r_p in universal coordinate system
    //float4 dir2=dir*dir;
    //__private float normVector=sqrt(dir2.x+dir2.y+dir2.z);
    //__private float normVector=length(dir);
    //dir2=dir/normVector;
    float4 dir2=normalize(dir);
    float4 offset={0,Yoffset*scale.x,0,0};
    int Ysize= get_global_size(1)/scale.x;

    //__private float4 pl={eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x),eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x),eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x),0};
    __private float4 pl={(eulerT.s0*r_p.x+eulerT.s1*r_p.y+(Vcenter.x*scale.x))*deform.x,(eulerT.s2*r_p.x+eulerT.s3*r_p.y+(Vcenter.y*scale.x))*deform.y,(eulerT.s4*r_p.x+eulerT.s5*r_p.y+(Vcenter.z*scale.x))*deform.z,0};
    //compute min and max alpha for the ray intersecting the volume
    //float4 alpha_min={-0.5f-pl.x,-0.5f-pl.y,-0.5f-pl.z,0};
    //alpha_min=(alpha_min)/dir;
    float4 alpha_min=(-pl-0.5f)/dir;
    float4 alpha_max=(Vsize*scale.x+0.5f-pl)/dir;

   float4 minmax=fmin(alpha_min,alpha_max);
    float galpha_min= fmax(fmax(minmax.x,minmax.y),minmax.z);
    minmax=fmax(alpha_min,alpha_max);
    float galpha_max= fmin(fmin(minmax.x,minmax.y),minmax.z);
    //float galpha_min= fmax(fmax(fmin(alpha_min.x,alpha_max.x),fmin(alpha_min.y,alpha_max.y)),fmin(alpha_min.z,alpha_max.z));
    //float galpha_max= fmin(fmin(fmax(alpha_min.x,alpha_max.x),fmax(alpha_min.y,alpha_max.y)),fmax(alpha_min.z,alpha_max.z));

    float4 idx=dir*galpha_min+pl;
    //float4 idx=mad(dir,galpha_min,pl);

    idx=min(max(idx,zeros),Vsize*scale.x-1);
    //follow the ray
    //__private float alpha=galpha_min;
    float4 tmp=(sign(dir)/2-pl)/dir;
    __private float ray_sum=0;
    __private float norm_sum=0;
    float4 pixel={0,0,0,1};
    //float4 alpha_xyz;
    //float4 diff;
    //float diff_alpha;
    //int index;
    float val;
    //float tmp2;
    float4 signum=sign(dir);
    if((galpha_max-galpha_min)>ACCURACY){
    //idx.y=idx.y-Yoffset;
        while(idx.x/scale.x>=0&&idx.x<Vsize.x*scale.x&&(idx.y-Yoffset*scale.x)/scale.x>=-0.5f&&(idx.y-Yoffset*scale.x)<Vsize.y*scale.x&&idx.z/scale.x>=0&&idx.z<Vsize.z*scale.x){
            val=getInterpolatedValue(rec,(idx-offset)/scale.x,Vsize,Ysize);
            //if(isnan(val)) val=0;
            //if(isinf(val)) val=0;
            ray_sum+=val;
            norm_sum+=1;
            //idx+=dir*josephStep;
            idx+=dir2;
            //idx+=dir;

        }
    } else{
    //ray_sum=5000;
    ray_sum=NAN;
    norm_sum=-1;
    }
    pixel.x=ray_sum;
    write_imagef(proj, posOuti, pixel);
    pixel.x=norm_sum;
    write_imagef(norm, posOuti, pixel);
}

#ifdef IMAGE3D_WRITE
#pragma OPENCL EXTENSION cl_khr_3d_image_writes : enable
__kernel void _7_backProject_Partial_1P(
    __read_only image3d_t recInput,
    __write_only image3d_t recOutput,
    __read_only image2d_t proj,
    const float8 euler,
    const float4 Pcenter,
    const float4 Vcenter,
    const float4 Vsize,
    const int Yoffset

) {
    int4 index={get_global_id(0),get_global_id(1),get_global_id(2),0};
    __private float4 result=read_imagef(recInput, samplerInNotNormalized, index);
   //__private float result=rec[get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w)] ;
    __private float4 r_p={get_global_id(0)-Vcenter.x,get_global_id(1)+Yoffset-Vcenter.y,get_global_id(2)-Vcenter.z,0};
    __private float xx=r_p.x*euler.s0 +r_p.y*euler.s1+r_p.z*euler.s2+Pcenter.x;
    __private float yy=r_p.x*euler.s3 +r_p.y*euler.s4+r_p.z*euler.s5+Pcenter.y;
         __private float2 posproj={(xx +0.5f)/(float)Pcenter.z, (yy+0.5f)/(float)Pcenter.w};
         __private float4 value=read_imagef(proj,samplerIn,posproj);

       if(isnan(value.x)==0){
               result.x+=value.x;
               }
       write_imagef(recOutput, index, result);
       //rec[get_global_id(0)+(int)(get_global_id(1)*Vsize.x)+(int)(get_global_id(2)*Vsize.w)] =result;
}
#endif

__kernel void squareImage(
    __read_only image2d_t input,
    __write_only image2d_t output
)  {
    int2 pos={get_global_id(0),get_global_id(1)};
    float4 val=read_imagef(input,samplerInNotNormalized,pos);
    val=val*val;
    write_imagef(output,pos,val);
}


//   theta is [cosTheta,sinTheta]
//   recDim is [ width (X), height (Z), center on X axis, center on Z axis]
//   projDim is [width , center}
//
__kernel void backproject_2D (
    __global float*  xzPlane,
    __global float*  projection1D,
    const float2 theta,
    const float4 recDim,
    const float2 projDim,
    const float scale
){
    int2 posRec={get_global_id(0),get_global_id(1)};
    int pos=posRec.x+posRec.y*recDim.x;
    float val=xzPlane[pos];
    float rx=(posRec.x-recDim.z)*theta.x + (posRec.y-recDim.w)*theta.y+projDim.y;
    int irx=(int) rx;
    if(rx>=0 &&irx<projDim.x){
        float dx=rx-irx;
        dx=fmax(dx,0);
        float value= (irx < projDim.x - 1) ? (float) (projection1D[irx] + dx * (projection1D[irx + 1] - projection1D[irx])) : projection1D[irx];
        xzPlane[pos]=val+value;
    }
//    float2 posRecin={get_global_id(0),get_global_id(1)};
//    float4 val=read_imagef(xzPlaneIn,samplerProj,posRecin) ;
//    float2 rx={(posRec.x-recDim.z)*theta.x + (posRec.y-recDim.w)*theta.y+projDim.y,0};
//    if(rx.x>=0 &&rx.x<projDim.x){
//        val+=read_imagef(projection1D,samplerProj,rx);
//        write_imagef(xzPlane,posRec,val);
//    }
//    float2 rx={(posRec.x-recDim.z)*theta.x + (posRec.y-recDim.w)*theta.y+projDim.y,0};
//
//    if(rx.x>=0 &&rx.x<projDim.x){
//        write_imagef(xzPlane,posRec,read_imagef(projection1D,samplerProj,rx));
//    }
//    float2 p=(as_float2(posRec)-recDim.hi)*theta;
//    float2 px={(p.x+p.y)*scale+projDim.s1,0};
//    if(px.s0>=0&&px.s0<projDim.s0){
//        write_imagef(xzPlane,posRec,read_imagef(projection1D,samplerProj,px));
//    }
}

__kernel void convolve(
    __read_only image3d_t rec,
    __constant float * weights,
    __global float * result,
    __private float factor
)  {
    int4 posi = {get_global_id(0), get_global_id(1), get_global_id(2),0};
    float4 pos = {get_global_id(0), get_global_id(1), get_global_id(2),0};
    pos+=0.5f;
    // Collect neighbor values and multiply with weights
    float sum = 0.0f;
    int x=-1;
    int y=-1;
    int z=-1;
    for(int index=0;index<27;index++){
        sum+=weights[index]*read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(x,y,z,0)).x;
        x++;
        if(x>=2){
            x=-1;
            y++;
            if(y>=2){
                y=-1;
                z++;
            }
        }
    }
    result[posi.x+posi.y*get_global_size(0)+posi.z*get_global_size(0)*get_global_size(1)] = sum*factor;
    //result[pos.x+pos.y*get_global_size(0)+pos.z*get_global_size(0)*get_global_size(1)] = weights[13];
}

__kernel void convolveAdd(
    __read_only image3d_t rec,
    __constant float * weights,
    __global float * result,
    __private float factorLambda,
    __private float factorAlpha
)  {
    int4 posi = {get_global_id(0), get_global_id(1), get_global_id(2),0};
    float4 pos = {get_global_id(0), get_global_id(1), get_global_id(2),0};
    pos+=0.5f;
    // Collect neighbor values and multiply with weights
    float sum = 0.0f;

    float val= read_imagef(rec,samplerInNearestNeightbor,pos).x ;
    //float val = result[pos.x+pos.y*get_global_size(0)+pos.z*get_global_size(0)*get_global_size(1)];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1, -1,-1,0)).x * weights[0];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0, -1,-1,0)).x * weights[1];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1, -1,-1,0)).x * weights[2];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1,  0,-1,0)).x * weights[3];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0,  0,-1,0)).x * weights[4];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1,  0,-1,0)).x * weights[5];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1,  1,-1,0)).x * weights[6];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0,  1,-1,0)).x * weights[7];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1,  1,-1,0)).x * weights[8];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1, -1, 0,0)).x * weights[9];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0, -1, 0,0)).x * weights[10];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1, -1, 0,0)).x * weights[11];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1,  0, 0,0)).x * weights[12];
    sum+=val * weights[13];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1,  0, 0,0)).x * weights[14];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1,  1, 0,0)).x * weights[15];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0,  1, 0,0)).x * weights[16];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1,  1, 0,0)).x * weights[17];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1, -1, 1,0)).x * weights[18];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0, -1, 1,0)).x * weights[19];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1, -1, 1,0)).x * weights[20];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1,  0, 1,0)).x * weights[21];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0,  0, 1,0)).x * weights[22];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1,  0, 1,0)).x * weights[23];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1,  1, 1,0)).x * weights[24];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 0,  1, 1,0)).x * weights[25];
    sum+=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)( 1,  1, 1,0)).x * weights[26];

    result[posi.x+posi.y*get_global_size(0)+posi.z*get_global_size(0)*get_global_size(1)] = val+sum*factorLambda*factorAlpha;
    //result[pos.x+pos.y*get_global_size(0)+pos.z*get_global_size(0)*get_global_size(1)] = factorAlpha;
}

__kernel void addImage3DtoBuffer(
    __read_only image3d_t rec,
    __global float*  diffRec,
    __private float factor,
    __private short positivityconstraint
) {
    int4 posi = {get_global_id(0), get_global_id(1), get_global_id(2),0};
   float4 pos = {get_global_id(0), get_global_id(1), get_global_id(2),0};
   pos+=0.5f;
   const int index=posi.x+posi.y*get_global_size(0)+posi.z*get_global_size(0)*get_global_size(1);
   float diff = diffRec[index]*factor;
   if(isnan(diff)) diff=0;
   if(isinf(diff)) diff=0;
   float val = read_imagef(rec,samplerInNearestNeightbor,pos).x;
   if(isnan(val)) val=0;
   if(isinf(val)) val=0;
   val+=diff;
   diffRec[index] = (positivityconstraint&&val<0)? 0 : val;
   //diffRec[index]=val;
}

__kernel void tvm( __read_only image3d_t rec,
  __global float * result,
  __private float factor
)  {
  int4 posi = {get_global_id(0), get_global_id(1), get_global_id(2),0};
  float4 pos = {get_global_id(0), get_global_id(1), get_global_id(2),0};
  pos+=0.5f;

   float sum=0.0f;
   float val=read_imagef(rec,samplerInNearestNeightbor,pos).x;
  float tmp=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(1,0,0,0)).x;
  sum+=(tmp-val)*(tmp-val);
     tmp=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(-1,0,0,0)).x;
  sum+=(val-tmp)*(val-tmp);
   tmp=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(0,1,0,0)).x;
  sum+=(tmp-val)*(tmp-val);
     tmp=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(0,-1,0,0)).x;
  sum+=(val-tmp)*(val-tmp);
   tmp=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(0,0,1,0)).x;
  sum+=(tmp-val)*(tmp-val);
     tmp=read_imagef(rec,samplerInNearestNeightbor,pos+(float4)(0,0,-1,0)).x;
  sum+=(val-tmp)*(val-tmp);

  result[posi.x+posi.y*get_global_size(0)+posi.z*get_global_size(0)*get_global_size(1)] = sqrt(sum)*factor;

}

__kernel void _10_projectImage3D_deform_Partial_Diff_Ali(
    __read_only image3d_t rec,
    __read_only image2d_t exp_proj,
    __write_only image2d_t diff_image,
    const float8 euler,
    const float4 Pcenters,
    const float4 Vcenters,
    const float4 Vsize,
    const int Yoffset,
    const float factor,
    const float longObjectCompensation,
    const float8 Tinv,
    const float2 stats,
    const float2 scale
) {
    __private float2 posOut={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private int2 posOuti={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private float2 posOutProj={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};

    //prefecthing read exp_prj value;
    int2 dim=get_image_dim(exp_proj);

    float8 inPos={posOutProj.x-Pcenters.x,posOutProj.x-Pcenters.x,posOutProj.y-Pcenters.y,posOutProj.y-Pcenters.y,1,1,1,1};
    float8 in=Tinv*inPos;
    float2 posIn = {in.s0+in.s2+in.s4+in.s6, in.s1+in.s3+in.s5+in.s7};
    //posIn.x=posOut.x;
    //posIn.y=posOut.y;
    //posIn+=0.5f;
    __private float4 valp={0,0,0,1} ;
    float val;

    bool defined= posIn.x>=0&&posIn.x<dim.x&&posIn.y>=0&&posIn.y<dim.y;
    if(defined){
        posIn+=0.5f;
         __private float4 pixelexp=read_imagef(exp_proj, samplerProj, posIn);

         if(stats.x!=0) pixelexp=(pixelexp-stats.x)/stats.y;
         __private float icenterFactorForYdetermination=euler.s3/euler.s0;
         __private float zcenterFactorForYdetermination=euler.s5-(euler.s3*euler.s2/euler.s0);
         __private float divisionFactorForYdetermination=euler.s4-(euler.s3*euler.s1/euler.s0);
         __private float dirY = -(euler.s5-euler.s3*euler.s2/euler.s0)/(euler.s4-euler.s3*euler.s1/euler.s0);
         __private float dirX = (euler.s1*(-dirY) -euler.s2)/euler.s0;
         __private float normIncrement=sqrt(dirX*dirX+dirY*dirY+1);

         __private float ray_sum=0;
         __private float norm_sum=0;
         __private float2 posInCentered=posOut-Pcenters.hi;
         float icentered= (posOut.x-Pcenters.x)/scale.x;
         float jcentered= (posOut.y-Pcenters.y)/scale.x;
         __private float oldy=((jcentered-icenterFactorForYdetermination*icentered)-zcenterFactorForYdetermination*(-1.0f-Vcenters.z))/ divisionFactorForYdetermination;
         __private float oldx=(icentered-euler.s1*oldy-euler.s2*(-1.0f-Vcenters.z))/euler.s0;
         //oldx/=divisionFactorForXdetermination;
         __private float xCentered,yCentered,x,y;
         __private bool entered=false;
         for(int z=0;z<Vsize.z;z++){
             xCentered=oldx+dirX;
             yCentered=oldy+dirY;
             x=xCentered+Vcenters.x;
             y=yCentered+Vcenters.y;
             if(x+ACCURACY>=0&&x<Vsize.x+ACCURACY&&y+ACCURACY>=0&&y<Vsize.y+ACCURACY){
                 float4 idxF={x+0.5f,y-Yoffset+0.5f, z+0.5f,0};
                 val=read_imagef(rec, samplerInNotNormalized, idxF).x*normIncrement;
                 //if(isnan(val)) val=0;
                 //if(isinf(val)) val=0;
                 ray_sum += val;
                 norm_sum+=normIncrement;
                 entered=true;
             }else{
                 if(entered){
                     norm_sum+=normIncrement;
                     break;
                 }
             }
             oldx=xCentered;
             oldy=yCentered;
         }

         //if(longObjectCompensation!=0) norm_sum=longObjectCompensation;

        if(longObjectCompensation!=0) norm_sum=longObjectCompensation;
        //compute diff
        if(norm_sum!=0) valp.x=(pixelexp.x-ray_sum)/norm_sum*factor ;
        }
        write_imagef(diff_image, posOuti, valp);
}

__kernel void _11_projectImage3D_deform_Partial_Diff(
    __read_only image3d_t rec,
    __read_only image2d_t exp_proj,
    __write_only image2d_t diff_image,
    const float8 euler,
    const float4 Pcenters,
    const float4 Vcenters,
    const float4 Vsize,
    const int Yoffset,
    const float factor,
    const float longObjectCompensation,
    const float2 scale
) {
    __private int2 posOuti={get_global_id(0),get_global_id(1)};
        __private float2 posOutProj={get_global_id(0),get_global_id(1)};
        __private float4 pixelexp=read_imagef(exp_proj, samplerInNotNormalized, posOutProj+0.5f);
        //prefecthing read exp_prj value;
        int2 dim=get_image_dim(exp_proj);
        __private float4 valp={0,0,0,1} ;
        __private float icenterFactorForYdetermination=euler.s3/euler.s0;
        __private float zcenterFactorForYdetermination=euler.s5-(euler.s3*euler.s2/euler.s0);
        __private float divisionFactorForYdetermination=euler.s4-(euler.s3*euler.s1/euler.s0);
        __private float dirY = -(euler.s5-euler.s3*euler.s2/euler.s0)/(euler.s4-euler.s3*euler.s1/euler.s0);
        __private float dirX = (euler.s1*(-dirY) -euler.s2)/euler.s0;
        __private float normIncrement=sqrt(dirX*dirX+dirY*dirY+1);

        __private float ray_sum=0;
        __private float norm_sum=0;
        __private float2 posInCentered=posOutProj-Pcenters.hi;
        float icentered= (posOutProj.x-Pcenters.x)/scale.x;
        float jcentered= (posOutProj.y-Pcenters.y)/scale.x;
        __private float oldy=((jcentered-icenterFactorForYdetermination*icentered)-zcenterFactorForYdetermination*(-1.0f-Vcenters.z))/ divisionFactorForYdetermination;
        __private float oldx=(icentered-euler.s1*oldy-euler.s2*(-1.0f-Vcenters.z))/euler.s0;
        //oldx/=divisionFactorForXdetermination;
        __private float xCentered,yCentered,x,y;
        __private bool entered=false;
        __private float val;
        for(int z=0;z<Vsize.z;z++){
            xCentered=oldx+dirX;
            yCentered=oldy+dirY;
            x=xCentered+Vcenters.x;
            y=yCentered+Vcenters.y;
            if(x+ACCURACY>=0&&x<Vsize.x+ACCURACY&&y+ACCURACY>=0&&y<Vsize.y+ACCURACY){
                float4 idxF={x+0.5f,y-Yoffset+0.5f, z+0.5f,0};
                val=read_imagef(rec, samplerInNotNormalized, idxF).x*normIncrement;
                //if(isnan(val)) val=0;
                //if(isinf(val)) val=0;
                ray_sum += val;
                norm_sum+=normIncrement;
                entered=true;
            }else{
                if(entered){
                    norm_sum+=normIncrement;
                    break;
                }
            }
            oldx=xCentered;
            oldy=yCentered;
        }


        if(longObjectCompensation!=0) norm_sum=longObjectCompensation;
        //compute diff
        if(norm_sum!=0) valp.x=(pixelexp.x-ray_sum)/norm_sum*factor ;

        write_imagef(diff_image, posOuti, valp);
}
__kernel void _12_projectImage3D_deform_Partial(
    __read_only image3d_t rec,
    __write_only image2d_t proj,
    __write_only image2d_t norm,
    const float8 euler,
    const float4 Pcenters,
    const float4 Vcenters,
    const float4 Vsize,
    const int Yoffset,
    const float2 scale
) {
    __private int2 posOuti={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};
    __private float2 posOutProj={get_global_id(0),get_global_id(1)+Yoffset*scale.x+scale.y};

    //prefecthing read exp_prj value;
    int2 dim=get_image_dim(proj);
    __private float4 pixel={0,0,0,1} ;
    __private float icenterFactorForYdetermination=euler.s3/euler.s0;
    __private float zcenterFactorForYdetermination=euler.s5-(euler.s3*euler.s2/euler.s0);
    __private float divisionFactorForYdetermination=euler.s4-(euler.s3*euler.s1/euler.s0);
    __private float dirY = -(euler.s5-euler.s3*euler.s2/euler.s0)/(euler.s4-euler.s3*euler.s1/euler.s0);
    __private float dirX = (euler.s1*(-dirY) -euler.s2)/euler.s0;
    __private float normIncrement=sqrt(dirX*dirX+dirY*dirY+1);
    __private float val;

    __private float ray_sum=0;
    __private float norm_sum=0;
    __private float2 posInCentered=posOutProj-Pcenters.hi;
    float icentered= (posOutProj.x-Pcenters.x)/scale.x;
    float jcentered= (posOutProj.y-Pcenters.y)/scale.x;
    __private float oldy=((jcentered-icenterFactorForYdetermination*icentered)-zcenterFactorForYdetermination*(-1.0f-Vcenters.z))/ divisionFactorForYdetermination;
    __private float oldx=(icentered-euler.s1*oldy-euler.s2*(-1.0f-Vcenters.z))/euler.s0;
    //oldx/=divisionFactorForXdetermination;
    __private float xCentered,yCentered,x,y;
    __private bool entered=false;
    for(int z=0;z<Vsize.z;z++){
        xCentered=oldx+dirX;
        yCentered=oldy+dirY;
        x=xCentered+Vcenters.x;
        y=yCentered+Vcenters.y;
        if(x+ACCURACY>=0&&x<Vsize.x+ACCURACY&&y+ACCURACY>=0&&y<Vsize.y+ACCURACY){
            float4 idxF={x+0.5f,y-Yoffset+0.5f, z+0.5f,0};
            val = read_imagef(rec, samplerInNotNormalized, idxF).x*normIncrement;
            //if(isnan(val)) val=0;
            //if(isinf(val)) val=0;
            ray_sum += val;
            norm_sum+=normIncrement;
            entered=true;
        }else{
            if(entered){
                norm_sum+=normIncrement;
                break;
            }
        }
        oldx=xCentered;
        oldy=yCentered;
    }

    //if(longObjectCompensation!=0) norm_sum=longObjectCompensation;
    pixel.x=ray_sum;
    write_imagef(proj, posOuti, pixel);
    pixel.x=norm_sum;
    write_imagef(norm, posOuti, pixel);

}


