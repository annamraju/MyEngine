package org.kumar.excel;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TestSimpleDateFormat {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String pattern1 = "YYYY-MM-dd";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern1);
		String date1 = simpleDateFormat.format(new Date());
		System.out.println("Week Number: "+date1);
		
		String pattern2 = "yyyy-MM-dd";
		SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat(pattern2);
		String date2 = simpleDateFormat2.format(new Date());
		System.out.println("Week Number: "+date2);


	}

}
