package pt.realmeconomy.mixin.colony;
import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import net.neoforged.fml.loading.LoadingModList;
public final class ColonyMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){return LoadingModList.get().getModFileById("minecolonies")!=null;}
 public void acceptTargets(Set<String>a,Set<String>b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode c,String m,IMixinInfo i){} public void postApply(String t,ClassNode c,String m,IMixinInfo i){}
}
