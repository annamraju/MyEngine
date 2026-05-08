package org.kumar.marcus;

import java.util.Date;
import java.text.SimpleDateFormat;

public class MarcusTransactionRecord {
	
	private Date _Date;
	private String _Description;
	private double _Amt;
	private double _Balance;
	
	public MarcusTransactionRecord(Date date, String desc, double amt, double bal ) {
		_Date = date;
		_Description = desc;
		_Amt =  amt;
		_Balance = bal;
	}
	
	public String toString() {
		String pattern = "MM-dd-yyyy";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		return "Date : " + simpleDateFormat.format(_Date) + " Desc : " + _Description  + " Amt : " + _Amt + " Bal : " + _Balance;
	}
	
	public Date getDate() { return _Date;}
	public String getDescription() { return _Description;}
	public double getAmt() { return _Amt;}
	public double getBalance() { return _Balance;}

}
