package org.kumar.excel.util;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Iterator;

public class AllAccounts {
	private List<Account> _allAccounts;
	public AllAccounts() {
		_allAccounts =  new ArrayList<Account>();
	}
	
	public List<Account> getAllAccounts(){
		return _allAccounts;
	}
	
	public void loadAccountsFromFile(String path, String fileName)throws Exception {
		File metaDataFile =  new File(path, fileName);
		
		BufferedReader br = null;
		try {
			br= new BufferedReader(new FileReader(metaDataFile));
			String line = br.readLine();
			while(line !=  null) {
				line = br.readLine();
				if(line == null) break;
				String[] values =  line.split(",");
				// this is the format
				// Cash,GTE fcu,Checking, Kumar & Padma, USD
				Account acct =  new Account(
								AccountType.getAccountType(values[0]),
								values[1],
								values[2],
								values[3],
								values[4]
								);
				if(_allAccounts == null) _allAccounts = new ArrayList<Account>();
				_allAccounts.add(acct);	
			}
		}catch(Exception e) {
			if(br != null) br.close();
		}
	}
	
	public void printAccounts() {
		Iterator<Account> iter = _allAccounts.iterator();
		while(iter.hasNext()) {
			Account acct =  iter.next();
			System.out.println(acct.toString());
		}
	}
	
	public static void main(String[] args) {
		String path =  "c:\\users\\annam\\eclipse-workspace\\poi-demo\\src\\main\\resources\\";
		String fileName = "Account.metadata";
		try {
			AllAccounts all = new AllAccounts();
			all.loadAccountsFromFile(path, fileName);
			all.printAccounts();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
}
