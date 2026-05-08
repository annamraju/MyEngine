package org.kumar.excel.util;

import org.kumar.excel.exception.DateWiseFinParsingException;

public enum AccountType {
	Cash,
	CD,
	Stocks,
	Crypto,
	Retirement,
	Education,
	HSA,
	Insurance,
	Overseas,
	BusinessAccounts,
	Unknown,
	Donations,
	Investments,
	Assets,
	Liabilities,
	RealEstate,
	None;
	
	public static AccountType getAccountType(String cellValue) throws DateWiseFinParsingException{
		AccountType accountType;
		if(cellValue.equalsIgnoreCase("Cash")) accountType = AccountType.Cash;
		else if(cellValue.equalsIgnoreCase("CDs")) accountType =  AccountType.CD;
		else if(cellValue.equalsIgnoreCase("Stocks")) accountType =  AccountType.Stocks;
		else if(cellValue.equalsIgnoreCase("Crypto")) accountType =  AccountType.Crypto;
		else if(cellValue.equalsIgnoreCase("Retirement")) accountType =  AccountType.Retirement;
		else if(cellValue.equalsIgnoreCase("Education")) accountType =  AccountType.Education;
		else if(cellValue.equalsIgnoreCase("HSA")) accountType =  AccountType.HSA;
		else if(cellValue.equalsIgnoreCase("Overseas")) accountType =  AccountType.Overseas;
		else if(cellValue.equalsIgnoreCase("Insurance")) accountType =  AccountType.Insurance;
		else if(cellValue.equalsIgnoreCase("BusinessAccounts")) accountType =  AccountType.BusinessAccounts;		
		else if(cellValue.equalsIgnoreCase("Donations")) accountType =  AccountType.Donations;		
		else if(cellValue.equalsIgnoreCase("Investments")) accountType =  AccountType.Investments;		
		else if(cellValue.equalsIgnoreCase("Assets")) accountType =  AccountType.Assets;		
		else if(cellValue.equalsIgnoreCase("Liabilities")) accountType =  AccountType.Liabilities;		
		else if(cellValue.equalsIgnoreCase("RealEstate")) accountType =  AccountType.RealEstate;		
		else if(cellValue.equalsIgnoreCase("Total")) accountType = AccountType.None;
		else {
			accountType =  AccountType.Unknown;
			throw new DateWiseFinParsingException("this is not possible -  account type - " + cellValue);
		}
		return accountType;
	}
}
