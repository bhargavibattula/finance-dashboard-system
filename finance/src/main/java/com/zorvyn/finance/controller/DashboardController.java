package com.zorvyn.finance.controller;
import com.zorvyn.finance.dto.response.ApiResponse;
import com.zorvyn.finance.dto.response.FinancialRecordResponse;
import com.zorvyn.finance.entity.FinancialRecord;
import com.zorvyn.finance.entity.enums.RecordType;
import com.zorvyn.finance.repository.FinancialRecordRepository;
import com.zorvyn.finance.repository.projection.CategorySummaryProjection;
import com.zorvyn.finance.repository.projection.MonthlyTrendProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dashboard aggregation endpoints — no writes, read-only.
 *
 * This controller calls the repository directly rather than going through
 * a service layer, which is acceptable here because:
 *   1. There is no business logic to isolate — these are pure aggregation reads.
 *   2. The JPQL queries live in the repository already.
 *   3. Adding a DashboardService wrapper would be boilerplate with no benefit.
 *
 * URL structure: /api/v1/dashboard/**
 *
 * Role rules (matching SecurityConfig):
 *   GET /v1/dashboard/summary          → ALL authenticated
 *   GET /v1/dashboard/by-category      → ALL authenticated
 *   GET /v1/dashboard/recent-activity  → ALL authenticated
 *   GET /v1/dashboard/monthly-trend    → ANALYST + ADMIN
 *   GET /v1/dashboard/weekly-trend     → ANALYST + ADMIN
 */
