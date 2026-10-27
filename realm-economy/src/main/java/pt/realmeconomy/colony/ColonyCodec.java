package pt.realmeconomy.colony;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import net.minecraft.nbt.*;
/** UTF-8 byte arrays avoid NBT strings' 65,535-byte encoded limit for a growing bank ledger. */
public final class ColonyCodec {
 public static ColonyLedger.State read(CompoundTag root,String key){if(!root.contains(key,Tag.TAG_BYTE_ARRAY))throw new IllegalStateException("Missing colony ledger bytes");byte[] bytes=root.getByteArray(key);if(bytes.length>12_000_000)throw new IllegalStateException("Colony ledger capacity exceeded");try{return ColonyLedger.decode(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());}catch(CharacterCodingException e){throw new IllegalStateException("Invalid colony UTF-8",e);}}
 public static void write(CompoundTag root,String key,ColonyLedger.State state){byte[] bytes=ColonyLedger.encode(state).getBytes(StandardCharsets.UTF_8);if(bytes.length>12_000_000)throw new IllegalStateException("Colony ledger capacity exceeded");root.putByteArray(key,bytes);}
}
