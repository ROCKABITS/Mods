package pt.realmcasino.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmcasino.block.BlockRegistry;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.compat.CrownBridge;
import pt.realmcasino.service.CasinoService;

/** Actual jointly built Crown API, wallet funding, durable receipt retry and profit-only taxation. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoTaxGameTests {
    private static final class Crown implements AutoCloseable {
        final GameTestHelper h;final Class<?> type;final Object data,treasury,income;final boolean dirty;final Path path;final byte[] bytes;
        Crown(GameTestHelper h)throws Exception{
            this.h=h;IOUtilities.waitUntilIOWorkerComplete();type=Class.forName("pt.crownledger.data.TreasuryData");data=type.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,h.getLevel().getServer());treasury=type.getField("treasury").get(data);income=type.getField("income").get(data);dirty=((SavedData)data).isDirty();path=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data/crown_ledger.dat");Files.createDirectories(path.getParent());bytes=Files.isRegularFile(path)?Files.readAllBytes(path):null;
            Object fresh=Class.forName("pt.crownledger.domain.Treasury").getConstructor().newInstance();type.getField("treasury").set(data,fresh);type.getField("income").set(data,Class.forName("pt.crownledger.domain.IncomeLedger").getConstructor().newInstance());
            Object old=fresh.getClass().getMethod("policy").invoke(fresh);var components=old.getClass().getRecordComponents();Object[] values=new Object[components.length];Class<?>[] types=new Class<?>[components.length];for(int i=0;i<components.length;i++){types[i]=components[i].getType();values[i]=components[i].getName().equals("casinoTaxBasisPoints")?500:components[i].getAccessor().invoke(old);}Object policy=old.getClass().getConstructor(types).newInstance(values);fresh.getClass().getMethod("setPolicy",old.getClass(),UUID.class,long.class).invoke(fresh,policy,UUID.randomUUID(),System.currentTimeMillis());
        }
        long balance()throws Exception{Object t=type.getField("treasury").get(data);Class<?> currency=Class.forName("pt.crownledger.domain.Treasury$Currency");Object value=java.util.Arrays.stream(currency.getEnumConstants()).filter(v->v.toString().equals("CHRONICLES")).findFirst().orElseThrow();return (Long)t.getClass().getMethod("balance",currency).invoke(t,value);}
        Map<?,?> receipts()throws Exception{Object i=type.getField("income").get(data);return (Map<?,?>)i.getClass().getMethod("receipts").invoke(i);}
        void reload()throws Exception{Object restored=type.getMethod("load",CompoundTag.class,HolderLookup.Provider.class).invoke(null,NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()).getCompound("data"),h.getLevel().registryAccess());type.getField("treasury").set(data,type.getField("treasury").get(restored));type.getField("income").set(data,type.getField("income").get(restored));}
        @Override public void close(){try{IOUtilities.waitUntilIOWorkerComplete();type.getField("treasury").set(data,treasury);type.getField("income").set(data,income);((SavedData)data).setDirty(dirty);if(Files.isDirectory(path))Files.delete(path);if(bytes==null)Files.deleteIfExists(path);else Files.write(path,bytes);}catch(Exception error){throw new IllegalStateException(error);}}
    }
    private static void profit(CasinoFixture f){f.fund(10000);f.open(f.player);try(var rng=new CasinoFixture.Entropy(CasinoFixture.draws(0))){f.amount(f.player,"bet",100);}f.open(f.owner);}
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void actualTreasuryReceivesOnlyFivePercentOfTheOwnersRealizedProfit(GameTestHelper h)throws Exception{
        if(!ChroniclesBridge.loaded()||!CrownBridge.available()){h.succeed();return;}
        try(var crown=new Crown(h);var f=new CasinoFixture(h,BlockRegistry.FORTUNE_WHEEL.get())){
            profit(f);h.assertTrue(f.view(f.owner).get("casinoTaxBasisPoints").getAsInt()==500&&f.machine().capital()==10000,"Actual Crown policy or deposited capital did not reach owner controls");
            f.amount(f.owner,"withdraw",10100);h.assertTrue(f.pending(f.owner)==10095&&crown.balance()==5&&f.data.ledger().taxes().isEmpty(),"Tax was charged on capital or failed to reach the genuine treasury");UUID receipt=(UUID)crown.receipts().keySet().iterator().next();crown.reload();CrownBridge.credit(h.getLevel().getServer(),receipt,f.owner.getUUID(),5);for(int i=0;i<40;i++)CasinoService.tick(h.getLevel().getServer());h.assertTrue(crown.balance()==5&&crown.receipts().size()==1,"Replaying or reopening the durable tax receipt credited twice");
            f.amount(f.owner,"claim",0);h.assertTrue(ChroniclesBridge.balance(f.owner)+ChroniclesBridge.balance(f.player)+crown.balance()==2100000,"Wallet plus bank plus public tax did not conserve the original funds");
        }System.out.println("CASINO_TAX_INTEGRATION_PASSED");h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void aFailedCrownWriteKeepsTheFundedTaxOutboxAndRetriesOnlyOnce(GameTestHelper h)throws Exception{
        if(!ChroniclesBridge.loaded()||!CrownBridge.available()){h.succeed();return;}
        try(var crown=new Crown(h);var f=new CasinoFixture(h,BlockRegistry.FORTUNE_WHEEL.get())){
            profit(f);IOUtilities.waitUntilIOWorkerComplete();Files.deleteIfExists(crown.path);Files.createDirectory(crown.path);f.amount(f.owner,"withdraw",10100);
            h.assertTrue(f.pending(f.owner)==10095&&f.data.ledger().taxes().size()==1&&crown.balance()==0,"An unavailable treasury lost the tax or charged the owner twice");UUID receipt=f.data.ledger().taxes().keySet().iterator().next();Files.delete(crown.path);for(int i=0;i<40;i++)CasinoService.tick(h.getLevel().getServer());
            h.assertTrue(f.data.ledger().taxes().isEmpty()&&crown.balance()==5&&crown.receipts().containsKey(receipt),"The same funded tax did not recover idempotently");
        }h.succeed();
    }
    @GameTest(template="casino_empty",timeoutTicks=100)
    public static void returningAnOwnersDepositHasNoProfitTax(GameTestHelper h)throws Exception{
        if(!ChroniclesBridge.loaded()||!CrownBridge.available()){h.succeed();return;}
        try(var crown=new Crown(h);var f=new CasinoFixture(h,BlockRegistry.FORTUNE_WHEEL.get())){
            f.fund(10000);f.amount(f.owner,"withdraw",10000);h.assertTrue(f.pending(f.owner)==10000&&crown.balance()==0&&f.data.ledger().taxes().isEmpty()&&crown.receipts().isEmpty(),"Returning a deposit was taxed as profit");
        }h.succeed();
    }
}
