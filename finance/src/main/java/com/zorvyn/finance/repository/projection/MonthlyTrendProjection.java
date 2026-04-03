
package com.zorvyn.finance.repository.projection;

import java.math.BigDecimal;

/**
 * Projection for monthly and weekly trend queries.
 *
 * IMPORTANT: getType() returns String, NOT RecordType enum.
 * Native SQL queries return raw column values — PostgreSQL returns the
 * enum-stored string (e.g. "INCOME", "EXPENSE") as a plain String.
 * Spring Data cannot auto-convert a native query string column to a
 * Java enum in a projection interface — it throws a mapping exception.
 *
 * The DashboardController receives the String and can display it as-is
 * (it already does: row.put("type", p.getType())).
 */
public interface MonthlyTrendProjection {
    Integer    getYear();
    Integer    getMonth();
    String     getType();        // String, not RecordType — native query returns raw DB value
    BigDecimal getTotalAmount();
    Long       getRecordCount();
}

