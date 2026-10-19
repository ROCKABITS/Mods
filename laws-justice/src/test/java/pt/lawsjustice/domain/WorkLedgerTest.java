package pt.lawsjustice.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pt.lawsjustice.domain.WorkLedger.*;

class WorkLedgerTest {
    private static UUID id(long n){return new UUID(0,n);}
    private static final UUID ZONE=id(1),PRISON=id(2),PLAYER=id(3),SENTENCE=id(4),JOB=id(5);
    private static JusticeLedger.Point point(int x){return new JusticeLedger.Point(x,64,0);}
    private static Zone zone(String type){return new Zone(ZONE,PRISON,"Royal workshop",type,JusticeLedger.Area.between("minecraft:overworld",point(0),new JusticeLedger.Point(100,68,5)),0);}
    private static Node node(long n,String resource){return new Node(id(n),ZONE,point((int)n),resource,"minecraft:"+switch(resource){case "STONE"->"stone";case "WHEAT"->"wheat";case "LOG"->"oak_log";case "IRON"->"iron_ore";case "COAL"->"coal_ore";default->"stone_bricks";},"READY");}
    private static WorkLedger ready(String kind,int quantity,List<Node> nodes){
        var tasks=new ArrayList<>(Rules.defaults().tasks());tasks.replaceAll(r->r.kind().equals(kind)?new Rule(kind,quantity,r.reductionTicks()):r);
        return WorkLedger.empty().configure(new Rules(50,tasks)).putZone(zone(zoneType(kind))).commission(ZONE,nodes).accept(JOB,PLAYER,SENTENCE,PRISON,ZONE,kind,1000);
    }
    @Test void originalRulesContainAllSixRequestedTasksAndDefaultHalfSentenceCap(){
        assertEquals(50,Rules.defaults().maximumPercent());assertEquals(6,Rules.defaults().tasks().size());
        assertEquals(64,Rules.defaults().rule("MINING").quantity());assertEquals(4800,Rules.defaults().rule("FARMING").reductionTicks());
    }
    @Test void malformedRulesNeverBecomeEnabledWork(){
        for(int percent:new int[]{-1,101})assertThrows(IllegalArgumentException.class,()->new Rules(percent,Rules.defaults().tasks()));
        assertThrows(IllegalArgumentException.class,()->new Rules(50,List.of()));
        assertThrows(IllegalArgumentException.class,()->new Rule("MINING",0,1200));
        assertThrows(IllegalArgumentException.class,()->new Rule("MINING",1,-1));
        assertThrows(IllegalArgumentException.class,()->new Rule("FREE_MONEY",1,1200));
    }
    @Test void zonePlacementMustNotOverlapAnotherCommissionedSpace(){
        WorkLedger ledger=WorkLedger.empty().putZone(zone("MINE"));
        assertThrows(IllegalArgumentException.class,()->ledger.putZone(new Zone(id(90),PRISON,"Overlap","FARM",zone("FARM").area(),0)));
        assertThrows(IllegalArgumentException.class,()->ledger.commission(ZONE,List.of(node(10,"WHEAT"))));
    }
    @Test void aRealResourceProofCanOnlyBeCollectedOnce(){
        Node node=node(10,"STONE");WorkLedger before=ready("MINING",2,List.of(node));WorkLedger after=before.gather(PLAYER,node.id(),1001);
        assertEquals(0,before.active(PLAYER).output());assertEquals(1,after.active(PLAYER).output());assertEquals("SPENT",after.nodes().get(node.id()).state());
        assertThrows(IllegalArgumentException.class,()->after.gather(PLAYER,node.id(),1002));
        assertThrows(IllegalArgumentException.class,()->after.gather(PLAYER,id(999),1002));
    }
    @Test void mismatchedZonePrisonAndAnotherPlayersWorkNeverCount(){
        WorkLedger ledger=ready("MINING",2,List.of(node(10,"STONE")));
        assertThrows(IllegalArgumentException.class,()->ledger.gather(id(999),id(10),1001));
        assertThrows(IllegalArgumentException.class,()->ledger.accept(id(9),id(8),id(7),id(999),ZONE,"MINING",1001));
        assertThrows(IllegalArgumentException.class,()->ledger.accept(id(9),id(8),id(7),PRISON,ZONE,"FARMING",1001));
    }
    @Test void replacingAResourceInSurvivalDoesNotAutomaticallyMakeItReady(){
        Node n=node(10,"STONE");WorkLedger spent=ready("MINING",2,List.of(n)).gather(PLAYER,n.id(),1001).cancel(PLAYER,1002);
        WorkLedger ordinary=spent.placed("minecraft:overworld",n.position(),false).commission(ZONE,List.of(n));
        assertEquals("SPENT",ordinary.nodes().get(n.id()).state());
        WorkLedger refill=spent.placed("minecraft:overworld",n.position(),true);
        assertEquals("REFILLABLE",refill.nodes().get(n.id()).state());
        WorkLedger approved=refill.commission(ZONE,List.of(node(99,"STONE")));
        assertEquals("REFILLABLE",approved.nodes().get(n.id()).state());
        approved=refill.commission(ZONE,List.of(n));assertEquals("READY",approved.nodes().get(n.id()).state());
    }
    @Test void activeWorkFreezesZoneResourcesAgainstAdministrativeRecommissioning(){
        var ledger=ready("MINING",2,List.of(node(10,"STONE")));
        assertThrows(IllegalArgumentException.class,()->ledger.commission(ZONE,List.of(node(11,"STONE"))));
        var existing=ledger.zones().get(ZONE);assertThrows(IllegalArgumentException.class,()->ledger.putZone(existing));
    }
    @Test void changingFutureRulesDoesNotAlterAnAcceptedObjectiveOrReward(){
        var ledger=ready("MINING",2,List.of(node(10,"STONE")));var before=ledger.active(PLAYER);
        var rules=new ArrayList<>(ledger.rules().tasks());rules.replaceAll(r->r.kind().equals("MINING")?new Rule("MINING",400,1):r);
        var next=ledger.configure(new Rules(10,rules));assertEquals(before,next.active(PLAYER));assertEquals(2,next.active(PLAYER).quantity());
    }
    @Test void carpentryRequiresSealedLogsAndProducesAtMostTheConfiguredQuantity(){
        var ledger=ready("CARPENTRY",5,List.of(node(10,"LOG"),node(11,"LOG")));
        WorkLedger empty=ledger;assertThrows(IllegalArgumentException.class,()->empty.produce(PLAYER,1001));
        ledger=ledger.gather(PLAYER,id(10),1001).produce(PLAYER,1002);assertEquals(4,ledger.active(PLAYER).output());assertEquals(0,ledger.active(PLAYER).raw());
        ledger=ledger.gather(PLAYER,id(11),1003).produce(PLAYER,1004);assertEquals(5,ledger.active(PLAYER).output());
        WorkLedger full=ledger;assertThrows(IllegalArgumentException.class,()->full.produce(PLAYER,1005));
    }
    @Test void smithingConsumesARealOreProofAndFiniteCoalBeforeEachIngot(){
        var ledger=ready("SMITHING",2,List.of(node(10,"IRON"),node(11,"IRON"),node(12,"COAL")));
        ledger=ledger.gather(PLAYER,id(10),1001);WorkLedger unfueled=ledger;assertThrows(IllegalArgumentException.class,()->unfueled.produce(PLAYER,1002));
        ledger=ledger.gather(PLAYER,id(12),1003).produce(PLAYER,1004);assertEquals(1,ledger.active(PLAYER).output());assertEquals(7,ledger.active(PLAYER).fuel());
        ledger=ledger.gather(PLAYER,id(11),1005).produce(PLAYER,1006);assertEquals(2,ledger.active(PLAYER).output());assertEquals(6,ledger.active(PLAYER).fuel());
    }
    @Test void cleaningPointsAreDistinctServerResourcesThatCannotBeRepeated(){
        var ledger=ready("CLEANING",2,List.of(node(10,"CLEAN"),node(11,"CLEAN"))).gather(PLAYER,id(10),1001);
        assertThrows(IllegalArgumentException.class,()->ledger.gather(PLAYER,id(10),1002));assertEquals(2,ledger.gather(PLAYER,id(11),1003).active(PLAYER).output());
    }
    @Test void transportRequiresTwoSeparatePostsAtLeastSixBlocksApart(){
        var ledger=ready("TRANSPORT",16,List.of()).cargo(PLAYER,point(10),1001);
        assertEquals(0,ledger.active(PLAYER).output());assertThrows(IllegalArgumentException.class,()->ledger.cargo(PLAYER,point(10),1002));
        assertThrows(IllegalArgumentException.class,()->ledger.cargo(PLAYER,point(15),1002));
        var carried=ledger.cargo(PLAYER,point(16),1003);assertEquals(16,carried.active(PLAYER).output());assertEquals(2,carried.active(PLAYER).route().size());
        assertThrows(IllegalArgumentException.class,()->carried.cargo(PLAYER,point(30),1004));
    }
    @Test void completionRequiresTheWholeObjectiveAndTheSameCustodySentence(){
        var empty=ready("MINING",1,List.of(node(10,"STONE")));assertThrows(IllegalArgumentException.class,()->empty.finish(PLAYER,SENTENCE,1,1002));
        var full=empty.gather(PLAYER,id(10),1001);assertThrows(IllegalArgumentException.class,()->full.finish(PLAYER,id(999),1,1002));
        assertThrows(IllegalArgumentException.class,()->full.finish(PLAYER,SENTENCE,0,1002));
        assertThrows(IllegalArgumentException.class,()->full.finish(PLAYER,SENTENCE,full.active(PLAYER).reductionTicks()+1,1002));
    }
    @Test void aDeliveryAwardsOnlyTheActualReductionExactlyOnce(){
        var full=ready("MINING",1,List.of(node(10,"STONE"))).gather(PLAYER,id(10),1001);var done=full.finish(PLAYER,SENTENCE,17,1002);
        assertNull(done.active(PLAYER));assertEquals(DONE,done.jobs().get(JOB).status());assertEquals(17,done.jobs().get(JOB).awardedTicks());assertEquals(17,done.credits().get(SENTENCE).ticks());
        assertThrows(IllegalArgumentException.class,()->done.finish(PLAYER,SENTENCE,17,1003));assertEquals(1,done.credits().size());
    }
    @Test void clockRollbackDoesNotDestroyEarnedWorkOrAllowNegativeTimes(){
        var full=ready("MINING",1,List.of(node(10,"STONE"))).gather(PLAYER,id(10),0);assertEquals(1000,full.active(PLAYER).lastActionAt());
        var done=full.finish(PLAYER,SENTENCE,17,0);assertEquals(1000,done.jobs().get(JOB).finishedAt());
        assertThrows(IllegalArgumentException.class,()->ready("MINING",1,List.of(node(10,"STONE"))).gather(PLAYER,id(10),-1));
    }
    @Test void cancellationAndTheEndOfCustodyInvalidateEveryOutstandingBundle(){
        var ledger=ready("MINING",1,List.of(node(10,"STONE"))).gather(PLAYER,id(10),1001);
        var cancelled=ledger.reconcile(JusticeLedger.empty(),1002);assertNull(cancelled.active(PLAYER));assertEquals(CANCELLED,cancelled.jobs().get(JOB).status());assertTrue(cancelled.credits().isEmpty());
        assertThrows(IllegalArgumentException.class,()->cancelled.finish(PLAYER,SENTENCE,1,1003));assertSame(cancelled,cancelled.cancel(PLAYER,1004));
    }
    @Test void mutableCollectionsAndRestorationCannotForgeCredits(){
        var ledger=ready("MINING",1,List.of(node(10,"STONE"))).gather(PLAYER,id(10),1001).finish(PLAYER,SENTENCE,17,1002);
        assertEquals(ledger.state(),WorkLedger.restore(ledger.state()).state());
        assertThrows(UnsupportedOperationException.class,()->ledger.nodes().clear());
        assertThrows(IllegalArgumentException.class,()->new State(ledger.zones(),ledger.nodes(),ledger.jobs(),ledger.rules(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new State(ledger.zones(),ledger.nodes(),ledger.jobs(),ledger.rules(),Map.of(SENTENCE,new Credit(SENTENCE,PLAYER,16))));
    }
}
