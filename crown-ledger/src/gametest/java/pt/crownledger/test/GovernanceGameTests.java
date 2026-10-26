package pt.crownledger.test;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.gametest.*;
import pt.crownledger.api.CrownIncomeApi;
import pt.crownledger.block.BlockRegistry;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.*;
import pt.crownledger.service.*;
import static pt.crownledger.domain.Treasury.Currency.EMERALD;
import static pt.crownledger.domain.Governance.*;

@GameTestHolder("kingdomtreasury") @PrefixGameTestTemplate(false)
public final class GovernanceGameTests {
    static class Actor extends ServerPlayer {
        Actor(GameTestHelper h){super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"TreasuryQA"),ClientInformation.createDefault());connection=new ServerGamePacketListenerImpl(getServer(),new Connection(PacketFlow.SERVERBOUND),this,CommonListenerCookie.createInitial(getGameProfile(),false)){
            @Override public void send(Packet<?> packet){}
            @Override public boolean isAcceptingMessages(){return true;}
        };setGameMode(GameType.SURVIVAL);}
    }
    static class Fixture implements AutoCloseable {
        final GameTestHelper h;final TreasuryData data;final Treasury old;final Governance oldGov;final IncomeLedger income;final Path file;final byte[] bytes;final boolean dirty;
        Fixture(GameTestHelper h){this.h=h;IOUtilities.waitUntilIOWorkerComplete();data=TreasuryData.get(h.getLevel().getServer());old=data.treasury;oldGov=data.governance;income=data.income;dirty=data.isDirty();file=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/crown_ledger.dat");try{bytes=Files.isRegularFile(file)?Files.readAllBytes(file):null;}catch(Exception e){throw new IllegalStateException(e);}data.treasury=new Treasury();data.governance=new Governance();data.income=new IncomeLedger();data.treasury.addFunds(EMERALD,1000,Treasury.Kind.DEPOSIT,null,"Native treasury fixture",0);}
        TreasuryData reopen(){try{return TreasuryData.load(NbtIo.readCompressed(file,NbtAccounter.create(32_000_000)).getCompound("data"),h.getLevel().registryAccess());}catch(Exception e){throw new IllegalStateException(e);}}
        public void close(){IOUtilities.waitUntilIOWorkerComplete();data.treasury=old;data.governance=oldGov;data.income=income;data.setDirty(dirty);try{if(Files.isDirectory(file))Files.delete(file);if(bytes==null)Files.deleteIfExists(file);else Files.write(file,bytes);}catch(Exception e){throw new IllegalStateException(e);}}
    }
    static void reject(GameTestHelper h,Runnable op){boolean bad=false;try{op.run();}catch(RuntimeException expected){bad=true;}h.assertTrue(bad,"Invalid governance operation succeeded");}
    static UUID propose(Fixture f,UUID player,long now,Offer offer){return f.data.governance.propose(f.data.treasury,player,"Aetherion","Royal proposal",offer,Set.of(player),50,4,10_000,0,now);}
    static void active(Fixture f,UUID player,Benefit kind,int strength){f.data.governance=new Governance();long now=GovernanceService.now(f.h.getLevel().getServer());var id=propose(f,player,now-10_000,new Offer(kind,EMERALD,1,60000,strength));f.data.governance.vote(id,player,true,now-1);f.data.governance.advance(f.data.treasury,now);}
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void ballotEscrowAndExternalIncomeSurviveActualCompressedFileAndReplay(GameTestHelper h){try(var f=new Fixture(h)){var player=UUID.randomUUID();long now=System.currentTimeMillis();var id=propose(f,player,now,new Offer(Benefit.WORKS,EMERALD,400,60000,1));f.data.governance.vote(id,player,true,now+1);f.data.commitGovernance(h.getLevel().getServer(),f.data.treasury.copy(),f.data.governance.copy());var restored=f.reopen();h.assertTrue(restored.treasury.balance(EMERALD)==600&&restored.governance.current().yes()==1,"Ballot and reserved cost did not persist atomically");var receipt=UUID.randomUUID();CrownIncomeApi.credit(h.getLevel().getServer(),receipt,"EMERALD",25,player,"Native external tax during vote");restored=f.reopen();h.assertTrue(restored.treasury.balance(EMERALD)==625&&restored.income.receipts().containsKey(receipt)&&restored.governance.current().id().equals(id)&&restored.governance.current().yes()==1,"An external tax erased a funded ballot");restored.governance.advance(restored.treasury,now+10000);f.data.commitGovernance(h.getLevel().getServer(),restored.treasury,restored.governance);restored=f.reopen();h.assertTrue(restored.governance.current()==null&&restored.governance.active().size()==1&&restored.governance.history().size()==1&&restored.treasury.balance(EMERALD)==625,"Paid benefit was not recoverable");restored.governance.advance(restored.treasury,now+10001);h.assertTrue(restored.treasury.balance(EMERALD)==625&&restored.governance.history().size()==1,"Recovery charged twice");}h.succeed();}
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void failedAtomicVoteWritePreservesBothStatesAndCanRetry(GameTestHelper h){try(var f=new Fixture(h)){var treasury=f.data.treasury.copy();var gov=f.data.governance.copy();gov.propose(treasury,UUID.randomUUID(),"King","Poll",new Offer(Benefit.FEAST,EMERALD,400,60000,20),Set.of(UUID.randomUUID()),50,2,10000,0,System.currentTimeMillis());try{Files.deleteIfExists(f.file);Files.createDirectory(f.file);}catch(Exception e){throw new IllegalStateException(e);}reject(h,()->f.data.commitGovernance(h.getLevel().getServer(),treasury,gov));h.assertTrue(f.data.treasury.balance(EMERALD)==1000&&f.data.governance.current()==null,"Failed replacement committed half a ballot");try{Files.delete(f.file);}catch(Exception e){throw new IllegalStateException(e);}f.data.commitGovernance(h.getLevel().getServer(),treasury,gov);h.assertTrue(f.reopen().treasury.balance(EMERALD)==600&&f.reopen().governance.current()!=null,"Storage recovery could not retry");}h.succeed();}
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void formatSevenRejectsCorruptOrDowngradedFundedBallotsAndMigratesSix(GameTestHelper h){try(var f=new Fixture(h)){var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess());tag.putInt("version",6);tag.remove("governance");var legacy=TreasuryData.load(tag,h.getLevel().registryAccess());h.assertTrue(legacy.treasury.balance(EMERALD)==1000&&legacy.governance.current()==null&&legacy.save(new CompoundTag(),h.getLevel().registryAccess()).getInt("version")==9,"Legacy migration lost funds");propose(f,UUID.randomUUID(),System.currentTimeMillis(),new Offer(Benefit.WORKS,EMERALD,400,60000,1));var funded=f.data.save(new CompoundTag(),h.getLevel().registryAccess());funded.putInt("version",6);reject(h,()->TreasuryData.load(funded,h.getLevel().registryAccess()));funded.putInt("version",7);funded.putByteArray("governance",new byte[]{(byte)0xFF,0});reject(h,()->TreasuryData.load(funded,h.getLevel().registryAccess()));funded.putString("governance","{}");reject(h,()->TreasuryData.load(funded,h.getLevel().registryAccess()));}h.succeed();}
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void publicSnapshotsContainOnlyOwnChoiceAndFinalTallies(GameTestHelper h){try(var f=new Fixture(h)){var a=new Actor(h);var b=new Actor(h);long now=GovernanceService.now(h.getLevel().getServer());var id=f.data.governance.propose(f.data.treasury,a.getUUID(),"King","Private choices",null,Set.of(a.getUUID(),b.getUUID()),50,2,10000,0,now);f.data.governance.vote(id,a.getUUID(),true,now);String raw=GovernanceService.snapshot(b,false,"");var p=JsonParser.parseString(raw).getAsJsonObject().getAsJsonObject("poll");h.assertTrue(!p.has("ballots")&&!p.has("eligible")&&!p.has("yes")&&!p.has("no")&&!p.has("ownVote")&&!raw.contains(a.getUUID().toString()),"Private votes or electorate identities leaked");h.assertTrue(JsonParser.parseString(GovernanceService.snapshot(a,false,"")).getAsJsonObject().getAsJsonObject("poll").get("ownVote").getAsBoolean(),"Citizen cannot see their own ballot");f.data.governance.advance(f.data.treasury,now+10000);var publicResult=JsonParser.parseString(GovernanceService.snapshot(b,false,"")).getAsJsonObject().getAsJsonArray("history").get(0).getAsJsonObject();h.assertTrue(publicResult.get("yes").getAsInt()==1&&!publicResult.has("ballots"),"Final tally unavailable or private choices retained");}h.succeed();}
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void authorityRequiresPhysicalChestAndSwornLivePresident(GameTestHelper h){try(var f=new Fixture(h);var council=CouncilFixture.loaded()?new CouncilFixture(h.getLevel().getServer()):null){var a=new Actor(h);var pos=h.absolutePos(new BlockPos(4,1,4));h.getLevel().setBlockAndUpdate(pos,BlockRegistry.CROWN_CHEST.get().defaultBlockState());a.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);if(council!=null)council.mandate(a.getUUID(),System.currentTimeMillis()-1000,System.currentTimeMillis()+60000,0);else f.data.treasury.setStandaloneManager(a.getUUID(),a.getUUID(),System.currentTimeMillis());reject(h,()->GovernanceService.requireProposer(a));CrownService.openBoard(a,pos);if(council!=null){reject(h,()->GovernanceService.requireProposer(a));council.claim(System.currentTimeMillis()-1);}GovernanceService.requireProposer(a);if(council!=null){council.expire(System.currentTimeMillis()-1);reject(h,()->GovernanceService.requireProposer(a));council.mandate(a.getUUID(),System.currentTimeMillis()-1000,System.currentTimeMillis()+60000,System.currentTimeMillis()-1);}a.setPos(pos.getX()+20,pos.getY(),pos.getZ());reject(h,()->GovernanceService.requireProposer(a));}h.succeed();}
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void realServerMiningAndXpEventsRespectCreativeAndExpiry(GameTestHelper h){try(var f=new Fixture(h)){var a=new Actor(h);active(f,a.getUUID(),Benefit.WORKS,20);var speed=new PlayerEvent.BreakSpeed(a,Blocks.STONE.defaultBlockState(),10,BlockPos.ZERO);NeoForge.EVENT_BUS.post(speed);h.assertTrue(Math.abs(speed.getNewSpeed()-12)<.001,"Mining event did not apply public works");a.setGameMode(GameType.CREATIVE);var creative=new PlayerEvent.BreakSpeed(a,Blocks.STONE.defaultBlockState(),10,BlockPos.ZERO);NeoForge.EVENT_BUS.post(creative);h.assertTrue(creative.getNewSpeed()==10,"Creative received a survival benefit");a.setGameMode(GameType.SURVIVAL);active(f,a.getUUID(),Benefit.KNOWLEDGE,20);int xp=a.totalExperience;a.giveExperiencePoints(100);h.assertTrue(a.totalExperience-xp==120,"Actual XP grant was not increased by 20 percent");f.data.governance=new Governance();var expired=new PlayerEvent.BreakSpeed(a,Blocks.STONE.defaultBlockState(),10,BlockPos.ZERO);NeoForge.EVENT_BUS.post(expired);h.assertTrue(expired.getNewSpeed()==10,"Expired benefit still modifies speed");}h.succeed();}
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void harvestUsesRealGrowthEventsWithoutOverridingProtectionOrMatureCrops(GameTestHelper h){
        try(var f=new Fixture(h)){
            active(f,UUID.randomUUID(),Benefit.HARVEST,100);
            var pos=h.absolutePos(new BlockPos(3,1,3));
            var eligible=new net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre(h.getLevel(),pos,Blocks.WHEAT.defaultBlockState());
            NeoForge.EVENT_BUS.post(eligible);h.assertTrue(eligible.getResult()==net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre.Result.GROW,"Harvest did not grant an eligible growth opportunity");
            var protectedCrop=new net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre(h.getLevel(),pos,Blocks.WHEAT.defaultBlockState());
            protectedCrop.setResult(net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre.Result.DO_NOT_GROW);NeoForge.EVENT_BUS.post(protectedCrop);
            h.assertTrue(protectedCrop.getResult()==net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre.Result.DO_NOT_GROW,"Harvest overrode a protection decision");
            var mature=new net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre(h.getLevel(),pos,Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7));
            NeoForge.EVENT_BUS.post(mature);h.assertTrue(mature.getResult()==net.neoforged.neoforge.event.level.block.CropGrowEvent.Pre.Result.DEFAULT,"Mature crops received extra resources");
        }h.succeed();
    }
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void actualPlayerExhaustionMixinReducesOnlyPositiveSurvivalHunger(GameTestHelper h){try(var f=new Fixture(h)){var a=new Actor(h);active(f,a.getUUID(),Benefit.FEAST,20);float before=a.getFoodData().getExhaustionLevel();a.causeFoodExhaustion(1);h.assertTrue(Math.abs(a.getFoodData().getExhaustionLevel()-before-.8F)<.001,"Actual exhaustion did not pass through the benefit mixin");f.data.governance=new Governance();before=a.getFoodData().getExhaustionLevel();a.causeFoodExhaustion(1);h.assertTrue(Math.abs(a.getFoodData().getExhaustionLevel()-before-1)<.001,"Hunger remained modified after expiry");}h.succeed();}

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void freePollDescriptionSurvivesIncomeBallotsFinalHistoryAndRealFileReopen(GameTestHelper h){
        try(var f=new Fixture(h)){
            var player=UUID.randomUUID();long now=System.currentTimeMillis();String description="Construir uma ponte para a vila.\nPassagem pública, feita de pedra e madeira.";
            var id=f.data.governance.propose(f.data.treasury,player,"Aetherion","Nova ponte",description,null,Set.of(player),50,2,10000,0,now);
            f.data.governance.vote(id,player,true,now+1);f.data.commitGovernance(h.getLevel().getServer(),f.data.treasury.copy(),f.data.governance.copy());
            CrownIncomeApi.credit(h.getLevel().getServer(),UUID.randomUUID(),"EMERALD",25,player,"Income during described poll");
            var restored=f.reopen();h.assertTrue(restored.governance.current().description().equals(description)&&restored.governance.current().yes()==1&&restored.treasury.balance(EMERALD)==1025,"Description or money lost during external income");
            restored.governance.advance(restored.treasury,now+10000);f.data.commitGovernance(h.getLevel().getServer(),restored.treasury,restored.governance);restored=f.reopen();
            h.assertTrue(restored.governance.history().getFirst().question().equals("Nova ponte")&&restored.governance.history().getFirst().description().equals(description)&&restored.treasury.balance(EMERALD)==1025&&restored.governance.active().isEmpty(),"General poll changed funds or lost its archived text");
            var downgraded=restored.save(new CompoundTag(),h.getLevel().registryAccess());downgraded.putInt("version",7);reject(h,()->TreasuryData.load(downgraded,h.getLevel().registryAccess()));
        }h.succeed();
    }
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void actualFormatSevenBallotWithoutDescriptionMigratesWithoutLosingEscrowOrVotes(GameTestHelper h){
        try(var f=new Fixture(h)){
            var player=UUID.randomUUID();long now=System.currentTimeMillis();var id=propose(f,player,now,new Offer(Benefit.WORKS,EMERALD,400,60000,20));
            f.data.governance.vote(id,player,true,now+1);var old=f.data.save(new CompoundTag(),h.getLevel().registryAccess());old.putInt("version",7);
            var legacy=JsonParser.parseString(new String(old.getByteArray("governance"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();legacy.getAsJsonObject("current").remove("description");
            old.putByteArray("governance",legacy.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));var migrated=TreasuryData.load(old,h.getLevel().registryAccess());
            h.assertTrue(migrated.governance.current().description().isEmpty()&&migrated.governance.current().yes()==1&&migrated.treasury.balance(EMERALD)==600,"Format-seven migration lost a reserved vote");
            migrated.governance.advance(migrated.treasury,now+10000);h.assertTrue(migrated.governance.history().getFirst().outcome()==Outcome.APPROVED&&migrated.governance.active().size()==1&&migrated.treasury.balance(EMERALD)==600,"Migrated funded vote settled incorrectly");
        }h.succeed();
    }
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void completeDescribedHistoryAndMaximumRequestFitActualProductionPacketCodecs(GameTestHelper h){
        try(var f=new Fixture(h)){
            var player=new Actor(h);String text="a"+"\u2028".repeat(998)+"z";long began=System.currentTimeMillis();
            for(int i=0;i<64;i++){long now=began+i*2000L;f.data.governance.propose(f.data.treasury,player.getUUID(),"Aetherion","T"+"\u2028".repeat(118)+"T",text,null,Set.of(player.getUUID()),50,2,1000,0,now);f.data.governance.advance(f.data.treasury,now+1000);}
            String json=GovernanceService.snapshot(player,false,"");h.assertTrue(json.length()>262144,"History fixture did not exceed the previous packet bound");
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try{
                pt.crownledger.network.GovernancePackets.State.CODEC.encode(buffer,new pt.crownledger.network.GovernancePackets.State(json));
                h.assertTrue(pt.crownledger.network.GovernancePackets.State.CODEC.decode(buffer).json().equals(json),"Full description history was truncated by the codec");
                buffer.clear();var payload=new JsonObject();payload.addProperty("title","T".repeat(120));payload.addProperty("description",text);
                var request=new pt.crownledger.network.GovernancePackets.Request("propose",payload.toString());pt.crownledger.network.GovernancePackets.Request.CODEC.encode(buffer,request);
                h.assertTrue(pt.crownledger.network.GovernancePackets.Request.CODEC.decode(buffer).equals(request),"Maximum proposal was truncated by the request codec");
            }finally{buffer.release();}
        }h.succeed();
    }
}
