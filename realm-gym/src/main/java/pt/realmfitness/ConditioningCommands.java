package pt.realmfitness;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import pt.realmfitness.domain.Conditioning;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Server-wide controls available from chat, console and the existing /fitness alias. */
public final class ConditioningCommands {
    private ConditioningCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> create(){
        return Commands.literal("conditioning")
            .executes(context->status(context.getSource()))
            .then(Commands.literal("status").executes(context->status(context.getSource())))
            .then(Commands.literal("on").requires(source->source.hasPermission(2)).executes(context->change(context.getSource(),true)))
            .then(Commands.literal("off").requires(source->source.hasPermission(2)).executes(context->change(context.getSource(),false)))
            .then(Commands.literal("days").requires(source->source.hasPermission(2)).executes(context->status(context.getSource()))
                .then(mode(Conditioning.TimeMode.REAL)).then(mode(Conditioning.TimeMode.MINECRAFT)));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> mode(Conditioning.TimeMode mode){
        return Commands.literal(mode.id()).then(Commands.argument("days",IntegerArgumentType.integer(0,365))
            .executes(context->days(context.getSource(),mode,IntegerArgumentType.getInteger(context,"days"))));
    }
    private static int status(CommandSourceStack source){
        try{
            boolean enabled=FitnessService.conditioningEnabled(source.getServer());
            source.sendSuccess(()->Component.translatable(enabled?"fitness.conditioning.status_on":"fitness.conditioning.status_off"),false);
            var config=FitnessService.conditioningSettings(source.getServer());
            source.sendSuccess(()->Component.translatable("fitness.conditioning.schedule",Component.translatable("fitness.conditioning.mode."+config.timeMode.id()),config.graceDays),false);
            return 1;
        }catch(RuntimeException failure){return failed(source,failure);}
    }
    private static int days(CommandSourceStack source,Conditioning.TimeMode mode,int days){
        try{
            FitnessService.setConditioningDays(source.getServer(),mode,days);
            source.sendSuccess(()->Component.translatable("fitness.conditioning.days_set",days,Component.translatable("fitness.conditioning.mode."+mode.id())),true);
            return 1;
        }catch(RuntimeException failure){return failed(source,failure);}
    }
    private static int change(CommandSourceStack source,boolean enabled){
        try{
            FitnessService.setConditioning(source.getServer(),enabled);
            source.sendSuccess(()->Component.translatable(enabled?"fitness.conditioning.turned_on":"fitness.conditioning.turned_off"),true);
            return 1;
        }catch(RuntimeException failure){return failed(source,failure);}
    }
    private static int failed(CommandSourceStack source,RuntimeException failure){
        FitnessMod.LOG.error("Cannot read or update physical condition settings",failure);
        source.sendFailure(Component.translatable("fitness.conditioning.command_failed"));return 0;
    }
}
