package org.kumar.excel.util;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;

import org.apache.logging.log4j.Logger;
import org.kumar.excel.MyTestexcelReadWrite;

public class DateWiseFinSummary {
	private Date _date;
	private String _dateLabel;
	private Map<Account, Double> _finData;
	
	public  DateWiseFinSummary(Date date, String dateLabel) {
		_date =  date;
		_dateLabel = dateLabel;
		_finData =  new HashMap<Account, Double>();
	}
	
	public void addData(Account account, Double value) {
		if(_finData == null)
			_finData = new HashMap<Account,Double>();
		_finData.put(account,  value);
	}
	
	public String toString() {
		return "this is the finSummary for " + _dateLabel;
	}
	
	public List<Account> getAllAccounts(){
		List<Account> allAccounts = null;
		if(_finData != null) {
			allAccounts = new ArrayList<Account>(_finData.keySet());
		}
		return allAccounts;
	}
	
	public Double getAccountValue(Account acct) {
		Double acctValue = null;
		if(_finData == null) 
			return null;
		if( acct == null)
			return null;
		if(_finData.containsKey(acct)) {
			acctValue =  (Double)_finData.get(acct);
		}else {
			return null;
		}
		return acctValue;
	}
	
	public void print() {
		Logger LOGGER = MyTestexcelReadWrite.getLogger();
		LOGGER.debug("Date " + _dateLabel + " also " + _date.toString());
		Iterator<Account> iter =  _finData.keySet().iterator();
		NumberFormat formatter=NumberFormat.getCurrencyInstance(Locale.US);  
		while(iter.hasNext()) {
			Account account = (Account)iter.next();
			if(account.getCurrency().equalsIgnoreCase("USD")) {
				formatter =  NumberFormat.getCurrencyInstance(Locale.US);
			}else if (account.getCurrency().equalsIgnoreCase("INR")) {
				Locale indiaLocale = Locale.of("hi", "IN");
		    	formatter  = NumberFormat.getCurrencyInstance(indiaLocale);
			}else {
				//unknown currency
			}
			if(_finData.containsKey(account)) {
				Double dbl =  (Double)_finData.get(account);
				String currency=formatter.format(dbl);  
				LOGGER.debug("Account Type : " + account.getType() + " Institution : " + account.getInstitution() + " Name: " + account.getName() + " Value =  " + currency );
			}else {
				LOGGER.debug("value not found");
			}
		}
	}
	
	public void print(MyTestexcelReadWrite obj) {
		Logger LOGGER = MyTestexcelReadWrite.getLogger();
		LOGGER.debug("Date " + _dateLabel + " also " + _date.toString());
		Iterator<Account> iter =  _finData.keySet().iterator();
		NumberFormat formatter=NumberFormat.getCurrencyInstance(Locale.US);  
		while(iter.hasNext()) {
			Account account = (Account)iter.next();
			if(account.getCurrency().equalsIgnoreCase("USD")) {
				formatter =  NumberFormat.getCurrencyInstance(Locale.US);
			}else if (account.getCurrency().equalsIgnoreCase("INR")) {
				Locale indiaLocale = Locale.of("hi", "IN");
		    	formatter  = NumberFormat.getCurrencyInstance(indiaLocale);
			}else {
				//unknown currency
			}
			if(_finData.containsKey(account)) {
				Double dbl =  (Double)_finData.get(account);
				String currency=formatter.format(dbl);  
				LOGGER.debug("Account Type : " + account.getType() + " Institution : " + account.getInstitution() + " Name: " + account.getName() + " Value =  " + currency  + " Is this std one ; " + (obj.isThisAccountAStandardOne(account)));
			}else {
				LOGGER.debug("value not found");
			}
		}
	}
}
