package pt.crownledger.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.crownledger.api.CrownIncomeApi;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.IncomeLedger;
import pt.crownledger.domain.Treasury;

/** Actual compressed treasury files and ordered NeoForge IO, with no client income endpoint. */
@GameTestHolder("kingdomtreasury")
@PrefixGameTestTemplate(false)
public final class IncomeGameTests {
    private IncomeGameTests(){}
    private static final UUID PAYER=new UUID(0,271);
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final TreasuryData data;final Treasury previous;final IncomeLedger receipts;final boolean dirty;final Path file;final byte[] bytes;
        Fixture(GameTestHelper h){this.h=h;IOUtilities.waitUntilIOWorkerComplete();data=TreasuryData.get(h.getLevel().getServer());previous=data.treasury;receipts=data.income;dirty=data.isDirty();file=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/crown_ledger.dat");try{Files.createDirectories(file.getParent());bytes=Files.isRegularFile(file)?Files.readAllBytes(file):null;}catch(java.io.IOException failure){throw new IllegalStateException(failure);}data.treasury=new Treasury();data.income=new IncomeLedger();}
        TreasuryData reopen(){try{return TreasuryData.load(NbtIo.readCompressed(file,NbtAccounter.create(32_000_000)).getCompound("data"),h.getLevel().registryAccess());}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
        @Override public void close(){IOUtilities.waitUntilIOWorkerComplete();data.treasury=previous;data.income=receipts;data.setDirty(dirty);try{if(Files.isDirectory(file))Files.delete(file);if(bytes==null)Files.deleteIfExists(file);else Files.write(file,bytes);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
    }
    private static void rejected(GameTestHelper h,Runnable op){boolean rejected=false;try{op.run();}catch(RuntimeException expected){rejected=true;}h.assertTrue(rejected,"An invalid or failed Crown income operation was accepted");}

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void actualIncomeFileRestoresTheReceiptAndAReplayCannotCreditItAgain(GameTestHelper h){
        try(var f=new Fixture(h)){
            UUID id=UUID.randomUUID();boolean applied=CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",5,PAYER,"Native justice fine");h.assertTrue(applied&&f.data.treasury.balance(Treasury.Currency.EMERALD)==5,"Funded income did not enter the live treasury");
            var saved=f.reopen();h.assertTrue(saved.treasury.balance(Treasury.Currency.EMERALD)==5&&saved.income.receipts().containsKey(id),"Income and receipt were not committed in the same world file");f.data.treasury=saved.treasury;f.data.income=saved.income;
            h.assertTrue(!CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",5,PAYER,"Native justice fine")&&f.data.treasury.balance(Treasury.Currency.EMERALD)==5,"Replaying a restored receipt credited public funds twice");
            rejected(h,()->CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",6,PAYER,"Native justice fine"));h.assertTrue(f.data.treasury.balance(Treasury.Currency.EMERALD)==5,"A changed receipt altered the treasury");System.out.println("CROWN_INCOME_API_PASSED");
        }h.succeed();
    }

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void aRealIncomeWriteFailurePreservesMoneyAndDoesNotPoisonTheSharedSaveWorker(GameTestHelper h){
        try(var f=new Fixture(h)){
            try{Files.deleteIfExists(f.file);Files.createDirectory(f.file);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
            UUID id=UUID.randomUUID();rejected(h,()->CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",5,PAYER,"Native failed fine"));h.assertTrue(f.data.treasury.balance(Treasury.Currency.EMERALD)==0&&f.data.income.receipts().isEmpty(),"A failed actual file replacement committed half an income");
            var ran=new java.util.concurrent.atomic.AtomicBoolean();IOUtilities.withIOWorker(()->ran.set(true));IOUtilities.waitUntilIOWorkerComplete();h.assertTrue(ran.get(),"Failed income poisoned the shared asynchronous save chain");
            try{Files.delete(f.file);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}h.assertTrue(CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",5,PAYER,"Native failed fine"),"Income could not be retried after storage recovered");h.assertTrue(f.reopen().treasury.balance(Treasury.Currency.EMERALD)==5,"The retried actual income did not persist");
        }h.succeed();
    }

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void anOlderQueuedSavedDataWriteCannotOverwriteTheNewIncomeReceipt(GameTestHelper h){
        try(var f=new Fixture(h)){
            CompoundTag old=new CompoundTag();old.put("data",f.data.save(new CompoundTag(),h.getLevel().registryAccess()));NbtUtils.addCurrentDataVersion(old);
            IOUtilities.withIOWorker(()->{try{IOUtilities.writeNbtCompressed(old,f.file);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}});
            UUID id=UUID.randomUUID();CrownIncomeApi.credit(h.getLevel().getServer(),id,"CHRONICLES",200,PAYER,"Native paid bail");IOUtilities.waitUntilIOWorkerComplete();var restored=f.reopen();h.assertTrue(restored.treasury.balance(Treasury.Currency.CHRONICLES)==200&&restored.income.receipts().containsKey(id),"An older queued save erased the committed justice income");
        }h.succeed();
    }

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void replayCannotAcknowledgeAReceiptMissingFromTheActualWorldFile(GameTestHelper h){
        try(var f=new Fixture(h)){UUID id=UUID.randomUUID();CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",5,PAYER,"Native verified replay");
            try{byte[] saved=Files.readAllBytes(f.file);Files.delete(f.file);rejected(h,()->CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",5,PAYER,"Native verified replay"));h.assertTrue(f.data.treasury.balance(Treasury.Currency.EMERALD)==5,"Missing receipt caused another live credit");Files.write(f.file,saved);h.assertTrue(!CrownIncomeApi.credit(h.getLevel().getServer(),id,"EMERALD",5,PAYER,"Native verified replay"),"Restored durable receipt could not be acknowledged idempotently");}catch(java.io.IOException e){throw new IllegalStateException(e);}
        }h.succeed();
    }

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void malformedReceiptStorageIsRejectedAndLegacyTreasuriesMigrateWithoutInventingIncome(GameTestHelper h){
        try(var f=new Fixture(h)){
            var receipt=new IncomeLedger.Receipt(UUID.randomUUID(),Treasury.Currency.EMERALD,5,PAYER,"Native receipt");f.data.income=f.data.income.add(receipt);var saved=f.data.save(new CompoundTag(),h.getLevel().registryAccess());var duplicate=saved.copy();duplicate.getList("incomeReceipts",10).add(duplicate.getList("incomeReceipts",10).getCompound(0).copy());rejected(h,()->TreasuryData.load(duplicate,h.getLevel().registryAccess()));
            var negative=saved.copy();negative.getList("incomeReceipts",10).getCompound(0).putLong("amount",-1);rejected(h,()->TreasuryData.load(negative,h.getLevel().registryAccess()));
            var downgraded=saved.copy();downgraded.putInt("version",2);rejected(h,()->TreasuryData.load(downgraded,h.getLevel().registryAccess()));
            var legacy=saved.copy();legacy.remove("incomeReceipts");legacy.putInt("version",2);var migrated=TreasuryData.load(legacy,h.getLevel().registryAccess());h.assertTrue(migrated.income.receipts().isEmpty()&&migrated.treasury.snapshot().equals(f.data.treasury.snapshot()),"Legacy migration reset public funds or invented a receipt");h.assertTrue(migrated.save(new CompoundTag(),h.getLevel().registryAccess()).getInt("version")==9,"Legacy treasury did not migrate to receipt-aware storage");
        }h.succeed();
    }

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void economicServiceReceiptsPersistTheirCategoryAndCannotPayTwice(GameTestHelper h){
        try(var f=new Fixture(h)){var id=UUID.randomUUID();var server=h.getLevel().getServer();pt.crownledger.api.CrownEconomyApi.preflight(server,id,PAYER,123,"ENCHANT");h.assertTrue(pt.crownledger.api.CrownEconomyApi.credit(server,id,PAYER,123,"ENCHANT"),"Economic service tax did not commit");var saved=f.reopen();h.assertTrue(saved.treasury.balance(Treasury.Currency.CHRONICLES)==123&&saved.income.receipts().get(id).memo().equals("realmeconomy.service-tax:ENCHANT"),"Economic service receipt lost its frozen category");h.assertTrue(!pt.crownledger.api.CrownEconomyApi.credit(server,id,PAYER,123,"ENCHANT")&&f.data.treasury.balance(Treasury.Currency.CHRONICLES)==123,"Economic tax replay duplicated public money");rejected(h,()->pt.crownledger.api.CrownEconomyApi.credit(server,id,PAYER,123,"SMITHING"));}h.succeed();
    }
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void economicApprovalsPersistBothPoliciesWithoutCreatingFunds(GameTestHelper h){
        try(var f=new Fixture(h)){var id=UUID.randomUUID();String old="100,0,10000,0,2500,1",next="200,100,1000,1000,2000,0";pt.crownledger.api.CrownEconomyApi.policy(h.getLevel().getServer(),id,PAYER,"ANVIL_REPAIR",old,next,1234);var saved=f.reopen();h.assertTrue(saved.treasury.balance(Treasury.Currency.CHRONICLES)==0&&saved.income.receipts().get(id).memo().equals("realmeconomy.policy:ANVIL_REPAIR:1234:"+old+":"+next),"Approval invented money or dropped policy bounds");var data=saved.save(new CompoundTag(),h.getLevel().registryAccess());data.putInt("version",5);rejected(h,()->TreasuryData.load(data,h.getLevel().registryAccess()));}System.out.println("CROWN_ECONOMY_API_PASSED");h.succeed();
    }
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void formatFiveVillagerAuditsRemainValidAndMigrateToFormatSix(GameTestHelper h){
        try(var f=new Fixture(h)){UUID id=UUID.randomUUID();pt.crownledger.api.CrownVillagerApi.policy(h.getLevel().getServer(),id,PAYER,1000,1234);var data=f.data.save(new CompoundTag(),h.getLevel().registryAccess());data.putInt("version",5);var migrated=TreasuryData.load(data,h.getLevel().registryAccess());h.assertTrue(migrated.income.receipts().containsKey(id)&&migrated.treasury.balance(Treasury.Currency.CHRONICLES)==0&&migrated.save(new CompoundTag(),h.getLevel().registryAccess()).getInt("version")==9,"Legacy zero-value Villager audit could not migrate");}h.succeed();
    }
}
