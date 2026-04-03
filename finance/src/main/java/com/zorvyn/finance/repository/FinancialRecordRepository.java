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
    // MAIN FILTER QUERY
    // Fix 1: CAST(:search AS string) → stops PostgreSQL receiving the
    //         param as bytea when null: "lower(bytea) does not exist"
    // Fix 2: r.isDeleted = false added explicitly — @SQLRestriction
    //         does NOT apply to hand-written JPQL @Query methods.
    // ================================================================

    @Query(
            value = """
            SELECT r FROM FinancialRecord r
            JOIN FETCH r.createdBy
            WHERE r.isDeleted = false
              AND (:type     IS NULL OR r.type     = :type)
              AND (:category IS NULL OR r.category = :category)
              AND (:dateFrom IS NULL OR r.recordDate >= :dateFrom)
              AND (:dateTo   IS NULL OR r.recordDate <= :dateTo)
              AND (:search   IS NULL
                   OR LOWER(COALESCE(r.notes, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            ORDER BY r.recordDate DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM FinancialRecord r
            WHERE r.isDeleted = false
              AND (:type     IS NULL OR r.type     = :type)
              AND (:category IS NULL OR r.category = :category)
              AND (:dateFrom IS NULL OR r.recordDate >= :dateFrom)
              AND (:dateTo   IS NULL OR r.recordDate <= :dateTo)
              AND (:search   IS NULL
                   OR LOWER(COALESCE(r.notes, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
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
    // SUMMARY
    // ================================================================

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM FinancialRecord r
            WHERE r.type = :type
            """)
    BigDecimal sumByType(@Param("type") RecordType type);

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
    // CATEGORY SUMMARY
    // ================================================================

    @Query("""
            SELECT r.category    AS category,
                   r.type        AS type,
                   SUM(r.amount) AS totalAmount,
                   COUNT(r)      AS recordCount
            FROM FinancialRecord r
            GROUP BY r.category, r.type
            ORDER BY SUM(r.amount) DESC
            """)
    List<CategorySummaryProjection> findCategorySummary();

    @Query("""
            SELECT r.category    AS category,
                   r.type        AS type,
                   SUM(r.amount) AS totalAmount,
                   COUNT(r)      AS recordCount
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
    // MONTHLY TREND
    // Fix 3: FUNCTION('YEAR'/'MONTH',...) emits MySQL year()/month()
    //         calls — those do not exist in PostgreSQL.
    //         Switched to nativeQuery = true with DATE_PART('year/month').
    // Fix 4: Aliases must be ALL LOWERCASE — PostgreSQL folds unquoted
    //         identifiers to lowercase, so Spring Data maps them to
    //         getYear(), getMonth(), getTotalAmount(), getRecordCount().
    // Fix 5: getType() in projection must return String, not RecordType —
    //         native queries return raw DB strings, not Java enums.
    // ================================================================

    @Query(value = """
            SELECT DATE_PART('year',  r.record_date)::int  AS year,
                   DATE_PART('month', r.record_date)::int  AS month,
                   r.record_type                           AS type,
                   SUM(r.amount)                           AS totalamount,
                   COUNT(r.id)                             AS recordcount
            FROM financial_records r
            WHERE r.is_deleted = false
              AND r.record_date >= :startDate
            GROUP BY DATE_PART('year',  r.record_date),
                     DATE_PART('month', r.record_date),
                     r.record_type
            ORDER BY year ASC, month ASC
            """, nativeQuery = true)
    List<MonthlyTrendProjection> findMonthlyTrend(@Param("startDate") LocalDate startDate);

    // ================================================================
    // WEEKLY TREND
    // Same fixes as monthly trend above.
    // month alias reused for week number — projection is shared.
    // ================================================================

    @Query(value = """
            SELECT DATE_PART('year', r.record_date)::int   AS year,
                   DATE_PART('week', r.record_date)::int   AS month,
                   r.record_type                           AS type,
                   SUM(r.amount)                           AS totalamount,
                   COUNT(r.id)                             AS recordcount
            FROM financial_records r
            WHERE r.is_deleted = false
              AND r.record_date >= :startDate
            GROUP BY DATE_PART('year', r.record_date),
                     DATE_PART('week', r.record_date),
                     r.record_type
            ORDER BY year ASC, month ASC
            """, nativeQuery = true)
    List<MonthlyTrendProjection> findWeeklyTrend(@Param("startDate") LocalDate startDate);

    // ================================================================
    // RECENT ACTIVITY
    // ================================================================

    Page<FinancialRecord> findAllByOrderByRecordDateDescCreatedAtDesc(Pageable pageable);

    // ================================================================
    // EXPORT
    // ================================================================

    @Query("""
            SELECT r FROM FinancialRecord r
            JOIN FETCH r.createdBy
            WHERE r.isDeleted = false
              AND (:type     IS NULL OR r.type     = :type)
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
    // COUNTS
    // ================================================================

    long countByType(RecordType type);

    long countByCreatedBy(User createdBy);
}