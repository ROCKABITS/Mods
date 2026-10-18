package pt.realmcasino.data;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pt.realmcasino.domain.Blackjack;
import pt.realmcasino.domain.CasinoLedger.Kind;
import pt.realmcasino.domain.Roulette;
import pt.realmcasino.domain.Slots;

/** Authoritative persistent result/deck. Hidden cards and undealt cards never enter snapshots. */
public record GameState(UUID id, UUID machine, UUID player, Kind kind, long stake, long startedAt,
                        long deadline, long revision, Blackjack.State blackjack, List<Integer> reels,
                        int rouletteNumber, String betType, int betNumber, long payout, boolean finished) {
    public GameState {
        Objects.requireNonNull(id); Objects.requireNonNull(machine); Objects.requireNonNull(player); Objects.requireNonNull(kind);
        reels = List.copyOf(Objects.requireNonNull(reels)); Objects.requireNonNull(betType);
        if (stake <= 0 || startedAt < 0 || deadline < startedAt || revision < 1 || payout < 0) throw new IllegalArgumentException("Invalid game state.");
        switch (kind) {
            case SLOTS -> {
                if (!finished || blackjack != null || reels.size() != 3 || !betType.isEmpty() || rouletteNumber != -1 || betNumber != -1
                        || Slots.result(reels.get(0), reels.get(1), reels.get(2)).payout(stake) != payout) throw new IllegalArgumentException("Invalid slots state.");
            }
            case ROULETTE -> {
                if (!finished || blackjack != null || !reels.isEmpty()
                        || new Roulette.Bet(Roulette.Type.valueOf(betType), betNumber).payout(stake, rouletteNumber) != payout) throw new IllegalArgumentException("Invalid roulette state.");
            }
            case BLACKJACK -> {
                Objects.requireNonNull(blackjack);
                if (!reels.isEmpty() || !betType.isEmpty() || rouletteNumber != -1 || betNumber != -1 || finished != blackjack.finished()
                        || (finished ? blackjack.payout(stake) != payout : payout != 0)) throw new IllegalArgumentException("Invalid blackjack state.");
            }
        }
    }
    public static GameState slots(UUID id, UUID machine, UUID player, long stake, long now, Slots.Result result) {
        return new GameState(id,machine,player,Kind.SLOTS,stake,now,now,1,null,result.reels(),-1,"",-1,result.payout(stake),true);
    }
    public static GameState roulette(UUID id, UUID machine, UUID player, long stake, long now, Roulette.Bet bet, int result) {
        return new GameState(id,machine,player,Kind.ROULETTE,stake,now,now,1,null,List.of(),result,bet.type().name(),bet.number(),bet.payout(stake,result),true);
    }
    public static GameState blackjack(UUID id, UUID machine, UUID player, long stake, long now, Blackjack.State state) {
        return new GameState(id,machine,player,Kind.BLACKJACK,stake,now,Math.addExact(now,60_000),1,state,List.of(),-1,"",-1,state.finished()?state.payout(stake):0,state.finished());
    }
    public GameState update(Blackjack.State state) {
        if (kind != Kind.BLACKJACK || finished) throw new IllegalStateException("No active hand.");
        return new GameState(id,machine,player,kind,stake,startedAt,deadline,Math.addExact(revision,1),state,reels,rouletteNumber,betType,betNumber,state.finished()?state.payout(stake):0,state.finished());
    }
}
