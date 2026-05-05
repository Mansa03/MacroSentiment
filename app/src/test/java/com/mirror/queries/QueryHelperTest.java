package com.mirror.queries.v1;

import com.mirror.queries.QueryHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.sql.BatchUpdateException;
import java.sql.SQLException;

@Slf4j
public class QueryHelperTest {
    @Mock
    private BatchUpdateException mockE;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testRetryable() {
        Mockito.when(mockE.getSQLState()).thenReturn("08000").thenReturn("08000");
        assert QueryHelper.isRetryable(mockE);
    }

    @Test
    public void testNullSQLState(){
        Mockito.when(mockE.getSQLState()).thenReturn(null);
        assert !QueryHelper.isRetryable(mockE);
    }

    @Test
    public void testNotBatchUpdateException() {
        assert !QueryHelper.isRetryable(new SQLException());
    }
}
