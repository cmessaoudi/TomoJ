__constant sampler_t samplerIn = CLK_NORMALIZED_COORDS_TRUE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;
__constant sampler_t samplerInNotNormalized =CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;
__constant float ACCURACY = 0.0000001f;
__constant float4 zeros={0,0,0,0};


void readImageBuffer(
    __global float* image,
    __global float* patch,
     const int width,
     const int height,
     const int patchSize,
     float2 posCentered
){
long yy;
long index;
    for(int y=0;y<patchSize;y++){
        yy=(long)(posCentered.y+y-(patchSize/2)+(height/2));
        if(yy>=0&&yy<height){
            yy*=width;
            for(int x=0;x<patchSize;x++){
                //patch[y*patchSize+x]=width;
                index=yy+(long)(posCentered.x+x-patchSize/2+width/2);
                if(index>=0&&index<width*height){
                    patch[y*patchSize+x]= image[index];
                }
            }
        }
    }
}

void readImageBuffer2(
    __global float* image,
    __global float* patch,
     const int width,
     const int height,
     const int patchSize,
     float2 posCentered
){
    long lengthImg=width*height;
    long lengthPatch=patchSize*patchSize;
    int x=-1;
    int xx=posCentered.x-patchSize/2+width/2;
    int yy=posCentered.y-(patchSize/2)+(height/2);
    long indexY=yy*width;
    long index;
    for(int i=0;i<lengthPatch;i++){
        x++;
        if(x==patchSize){
            x=0;
            yy++;
            indexY+=width;
        }
        if(yy>=0&&yy<height){
            index=indexY+xx+x;
            if(index>=0&&index<lengthImg) patch[i]=image[index];
        }
    }
}

void readImageBufferApplyT(
    __global float* image,
    __global float* patch,
    __global float* Tinv,
    __global float* stats,
    const int width,
    const int height,
    const int patchSize,
    const float2 posCentered
){
    __private const float tx=Tinv[4];
    __private const float ty=Tinv[5];
    __private int ix0;
    __private int iy0;
    __private float dx0;
    __private float dy0;
    __private float fac4;
    __private float val;
    for(int y=0;y<patchSize;y++){
        float yy=posCentered.y+y-(patchSize/2);
        __private float ry1=Tinv[2]*yy;
        __private float ry2=Tinv[3]*yy;
            for(int x=0;x<patchSize;x++){
                float xx=posCentered.x+x-patchSize/2;
                float rx=Tinv[0]*xx+ry1+tx+width/2;
                float ry=Tinv[1]*xx+ry2+ty+height/2;
                long index=((long)ry)*width+(long)rx;
                if(ry>=0&&ry<height&&rx>=0&&rx<width){
                    if(rx<width-1&&ry<height-1){
                    //linear interpolation
                        float val3=image[index+width];
                        float val4=image[index+width+1];
                        ix0 = (int) rx;
                        iy0 = (int) ry;
                        dx0 = rx - ix0;
                        dy0 = ry - iy0;
                        fac4 = (dx0 * dy0);
                        val=(dy0-fac4)*val3+fac4*val4;
                        float val1=image[index];
                        float val2=image[index+1];
                        val+=(1-dx0-dy0+fac4)*val1+(dx0-fac4)*val2;
                        //val=val1+val2+val3+val4;

                    }else{
                         val=image[index];
                    }
                    val=(val-stats[0])/stats[1];
                    patch[y*patchSize+x]=val;
                }
            }

    }
}


float computeCorrelation(__global float* ref, __global float* mov, const long length){
    float avg1 = 0;
    float avg2 = 0;
    long tot = 0;
    for (int i = 0; i < length; i++) {
        avg1 += ref[i];
        avg2 += mov[i];
        tot++;
    }
    avg1 /= tot;
    avg2 /= tot;
    float sum1 = 0;
    float sum2 = 0;
    float sum3 = 0;
    float val1;
    float val2;
    for (int i = 0; i < length; i++) {
        val1 = (ref[i] - avg1);
        val2 = (mov[i] - avg2);
        sum1 += val1 * val2;
        sum2 += val1 * val1;
        sum3 += val2 * val2;
        //tot++;
    }
    sum2=sqrt(sum2*sum3);
    if(sum2==0)sum2=0.00001;
    //return sum1 / sqrt(sum2 * sum3);
    return sum1/sum2;
}

float computeMSD(__global float* ref, __global float* mov, const long length){
    float sum = 0;
    float tmp;
    for (int i = 0; i < length; i++) {
        tmp=ref[i]-mov[i];
        sum+=tmp*tmp;
    }
    return sum/length;
}

float4 refineLandmark(
    __global float* tiltSeries,
    __global float* ref,
    __global float* patch,
    float2 positionToRefine,
    const int width,
    const int height,
    const int patchSize
){
    int p2=(int)patchSize/2;
    float maxCorr=-20;
    float4 bestPos={-20,400,-80,0};
    float corr;
    float2 pos;
    float avg1 = 0;
    float avg2 = 0;
    long tot = 0;
    //float sum1 = 0;
    float sum2 = 0;
    float sum3 = 0;
    float val1;
    float val2;
    const long length=patchSize*patchSize;
    for(int ty=-p2;ty<=p2;ty++){
        for(int tx=-p2;tx<=p2;tx++){
            pos.x=positionToRefine.x+tx;
            pos.y=positionToRefine.y+ty;
            readImageBuffer(tiltSeries,patch, width,height,patchSize,pos);
            //corr=computeCorrelation(ref,patch,patchSize*patchSize);
            avg1 = 0;
            avg2 = 0;
            tot = 0;
            for (int i = 0; i < length; i++) {
                avg1 += ref[i];
                avg2 += patch[i];
                tot++;
            }
            avg1 /= tot;
            avg2 /= tot;
            corr = 0;
            sum2 = 0;
            sum3 = 0;
            for (int i = 0; i < length; i++) {
                val1 = (ref[i] - avg1);
                val2 = (patch[i] - avg2);
                corr += val1 * val2;
                sum2 += val1 * val1;
                sum3 += val2 * val2;
                //tot++;
            }
            sum2=sqrt(sum2*sum3);
            if(sum2==0)sum2=0.00001;
            corr/= sum2;

            if(corr>maxCorr){
                maxCorr=corr;
                bestPos.x=pos.x;
                bestPos.y=pos.y;
                //bestPos.z+=1;
            }
        }
    }
    bestPos.z=maxCorr;
    return bestPos;
}


