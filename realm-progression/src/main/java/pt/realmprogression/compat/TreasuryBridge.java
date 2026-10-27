package pt.realmprogression.compat;

import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Treasury.Currency;

/** Read-only money integration. Only the confirmed, available euro balance can satisfy a goal. */
public final class TreasuryBridge {
    public static long balance(MinecraftServer server){var data=TreasuryData.get(server);data.requireBankAvailable();return data.treasury.balance(Currency.CHRONICLES);}
    public static long confirmed(MinecraftServer server){
        long expected=balance(server);var data=TreasuryData.get(server);data.setDirty();
        server.overworld().getDataStorage().save();IOUtilities.waitUntilIOWorkerComplete();
        Path path=server.getWorldPath(LevelResource.ROOT).resolve("data/crown_ledger.dat");
        try{
            if(!Files.isRegularFile(path)||Files.size(path)>32_000_000)throw new IllegalStateException();
            var saved=TreasuryData.load(NbtIo.readCompressed(path,NbtAccounter.create(32_000_000)).getCompound("data"),server.registryAccess());
            saved.requireBankAvailable();if(saved.treasury.balance(Currency.CHRONICLES)!=expected||balance(server)!=expected)throw new IllegalStateException();
            return expected;
        }catch(Exception failure){throw new IllegalStateException("error.realmprogression.treasury",failure);}
    }
    private TreasuryBridge(){}
}
