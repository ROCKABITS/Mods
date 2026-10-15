package pt.realmcouncil;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class CouncilConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MIN_PLAY,NOMINATIONS,CAMPAIGN,VOTING,TERM,RUNOFF,MIN_VOTES,MAX_CANDIDATES,MAX_ROUNDS;
    static{
        var b=new ModConfigSpec.Builder();
        MIN_PLAY=b.comment("Minimum Minecraft play time in minutes. The electoral roll is fixed at creation; operators may enrol players during nominations.").defineInRange("minimumPlayMinutes",120,0,525600);
        NOMINATIONS=b.defineInRange("nominationMinutes",1440,1,525600);
        CAMPAIGN=b.defineInRange("campaignMinutes",2880,1,525600);
        VOTING=b.defineInRange("votingMinutes",2880,1,525600);
        TERM=b.defineInRange("termMinutes",20160,1,525600);
        RUNOFF=b.defineInRange("runoffMinutes",1440,1,525600);
        MIN_VOTES=b.defineInRange("minimumVotes",1,1,100000);
        MAX_CANDIDATES=b.defineInRange("maximumCandidates",12,1,16);
        MAX_ROUNDS=b.comment("Persistent ties end without a winner after this many rounds.").defineInRange("maximumRounds",3,1,10);
        SPEC=b.build();
    }
    public static pt.realmcouncil.domain.CouncilAdministration.Settings settings(){
        return new pt.realmcouncil.domain.CouncilAdministration.Settings(MIN_PLAY.get(),NOMINATIONS.get(),CAMPAIGN.get(),VOTING.get(),TERM.get(),RUNOFF.get(),MIN_VOTES.get(),MAX_CANDIDATES.get(),MAX_ROUNDS.get());
    }
}
