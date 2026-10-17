package pt.realmcouncil;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import pt.realmcouncil.domain.*;
import static pt.realmcouncil.domain.Rules.*;
public final class CouncilCommands {
    public static void register(RegisterCommandsEvent event){
        for(String rootName:new String[]{"council","eleicoes"}){
            var root=Commands.literal(rootName);
            var admin=Commands.literal("admin").requires(s->s.hasPermission(2));
            admin.then(Commands.literal("create").then(Commands.argument("title",StringArgumentType.greedyString()).executes(c->run(c.getSource(),()->{
                var server=c.getSource().getServer();server.getPlayerList().getPlayers().forEach(CouncilService::observe);
                var d=CouncilData.get(server);CouncilConfig.settings().create(d.state,StringArgumentType.getString(c,"title"),System.currentTimeMillis());d.setDirty();server.overworld().getDataStorage().save();CouncilService.broadcast(server,Component.translatable("message.realmcouncil.created",d.state.current.title));
            }))));
            admin.then(Commands.literal("advance").executes(c->change(c.getSource(),s->s.advance(System.currentTimeMillis()))));
            admin.then(Commands.literal("cancel").executes(c->change(c.getSource(),s->s.cancel(System.currentTimeMillis()))));
            admin.then(Commands.literal("endterm").executes(c->change(c.getSource(),s->s.endTerm(System.currentTimeMillis()))));
            admin.then(Commands.literal("enrol").then(Commands.argument("player",EntityArgument.player()).executes(c->{var player=EntityArgument.getPlayer(c,"player");CouncilService.observe(player);return change(c.getSource(),s->s.enrol(player.getUUID()));})));
            admin.then(Commands.literal("status").executes(c->{var d=CouncilData.get(c.getSource().getServer());var e=d.state.current;c.getSource().sendSuccess(()->Component.translatable("message.realmcouncil.admin_status",e==null?"—":e.title,e==null?"—":e.phase.name(),e==null?0:e.eligible.size()),false);return 1;}));
            admin.then(Commands.literal("remove").then(Commands.argument("player",EntityArgument.player()).executes(c->{var p=EntityArgument.getPlayer(c,"player");return change(c.getSource(),s->s.withdraw(p.getUUID(),System.currentTimeMillis()));})));
            root.then(admin);event.getDispatcher().register(root);
        }
    }
    private static int change(CommandSourceStack source,java.util.function.Consumer<CouncilState> f){return run(source,()->{
        var server=source.getServer();CouncilService.advanceClock(server);var d=CouncilData.get(server);
        var phase=d.state.current==null?null:d.state.current.phase;int round=d.state.current==null?0:d.state.current.round;
        long termEnd=d.state.mandate==null?0:d.state.mandate.endsAt;
        f.accept(d.state);d.setDirty();server.overworld().getDataStorage().save();
        if(d.state.current!=null&&(d.state.current.phase!=phase||d.state.current.round!=round))CouncilService.announcePhase(server);
        if(d.state.mandate!=null&&d.state.mandate.endsAt!=termEnd&&d.state.current!=null&&d.state.current.phase==phase)
            CouncilService.broadcast(server,Component.translatable("notification.realmcouncil.term_ended"));
    });}
    private static int run(CommandSourceStack source,Runnable f){try{f.run();source.sendSuccess(()->Component.translatable("message.realmcouncil.saved"),true);return 1;}catch(RuleException ex){source.sendFailure(Component.translatable("error.realmcouncil."+ex.code));return 0;}}
}
