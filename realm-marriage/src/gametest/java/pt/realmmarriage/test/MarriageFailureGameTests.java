package pt.realmmarriage.test;
import java.nio.file.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;
import pt.realmmarriage.compat.*;
import pt.realmmarriage.data.MarriageData;
import pt.realmmarriage.domain.MarriageLedger;
import pt.realmmarriage.domain.MarriageLedger.*;
import pt.realmmarriage.service.*;
@GameTestHolder("realmmarriage")
@PrefixGameTestTemplate(false)
public final class MarriageFailureGameTests {
    private static MarriageActors group(GameTestHelper h){MarriageActors a=new MarriageActors(h);a.actor(50000);a.actor(30000);a.actor(10000);a.place(a.actors.getFirst());return a;}
    @GameTest(template="marriage_empty",templateNamespace="realmmarriage",batch="marriage-wallet-failure",timeoutTicks=200)
    public static void intentWriteFailureCannotDebitTheWallet(GameTestHelper h)throws Exception{if(!WalletBridge.loaded()){h.succeed();return;}try(var a=group(h)){var u=a.married(Mode.SHARED,0);var p=a.actors.getFirst();MinecraftServer server=p.getServer();Path path=server.getWorldPath(LevelResource.ROOT).resolve("data/realm_marriage.dat"),backup=Files.createTempFile(path.getParent(),"marriage-qa-backup-",".dat");Files.delete(backup);Files.move(path,backup);Files.createDirectory(path);
        try{boolean rejected=false;try{MarriageService.money(p,u,"deposit",5000);}catch(IllegalStateException expected){rejected=true;}h.assertTrue(rejected&&WalletBridge.balance(p)==50000&&u.balance==0,"An undurable deposit changed money");}
        finally{Files.delete(path);Files.move(backup,path);MarriageData restored=MarriageData.load(NbtIo.readCompressed(path,NbtAccounter.create(64000000)).getCompound("data"),server.registryAccess());server.overworld().getDataStorage().set(MarriageData.KEY,restored);}
        MarriageService.money(p,MarriageData.get(server).ledger.union(u.id),"deposit",5000);h.assertTrue(WalletBridge.balance(p)==45000,"Valid retry did not debit exactly once");}
        System.out.println("MARRIAGE_INTENT_FAILURE_PASSED");h.succeed();}
    @GameTest(template="marriage_empty",templateNamespace="realmmarriage",batch="marriage-crown-failure",timeoutTicks=200)
    public static void treasuryInterruptionPreservesBothWalletReceipts(GameTestHelper h)throws Exception{if(!WalletBridge.loaded()||!CrownBridge.loaded()){h.succeed();return;}try(var a=group(h)){var u=a.committing(Mode.JOINT,5000);var p=a.actors.getFirst();MinecraftServer server=p.getServer();long before=MarriageActors.bank(server);Class<?> crown=Class.forName("pt.crownledger.data.TreasuryData");Object data=crown.getMethod("get",MinecraftServer.class).invoke(null,server);Object treasury=crown.getField("treasury").get(data);String key=(String)crown.getField("STORAGE_KEY").get(null);Path path=server.getWorldPath(LevelResource.ROOT).resolve("data/"+key+".dat");
        // Ensure the original actual ledger is on disk before making its path unwritable as a file.
        if(!Files.exists(path)){net.minecraft.nbt.CompoundTag root=new CompoundTag();root.put("data",(CompoundTag)crown.getMethod("save",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class).invoke(data,new CompoundTag(),server.registryAccess()));NbtUtils.addCurrentDataVersion(root);NbtIo.writeCompressed(root,path);}
        Path backup=Files.createTempFile(path.getParent(),"marriage-crown-backup-",".dat");Files.delete(backup);Files.move(path,backup);Files.createDirectory(path);
        try{MarriageService.settle(server,u);h.assertTrue(u.phase==Phase.COMMITTING&&u.fundedA&&u.fundedB&&u.feeEscrow==5000&&u.balance==75000,"Interrupted Crown fee did not stay reserved");h.assertTrue(WalletBridge.balance(p)==0&&WalletBridge.balance(a.actors.get(1))==0,"Wallet funding not durable before treasury call");}
        finally{Files.delete(path);Files.move(backup,path);}
        WalletBridge.install(p,WalletBridge.current(p),WalletBridge.changed(p,17));MarriageService.settle(server,u);h.assertTrue(u.phase==Phase.MARRIED&&WalletBridge.balance(p)==17&&u.balance==75000&&MarriageActors.bank(server)==before+5000,"Retry repeated a personal debit or public credit");CrownBridge.transfer(server,MarriageLedger.key(u.id+":wedding-fee"),u.a,5000);h.assertTrue(MarriageActors.bank(server)==before+5000,"Crown receipt replay created money");}
        System.out.println("MARRIAGE_CROWN_INTERRUPTION_PASSED");h.succeed();}
    @GameTest(template="marriage_empty",templateNamespace="realmmarriage",batch="marriage-receipt-recovery",timeoutTicks=200)
    public static void durablePlayerProofDoesNotOverwriteLaterLegitimateIncome(GameTestHelper h)throws Exception{if(!WalletBridge.loaded()){h.succeed();return;}try(var a=group(h)){var u=a.married(Mode.SHARED,0);var p=a.actors.getFirst();MarriageService.money(p,u,"deposit",5000);MarriageData d=MarriageData.get(p.getServer());UUID id=UUID.randomUUID();CompoundTag intent=WalletJournal.intent(p,id,1000,null);d.ledger.op(id,p.getUUID(),u,Kind.WITHDRAW,1000,0,"",System.currentTimeMillis());d.ledger.withdrawReserve(u,p.getUUID(),1000);d.journals.put(p.getUUID(),intent);d.persist(p.getServer());WalletBridge.install(p,intent.getCompound("profileBefore"),intent.getCompound("profileAfter"));CompoundTag proof=new CompoundTag();proof.putUUID("id",id);proof.putUUID("player",p.getUUID());p.getPersistentData().put(WalletJournal.RECEIPT,proof);var save=net.minecraft.server.players.PlayerList.class.getDeclaredMethod("save",net.minecraft.server.level.ServerPlayer.class);save.setAccessible(true);save.invoke(p.getServer().getPlayerList(),p);h.assertTrue(WalletJournal.savedReceipt(p,proof,false),"Player proof was not really saved");Path file=p.getServer().getWorldPath(LevelResource.ROOT).resolve("data/realm_marriage.dat");MarriageData restored=MarriageData.load(NbtIo.readCompressed(file,NbtAccounter.create(64000000)).getCompound("data"),p.registryAccess());p.getServer().overworld().getDataStorage().set(MarriageData.KEY,restored);WalletBridge.install(p,WalletBridge.current(p),WalletBridge.changed(p,17));WalletJournal.recover(p);WalletJournal.recover(p);h.assertTrue(WalletBridge.balance(p)==46017&&restored.ledger.union(u.id).balance==4000,"Recovery overwrote or replayed a legitimate profile change");}
        System.out.println("MARRIAGE_DURABLE_PLAYER_PROOF_PASSED");h.succeed();}
    @GameTest(template="marriage_empty",templateNamespace="realmmarriage",batch="marriage-menu-rate",timeoutTicks=100)
    public static void backgroundRefreshCannotSwallowConsent(GameTestHelper h)throws Exception{try(var a=group(h)){var p=a.actors.getFirst();MarriageService.clear();var d=MarriageData.get(p.getServer());MarriageService.request(p,"refresh","{}");MarriageService.request(p,"privacy","{\"revision\":"+d.ledger.revision+",\"request\":41,\"public\":false}");h.assertTrue(!d.ledger.citizen(p.getUUID()).publicSpouse,"Background refresh swallowed the next explicit choice");boolean correlated=p.packets.stream().filter(packet->packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket).map(packet->((net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket)packet).payload()).filter(payload->payload instanceof pt.realmmarriage.network.MarriagePackets.View).map(payload->(pt.realmmarriage.network.MarriagePackets.View)payload).anyMatch(view->view.kind().equals("snapshot")&&com.google.gson.JsonParser.parseString(view.json()).getAsJsonObject().get("reply").getAsLong()==41);h.assertTrue(correlated,"Explicit action response lost its correlation ID");}System.out.println("MARRIAGE_REFRESH_CONSENT_PASSED");h.succeed();}

}
