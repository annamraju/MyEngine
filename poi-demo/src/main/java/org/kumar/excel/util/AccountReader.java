/**
 * 
 */
package org.kumar.excel.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public class AccountReader {

	String _acctPath;

	public AccountReader() {
		_acctPath = "c:\\users\\annam\\eclipse-workspace\\myexcelproject\\resources\\";
	}
	
	public AccountReader(String metaDataFilePath) {
		_acctPath = metaDataFilePath;
	}
	
	public List<Account> readMetaData() throws Exception{
		return readMetaData("Account.metadata");
	}
	
	public List<Account> readMetaData(String fileName) throws Exception {
		List<Account> allAccounts =  null;
		File metaDataFile =  new File(_acctPath, fileName);
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
