package pt.lawsjustice.test;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.domain.WorkLedger;
import pt.lawsjustice.service.WorkService;

/** Physical work stations and a durable, isolated native prison; no client progress is injected. */
final class WorkFixture implements AutoCloseable {
    final JusticeFixture base;
    final GameTestHelper helper;
    final UUID zone=UUID.randomUUID();
    final BlockPos board,box,post,first,second;
    final String kind;
    WorkFixture(GameTestHelper helper,String kind){
        this.helper=helper;this.kind=kind;base=new JusticeFixture(helper);base.convict(12000);
        board=base.pos.offset(-1,0,0);box=base.pos.offset(4,0,4);post=base.pos.offset(4,0,1);first=base.pos.offset(3,0,0);second=base.pos.offset(4,0,0);
        helper.getLevel().setBlockAndUpdate(board,BlockRegistry.WORK_BOARD.get().defaultBlockState());helper.getLevel().setBlockAndUpdate(box,BlockRegistry.DELIVERY_BOX.get().defaultBlockState());helper.getLevel().setBlockAndUpdate(post,BlockRegistry.WORKSHOP_TABLE.get().defaultBlockState());
        var work=WorkLedger.empty().putZone(new WorkLedger.Zone(zone,base.prisonId,"Native work zone",WorkLedger.zoneType(kind),base.prison().area(),0));var tasks=new ArrayList<>(work.rules().tasks());tasks.replaceAll(r->r.kind().equals(kind)?new WorkLedger.Rule(kind,1,3600):r);base.data.replaceWork(work.configure(new WorkLedger.Rules(50,tasks)));
    }
    WorkLedger work(){return base.data.work();}
    void open(JusticeActors.Actor actor,BlockPos at){actor.setPos(at.getX()+.5,at.getY(),at.getZ()-1.5);helper.getLevel().getBlockState(at).useWithoutItem(helper.getLevel(),actor,new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false));helper.assertTrue(view(actor).get("open").getAsBoolean(),"The actual work furniture did not open a physical session");}
    JsonObject view(JusticeActors.Actor actor){return WorkService.snapshot(actor);}
    JsonObject mutation(JusticeActors.Actor actor){var state=view(actor);var p=new JsonObject();p.addProperty("nonce",state.get("nonce").getAsString());p.addProperty("version",state.get("version").getAsLong());return p;}
    void act(JusticeActors.Actor actor,String operation,JsonObject payload){WorkService.handle(actor,operation,payload.toString());}
    void accept(){open(base.prisoner,board);var p=mutation(base.prisoner);p.addProperty("zone",zone.toString());p.addProperty("kind",kind);act(base.prisoner,"work_accept",p);helper.assertTrue(work().active(base.prisoner.getUUID())!=null,"A genuine serving prisoner could not accept the task");}
    WorkLedger.Node resource(BlockPos at,String resource,net.minecraft.world.level.block.state.BlockState state){if(resource.equals("WHEAT"))helper.getLevel().setBlockAndUpdate(at.below(),Blocks.FARMLAND.defaultBlockState());helper.getLevel().setBlockAndUpdate(at,state);var node=new WorkLedger.Node(UUID.randomUUID(),zone,JusticeFixture.point(at),resource,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),"READY");base.data.replaceWork(work().commission(zone,List.of(node)));return node;}
    BlockEvent.BreakEvent breakResource(BlockPos at,net.minecraft.world.item.Item tool){base.prisoner.setPos(at.getX()+.5,at.getY(),at.getZ()+1.5);base.prisoner.getInventory().selected=8;base.prisoner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));var event=new BlockEvent.BreakEvent(helper.getLevel(),at,helper.getLevel().getBlockState(at),base.prisoner);NeoForge.EVENT_BUS.post(event);return event;}
    void deliver(){open(base.prisoner,box);act(base.prisoner,"work_deliver",mutation(base.prisoner));}
    boolean pouch(){return base.prisoner.getInventory().items.stream().anyMatch(s->s.is(BlockRegistry.WORK_BUNDLE.get()));}
    @Override public void close(){for(var actor:base.actors.actors)WorkService.logout(actor);base.close();}
}
