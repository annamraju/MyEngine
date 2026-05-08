package org.kumar.excel;

import java.time.Month;

public class TestMonthEnum {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String mon = "January";
		Month m = Month.valueOf(mon.toUpperCase());
		System.out.println("month string s " + mon + " AND eNUM IS " + m);
	}

}
