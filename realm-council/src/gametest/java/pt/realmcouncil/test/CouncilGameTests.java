package pt.realmcouncil.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.gametest.*;
import pt.realmcouncil.*;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.domain.CouncilState.*;
import pt.realmcouncil.network.CouncilPackets;
import static pt.realmcouncil.domain.CouncilAdministration.*;

@GameTestHolder(RealmCouncil.ID)
@PrefixGameTestTemplate(false)
public final class CouncilGameTests {
    private static FakePlayer player(GameTestHelper h){var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"CouncilQA"));p.setGameMode(GameType.SURVIVAL);return p;}
    private static CouncilData setup(GameTestHelper h,FakePlayer... players){var d=CouncilData.get(h.getLevel().getServer());d.state=new CouncilState();long now=System.currentTimeMillis();for(var p:players)d.state.observe(p.getUUID(),p.getGameProfile().getName(),2400,now-1);d.state.create("Realm Governor",now,60000,60000,60000,600000,60000,0,1,16,3);d.setDirty();return d;}
    private static void nominate(CouncilData d,FakePlayer p){d.state.nominate(p.getUUID(),RpBridge.identity(p),"A better realm","sun","Build a new market and improve public roads.",System.currentTimeMillis());}
    private static void voting(CouncilData d){d.state.advance(System.currentTimeMillis());d.state.advance(System.currentTimeMillis());}
    private static void rejected(GameTestHelper h,Runnable action){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Invalid action was accepted.");}
    private static void rejected(GameTestHelper h,String code,Runnable action){try{action.run();h.fail("Invalid action was accepted.");}catch(Rules.RuleException e){h.assertTrue(code.equals(e.code),"Expected "+code+" rejection, got "+e.code);}}
    private static BlockPos station(GameTestHelper h,FakePlayer p,String mode){var pos=h.absolutePos(new BlockPos(3,1,3));var block=switch(mode){case "board"->CouncilBlocks.NOTICE_BOARD.get();case "urn"->CouncilBlocks.BALLOT_BOX.get();case "oath"->CouncilBlocks.OATH_DESK.get();default->throw new IllegalArgumentException(mode);};h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());p.setPos(pos.getX()+0.5,pos.getY()+1,pos.getZ()+1.5);return pos;}
    private static BlockPos station(GameTestHelper h,FakePlayer p,boolean oath){return station(h,p,oath?"oath":"urn");}
    private static BlockPos board(GameTestHelper h,FakePlayer p){CouncilService.logout(p.getUUID());var pos=station(h,p,"board");CouncilService.open(p,"board",pos);return pos;}
    // GameTestServer assigns level 0 through op(); use an actual level-2 entry for permission tests.
    private static void operator(FakePlayer p){
        var list=p.server.getPlayerList();list.getOps().add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(),2,false));
        if(!p.hasPermissions(2))throw new IllegalStateException("Fixture permission: op="+list.isOp(p.getGameProfile())+", entry="+list.getOps().get(p.getGameProfile()).getLevel()+", server="+p.server.getProfilePermissions(p.getGameProfile()));
    }
    private static void action(GameTestHelper h,FakePlayer p,CouncilPackets.Action packet){board(h,p);CouncilService.action(p,packet);}
    private static CouncilPackets.Action admin(CouncilData d,int action,String target){var e=d.state.current;return new CouncilPackets.Action(action,e.id.toString(),e.round,target,"",e.phase.name());}

    @GameTest(template="council_empty",timeoutTicks=100)
    public static void sealedVoteFallbackNeverRevealsTheChoice(GameTestHelper h){
        var messages=new ArrayList<net.minecraft.network.chat.Component>();
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"VotePrivacyQA")){
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message){messages.add(message);}
        };
        p.setGameMode(GameType.SURVIVAL);var d=setup(h,p);nominate(d,p);voting(d);
        var pos=station(h,p,"urn");CouncilService.open(p,"urn",pos);messages.clear();
        h.assertTrue(!pt.realmcouncil.compat.NotificationsBridge.send(p,"election",net.minecraft.network.chat.Component.literal("unsupported")),
                "A fixture without the optional channel must use chat fallback.");
        CouncilService.action(p,new CouncilPackets.Action(3,d.state.current.id.toString(),1,p.getUUID().toString(),"",""));
        h.assertTrue(messages.size()==1,"A sealed vote must produce exactly one private fallback confirmation.");
        h.assertTrue(messages.getFirst().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents,
                "Vote confirmation must use a localized message.");
        var content=(net.minecraft.network.chat.contents.TranslatableContents)messages.getFirst().getContents();
        h.assertTrue(content.getKey().equals("notification.realmcouncil.vote_sealed")&&content.getArgs().length==0,
                "Private vote confirmation exposed a candidate, voter or ballot argument.");
        rejected(h,()->CouncilService.action(p,new CouncilPackets.Action(3,d.state.current.id.toString(),1,"","","")));
        h.assertTrue(messages.size()==1&&d.state.current.ballots.size()==1,"A rejected repeat request sent a second success notice or changed the ballot.");
        System.out.println("COUNCIL_NOTIFICATION_PRIVACY_PASSED");h.succeed();
    }

    @GameTest(template="council_empty",timeoutTicks=100)
    public static void blocksAndCraftingRecipesAreRegistered(GameTestHelper h){
        for(String id:List.of("ballot_box","notice_board","oath_desk")){var key=ResourceLocation.fromNamespaceAndPath(RealmCouncil.ID,id);var b=BuiltInRegistries.BLOCK.get(key);h.assertTrue(b!=Blocks.AIR,"Block missing: "+id);h.assertTrue(BuiltInRegistries.ITEM.get(key)!=net.minecraft.world.item.Items.AIR,"Block item missing: "+id);h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),"Recipe missing: "+id);}
        h.assertTrue(CouncilBlocks.BALLOT_BOX.get().defaultBlockState().getValue(CouncilBlock.FACING)==Direction.NORTH,"Default facing is invalid.");h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void actualNoticeBoardOpensForNonOperatorsAndAcceptsCandidacy(GameTestHelper h){
        var p=player(h);var d=setup(h,p);var pos=station(h,p,"board");
        h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(),p,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
        var view=CouncilService.snapshot(p,System.currentTimeMillis());
        h.assertTrue(view.mode().equals("board")&&view.administration()==null&&!p.hasPermissions(2),"Public board did not open safely for a non-operator.");
        CouncilService.action(p,new CouncilPackets.Action(1,d.state.current.id.toString(),1,"sun","A better realm","Build a public market and roads."));
        h.assertTrue(d.state.current.candidates.containsKey(p.getUUID()),"Physical board did not accept candidacy.");
        action(h,p,new CouncilPackets.Action(2,d.state.current.id.toString(),1,"","",""));
        h.assertTrue(!d.state.current.candidates.containsKey(p.getUUID()),"Physical board did not accept withdrawal.");h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void remoteOpensAndLegacyRemoteActionAreRejectedEvenForOperators(GameTestHelper h){
        var p=player(h);var d=setup(h,p);String before=d.state.toJson();
        rejected(h,"station",()->CouncilService.open(p,"remote",null));
        rejected(h,"station",()->CouncilService.open(p,"admin",null));
        rejected(h,"station",()->CouncilService.snapshot(p,System.currentTimeMillis()));
        rejected(h,"station",()->CouncilService.action(p,new CouncilPackets.Action(6,"",0,"","","")));
        board(h,p);rejected(h,"station",()->CouncilService.action(p,new CouncilPackets.Action(6,"",0,"","","")));
        operator(p);board(h,p);rejected(h,"station",()->CouncilService.action(p,new CouncilPackets.Action(6,"",0,"","","")));
        h.assertTrue(before.equals(d.state.toJson()),"Legacy remote action changed council data.");
        p.server.getPlayerList().deop(p.getGameProfile());h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void allPublicPacketsRequireAnExistingPhysicalSession(GameTestHelper h){
        var p=player(h);var d=setup(h,p);String before=d.state.toJson();
        for(int code=0;code<=5;code++){
            CouncilService.logout(p.getUUID());final int operation=code;
            rejected(h,"station",()->CouncilService.action(p,new CouncilPackets.Action(operation,d.state.current.id.toString(),1,"sun","A better realm","Build a public market and roads.")));
        }
        h.assertTrue(before.equals(d.state.toJson()),"A packet without a station session changed the election.");h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void openingRequiresNearbyMatchingPhysicalStation(GameTestHelper h){
        var p=player(h);setup(h,p);var pos=station(h,p,"board");
        rejected(h,"station",()->CouncilService.open(p,"board",null));
        rejected(h,"station",()->CouncilService.open(p,"urn",pos));
        p.setPos(pos.getX()+100,pos.getY(),pos.getZ());rejected(h,"station",()->CouncilService.open(p,"board",pos));
        p.setPos(pos.getX()+0.5,pos.getY()+1,pos.getZ()+1.5);h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        rejected(h,"station",()->CouncilService.open(p,"board",pos));h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void publicRefreshAndSnapshotRejectDistanceBrokenOrReplacedBoard(GameTestHelper h){
        var p=player(h);setup(h,p);var refresh=new CouncilPackets.Action(0,"",0,"","","");
        var pos=board(h,p);CouncilService.action(p,refresh);
        // Reopening clears request throttles so rejection must come from station validation.
        board(h,p);p.setPos(pos.getX()+100,pos.getY(),pos.getZ());
        rejected(h,"station",()->CouncilService.action(p,refresh));rejected(h,"station",()->CouncilService.snapshot(p,System.currentTimeMillis()));
        board(h,p);h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        rejected(h,"station",()->CouncilService.action(p,refresh));rejected(h,"station",()->CouncilService.snapshot(p,System.currentTimeMillis()));
        board(h,p);h.getLevel().setBlockAndUpdate(pos,CouncilBlocks.BALLOT_BOX.get().defaultBlockState());
        rejected(h,"station",()->CouncilService.action(p,refresh));rejected(h,"station",()->CouncilService.snapshot(p,System.currentTimeMillis()));h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void candidacyAndWithdrawalRejectDistanceAndBrokenBoard(GameTestHelper h){
        var p=player(h);var d=setup(h,p);
        for(int operation=1;operation<=2;operation++){
            if(operation==2)nominate(d,p);
            var packet=new CouncilPackets.Action(operation,d.state.current.id.toString(),1,"sun","A better realm","Build a public market and roads.");
            String before=d.state.toJson();var pos=board(h,p);p.setPos(pos.getX()+100,pos.getY(),pos.getZ());
            rejected(h,"station",()->CouncilService.action(p,packet));h.assertTrue(before.equals(d.state.toJson()),"Distant candidacy action changed the election.");
            board(h,p);h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            rejected(h,"station",()->CouncilService.action(p,packet));h.assertTrue(before.equals(d.state.toJson()),"Broken-board candidacy action changed the election.");
        }
        h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void actualBallotBoxInteractionRegistersTheVote(GameTestHelper h){
        var p=player(h);var d=setup(h,p);nominate(d,p);voting(d);var pos=station(h,p,false);
        h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(),p,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
        CouncilService.action(p,new CouncilPackets.Action(3,d.state.current.id.toString(),1,p.getUUID().toString(),"",""));
        h.assertTrue(d.state.current.ballots.get(p.getUUID()).equals(p.getUUID().toString()),"Vote was not registered.");h.assertTrue(d.state.current.ballots.size()==1,"Wrong number of ballots.");h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void noticeBoardCannotSubstituteForTheBallotBox(GameTestHelper h){var p=player(h);var d=setup(h,p);nominate(d,p);voting(d);board(h,p);rejected(h,"station",()->CouncilService.action(p,new CouncilPackets.Action(3,d.state.current.id.toString(),1,p.getUUID().toString(),"","")));h.assertTrue(d.state.current.ballots.isEmpty(),"Notice board allowed voting without an urn.");h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void stationRangeAndBrokenOrWrongBlocksAreRejected(GameTestHelper h){var p=player(h);setup(h,p);var pos=station(h,p,false);CouncilService.open(p,"urn",pos);CouncilService.physical(p,"urn");p.setPos(pos.getX()+100,pos.getY(),pos.getZ());rejected(h,()->CouncilService.physical(p,"urn"));p.setPos(pos.getX()+0.5,pos.getY()+1,pos.getZ()+1.5);h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());rejected(h,()->CouncilService.physical(p,"urn"));h.getLevel().setBlockAndUpdate(pos,CouncilBlocks.OATH_DESK.get().defaultBlockState());rejected(h,()->CouncilService.physical(p,"urn"));h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void winnerTakesOathThroughTheActualDesk(GameTestHelper h){var p=player(h);var voter=player(h);var d=setup(h,p,voter);nominate(d,p);voting(d);d.state.cast(voter.getUUID(),d.state.current.id,1,p.getUUID(),System.currentTimeMillis());d.state.advance(System.currentTimeMillis());var pos=station(h,p,true);h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(),p,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));CouncilService.action(p,new CouncilPackets.Action(4,d.state.mandate.electionId.toString(),0,"","",""));h.assertTrue(d.state.mandate.claimedAt>0,"Oath was not recorded.");h.assertTrue(!p.hasPermissions(2),"Election granted operator permissions.");h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void noticeBoardCannotSubstituteForTheOathDesk(GameTestHelper h){
        var p=player(h);var d=setup(h,p);nominate(d,p);voting(d);d.state.cast(p.getUUID(),d.state.current.id,1,p.getUUID(),System.currentTimeMillis());d.state.advance(System.currentTimeMillis());
        board(h,p);rejected(h,"station",()->CouncilService.action(p,new CouncilPackets.Action(4,d.state.mandate.electionId.toString(),0,"","","")));
        h.assertTrue(d.state.mandate.claimedAt==0,"Notice board allowed an oath without the desk.");h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void swornGovernorCanPublishARealNetworkDecision(GameTestHelper h){var p=player(h);var d=setup(h,p);nominate(d,p);voting(d);d.state.cast(p.getUUID(),d.state.current.id,1,p.getUUID(),System.currentTimeMillis());d.state.advance(System.currentTimeMillis());d.state.claim(p.getUUID(),System.currentTimeMillis());action(h,p,new CouncilPackets.Action(5,d.state.mandate.electionId.toString(),0,"","","Build a public market near the village."));h.assertTrue(d.state.mandate.decisions.size()==1,"Decision was not published.");h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void governorDecisionRejectsDistanceAndBrokenBoard(GameTestHelper h){
        var p=player(h);var d=setup(h,p);nominate(d,p);voting(d);d.state.cast(p.getUUID(),d.state.current.id,1,p.getUUID(),System.currentTimeMillis());d.state.advance(System.currentTimeMillis());d.state.claim(p.getUUID(),System.currentTimeMillis());
        var packet=new CouncilPackets.Action(5,d.state.mandate.electionId.toString(),0,"","","Build a public market near the village.");
        var pos=board(h,p);p.setPos(pos.getX()+100,pos.getY(),pos.getZ());rejected(h,"station",()->CouncilService.action(p,packet));
        board(h,p);h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());rejected(h,"station",()->CouncilService.action(p,packet));
        h.assertTrue(d.state.mandate.decisions.isEmpty(),"An invalid physical session published a decision.");h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void savedDataRoundtripSupportsLargeHistoryAndPrivateVotes(GameTestHelper h){var p=player(h);var d=setup(h,p);for(int election=0;election<8;election++){if(election>0)d.state.create("Election "+election,System.currentTimeMillis(),60000,60000,60000,60000,60000,0,1,16,3);for(int i=0;i<16;i++){UUID id=new UUID(800+election,i);d.state.observe(id,"Candidate"+i,2400,System.currentTimeMillis()-1);d.state.enrol(id);d.state.nominate(id,new Identity("Candidate"+i,"",0,false),"A new public programme","tree","x".repeat(1000),System.currentTimeMillis());}d.state.cancel(System.currentTimeMillis());}
        var tag=d.save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(tag.getTagType("council")==7&&tag.getByteArray("council").length>65535,"Large state must use byte-array NBT.");var restored=CouncilData.load(tag,h.getLevel().registryAccess());h.assertTrue(restored.state.history.size()==7&&restored.state.current.candidates.size()==16,"History did not survive storage.");h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void administratorCommandsRequirePermissionAndSupportBothAliases(GameTestHelper h){var p=player(h);CouncilData.get(p.server).state=new CouncilState();var commands=p.server.getCommands();var source=p.createCommandSourceStack();commands.performPrefixedCommand(source.withPermission(0),"council admin create Realm Governor");h.assertTrue(CouncilData.get(p.server).state.current==null,"Non-operator created an election.");commands.performPrefixedCommand(source.withPermission(2),"council admin create Realm Governor");commands.performPrefixedCommand(source.withPermission(2),"council admin enrol @s");var d=CouncilData.get(p.server);h.assertTrue(d.state.current!=null&&d.state.current.eligible.contains(p.getUUID()),"Admin create/enrol failed.");commands.performPrefixedCommand(source.withPermission(2),"eleicoes admin advance");h.assertTrue(d.state.current.phase==Phase.CAMPAIGN,"Portuguese alias failed.");commands.performPrefixedCommand(source.withPermission(2),"council admin cancel");h.assertTrue(d.state.current.phase==Phase.CANCELLED,"Cancel command failed.");h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void everyAdministratorPacketRejectsANonOperator(GameTestHelper h){var p=player(h);var d=setup(h,p);String before=d.state.toJson();for(int code=CREATE;code<=BLOCKS;code++){final int operation=code;rejected(h,"permission",()->action(h,p,new CouncilPackets.Action(operation,d.state.current.id.toString(),1,p.getUUID().toString(),"", "NOMINATIONS")));}h.assertTrue(before.equals(d.state.toJson()),"Unauthorized packets changed the election.");h.assertTrue(CouncilService.snapshot(p,System.currentTimeMillis()).administration()==null,"Non-operator received the electoral roll.");h.assertTrue(p.getInventory().isEmpty(),"Non-operator received blocks.");h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void operatorCanOrganizeAndFinishAnElectionUsingOnlyMenuPackets(GameTestHelper h){
        var p=player(h);operator(p);var d=CouncilData.get(p.server);d.state=new CouncilState();d.state.observe(p.getUUID(),"CouncilQA",0,System.currentTimeMillis()-1);
        var settings=new Settings(120,3,4,5,6,7,1,12,3);
        action(h,p,new CouncilPackets.Action(CREATE,"",0,"","Menu Governor",CouncilState.JSON.toJson(settings)));
        h.assertTrue(d.state.current!=null&&d.state.current.title.equals("Menu Governor")&&d.state.current.minimumPlayMinutes==120,"Menu creation did not use the chosen settings.");
        action(h,p,admin(d,ENROL,p.getUUID().toString()));
        action(h,p,new CouncilPackets.Action(1,d.state.current.id.toString(),1,"sun","A better realm","Build a public market and roads."));
        action(h,p,admin(d,ADVANCE,""));action(h,p,admin(d,ADVANCE,""));
        var pos=station(h,p,false);CouncilService.logout(p.getUUID());CouncilService.open(p,"urn",pos);
        CouncilService.action(p,new CouncilPackets.Action(3,d.state.current.id.toString(),1,p.getUUID().toString(),"",""));
        action(h,p,admin(d,ADVANCE,""));h.assertTrue(d.state.mandate!=null&&d.state.mandate.playerId.equals(p.getUUID()),"Menu counting did not elect the winner.");
        action(h,p,new CouncilPackets.Action(END_TERM,d.state.mandate.electionId.toString(),0,"","",""));h.assertTrue(!d.state.mandate.active(System.currentTimeMillis()),"Menu did not end the term.");
        p.server.getPlayerList().deop(p.getGameProfile());h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void offlineElectorsAndCandidatesCanBeManagedThroughMenuPackets(GameTestHelper h){
        var p=player(h);operator(p);var d=setup(h,p);UUID offline=UUID.randomUUID();d.state.observe(offline,"OfflineCitizen",0,System.currentTimeMillis()-1);
        action(h,p,admin(d,ENROL,offline.toString()));h.assertTrue(d.state.current.eligible.contains(offline),"Offline enrol failed.");
        d.state.nominate(offline,new Identity("Offline Citizen","",0,false),"A better realm","tree","Build a new public library.",System.currentTimeMillis());
        action(h,p,admin(d,REMOVE_CANDIDATE,offline.toString()));h.assertTrue(!d.state.current.candidates.containsKey(offline)&&d.state.current.eligible.contains(offline),"Candidate removal changed voter eligibility.");
        d.state.nominate(offline,new Identity("Offline Citizen","",0,false),"A better realm","tree","Build a new public library.",System.currentTimeMillis());
        action(h,p,admin(d,EXCLUDE,offline.toString()));h.assertTrue(!d.state.current.candidates.containsKey(offline)&&!d.state.current.eligible.contains(offline),"Elector exclusion did not remove the candidacy.");
        action(h,p,new CouncilPackets.Action(LIST,"",0,"","OfflineCitizen",""));var snapshot=CouncilService.snapshot(p,System.currentTimeMillis());
        h.assertTrue(snapshot.administration()!=null&&snapshot.administration().players().size()==1&&!snapshot.administration().players().getFirst().online(),"Offline menu search failed.");
        h.assertTrue(!CouncilState.JSON.toJson(snapshot.administration()).contains("ballots"),"Admin roll exposed ballots.");p.server.getPlayerList().deop(p.getGameProfile());h.succeed();
    }
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void outdatedConfirmationDoesNotAdvanceTwoPhases(GameTestHelper h){var p=player(h);operator(p);var d=setup(h,p);var original=admin(d,ADVANCE,"");action(h,p,original);rejected(h,()->action(h,p,original));h.assertTrue(d.state.current.phase==Phase.CAMPAIGN,"Stale confirmation advanced another phase.");p.server.getPlayerList().deop(p.getGameProfile());h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void revokingOperatorPermissionImmediatelyBlocksAnOpenMenu(GameTestHelper h){var p=player(h);operator(p);var d=setup(h,p);board(h,p);h.assertTrue(CouncilService.snapshot(p,System.currentTimeMillis()).administration()!=null,"Operator has no administration page.");p.server.getPlayerList().deop(p.getGameProfile());rejected(h,"permission",()->CouncilService.action(p,admin(d,CANCEL,"")));h.assertTrue(d.state.current.phase==Phase.NOMINATIONS&&CouncilService.snapshot(p,System.currentTimeMillis()).administration()==null,"Revoked operator could still change the election or read its roll.");h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void operatorMenuDeliversThreeActualBlockItems(GameTestHelper h){var p=player(h);operator(p);setup(h,p);action(h,p,new CouncilPackets.Action(BLOCKS,"",0,"","",""));for(var block:List.of(CouncilBlocks.BALLOT_BOX.get(),CouncilBlocks.NOTICE_BOARD.get(),CouncilBlocks.OATH_DESK.get()))h.assertTrue(p.getInventory().countItem(block.asItem())==1,"Menu did not deliver exactly one block item.");p.server.getPlayerList().deop(p.getGameProfile());h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    public static void invalidMenuCreationCannotReplaceTheCurrentElection(GameTestHelper h){var p=player(h);operator(p);var d=setup(h,p);action(h,p,admin(d,CANCEL,""));UUID previous=d.state.current.id;var bad=new Settings(0,1,1,1,1,1,1,17,1);rejected(h,()->action(h,p,new CouncilPackets.Action(CREATE,previous.toString(),1,"CANCELLED","Invalid election",CouncilState.JSON.toJson(bad))));h.assertTrue(d.state.current.id.equals(previous)&&d.state.current.phase==Phase.CANCELLED,"Invalid options replaced an election.");p.server.getPlayerList().deop(p.getGameProfile());h.succeed();}
    @GameTest(template="council_empty",timeoutTicks=100)
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void optionalCronicasAdapterWorksWithAndWithoutTheMod(GameTestHelper h) throws Exception {
        var p=player(h);var identity=RpBridge.identity(p);h.assertTrue(!identity.roleplay()&&identity.name().equals("CouncilQA"),"Uncreated profile did not fall back to Minecraft.");
        if(RpBridge.present()){
            var attachment=(AttachmentType)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();
            var clazz=Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object profile=clazz.getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,"Ari do Conselho",128,"Elfo","History","Ari","Comum",10000L,15000L,"");
            p.setData(attachment,profile);identity=RpBridge.identity(p);
            h.assertTrue(identity.roleplay()&&identity.name().equals("Ari do Conselho")&&identity.age()==128&&identity.race().equals("Elfo"),"Crónicas character was not imported.");
            var d=setup(h,p);nominate(d,p);h.assertTrue(d.state.current.candidates.get(p.getUUID()).name().equals("Ari do Conselho"),"Candidate did not use the RP name.");
            h.assertTrue(p.getData(attachment)==profile,"Optional adapter modified the RP profile.");
        }else h.assertTrue(identity.race().isEmpty()&&identity.age()==0,"Standalone adapter invented RP fields.");
        h.succeed();
    }
}
