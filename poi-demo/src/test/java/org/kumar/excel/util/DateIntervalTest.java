package org.kumar.excel.util;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class DateIntervalTest {

    @Test
    void findMonthlyMissingCyclesReturnsNullForEmptyInput() {
        assertNull(DateInterval.findMonthlyMissingCycles(Collections.emptySet()));
        assertNull(DateInterval.findMonthlyMissingCycles(null));
    }
}
