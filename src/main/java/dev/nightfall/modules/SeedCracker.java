package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.util.NotificationCenter;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.finder.Finder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.Locale;
/** Shows collected evidence and actual SeedCrackerX search progress. */
public final class SeedCracker extends Module {
    private static long foundSeed,foundAt;private static boolean hasResult;
    private final ModeSetting searchProfile = add(new ModeSetting(
            "Suchintensitaet", "Ausgewogen", "Sparsam", "Ausgewogen", "Intensiv"
    ));
    private String appliedProfile = "";

    public SeedCracker(){super("SeedCracker","Sammelt Seed-Daten und steuert die Zuverlaessigkeit und Intensitaet der Seed-Suche.",Category.WORLD);}
    @Override protected void onEnable(){SeedCrackerProgress.reset();setCrackerActive(true);hasResult=false;applySearchProfile();}
    @Override public void onTick(){
        if (!searchProfile.get().equals(appliedProfile)) applySearchProfile();
    }

    private void applySearchProfile() {
        try {
            Config config = Config.get();
            String profile = searchProfile.get();

            // Core structure finders and End pillars are the stable baseline in every profile.
            config.buriedTreasure.set(true);
            config.desertTemple.set(true);
            config.endCity.set(true);
            config.jungleTemple.set(true);
            config.monument.set(true);
            config.swampHut.set(true);
            config.shipwreck.set(true);
            config.outpost.set(true);
            config.igloo.set(true);
            config.trialChambers.set(true);
            config.endPillars.set(true);

            // Keep experimental/known-problematic finders disabled in every profile.
            config.dungeon.set(false);
            config.emeraldOre.set(false);
            config.warpedFungus.set(false);

            boolean balanced = profile.equals("Ausgewogen") || profile.equals("Intensiv");
            boolean intensive = profile.equals("Intensiv");

            // Additional supported decorators are enabled progressively.
            config.endGateway.set(balanced);
            config.desertWell.set(balanced);
            config.biome.set(intensive);

            Finder.Type.END_PILLARS.enabled.set(true);
            Finder.Type.END_GATEWAY.enabled.set(balanced);
            Finder.Type.DESERT_WELL.enabled.set(balanced);
            Finder.Type.BIOME.enabled.set(intensive);
            Finder.Type.DUNGEON.enabled.set(false);
            Finder.Type.EMERALD_ORE.enabled.set(false);
            Finder.Type.WARPED_FUNGUS.enabled.set(false);

            appliedProfile = profile;
            Config.save();
        } catch (Throwable t) {
            dev.nightfall.Nightfall.LOGGER.error("SeedCracker-Suchprofil konnte nicht angewendet werden", t);
        }
    }
    @Override protected void onDisable(){setCrackerActive(false);SeedCrackerProgress.reset();hasResult=false;}
    public static void syncState(){try{setCrackerActive(dev.nightfall.module.ModuleManager.get(SeedCracker.class).isEnabled());}catch(Throwable ignored){}}
    private static void setCrackerActive(boolean active){
        try{Config.get().active=active;Config.save();}
        catch(Throwable t){dev.nightfall.Nightfall.LOGGER.error("SeedCrackerX konnte nicht umgeschaltet werden",t);}
    }
    public static void reportSeed(long seed){
        SeedCracker module;try{module=dev.nightfall.module.ModuleManager.get(SeedCracker.class);}catch(Throwable ignored){return;}
        if(!module.isEnabled())return;
        foundSeed=seed;foundAt=System.currentTimeMillis();hasResult=true;SeedCrackerProgress.complete();
        NotificationCenter.push("SEED GEFUNDEN",Long.toString(seed),12_000L,0xFF65E6A5);
    }
    @Override public void onWorldLeave(){hasResult=false;SeedCrackerProgress.reset();}
    private record ProgressView(float progress,String label,boolean indeterminate){}
    private ProgressView readProgress(){
        try{
            var instance=kaptainwutax.seedcrackerX.SeedCracker.get();
            if(instance==null)return new ProgressView(0f,"Warte auf SeedCrackerX ...",false);
            var data=instance.getDataStorage();double wanted=Math.max(1.0,data.getWantedBits());
            double bits=Math.max(0.0,Math.min(wanted,data.getBaseBits()));
            boolean running=data.getTimeMachine().isRunning;float search=SeedCrackerProgress.getSearchProgress();
            if(running&&search>0f)return new ProgressView(Math.min(1f,search/100f),"Seed-Suche · "+Math.round(search)+"%",false);
            if(running)return new ProgressView(0f,"Seed-Suche laeuft ...",true);
            return new ProgressView((float)(bits/wanted),"Daten gesammelt · "+String.format(Locale.ROOT,"%.1f / %.0f Bits",bits,wanted),false);
        }catch(Throwable ignored){return new ProgressView(0f,"Seed-Daten werden gelesen ...",true);}
    }
    @Override public void renderHud(DrawContext ctx){
        int sw=mc.getWindow().getScaledWidth();ProgressView view=readProgress();
        int x=8,y=8,w=Math.min(250,sw-16);
        ctx.fill(x,y,x+w,y+39,0xC910121B);ctx.fill(x,y,x+3,y+39,0xFF65E6A5);
        ctx.drawText(mc.textRenderer,"SEEDCRACKER",x+10,y+6,0xFF65E6A5,true);
        String label=view.label();while(mc.textRenderer.getWidth(label)>w-20&&label.length()>8)label=label.substring(0,label.length()-2)+"…";
        ctx.drawText(mc.textRenderer,label,x+10,y+18,0xFFF0F2F7,false);
        int bx=x+10,by=y+31,bw=w-20;ctx.fill(bx,by,bx+bw,by+4,0xFF323541);
        if(view.indeterminate()){
            int segment=Math.max(18,bw/5),offset=(int)((System.currentTimeMillis()/12L)%(bw+segment))-segment;
            int left=Math.max(bx,bx+offset),right=Math.min(bx+bw,bx+offset+segment);
            if(right>left)ctx.fill(left,by,right,by+4,0xFF65E6A5);
        }else{int fill=Math.round(bw*view.progress());if(fill>0)ctx.fill(bx,by,bx+fill,by+4,0xFF65E6A5);}
        if(!hasResult)return;
        long elapsed=System.currentTimeMillis()-foundAt;if(elapsed>=12_000L){hasResult=false;return;}
        int pw=Math.min(420,sw-24),ph=72,px=(sw-pw)/2,py=Math.max(54,mc.getWindow().getScaledHeight()/2-ph/2);
        float fade=elapsed>10_500L?(12_000L-elapsed)/1_500f:1f;int alpha=Math.max(0,Math.min(255,Math.round(235*fade)));
        int panel=(alpha<<24)|0x10131D,accent=(alpha<<24)|0x65E6A5,white=(alpha<<24)|0xFFFFFF;
        ctx.fill(px,py,px+pw,py+ph,panel);ctx.fill(px,py,px+pw,py+3,accent);
        ctx.drawCenteredTextWithShadow(mc.textRenderer,Text.literal("SEED GEFUNDEN"),sw/2,py+13,accent);
        ctx.drawCenteredTextWithShadow(mc.textRenderer,Text.literal(Long.toString(foundSeed)),sw/2,py+34,white);
    }
}
