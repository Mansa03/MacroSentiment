package com.mirror.queries;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.Set;

public final class QueryHelper {
    private static final Set<String> RETRYABLE = Set.of(
            "08000", "08001", "08003", "08004", "08006",
            "40001", "40P01",
            "53000", "53200", "53300",
            "55P03",
            "57P01", "57P02", "57P03"
    );

    private static final Set<String> NON_RETRYABLE_PREFIXES = Set.of(
            "22",   // data exception
            "23",   // integrity constraint
            "42",   // syntax / schema
            "28"    // authentication
    );

    public static boolean isRetryable(Exception e) {
        if (!(e instanceof BatchUpdateException se)) return false;
        if (se.getSQLState() == null) return false;

        // explicit retryable set
        return RETRYABLE.contains(se.getSQLState());
    }
}