__kernel void followSeed(
    __global float* tiltseries,
    __global float* patches,
    __global float* posList,
    __global float* T,
    __global float* Tinv,
    __global float* stats,
     const int width,
     const int height,
     const int nImages,
     const int patchSize,
     const int seqLength
){
    int pos=get_global_id(0);
    long startingPos=get_global_id(0)*(seqLength*3+1)+seqLength/2*3;
    long writingPos=startingPos+3;
    int startImgIndex=(int)posList[startingPos];
    float2 posCentered={(int)posList[startingPos+1],(int)posList[startingPos+2]};
    int imgIndex=startImgIndex;
    float worseCorr=50;
    int movIndex;
    long Tindex;

    const int p2=(int)patchSize/2;
    float maxCorr=-20;
    float4 refined={-20,400,-80,0};
    float corr;
    float2 posCorr;
    float avg1 = 0;
    float avg2 = 0;
    long tot = 0;
    //float sum1 = 0;
    float sum2 = 0;
    float sum3 = 0;
    float val1;
    float val2;
    const long length=patchSize*patchSize;
    global float* ref;
    global float* patch;
    float2 posToRefine;


    //forward
    for(int i =0; i<seqLength/2;i++){
        ref=patches+(pos*2)*patchSize*patchSize;
        readImageBuffer(tiltseries+imgIndex*width*height,ref, width,height,patchSize,posCentered);
        //float2 posToRefine=posCentered;
        movIndex=imgIndex+1;
        Tindex=imgIndex*6;
        posToRefine.x=T[Tindex]*posCentered.x+T[Tindex+2]*posCentered.y+T[Tindex+4];
        posToRefine.y=T[Tindex+1]*posCentered.x+T[Tindex+3]*posCentered.y+T[Tindex+5];
        if(movIndex<nImages){
            //float4 refined=refineLandmark(tiltseries+movIndex*width*height,patches+(pos*2)*patchSize*patchSize,patches+(pos*2+1)*patchSize*patchSize,posToRefine,width,height,patchSize);

                maxCorr=-20;
                for(int ty=-p2;ty<=p2;ty++){
                    for(int tx=-p2;tx<=p2;tx++){
                        posCorr.x=posToRefine.x+tx;
                        posCorr.y=posToRefine.y+ty;
                        patch=patches+(pos*2+1)*patchSize*patchSize;
                        readImageBuffer(tiltseries+movIndex*width*height,patch, width,height,patchSize,posCorr);
                        //corr=computeCorrelation(ref,patch,patchSize*patchSize);
                        avg1 = 0;
                        avg2 = 0;
                        tot = 0;
                        for (int i = 0; i < length; i++) {
                            avg1 += ref[i];
                            avg2 += patch[i];
                            tot++;
                        }
                        avg1 /= tot;
                        avg2 /= tot;
                        corr = 0;
                        sum2 = 0;
                        sum3 = 0;
                        for (int i = 0; i < length; i++) {
                            val1 = (ref[i] - avg1);
                            val2 = (patch[i] - avg2);
                            corr += val1 * val2;
                            sum2 += val1 * val1;
                            sum3 += val2 * val2;
                            //tot++;
                        }
                        sum2=sqrt(sum2*sum3);
                        if(sum2==0)sum2=0.00001;
                        corr/= sum2;

                        if(corr>maxCorr){
                            maxCorr=corr;
                            refined.x=posCorr.x;
                            refined.y=posCorr.y;
                            //bestPos.z+=1;
                        }
                    }
                }
                refined.z=maxCorr;
            posList[writingPos]=movIndex;
            posList[writingPos+1]=refined.x;
            posList[writingPos+2]=refined.y;
            imgIndex++;
            writingPos+=3;
            posCentered.x=refined.x;
            posCentered.y=refined.y;
            if(refined.z<worseCorr)worseCorr=refined.z;
        }
    }
    //backward
    /*imgIndex=startImgIndex;
    writingPos=startingPos-3;
    posCentered.x=(int)posList[startingPos+1];
    posCentered.y=(int)posList[startingPos+2];
    for(int i =0; i<seqLength/2;i++){
        ref=patches+(pos*2)*patchSize*patchSize;
        readImageBuffer(tiltseries+imgIndex*width*height,ref, width,height,patchSize,posCentered);
            //readImageBuffer(tiltseries+imgIndex*width*height,patches+(pos*2)*patchSize*patchSize, width,height,patchSize,posCentered);
            movIndex=imgIndex-1;
            //float2 posToRefine=posCentered;
            Tindex=movIndex*6;
            float2 posToRefine={Tinv[Tindex]*posCentered.x+Tinv[Tindex+2]*posCentered.y+Tinv[Tindex+4],Tinv[Tindex+1]*posCentered.x+Tinv[Tindex+3]*posCentered.y+Tinv[Tindex+5]};
            if(movIndex>=0){
                //float4 refined=refineLandmark(tiltseries+movIndex*width*height,patches+(pos*2)*patchSize*patchSize,patches+(pos*2+1)*patchSize*patchSize,posToRefine,width,height,patchSize);
                maxCorr=-20;
                for(int ty=-p2;ty<=p2;ty++){
                    for(int tx=-p2;tx<=p2;tx++){
                        posCorr.x=posToRefine.x+tx;
                        posCorr.y=posToRefine.y+ty;
                        patch=patches+(pos*2+1)*patchSize*patchSize;
                        readImageBuffer(tiltseries+movIndex*width*height,patch, width,height,patchSize,posCorr);
                        //corr=computeCorrelation(ref,patch,patchSize*patchSize);
                        avg1 = 0;
                        avg2 = 0;
                        tot = 0;
                        for (int i = 0; i < length; i++) {
                            avg1 += ref[i];
                            avg2 += patch[i];
                            tot++;
                        }
                        avg1 /= tot;
                        avg2 /= tot;
                        corr = 0;
                        sum2 = 0;
                        sum3 = 0;
                        for (int i = 0; i < length; i++) {
                            val1 = (ref[i] - avg1);
                            val2 = (patch[i] - avg2);
                            corr += val1 * val2;
                            sum2 += val1 * val1;
                            sum3 += val2 * val2;
                            //tot++;
                        }
                        sum2=sqrt(sum2*sum3);
                        if(sum2==0)sum2=0.00001;
                        corr/= sum2;

                        if(corr>maxCorr){
                            maxCorr=corr;
                            refined.x=posCorr.x;
                            refined.y=posCorr.y;
                            //bestPos.z+=1;
                        }
                    }
                }
                refined.z=maxCorr;
                posList[writingPos]=movIndex;
                posList[writingPos+1]=refined.x;
                posList[writingPos+2]=refined.y;
                //posList[writingPos+1]=5423;
                //posList[writingPos+2]=123456;
                imgIndex--;
                writingPos-=3;
                posCentered.x=refined.x;
                posCentered.y=refined.y;
                if(refined.z<worseCorr)worseCorr=refined.z;
            }
        }
     */

    posList[pos*(seqLength*3+1)+seqLength*3]=worseCorr;;




}

