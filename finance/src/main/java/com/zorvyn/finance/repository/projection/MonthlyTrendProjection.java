package com.zorvyn.finance.repository.projection;
import com.zorvyn.finance.entity.enums.RecordType;

import java.math.BigDecimal;
public interface MonthlyTrendProjection {
    Integer        getYear();
    Integer        getMonth();
    RecordType getType();
    BigDecimal     getTotalAmount();
    Long           getRecordCount();
}


