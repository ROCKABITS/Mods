package pt.cronicasrp.network;

import io.netty.buffer.Unpooled;
import pt.cronicasrp.i18n.ModMessages;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CashPacketsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraft() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void allCashActionsRoundtripWithExactCentsAndNoTrailingBytes() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            for (int action = 0; action <= 4; action++) {
                var request = new CashPackets.Request(action, 12345678901234567L, "note_500_euros", 2304);
                CashPackets.Request.CODEC.encode(buf, request); assertEquals(request, CashPackets.Request.CODEC.decode(buf));
                assertEquals(0, buf.readableBytes());
            }
            var result = new CashPackets.Result(false, ModMessages.error("Não há espaço no inventário para o dinheiro ou troco. Liberta algumas posições."));
            CashPackets.Result.CODEC.encode(buf, result); assertEquals(result, CashPackets.Result.CODEC.decode(buf)); assertEquals(0, buf.readableBytes());
            var notice = new CashPackets.Result(true, ModMessages.tr("message.cronicasrp.cash.withdrawn", ModMessages.money(1250)));
            CashPackets.Result.CODEC.encode(buf, notice);
            assertEquals(notice, CashPackets.Result.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
}