__kernel void followSeed_1Img(
    __read_only image2d_t ref,
     __read_only image2d_t mov,
     __global float* posref,
     __global float* posmov,
     __global float* T,
    __global float* stats,
    const int patchSize,
    const int width,
    const int height,
    const short refine
){
    int pos=get_global_id(0);
    long startingPos=get_global_id(0)*2;
    long writingPos=get_global_id(0)*3;
    //get position on reference image
    float2 posCentered={posref[startingPos],posref[startingPos+1]};
    //is it defined (inside image)? No, nothing to do
    if(posCentered.x<-width/2||posCentered.x>width/2||posCentered.y<-height/2||posCentered.y>height/2) {
        posmov[writingPos+2]=-20;
        return;
    }
    //get position on moving image
    float2 posToRefine={posmov[writingPos],posmov[writingPos+1]};
    //is it defined (inside image)? yes nothing to do
    //if(posToRefine.x>-width/2&&posToRefine.x<width/2&&posToRefine.y>-height/2&&posToRefine.y<height/2) return;
    //is it undefined yes create position from posCentered
    if(posToRefine.x<-width/2||posToRefine.x>width/2||posToRefine.y<-height/2||posToRefine.y>height/2){
        // theoritical position of landmark if perfectly aligned
        posToRefine.x=T[0]*posCentered.x+T[2]*posCentered.y+T[4];
        posToRefine.y=T[1]*posCentered.x+T[3]*posCentered.y+T[5];
    } else if(refine==0) {
        posmov[writingPos+2]=-40;
        return;
    }

    float msd;
    float maxMSD=-5000000;
    int p2=patchSize/2;
    float2 center={width/2,height/2};
    float2 size={width,height};
    float lengthPatch=patchSize*patchSize;
    float2 refined;
    refined=posToRefine;

    //precoomputing of avg1
    float avg1=0;
    float avg2=0;
    long tot=0;
    for(int posy=-p2;posy<=p2;posy++){
        for(int posx=-p2;posx<=p2;posx++){
        float2 pos={posx,posy};
            float4 pixRef=read_imagef(ref,samplerInNotNormalized,(posCentered+center+pos));
            avg1+=pixRef.x;
            float4 pixMov=read_imagef(mov,samplerInNotNormalized,(posToRefine+center+pos));
            avg2+=pixMov.x;
            tot++;
            //printf("(%i,%i) pos(%i,%i) avg1=%f avg2=%f\n",tx,ty,posx,posy,avg1,avg2);
        }
    }
    avg1 /= tot;
    avg2 /= tot;

    //refine position for shifts in patch
    for(int ty=-p2;ty<=p2;ty++){
        for(int tx=-p2;tx<=p2;tx++){
            float2 transl={tx,ty};
            //compute correlation
            msd=0;
            /*float avg2 = 0;

            for(int posy=-p2;posy<=p2;posy++){
                for(int posx=-p2;posx<=p2;posx++){
                float2 pos={posx,posy};
                    float4 pixMov=read_imagef(mov,samplerInNotNormalized,(posToRefine+transl+center+pos));
                    avg2+=pixMov.x;
                    //printf("(%i,%i) pos(%i,%i) avg1=%f avg2=%f\n",tx,ty,posx,posy,avg1,avg2);
                }
            }
            avg2 /= tot;   */
            //printf("avg1=%f avg2=%f\n", avg1, avg2);
            float sum1 = 0;
            float sum2 = 0;
            float sum3 = 0;
            float val1;
            float val2;
            for(int posy=-p2;posy<=p2;posy++){
                for(int posx=-p2;posx<=p2;posx++){
                    float2 pos={posx,posy};
                    float4 pixRef=read_imagef(ref,samplerInNotNormalized,(posCentered+center+pos));
                    float4 pixMov=read_imagef(mov,samplerInNotNormalized,(posToRefine+transl+center+pos));
                    val1 = (pixRef - avg1).x;
                    //printf("pos= %v2hlf pixRef=%v4hlf avg1=%f val1=%f\n",posCentered pixRef,avg1,val1);
                    val2 = (pixMov.x) - avg2;
                    sum1 += val1 * val2;
                    sum2 += val1 * val1;
                    sum3 += val2 * val2;
                    /*if(posx==0&&posy==0){
                        tmp=pixMov.x;
                        tmp2=val2;
                    }*/
                //tot++;
                }
            }
            float sum4=sqrt(sum2*sum3);
            if(sum4==0)sum4=0.00001;
            sum1=fabs(sum1);
            //return sum1 / sqrt(sum2 * sum3);
            msd= sum1/sum4;
            //printf("(%i,%i) avg1=%f avg2=%f sum1=%f, sum2=%f, sum3=%f, sum4=%f, msd=%f\n",tx,ty,avg1,avg2,sum1,sum2,sum3,sum4,msd);
            //msd= sum1;
            //beter score ?
            if(msd>maxMSD){
                maxMSD=msd;
                refined=posToRefine+transl;
                //refined.x=sum1;
               //refined.y=sum4;
            }

        }
    }
    //write result
    posmov[writingPos]=refined.x;
    posmov[writingPos+1]=refined.y;
    posmov[writingPos+2]=maxMSD;
    //posmov[writingPos+2]=msd;
}

