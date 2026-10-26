package pt.crownledger.data;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import pt.crownledger.domain.Loans;
import pt.crownledger.domain.Treasury;

/** Explicit bounded receipts; malformed or downgraded financial state never becomes an empty bank. */
public final class LoanCodec {
    private static final Gson JSON=new Gson();
    public static byte[] encode(Object value){var bytes=JSON.toJson(value).getBytes(StandardCharsets.UTF_8);if(bytes.length>6_000_000)throw error();return bytes;}
    private static <T>T decode(byte[] bytes,Class<T> type){try{if(bytes.length<2||bytes.length>6_000_000)throw error();return Objects.requireNonNull(JSON.fromJson(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(),type));}catch(Exception e){throw new IllegalStateException("Invalid public loan data",e);}}
    public static Loans loans(byte[] bytes){return new Loans(decode(bytes,Loans.Snapshot.class));}
    public static Treasury treasury(byte[] bytes){return new Treasury(decode(bytes,Treasury.Snapshot.class));}
    public static void validateIntent(TreasuryData data,CompoundTag intent,HolderLookup.Provider lookup){
        if(!intent.getAllKeys().equals(Set.of("id","loan","player","name","currency","credit","amount","before","after","at","inventoryBefore","inventoryAfter","loansAfter","treasuryAfter")))throw error();
        for(String k:List.of("id","loan","player"))if(!intent.hasUUID(k))throw error();
        for(String k:List.of("name","currency"))if(!intent.contains(k,Tag.TAG_STRING))throw error();
        for(String k:List.of("amount","before","after","at"))if(!intent.contains(k,Tag.TAG_LONG)||intent.getLong(k)<0)throw error();
        if(!intent.contains("credit",Tag.TAG_BYTE)||!intent.contains("loansAfter",Tag.TAG_BYTE_ARRAY)||!intent.contains("treasuryAfter",Tag.TAG_BYTE_ARRAY))throw error();
        long amount=intent.getLong("amount");if(amount<1||intent.getLong("after")!=Math.addExact(intent.getLong("before"),intent.getBoolean("credit")?amount:-amount))throw error();
        var loan=data.loans.get(intent.getUUID("loan"));if(!loan.borrower().equals(intent.getUUID("player"))||!loan.currency().name().equals(intent.getString("currency")))throw error();
        var expected=data.loans.copy();var funds=data.treasury.copy();
        if(intent.getBoolean("credit")){if(amount!=loan.principal())throw error();expected.accept(loan.id(),loan.borrower(),intent.getString("name"),intent.getLong("at"));}
        else expected.repay(funds,loan.id(),loan.borrower(),intent.getString("name"),amount,intent.getLong("at"));
        var after=loans(intent.getByteArray("loansAfter"));if(!expected.snapshot().loans().equals(after.snapshot().loans())||expected.lastObservedAt()!=after.lastObservedAt()||!funds.snapshot().equals(treasury(intent.getByteArray("treasuryAfter")).snapshot()))throw error();
        for(String key:List.of("inventoryBefore","inventoryAfter")){
            if(!intent.contains(key,Tag.TAG_LIST)||intent.getList(key,Tag.TAG_COMPOUND).size()!=36)throw error();
            var entries=intent.getList(key,Tag.TAG_COMPOUND);
            for(int i=0;i<36;i++){var entry=entries.getCompound(i);if(!entry.contains("slot",Tag.TAG_INT)||entry.getInt("slot")!=i||!Set.of("slot","item").containsAll(entry.getAllKeys()))throw error();if(entry.contains("item")&&ItemStack.parse(lookup,entry.getCompound("item")).isEmpty())throw error();}
        }
        if(loan.currency()==Treasury.Currency.CHRONICLES&&!intent.getList("inventoryBefore",Tag.TAG_COMPOUND).equals(intent.getList("inventoryAfter",Tag.TAG_COMPOUND)))throw error();
    }
    private static IllegalStateException error(){return new IllegalStateException("realm.treasury.loan.error.recovery");}
    private LoanCodec(){}
}
