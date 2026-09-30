package com.starskyxiii.cgbridgeprobe.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.starskyxiii.cgbridgeprobe.Observation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
@Mixin(targets="com.starskyxiii.collapsible_groups.client.editor.EditorLeftPanel",remap=false)
public abstract class SourceCellMixin {
 @Inject(method="renderCell",at=@At("RETURN")) private void cg$cell(GuiGraphicsExtractor g,Object e,int x,int y,CallbackInfo c) { Observation.sourceCell(e,x,y); }
}