__kernel void followSeed_1ImgBuffer(
    __global float* ref,
     __global float* mov,
     __global float* posref,
     __global float* posmov,
     __global float* T,
    __global float* stats,
    const int patchSize,
    const int width,
    const int height,
    const short refine
){
    int pos=get_global_id(0);
    long startingPos=get_global_id(0)*2;
    long writingPos=get_global_id(0)*3;
    //get position on reference image
    float2 posCentered={posref[startingPos],posref[startingPos+1]};
    //is it defined (inside image)? No, nothing to do
    if(posCentered.x<-width/2||posCentered.x>width/2||posCentered.y<-height/2||posCentered.y>height/2) {
        posmov[writingPos+2]=-20;
        return;
    }
    //get position on moving image
    float2 posToRefine={posmov[writingPos],posmov[writingPos+1]};
    //is it defined (inside image)? yes nothing to do
    //if(posToRefine.x>-width/2&&posToRefine.x<width/2&&posToRefine.y>-height/2&&posToRefine.y<height/2) return;
    //is it undefined yes create position from posCentered
    if(posToRefine.x<-width/2||posToRefine.x>width/2||posToRefine.y<-height/2||posToRefine.y>height/2){
        // theoritical position of landmark if perfectly aligned
        posToRefine.x=T[0]*posCentered.x+T[2]*posCentered.y+T[4];
        posToRefine.y=T[1]*posCentered.x+T[3]*posCentered.y+T[5];
    } else if(refine==0) {
        posmov[writingPos+2]=-40;
        return;
    }

    //printf("ref (%f,%f), mov(%f,%f)\n",posCentered.x,posCentered.y, posToRefine.x,posToRefine.y);

    float msd;
    float maxMSD=-5000000;
    //int xx,yy;
    //long indexY,index;
    //float tmp;
    int p2=patchSize/2;
    float2 center={width/2,height/2};
    float2 size={width,height};
    float lengthPatch=patchSize*patchSize;
    float2 refined;
    refined=posToRefine;

    //precoomputing of avg1
    float avg1=0;
    float sum2_1=0;
    float avg2=0;
    float val0,val1,val2,val3,val4,val5,val6,val7;
    int tot=0;
    float8 valuesRef;
    for(int posy=-p2;posy<=p2;posy++){
        for(int posx=-p2;posx<p2;posx+=8){
            float2 pos={posx,posy};
            int2 coord=convert_int2(posCentered+center+pos);
            //float8 valuesRef=vload8((coord.y*width+coord.x+1)/8,ref);
            int index=coord.y*width+coord.x;
            val0=ref[index];
            val1=ref[index+1];
            val2=ref[index+2];
            val3=ref[index+3];
            val4=ref[index+4];
            val5=ref[index+5];
            val6=ref[index+6];
            val7=ref[index+7];
            avg1+=val0;
            avg1+=val1;
            avg1+=val2;
            avg1+=val3;
            avg1+=val4;
            avg1+=val5;
            avg1+=val6;
            avg1+=val7;
            /*sum2_1+=val0*val0;
            sum2_1+=val1*val1;
            sum2_1+=val2*val2;
            sum2_1+=val3*val3;
            sum2_1+=val4*val4;
            sum2_1+=val5*val5;
            sum2_1+=val6*val6;
            sum2_1+=val7*val7;  */
            tot+=8;
        }
    }
    avg1 /= tot;
    //float std1=sqrt(sum2_1/tot-(avg1*avg1));
    //printf("(%f,%f) patchSize=%i p2=%i sum=%f, sum2=%f, tot=%i avg1=%f, std1=%f\n",posCentered.x,posCentered.y, patchSize, p2, avg1*tot,sum2_1,tot,avg1,std1);
    float std2=0;
   // avg2 /= tot;

    //printf("avg1:%f, avg2:%f\n", avg1, avg2);

    //refine position for shifts in patch
    for(int ty=-p2;ty<=p2;ty++){
        for(int tx=-p2;tx<=p2;tx++){
            float2 transl={tx,ty};
            //compute correlation
            msd=0;
            avg2=0;
            sum2_1=0;
            for(int posy=-p2;posy<=p2;posy++){
                    for(int posx=-p2;posx<p2;posx+=8){
                        float2 pos={posx,posy};

                        int2 coord=convert_int2(posToRefine+center+transl+pos);
                        int index=coord.y*width+coord.x;
                        val0=mov[index];
                        val1=mov[index+1];
                        val2=mov[index+2];
                        val3=mov[index+3];
                        val4=mov[index+4];
                        val5=mov[index+5];
                        val6=mov[index+6];
                        val7=mov[index+7];
                        avg2+=val0;
                        avg2+=val1;
                        avg2+=val2;
                        avg2+=val3;
                        avg2+=val4;
                        avg2+=val5;
                        avg2+=val6;
                        avg2+=val7;
                        /*sum2_1+=val0*val0;
                        sum2_1+=val1*val1;
                        sum2_1+=val2*val2;
                        sum2_1+=val3*val3;
                        sum2_1+=val4*val4;
                        sum2_1+=val5*val5;
                        sum2_1+=val6*val6;
                        sum2_1+=val7*val7; */
                    }
                }
                avg2 /= tot;
                //std2=sqrt(sum2_1/tot-avg2*avg2);

            //printf("avg1=%f avg2=%f\n", avg1, avg2);
            float sum1 = 0;
            float sum2= 0;
            float sum3=0;
            //float tmp;
            //float tmp2;
            for(int posy=-p2;posy<=p2;posy++){
                for(int posx=-p2;posx<p2;posx+=8){
                    float2 pos={posx,posy};
                    int2 coord=convert_int2(posCentered+center+pos);
                    long index=coord.y*width+coord.x;
                    //float pixRef=ref[coord.y*width+coord.x];
                    //val1 = pixRef - avg1;
                    val0=(ref[index]-avg1);
                    val1=(ref[index+1]-avg1);
                    val2=(ref[index+2]-avg1);
                    val3=(ref[index+3]-avg1);
                    val4=(ref[index+4]-avg1);
                    val5=(ref[index+5]-avg1);
                    val6=(ref[index+6]-avg1);
                    val7=(ref[index+7]-avg1);

                    coord=convert_int2(posToRefine+center+pos+transl);
                    //printf("convert before (%v2hlf) after (%i,%i)\n",posToRefine+center+pos+transl,coord.x,coord.y);
                    index=coord.y*width+coord.x;
                    //float8 pixMov=vload8(coord.y*width+coord.x,mov);
                    index=coord.y*width+coord.x;
                    float val0_2=(mov[index]-avg2);
                    float val1_2=(mov[index+1]-avg2);
                    float val2_2=(mov[index+2]-avg2);
                    float val3_2=(mov[index+3]-avg2);
                    float val4_2=(mov[index+4]-avg2);
                    float val5_2=(mov[index+5]-avg2);
                    float val6_2=(mov[index+6]-avg2);
                    float val7_2=(mov[index+7]-avg2);


                    //printf("pos= %v2hlf pixRef=%v4hlf avg1=%f val1=%f\n",posCentered pixRef,avg1,val1);
                    /*sum1 += (val0 - val0_2)*(val0 - val0_2);
                    sum1 += (val1 - val1_2)*(val1 - val1_2);
                    sum1 += (val2 - val2_2)*(val2 - val2_2);
                    sum1 += (val3 - val3_2)*(val3 - val3_2);
                    sum1 += (val4 - val4_2)*(val4 - val4_2);
                    sum1 += (val5 - val5_2)*(val5 - val5_2);
                    sum1 += (val6 - val6_2)*(val6 - val6_2);
                    sum1 += (val7 - val7_2)*(val7 - val7_2); */


                    sum1 += val0 * val0_2;
                    sum1 += val1 * val1_2;
                    sum1 += val2 * val2_2;
                    sum1 += val3 * val3_2;
                    sum1 += val4 * val4_2;
                    sum1 += val5 * val5_2;
                    sum1 += val6 * val6_2;
                    sum1 += val7 * val7_2;

                    sum2+= val0*val0;
                    sum2+= val1*val1;
                    sum2+= val2*val2;
                    sum2+= val3*val3;
                    sum2+= val4*val4;
                    sum2+= val5*val5;
                    sum2+= val6*val6;
                    sum2+= val7*val7;

                    sum3+= val0_2*val0_2;
                    sum3+= val1_2*val1_2;
                    sum3+= val2_2*val2_2;
                    sum3+= val3_2*val3_2;
                    sum3+= val4_2*val4_2;
                    sum3+= val5_2*val5_2;
                    sum3+= val6_2*val6_2;
                    sum3+= val7_2*val7_2;


                //tot++;
                }
            }
            float sum4=sum2*sum3;
            if(sum4<0.00000001) sum4=1;
            msd=fabs(sum1)/native_sqrt(sum4);
            //msd= sum1/(std1*std2);
            //msd/=tot;
            //printf("(%i,%i) avg1=%f avg2=%f sum1=%f, sum2=%f, sum3=%f, sum4=%f, msd=%f\n",tx,ty,avg1,avg2,sum1,sum2,sum3,sum4,msd);
            //msd= sum1;
            //msd=(sum1/(std1*std2))/tot;
            //printf("(%i,%i) avg1=%f std1=%f avg2=%f, std2=%f, sum1=%f, msd=%f\n",tx,ty,avg1,std1,avg2,std2,sum1,msd);
            //msd=sum1/tot;
            //beter score ?
            //printf("(%i,%i) maxmsd:%f , msd:%f , posToRefine:%v2hlf , refined:%v2hlf\n",tx,ty,maxMSD,msd,(posToRefine+center),(refined+center));
            if((msd)>maxMSD){
                maxMSD=msd;
                refined=posToRefine+transl;
                //printf("changing best score with : %v2hlf\n",transl);
            }


        }

    }

    //write result
    posmov[writingPos]=refined.x;
    posmov[writingPos+1]=refined.y;
    posmov[writingPos+2]=maxMSD;
    //posmov[writingPos+2]=msd;
    //printf("######best posToRefine:%v2hlf , refined : %v2hlf\n",posToRefine+center, refined+center);
}



