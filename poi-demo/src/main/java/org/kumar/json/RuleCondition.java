package org.kumar.json;

import org.kumar.excel.util.*;
import java.util.Arrays;

public class RuleCondition {
	
	SimpleCondition[] _conditions;

	public RuleCondition(SimpleCondition con) {
		_conditions = new SimpleCondition[10];
		_conditions[0] = con; 
	}
	
	public RuleCondition(SimpleCondition[] con) {
		_conditions = Arrays.copyOf(con, con.length);
	}
		
	public boolean applyCondition(LedgerRecord rec) {
		boolean result =  false;
		
		if(_conditions.length == 0) return result;
		if(rec == null) return result;
		
		for(int i = 0; i < _conditions.length; i++) {
			SimpleCondition con = _conditions[i];
			if(!con.applyCondition(rec))
				return result;
		}
		return true;
	}
	
	public String toString() {
		StringBuffer buf =  new StringBuffer();
		for(int i = 0; i < _conditions.length; i++) {
			if(i!=0) buf.append( " and ");
			buf.append((_conditions[i]).toString());
		}
		return buf.toString();
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
