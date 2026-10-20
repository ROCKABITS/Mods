package pt.realmcasino.test;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmcasino.block.BlockRegistry;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.data.CasinoData;
import pt.realmcasino.data.GameState;
import pt.realmcasino.domain.CasinoLedger;
import pt.realmcasino.domain.ChickenRoad;
import pt.realmcasino.service.CasinoService;

/** Real registered furniture, durable wallets, server actions and NBT migration for phase two. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoArcadeGameTests {
    private static boolean economy(CasinoFixture f){f.open(f.player);return ChroniclesBridge.loaded();}
    private static JsonObject move(CasinoFixture f){var p=f.mutation(f.player);var game=f.view(f.player).getAsJsonObject("round");p.addProperty("round",game.get("id").getAsString());p.addProperty("revision",game.get("revision").getAsLong());return p;}
    private static void safe(CasinoFixture f,int steps){try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(1))){for(int i=0;i<steps;i++)f.act(f.player,"advance",move(f));}}
    private static void start(CasinoFixture f){f.fund(10000);f.open(f.player);f.amount(f.player,"bet",100);}

    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void newMachinesRequireChroniclesAndDoNotMintAFallbackCurrency(GameTestHelper h){
        for(var block:List.of(BlockRegistry.PLINKO_BOARD.get(),BlockRegistry.FORTUNE_WHEEL.get(),BlockRegistry.CHICKEN_ROAD_TABLE.get()))try(var f=new CasinoFixture(h,block)){
            f.open(f.player);h.assertTrue(f.view(f.player).get("game").getAsString().equals(block.game()),"Wrong registered game session");
            if(!ChroniclesBridge.loaded()){f.amount(f.player,"bet",100);h.assertTrue(f.data.states().isEmpty()&&f.machine().bank()==0&&!f.player.snapshot().get("error").getAsString().isEmpty(),"Standalone arcade accepted or minted money");}
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void realPlinkoPathSettles32xOnceAndClaimCannotReplay(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.PLINKO_BOARD.get())){
            if(!economy(f)){h.succeed();return;}f.fund(10000);f.open(f.player);
            try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(1))){f.amount(f.player,"bet",100);}
            var game=f.data.states().get(f.player.getUUID());
            h.assertTrue(game.finished()&&game.reels().size()==12&&game.reels().stream().allMatch(v->v==1)&&game.payout()==3200&&f.pending(f.player)==3200&&f.machine().bank()==6900&&f.machine().reserved()==0,"Plinko did not conserve its actual 32x payout");
            h.assertTrue(ChroniclesBridge.balance(f.player)==99900,"Plinko charged an incorrect stake");
            var claim=f.amount(f.player,0);f.act(f.player,"claim",claim);f.act(f.player,"claim",claim);
            h.assertTrue(ChroniclesBridge.balance(f.player)==103100&&f.pending(f.player)==0,"Plinko claim replay credited twice");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void realFortuneWheelCommitsTheExactSectorAndPrize(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.FORTUNE_WHEEL.get())){
            if(!economy(f)){h.succeed();return;}f.fund(10000);f.open(f.player);
            try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(18))){f.amount(f.player,"bet",100);}
            var game=f.data.states().get(f.player.getUUID());
            h.assertTrue(game.finished()&&game.reels().equals(List.of(18))&&game.payout()==500&&f.machine().bank()+f.pending(f.player)==10100&&f.pending(f.player)==500,"Wheel result does not match its five-times prize");
            h.assertTrue(h.getLevel().getBlockEntity(f.pos) instanceof pt.realmcasino.blockentity.CasinoBlockEntity be&&be.result()[0]==18,"Committed fortune sector did not reach the native block entity");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void chickenReservesAllTenStepsAndCashoutUsesOneStakeAndOneSettlement(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);
            h.assertTrue(f.machine().reserved()==884&&ChroniclesBridge.balance(f.player)==99900,"Chicken did not reserve its full ten-step liability");
            safe(f,2);var active=f.data.states().get(f.player.getUUID());
            h.assertTrue(!active.finished()&&active.reels().equals(List.of(1,1))&&ChroniclesBridge.balance(f.player)==99900,"Crossing charged again or lost progress");
            var cashout=move(f);f.act(f.player,"cashout",cashout);f.act(f.player,"cashout",cashout);
            h.assertTrue(f.pending(f.player)==148&&f.machine().reserved()==0&&f.machine().bank()==9952&&f.data.ledger().history().size()==1,"Cashout or its replay changed the wrong amount");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void chickenCrashCannotBeReplayedOrCashedOut(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);safe(f,2);
            try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(0))){f.act(f.player,"advance",move(f));}
            var game=f.data.states().get(f.player.getUUID());var request=move(f);f.act(f.player,"cashout",request);
            h.assertTrue(game.finished()&&game.reels().equals(List.of(1,1,0))&&game.payout()==0&&f.pending(f.player)==0&&f.machine().bank()==10100&&f.data.states().get(f.player.getUUID()).equals(game),"Crash could be undone or paid out");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void closingChickenCollectsSafeProgressWithoutAnotherCrossing(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);safe(f,1);
            CasinoService.action(f.player,"close","{}");CasinoService.logout(f.player);CasinoService.logout(f.player);
            h.assertTrue(f.pending(f.player)==118&&f.machine().reserved()==0&&f.data.ledger().history().size()==1,"Closing or repeated disconnect lost or repeated the earned crossing");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void closingBeforeFirstCrossingRefundsOnlyTheOriginalStake(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);CasinoService.action(f.player,"close","{}");
            h.assertTrue(f.pending(f.player)==100&&f.machine().bank()==10000&&ChroniclesBridge.balance(f.player)==99900,"An unplayed road minted money or lost the stake");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void tenthCrossingAutomaticallyCollectsAndOldRevisionsCannotAdvance(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);var stale=move(f);safe(f,1);
            f.act(f.player,"advance",stale);h.assertTrue(f.data.states().get(f.player.getUUID()).reels().size()==1,"A replay crossed another road");
            safe(f,9);var game=f.data.states().get(f.player.getUUID());
            h.assertTrue(game.finished()&&game.reels().size()==10&&game.payout()==884&&f.pending(f.player)==884&&f.machine().reserved()==0,"Ten successes did not auto-collect exactly the reserved prize");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void chickenProgressAndReservationsSurviveDurableNbtReload(GameTestHelper h)throws Exception{
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);safe(f,3);
            var restored=CasinoData.load(NbtIo.readCompressed(f.data.path(),NbtAccounter.unlimitedHeap()),h.getLevel().registryAccess());
            h.assertTrue(restored.states().equals(f.data.states())&&restored.ledger().rounds().equals(f.data.ledger().rounds())&&restored.ledger().machines().equals(f.data.ledger().machines()),"Reload lost the road progress or liability");
            CasinoFixture.field(restored,"file",f.data.path());f.loaded.put(h.getLevel().getServer(),restored);
            CasinoService.logout(f.player);h.assertTrue(restored.ledger().credit(f.player.getUUID())==185&&restored.ledger().rounds().isEmpty(),"Reloaded road did not auto-collect the original stage once");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void deadlineAndPhysicalRangeAutoCollectRoadProgress(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);safe(f,2);f.player.setPos(f.pos.getX()+30,f.pos.getY(),f.pos.getZ());
            for(int i=0;i<40;i++)CasinoService.tick(h.getLevel().getServer());
            h.assertTrue(f.pending(f.player)==148&&f.data.ledger().rounds().isEmpty(),"Leaving the physical road did not safely collect progress");
        }
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);safe(f,2);var game=f.data.states().get(f.player.getUUID());long now=System.currentTimeMillis()-61000;
            var expired=new GameState(game.id(),game.machine(),game.player(),game.kind(),game.stake(),now,now+60000,game.revision(),null,game.reels(),-1,"",-1,0,false);
            var ledger=f.data.ledger();var old=ledger.rounds().get(game.id());var aged=new CasinoLedger.Round(old.id(),old.machineId(),old.player(),old.stake(),old.maxPayout(),now);
            f.data.replace(new CasinoLedger(ledger.machines().values(),List.of(aged),ledger.credits(),ledger.history()),Map.of(game.player(),expired));
            for(int i=0;i<40;i++)CasinoService.tick(h.getLevel().getServer());
            h.assertTrue(f.pending(f.player)==148&&f.data.ledger().rounds().isEmpty(),"Expired road did not auto-collect the earned progress");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void wrongGameActionsAndStaleRoadRevisionCannotMutateProgress(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.CHICKEN_ROAD_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);var game=f.data.states().get(f.player.getUUID());
            f.act(f.player,"hit",move(f));h.assertTrue(f.data.states().get(f.player.getUUID()).equals(game),"Blackjack action changed a road");
            safe(f,1);var request=move(f);request.addProperty("revision",1);var after=f.data.states().get(f.player.getUUID());f.act(f.player,"advance",request);
            h.assertTrue(f.data.states().get(f.player.getUUID()).equals(after)&&ChroniclesBridge.balance(f.player)==99900,"Stale road revision changed progress or wallet");
            f.open(f.owner);h.assertTrue(f.view(f.owner).get("busy").getAsBoolean()&&f.view(f.owner).get("round").isJsonNull(),"Another player can operate the active road");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void existingVersionOneWorldsMigrateWithoutResettingBanksOrClaims(GameTestHelper h){
        var data=new CasinoData();var owner=java.util.UUID.randomUUID();var machine=java.util.UUID.randomUUID();
        var ledger=new CasinoLedger().register(machine,owner,CasinoLedger.Kind.SLOTS,"minecraft:overworld",1,"Existing bank",1,100).deposit(machine,owner,10000).withdraw(machine,owner,100);
        CasinoFixture.field(data,"ledger",ledger);var raw=data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());raw.putInt("version",1);
        var restored=CasinoData.load(raw,h.getLevel().registryAccess());
        h.assertTrue(restored.ledger().machine(machine).orElseThrow().bank()==9900&&restored.ledger().credit(owner)==100&&restored.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()).getInt("version")==3,"Existing world format did not migrate to version two");
        System.out.println("CASINO_ARCADE_NATIVE_PASSED");h.succeed();
    }
}