float correlation(__global float* data1, __global float* data2, int width, int height,float2 coord1, float2 coord2, int patchSize, float avg1, float stdDev1){
    int p2=patchSize/2;
    float avg2=0;
    float std2;
    float corr=0;
    float sum2_2=0;
    float2 center={width/2,height/2};
    float val0,val1,val2,val3,val4,val5,val6,val7;
    int tot=0;
    for(int posy=-p2;posy<=p2;posy++){
        for(int posx=-p2;posx<p2;posx+=8){
            float2 pos={posx,posy};
            int2 coord=convert_int2(coord2+center+pos);
            int index=coord.y*width+coord.x;
            val0=data2[index];
            val1=data2[index+1];
            val2=data2[index+2];
            val3=data2[index+3];
            val4=data2[index+4];
            val5=data2[index+5];
            val6=data2[index+6];
            val7=data2[index+7];
            avg2+=val0;
            avg2+=val1;
            avg2+=val2;
            avg2+=val3;
            avg2+=val4;
            avg2+=val5;
            avg2+=val6;
            avg2+=val7;
            sum2_2+=val0*val0;
            sum2_2+=val1*val1;
            sum2_2+=val2*val2;
            sum2_2+=val3*val3;
            sum2_2+=val4*val4;
            sum2_2+=val5*val5;
            sum2_2+=val6*val6;
            sum2_2+=val7*val7;
            tot+=8;
        }
    }
    avg2 /= tot;
    std2=sqrt(sum2_2/tot-avg2*avg2);

    //printf("avg1=%f avg2=%f\n", avg1, avg2);
    float sum1 = 0;
    float sum2= 0;
    float sum3=0;
    //float tmp;
    //float tmp2;
    for(int posy=-p2;posy<=p2;posy++){
        for(int posx=-p2;posx<p2;posx+=8){
            float2 pos={posx,posy};
            int2 coord=convert_int2(coord1+center+pos);
            long index=coord.y*width+coord.x;
            //float pixRef=ref[coord.y*width+coord.x];
            //val1 = pixRef - avg1;
            val0=(data1[index]-avg1);
            val1=(data1[index+1]-avg1);
            val2=(data1[index+2]-avg1);
            val3=(data1[index+3]-avg1);
            val4=(data1[index+4]-avg1);
            val5=(data1[index+5]-avg1);
            val6=(data1[index+6]-avg1);
            val7=(data1[index+7]-avg1);

            coord=convert_int2(coord2+center+pos);
            //printf("convert before (%v2hlf) after (%i,%i)\n",posToRefine+center+pos+transl,coord.x,coord.y);
            index=coord.y*width+coord.x;
            //float8 pixMov=vload8(coord.y*width+coord.x,mov);
            index=coord.y*width+coord.x;
            float val0_2=(data2[index]-avg2);
            float val1_2=(data2[index+1]-avg2);
            float val2_2=(data2[index+2]-avg2);
            float val3_2=(data2[index+3]-avg2);
            float val4_2=(data2[index+4]-avg2);
            float val5_2=(data2[index+5]-avg2);
            float val6_2=(data2[index+6]-avg2);
            float val7_2=(data2[index+7]-avg2);


            //printf("pos= %v2hlf pixRef=%v4hlf avg1=%f val1=%f\n",posCentered pixRef,avg1,val1);
            /*sum1 += (val0 - val0_2)*(val0 - val0_2);
            sum1 += (val1 - val1_2)*(val1 - val1_2);
            sum1 += (val2 - val2_2)*(val2 - val2_2);
            sum1 += (val3 - val3_2)*(val3 - val3_2);
            sum1 += (val4 - val4_2)*(val4 - val4_2);
            sum1 += (val5 - val5_2)*(val5 - val5_2);
            sum1 += (val6 - val6_2)*(val6 - val6_2);
            sum1 += (val7 - val7_2)*(val7 - val7_2); */


            sum1 += val0 * val0_2;
            sum1 += val1 * val1_2;
            sum1 += val2 * val2_2;
            sum1 += val3 * val3_2;
            sum1 += val4 * val4_2;
            sum1 += val5 * val5_2;
            sum1 += val6 * val6_2;
            sum1 += val7 * val7_2;

            /*sum2+= val0*val0;
            sum2+= val1*val1;
            sum2+= val2*val2;
            sum2+= val3*val3;
            sum2+= val4*val4;
            sum2+= val5*val5;
            sum2+= val6*val6;
            sum2+= val7*val7;

            sum3+= val0_2*val0_2;
            sum3+= val1_2*val1_2;
            sum3+= val2_2*val2_2;
            sum3+= val3_2*val3_2;
            sum3+= val4_2*val4_2;
            sum3+= val5_2*val5_2;
            sum3+= val6_2*val6_2;
            sum3+= val7_2*val7_2;  //*/


        //tot++;
        }
    }
    //float sum4=sum2*sum3;
    //if(sum4<0.00000001) sum4=1;
    //msd=fabs(sum1)/native_sqrt(sum4);
    return (sum1/(stdDev1*std2))/tot;

}

