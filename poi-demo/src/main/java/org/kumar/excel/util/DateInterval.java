package org.kumar.excel.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class DateInterval {
    private final LocalDate startDate;
    private final LocalDate endDate;

    /**
     * 
     * @param startDate
     * @param endDate
     */
    public DateInterval(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 
     * @return
     */
    public LocalDate getStartDate() {
        return startDate;
    }

    /**
     * 
     * @return
     */
    public LocalDate getEndDate() {
        return endDate;
    }

    /**
     * returns true if the end date is past
     * @return
     */
    public boolean isCycleDone() {
    	return (LocalDate.now()).isAfter(endDate);
    }
    
    /**
     * 
     * @param date
     * @return
     */
    public boolean contains(LocalDate date) {
        return (date.isEqual(startDate) || date.isAfter(startDate)) &&
               (date.isEqual(endDate) || date.isBefore(endDate));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DateInterval)) return false;
        DateInterval that = (DateInterval) o;
        return startDate.equals(that.startDate) &&
               endDate.equals(that.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startDate, endDate);
    }

    @Override
    public String toString() {
        return "DateInterval{" +
               "startDate=" + startDate +
               ", endDate=" + endDate +
               '}';
    }
    
    /**
     * Returns true if this interval is continuous with the given interval.
     * Two intervals are continuous if:
     * - this.endDate + 1 day = other.startDate, OR
     * - other.endDate + 1 day = this.startDate.
     */
    public boolean isContinuousWith(DateInterval other) {
        Objects.requireNonNull(other, "Other interval cannot be null");

        return this.endDate.plusDays(1).isEqual(other.startDate)
            || other.endDate.plusDays(1).isEqual(this.startDate);
    }

	/**
	 * This is to get the next consecutive interval given a date interval ( start date and end date )
	 * @param current
	 * @return
	 */
	public static DateInterval nextConsecutive(DateInterval current, short _cycleStart, short _cycleEnd) {
	    Objects.requireNonNull(current, "Current interval cannot be null");

	    LocalDate start = current.getStartDate();
	    LocalDate end   = current.getEndDate();

	    // Validate that this interval follows the _cycleStart -> _cyceEnd pattern
	    if (start.getDayOfMonth() != _cycleStart || end.getDayOfMonth() != _cycleEnd) {
	        throw new IllegalArgumentException(
	            "DateInterval does not follow " + _cycleStart + " -to-" + _cycleEnd + " cycle: " + current
	        );
	    }

	    LocalDate expectedEnd = start.plusMonths(1).withDayOfMonth(_cycleEnd);
	    if (!end.equals(expectedEnd)) {
	        throw new IllegalArgumentException(
	            "DateInterval is not a valid " + _cycleStart + "-to-" + _cycleEnd + " cycle: " + current
	        );
	    }

	    // Next interval is just one month ahead on both start and end
	    LocalDate nextStart = start.plusMonths(1);   // _cycleStart of next month
	    LocalDate nextEnd   = end.plusMonths(1);     // _cyceEnd of the month after that

	    return new DateInterval(nextStart, nextEnd);
	}

	/**
	 * 
	 * @param current
	 * @return
	 */
	public static DateInterval nextMonthlyConsecutive(DateInterval current) {
	    Objects.requireNonNull(current, "Current interval cannot be null");

	    LocalDate start = current.getStartDate();

	    // Next interval is just one month ahead on both start and end
	    LocalDate nextStart = start.plusMonths(1);   // _cycleStart of next month
	    
	    LocalDate nextEnd   = nextStart.withDayOfMonth(nextStart.lengthOfMonth());;     // _cyceEnd of the month after that

	    return new DateInterval(nextStart, nextEnd);
	}
	
	/**
	 * This is to return the billing cycle interval given a transaction date (in LocalDate)
	 * @param date
	 * @return
	 */
	public static DateInterval getDateInterval(LocalDate date, short _cycleStart, short _cycleEnd) {
		int day = date.getDayOfMonth();
	    LocalDate start;
	    LocalDate end;

	    if (day > _cycleEnd) {
	        // Interval runs from _cyceEnd of this month to _cycleStart of next month
	        start = date.withDayOfMonth(_cycleStart);
	        end   = start.plusMonths(1).withDayOfMonth(_cycleEnd);
	    } else {
	        // Interval runs from _cyceEnd of previous month to _cycleStart of this month
	        end = date.withDayOfMonth(_cycleEnd);
	        LocalDate prevMonth = end.minusMonths(1);
	        start = prevMonth.withDayOfMonth(_cycleStart);
	    }

	    // --- VALIDATION: Ensure interval length <= 31 days ---
	    long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
	    if (days > 31) {
	        throw new IllegalStateException(
	            "DateInterval exceeds 31 days: " + days +
	            " days (" + start + " to " + end + ")"
	        );
	    }

	    return new DateInterval(start, end);		
	}

	/**
	 * 
	 * @param date
	 * @return
	 */
	public static DateInterval getMonthlyDateInterval(LocalDate date) {
		LocalDate start = date.with(TemporalAdjusters.firstDayOfMonth());
		LocalDate end  = date.with(TemporalAdjusters.lastDayOfMonth());
		
	    return new DateInterval(start, end);		
	}
	
	/**
	 * Given a transaction date, it will return the billing cycle
	 * @param utilDate
	 * @return
	 */
	public static DateInterval getDateInterval(Date utilDate, short _cycleStart, short _cycleEnd) {
	    Objects.requireNonNull(utilDate, "Date cannot be null");

	    // Convert java.util.Date -> LocalDate using system timezone
	    LocalDate date = Instant.ofEpochMilli(utilDate.getTime())
	                            .atZone(ZoneId.systemDefault())
	                            .toLocalDate();

	    return DateInterval.getDateInterval(date, _cycleStart, _cycleEnd);
	}

	/**
	 * 
	 * @param utilDate
	 * @return
	 */
	public static DateInterval getMonthlyDateInterval(Date utilDate) {
	    Objects.requireNonNull(utilDate, "Date cannot be null");

	    // Convert java.util.Date -> LocalDate using system timezone
	    LocalDate date = Instant.ofEpochMilli(utilDate.getTime())
	                            .atZone(ZoneId.systemDefault())
	                            .toLocalDate();	
		
	    return getMonthlyDateInterval(date);		
	}
	
	/**
	 * This is to find the missing billing cycles given s list of cycles
	 * @param cycles
	 * @return
	 */
	public static List<DateInterval> findMissingCycles(Set<DateInterval> cycles, short _cycleStart, short _cycleEnd){
		List<DateInterval> missingCycles =  null;

		if (cycles == null || cycles.isEmpty()) {
			return null;
		}
		
		// Convert to list
		List<DateInterval> sorted = new ArrayList<>(cycles);

		// Sort by startDate
		sorted.sort(Comparator.comparing(DateInterval::getStartDate));
		//LOGGER.info(" current intervals are @@@@@@ ");
		//LOGGER.info(sorted);

		// Optional: then iterate
		// check to see if there are any gaps in contiguous cycles
		DateInterval prev = null;
		for(int count =  0; count < sorted.size(); count++) {
			if(prev != null) {
				DateInterval curr =  sorted.get(count);
				if(!prev.isContinuousWith(curr)) {
					//LOGGER.info("missing cycle between "  + prev + " and " + curr);
					// it could be missing 1 cycle or multiple cycles
					DateInterval start = prev;
					do {
						DateInterval nextMissing =  DateInterval.nextConsecutive(start, _cycleStart, _cycleEnd);
						if(nextMissing.equals(curr)) break;
						if(missingCycles == null)
							missingCycles= new ArrayList<DateInterval>();
						missingCycles.add(nextMissing);
						start = nextMissing;
					}while(true);
					
					prev =  curr;
				}else {
					prev = curr;
				}
				prev =  curr;
			}else {
				prev = sorted.get(count);				
			}
		}

		// check to see if the cycles are current
		//latest cycle loaded is
		DateInterval last =  sorted.getLast();
		do {
			DateInterval next = DateInterval.nextConsecutive(last, _cycleStart, _cycleEnd);
			if(next.isCycleDone()) {
				// then its missing
				missingCycles.add(next);
				last = next;
			}else{
				break;
			}
		}while(true);
		
		return missingCycles;
	}
	
	/**
	 * 
	 * @param cycles
	 * @return
	 */
	public static List<DateInterval> findMonthlyMissingCycles(Set<DateInterval> cycles){
		List<DateInterval> missingCycles =  null;

		if (cycles == null || cycles.isEmpty()) {
			return null;
		}
		
		// Convert to list
		List<DateInterval> sorted = new ArrayList<>(cycles);

		// Sort by startDate
		sorted.sort(Comparator.comparing(DateInterval::getStartDate));
		//LOGGER.info(" current intervals are @@@@@@ ");
		//LOGGER.info(sorted);

		// Optional: then iterate
		// check to see if there are any gaps in contiguous cycles
		DateInterval prev = null;
		for(int count =  0; count < sorted.size(); count++) {
			if(prev != null) {
				DateInterval curr =  sorted.get(count);
				if(!prev.isContinuousWith(curr)) {
					//LOGGER.info("missing cycle between "  + prev + " and " + curr);
					// it could be missing 1 cycle or multiple cycles
					DateInterval start = prev;
					do {
						DateInterval nextMissing =  DateInterval.nextMonthlyConsecutive(start);
						if(nextMissing.equals(curr)) break;
						if(missingCycles == null)
							missingCycles= new ArrayList<DateInterval>();
						missingCycles.add(nextMissing);
						start = nextMissing;
					}while(true);
					
					prev =  curr;
				}else {
					prev = curr;
				}
				prev =  curr;
			}else {
				prev = sorted.get(count);				
			}
		}

		// check to see if the cycles are current
		//latest cycle loaded is
		DateInterval last =  sorted.getLast();
		do {
			DateInterval next = DateInterval.nextMonthlyConsecutive(last);
			if(next.isCycleDone()) {
				// then its missing
				if(missingCycles == null)
					missingCycles= new ArrayList<DateInterval>();
				missingCycles.add(next);
				last = next;
			}else{
				break;
			}
		}while(true);
		
		return missingCycles;
	}

	public static void main(String[] args) {
		System.out.println("checking this use case");
		/*
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy");
		LocalDate dt1 = LocalDate.parse("12/23/2025", fmt);
		*/
		LocalDate[] dates = {
			    LocalDate.parse("2025-12-23"),
			    LocalDate.parse("2025-11-20"),
			    LocalDate.parse("2025-10-24"),
			    LocalDate.parse("2025-09-23"),
			    LocalDate.parse("2025-08-22"),
			    LocalDate.parse("2025-07-24"),
			    LocalDate.parse("2025-06-23"),
			    LocalDate.parse("2025-05-22"),
			    LocalDate.parse("2025-04-23"),
			    LocalDate.parse("2025-03-24"),
			    LocalDate.parse("2025-02-21"),
			    LocalDate.parse("2025-01-24"),
			    LocalDate.parse("2024-12-23"),
			    LocalDate.parse("2024-11-21"),
			    LocalDate.parse("2024-10-24"),
			    LocalDate.parse("2024-09-23"),
			    LocalDate.parse("2024-08-23"),
			    LocalDate.parse("2024-07-24")
			};
		
		for(int i = 1; i < dates.length; i++) {
			System.out.println(" difference between " + dates[i-1] + " and " + dates[i] + " is " +  ChronoUnit.DAYS.between(dates[i-1], dates[i]));
		}
		
	}
}
