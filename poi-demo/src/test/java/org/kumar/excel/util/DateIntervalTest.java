package org.kumar.excel.util;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DateIntervalTest {

    @Test
    void findMonthlyMissingCyclesReturnsNullForEmptyInput() {
        assertNull(DateInterval.findMonthlyMissingCycles(Collections.emptySet()));
        assertNull(DateInterval.findMonthlyMissingCycles(null));
    }

    @Test
    void findMissingCyclesReturnsNullForEmptyInput() {
        Set<DateInterval> empty = Collections.emptySet();
        assertNull(DateInterval.findMissingCycles(empty, (short) 15, (short) 14));
        assertNull(DateInterval.findMissingCycles(null, (short) 26, (short) 25));
    }
}
