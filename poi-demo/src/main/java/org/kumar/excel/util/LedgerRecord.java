package org.kumar.excel.util;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;

public class LedgerRecord {

    private String _account;
    private short _order;
    private short _year;
    private Month _monEnum;
    private Date _transDate;
    private String _description;
    private double _amount;
    private double _balance;

    private String _category;
    private String _sub1;
    private String _sub2;
    private String _sub3;
    private String _sub4;

    // =========================
    // NEW FIELDS
    // =========================
    private String _merchantId;   // NEW
    private String _merchant; //canonical name
    private Integer _ruleNo;      // NEW

    public LedgerRecord() {
    }

    public LedgerRecord(String account, short order, short year, Month monEnum, Date transDate, String desc, double amount, double bal,
			String category, String sub1, String sub2, String sub3, String sub4) {
		_account = account;
		_order = order;
		_year =  year;
		_monEnum = monEnum;
		_transDate =  transDate;
		_description = desc;
		_amount = amount;
		_balance = bal;
		_category = category;
		_sub1 =  sub1;
		_sub2 =  sub2;
		_sub3 =  sub3;
		_sub4 =  sub4;
	}

    public LedgerRecord(String account, short order, short year, String mon, Date transDate, String desc, double amount, double bal,
    					String category, String sub1, String sub2, String sub3, String sub4) {
    	_account = account;
    	_order = order;
    	_year =  year;
    	_monEnum = Month.valueOf(mon.trim().toUpperCase());
    	_transDate =  transDate;
    	_description = desc;
    	_amount = amount;
    	_balance = bal;
    	_category = category;
    	_sub1 =  sub1;
    	_sub2 =  sub2;
    	_sub3 =  sub3;
    	_sub4 =  sub4;
    }
    
    public LedgerRecord(LedgerRecord rec) {
		_account = rec._account;
		_order = rec._order;
		_year =  rec._year;
		_monEnum = rec._monEnum;
		_transDate =  rec._transDate;
		_description = rec._description;
		_amount = rec._amount;
		_balance = rec._balance;
		_category = rec._category;
		_sub1 =  rec._sub1;
		_sub2 =  rec._sub2;
		_sub3 =  rec._sub3;
		_sub4 =  rec._sub4;
    	
    }
    // -------------------------
    // Existing getters/setters
    // -------------------------
    public String getAccount() {
        return _account;
    }

    public void setAccount(String account) {
        this._account = account;
    }

    public short getOrder() {
    	return _order;
    }
    public void setOrder(short or) {
    	_order =  or;
    }
    public short getYear() {
    	return _year;
    }
    public void setYear(short year) {
    	_year = year;
    }
    public Month getMonEnum() {
    	return _monEnum;
    }
    public String getMonthFullName() {
    	return _monEnum.getDisplayName(TextStyle.FULL, Locale.US);
    }
    public String getMonthShortName() {
    	return _monEnum.getDisplayName(TextStyle.SHORT, Locale.US);
    }
    public short getMonthNum() {
    	return (short)(_monEnum.getValue());
    }
    public Date getTransDate() {
        return _transDate;
    }

    public void setTransDate(Date transDate) {
        this._transDate = transDate;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        this._description = description;
    }

    public double getAmount() {
        return _amount;
    }

    public void setAmount(double amount) {
        this._amount = amount;
    }

    public double getBalance() {
    	return _balance;
    }
    public void setBalance(double bal) {
    	_balance = bal;
    }
    public String getCategory() {
        return _category;
    }

    public void setCategory(String category) {
        this._category = category;
    }

    public String getSub1() {
        return _sub1;
    }

    public void setSubCategory1(String sub1) {
        this._sub1 = sub1;
    }

    public String getSub2() {
        return _sub2;
    }

    public void setSubCategory2(String sub2) {
        this._sub2 = sub2;
    }

    public String getSub3() {
        return _sub3;
    }

    public void setSubCategory3(String sub3) {
        this._sub3 = sub3;
    }

    public String getSub4() {
        return _sub4;
    }

    public void setSubCategory4(String sub4) {
        this._sub4 = sub4;
    }

    // =========================
    // NEW getters/setters
    // =========================
    public String getMerchant() {
    	return _merchant;
    }
    public String getMerchantId() {               // NEW
        return _merchantId;
    }

    public void setMerchant(String merchant) {
    	this._merchant = merchant;
    }
    
    public void setMerchantId(String merchantId) { // NEW
        this._merchantId = merchantId;
    }

    public Integer getRuleNo() {                   // NEW
        return _ruleNo;
    }

    public void setRuleNo(Integer ruleNo) {        // NEW
        this._ruleNo = ruleNo;
    }

    @Override
    public String toString() {
        return "LedgerRecord{" +
                "account='" + _account + '\'' +
                ", transDate=" + _transDate +
                ", description='" + _description + '\'' +
                ", amount=" + _amount +
                ", merchantId='" + _merchantId + '\'' +   // NEW
                ", ruleNo=" + _ruleNo +                   // NEW
                ", category='" + _category + '\'' +
                ", sub1='" + _sub1 + '\'' +
                ", sub2='" + _sub2 + '\'' +
                ", sub3='" + _sub3 + '\'' +
                ", sub4='" + _sub4 + '\'' +
                '}';
    }
}
