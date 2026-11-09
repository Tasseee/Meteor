package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.*;
import dev.nightfall.scanner.ChunkFavorites;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;
import java.util.*;
public final class ChunkEsp extends Module {
    private final ModeSetting mode=add(new ModeSetting("Modus","Frames","Surface","Frames","Pulse"));
    private final NumberSetting dist=add(new NumberSetting("Max Distanz (Chunks)",12,2,48,1));
    private final NumberSetting height=add(new NumberSetting("Rahmenhoehe",24,4,128,2));
    private final NumberSetting speed=add(new NumberSetting("Pulse-Speed",1.0,0.2,4.0,0.1));
    private final NumberSetting thick=add(new NumberSetting("Linienstaerke",1,1,4,1));
    private final NumberSetting opacity=add(new NumberSetting("Transparenz",0.82,0.2,1.0,0.05));
    private final ColorSetting susColor=add(new ColorSetting("SUS-Farbe",0xFF9B75F5));
    private final ColorSetting goneColor=add(new ColorSetting("SUS-WEG-Farbe",0xFF4386D9));
    private final ColorSetting favoriteColor=add(new ColorSetting("Favoriten-Farbe",0xFFFFC85A));
    private final BoolSetting fadeDistance=add(new BoolSetting("Distanz-Fade",true));
    private final NumberSetting fadeStart=add(new NumberSetting("Fade ab (Anteil)",0.55,0.1,0.9,0.05));
    private final BoolSetting showScore=add(new BoolSetting("Score anzeigen",true));
    private final BoolSetting showGone=add(new BoolSetting("SUS WEG anzeigen",true));
    private final BoolSetting showFavorites=add(new BoolSetting("Favoriten hervorheben",true));
    private final NumberSetting maxObj=add(new NumberSetting("Max Regionen",48,4,256,4));
    private record Region(int cx,int cz,int width,int depth,int count,double score,boolean favorite,ChunkResult.Status status) {}
    public ChunkEsp() { super("ChunkESP","Zeigt zusammengefasste SUS-Regionen und Favoriten.",Category.VISUALS); }
    private static long key(int cx,int cz) { return ((long)cx<<32)^(cz&0xffffffffL); }
    private List<Region> mergeAdjacent(Collection<ChunkResult> source,int pcx,int pcz,int range) {
        Map<Long,ChunkResult> candidates=new HashMap<>();
        for(ChunkResult r:source) {
            if(r.status==ChunkResult.Status.GONE&&!showGone.get()) continue;
            int dx=r.cx-pcx,dz=r.cz-pcz;if(dx*dx+dz*dz<=range*range)candidates.put(key(r.cx,r.cz),r);
        }
        List<ChunkResult> ordered=new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingInt((ChunkResult r)->r.cz).thenComparingInt(r->r.cx));
        Set<Long> visited=new HashSet<>();List<Region> regions=new ArrayList<>();
        for(ChunkResult seed:ordered) {
            if(visited.contains(key(seed.cx,seed.cz))) continue;
            int width=1;
            while(true) {
                ChunkResult n=candidates.get(key(seed.cx+width,seed.cz));
                if(n==null||n.status!=seed.status||visited.contains(key(n.cx,n.cz)))break;width++;
            }
            int depth=1;
            while(true) {
                boolean full=true;
                for(int x=0;x<width;x++) {
                    ChunkResult n=candidates.get(key(seed.cx+x,seed.cz+depth));
                    if(n==null||n.status!=seed.status||visited.contains(key(n.cx,n.cz))){full=false;break;}
                }
                if(!full)break;depth++;
            }
            double score=0;boolean favorite=false;
            for(int z=0;z<depth;z++)for(int x=0;x<width;x++) {
                ChunkResult part=candidates.get(key(seed.cx+x,seed.cz+z));
                visited.add(key(part.cx,part.cz));score=Math.max(score,part.score);
                favorite|=ChunkFavorites.isFavorite(part.cx,part.cz);
            }
            regions.add(new Region(seed.cx,seed.cz,width,depth,width*depth,score,favorite,seed.status));
        }
        return regions;
    }
    @Override public void renderHud(DrawContext ctx) {
        if(mc.player==null||!Projection.valid())return;
        BaseFinder finder=ModuleManager.get(BaseFinder.class);if(!finder.isEnabled())return;
        int pcx=((int)Math.floor(Projection.eyeX()))>>4,pcz=((int)Math.floor(Projection.eyeZ()))>>4;
        List<Region> regions=mergeAdjacent(finder.marks(),pcx,pcz,dist.getInt());
        double phase=(System.currentTimeMillis()%100000L)/1000.0*speed.get(),pulse=0.5+0.5*Math.sin(phase*Math.PI*2.0);
        double py=Projection.eyeY();int drawn=0;
        for(Region r:regions) {
            if(drawn>=maxObj.getInt()||EspBase.drawn>=2048)break;
            float scoreT=(float)Math.max(0.0,Math.min(1.0,r.score/(finder.thresholdValue()*3.0)));
            int baseColor=r.status==ChunkResult.Status.GONE?goneColor.rgb():Theme.lerp(susColor.rgb(),0xFFFF526E,scoreT);
            if(r.favorite&&showFavorites.get())baseColor=favoriteColor.rgb();
            double x0=r.cx*16.0,z0=r.cz*16.0,x1=x0+r.width*16.0,z1=z0+r.depth*16.0;
            double h=height.get(),y0=py-h/2.0;
            double regionDistance=new net.minecraft.util.math.Vec3d(mc.player.getX(),mc.player.getY(),mc.player.getZ()).distanceTo(new net.minecraft.util.math.Vec3d((x0+x1)/2.0,py,(z0+z1)/2.0));
            double fade=1.0;
            if(fadeDistance.get()){
                double normalized=regionDistance/Math.max(16.0,dist.get()*16.0);
                double progress=(normalized-fadeStart.get())/Math.max(0.01,1.0-fadeStart.get());
                fade=Math.max(0.0,Math.min(1.0,1.0-progress));
            }
            if(fade<=0.005)continue;
            int alpha=(int)(opacity.get()*255.0*fade);int color=0xFF000000|(baseColor&0x00FFFFFF);Box box;
            switch(mode.get()) {
                case "Surface" -> box=new Box(x0,py-0.06,z0,x1,py-0.02,z1);
                case "Pulse" -> { double hh=h*(0.65+0.35*pulse);box=new Box(x0,py-hh/2.0,z0,x1,py+hh/2.0,z1);alpha=(int)(alpha*(0.55+0.45*pulse)); }
                default -> box=new Box(x0,y0,z0,x1,y0+h,z1);
            }
            if(!Draw.box(ctx,box,Theme.withAlpha(color,alpha),thick.getInt(),true))continue;
            String label=(r.favorite&&showFavorites.get()?"★ ":"")+(r.status==ChunkResult.Status.GONE?"SUS WEG":"SUS")
                    +(r.count>1?" · "+r.count+" Chunks":"")+(showScore.get()?" · "+String.format(Locale.ROOT,"%.1f",r.score):"");
            int tw=mc.textRenderer.getWidth(label),lx=Math.round(Draw.topX-tw/2.0f),ly=Math.round(Draw.topY-14.0f);
            // No dark backing/fill behind SUS labels; keep the projected chunk outline.
            ctx.drawText(mc.textRenderer,label,lx,ly,0xFFF5F6FA,true);drawn++;EspBase.drawn++;
        }
    }
}
