package dev.nightfall.scanner;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Iterator;

/** Client-thread-only store for tracked live chunks and a bounded LRU of unloaded chunks. */
public final class ChunkStore<C> {
   public static final class Entry<C> {
      public final long key; public final int cx, cz;
      public C chunk; public long mask; public int version=1;
      public long surveyTick=-1L, unloadTick; public int surveys;
      public boolean loaded; int dense=-1;
      Entry(long key,int cx,int cz){this.key=key;this.cx=cx;this.cz=cz;}
   }
   public interface Live<C>{C get(int cx,int cz);}
   public interface Visitor<C>{void visit(Entry<C> entry);}
   private final Long2ObjectOpenHashMap<Entry<C>> loaded=new Long2ObjectOpenHashMap<>();
   private final Long2ObjectLinkedOpenHashMap<Entry<C>> remembered=new Long2ObjectLinkedOpenHashMap<>();
   @SuppressWarnings("unchecked") private Entry<C>[] dense=(Entry<C>[])new Entry[128];
   private int denseCount,cursor,limit=256;
   public long evicted,invalidated,expired,unloaded;
   public static long key(int cx,int cz){return(cx&0xFFFFFFFFL)|((long)cz<<32);}
   public int loadedCount(){return denseCount;} public int rememberedCount(){return remembered.size();}
   public int limit(){return limit;} public void setLimit(int v){limit=Math.max(0,v);trim();}
   public Entry<C> loadedEntry(long k){return loaded.get(k);}
   public Entry<C> rememberedEntry(long k,boolean touch){return touch?remembered.getAndMoveToLast(k):remembered.get(k);}
   public Entry<C> adopt(int cx,int cz,C chunk){
      long k=key(cx,cz);Entry<C> e=loaded.get(k);
      if(e!=null){if(e.chunk!=chunk){e.chunk=chunk;e.version++;e.surveyTick=-1L;invalidated++;}return e;}
      e=remembered.remove(k);if(e==null)e=new Entry<>(k,cx,cz);else{e.version++;e.surveyTick=-1L;}
      e.chunk=chunk;e.loaded=true;loaded.put(k,e);
      if(denseCount==dense.length)dense=Arrays.copyOf(dense,denseCount*2);
      e.dense=denseCount;dense[denseCount++]=e;return e;
   }
   public boolean unload(Entry<C> e,long now){
      if(!e.loaded)return false;loaded.remove(e.key);int i=e.dense;Entry<C> last=dense[--denseCount];dense[denseCount]=null;
      if(last!=e){dense[i]=last;last.dense=i;}e.dense=-1;e.loaded=false;e.unloadTick=now;unloaded++;
      if(limit<=0){e.chunk=null;return false;}remembered.putAndMoveToLast(e.key,e);trim();return true;
   }
   public int validate(Live<C> live,int count,long now){
      int changes=0;for(int n=0;n<count&&denseCount>0;n++){
         if(cursor>=denseCount)cursor=0;Entry<C> e=dense[cursor];C c=live.get(e.cx,e.cz);
         if(c==null){unload(e,now);changes++;}
         else{if(c!=e.chunk){e.chunk=c;e.version++;e.surveyTick=-1L;invalidated++;changes++;}cursor++;}
      }return changes;
   }
   public int expire(long now,long age){
      int removed=0;Iterator<Entry<C>> it=remembered.values().iterator();
      while(it.hasNext()){Entry<C> e=it.next();if(now-e.unloadTick>age){it.remove();e.chunk=null;removed++;}}
      expired+=removed;return removed;
   }
   public void forEachRemembered(Visitor<C> v){for(Entry<C> e:remembered.values())v.visit(e);}
   public void clear(){loaded.clear();remembered.clear();Arrays.fill(dense,0,denseCount,null);denseCount=0;cursor=0;}
   private void trim(){while(remembered.size()>limit){Entry<C> e=remembered.removeFirst();e.chunk=null;evicted++;}}
}