package pt.crownledger.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IncomeLedgerTest {
    private static final UUID PAYMENT=new UUID(0,1),PAYER=new UUID(0,2);
    private static IncomeLedger.Receipt receipt(){return new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,500,PAYER,"Fine — Aveline Vale");}
    @Test void acceptingAnIncomeReceiptPreservesItsFullPayload(){var before=new IncomeLedger();var after=before.add(receipt());assertTrue(before.receipts().isEmpty());assertEquals(receipt(),after.receipts().get(PAYMENT));assertTrue(after.contains(receipt()));}
    @Test void replayingTheSameReceiptIsAnIdempotentNoOp(){var ledger=new IncomeLedger().add(receipt());assertSame(ledger,ledger.add(receipt()));assertEquals(1,ledger.receipts().size());}
    @Test void aReplayCannotChangeCurrencyAmountPayerOrDescription(){var ledger=new IncomeLedger().add(receipt());for(var changed:java.util.List.of(new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.EMERALD,500,PAYER,"Fine — Aveline Vale"),new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,501,PAYER,"Fine — Aveline Vale"),new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,500,new UUID(0,3),"Fine — Aveline Vale"),new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,500,PAYER,"Bail — Aveline Vale")))assertThrows(IllegalArgumentException.class,()->ledger.add(changed));}
    @Test void invalidAmountsAndControlCharactersCannotBecomeTreasuryIncome(){for(long amount:new long[]{Long.MIN_VALUE,-1,0,IncomeLedger.MAX_AMOUNT+1})assertThrows(IllegalArgumentException.class,()->new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.EMERALD,amount,PAYER,"Fine"));for(String memo:new String[]{""," ","x".repeat(257),"Forged\nrecord"})assertThrows(IllegalArgumentException.class,()->new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.EMERALD,1,PAYER,memo));}
    @Test void restorationAndReturnedMapsCannotChangeReceiptOwnership(){var source=new LinkedHashMap<UUID,IncomeLedger.Receipt>();source.put(PAYMENT,receipt());var ledger=new IncomeLedger(source);source.clear();assertEquals(1,ledger.receipts().size());assertThrows(UnsupportedOperationException.class,()->ledger.receipts().clear());assertThrows(IllegalArgumentException.class,()->new IncomeLedger(Map.of(PAYER,receipt())));}
    @Test void anOlderReceiptRemainsKnownAfterThePublicHistoryWouldHaveBeenPruned(){var ledger=new IncomeLedger().add(receipt());for(int n=3;n<300;n++)ledger=ledger.add(new IncomeLedger.Receipt(new UUID(0,n),Treasury.Currency.EMERALD,1,PAYER,"Funded income"));assertSame(ledger,ledger.add(receipt()));assertEquals(298,ledger.receipts().size());}
    @Test void fiscalPolicyReceiptsNeverCreateMoneyAndBindTheirRateAndDate(){
        var policy=new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,0,PAYER,"realmvillagereconomy.policy:2500:1000");var ledger=new IncomeLedger().add(policy);assertSame(ledger,ledger.add(policy));
        assertThrows(IllegalArgumentException.class,()->ledger.add(new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,0,PAYER,"realmvillagereconomy.policy:1000:1000")));
        assertThrows(IllegalArgumentException.class,()->new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.EMERALD,0,PAYER,"realmvillagereconomy.policy:2500:1000"));
        assertThrows(IllegalArgumentException.class,()->new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,1,PAYER,"realmvillagereconomy.policy:2500:1000"));
    }
    @Test void malformedOrExcessiveFiscalApprovalsAreNotValidZeroValueIncome(){for(String memo:java.util.List.of("realmvillagereconomyXpolicy:1000:1","realmvillagereconomy.policy:10001:1","realmvillagereconomy.policy:-1:1","realmvillagereconomy.policy:1000:-1"))assertThrows(IllegalArgumentException.class,()->new IncomeLedger.Receipt(PAYMENT,Treasury.Currency.CHRONICLES,0,PAYER,memo));}
}
