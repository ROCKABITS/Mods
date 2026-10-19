package pt.lawsjustice.domain;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class FinanceAmountsTest {
 @Test void realWalletAmountsConvertExactlyWithPortugueseAndEnglishDecimalSeparators(){assertEquals(2001,FinanceAmounts.parse("20,01","CHRONICLES"));assertEquals(2001,FinanceAmounts.parse("20.01","CHRONICLES"));assertEquals(20,FinanceAmounts.parse("20","EMERALD"));assertEquals(FinanceLedger.MAX_AMOUNT,FinanceAmounts.parse("9000000000000000.00","CHRONICLES"));}
 @Test void ambiguousAndOverflowingOrNegativePaymentsCannotReachAWallet(){for(String value:new String[]{"0","-1","1e3","+20","NaN","Infinity","20.001","1 000","9000000000000000.01"})assertThrows(IllegalArgumentException.class,()->FinanceAmounts.parse(value,"CHRONICLES"));assertThrows(IllegalArgumentException.class,()->FinanceAmounts.parse("1.5","EMERALD"));assertThrows(IllegalArgumentException.class,()->FinanceAmounts.parse("1","UNKNOWN"));}
}