__kernel void testCorrelation2(
    __global float* data1,
     __global float* data2,
    const int width,
    const int height,
     __global float* posref,
     __global float* posmov,
    const int patchSize
){
    int pos=get_global_id(0);
    long startingPos=get_global_id(0)*2;
    long writingPos=get_global_id(0)*3;
    //get position on reference image
    float2 posCentered={posref[startingPos],posref[startingPos+1]};
    //is it defined (inside image)? No, nothing to do
    if(posCentered.x<-width/2||posCentered.x>width/2||posCentered.y<-height/2||posCentered.y>height/2) {
        posmov[writingPos+2]=-20;
        return;
    }
    //get position on moving image
    float2 posToRefine={posmov[writingPos],posmov[writingPos+1]};

    float msd;
    int p2=patchSize/2;
    float2 center={width/2,height/2};
    float2 size={width,height};
    float lengthPatch=patchSize*patchSize;
    float2 refined;
    refined=posToRefine;

    //precoomputing of avg1
    float avg1=0;
    float sum2_1=0;
    float val0,val1,val2,val3,val4,val5,val6,val7;
    int tot=0;
    for(int posy=-p2;posy<=p2;posy++){
        for(int posx=-p2;posx<p2;posx+=8){
            float2 pos={posx,posy};
            int2 coord=convert_int2(posCentered+center+pos);
            //float8 valuesRef=vload8((coord.y*width+coord.x+1)/8,ref);
            int index=coord.y*width+coord.x;
            val0=data1[index];
            val1=data1[index+1];
            val2=data1[index+2];
            val3=data1[index+3];
            val4=data1[index+4];
            val5=data1[index+5];
            val6=data1[index+6];
            val7=data1[index+7];
            avg1+=val0;
            avg1+=val1;
            avg1+=val2;
            avg1+=val3;
            avg1+=val4;
            avg1+=val5;
            avg1+=val6;
            avg1+=val7;
            sum2_1+=val0*val0;
            sum2_1+=val1*val1;
            sum2_1+=val2*val2;
            sum2_1+=val3*val3;
            sum2_1+=val4*val4;
            sum2_1+=val5*val5;
            sum2_1+=val6*val6;
            sum2_1+=val7*val7;  //*/
            tot+=8;
        }
    }
    avg1 /= tot;
    float std1=sqrt(sum2_1/tot-(avg1*avg1));
    msd=correlation(data1,data2,width,height,posCentered,posToRefine,patchSize,avg1,std1);
    //posmov[writingPos]=refined.x;
    //posmov[writingPos+1]=refined.y;
    posmov[writingPos+2]=msd;
}


__kernel void followSeed_1ImgBuffer2(
    __global float* ref,
     __global float* mov,
     __global float* posref,
     __global float* posmov,
     __global float* T,
    __global float* stats,
    const int patchSize,
    const int width,
    const int height,
    const short refine
){
    int pos=get_global_id(0);
    long startingPos=get_global_id(0)*2;
    long writingPos=get_global_id(0)*3;
    //get position on reference image
    float2 posCentered={posref[startingPos],posref[startingPos+1]};
    //is it defined (inside image)? No, nothing to do
    if(posCentered.x<-width/2||posCentered.x>width/2||posCentered.y<-height/2||posCentered.y>height/2) {
        posmov[writingPos+2]=-20;
        return;
    }
    //get position on moving image
    float2 posToRefine={posmov[writingPos],posmov[writingPos+1]};
    //is it defined (inside image)? yes nothing to do
    //if(posToRefine.x>-width/2&&posToRefine.x<width/2&&posToRefine.y>-height/2&&posToRefine.y<height/2) return;
    //is it undefined yes create position from posCentered
    if(posToRefine.x<-width/2||posToRefine.x>width/2||posToRefine.y<-height/2||posToRefine.y>height/2){
        // theoritical position of landmark if perfectly aligned
        posToRefine.x=T[0]*posCentered.x+T[2]*posCentered.y+T[4];
        posToRefine.y=T[1]*posCentered.x+T[3]*posCentered.y+T[5];
    } else if(refine==0) {
        posmov[writingPos+2]=-40;
        return;
    }

    //printf("ref (%f,%f), mov(%f,%f)\n",posCentered.x,posCentered.y, posToRefine.x,posToRefine.y);

    float msd;
    float maxMSD=-5000000;
    //int xx,yy;
    //long indexY,index;
    //float tmp;
    int p2=patchSize/2;
    float2 center={width/2,height/2};
    float2 size={width,height};
    float lengthPatch=patchSize*patchSize;
    float2 refined;
    refined=posToRefine;

    //precoomputing of avg1
    float avg1=0;
    float sum2_1=0;
    float avg2=0;
    float val0,val1,val2,val3,val4,val5,val6,val7;
    int tot=0;
    float8 valuesRef;
    for(int posy=-p2;posy<=p2;posy++){
        for(int posx=-p2;posx<p2;posx+=8){
            float2 pos={posx,posy};
            int2 coord=convert_int2(posCentered+center+pos);
            //float8 valuesRef=vload8((coord.y*width+coord.x+1)/8,ref);
            int index=coord.y*width+coord.x;
            val0=ref[index];
            val1=ref[index+1];
            val2=ref[index+2];
            val3=ref[index+3];
            val4=ref[index+4];
            val5=ref[index+5];
            val6=ref[index+6];
            val7=ref[index+7];
            avg1+=val0;
            avg1+=val1;
            avg1+=val2;
            avg1+=val3;
            avg1+=val4;
            avg1+=val5;
            avg1+=val6;
            avg1+=val7;
            sum2_1+=val0*val0;
            sum2_1+=val1*val1;
            sum2_1+=val2*val2;
            sum2_1+=val3*val3;
            sum2_1+=val4*val4;
            sum2_1+=val5*val5;
            sum2_1+=val6*val6;
            sum2_1+=val7*val7;  //*/
            tot+=8;
        }
    }
    avg1 /= tot;
    float std1=sqrt(sum2_1/tot-(avg1*avg1));
    //printf("(%f,%f) patchSize=%i p2=%i sum=%f, sum2=%f, tot=%i avg1=%f, std1=%f\n",posCentered.x,posCentered.y, patchSize, p2, avg1*tot,sum2_1,tot,avg1,std1);


    //printf("avg1:%f, avg2:%f\n", avg1, avg2);

    //refine position for shifts in patch
    for(int ty=-p2;ty<=p2;ty++){
        for(int tx=-p2;tx<=p2;tx++){
            float2 transl={tx,ty};
            //compute correlation
            msd=correlation(ref,mov,width, height,posCentered,posToRefine+transl,patchSize,avg1,std1);
            //beter score ?
            //printf("(%i,%i) maxmsd:%f , msd:%f , posToRefine:%v2hlf , refined:%v2hlf\n",tx,ty,maxMSD,msd,(posToRefine+center),(refined+center));
            if((msd)>maxMSD){
                maxMSD=msd;
                refined=posToRefine+transl;
                //printf("changing best score with : %v2hlf\n",transl);
            }


        }

    }

    //write result
    posmov[writingPos]=refined.x;
    posmov[writingPos+1]=refined.y;
    posmov[writingPos+2]=maxMSD;
    //posmov[writingPos+2]=msd;
    //printf("######best posToRefine:%v2hlf , refined : %v2hlf\n",posToRefine+center, refined+center);
}


