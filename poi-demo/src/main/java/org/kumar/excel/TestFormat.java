package org.kumar.excel;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.text.DecimalFormatSymbols;
import java.text.DecimalFormat;

public class TestFormat {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		NumberFormat df = NumberFormat.getCurrencyInstance();
		DecimalFormatSymbols dfs = new DecimalFormatSymbols();
		dfs.setCurrencySymbol("$");
		dfs.setGroupingSeparator('.');
		dfs.setMonetaryDecimalSeparator('.');
		((DecimalFormat) df).setDecimalFormatSymbols(dfs);
		//df.setMaximumFractionDigits(0);
		System.out.println(df.format(3333454));

		Locale indiaLocale = Locale.of("hi", "IN");
		
    	NumberFormat formatter  = NumberFormat.getCurrencyInstance(indiaLocale);
    	System.out.println("indian currency is - " + "\u20B9" + formatter.format(333345566));
    	
    	try {
    	String string = "\u20B9";
        byte[] utf8 = string.getBytes("UTF-8");

        string = new String(utf8, "UTF-8");
        System.out.println(string);
    	}catch(Exception e) {
    		e.printStackTrace();
    	}
    	
    	NumberFormat us = NumberFormat.getCurrencyInstance(Locale.US);
    	NumberFormat india = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    	NumberFormat china = NumberFormat.getCurrencyInstance(Locale.CHINESE);
    	NumberFormat france = NumberFormat.getCurrencyInstance(Locale.FRANCE);

    	double payment = 234556778;
    	
    	System.out.println("US: " + us.format(payment));
    	System.out.println("India: " + india.format(payment).substring(india.format(payment).indexOf('\u20B9') + 1));
    	System.out.println("China: \uFFE5" + china.format(payment).substring(china.format(payment).indexOf('\u0020') + 1));
    	System.out.println("France: " + france.format(payment));
    	
    	
    	double currencyAmt = 123456778.90;
    	Currency currentCurrency = Currency.getInstance(indiaLocale);
    	NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(indiaLocale);
    	System.out.println(indiaLocale.getDisplayName() + ", " + currentCurrency.getDisplayName() + ", " + currencyFormatter.format(currencyAmt));
    	
	}

}
