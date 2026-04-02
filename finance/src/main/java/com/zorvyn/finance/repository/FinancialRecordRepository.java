package com.zorvyn.finance.repository;
import com.zorvyn.finance.entity.FinancialRecord;
import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;
import com.zorvyn.finance.repository.projection.CategorySummaryProjection;
import com.zorvyn.finance.repository.projection.MonthlyTrendProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Repository
public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, UUID> {

    // ================================================================
    // Section 1: Filtered + paginated list (GET /api/v1/records)
    // ================================================================

    /**
     * The primary listing query — supports all optional filter combinations
     * in a single method using null-safe JPQL conditions.
     *
     * Filters:
     *   type      — INCOME or EXPENSE; null means both
     *   category  — specific category; null means all
     *   dateFrom  — inclusive start date; null means no lower bound
     *   dateTo    — inclusive end date; null means no upper bound
     *   search    — partial case-insensitive match on notes field
     *
     * JOIN FETCH on createdBy avoids N+1: without it, each record in
     * the page would trigger a separate SELECT to load the user.
     * With JOIN FETCH, we load records + their creators in one query.
     *
     * Note: JOIN FETCH with pagination requires countQuery to be declared
     * separately — Spring Data cannot derive a count from a fetch join.
     */
    @Query(
            value = """
                SELECT r FROM FinancialRecord r
                JOIN FETCH r.createdBy
                WHERE (:type     IS NULL OR r.type     = :type)
                  AND (:category IS NULL OR r.category = :category)
                  AND (:dateFrom IS NULL OR r.recordDate >= :dateFrom)
                  AND (:dateTo   IS NULL OR r.recordDate <= :dateTo)
                  AND (:search   IS NULL
                       OR LOWER(r.notes) LIKE LOWER(CONCAT('%', :search, '%')))
                """,
            countQuery = """
                SELECT COUNT(r) FROM FinancialRecord r
                WHERE (:type     IS NULL OR r.type     = :type)
                  AND (:category IS NULL OR r.category = :category)
                  AND (:dateFrom IS NULL OR r.recordDate >= :dateFrom)
                  AND (:dateTo   IS NULL OR r.recordDate <= :dateTo)
                  AND (:search   IS NULL
                       OR LOWER(r.notes) LIKE LOWER(CONCAT('%', :search, '%')))
                """
    )
    Page<FinancialRecord> findAllWithFilters(
            @Param("type")     RecordType     type,
            @Param("category") RecordCategory category,
            @Param("dateFrom") LocalDate      dateFrom,
            @Param("dateTo")   LocalDate      dateTo,
            @Param("search")   String         search,
            Pageable pageable
    );

    // ================================================================
    // Section 2: Dashboard summary (GET /api/v1/dashboard/summary)
    // ================================================================

    /**
     * Total amount for a given record type (INCOME or EXPENSE).
     *
     * Returns BigDecimal directly — no projection needed when there
     * is a single scalar result.
     *
     * COALESCE handles the edge case where no records exist yet:
     * SUM over an empty set returns NULL in SQL, which would cause a
     * NullPointerException when the service tries to use the result.
     */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM FinancialRecord r
            WHERE r.type = :type
            """)
    BigDecimal sumByType(@Param("type") RecordType type);

    /**
     * Overload with a date range — for period-specific summaries.
     * The dashboard can call this with the first and last day of the
     * current month to show "this month's" totals.
     */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM FinancialRecord r
            WHERE r.type = :type
              AND r.recordDate BETWEEN :dateFrom AND :dateTo
            """)
    BigDecimal sumByTypeAndDateRange(
            @Param("type")     RecordType type,
            @Param("dateFrom") LocalDate  dateFrom,
            @Param("dateTo")   LocalDate  dateTo
    );

    // ================================================================
    // Section 3: Category-wise totals (GET /api/v1/dashboard/by-category)
    // ================================================================

    /**
     * Returns total amount and record count grouped by category and type.
     *
     * The result is mapped into CategorySummaryProjection — an interface
     * whose getter names match the SELECT aliases exactly (category, type,
     * totalAmount, recordCount). Spring Data wires the mapping automatically.
     *
     * ORDER BY totalAmount DESC puts the highest-spend categories first,
     * which is the most useful default for a dashboard.
     */
    @Query("""
            SELECT r.category   AS category,
                   r.type       AS type,
                   SUM(r.amount) AS totalAmount,
                   COUNT(r)      AS recordCount
            FROM FinancialRecord r
            GROUP BY r.category, r.type
            ORDER BY SUM(r.amount) DESC
            """)
    List<CategorySummaryProjection> findCategorySummary();

    /**
     * Same as above but scoped to a date range.
     * Used when the frontend requests category breakdown for a specific period.
     */
    @Query("""
            SELECT r.category    AS category,
                   r.type        AS type,
                   SUM(r.amount)  AS totalAmount,
                   COUNT(r)       AS recordCount
            FROM FinancialRecord r
            WHERE r.recordDate BETWEEN :dateFrom AND :dateTo
            GROUP BY r.category, r.type
            ORDER BY SUM(r.amount) DESC
            """)
    List<CategorySummaryProjection> findCategorySummaryByDateRange(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo")   LocalDate dateTo
    );

    // ================================================================
    // Section 4: Monthly trend (GET /api/v1/dashboard/monthly-trend)
    // ================================================================

    /**
     * Month-by-month breakdown of income and expenses.
     *
     * FUNCTION('YEAR', ...) and FUNCTION('MONTH', ...) are portable
     * JPQL wrappers around PostgreSQL's EXTRACT(YEAR FROM ...) and
     * EXTRACT(MONTH FROM ...).
     *
     * The result is one row per (year, month, type) combination.
     * The service layer pivots this into a cleaner per-month structure.
     *
     * :months controls how far back to look — e.g. 12 = last 12 months.
     */
    @Query("""
            SELECT FUNCTION('YEAR',  r.recordDate) AS year,
                   FUNCTION('MONTH', r.recordDate) AS month,
                   r.type                          AS type,
                   SUM(r.amount)                   AS totalAmount,
                   COUNT(r)                        AS recordCount
            FROM FinancialRecord r
            WHERE r.recordDate >= :startDate
            GROUP BY FUNCTION('YEAR',  r.recordDate),
                     FUNCTION('MONTH', r.recordDate),
                     r.type
            ORDER BY year ASC, month ASC
            """)
    List<MonthlyTrendProjection> findMonthlyTrend(@Param("startDate") LocalDate startDate);

    // ================================================================
    // Section 5: Weekly trend (GET /api/v1/dashboard/weekly-trend)
    // ================================================================

    /**
     * Week-by-week totals for the past N weeks.
     *
     * FUNCTION('WEEK', ...) maps to PostgreSQL's EXTRACT(WEEK FROM ...).
     * We include year in the GROUP BY to avoid weeks from different years
     * colliding (week 1 of 2023 ≠ week 1 of 2024).
     */
    @Query("""
            SELECT FUNCTION('YEAR', r.recordDate) AS year,
                   FUNCTION('WEEK', r.recordDate) AS month,
                   r.type                         AS type,
                   SUM(r.amount)                  AS totalAmount,
                   COUNT(r)                       AS recordCount
            FROM FinancialRecord r
            WHERE r.recordDate >= :startDate
            GROUP BY FUNCTION('YEAR', r.recordDate),
                     FUNCTION('WEEK', r.recordDate),
                     r.type
            ORDER BY year ASC, month ASC
            """)
    List<MonthlyTrendProjection> findWeeklyTrend(@Param("startDate") LocalDate startDate);

    // ================================================================
    // Section 6: Recent activity (GET /api/v1/dashboard/recent-activity)
    // ================================================================

    /**
     * Fetch the N most recent records for the activity feed.
     *
     * Using a derived method name here because it maps cleanly to
     * a simple ORDER BY + LIMIT — no JOIN FETCH needed since the
     * activity feed only displays a handful of rows.
     *
     * Pageable controls the limit: caller passes PageRequest.of(0, n).
     */
    Page<FinancialRecord> findAllByOrderByRecordDateDescCreatedAtDesc(Pageable pageable);

    // ================================================================
    // Section 7: CSV export (GET /api/v1/records/export)
    // ================================================================

    /**
     * Fetch all records matching the export filters as a plain List.
     *
     * Deliberately NOT paginated — the CSV export should include all
     * matching records, not just one page. The service streams the
     * list directly into the CSV writer row by row.
     *
     * The query is identical to findAllWithFilters above but returns
     * List instead of Page.
     */
    @Query("""
            SELECT r FROM FinancialRecord r
            JOIN FETCH r.createdBy
            WHERE (:type     IS NULL OR r.type     = :type)
              AND (:category IS NULL OR r.category = :category)
              AND (:dateFrom IS NULL OR r.recordDate >= :dateFrom)
              AND (:dateTo   IS NULL OR r.recordDate <= :dateTo)
            ORDER BY r.recordDate DESC
            """)
    List<FinancialRecord> findAllForExport(
            @Param("type")     RecordType     type,
            @Param("category") RecordCategory category,
            @Param("dateFrom") LocalDate      dateFrom,
            @Param("dateTo")   LocalDate      dateTo
    );

    // ================================================================
    // Section 8: Simple counts (used by admin summary stats)
    // ================================================================

    /** Total number of non-deleted records of a given type. */
    long countByType(RecordType type);

    /** Count records created by a specific user — useful for user detail view. */
    long countByCreatedBy(User createdBy);
}


