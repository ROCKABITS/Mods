package pt.realmfitness;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
            .then(Commands.literal("off").requires(source->source.hasPermission(2)).executes(context->change(context.getSource(),false)));
    }
    private static int status(CommandSourceStack source){
        try{
            boolean enabled=FitnessService.conditioningEnabled(source.getServer());
            source.sendSuccess(()->Component.translatable(enabled?"fitness.conditioning.status_on":"fitness.conditioning.status_off"),false);
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
