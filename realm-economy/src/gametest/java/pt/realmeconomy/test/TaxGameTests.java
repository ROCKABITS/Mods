package pt.realmeconomy.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmeconomy.compat.ChroniclesBridge;
import pt.realmeconomy.compat.CrownBridge;
import pt.realmeconomy.service.TradeJournal;
import pt.realmeconomy.service.TradingService;

@GameTestHolder("realmeconomy") @PrefixGameTestTemplate(false)
public final class TaxGameTests {
    static final class Crown implements AutoCloseable {
        final Object data,treasury,income;final Class<?> type;final Path file;final byte[] bytes;final boolean dirty;
        Crown(GameTestHelper h)throws Exception{
            type=Class.forName("pt.crownledger.data.TreasuryData");data=type.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,h.getLevel().getServer());treasury=type.getField("treasury").get(data);income=type.getField("income").get(data);dirty=((SavedData)data).isDirty();
            IOUtilities.waitUntilIOWorkerComplete();file=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/crown_ledger.dat");bytes=Files.exists(file)?Files.readAllBytes(file):null;
            type.getField("treasury").set(data,treasury.getClass().getConstructor().newInstance());type.getField("income").set(data,income.getClass().getConstructor().newInstance());
        }
        long balance()throws Exception{Object t=type.getField("treasury").get(data);Class<?> currency=Class.forName("pt.crownledger.domain.Treasury$Currency");@SuppressWarnings({"unchecked","rawtypes"})Object c=Enum.valueOf((Class)currency,"CHRONICLES");return (Long)t.getClass().getMethod("balance",currency).invoke(t,c);}
        java.util.Map<?,?> receipts()throws Exception{Object i=type.getField("income").get(data);return (java.util.Map<?,?>)i.getClass().getMethod("receipts").invoke(i);}
        @Override public void close(){try{IOUtilities.waitUntilIOWorkerComplete();type.getField("treasury").set(data,treasury);type.getField("income").set(data,income);((SavedData)data).setDirty(dirty);if(Files.isDirectory(file))Files.delete(file);if(bytes==null)Files.deleteIfExists(file);else Files.write(file,bytes);}catch(Exception failed){throw new IllegalStateException(failed);}}
    }
    static boolean integrated(){return CrownBridge.installed()&&ChroniclesBridge.loaded();}
    @GameTest(template="trade_empty",timeoutTicks=100) public static void aHundredEuroPurchaseChargesOneHundredTenAndFundsTheActualTreasuryOnce(GameTestHelper h)throws Exception{
        if(!integrated()){h.succeed();return;}
        try(var crown=new Crown(h);var f=new TradeFixture(h)){
            f.op(f.player);f.open(f.player);f.policy(f.player,1000);h.assertTrue(crown.balance()==0&&crown.receipts().size()==1,"Policy approval created money or lost its durable audit");f.trade(f.player,0);
            h.assertTrue(ChroniclesBridge.balance(f.player)==89000&&crown.balance()==1000&&f.player.getInventory().countItem(net.minecraft.world.item.Items.BREAD)==3,"100+10 purchase did not burn only the base and transfer public tax");
            UUID id=crown.receipts().entrySet().stream().filter(e->{try{return (Long)e.getValue().getClass().getMethod("amount").invoke(e.getValue())>0;}catch(Exception x){throw new IllegalStateException(x);}}).map(e->(UUID)e.getKey()).findFirst().orElseThrow();
            CrownBridge.credit(f.player.getServer(),id,f.player.getUUID(),1000);h.assertTrue(crown.balance()==1000&&f.data.outbox().isEmpty(),"Receipt replay duplicated purchase tax");
        }System.out.println("VILLAGER_TAX_INTEGRATION_PASSED");h.succeed();
    }
    @GameTest(template="trade_empty",timeoutTicks=100) public static void failedTreasuryWritesPreserveTheAlreadyFundedTaxWithoutRepeatingThePurchase(GameTestHelper h)throws Exception{
        if(!integrated()){h.succeed();return;}
        try(var crown=new Crown(h);var f=new TradeFixture(h)){
            f.op(f.player);f.open(f.player);f.policy(f.player,1000);IOUtilities.waitUntilIOWorkerComplete();Files.deleteIfExists(crown.file);Files.createDirectory(crown.file);f.trade(f.player,0);
            h.assertTrue(ChroniclesBridge.balance(f.player)==89000&&f.data.outbox().size()==1&&crown.balance()==0,"Failed tax write lost escrow or repeated a charge");
            Files.delete(crown.file);TradingService.flush(f.player.getServer());TradingService.flush(f.player.getServer());h.assertTrue(crown.balance()==1000&&f.data.outbox().isEmpty()&&f.merchant.getOffers().getFirst().getUses()==1,"Retry duplicated tax or consumed stock twice");
        }h.succeed();
    }
    @GameTest(template="trade_empty",timeoutTicks=100) public static void aTaxChangeInvalidatesTheOldDisplayedTotalBeforeCharging(GameTestHelper h){
        try(var f=new TradeFixture(h)){f.open(f.player);var p=f.payload(f.player);p.addProperty("offer",0);f.data.policy(f.other.getUUID(),"President QA",1000,false);TradingService.action(f.player,"trade",p.toString());h.assertTrue(f.merchant.getOffers().getFirst().getUses()==0&&f.player.getInventory().countItem(net.minecraft.world.item.Items.BREAD)==0,"Changed fiscal policy accepted a stale total");}h.succeed();
    }
    @GameTest(template="trade_empty",timeoutTicks=100) public static void lostPlayerFileWriteCanRecoverTheSameInventoryAndStockIntentWithoutASecondDebit(GameTestHelper h)throws Exception{
        if(!ChroniclesBridge.loaded()){h.succeed();return;}
        try(var f=new TradeFixture(h)){
            f.open(f.player);Path path=f.player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(f.player.getUUID()+".dat");Files.createDirectories(path.getParent());Files.deleteIfExists(path);Files.createDirectory(path);f.trade(f.player,0);
            h.assertTrue(f.data.journal()!=null&&ChroniclesBridge.balance(f.player)==90000,"Interrupted purchase did not retain its funded intent");Files.deleteIfExists(path);Files.deleteIfExists(path.resolveSibling(f.player.getUUID()+".dat_old"));TradeJournal.recover(f.player);TradingService.restore(f.merchant);
            h.assertTrue(f.data.journal()==null&&ChroniclesBridge.balance(f.player)==90000&&f.player.getInventory().countItem(net.minecraft.world.item.Items.BREAD)==3&&f.merchant.getOffers().getFirst().getUses()==1,"Recovery debited/delivered/consumed the same trade more than once");
        }System.out.println("VILLAGER_RECOVERY_PASSED");h.succeed();
    }
    @GameTest(template="trade_empty",timeoutTicks=100) public static void injectedClientPricesAndBalancesAreRejectedBeforeAnyMutation(GameTestHelper h){try(var f=new TradeFixture(h)){f.open(f.player);var p=f.payload(f.player);p.addProperty("offer",0);p.addProperty("total",1);p.addProperty("balance",999999999);TradingService.action(f.player,"trade",p.toString());h.assertTrue(f.merchant.getOffers().getFirst().getUses()==0,"Client-authoritative price was accepted");}h.succeed();}
}
