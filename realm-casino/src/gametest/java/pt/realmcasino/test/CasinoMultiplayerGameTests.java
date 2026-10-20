package pt.realmcasino.test;

import com.google.gson.JsonObject;
import java.util.ArrayList;
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
import pt.realmcasino.domain.TableGame;
import pt.realmcasino.service.CasinoService;

/** Real funded seats, authorized turn actions, private snapshots and durable multiplayer hands. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoMultiplayerGameTests {
    private static boolean economy(CasinoFixture f){f.open(f.player);return ChroniclesBridge.loaded();}
    private static JsonObject action(CasinoFixture f,CasinoActors.Actor actor){var p=f.mutation(actor);var t=f.view(actor).getAsJsonObject("table");p.addProperty("round",t.get("id").getAsString());p.addProperty("revision",t.get("revision").getAsLong());return p;}
    private static void seats(CasinoFixture f){f.open(f.owner);f.amount(f.owner,"table_join",100);f.open(f.player);f.amount(f.player,"table_join",100);}
    private static void start(CasinoFixture f){seats(f);f.act(f.owner,"table_start",action(f,f.owner));}
    private static TableGame table(CasinoFixture f){return f.data.ledger().tables().get(f.machine().id());}
    private static CasinoActors.Actor actor(CasinoFixture f,java.util.UUID id){return id.equals(f.owner.getUUID())?f.owner:f.player;}

    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void sixNewModelsRegisterAndAllPvPBuyInsStayDisabledWithoutChronicles(GameTestHelper h){
        for(var block:List.of(BlockRegistry.POKER_TABLE.get(),BlockRegistry.MULTIPLAYER_BLACKJACK.get(),BlockRegistry.DICE_TABLE.get(),BlockRegistry.COIN_TABLE.get()))try(var f=new CasinoFixture(h,block)){
            f.open(f.player);h.assertTrue(f.view(f.player).get("shared").getAsBoolean(),"Shared table did not expose its public mode");
            if(!ChroniclesBridge.loaded()){f.amount(f.player,"table_join",100);h.assertTrue(f.data.ledger().tables().isEmpty()&&f.data.ledger().credits().isEmpty(),"Standalone table created money or accepted a buy-in");}
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void fiveReelJackpotUsesExactlyThePublishedThousandTimesReturn(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.FIVE_SLOT_MACHINE.get())){
            if(!economy(f)){h.succeed();return;}f.fund(10000);f.open(f.player);try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(0))){f.amount(f.player,"bet",2);}
            var game=f.data.states().get(f.player.getUUID());h.assertTrue(game.reels().equals(List.of(0,0,0,0,0))&&game.payout()==2000&&f.pending(f.player)==2000&&f.machine().bank()==8002&&ChroniclesBridge.balance(f.player)==99998,"Five reels did not reserve/pay the 1000x jackpot exactly once");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void actualMiniatureRaceUsesOneOfTheFourFundedSelections(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.HORSE_RACE_TABLE.get())){
            if(!economy(f)){h.succeed();return;}f.fund(10000);f.open(f.player);var wager=f.amount(f.player,100);wager.addProperty("number",1);
            try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(0))){f.act(f.player,"bet",wager);}
            var game=f.data.states().get(f.player.getUUID());h.assertTrue(game.reels().getFirst()==1&&game.payout()==380&&f.pending(f.player)==380&&f.machine().bank()==9720,"Real race did not pay the selected winner 3.8x");
            h.assertTrue(f.view(f.player).getAsJsonObject("round").get("selectionNumber").getAsInt()==1,"Race snapshot lost the player's committed selection");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void fundedPokerSeatsUseNoHouseBankAndPrivateCardsNeverReachAnotherPlayer(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.POKER_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);var table=table(f);var view=f.view(f.owner).getAsJsonObject("table");
            h.assertTrue(ChroniclesBridge.balance(f.owner)==1999900&&ChroniclesBridge.balance(f.player)==99900&&table.escrow()==200&&f.machine().bank()==0,"Actual poker seats did not reserve the two genuine wallets");
            h.assertTrue(table.phase()==TableGame.Phase.PLAYING&&view.getAsJsonArray("board").isEmpty()&&!view.has("deck"),"Poker exposed its future board or undealt deck");
            for(var raw:view.getAsJsonArray("seats")){var seat=raw.getAsJsonObject();boolean own=seat.get("id").getAsString().equals(f.owner.getUUID().toString());h.assertTrue(seat.getAsJsonArray("cards").size()==2&&seat.getAsJsonArray("cards").get(0).getAsInt()==(own?table.seat(f.owner.getUUID()).cards().getFirst():-1),"Private poker hole card reached another participant");}
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void oneLobbyBuyInCannotBeReplayedAndLeavingRefundsExactlyOnce(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.DICE_TABLE.get())){
            if(!economy(f)){h.succeed();return;}f.open(f.player);var buyin=f.amount(f.player,100);f.act(f.player,"table_join",buyin);f.act(f.player,"table_join",buyin);
            h.assertTrue(ChroniclesBridge.balance(f.player)==99900&&table(f).seats().size()==1,"Buy-in replay charged twice");var leave=action(f,f.player);f.act(f.player,"table_leave",leave);f.act(f.player,"table_leave",leave);
            h.assertTrue(f.data.ledger().tables().isEmpty()&&f.pending(f.player)==100,"Lobby leave duplicated or lost the refund");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void aDifferentBuyInOrThirdCoinSeatCannotEnterTheFundedLobby(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.COIN_TABLE.get())){
            if(!economy(f)){h.succeed();return;}f.open(f.owner);f.amount(f.owner,"table_join",100);f.open(f.player);f.amount(f.player,"table_join",101);h.assertTrue(ChroniclesBridge.balance(f.player)==100000&&table(f).seats().size()==1,"A different coin buy-in entered the pot");f.amount(f.player,"table_join",100);
            var third=f.actors.actor("ThirdCoinQA",100000);f.open(third);f.amount(third,"table_join",100);h.assertTrue(ChroniclesBridge.balance(third)==100000&&table(f).seats().size()==2,"A third seat debited into a two-player coin toss");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void headsTossAndRepeatedStartCannotPayTheSamePotTwice(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.COIN_TABLE.get())){
            if(!economy(f)){h.succeed();return;}seats(f);var request=action(f,f.owner);try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(0))){f.act(f.owner,"table_start",request);}f.act(f.owner,"table_start",request);
            h.assertTrue(table(f).finished()&&f.pending(f.owner)==200&&f.pending(f.player)==0&&f.data.ledger().history().size()==2,"Coin start or its replay failed to distribute exactly one funded pot");
            f.amount(f.owner,"claim",0);h.assertTrue(ChroniclesBridge.balance(f.owner)==2000100,"Actual winner did not collect the funded coin prize");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void equalDiceTotalsSplitTheFundedPotWithoutCreatingMoney(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.DICE_TABLE.get())){
            if(!economy(f)){h.succeed();return;}seats(f);try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(5))){f.act(f.owner,"table_start",action(f,f.owner));}
            h.assertTrue(table(f).results().equals(List.of(6,6,6,6))&&f.pending(f.owner)==100&&f.pending(f.player)==100&&table(f).escrow()==0,"Equal actual dice totals did not split the exact funded pot");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void wrongTurnAndAnOldPokerRevisionCannotSpendAnotherPlayersStack(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.POKER_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);var before=table(f);CasinoActors.Actor acting=actor(f,before.seats().get(before.actor()).player()),other=acting==f.owner?f.player:f.owner;
            f.act(other,"table_allin",action(f,other));h.assertTrue(table(f).equals(before),"An outsider to the turn moved another player's stack");var old=action(f,acting);f.act(acting,"table_call",old);var after=table(f);f.act(acting,"table_call",old);h.assertTrue(table(f).equals(after),"An old poker action was played twice");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void actualPokerAllInAndSidePotsUseTheOriginalReservedMoney(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.POKER_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);var first=actor(f,table(f).seats().get(table(f).actor()).player());f.act(first,"table_allin",action(f,first));var second=actor(f,table(f).seats().get(table(f).actor()).player());f.act(second,"table_call",action(f,second));
            h.assertTrue(table(f).finished()&&table(f).board().size()==5&&f.pending(f.owner)+f.pending(f.player)==200&&table(f).escrow()==0,"Actual poker all-in lost or created funded money");h.assertTrue(ChroniclesBridge.balance(f.owner)==1999900&&ChroniclesBridge.balance(f.player)==99900,"Poker betting streets charged new wallet money");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void disconnectFoldsThePokerHandAndLeavesUncommittedMoneyClaimable(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.POKER_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);var before=table(f);long unspent=before.seat(f.owner.getUUID()).stack();CasinoService.logout(f.owner);
            h.assertTrue(table(f).finished()&&f.pending(f.owner)==unspent&&f.pending(f.owner)+f.pending(f.player)==200,"Disconnect discarded uncommitted poker money or reset the hand");CasinoService.logout(f.owner);h.assertTrue(f.pending(f.owner)+f.pending(f.player)==200,"Repeated disconnect replayed the poker payout");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void multiplayerBlackjackStandsOriginalCardsAndNeverChargesForHits(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.MULTIPLAYER_BLACKJACK.get())){
            if(!economy(f)){h.succeed();return;}start(f);int actions=0;while(!table(f).finished()){
                h.assertTrue(actions++<10,"Multiplayer twenty-one did not finish");var active=actor(f,table(f).seats().get(table(f).actor()).player());f.act(active,"table_stand",action(f,active));
            }
            h.assertTrue(f.pending(f.owner)+f.pending(f.player)==200&&ChroniclesBridge.balance(f.owner)==1999900&&ChroniclesBridge.balance(f.player)==99900,"Twenty-one charged an extra bet or lost the pot");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void aPokerParticipantCannotStartAHouseBetOrSitAtAnotherTable(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.POKER_TABLE.get())){
            if(!economy(f)){h.succeed();return;}seats(f);var before=f.data.ledger();f.amount(f.owner,"bet",100);h.assertTrue(table(f).phase()==TableGame.Phase.WAITING&&ChroniclesBridge.balance(f.owner)==1999900,"A house bet changed a seated player's escrow");f.amount(f.owner,"table_join",100);h.assertTrue(table(f).seats().size()==2&&f.data.ledger().total().equals(before.total()),"The same player joined the funded table twice");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void filledPokerTableCannotBeDismantledByItsOwner(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.POKER_TABLE.get())){
            if(!economy(f)){h.succeed();return;}seats(f);h.assertTrue(!CasinoService.canBreak(f.owner,f.pos)&&CasinoService.hasFunds(h.getLevel(),f.pos),"Owner can dismantle a table holding player buy-ins");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void actualPokerDeckStacksAndPotSurviveDurableNbtReload(GameTestHelper h)throws Exception{
        try(var f=new CasinoFixture(h,BlockRegistry.POKER_TABLE.get())){
            if(!economy(f)){h.succeed();return;}start(f);var before=table(f);var restored=CasinoData.load(NbtIo.readCompressed(f.data.path(),NbtAccounter.unlimitedHeap()),h.getLevel().registryAccess());h.assertTrue(restored.ledger().tables().get(f.machine().id()).equals(before)&&restored.ledger().total().equals(f.data.ledger().total()),"NBT reload changed a private deck, stack or funded pot");
            CasinoFixture.field(restored,"file",f.data.path());f.loaded.put(h.getLevel().getServer(),restored);CasinoService.logout(f.owner);h.assertTrue(restored.ledger().tables().get(f.machine().id()).finished()&&restored.ledger().credit(f.owner.getUUID())+restored.ledger().credit(f.player.getUUID())==200,"Reloaded poker could not safely settle a departure");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void relabelingAMultiplayerSaveAsAnOlderFormatCannotEraseItsEscrow(GameTestHelper h){
        try(var f=new CasinoFixture(h,BlockRegistry.DICE_TABLE.get())){
            if(!economy(f)){h.succeed();return;}seats(f);var tag=f.data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());tag.putInt("version",2);boolean rejected=false;try{CasinoData.load(tag,h.getLevel().registryAccess());}catch(RuntimeException expected){rejected=true;}h.assertTrue(rejected,"Downgrading a save silently discarded multiplayer escrow");
        }System.out.println("CASINO_MULTIPLAYER_NATIVE_PASSED");h.succeed();
    }
}
