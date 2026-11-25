package dev.nightfall.scanner;
/** Virtual target planner independent of Minecraft classes. */
public final class ScanPlanner {
   public enum Mode{INTELLIGENT,SPIRAL,RASTER}
   public interface Info{boolean available(int cx,int cz);long age(int cx,int cz);int interest(int cx,int cz);}
   public static final int PICK_LIMIT=384;private static final double AGE_CAP_SECONDS=600.0;
   public int targetCx,targetCz;public boolean hasTarget;
   private int anchorCx,anchorCz,range=8;private int[] disc=new int[0];private int discSize,pickCursor;
   private int sx,sz,sdx,sdz,segLen,segDone,turns,rasterIndex;
   public int anchorCx(){return anchorCx;}public int anchorCz(){return anchorCz;}public int range(){return range;}
   public void reset(int x,int z,int r){anchorCx=x;anchorCz=z;hasTarget=false;setRange(r);resetSpiral();rasterIndex=pickCursor=0;}
   public void setRange(int r){
      r=Math.max(1,r);if(r==range&&disc.length>0)return;range=r;
      int[] tmp=new int[(2*r+1)*(2*r+1)];int n=0;
      for(int dz=-r;dz<=r;dz++)for(int dx=-r;dx<=r;dx++)if(dx*dx+dz*dz<=r*r)tmp[n++]=pack(dx,dz);
      disc=tmp;discSize=n;pickCursor=0;resetSpiral();rasterIndex=0;
   }
   public boolean pick(Mode mode,int pcx,int pcz,int ccx,int ccz,Info info){
      if(mode==Mode.INTELLIGENT){anchorCx=pcx;anchorCz=pcz;}
      else{int dx=pcx-anchorCx,dz=pcz-anchorCz,lim=Math.max(2,range/2);if(dx*dx+dz*dz>lim*lim)reset(pcx,pcz,range);}
      hasTarget=switch(mode){case INTELLIGENT->pickIntelligent(ccx,ccz,info);case SPIRAL->pickSpiral(info);case RASTER->pickRaster(info);};return hasTarget;
   }
   private boolean pickIntelligent(int ccx,int ccz,Info info){
      if(discSize==0)return false;int checks=Math.min(discSize,PICK_LIMIT),idx=pickCursor;double best=Double.NEGATIVE_INFINITY;
      int bx=0,bz=0;boolean found=false;
      for(int i=0;i<checks;i++){
         if(idx>=discSize)idx=0;int p=disc[idx++],cx=anchorCx+unpackX(p),cz=anchorCz+unpackZ(p);
         if(!info.available(cx,cz))continue;long age=info.age(cx,cz);
         double ageSec=age==Long.MAX_VALUE?AGE_CAP_SECONDS:Math.min(age/20.0,AGE_CAP_SECONDS);
         double dx=cx-ccx,dz=cz-ccz,dist=Math.sqrt(dx*dx+dz*dz);int interest=info.interest(cx,cz);
         double score=ageSec-dist*4.0+(interest>0?20.0:interest==0?-200.0:0.0)-(dist<2.0?50.0:0.0);
         if(score>best){best=score;bx=cx;bz=cz;found=true;}
      }
      pickCursor=idx>=discSize?0:idx;if(found){targetCx=bx;targetCz=bz;}return found;
   }
   private void resetSpiral(){sx=sz=0;sdx=1;sdz=0;segLen=1;segDone=turns=0;}
   private boolean stepSpiral(){
      sx+=sdx;sz+=sdz;if(++segDone==segLen){segDone=0;int t=sdx;sdx=-sdz;sdz=t;if(++turns%2==0)segLen++;}
      return Math.abs(sx)<=range&&Math.abs(sz)<=range;
   }
   private boolean pickSpiral(Info info){
      int max=(2*range+1)*(2*range+1);for(int i=0;i<max;i++){
         if(!stepSpiral())resetSpiral();if(sx*sx+sz*sz>range*range)continue;
         int cx=anchorCx+sx,cz=anchorCz+sz;if(info.available(cx,cz)){targetCx=cx;targetCz=cz;return true;}
      }return false;
   }
   private boolean pickRaster(Info info){
      int width=2*range+1,total=width*width;for(int i=0;i<total;i++){
         int idx=rasterIndex;rasterIndex=(idx+1)%total;int row=idx/width,col=idx%width;if((row&1)==1)col=width-1-col;
         int dx=col-range,dz=row-range;if(dx*dx+dz*dz>range*range)continue;
         int cx=anchorCx+dx,cz=anchorCz+dz;if(info.available(cx,cz)){targetCx=cx;targetCz=cz;return true;}
      }return false;
   }
   private static int pack(int x,int z){return(x&65535)|(z<<16);}
   private static int unpackX(int p){return(short)(p&65535);}
   private static int unpackZ(int p){return p>>16;}
}