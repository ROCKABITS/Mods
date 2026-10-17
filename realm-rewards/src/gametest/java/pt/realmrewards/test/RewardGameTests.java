package pt.realmrewards.test;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmrewards.RealmRewardsMod;
import pt.realmrewards.compat.ChroniclesBridge;
import pt.realmrewards.data.RewardData;
import pt.realmrewards.domain.RewardLedger;
import pt.realmrewards.service.RewardService;

@GameTestHolder("realmrewards")
@PrefixGameTestTemplate(false)
public final class RewardGameTests {
    /** NeoForge's normal fake advancement tracker deliberately rejects award(); use the real tracker. */
    private static final class Actor extends FakePlayer {
        private final PlayerAdvancements actual;
        Actor(GameTestHelper helper) {
            super(helper.getLevel(),new GameProfile(UUID.randomUUID(),"RewardsQA"));
            var server=helper.getLevel().getServer();
            Path path=server.getWorldPath(LevelResource.ROOT).resolve("rewards-qa/"+getUUID()+".json");
            actual=new PlayerAdvancements(server.getFixerUpper(),server.getPlayerList(),server.getAdvancements(),path,this);
        }
        @Override public PlayerAdvancements getAdvancements() { return actual==null?super.getAdvancements():actual; }
    }
    private static Actor actor(GameTestHelper helper) { return new Actor(helper); }
    private static AdvancementHolder holder(GameTestHelper helper,String path) {
        var value=helper.getLevel().getServer().getAdvancements().get(ResourceLocation.parse(path.contains(":")?path:"rewards_fixture:"+path));
        helper.assertTrue(value!=null,"Real advancement fixture missing: "+path); return value;
    }
    private static void grant(Actor actor,AdvancementHolder holder) {
        for (String criterion:holder.value().criteria().keySet()) actor.getAdvancements().award(holder,criterion);
    }
    private static void revoke(Actor actor,AdvancementHolder holder) {
        for (String criterion:holder.value().criteria().keySet()) actor.getAdvancements().revoke(holder,criterion);
    }
    private static RewardLedger ledger(GameTestHelper helper) { return RewardData.get(helper.getLevel().getServer()).ledger; }
    private static long pending(GameTestHelper helper,Actor player) { return ledger(helper).account(player.getUUID()).pending(); }
    private static void rejected(GameTestHelper helper,Runnable action) {
        boolean rejected=false; try { action.run(); } catch (RuntimeException expected) { rejected=true; }
        helper.assertTrue(rejected,"Malformed or unauthorized action was accepted");
    }
    @SuppressWarnings("unchecked") private static AttachmentType<Object> attachment() throws Exception {
        return (AttachmentType<Object>)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();
    }
    private static void profile(Actor player,long balance) {
        try {
            Class<?> type=Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object value=type.getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class)
                    .newInstance(true,"Aetherion",28,"origins:human","Private biography","Private signature","Comum",10_000L,balance,"");
            player.setData(attachment(),value);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static Object profile(Actor player) {
        try { return player.getData(attachment()); } catch(Exception exception) { throw new IllegalStateException(exception); }
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void actualRuntimeDependenciesMatchTheRequestedMode(GameTestHelper h) {
        boolean integration=Boolean.getBoolean("rewards.integration");
        h.assertTrue(ModList.get().isLoaded("cronicasrp")==integration,"Chronicles runtime mode differs from CI configuration");
        if (integration) {
            for (String id:List.of("origins","jupiter","contracts","realmcouncil","kingdomtreasury","realmidentity","realmmarkets"))
                h.assertTrue(ModList.get().isLoaded(id),"Actual companion missing: "+id);
        }
        RealmRewardsMod.LOGGER.info("REWARDS_RUNTIME_MODE_PASSED integration={}",integration);
        h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void realVanillaAwardPaysOnlyOnceEvenAfterRevokeAndRegrant(GameTestHelper h) {
        Actor p=actor(h); var advancement=holder(h,"minecraft:story/root"); grant(p,advancement);
        h.assertTrue(p.getAdvancements().getOrStartProgress(advancement).isDone(),"Vanilla tracker did not complete");
        long amount=pending(h,p); h.assertTrue(amount==ledger(h).amount(advancement.id().toString(),RewardService.kind(advancement)),"Vanilla reward mismatch");
        revoke(p,advancement); grant(p,advancement);
        h.assertTrue(pending(h,p)==amount,"Revoke/regrant duplicated a Vanilla reward"); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void normalModAdvancementsAreDetectedWithoutExplicitRegistration(GameTestHelper h) {
        Actor p=actor(h); grant(p,holder(h,"task")); grant(p,holder(h,"goal")); grant(p,holder(h,"challenge"));
        h.assertTrue(pending(h,p)==1700,"Mod task/goal/challenge defaults were not automatically discovered");
        h.assertTrue(ledger(h).account(p.getUUID()).completed().size()==3,"Missing completion identities");
        RealmRewardsMod.LOGGER.info("REWARDS_NATIVE_ADVANCEMENTS_PASSED"); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void backgroundAdvancementsDefaultToZeroAndRemainSeen(GameTestHelper h) {
        Actor p=actor(h); var advancement=holder(h,"background"); grant(p,advancement);
        h.assertTrue(pending(h,p)==0 && ledger(h).account(p.getUUID()).completed().contains(advancement.id().toString()),"Background advancement was paid or not recorded"); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void incompleteOrForgedEarnEventCannotCreateMoney(GameTestHelper h) {
        Actor p=actor(h); RewardService.earned(new AdvancementEvent.AdvancementEarnEvent(p,holder(h,"task")));
        h.assertTrue(pending(h,p)==0 && ledger(h).account(p.getUUID()).completed().isEmpty(),"Incomplete advancement event minted money"); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void existingCompletionsAreBaselinedWithoutRetroactivePayout(GameTestHelper h) {
        Actor p=actor(h); var advancement=holder(h,"task");
        for (String criterion:advancement.value().criteria().keySet()) p.getAdvancements().getOrStartProgress(advancement).grantProgress(criterion);
        RewardService.login(new PlayerEvent.PlayerLoggedInEvent(p));
        revoke(p,advancement); grant(p,advancement);
        h.assertTrue(pending(h,p)==0,"Old completion was paid after its first Rewards login"); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void adminWritesAndSnapshotsRequireCurrentOperatorPermission(GameTestHelper h) {
        Actor p=actor(h); JsonObject input=new JsonObject(); input.addProperty("id","rewards_fixture:task"); input.addProperty("value","2,75");
        rejected(h,()->RewardService.configure(p,"save",input)); rejected(h,()->RewardService.snapshot(p,new JsonObject(),""));
        var players=h.getLevel().getServer().getPlayerList(); players.op(p.getGameProfile());
        try {
            RewardService.configure(p,"save",input);
            h.assertTrue(ledger(h).amount("rewards_fixture:task",RewardLedger.Kind.TASK)==275,"Admin amount did not use integer cents");
            RewardService.configure(p,"reset",input);
        } finally { players.deop(p.getGameProfile()); }
        rejected(h,()->RewardService.configure(p,"save",input)); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void adminSearchFindsAdvancementsFromOtherNamespaces(GameTestHelper h) {
        Actor p=actor(h); var players=h.getLevel().getServer().getPlayerList(); players.op(p.getGameProfile());
        try {
            JsonObject input=new JsonObject(); input.addProperty("search","rewards_fixture:"); input.addProperty("size",12); input.addProperty("page",0);
            var snapshot=RewardService.snapshot(p,input,"");
            h.assertTrue(snapshot.get("total").getAsInt()==4,"Loaded mod advancements were absent from admin search");
            input.addProperty("size",true); rejected(h,()->RewardService.snapshot(p,input,""));
        } finally { players.deop(p.getGameProfile()); }
        h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void nativeNbtRoundTripPreservesCompletedIdsAndPendingAmounts(GameTestHelper h) {
        Actor p=actor(h); grant(p,holder(h,"challenge"));
        var before=RewardData.get(h.getLevel().getServer()); CompoundTag tag=before.save(new CompoundTag(),h.getLevel().registryAccess());
        var after=RewardData.load(tag,h.getLevel().registryAccess());
        h.assertTrue(after.ledger.account(p.getUUID()).equals(before.ledger.account(p.getUUID())),"Saved reward claims changed across NBT reload"); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void malformedNbtNeverSilentlyResetsPaymentHistory(GameTestHelper h) {
        CompoundTag tag=new RewardData().save(new CompoundTag(),h.getLevel().registryAccess());
        tag.putInt("version",2); rejected(h,()->RewardData.load(tag,h.getLevel().registryAccess()));
        tag.putInt("version",1); tag.remove("players"); rejected(h,()->RewardData.load(tag,h.getLevel().registryAccess())); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void realChroniclesWalletCreditPreservesEveryOtherProfileField(GameTestHelper h) {
        Actor p=actor(h);
        if (!ChroniclesBridge.loaded()) { grant(p,holder(h,"task")); h.assertTrue(pending(h,p)==200,"Absent wallet lost pending reward"); h.succeed(); return; }
        profile(p,10_000); Object before=profile(p); grant(p,holder(h,"task")); Object after=profile(p);
        h.assertTrue(ChroniclesBridge.balance(p)==10_200 && pending(h,p)==0,"Actual Chronicles wallet did not receive the reward");
        try {
            for (var field:before.getClass().getRecordComponents()) if (!field.getName().equals("balance"))
                h.assertTrue(Objects.equals(field.getAccessor().invoke(before),field.getAccessor().invoke(after)),"Reward changed private profile field "+field.getName());
        } catch(Exception ex) { throw new IllegalStateException(ex); }
        revoke(p,holder(h,"task")); grant(p,holder(h,"task")); RewardService.payout(p);
        h.assertTrue(ChroniclesBridge.balance(p)==10_200,"Repeated achievement or payout minted money");
        RealmRewardsMod.LOGGER.info("REWARDS_CHRONICLES_PAYMENTS_PASSED"); h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void uncreatedProfileQueuesThenPaysExactlyOnceAfterCreation(GameTestHelper h) {
        Actor p=actor(h); grant(p,holder(h,"goal")); h.assertTrue(pending(h,p)==500,"Missing profile did not retain claim");
        if (ChroniclesBridge.loaded()) {
            profile(p,1000); h.assertTrue(RewardService.payout(p)==500 && ChroniclesBridge.balance(p)==1500,"Pending reward failed after character creation");
            h.assertTrue(RewardService.payout(p)==0,"Claim paid twice");
        } h.succeed();
    }
    @GameTest(template="rewards_empty",timeoutTicks=100)
    public static void fullWalletRetainsTheRemainderAndLaterPaysOnlyThatRemainder(GameTestHelper h) {
        Actor p=actor(h);
        if (!ChroniclesBridge.loaded()) { h.assertTrue(RewardService.payout(p)==0,"Absent wallet paid money"); h.succeed(); return; }
        profile(p,ChroniclesBridge.MAX_CENTS-50); grant(p,holder(h,"task"));
        h.assertTrue(ChroniclesBridge.balance(p)==ChroniclesBridge.MAX_CENTS && pending(h,p)==150,"Capacity-limited payment did not conserve money");
        ChroniclesBridge.debit(p,200);
        h.assertTrue(RewardService.payout(p)==150 && pending(h,p)==0,"Only the exact remainder should be paid");
        h.assertTrue(ChroniclesBridge.balance(p)==ChroniclesBridge.MAX_CENTS-50,"Remainder payment duplicated or lost money"); h.succeed();
    }
}
