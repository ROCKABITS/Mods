package pt.crownledger.api;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.IncomeLedger;
import pt.crownledger.domain.IncomeLedger.Receipt;
import pt.crownledger.domain.Treasury;

/** Server-only transfer of already-funded escrow, with durable single-use receipts. */
public final class CrownIncomeApi {
    public static final int VERSION=1;
    private CrownIncomeApi(){}
    private static Receipt receipt(UUID id,String currency,long amount,UUID payer,String memo){return new Receipt(id,Treasury.Currency.valueOf(currency),amount,payer,memo);}
    private static void thread(MinecraftServer server){if(server==null||!server.isSameThread())throw new IllegalStateException("Crown income requires the server thread");}

    /** Validate the complete destination without taking ownership of a caller's escrow. */
    public static void preflight(MinecraftServer server,UUID id,String currency,long amount,UUID payer,String memo){
        thread(server);Receipt receipt=receipt(id,currency,amount,payer,memo);TreasuryData data=TreasuryData.get(server);
        if(data.income.contains(receipt))return;data.income.add(receipt);candidate(data,receipt);
    }

    /** True for a newly committed income; false for an identical, previously committed receipt. */
    public static boolean credit(MinecraftServer server,UUID id,String currency,long amount,UUID payer,String memo){
        thread(server);Receipt receipt=receipt(id,currency,amount,payer,memo);TreasuryData data=TreasuryData.get(server);
        if(data.income.contains(receipt)){verifyDurable(server,receipt);return false;}
        IncomeLedger nextIncome=data.income.add(receipt);Treasury next=candidate(data,receipt);
        TreasuryData staged=new TreasuryData();staged.treasury=next;staged.income=nextIncome;
        CompoundTag root=new CompoundTag();root.put("data",staged.save(new CompoundTag(),server.registryAccess()));NbtUtils.addCurrentDataVersion(root);
        Path file=server.getWorldPath(LevelResource.ROOT).resolve("data/"+TreasuryData.STORAGE_KEY+".dat");
        AtomicBoolean moved=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();
        // SavedData uses the same ordered IO chain. An older queued save must finish first.
        // Catch failures inside our task so a failed transfer cannot poison the shared save chain.
        IOUtilities.withIOWorker(()->{try{write(file,root,moved);}catch(Throwable error){failure.set(error);}});
        IOUtilities.waitUntilIOWorkerComplete();
        if(moved.get()){
            // Rename already made the new state visible. Preserve it in memory even if directory
            // synchronization reports a later error; retrying must not issue another credit.
            data.treasury=next;data.income=nextIncome;data.setDirty(false);
        }
        if(failure.get()!=null||!moved.get())throw new IllegalStateException("Cannot commit funded Crown income",failure.get());
        return true;
    }
    /** Replaying a receipt must also finish any previously failed file/directory synchronization. */
    private static void verifyDurable(MinecraftServer server,Receipt receipt){
        IOUtilities.waitUntilIOWorkerComplete();Path file=server.getWorldPath(LevelResource.ROOT).resolve("data/"+TreasuryData.STORAGE_KEY+".dat");
        try{if(!Files.isRegularFile(file)||Files.size(file)>32_000_000)throw new IOException("Missing or oversized income receipt file");
            TreasuryData saved=TreasuryData.load(NbtIo.readCompressed(file,NbtAccounter.create(32_000_000)).getCompound("data"),server.registryAccess());
            if(!saved.income.contains(receipt))throw new IOException("Income receipt has not reached persistent storage");
            try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}
            if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))try(FileChannel directory=FileChannel.open(file.getParent(),StandardOpenOption.READ)){directory.force(true);}
        }catch(IOException|RuntimeException error){throw new IllegalStateException("Cannot confirm durable Crown income receipt",error);}
    }
    private static Treasury candidate(TreasuryData data,Receipt receipt){
        Treasury next=data.treasury.copy();long at=Math.max(0,System.currentTimeMillis());
        if(IncomeLedger.validEconomicPolicy(receipt.memo())){String[] p=receipt.memo().split(":");next.recordEconomyPolicy(receipt.payer(),p[1]+":"+p[3]+">"+p[4],Long.parseLong(p[2]));}
        else if(IncomeLedger.validPolicy(receipt.memo())&&receipt.amount()==0){String[] pieces=receipt.memo().split(":");next.recordVillagerPolicy(receipt.payer(),Integer.parseInt(pieces[1]),Long.parseLong(pieces[2]));}
        else {Treasury.Kind kind=receipt.memo().equals("contracts.daily-tax")?Treasury.Kind.TAX_COLLECTED:receipt.memo().startsWith("realmeconomy.service-tax:")?Treasury.Kind.ECONOMY_TAX:receipt.memo().equals("realmcasino.profit-tax")?Treasury.Kind.CASINO_TAX:receipt.memo().equals("realmvillagereconomy.purchase-tax")?Treasury.Kind.VILLAGER_TAX:Treasury.Kind.JUSTICE_INCOME;next.addFunds(receipt.currency(),receipt.amount(),kind,receipt.payer(),receipt.memo(),at);next.settlePending(at);}return next;
    }
    private static void write(Path file,CompoundTag root,AtomicBoolean moved)throws IOException {
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"crown-income-",".tmp");
        try{
            NbtIo.writeCompressed(root,temporary);try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);moved.set(true);
            if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))try(FileChannel directory=FileChannel.open(file.getParent(),StandardOpenOption.READ)){directory.force(true);}
        }finally{Files.deleteIfExists(temporary);}
    }
}
