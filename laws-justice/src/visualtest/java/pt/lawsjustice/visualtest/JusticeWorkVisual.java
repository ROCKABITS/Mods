package pt.lawsjustice.visualtest;

import static pt.lawsjustice.visualtest.JusticeVisualQA.*;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.WorkLedger;
import pt.lawsjustice.service.JusticeService;
import pt.lawsjustice.service.WorkService;

/** Actual C2S work actions and physical menus. Scene/resources are explicitly authored QA fixtures. */
final class JusticeWorkVisual {
    private static final String[] FURNITURE={"work_board","delivery_box","mining_post","agricultural_post","workshop_table","maintenance_post","warehouse_post"};
    private static final String[] KINDS={"MINING","FARMING","CARPENTRY","SMITHING","CLEANING","TRANSPORT"};
    private static final Map<String,UUID> ZONES=new ConcurrentHashMap<>();
    private static final List<String> FINISHED=new ArrayList<>();
    private static final List<Map<String,Object>> ACTIONS=new ArrayList<>();
    private static final BlockPos OUTSIDE=new BlockPos(3,161,-4),BOARD=new BlockPos(-4,161,4),TABLE=new BlockPos(-4,161,8),SOURCE=new BlockPos(-5,161,10),DESTINATION=new BlockPos(4,161,10);
    private static final BlockPos STONE=new BlockPos(-4,161,2),WHEAT=new BlockPos(-1,161,2),LOG=new BlockPos(-4,161,6),ORE=new BlockPos(-3,161,6),COAL=new BlockPos(-2,161,6),CLEAN=new BlockPos(-1,160,6);
    private static volatile boolean prepared,configured,positioned,cleanDone,deliveryVerified,persistence;
    private static boolean models,zoneUi,policyUi,survival,started;
    private static double standX,standY,standZ;
    private static int nodeIndex;
    private static long policyVersion;
    private static UUID task,sentence;
    private JusticeWorkVisual(){}
    static void begin(){
        if(Minecraft.getInstance().screen!=null)Minecraft.getInstance().screen.onClose();Minecraft.getInstance().setScreen(null);Minecraft.getInstance().options.hideGui=true;
        server(()->{
            var p=player();op(p,true);var level=p.serverLevel();
            for(int i=0;i<FURNITURE.length;i++)place(new BlockPos(-9+i*3,161,-9),FURNITURE[i]);
            place(OUTSIDE,"work_board");place(BOARD,"work_board");place(TABLE,"workshop_table");place(SOURCE,"warehouse_post");place(DESTINATION,"warehouse_post");
            for(BlockPos box:List.of(new BlockPos(-3,161,3),new BlockPos(0,161,3),new BlockPos(-2,161,8),new BlockPos(0,161,8),new BlockPos(0,161,9)))place(box,"delivery_box");
            place(new BlockPos(-3,161,1),"mining_post");place(new BlockPos(0,161,1),"agricultural_post");place(new BlockPos(0,161,5),"maintenance_post");
            level.setBlockAndUpdate(STONE,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(WHEAT.below(),Blocks.FARMLAND.defaultBlockState());level.setBlockAndUpdate(WHEAT,((CropBlock)Blocks.WHEAT).getStateForAge(7));
            level.setBlockAndUpdate(LOG,Blocks.OAK_LOG.defaultBlockState());level.setBlockAndUpdate(ORE,Blocks.IRON_ORE.defaultBlockState());level.setBlockAndUpdate(COAL,Blocks.COAL_ORE.defaultBlockState());level.setBlockAndUpdate(CLEAN,Blocks.STONE_BRICKS.defaultBlockState());
            var tasks=WorkLedger.Rules.defaults().tasks().stream().map(r->new WorkLedger.Rule(r.kind(),1,1200)).toList();JusticeData data=JusticeData.get(p.getServer());check(data.work().jobs().isEmpty(),"The authored work scene unexpectedly contains work progress");data.replaceWork(data.work().configure(new WorkLedger.Rules(50,tasks)));prepared=true;
        });
    }
    private static void closeMenu(){var mc=Minecraft.getInstance();if(mc.screen!=null)mc.screen.onClose();mc.setScreen(null);}
    private static boolean workReady(){return ready()&&string(state(),"kind").equals("work");}
    private static JsonObject job(){return state()!=null&&state().has("job")&&state().get("job").isJsonObject()?state().getAsJsonObject("job"):null;}
    private static boolean atWorkScreen(){return workReady()&&Minecraft.getInstance().screen.getClass().getSimpleName().equals("WorkScreen");}
    private static WorkLedger.Zone currentZone(){return JusticeData.get(player().getServer()).work().zones().get(ZONES.get(WorkLedger.zoneType(currentKind())));}
    private static int index(){return (step-130)/20;}
    private static int phase(){return (step-130)%20;}
    private static String currentKind(){return KINDS[index()];}
    private static void phase(int value){step=130+index()*20+value;}
    private static void openWork(BlockPos anchor){
        opened=false;server(()->{var p=player();JusticeData data=JusticeData.get(p.getServer());var j=data.work().active(p.getUUID());var zone=j==null?null:data.work().zones().get(j.zone());var prison=data.ledger().prisons().get(prisonId);
            BlockPos stand=null;for(BlockPos q:List.of(anchor.offset(1,0,0),anchor.offset(-1,0,0),anchor.offset(0,0,1),anchor.offset(0,0,-1),anchor.offset(2,0,0),anchor.offset(-2,0,0),anchor.offset(0,0,2),anchor.offset(0,0,-2))){
                if(!p.level().isEmptyBlock(q)||!p.level().isEmptyBlock(q.above()))continue;
                boolean board=p.level().getBlockState(anchor).is(BuiltInRegistries.BLOCK.get(key("work_board")));
                if(data.ledger().active().containsKey(p.getUUID())&&!prison.area().contains(point(q)))continue;
                if(zone!=null&&!board&&!zone.area().contains(point(q)))continue;stand=q;break;
            }
            check(stand!=null,"No safe authored approach to actual work station "+anchor);p.connection.teleport(stand.getX()+.5,stand.getY(),stand.getZ()+.5,0,14);WorkService.open(p,anchor);opened=true;
        });
    }
    private static JusticeLedger.Point point(BlockPos p){return new JusticeLedger.Point(p.getX(),p.getY(),p.getZ());}
    private static void selectMine(){opened=false;server(()->{var p=player();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(key("area_selector"))));
        for(BlockPos corner:List.of(new BlockPos(-5,160,1),new BlockPos(-3,164,3))){p.setPos(corner.getX()+.5,corner.getY()+1,corner.getZ()+.5);JusticeService.select(p,corner,false);}p.connection.teleport(OUTSIDE.getX()+2.5,161,OUTSIDE.getZ()+.5,0,12);WorkService.open(p,OUTSIDE);opened=true;});}
    private static void prepareRemaining(){configured=false;server(()->{
        var p=player();JusticeData data=JusticeData.get(p.getServer());WorkLedger work=data.work();
        for(String type:List.of("FARM","WORKSHOP","YARD","WAREHOUSE")){
            BlockPos a=switch(type){case "FARM"->new BlockPos(-2,160,1);case "WORKSHOP"->new BlockPos(-5,160,5);case "YARD"->new BlockPos(-1,160,5);default->new BlockPos(-5,160,9);};
            BlockPos b=switch(type){case "FARM"->new BlockPos(0,164,3);case "WORKSHOP"->new BlockPos(-2,164,8);case "YARD"->new BlockPos(0,164,8);default->new BlockPos(5,164,10);};
            UUID id=UUID.randomUUID();ZONES.put(type,id);work=work.putZone(new WorkLedger.Zone(id,prisonId,"Demonstration "+type.toLowerCase(),type,JusticeLedger.Area.between(p.level().dimension().location().toString(),point(a),point(b)),0));List<WorkLedger.Node> nodes=new ArrayList<>();
            List<BlockPos> resources=switch(type){case "FARM"->List.of(WHEAT);case "WORKSHOP"->List.of(LOG,ORE,COAL);case "YARD"->List.of(CLEAN);default->List.of();};
            for(BlockPos at:resources){String resource=at.equals(WHEAT)?"WHEAT":at.equals(LOG)?"LOG":at.equals(ORE)?"IRON":at.equals(COAL)?"COAL":"CLEAN";nodes.add(new WorkLedger.Node(UUID.randomUUID(),id,point(at),resource,BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(at).getBlock()).toString(),"READY"));}
            work=work.commission(id,nodes);
        }
        data.replaceWork(work);p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.getAbilities().mayfly=false;p.onUpdateAbilities();configured=true;
    });}
    private static void compactWork(boolean administrator){var mc=Minecraft.getInstance();var screen=mc.screen;for(String name:administrator?List.of("TASKS","PROGRESS","ZONES","RULES","HISTORY"):List.of("TASKS","PROGRESS","HISTORY")){tab(name);screen.resize(mc,320,240);bounds(screen,320,240);for(int n=0;n<20;n++){int x=(int)field(screen,"px"),top=(int)field(screen,"bodyTop"),bottom=(int)field(screen,"bodyBottom");screen.mouseScrolled(x+20,(top+bottom)/2.0,0,-1);bounds(screen,320,240);}scroll(1);}screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());CHECKS.add("Actual "+(administrator?"administrator":"prisoner")+" work tabs remain within 320x240 bounds while scrolling.");}
    private static void validateModels(){var mc=Minecraft.getInstance();for(String name:FURNITURE){var block=BuiltInRegistries.BLOCK.get(key(name));check(block!=Blocks.AIR,"Missing original work furniture "+name);for(var state:block.getStateDefinition().getPossibleStates()){var model=mc.getBlockRenderer().getBlockModel(state);var quads=new ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();quads.addAll(model.getQuads(state,null,RandomSource.create(0)));for(Direction direction:Direction.values())quads.addAll(model.getQuads(state,direction,RandomSource.create(0)));check(!quads.isEmpty(),"Missing baked work mesh "+name);for(var quad:quads)check(!quad.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing work texture "+name);MODEL_QUADS.put(state.toString(),quads.size());}}
        for(String name:List.of("work_board","delivery_box","mining_post","agricultural_post","workshop_table","maintenance_post","warehouse_post","work_bundle")){var model=mc.getItemRenderer().getModel(new ItemStack(BuiltInRegistries.ITEM.get(key(name))),null,null,0);check(!model.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"Missing work item texture "+name);}models=true;
    }
    private static boolean clickNth(String key,int ordinal){var screen=Minecraft.getInstance().screen;String label=Component.translatable(key).getString();for(var child:List.copyOf(screen.children()))if(child instanceof AbstractButton button&&button.active&&button.getMessage().getString().equals(label)){if(ordinal-->0)continue;for(int n=0;n<50&&!button.visible;n++){int top=(int)field(screen,"bodyTop"),bottom=(int)field(screen,"bodyBottom"),x=(int)field(screen,"px");screen.mouseScrolled(x+30,(top+bottom)/2.0,0,button.getY()<top?1:-1);}if(button.visible){button.onPress();return true;}}return false;}
    private static void acceptTask(){check(clickNth("gui.lawsjustice.work.accept",currentKind().equals("SMITHING")?1:0),"The actual task card is unavailable: "+currentKind());request("work_accept");phase(2);}
    private static BlockPos[] nodes(){return switch(currentKind()){case "MINING"->new BlockPos[]{STONE};case "FARMING"->new BlockPos[]{WHEAT};case "CARPENTRY"->new BlockPos[]{LOG};case "SMITHING"->new BlockPos[]{ORE,COAL};case "CLEANING"->new BlockPos[]{CLEAN};default->new BlockPos[0];};}
    private static Item tool(){return switch(currentKind()){case "MINING","SMITHING"->Items.IRON_PICKAXE;case "CARPENTRY"->Items.IRON_AXE;case "CLEANING"->Items.BRUSH;default->Items.AIR;};}
    private static void approachNode(){Minecraft.getInstance().player.getInventory().selected=8;positioned=false;started=false;BlockPos at=nodes()[nodeIndex];Item tool=tool();server(()->{var p=player();var z=currentZone();BlockPos stand=null;
        for(BlockPos q:List.of(at.offset(0,0,1),at.offset(0,0,-1),at.offset(1,0,0),at.offset(-1,0,0),at.above())){if(p.level().isEmptyBlock(q)&&p.level().isEmptyBlock(q.above())&&z.area().contains(point(q))){stand=q;break;}}
        check(stand!=null,"No actual survival approach to work node "+at);p.getInventory().selected=8;p.setItemInHand(InteractionHand.MAIN_HAND,tool==Items.AIR?ItemStack.EMPTY:new ItemStack(tool));p.containerMenu.broadcastChanges();standX=stand.getX()+.5;standY=stand.getY();standZ=stand.getZ()+.5;p.connection.teleport(standX,standY,standZ,0,20);positioned=true;
    });}
    private static boolean nodeReady(){var mc=Minecraft.getInstance();return positioned&&mc.player!=null&&mc.player.distanceToSqr(standX,standY,standZ)<.04&&(tool()==Items.AIR?mc.player.getMainHandItem().isEmpty():mc.player.getMainHandItem().is(tool()));}
    private static void pollClean(){server(()->{var j=JusticeData.get(player().getServer()).work().active(owner);if(j!=null&&j.output()==j.quantity())cleanDone=true;});}
    private static BlockPos delivery(){return switch(currentKind()){case "MINING"->new BlockPos(-3,161,3);case "FARMING"->new BlockPos(0,161,3);case "CARPENTRY","SMITHING"->new BlockPos(-2,161,8);case "CLEANING"->new BlockPos(0,161,8);default->new BlockPos(0,161,9);};}
    static void tick(long now)throws Exception {
        var mc=Minecraft.getInstance();
        if(step>=130&&step<250){workTick(now);return;}
        switch(step){
            case 100->{if(!prepared)return;validateModels();mc.options.fov().set(64);move(12,165,-22,28,12);step++;}
            case 101->{if(!cameraReady())return;photo(now);step++;}
            case 102->{capture("laws-justice-work-furniture.png");mc.options.fov().set(49);move(-5.5,162.2,-13.4,36,15);step++;}
            case 103->{if(!cameraReady())return;photo(now);step++;}
            case 104->{capture("laws-justice-work-board-model.png");move(5,162.3,-14.5,25,15);step=115;}
            case 115->{if(!cameraReady())return;photo(now);step++;}
            case 116->{capture("laws-justice-workshop-model.png");move(-1.2,162.3,-14.5,25,15);step++;}
            case 117->{if(!cameraReady())return;photo(now);step++;}
            case 118->{capture("laws-justice-work-posts-models.png");mc.options.hideGui=false;selectMine();step=105;}
            case 105->{if(!atWorkScreen())return;check(state().get("admin").getAsBoolean(),"Actual work setup lost administrator authority");compactWork(true);tab("ZONES");selected("selectedPrison",prisonId);input("zone_name","Demonstration Mine");check(click("gui.lawsjustice.work.create_zone"),"Native work-zone creation control unavailable");request("work_zone");step++;}
            case 106->{if(!atWorkScreen()||state().getAsJsonArray("zones").isEmpty())return;var z=state().getAsJsonArray("zones").get(0).getAsJsonObject();UUID id=UUID.fromString(string(z,"id"));ZONES.put("MINE",id);zoneUi=true;tab("ZONES");selected("selectedZone",id);check(click("gui.lawsjustice.work.commission"),"Native resource commission control unavailable");request("work_commission");step++;}
            case 107->{if(!atWorkScreen()||state().getAsJsonArray("zones").get(0).getAsJsonObject().get("readyNodes").getAsInt()<1)return;tab("ZONES");scroll(1);photo(now);step++;}
            case 108->{capture("laws-justice-work-zones.png");tab("RULES");input("quantity","1");input("minutes","1");input("percent","50");policyVersion=state().get("version").getAsLong();check(click("gui.lawsjustice.work.save_policy"),"Native work policy control unavailable");request("work_policy");step++;}
            case 109->{if(!atWorkScreen()||state().get("version").getAsLong()<=policyVersion)return;policyUi=true;tab("RULES");scroll(1);photo(now);step++;}
            case 110->{capture("laws-justice-work-rules.png");prepareRemaining();closeMenu();step++;}
            case 111->{if(!configured)return;open(CONTROL);step++;}
            case 112->{if(!ready()||!state().get("admin").getAsBoolean())return;tab("SENTENCES");selected("selectedPlayer",owner);selected("selectedPrison",prisonId);selected("selectedCell",cellId);input("minutes","20");input("reason","Authored community-service demonstration: six configured one-unit tasks, with a one-minute reward each.");check(click("gui.lawsjustice.issue_sentence"),"Actual work demonstration sentence could not be applied");request("sentence");step++;}
            case 113->{server(()->{var p=player();var s=JusticeData.get(p.getServer()).ledger().active().get(owner);if(s==null)return;check(s.originalTicks()==24000,"The actual work demonstration sentence does not last twenty minutes");sentence=s.id();op(p,false);p.setGameMode(GameType.SURVIVAL);check(!p.hasPermissions(2)&&!p.isCreative()&&!p.getAbilities().mayfly,"The work prisoner retained operator, creative or flight powers");survival=true;});if(!survival)return;openWork(BOARD);step=130;}
            case 250->{openWork(BOARD);step=251;}
            case 251->{if(!atWorkScreen())return;tab("PROGRESS");check(state().getAsJsonObject("self").get("reductionTicks").getAsLong()==7200,"The real six-task sentence reduction differs from the awarded sum");scroll(1);photo(now);step++;}
            case 252->{capture("laws-justice-work-sentence-reductions.png");tab("HISTORY");scroll(1);photo(now);step++;}
            case 253->{capture("laws-justice-work-history.png");server(()->{JusticeData data=JusticeData.get(player().getServer());data.flush();JusticeData reopened=JusticeData.readState(player().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/laws_justice.dat"));check(reopened.work().state().equals(data.work().state()),"Actual sealed work records changed after reopening their compressed world file");check(reopened.ledger().active().get(owner).reductionTicks()==7200&&reopened.work().credits().get(sentence).ticks()==7200,"Durable work credits and actual sentence reduction diverged");check(reopened.work().jobs().values().stream().filter(j->j.player().equals(owner)&&j.status().equals("DONE")).count()==6,"Not all six actual task deliveries are durable");persistence=true;});step++;}
            case 254->{if(!persistence)return;check(models&&zoneUi&&policyUi&&survival&&FINISHED.size()==6&&CAPTURES.size()>=35,"Actual work client coverage is incomplete");CHECKS.add("The real Work Board C2S registered and commissioned a surveyed mine and saved work policy. All five administrator and three non-operator prisoner tabs passed 320x240 bounds and scrolling.");CHECKS.add("The actual Survival host, without operator or flight privileges, used genuine client mining, wheat harvest, axe, ore/coal and brush packets, timed table buttons, two physical warehouse posts and sealed delivery buttons to complete all six configured demonstration tasks. Each one-unit task awarded one minute; the real twenty-minute sentence retained exactly six minutes of work reduction and the same amount of durable work credit. No client progress or S2C snapshots were injected.");JusticeFinanceVisual.begin();step=300;}
            default->throw new IllegalStateException("Unexpected work client stage "+step);
        }
    }
    private static void workTick(long now)throws Exception {
        var mc=Minecraft.getInstance();String kind=currentKind();
        switch(phase()){
            case 0->{openWork(BOARD);phase(1);}
            case 1->{if(!atWorkScreen())return;check(!state().get("admin").getAsBoolean()&&state().get("canWork").getAsBoolean(),"The real prisoner has invalid work permissions");if(index()==0)compactWork(false);tab("TASKS");selected("selectedZone",ZONES.get(WorkLedger.zoneType(kind)));if(index()==0){scroll(1);photo(now);phase(17);}else acceptTask();}
            case 17->{capture("laws-justice-work-board-menu.png");acceptTask();}
            case 2->{if(!atWorkScreen()||job()==null)return;check(string(job(),"kind").equals(kind),"The actual task card accepted the wrong work: "+string(job(),"kind"));task=UUID.fromString(string(job(),"id"));nodeIndex=0;if(kind.equals("TRANSPORT")){openWork(SOURCE);phase(10);}else{closeMenu();approachNode();phase(3);}}
            case 3->{if(!nodeReady())return;BlockPos at=nodes()[nodeIndex];if(kind.equals("CLEANING")){cleanDone=false;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(at.getX()+.5,at.getY()+1,at.getZ()+.5),Direction.UP,at,false));ACTIONS.add(Map.of("task",kind,"action","vanilla_brush_use","position",at.toShortString()));phase(4);return;}
                boolean consumed=kind.equals("FARMING")?mc.level.getBlockState(at).is(Blocks.WHEAT)&&!((CropBlock)Blocks.WHEAT).isMaxAge(mc.level.getBlockState(at)):mc.level.isEmptyBlock(at);
                if(consumed){mc.gameMode.stopDestroyBlock();nodeIndex++;if(nodeIndex<nodes().length){approachNode();return;}openWork(kind.equals("CARPENTRY")||kind.equals("SMITHING")?TABLE:BOARD);phase(5);return;}
                if(!started){check(mc.gameMode.startDestroyBlock(at,Direction.UP),"The actual client could not start the configured work resource");started=true;ACTIONS.add(Map.of("task",kind,"action","vanilla_block_break","position",at.toShortString()));}else mc.gameMode.continueDestroyBlock(at,Direction.UP);mc.player.swing(InteractionHand.MAIN_HAND);
            }
            case 4->{pollClean();if(!cleanDone)return;openWork(BOARD);phase(5);}
            case 5->{if(!atWorkScreen()||job()==null)return;tab("PROGRESS");if(kind.equals("CARPENTRY")||kind.equals("SMITHING")){check(job().get("raw").getAsInt()>0,"The genuine world action did not seal raw workshop supplies");check(click("gui.lawsjustice.work.produce"),"Native timed production button unavailable");request("work_produce");phase(6);}else{if(job().get("output").getAsInt()!=1)return;scroll(1);photo(now);phase(9);}}
            case 6->{if(!atWorkScreen()||state().get("processingRemaining").getAsInt()<=0)return;tab("PROGRESS");scroll(1);photo(now);phase(7);}
            case 7->{capture(kind.equals("CARPENTRY")?"laws-justice-workshop-production.png":"laws-justice-forge-production.png");phase(8);}
            case 8->{if(!atWorkScreen()||job()==null||job().get("output").getAsInt()!=1)return;tab("PROGRESS");scroll(1);photo(now);phase(9);}
            case 9->{capture("laws-justice-"+kind.toLowerCase()+"-work.png");openWork(delivery());phase(14);}
            case 10->{if(!atWorkScreen())return;tab("PROGRESS");check(click("gui.lawsjustice.work.pickup"),"Native source cargo action unavailable");request("work_cargo");phase(11);}
            case 11->{if(!atWorkScreen()||job()==null||job().getAsJsonArray("route").size()!=1)return;openWork(DESTINATION);phase(12);}
            case 12->{if(!atWorkScreen())return;tab("PROGRESS");check(click("gui.lawsjustice.work.unload"),"Native destination cargo action unavailable");request("work_cargo");phase(13);}
            case 13->{if(!atWorkScreen()||job()==null||job().get("output").getAsInt()!=1)return;tab("PROGRESS");scroll(1);photo(now);phase(9);}
            case 14->{if(!atWorkScreen())return;check(string(state(),"station").equals("DELIVERY"),"The task did not reach its actual delivery station");tab("PROGRESS");check(click("gui.lawsjustice.work.deliver"),"Native sealed-delivery button unavailable");request("work_deliver");phase(15);}
            case 15->{if(!atWorkScreen()||job()!=null)return;long expected=(index()+1)*1200L;check(state().getAsJsonObject("self").get("reductionTicks").getAsLong()==expected,"An actual delivery awarded the wrong sentence reduction");deliveryVerified=false;UUID finishedTask=task;server(()->{var data=JusticeData.get(player().getServer());var record=data.work().jobs().get(finishedTask);check(record!=null&&record.status().equals("DONE")&&record.awardedTicks()==1200,"The real delivery was not committed once with its actual award");check(data.work().credits().get(sentence).ticks()==expected,"Work credit and custody reduction diverged");deliveryVerified=true;});phase(16);}
            case 16->{if(!deliveryVerified)return;FINISHED.add(kind);step=index()==5?250:130+(index()+1)*20;}
            default->throw new IllegalStateException("Unexpected actual work phase "+phase());
        }
    }
    static void validateCapture(String name){
        if(name.equals("laws-justice-work-furniture.png")||name.equals("laws-justice-work-board-model.png")||name.equals("laws-justice-workshop-model.png")||name.equals("laws-justice-work-posts-models.png")){check(Minecraft.getInstance().screen==null,"A work model photograph was replaced by a stale menu");return;}
        String expected=switch(name){case "laws-justice-work-zones.png"->"ZONES";case "laws-justice-work-rules.png"->"RULES";case "laws-justice-work-board-menu.png"->"TASKS";case "laws-justice-work-history.png"->"HISTORY";default->name.endsWith("-work.png")||name.endsWith("-production.png")||name.equals("laws-justice-work-sentence-reductions.png")?"PROGRESS":"";};
        if(!expected.isEmpty())check(Minecraft.getInstance().screen!=null&&Minecraft.getInstance().screen.getClass().getSimpleName().equals("WorkScreen")&&field(Minecraft.getInstance().screen,"tab").toString().equals(expected),"Work screenshot does not show its genuine native tab: "+name);
    }
    static void addReport(Map<String,Object> report){report.put("real_work_models",models);report.put("real_work_zone_ui",zoneUi);report.put("real_work_policy_ui",policyUi);report.put("real_work_survival_prisoner",survival);report.put("real_work_deliveries",FINISHED.size()==6);report.put("real_work_persistence",persistence);report.put("work_kinds_completed",List.copyOf(FINISHED));report.put("work_native_actions",List.copyOf(ACTIONS));report.put("work_demonstration_quantity_per_task",1);report.put("work_demonstration_reduction_per_task_ticks",1200);report.put("work_sentence_id",sentence);}
}
