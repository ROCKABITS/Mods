package pt.lawsjustice.domain;

import java.math.BigDecimal;
import java.util.Locale;

/** Human amounts at the boundary; persistent accounting always uses positive integer units. */
public final class FinanceAmounts {
    private FinanceAmounts(){}
    public static long parse(String value,String currency){
        if(value==null||value.length()>22||!value.matches("[0-9]+([.,][0-9]{1,2})?")||!(currency.equals("CHRONICLES")||currency.equals("EMERALD")))throw bad();
        try{BigDecimal decimal=new BigDecimal(value.replace(',','.'));long amount=(currency.equals("CHRONICLES")?decimal.movePointRight(2):decimal).longValueExact();if(amount<=0||amount>FinanceLedger.MAX_AMOUNT)throw bad();return amount;}catch(ArithmeticException invalid){throw bad();}
    }
    public static String format(long amount,String currency){return currency.equals("CHRONICLES")?String.format(Locale.ROOT,"%,d.%02d €",amount/100,amount%100):Long.toString(amount);}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("lawsjustice.error.finance_amount");}
}
