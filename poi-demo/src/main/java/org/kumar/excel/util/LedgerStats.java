/**
 * 
 */
package org.kumar.excel.util;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

/**
 * 
 */
public class LedgerStats {
	
	int _totalCount;
	Map<Integer, Integer> _yearlyCount;
	Map<Integer, Integer> _monthlyCount;
	Map<String, Integer> _categoryCount;
	Map<String, Integer> _sub1Count;
	Map<String, Integer> _sub2Count;
	Map<String, Integer> _sub3Count;
	Map<String, Integer> _sub4Count;
	
	public LedgerStats() {
		_totalCount = 0;
		_yearlyCount = null;
		_monthlyCount = null;
		_categoryCount = null;
		_sub1Count = null;
		_sub2Count = null;
	 	_sub3Count =  null;
		_sub4Count = null;	
	}
	
	public static void updateStatMap(Integer key, Map<Integer, Integer> statMap) {
		if(key == null) {
			Integer blankKey = 99999;
			if(statMap.containsKey(blankKey)) {
				Integer value = statMap.get(blankKey);
				value = value + 1;
				statMap.put(blankKey, value);
			}else {
				statMap.put(blankKey, 1);
			}
		}else if(statMap.containsKey(key)) {
			Integer value =  statMap.get(key);
			value = value + 1;
			statMap.put(key, value);
		}else {
			statMap.put(key, 1);
		}
	}

	public static void updateStatMap(String key, Map<String, Integer> statMap) {
		if(key == null || key.trim().isEmpty()) {
			String blankKey = "BLANK";
			if(statMap.containsKey(blankKey)) {
				Integer value = statMap.get(blankKey);
				value = value + 1;
				statMap.put(blankKey, value);
			}else {
				statMap.put(blankKey, 1);
			}
		}else if(statMap.containsKey(key)) {
			Integer value =  statMap.get(key);
			value = value + 1;
			statMap.put(key, value);
		}else {
			statMap.put(key, 1);
		}
	}

	public void buildStats(List<LedgerRecord> recs) {
		Iterator<LedgerRecord> iter = recs.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec =  (LedgerRecord)(iter.next());
			if(rec == null) continue;
			
			_totalCount++;
			
			if(_yearlyCount == null)  _yearlyCount = new HashMap<Integer,Integer>();
			Integer keyYear = Integer.valueOf((int) rec.getYear());
			LedgerStats.updateStatMap(keyYear, _yearlyCount);
			
			if(_monthlyCount == null)  _monthlyCount = new HashMap<Integer,Integer>();
			Integer keyMonth = Integer.valueOf((int) rec.getMonthNum());
			LedgerStats.updateStatMap(keyMonth, _monthlyCount);
			
			if(rec.getYear() == 2025) continue;
			
			if(_categoryCount == null)  _categoryCount = new HashMap<String,Integer>();
			LedgerStats.updateStatMap(rec.getCategory(), _categoryCount);
			
			if(_sub1Count == null)  _sub1Count = new HashMap<String,Integer>();
			LedgerStats.updateStatMap(rec.getSub1(), _sub1Count);
			
			if(_sub2Count == null)  _sub2Count = new HashMap<String,Integer>();
			LedgerStats.updateStatMap(rec.getSub2(), _sub2Count);

			if(_sub3Count == null)  _sub3Count = new HashMap<String,Integer>();
			LedgerStats.updateStatMap(rec.getSub3(), _sub3Count);
			
			if(_sub4Count == null)  _sub4Count = new HashMap<String,Integer>();
			LedgerStats.updateStatMap(rec.getSub4(), _sub4Count);
		}
	}

	@Override
	public String toString() {
	    return "LedgerStats {\n" +
	            "  _totalCount=" + _totalCount + ",\n" +
	            "  _yearlyCount=" + _yearlyCount + ",\n" +
	            "  _monthlyCount=" + _monthlyCount + ",\n" +
	            "  _categoryCount=" + _categoryCount + ",\n" +
	            "  _sub1Count=" + _sub1Count + ",\n" +
	            "  _sub2Count=" + _sub2Count + ",\n" +
	            "  _sub3Count=" + _sub3Count + ",\n" +
	            "  _sub4Count=" + _sub4Count + "\n" +
	            "}";
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
