package pt.cronicasrp;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.UnaryOperator;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import pt.cronicasrp.domain.*;
import pt.cronicasrp.i18n.ModMessages;
import pt.cronicasrp.network.*;

public final class RpCommands {
    private RpCommands() {}
    private record Words(String profile, String balance, String destiny, String pay, String exchange,
                         String withdraw, String deposit, String all, String withdrawItems, String depositItems,
                         String player, String amount, String item, String quantity, String give,
                         String reputation, String mission, String medal, boolean english) {}
    private static final Words PT = new Words("ficha", "saldo", "destino", "pagar", "cambio", "levantar", "depositar", "tudo",
            "levantarpecas", "depositarpecas", "jogador", "valor", "moeda", "quantidade", "dar", "reputacao", "missao", "medalha", false);
    private static final Words EN = new Words("profile", "balance", "destiny", "pay", "exchange", "withdraw", "deposit", "all",
            "withdrawpieces", "depositpieces", "player", "amount", "denomination", "quantity", "give", "reputation", "mission", "medal", true);
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("rp");
        var admin = Commands.literal("admin").requires(source -> source.hasPermission(2));
        for (Words w : new Words[]{PT, EN}) {
            root.then(Commands.literal(w.profile()).executes(context -> {
                var player = context.getSource().getPlayerOrException();
                ProfileNetwork.send(player, player.getData(ModAttachments.PROFILE).created() ? ProfilePackets.VIEW : ProfilePackets.CREATE, "");
                return 1;
            }));
            root.then(Commands.literal(w.balance()).executes(context -> {
                var player = context.getSource().getPlayerOrException();
                var profile = player.getData(ModAttachments.PROFILE);
                long physical = CashExchange.total(CashService.snapshot(player));
                context.getSource().sendSuccess(() -> profile.created()
                        ? ModMessages.tr("message.cronicasrp.balance", ModMessages.money(profile.balance()), ModMessages.money(physical), ModMessages.money(profile.balance() + physical))
                        : ModMessages.tr("error.cronicasrp.create_hint"), false);
                return 1;
            }));
            root.then(Commands.literal(w.destiny()).executes(context -> {
                var player = context.getSource().getPlayerOrException();
                ProfileNetwork.send(player, player.getData(ModAttachments.PROFILE).created() ? ProfilePackets.REVEAL : ProfilePackets.CREATE, ""); return 1;
            }));
            root.then(Commands.literal(w.pay()).then(Commands.argument(w.player(), EntityArgument.player())
                    .then(Commands.argument(w.amount(), StringArgumentType.greedyString()).executes(context -> pay(context.getSource(),
                            context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, w.player()), StringArgumentType.getString(context, w.amount()))))));
            root.then(Commands.literal(w.exchange()).executes(context -> openCash(context.getSource())));
            root.then(Commands.literal(w.withdraw()).executes(context -> openCash(context.getSource()))
                    .then(Commands.argument(w.amount(), StringArgumentType.greedyString()).executes(context -> exchangeValue(context.getSource(), CashPackets.WITHDRAW, StringArgumentType.getString(context, w.amount())))));
            root.then(Commands.literal(w.deposit()).executes(context -> exchange(context.getSource(), new CashPackets.Request(CashPackets.DEPOSIT_ALL, 0, "", 0)))
                    .then(Commands.literal(w.all()).executes(context -> exchange(context.getSource(), new CashPackets.Request(CashPackets.DEPOSIT_ALL, 0, "", 0))))
                    .then(Commands.argument(w.amount(), StringArgumentType.greedyString()).executes(context -> exchangeValue(context.getSource(), CashPackets.DEPOSIT, StringArgumentType.getString(context, w.amount())))));
            root.then(piecesCommand(w.withdrawItems(), CashPackets.WITHDRAW_PIECES, w));
            root.then(piecesCommand(w.depositItems(), CashPackets.DEPOSIT_PIECES, w));
            addAdmin(admin, w);
        }
        root.then(admin);
        event.getDispatcher().register(root);
    }
    private static void addAdmin(LiteralArgumentBuilder<CommandSourceStack> admin, Words w) {
        admin.then(Commands.literal(w.give()).then(Commands.argument(w.player(), EntityArgument.player())
                .then(Commands.argument(w.amount(), StringArgumentType.greedyString()).executes(context -> grant(context.getSource(),
                        EntityArgument.getPlayer(context, w.player()), StringArgumentType.getString(context, w.amount()))))));
        admin.then(Commands.literal(w.reputation()).then(Commands.argument(w.player(), EntityArgument.player())
                .then(Commands.argument(w.amount(), IntegerArgumentType.integer(0, 100)).executes(context -> progress(context.getSource(),
                        EntityArgument.getPlayer(context, w.player()), rp -> rp.withReputation(IntegerArgumentType.getInteger(context, w.amount())),
                        ModMessages.tr("message.cronicasrp.progress.reputation"))))));
        admin.then(Commands.literal(w.mission()).then(Commands.argument(w.player(), EntityArgument.player())
                .executes(context -> progress(context.getSource(), EntityArgument.getPlayer(context, w.player()), rp -> rp.completeMissions(1), ModMessages.tr("message.cronicasrp.progress.mission")))
                .then(Commands.argument(w.quantity(), IntegerArgumentType.integer(1, RpProgress.MAX_COUNTER)).executes(context -> progress(context.getSource(),
                        EntityArgument.getPlayer(context, w.player()), rp -> rp.completeMissions(IntegerArgumentType.getInteger(context, w.quantity())),
                        ModMessages.tr("message.cronicasrp.progress.missions"))))));
        var medals = Commands.argument(w.player(), EntityArgument.player());
        for (var medal : new RpProgress.Medal[]{RpProgress.Medal.BRONZE, RpProgress.Medal.SILVER, RpProgress.Medal.GOLD}) {
            String literal = w.english() ? switch (medal) { case SILVER -> "silver"; case GOLD -> "gold"; default -> "bronze"; } : medal.id();
            medals.then(Commands.literal(literal).executes(context -> progress(context.getSource(), EntityArgument.getPlayer(context, w.player()),
                    rp -> rp.award(medal), ModMessages.tr("message.cronicasrp.progress.medal", ModMessages.label("medal", medal.id())))));
        }
        admin.then(Commands.literal(w.medal()).then(medals));
    }
    private static int progress(CommandSourceStack source, ServerPlayer player, UnaryOperator<RpProgress> change, Component message) {
        try {
            var profile = player.getData(ModAttachments.PROFILE);
            player.setData(ModAttachments.PROFILE, profile.withProgress(change.apply(profile.progress())));
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
            source.sendSuccess(() -> ModMessages.tr("message.cronicasrp.progress.updated", message, profile.name()), true); return 1;
        } catch (IllegalArgumentException ex) { source.sendFailure(ModMessages.error(ex.getMessage())); return 0; }
    }
    private static int pay(CommandSourceStack source, ServerPlayer sender, ServerPlayer receiver, String value) {
        if (sender.getUUID().equals(receiver.getUUID())) { source.sendFailure(ModMessages.tr("error.cronicasrp.other_player")); return 0; }
        try {
            long amount = Money.positive(value);
            var result = Wallet.transfer(sender.getData(ModAttachments.PROFILE), receiver.getData(ModAttachments.PROFILE), amount);
            sender.setData(ModAttachments.PROFILE, result.sender()); receiver.setData(ModAttachments.PROFILE, result.receiver());
            ProfileNetwork.send(sender, ProfilePackets.SYNC, ""); ProfileNetwork.send(receiver, ProfilePackets.SYNC, "");
            source.sendSuccess(() -> ModMessages.tr("message.cronicasrp.payment.sent", ModMessages.money(amount), result.receiver().name()), false);
            receiver.sendSystemMessage(ModMessages.tr("message.cronicasrp.payment.received", ModMessages.money(amount), result.sender().name())); return 1;
        } catch (IllegalArgumentException ex) { source.sendFailure(ModMessages.error(ex.getMessage())); return 0; }
    }
    private static int grant(CommandSourceStack source, ServerPlayer player, String value) {
        var profile = player.getData(ModAttachments.PROFILE);
        if (!profile.created()) { source.sendFailure(ModMessages.tr("error.cronicasrp.character_missing")); return 0; }
        try {
            long amount = Money.positive(value);
            player.setData(ModAttachments.PROFILE, profile.withBalance(Math.addExact(profile.balance(), amount)));
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
            source.sendSuccess(() -> ModMessages.tr("message.cronicasrp.payment.granted", ModMessages.money(amount), profile.name()), true); return 1;
        } catch (ArithmeticException | IllegalArgumentException ex) { source.sendFailure(ModMessages.error(ex.getMessage())); return 0; }
    }
    private static int openCash(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        ProfileNetwork.send(player, player.getData(ModAttachments.PROFILE).created() ? ProfilePackets.CASH : ProfilePackets.CREATE, ""); return 1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> piecesCommand(String name, int action, Words w) {
        return Commands.literal(name).then(Commands.argument(w.item(), StringArgumentType.word()).suggests((context, builder) -> {
            for (var denomination : CurrencyDenomination.values()) if (denomination.alias().startsWith(builder.getRemaining())) builder.suggest(denomination.alias());
            return builder.buildFuture();
        }).then(Commands.argument(w.quantity(), IntegerArgumentType.integer(1, 1_000_000)).executes(context -> exchange(context.getSource(),
                new CashPackets.Request(action, 0, StringArgumentType.getString(context, w.item()), IntegerArgumentType.getInteger(context, w.quantity()))))));
    }
    private static int exchangeValue(CommandSourceStack source, int action, String value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try { return exchange(source, new CashPackets.Request(action, Money.positive(value), "", 0)); }
        catch (IllegalArgumentException ex) { source.sendFailure(ModMessages.error(ex.getMessage())); return 0; }
    }
    private static int exchange(CommandSourceStack source, CashPackets.Request request) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            Component message = CashService.perform(source.getPlayerOrException(), request);
            source.sendSuccess(() -> message, false); return 1;
        } catch (IllegalArgumentException ex) { source.sendFailure(ModMessages.error(ex.getMessage())); return 0; }
    }
}