__kernel void followSeed2(
    __global float* tiltseries,
    __global float* patches,
    __global float* posList,
    __global float* T,
    __global float* Tinv,
    __global float* stats,
     const int width,
     const int height,
     const int nImages,
     const int patchSize,
     const int seqLength
){
//bool msdCompute=true;
    int pos=get_global_id(0);
    long startingPos=get_global_id(0)*(seqLength*3+1)+seqLength/2*3;
    long writingPos=startingPos+3;
    int startImgIndex=(int)posList[startingPos];
    float2 posCentered={(int)posList[startingPos+1],(int)posList[startingPos+2]};
    int imgIndex=startImgIndex;
    int movIndex;
    long Tindex;

    const int p2=(int)patchSize/2;
    //float maxCorr=-20;
    float4 refined={-20,400,-80,0};
    //float corr;
    float msd;
    float maxMSD=50;
    float worseMSD=-50;
    //float worseCorr=50;
    //float2 posCorr;
    //float avg1 = 0;
    //float avg2 = 0;
    //long tot = 0;
    //float sum1 = 0;
    //float sum2 = 0;
    //float sum3 = 0;
    //float val1;
    //float val2;
    //const long length=patchSize*patchSize;
    global float* ref;
    global float* mov;
    //global float* patch;
    float2 posToRefine;
    long lengthImg=width*height;
    long lengthPatch=patchSize*patchSize;
    int x=-1;
    int xx,yy;
    long indexY,index;
    float tmp;

    //forward
    for(int i =0; i<seqLength/2;i++){
        ref=patches+(pos)*patchSize*patchSize;
        readImageBuffer2(tiltseries+imgIndex*width*height,ref, width,height,patchSize,posCentered);
        //float2 posToRefine=posCentered;
        movIndex=imgIndex+1;
        Tindex=imgIndex*6;
        posToRefine.x=T[Tindex]*posCentered.x+T[Tindex+2]*posCentered.y+T[Tindex+4];
        posToRefine.y=T[Tindex+1]*posCentered.x+T[Tindex+3]*posCentered.y+T[Tindex+5];
        if(movIndex<nImages){
            //float4 refined=refineLandmark(tiltseries+movIndex*width*height,patches+(pos*2)*patchSize*patchSize,patches+(pos*2+1)*patchSize*patchSize,posToRefine,width,height,patchSize);

                //maxCorr=-20;
                maxMSD=50;
                        mov=tiltseries+movIndex*width*height;
                for(int ty=-p2;ty<=p2;ty++){
                    for(int tx=-p2;tx<=p2;tx++){
                        //posCorr.x=posToRefine.x+tx;
                       // posCorr.y=posToRefine.y+ty;
                        //patch=patches+(pos*2+1)*patchSize*patchSize;
                        //readImageBuffer2(tiltseries+movIndex*width*height,patch, width,height,patchSize,posCorr);
                            x=-1;
                            xx=posToRefine.x+tx-patchSize/2+width/2;
                            yy=posToRefine.y+ty-(patchSize/2)+(height/2);
                            indexY=yy*width;
                            for(int p=0;p<lengthPatch;p++){
                                x++;
                                if(x==patchSize){
                                    x=0;
                                    yy++;
                                    indexY+=width;
                                }
                                if(yy>=0&&yy<height){
                                    index=indexY+xx+x;
                                    if(index>=0&&index<lengthImg){
                                        tmp=ref[p]-mov[index];
                                        msd+=tmp*tmp;
                                    } //patch[i]=image[index];
                                }
                            }
                            msd/=lengthPatch;
                        //if(msdCompute) msd=computeMSD(ref,patch,patchSize*patchSize);
                        //else corr=computeCorrelation(ref,patch,patchSize*patchSize);

                        //if(msdCompute){
                             if(msd<maxMSD){
                                maxMSD=msd;
                                refined.x=posToRefine.x+tx;
                                refined.y=posToRefine.y+ty;
                            }
                        /*}else{
                            if(corr>maxCorr){
                                maxCorr=corr;
                                refined.x=posCorr.x;
                                refined.y=posCorr.y;
                            }
                        } */
                    }
                }
                //if(msdCompute)
                    refined.z=maxMSD;
                //else refined.z=maxCorr;
            posList[writingPos]=movIndex;
            posList[writingPos+1]=refined.x;
            posList[writingPos+2]=refined.y;
            imgIndex++;
            writingPos+=3;
            posCentered.x=refined.x;
            posCentered.y=refined.y;
            //if(msdCompute) {
                if(refined.z<worseMSD)worseMSD=refined.z;
            //} else {if(refined.z>worseCorr)worseCorr=refined.z;}
        }
    }
    //backward
    /*imgIndex=startImgIndex;
    writingPos=startingPos-3;
    posCentered.x=(int)posList[startingPos+1];
    posCentered.y=(int)posList[startingPos+2];
    for(int i =0; i<seqLength/2;i++){
        ref=patches+(pos*2)*patchSize*patchSize;
        readImageBuffer(tiltseries+imgIndex*width*height,ref, width,height,patchSize,posCentered);
            //readImageBuffer(tiltseries+imgIndex*width*height,patches+(pos*2)*patchSize*patchSize, width,height,patchSize,posCentered);
            movIndex=imgIndex-1;
            //float2 posToRefine=posCentered;
            Tindex=movIndex*6;
            float2 posToRefine={Tinv[Tindex]*posCentered.x+Tinv[Tindex+2]*posCentered.y+Tinv[Tindex+4],Tinv[Tindex+1]*posCentered.x+Tinv[Tindex+3]*posCentered.y+Tinv[Tindex+5]};
            if(movIndex>=0){
                //float4 refined=refineLandmark(tiltseries+movIndex*width*height,patches+(pos*2)*patchSize*patchSize,patches+(pos*2+1)*patchSize*patchSize,posToRefine,width,height,patchSize);
                maxCorr=-20;
                for(int ty=-p2;ty<=p2;ty++){
                    for(int tx=-p2;tx<=p2;tx++){
                        posCorr.x=posToRefine.x+tx;
                        posCorr.y=posToRefine.y+ty;
                        patch=patches+(pos*2+1)*patchSize*patchSize;
                        readImageBuffer(tiltseries+movIndex*width*height,patch, width,height,patchSize,posCorr);
                        //corr=computeCorrelation(ref,patch,patchSize*patchSize);
                        avg1 = 0;
                        avg2 = 0;
                        tot = 0;
                        for (int i = 0; i < length; i++) {
                            avg1 += ref[i];
                            avg2 += patch[i];
                            tot++;
                        }
                        avg1 /= tot;
                        avg2 /= tot;
                        corr = 0;
                        sum2 = 0;
                        sum3 = 0;
                        for (int i = 0; i < length; i++) {
                            val1 = (ref[i] - avg1);
                            val2 = (patch[i] - avg2);
                            corr += val1 * val2;
                            sum2 += val1 * val1;
                            sum3 += val2 * val2;
                            //tot++;
                        }
                        sum2=sqrt(sum2*sum3);
                        if(sum2==0)sum2=0.00001;
                        corr/= sum2;

                        if(corr>maxCorr){
                            maxCorr=corr;
                            refined.x=posCorr.x;
                            refined.y=posCorr.y;
                            //bestPos.z+=1;
                        }
                    }
                }
                refined.z=maxCorr;
                posList[writingPos]=movIndex;
                posList[writingPos+1]=refined.x;
                posList[writingPos+2]=refined.y;
                //posList[writingPos+1]=5423;
                //posList[writingPos+2]=123456;
                imgIndex--;
                writingPos-=3;
                posCentered.x=refined.x;
                posCentered.y=refined.y;
                if(refined.z<worseCorr)worseCorr=refined.z;
            }
        }
     */

    posList[pos*(seqLength*3+1)+seqLength*3]=worseMSD;
}

