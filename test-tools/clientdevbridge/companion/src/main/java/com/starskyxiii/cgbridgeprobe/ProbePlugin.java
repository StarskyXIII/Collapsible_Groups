package com.starskyxiii.cgbridgeprobe;
import org.spongepowered.asm.mixin.extensibility.*;
import org.objectweb.asm.tree.ClassNode;
import java.util.*;
public final class ProbePlugin implements IMixinConfigPlugin {
 public void onLoad(String p) {}
 public String getRefMapperConfig() { return null; }
 public boolean shouldApplyMixin(String target, String mixin) {
  if (!Boolean.getBoolean("cgbridgeprobe.enabled") || !Boolean.getBoolean("clientdevbridge.enabled")) return false;
  String resource = target.replace('.', '/') + ".class";
  return getClass().getClassLoader().getResource(resource) != null;
 }
 public void acceptTargets(Set<String> a, Set<String> b) {}
 public List<String> getMixins() { return null; }
 public void preApply(String n, ClassNode c, String m, IMixinInfo i) {}
 public void postApply(String n, ClassNode c, String m, IMixinInfo i) {}
}
