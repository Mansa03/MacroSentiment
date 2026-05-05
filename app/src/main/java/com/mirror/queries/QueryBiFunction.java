package com.mirror.queries;

import java.sql.SQLException;
public interface QueryBiFunction<T, U, R> {

    public R apply(T t, U u) throws SQLException;
}
