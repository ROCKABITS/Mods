package pt.realmcasino.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import pt.realmcasino.domain.CasinoLedger.Kind;

/** Immutable, fully funded multiplayer hand. Deck and other players' hole cards stay on the server. */
public record TableGame(UUID id,UUID machine,Kind kind,long stake,long createdAt,long deadline,long revision,
                        Phase phase,int dealer,int actor,int street,long currentBet,long minimumRaise,int coinChoice,
                        List<Seat> seats,List<Integer> deck,List<Integer> board,List<UUID> pending,List<UUID> raiseRights,
                        List<Integer> results,Map<UUID,Long> payouts) {
    public enum Phase{WAITING,PLAYING,FINISHED}
    public static final int MAX_SEATS=6;
    public record Seat(UUID player,long stack,long committed,long streetBet,boolean folded,boolean stood,List<Integer> cards){
        public Seat{Objects.requireNonNull(player);Money.checked(stack);Money.checked(committed);Money.checked(streetBet);cards=List.copyOf(cards);if(streetBet>committed||cards.size()>16)throw new DomainException("invalid");cards.forEach(Blackjack::rank);}
        public long buyIn(){return Money.add(stack,committed);}
    }
    public TableGame{
        Objects.requireNonNull(id);Objects.requireNonNull(machine);Objects.requireNonNull(kind);Objects.requireNonNull(phase);
        seats=List.copyOf(seats);deck=List.copyOf(deck);board=List.copyOf(board);pending=List.copyOf(pending);raiseRights=List.copyOf(raiseRights);results=List.copyOf(results);payouts=Map.copyOf(payouts);
        if(!shared(kind)||stake<1||stake>Money.MAX_CENTS/MAX_SEATS||createdAt<0||deadline<createdAt||revision<1||seats.isEmpty()||seats.size()>capacity(kind)||dealer<0||dealer>=MAX_SEATS||actor< -1||actor>=seats.size()||street<0||street>3||minimumRaise<1||coinChoice<0||coinChoice>1)throw new DomainException("invalid");
        Money.checked(currentBet);var players=new HashSet<UUID>();var cards=new ArrayList<Integer>(deck);cards.addAll(board);
        for(Seat seat:seats){if(!players.add(seat.player())||(kind!=Kind.POKER?seat.buyIn()!=stake:seat.buyIn()<2||seat.buyIn()>Money.MAX_CENTS/MAX_SEATS)||seat.streetBet()>currentBet)throw new DomainException("invalid");cards.addAll(seat.cards());}
        if(!players.containsAll(pending)||!players.containsAll(raiseRights)||new HashSet<>(pending).size()!=pending.size()||new HashSet<>(raiseRights).size()!=raiseRights.size()||board.size()>5)throw new DomainException("invalid");
        cards.forEach(Blackjack::rank);if(new HashSet<>(cards).size()!=cards.size()||cards.size()>52)throw new DomainException("invalid");
        if(phase==Phase.WAITING){if(actor!=-1||!deck.isEmpty()||!board.isEmpty()||!pending.isEmpty()||!raiseRights.isEmpty()||!results.isEmpty()||currentBet!=0||seats.stream().anyMatch(s->s.committed()!=0||s.folded()||s.stood()||!s.cards().isEmpty()))throw new DomainException("invalid");}
        else if(kind==Kind.POKER||kind==Kind.BLACKJACK_PVP){if(cards.size()!=52||seats.size()<2||seats.stream().anyMatch(s->s.cards().size()<2)||!results.isEmpty())throw new DomainException("invalid");}
        else if(!cards.isEmpty()||phase==Phase.PLAYING)throw new DomainException("invalid");
        if(phase==Phase.PLAYING&&(actor<0||seats.get(actor).folded()||seats.get(actor).stood()||kind==Kind.POKER&&!pending.contains(seats.get(actor).player())))throw new DomainException("invalid");
        if(phase==Phase.FINISHED){
            if(actor!=-1||!pending.isEmpty()||!raiseRights.isEmpty()||!payouts.keySet().equals(players))throw new DomainException("invalid");
            long total=0;for(long amount:payouts.values())total=Money.add(total,amount);long funding=0;for(Seat seat:seats)funding=Money.add(funding,seat.buyIn());if(total!=funding)throw new DomainException("invalid");
            if(kind==Kind.COIN&&(results.size()!=1||results.getFirst()<0||results.getFirst()>1)||kind==Kind.DICE&&(results.size()!=seats.size()*2||results.stream().anyMatch(n->n<1||n>6)))throw new DomainException("invalid");
        }else if(!payouts.isEmpty())throw new DomainException("invalid");
    }
    public static boolean shared(Kind kind){return kind==Kind.POKER||kind==Kind.BLACKJACK_PVP||kind==Kind.DICE||kind==Kind.COIN;}
    public static int capacity(Kind kind){return kind==Kind.COIN?2:MAX_SEATS;}
    public boolean finished(){return phase==Phase.FINISHED;}
    public long escrow(){if(finished())return 0;long total=0;for(Seat seat:seats)total=Money.add(total,seat.buyIn());return total;}
    public long pot(){long total=0;for(Seat s:seats)total=Money.add(total,s.committed());return total;}
    public Seat seat(UUID player){return seats.stream().filter(s->s.player().equals(player)).findFirst().orElse(null);}
    public static TableGame lobby(UUID id,UUID machine,Kind kind,UUID player,long stake,int choice,int dealer,long now){return new TableGame(id,machine,kind,stake,now,Math.addExact(now,120000),1,Phase.WAITING,dealer,-1,0,0,1,choice,List.of(new Seat(player,stake,0,0,false,false,List.of())),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of());}
    public TableGame join(UUID player,long amount){
        if(phase!=Phase.WAITING||(kind!=Kind.POKER&&amount!=stake)||seat(player)!=null||seats.size()>=capacity(kind))throw new DomainException("table_unavailable");
        var w=new Work(this);w.seats.add(new Seat(player,amount,0,0,false,false,List.of()));return w.freeze();
    }
    public TableGame leaveLobby(UUID player){
        if(phase!=Phase.WAITING||seat(player)==null)throw new DomainException("table_unavailable");var w=new Work(this);w.seats.removeIf(s->s.player().equals(player));return w.seats.isEmpty()?null:w.freeze();
    }
    public TableGame start(long now,IntUnaryOperator random){
        if(phase!=Phase.WAITING||seats.size()<2)throw new DomainException("table_players");var w=new Work(this);w.phase=Phase.PLAYING;w.dealer%=seats.size();w.deadline=Math.addExact(now,60000);
        if(kind==Kind.COIN){int side=random.applyAsInt(2);if(side<0||side>1)throw new DomainException("invalid");w.results.add(side);w.seats.replaceAll(s->new Seat(s.player(),0,stake,stake,false,true,List.of()));w.currentBet=stake;w.award(List.of(side==coinChoice?seats.getFirst().player():seats.get(1).player()));}
        else if(kind==Kind.DICE){int highest=0;var winners=new ArrayList<UUID>();for(int i=0;i<seats.size();i++){int a=random.applyAsInt(6)+1,b=random.applyAsInt(6)+1;if(a<1||a>6||b<1||b>6)throw new DomainException("invalid");w.results.add(a);w.results.add(b);if(a+b>highest){highest=a+b;winners.clear();}if(a+b==highest)winners.add(seats.get(i).player());}w.seats.replaceAll(s->new Seat(s.player(),0,stake,stake,false,true,List.of()));w.currentBet=stake;w.award(winners);}
        else{
            w.deck.addAll(Blackjack.shuffledDeck(random));for(int round=0;round<2;round++)for(int offset=1;offset<=seats.size();offset++){int index=(w.dealer+offset)%seats.size();w.card(index);}
            if(kind==Kind.POKER){
                if(stake<2)throw new DomainException("poker_buyin");long blind=Math.max(1,stake/100);int small=seats.size()==2?w.dealer:(w.dealer+1)%seats.size(),big=(small+1)%seats.size();w.pay(small,Math.min(blind,w.seats.get(small).stack()));w.pay(big,Math.min(blind*2,w.seats.get(big).stack()));w.currentBet=Math.max(w.seats.get(small).streetBet(),w.seats.get(big).streetBet());w.minimumRaise=blind*2;
                w.pending.addAll(w.eligible());w.rights.addAll(w.pending);w.actor=w.next(big,w.pending);w.afterPoker(big,now);
            }else{w.seats.replaceAll(s->new Seat(s.player(),0,stake,stake,false,Blackjack.hand(s.cards()).total()>=21,s.cards()));w.currentBet=stake;w.actor=w.nextBlackjack(w.dealer);if(w.actor<0)w.finishBlackjack();}
        }
        return w.freeze();
    }
    public TableGame act(UUID player,String action,long amount,long now){
        if(phase!=Phase.PLAYING||actor<0||!seats.get(actor).player().equals(player))throw new DomainException("table_turn");var w=new Work(this);int index=actor;
        if(kind==Kind.BLACKJACK_PVP){
            if(action.equals("hit")){w.card(index);Seat s=w.seats.get(index);if(Blackjack.hand(s.cards()).total()>=21)w.seats.set(index,new Seat(s.player(),0,stake,stake,false,true,s.cards()));}
            else if(action.equals("stand")){Seat s=w.seats.get(index);w.seats.set(index,new Seat(s.player(),0,stake,stake,false,true,s.cards()));}
            else throw new DomainException("invalid_request");
            if(w.seats.get(index).stood())w.actor=w.nextBlackjack(index);if(w.actor<0)w.finishBlackjack();else w.deadline=Math.addExact(now,60000);
        }else if(kind==Kind.POKER){
            Seat before=w.seats.get(index);long due=w.currentBet-before.streetBet();
            switch(action){
                case "fold"->w.fold(index);
                case "check"->{if(due!=0)throw new DomainException("poker_call");}
                case "call"->w.pay(index,Math.min(due,before.stack()));
                case "raise","allin"->{
                    long target=action.equals("allin")?Money.add(before.streetBet(),before.stack()):amount;
                    if(target<=w.currentBet){if(!action.equals("allin"))throw new DomainException("poker_raise");w.pay(index,Math.min(due,before.stack()));}
                    else{
                        if(!w.rights.contains(player)||target>Money.add(before.streetBet(),before.stack()))throw new DomainException("poker_raise");long increase=target-w.currentBet;boolean full=increase>=w.minimumRaise;
                        if(!full&&target!=Money.add(before.streetBet(),before.stack()))throw new DomainException("poker_raise");
                        w.pay(index,target-before.streetBet());w.currentBet=target;
                        if(full){w.minimumRaise=increase;w.pending.clear();w.pending.addAll(w.eligible());w.rights.clear();w.rights.addAll(w.pending);}
                        else for(Seat s:w.seats)if(!s.folded()&&s.stack()>0&&s.streetBet()<target)w.pending.add(s.player());
                    }
                }
                default->throw new DomainException("invalid_request");
            }
            w.pending.remove(player);w.rights.remove(player);w.afterPoker(index,now);
        }else throw new DomainException("invalid_request");
        return w.freeze();
    }
    public TableGame departure(UUID player,long now){
        if(phase!=Phase.PLAYING||seat(player)==null)return this;var w=new Work(this);int index=0;while(!w.seats.get(index).player().equals(player))index++;
        Seat s=w.seats.get(index);if(kind==Kind.POKER){if(s.folded())return this;w.fold(index);w.pending.remove(player);w.rights.remove(player);w.afterPoker(index==actor?actor:(actor+seats.size()-1)%seats.size(),now);}
        else if(kind==Kind.BLACKJACK_PVP){if(s.stood())return this;w.seats.set(index,new Seat(s.player(),0,stake,stake,false,true,s.cards()));if(index==actor)w.actor=w.nextBlackjack(index);if(w.actor<0)w.finishBlackjack();}
        return w.freeze();
    }
    public TableGame timeout(long now){if(phase!=Phase.PLAYING)throw new DomainException("invalid_request");Seat seat=seats.get(actor);return act(seat.player(),kind==Kind.BLACKJACK_PVP?"stand":seat.streetBet()==currentBet?"check":"fold",0,now);}
    private static final class Work{
        final TableGame original;Phase phase;int dealer,actor,street;long currentBet,minimumRaise,deadline;final ArrayList<Seat> seats;final ArrayList<Integer> deck,board,results;final LinkedHashSet<UUID> pending,rights;final LinkedHashMap<UUID,Long> payouts;
        Work(TableGame t){original=t;phase=t.phase;dealer=t.dealer;actor=t.actor;street=t.street;currentBet=t.currentBet;minimumRaise=t.minimumRaise;deadline=t.deadline;seats=new ArrayList<>(t.seats);deck=new ArrayList<>(t.deck);board=new ArrayList<>(t.board);results=new ArrayList<>(t.results);pending=new LinkedHashSet<>(t.pending);rights=new LinkedHashSet<>(t.raiseRights);payouts=new LinkedHashMap<>(t.payouts);}
        TableGame freeze(){return new TableGame(original.id,original.machine,original.kind,original.stake,original.createdAt,deadline,Math.addExact(original.revision,1),phase,dealer,actor,street,currentBet,minimumRaise,original.coinChoice,seats,deck,board,new ArrayList<>(pending),new ArrayList<>(rights),results,payouts);}
        void card(int index){if(deck.isEmpty())throw new DomainException("invalid");Seat s=seats.get(index);var hand=new ArrayList<>(s.cards());hand.add(deck.removeFirst());seats.set(index,new Seat(s.player(),s.stack(),s.committed(),s.streetBet(),s.folded(),s.stood(),hand));}
        void pay(int index,long amount){Seat s=seats.get(index);if(amount<0||amount>s.stack())throw new DomainException("poker_stack");seats.set(index,new Seat(s.player(),s.stack()-amount,Money.add(s.committed(),amount),Money.add(s.streetBet(),amount),s.folded(),s.stood(),s.cards()));}
        void fold(int index){Seat s=seats.get(index);seats.set(index,new Seat(s.player(),s.stack(),s.committed(),s.streetBet(),true,s.stood(),s.cards()));}
        List<UUID> eligible(){return seats.stream().filter(s->!s.folded()&&s.stack()>0).map(Seat::player).toList();}
        int next(int previous,Collection<UUID> candidates){for(int n=1;n<=seats.size();n++){int i=(previous+n)%seats.size();if(candidates.contains(seats.get(i).player()))return i;}return -1;}
        int nextBlackjack(int previous){for(int n=1;n<=seats.size();n++){int i=(previous+n)%seats.size();if(!seats.get(i).stood())return i;}return -1;}
        void afterPoker(int previous,long now){
            var alive=seats.stream().filter(s->!s.folded()).map(Seat::player).toList();if(alive.size()==1){award(alive);return;}
            pending.retainAll(eligible());rights.retainAll(eligible());
            if(!pending.isEmpty()){
                actor=next(previous,pending);deadline=Math.addExact(now,60000);return;
            }
            if(street==3){showdown();return;}
            if(eligible().size()<=1){while(board.size()<5)board.add(deck.removeFirst());street=3;showdown();return;}
            street++;int count=street==1?3:1;for(int i=0;i<count;i++)board.add(deck.removeFirst());currentBet=0;minimumRaise=Math.max(2,original.stake/100*2);
            seats.replaceAll(s->new Seat(s.player(),s.stack(),s.committed(),0,s.folded(),s.stood(),s.cards()));pending.addAll(eligible());rights.addAll(pending);actor=next(dealer,pending);deadline=Math.addExact(now,60000);
        }
        void finishBlackjack(){int best=-1;var winners=new ArrayList<UUID>();for(Seat s:seats){var h=Blackjack.hand(s.cards());int score=h.bust()?-1:h.natural()?22:h.total();if(score>best){best=score;winners.clear();}if(score==best&&score>=0)winners.add(s.player());}if(winners.isEmpty()){seats.forEach(s->payouts.put(s.player(),original.stake));finish();}else award(winners);}
        void initializePayouts(){payouts.clear();seats.forEach(s->payouts.put(s.player(),s.stack()));}
        void award(List<UUID> winners){initializePayouts();long pot=0;for(Seat s:seats)pot=Money.add(pot,s.committed());split(pot,winners);finish();}
        void split(long amount,List<UUID> winners){if(winners.isEmpty())throw new DomainException("invalid");long each=amount/winners.size(),remainder=amount%winners.size();for(int offset=1;offset<=seats.size();offset++){UUID id=seats.get((dealer+offset)%seats.size()).player();if(winners.contains(id)){long extra=remainder>0?1:0;remainder-=extra;payouts.put(id,Money.add(payouts.getOrDefault(id,0L),each+extra));}}}
        void showdown(){
            initializePayouts();var levels=seats.stream().map(Seat::committed).filter(n->n>0).distinct().sorted().toList();long previous=0;
            for(long level:levels){var contributors=seats.stream().filter(s->s.committed()>=level).toList();long pot=Money.multiply(level-previous,contributors.size());previous=level;
                if(contributors.size()==1){UUID id=contributors.getFirst().player();payouts.put(id,Money.add(payouts.get(id),pot));continue;}
                var eligible=contributors.stream().filter(s->!s.folded()).toList();if(eligible.isEmpty())eligible=seats.stream().filter(s->!s.folded()).toList();
                PokerHands.Rank best=null;var winners=new ArrayList<UUID>();for(Seat s:eligible){var cards=new ArrayList<>(s.cards());cards.addAll(board);var rank=PokerHands.best(cards);if(best==null||rank.compareTo(best)>0){best=rank;winners.clear();}if(rank.compareTo(best)==0)winners.add(s.player());}split(pot,winners);
            }finish();
        }
        void finish(){phase=Phase.FINISHED;actor=-1;pending.clear();rights.clear();}
    }
}