__kernel void test(
    __global float* points,
    const int seqLength,
    const int pointsLength
) {
    int startingPos=get_global_id(0)*(seqLength*3+1)+seqLength/2*3;
    float x=points[startingPos];
    float y=points[startingPos+1];
    float z=points[startingPos+2];

    for(int i=1;i<=seqLength/2;i++){
        int pos=startingPos-i*3;
        if(pos>=0){
        points[pos]=x-i;
        points[pos+1]=y-i;
        points[pos+2]=z-i;
        }
        pos=startingPos+i*3;
        if(pos<pointsLength-3) {
        points[pos]=x+i;
        points[pos+1]=y+i;
        points[pos+2]=z+i;
        }
    }
    //points[(get_global_id(0)+1)*(seqLength*3+1)-1];
    /*for(int i=get_global_id(0)*(seqLength*3+1);i<(get_global_id(0)+1)*(seqLength*3+1);i++){
        points[i]=i;
    }*/

}



__kernel void testReadingImageBuffer(
    __global float* tiltseries,
    __global float* patch,
    __global int* posList,
    const int width,
    const int height,
    const int nImages,
    const int patchSize
){
    int pos=get_global_id(0);
    int imgIndex=posList[pos*3];
    float2 posCentered={posList[pos*3+1],posList[pos*3+2]};

    readImageBuffer2(tiltseries+imgIndex*width*height,patch+pos*patchSize*patchSize, width,height,patchSize,posCentered);

}

__kernel void testReadingImageBufferApplyT(
    __global float* tiltseries,
    __global float* patch,
    __global int* posList,
    __global float* Tinv,
    __global float* stats,
    const int width,
    const int height,
    const int nImages,
    const int patchSize
){
    int pos=get_global_id(0);
    int imgIndex=posList[pos*3];
    float2 posCentered={posList[pos*3+1],posList[pos*3+2]};

   // float* p=getSubImagePixels(tiltseries+imgIndex*width*height,Tinv+pos*6,stats+pos*2, width,height,patchSize,posCentered);
    //for(int i=0;i<patchSize*patchSize;i++) patch[i]=p[i];
    readImageBufferApplyT(tiltseries+imgIndex*width*height,patch+pos*patchSize*patchSize,Tinv+pos*6,stats+pos*2, width,height,patchSize,posCentered);

}


__kernel void testCorrelation(
    __global float* tiltseries,
    __global float* patchRef,
    __global float* patchMov,
    __global int* posList,
    __global float* Tinv,
    __global float* stats,
    __global float* corr,
    const int width,
    const int height,
    const int nImages,
    const int patchSize
){
    int pos=get_global_id(0);
    int imgIndex1=posList[pos*3];
    float2 posCentered1={posList[pos*3+1],posList[pos*3+2]};
    long patchRefIndex=(pos)*patchSize*patchSize;
    readImageBufferApplyT(tiltseries+imgIndex1*width*height,patchRef+patchRefIndex,Tinv+pos*6,stats+pos*2, width,height,patchSize,posCentered1);
    //readImageBuffer(tiltseries+imgIndex1*width*height,patchRef+patchRefIndex, width,height,patchSize,posCentered1);

    int imgIndex2=imgIndex1+1;
    int pos2=pos+1;
    float2 posCentered2=posCentered1;
    long patchMovIndex=(pos)*patchSize*patchSize;
    readImageBufferApplyT(tiltseries+imgIndex2*width*height,patchMov+patchMovIndex,Tinv+pos2*6,stats+pos2*2, width,height,patchSize,posCentered2);
    //readImageBuffer(tiltseries+imgIndex2*width*height,patchMov+patchMovIndex, width,height,patchSize,posCentered2);

corr[pos]= computeCorrelation(patchRef+patchRefIndex,patchMov+patchMovIndex,patchSize*patchSize);
//corr[pos]=512;
}


