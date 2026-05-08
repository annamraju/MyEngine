/**
 * 
 */
package org.kumar.excel.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/**
 * 
 */
public class BalanceRecord {

	static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
	
	Date _dt;
	String _dateString = "";
	int _year;
	int _month;
	Map<Account, Double> _balance = null;
	private static final NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.US);


	public String getDateString() { return _dateString;}
	public String getBalanceString(Account act) {
		Double val =  getBalance(act);
		return 	formatter.format(val);
	}
	public Double getBalance(Account act) {
		Double  bal =  0.0;
		if(_balance.containsKey(act)) {
			bal =  _balance.get(act);
		}else {
			System.out.println(" act " + act + " not found in balances");
		}
		return bal;
	}
	/**
	 * A constructor that takes a date string ( mm/dd/yyyy ), an account type object and a balance value 
	 * @param dtString
	 * @param act
	 * @param Bal
	 */
	public BalanceRecord(String dtString, Account act, Double Bal) {
		_dateString = dtString;
		if(dtString != null && !(dtString.trim().isEmpty())) {
			try {
				_dt = sdf.parse(dtString);
			}catch(ParseException e) {
				_dt =  new Date(0L);
			}
		}
		if(_balance == null) _balance =  new HashMap <Account, Double>();
		if(act != null) {
			if(!_balance.containsKey(act)) {
				_balance.put(act , Bal);
			}
		}
	}
	
	/**
	 * another constructor
	 * @param dt
	 * @param act
	 * @param Bal
	 */
	public BalanceRecord(Date dt, int monthNum, int yearNum, Account act, Double Bal) {
		_dt = dt;
		_dateString =  sdf.format(dt);
		_month = monthNum;
		_year = yearNum;
		if(_balance == null) _balance =  new HashMap <Account, Double>();
		if(act != null) {
			if(!_balance.containsKey(act)) {
				_balance.put(act , Bal);
			}
		}
	}

	/**
	 * This is to add an account with a balance
	 * @param act
	 * @param bal
	 */
	public void addActBalance(Account act, double bal) {
		if(_balance == null) _balance =  new HashMap <Account, Double>();
		_balance.put(act, bal);
	}

	public String toString() {
		StringBuffer buf =  new StringBuffer();
		buf.append("Date : " + _dateString + "\r");
		for (Map.Entry<Account, Double> entry : _balance.entrySet()) {
			Account key = entry.getKey();
		    Double value = entry.getValue();
		    buf.append("\t" + key + ":" + value.toString() + "\r");
		}
		return buf.toString();
	}
	
	public String specialToString() {
		StringBuffer buf =  new StringBuffer();
		buf.append("Date : " + _dateString + "\r");
		for (Map.Entry<Account, Double> entry : _balance.entrySet()) {
			Account key = entry.getKey();
		    Double value = entry.getValue();
		    if(key.getInstitution().contains("Total"))
		    	buf.append("\t" + key + ":" + value.toString() + "\r");
		}
		return buf.toString();
	}	
	
	public int getMonth() {return _month;}
	public int getYear() { return _year;}
			
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
