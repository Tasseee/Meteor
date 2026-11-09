package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
/** Displays only slots provided to the client by the currently open handled screen. */
public final class InvSee extends Module {
    private final StringSetting titleFilter=add(new StringSetting("Titel-Filter (leer = alle)",""));
    private final NumberSetting maxEntries=add(new NumberSetting("Max. Eintraege",18,6,54,1));
    public InvSee(){super("InvSee","Zeigt Slots eines geoeffneten, serverseitig bereitgestellten Menues.",Category.VISUALS);}
    @Override public void renderHud(DrawContext ctx){
        if(mc.player==null||!(mc.currentScreen instanceof HandledScreen<?> screen))return;
        String filter=titleFilter.get().trim(),title=screen.getTitle().getString();
        if(!filter.isEmpty()&&!title.toLowerCase(java.util.Locale.ROOT).contains(filter.toLowerCase(java.util.Locale.ROOT)))return;
        var stacks=mc.player.currentScreenHandler.getStacks();int sw=mc.getWindow().getScaledWidth(),sh=mc.getWindow().getScaledHeight();
        int pw=Math.min(220,sw-16),x=sw-pw-8,y=42,rows=Math.min(maxEntries.getInt(),Math.max(1,(sh-y-18)/12));
        ctx.fill(x,y,x+pw,y+28+rows*12,0xD910121B);ctx.fill(x,y,x+pw,y+2,0xFF65E6A5);
        ctx.drawText(mc.textRenderer,"INVSEE · "+title,x+8,y+7,0xFF65E6A5,true);int row=0;
        for(int i=0;i<stacks.size()&&row<rows;i++){
            ItemStack stack=stacks.get(i);if(stack.isEmpty())continue;
            String line=i+": "+stack.getName().getString()+" x"+stack.getCount();
            while(mc.textRenderer.getWidth(line)>pw-16&&line.length()>8)line=line.substring(0,line.length()-2)+"…";
            int ry=y+21+row*12;if((row&1)==0)ctx.fill(x+5,ry-2,x+pw-5,ry+10,0x301A1F2E);
            ctx.drawText(mc.textRenderer,line,x+8,ry,0xFFF0F2F7,false);row++;
        }
        if(row==0)ctx.drawText(mc.textRenderer,"Keine belegten Slots",x+8,y+22,0xFFB0B5C5,false);
    }
}
