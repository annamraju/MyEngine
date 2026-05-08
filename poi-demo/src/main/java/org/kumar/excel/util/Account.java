package org.kumar.excel.util;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import org.kumar.excel.exception.DateWiseFinParsingException;

public class Account {
	private AccountType _type;
	private String _institution;
	private String _name;
	private String _owner;
	private String _currency;
	
	public Account(AccountType type, String institution, String name, String owner) {
		_type = type;
		_institution = institution;
		_name = name;
		_owner = owner;
		_currency = "USD"; //default
	}
	
	public Account(AccountType type, String institution, String name, String owner, String currency) {
		_type = type;
		_institution = institution;
		_name = name;
		_owner = owner;
		_currency = currency;
	}

	public Account(String type, String institution, String name, String owner, String currency) throws Exception{
		_type = AccountType.getAccountType(type);
		_institution = institution;
		_name = name;
		_owner = owner;
		_currency = currency;
	}

	public Account(String[] values) throws Exception{
		try {
			_type = AccountType.getAccountType(values[0]);
			_institution = (values[1]).trim();
			_name =  (values[2]).trim();
			_owner = (values[3]).trim();
			_currency = (values[4]).trim();
		}catch(DateWiseFinParsingException e1) {
			throw e1;
		}
	}

	public Account(List<String> values) throws Exception{
		try {
			_type = AccountType.getAccountType((String)values.get(0));
			_institution = (String)values.get(1);
			_name = (String)values.get(2);
			_owner = (String)values.get(3);
			_currency = (String)values.get(4);
		}catch(DateWiseFinParsingException e1) {
			throw e1;
		}catch(Exception e2) {
			throw e2;
		}
	}
	
	public AccountType getType() {
		return _type;
	}
	
	public String getInstitution() {
		return _institution;
	}
	public String getName() {
		return _name;
	}
	
	public String getOwner() {
		return _owner;
	}
	
	public String getCurrency() {
		return _currency;
	}
	
	@Override
	public String toString() {
		return "Type : " + _type + "; Institution: " + _institution + "; Name : " + _name + " Owner: " + _owner +  " Currency: " + _currency;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(this._type == ((Account)obj)._type && 
		   this._institution.equalsIgnoreCase(((Account)obj)._institution) &&
		   this._name.equalsIgnoreCase(((Account)obj)._name) &&
		   this._owner.equalsIgnoreCase(((Account)obj)._owner ) &&
		   this._currency.equalsIgnoreCase(((Account)obj)._currency))
		return true;
		
		return false;
	}

	@Override
	public int hashCode() {
	    final int prime = 31;
	    int result = 1;
	    result = result * prime + this._type.hashCode();
	    result = result * prime + this._institution.hashCode();
	    result = result * prime + this._name.hashCode();
	    result = result * prime + this._owner.hashCode();
	    result = result * prime + this._currency.hashCode();
	    
	    return result;
	}
	
	public static List<Account> loadAccountsFromFile(String path, String fileName)throws Exception {
		List<Account> allAccounts =  null;
		File metaDataFile =  new File(path, fileName);
		FileReader acctFile = new FileReader(metaDataFile);
		BufferedReader bf =  new BufferedReader(acctFile);
		try {
			String line =  bf.readLine();
			while(line != null) {
				if(line.startsWith("Acct Type")) {
					// ignore this and read the next line
				}else {
					//split
					String[] tokens =  line.split(",");
					Account acct =  new Account(tokens);
					if(allAccounts == null) {
						allAccounts = new ArrayList<Account>();
					}
					//System.out.println("adding Account " + acct);
					allAccounts.add(acct);
				}
				line = bf.readLine();
			}	
		}catch(Exception e) {
			e.printStackTrace();
			throw e;
		}finally {
			bf.close();
		}
		return allAccounts;
	}
}