@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

    private final FinancialRecordRepository recordRepository;

    public DashboardController(FinancialRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    // ----------------------------------------------------------------
    // GET /api/v1/dashboard/summary  — ALL authenticated
    // ----------------------------------------------------------------

    /**
     * Returns the top-level financial summary:
     *   totalIncome, totalExpenses, netBalance (income − expenses),
     *   thisMonthIncome, thisMonthExpenses, thisMonthNetBalance,
     *   totalIncomeRecords, totalExpenseRecords, totalRecords.
     *
     * All sums are computed at the DB level (COALESCE(SUM, 0) in JPQL)
     * so no NPE risk when there are zero records.
     *
     * Optional date range params let the frontend request
     * period-specific summaries (e.g. current year only).
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        // All-time totals
        BigDecimal totalIncome   = recordRepository.sumByType(RecordType.INCOME);
        BigDecimal totalExpenses = recordRepository.sumByType(RecordType.EXPENSE);
        BigDecimal netBalance    = totalIncome.subtract(totalExpenses);

        // This-month totals
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate today        = LocalDate.now();
        BigDecimal monthIncome   = recordRepository.sumByTypeAndDateRange(
                RecordType.INCOME,  firstOfMonth, today);
        BigDecimal monthExpenses = recordRepository.sumByTypeAndDateRange(
                RecordType.EXPENSE, firstOfMonth, today);

        // Record counts
        long incomeCount  = recordRepository.countByType(RecordType.INCOME);
        long expenseCount = recordRepository.countByType(RecordType.EXPENSE);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalIncome",          totalIncome);
        summary.put("totalExpenses",        totalExpenses);
        summary.put("netBalance",           netBalance);
        summary.put("thisMonthIncome",      monthIncome);
        summary.put("thisMonthExpenses",    monthExpenses);
        summary.put("thisMonthNetBalance",  monthIncome.subtract(monthExpenses));
        summary.put("totalIncomeRecords",   incomeCount);
        summary.put("totalExpenseRecords",  expenseCount);
        summary.put("totalRecords",         incomeCount + expenseCount);

        return ResponseEntity.ok(ApiResponse.ok(summary, "Summary fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/dashboard/by-category  — ALL authenticated
    // ----------------------------------------------------------------

    /**
     * Returns total amount and record count for every (category, type) pair,
     * sorted by totalAmount descending (highest spend first).
     *
     * Optional dateFrom / dateTo scope the result to a time window.
     * Useful for a "this quarter's spending by category" chart.
     */
    @GetMapping("/by-category")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCategoryBreakdown(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        List<CategorySummaryProjection> projections =
                (dateFrom != null && dateTo != null)
                        ? recordRepository.findCategorySummaryByDateRange(dateFrom, dateTo)
                        : recordRepository.findCategorySummary();

        List<Map<String, Object>> result = projections.stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category",    p.getCategory());
                    row.put("type",        p.getType());
                    row.put("totalAmount", p.getTotalAmount());
                    row.put("recordCount", p.getRecordCount());
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.ok(result, "Category breakdown fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/dashboard/monthly-trend  — ANALYST + ADMIN
    // ----------------------------------------------------------------

    /**
     * Month-by-month income and expense totals for the past N months.
     *
     * @param months how many months to look back (default 12, max 24)
     *
     * Each row in the response represents one (year, month, type) combination.
     * A human-readable periodLabel ("Jan 2024") is added for direct use in charts.
     */
    @GetMapping("/monthly-trend")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMonthlyTrend(
            @RequestParam(defaultValue = "12") int months) {

        int safeMonths     = Math.min(Math.max(months, 1), 24);
        LocalDate startDate = LocalDate.now().minusMonths(safeMonths);

        List<MonthlyTrendProjection> projections =
                recordRepository.findMonthlyTrend(startDate);

        List<Map<String, Object>> result = projections.stream()
                .map(p -> {
                    String label = Month.of(p.getMonth())
                            .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                            + " " + p.getYear();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("year",        p.getYear());
                    row.put("month",       p.getMonth());
                    row.put("periodLabel", label);
                    row.put("type",        p.getType());
                    row.put("totalAmount", p.getTotalAmount());
                    row.put("recordCount", p.getRecordCount());
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.ok(result, "Monthly trend fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/dashboard/weekly-trend  — ANALYST + ADMIN
    // ----------------------------------------------------------------

    /**
     * Week-by-week income and expense totals for the past N weeks.
     *
     * @param weeks how many weeks to look back (default 8, max 52)
     *
     * periodLabel format: "W04 2024" (ISO week number + year).
     */
    @GetMapping("/weekly-trend")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWeeklyTrend(
            @RequestParam(defaultValue = "8") int weeks) {

        int safeWeeks      = Math.min(Math.max(weeks, 1), 52);
        LocalDate startDate = LocalDate.now().minusWeeks(safeWeeks);

        List<MonthlyTrendProjection> projections =
                recordRepository.findWeeklyTrend(startDate);

        List<Map<String, Object>> result = projections.stream()
                .map(p -> {
                    // p.getMonth() holds the ISO week number for weekly queries
                    String label = String.format("W%02d %d", p.getMonth(), p.getYear());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("year",        p.getYear());
                    row.put("week",        p.getMonth());
                    row.put("periodLabel", label);
                    row.put("type",        p.getType());
                    row.put("totalAmount", p.getTotalAmount());
                    row.put("recordCount", p.getRecordCount());
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.ok(result, "Weekly trend fetched successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/dashboard/recent-activity  — ALL authenticated
    // ----------------------------------------------------------------

    /**
     * Returns the N most recent financial records, sorted by record date
     * then by creation timestamp (newest first).
     *
     * @param limit number of records to return (default 10, max 50)
     *
     * Used for the "recent activity" feed on the dashboard home page.
     */
    @GetMapping("/recent-activity")
    public ResponseEntity<ApiResponse<List<FinancialRecordResponse>>> getRecentActivity(
            @RequestParam(defaultValue = "10") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), 50);

        Page<FinancialRecord> page = recordRepository
                .findAllByOrderByRecordDateDescCreatedAtDesc(
                        PageRequest.of(0, safeLimit));

        List<FinancialRecordResponse> activity = page.getContent()
                .stream()
                .map(FinancialRecordResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.ok(activity, "Recent activity fetched successfully"));
    }
}