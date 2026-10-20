package pt.contracts.test;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.IOUtilities;
import pt.contracts.*;
import pt.contracts.api.ContractsApi;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.daily.*;
import pt.contracts.daily.DailyLedger.*;
import pt.contracts.daily.DailyRules.*;

@GameTestHolder("contracts_daily")
@PrefixGameTestTemplate(false)
public final class DailyFiscalGameTests {
    private static DailyData data(ServerPlayer p){return DailyData.get(p.getServer());}
    private static Reward finish(ServerPlayer p){Quest q=DailyActors.quest(p,Category.MINING);data(p).ledger.accept(p.getUUID(),q.id,System.currentTimeMillis());DailyService.activity(p,Category.MINING,Validation.NATURAL_BLOCK,t->t.id().equals(q.template.id()),1);return data(p).ledger.rewards.get(q.id);}
    private static Object treasury(MinecraftServer server){try{var type=Class.forName("pt.crownledger.data.TreasuryData");return type.getField("treasury").get(type.getMethod("get",MinecraftServer.class).invoke(null,server));}catch(Exception e){throw new IllegalStateException(e);}}
    @SuppressWarnings({"unchecked","rawtypes"}) private static long bank(MinecraftServer server){try{var currency=(Class<Enum>)Class.forName("pt.crownledger.domain.Treasury$Currency");return(long)treasury(server).getClass().getMethod("balance",currency).invoke(treasury(server),Enum.valueOf(currency,"CHRONICLES"));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void rejected(GameTestHelper h,Runnable r){boolean denied=false;try{r.run();}catch(RuntimeException expected){denied=true;}h.assertTrue(denied,"Unauthorized fiscal operation succeeded");}
    @GameTest(template="daily_empty",batch="daily_fiscal",timeoutTicks=200)
    public static void actualCrownReceivesOnlyTheTaxAndAReceiptReplayCannotDuplicateIt(GameTestHelper h){
        if(!DailyTreasury.available()){h.assertTrue(!Boolean.getBoolean("daily.integration"),"Fresh Crown daily API is missing");h.succeed();return;}
        try(var f=new DailyActors(h)){var p=f.actor();int old=data(p).ledger.settings.taxBps;data(p).ledger.settings.taxBps=1000;try{
            long before=ChroniclesBridge.balance(p),publicBefore=bank(p.getServer());Reward r=finish(p);h.assertTrue(r.paid&&r.tax>0&&ChroniclesBridge.balance(p)-before==r.net&&bank(p.getServer())-publicBefore==r.tax,"Tax split did not conserve the funded gross reward");
            DailyTreasury.credit(p.getServer(),r);h.assertTrue(bank(p.getServer())==publicBefore+r.tax,"Replayed Crown tax was credited twice");
            Object state=treasury(p.getServer());var history=(List<?>)state.getClass().getMethod("history").invoke(state);boolean recorded=false;for(Object row:history){Object kind=row.getClass().getMethod("kind").invoke(row);Object note=row.getClass().getMethod("details").invoke(row);if(kind.toString().equals("TAX_COLLECTED")&&note.toString().equals("contracts.daily-tax"))recorded=true;}h.assertTrue(recorded,"Crown fiscal history did not identify daily quest tax");System.out.println("DAILY_TAX_PASSED");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}finally{data(p).ledger.settings.taxBps=old;}}
        h.succeed();
    }
    @GameTest(template="daily_empty",batch="daily_fiscal",timeoutTicks=200)
    public static void aRealCrownDiskFailureAfterWalletSaveRecoversWithoutPayingAgain(GameTestHelper h){
        if(!DailyTreasury.available()){h.succeed();return;}
        try(var f=new DailyActors(h)){var p=f.actor();int old=data(p).ledger.settings.taxBps;data(p).ledger.settings.taxBps=1000;Path file=p.getServer().getWorldPath(LevelResource.ROOT).resolve("data/crown_ledger.dat");byte[] original=null;
            try{IOUtilities.waitUntilIOWorkerComplete();if(Files.isRegularFile(file))original=Files.readAllBytes(file);Files.deleteIfExists(file);Files.createDirectory(file);long wallet=ChroniclesBridge.balance(p),publicBefore=bank(p.getServer());Reward r=finish(p);
                h.assertTrue(!r.paid&&data(p).journal(p.getUUID())!=null,"Failed Crown credit lost its prepared journal");h.assertTrue(ChroniclesBridge.balance(p)==wallet+r.net,"Net player payment was not durably saved before the fiscal failure");
                Files.delete(file);if(original!=null)Files.write(file,original);ChroniclesBridge.credit(p,10);DailyService.retry(p);
                h.assertTrue(r.paid&&data(p).journal(p.getUUID())==null&&ChroniclesBridge.balance(p)==wallet+r.net+10&&bank(p.getServer())==publicBefore+r.tax,"Recovery repeated money or lost the public tax");DailyService.retry(p);h.assertTrue(ChroniclesBridge.balance(p)==wallet+r.net+10&&bank(p.getServer())==publicBefore+r.tax,"Recovered payment was replayed");System.out.println("DAILY_TAX_INTERRUPTION_PASSED");
            }catch(java.io.IOException e){throw new IllegalStateException(e);}finally{data(p).ledger.settings.taxBps=old;try{if(Files.isDirectory(file)){Files.delete(file);if(original!=null)Files.write(file,original);}}catch(java.io.IOException e){throw new IllegalStateException(e);}}
        }h.succeed();
    }
    @GameTest(template="daily_empty",batch="daily_fiscal",timeoutTicks=200)
    public static void dailyAndExistingPublicContractsShareTheSameReputationCeiling(GameTestHelper h){
        if(!ChroniclesBridge.loaded()||!ContractsApi.treasuryAvailable()){h.succeed();return;}
        try(var f=new DailyActors(h)){var manager=f.actor();var worker=f.actor();DailyActors.op(manager);DailyActors.round(worker);DailyActors.board(worker,h);Quest quest=DailyActors.quest(worker,Category.DELIVERY);data(worker).ledger.accept(worker.getUUID(),quest.id,System.currentTimeMillis());worker.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,4));DailyService.deliver(worker,quest.id);h.assertTrue(ChroniclesBridge.reputation(worker)==2,"Hard daily quest did not reserve its two reputation points");
            try(var council=new DailyCouncilFixture(manager.getServer())){long now=System.currentTimeMillis();council.mandate(manager.getUUID(),now-10000,now+60000,now-5000);
            ContractsApi.depositKingdom(manager,1000,"CHRONICLES");for(int i=0;i<2;i++){var contract=ContractsApi.publishPublic(manager,"Native public work","minecraft:iron_ingot",1,100);ContractsService.complete(worker,contract.id());}
            h.assertTrue(ChroniclesBridge.reputation(worker)==3,"Public contracts ignored the daily quest reputation already awarded");h.assertTrue(data(worker).ledger.reputation(worker.getUUID(),System.currentTimeMillis())==2,"Daily reputation accounting was not preserved");System.out.println("DAILY_REPUTATION_SHARED_PASSED");}
        }h.succeed();
    }
    @GameTest(template="daily_empty",batch="daily_authority",timeoutTicks=200)
    public static void aSwornPresidentLosesDailyFiscalPowerImmediatelyWhenTheMandateEnds(GameTestHelper h){
        if(!DailyCouncil.loaded()){h.succeed();return;}
        try(var f=new DailyActors(h)){var p=f.actor();DailyActors.round(p);String frozen=DailyLedger.JSON.toJson(data(p).ledger.account(p.getUUID()).round);var previous=data(p).ledger.settings;
            try(var council=new DailyCouncilFixture(p.getServer())){long now=System.currentTimeMillis();council.mandate(p.getUUID(),now-10000,now+60000,0);h.assertTrue(!DailyPermissions.governor(p),"Unsworn winner received fiscal powers");council.claim(now-5000);h.assertTrue(DailyPermissions.governor(p),"Sworn active President was refused");JsonObject input=new JsonObject();input.addProperty("revision",data(p).ledger.revision);input.addProperty("taxBps",0);JsonObject bonuses=new JsonObject();for(Category c:Category.values())bonuses.addProperty(c.name(),c==Category.MINING?1000:0);input.add("bonuses",bonuses);DailyService.govern(p,input);h.assertTrue(frozen.equals(DailyLedger.JSON.toJson(data(p).ledger.account(p.getUUID()).round)),"Presidential bonuses changed a quest already generated");
                council.expire(now-1);h.assertTrue(!DailyPermissions.governor(p),"Expired President retained fiscal powers");input.addProperty("revision",data(p).ledger.revision);rejected(h,()->DailyService.govern(p,input));System.out.println("DAILY_REALM_COUNCIL_PASSED");
            }finally{data(p).ledger.settings=previous;}
        }h.succeed();
    }
    @GameTest(template="daily_empty",batch="daily_authority",timeoutTicks=200)
    public static void realmAdminProviderIsAuthoritativeAndFailuresNeverGrantAccess(GameTestHelper h){try(var f=new DailyActors(h)){
        var p=f.actor();var other=f.actor();DailyActors.op(other);DailyPermissions.Provider provider=player->player.getUUID().equals(p.getUUID());DailyPermissions.register(provider);try{h.assertTrue(DailyPermissions.administrator(p)&&!DailyPermissions.administrator(other),"Role provider was bypassed by default OP handling");rejected(h,()->DailyPermissions.unregister(player->true));}finally{DailyPermissions.unregister(provider);}
        DailyPermissions.Provider broken=player->{throw new IllegalStateException("Unavailable role service");};DailyPermissions.register(broken);try{h.assertTrue(!DailyPermissions.administrator(other),"Broken role service granted administrative access");}finally{DailyPermissions.unregister(broken);}
    }h.succeed();}
    @GameTest(template="daily_empty",batch="daily_authority",timeoutTicks=200)
    public static void exceptionalProgressRepairRequiresAnAdminReasonAndOriginalQuestLimits(GameTestHelper h){try(var f=new DailyActors(h)){
        var admin=f.actor();var worker=f.actor();var q=DailyActors.quest(worker,Category.MINING);data(worker).ledger.accept(worker.getUUID(),q.id,System.currentTimeMillis());JsonObject input=new JsonObject();input.addProperty("player",worker.getUUID().toString());input.addProperty("quest",q.id.toString());input.addProperty("progress",1);input.addProperty("reason","Recover validated progress after a server incident");rejected(h,()->DailyService.repair(admin,input));DailyActors.op(admin);DailyService.repair(admin,input);h.assertTrue(data(worker).ledger.rewards.get(q.id).paid,"Bounded administrative repair did not settle the original quest");rejected(h,()->DailyService.repair(admin,input));h.assertTrue(data(worker).ledger.audit.stream().anyMatch(a->a.operation().equals("repair_progress")&&a.actor().equals(admin.getUUID())),"Exceptional repair had no attributable audit record");
    }h.succeed();}
}
