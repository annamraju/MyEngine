package org.kumar.excel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Month;
import java.util.Date;

import org.junit.jupiter.api.Test;

class LedgerRecordTest {

    @Test
    void parseMonthFromFullName() {
        assertEquals(Month.JANUARY, LedgerRecord.parseMonth("January", null));
        assertEquals(Month.JUNE, LedgerRecord.parseMonth("June", null));
    }

    @Test
    void parseMonthFromTransactionDateWhenCellBlank() {
        Date julyFirst = Date.from(java.time.LocalDate.of(2026, 7, 15)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());

        assertEquals(Month.JULY, LedgerRecord.parseMonth("", julyFirst));
        assertEquals(Month.JULY, LedgerRecord.parseMonth(null, julyFirst));
    }
}
