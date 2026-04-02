package com.zorvyn.finance.repository.projection;
import com.zorvyn.finance.entity.enums.RecordCategory;
import com.zorvyn.finance.entity.enums.RecordType;
import java.math.BigDecimal;







public interface CategorySummaryProjection {
    RecordCategory getCategory();
    RecordType     getType();
    BigDecimal     getTotalAmount();
    Long           getRecordCount();
}
