package com.mirror.queries.v1;


import java.sql.BatchUpdateException;
import java.sql.Connection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.mirror.models.v1.ImmutableRawAPINews;
import com.mirror.queries.TransactionResults;
import com.mirror.queries.FailedTransactions;
import com.mirror.queries.QueryBiFunction;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import com.mirror.queries.QueryHelper;


@Slf4j
@AllArgsConstructor
public class BatchRawAPINews implements QueryBiFunction<Connection, List<ImmutableRawAPINews>, TransactionResults<ImmutableRawAPINews>> {
    @Getter
    private static final String insertSQL = """
        INSERT INTO raw_api_news_v1 (url, source_id, source_name, author, title, description, urlToImage, publishedAt, content)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (url) DO NOTHING;
    """;
    @Override
    public TransactionResults<ImmutableRawAPINews> apply(@NonNull Connection conn, @NonNull List<ImmutableRawAPINews> rawAPINewsList) throws SQLException{
        try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            for (ImmutableRawAPINews news : rawAPINewsList) {
                pstmt.setString(1, news.url());
                pstmt.setString(2, news.sourceId());
                pstmt.setString(3, news.sourceName());
                pstmt.setString(4, news.author());
                pstmt.setString(5, news.title());
                pstmt.setString(6, news.description());
                pstmt.setString(7, news.urlToImage());
                pstmt.setString(8, news.publishedAt());
                pstmt.setString(9, news.content());
                pstmt.addBatch();
            }
            int[] batchResults = pstmt.executeBatch();
            pstmt.clearBatch();
            pstmt.close();
            return getResults(batchResults, rawAPINewsList, null);
        }
        catch (BatchUpdateException e) {
            int[] succeeded = e.getUpdateCounts();
            return getResults(succeeded, rawAPINewsList, e);
        }
        catch (SQLException e) {
            throw e;
        }
    }

    private TransactionResults<ImmutableRawAPINews> getResults(int[] results, List<ImmutableRawAPINews> transactions, SQLException e) {
        assert transactions.size() == results.length;
        List<ImmutableRawAPINews> succeededTransactions = new ArrayList<>();
        List<ImmutableRawAPINews> skippedTransactions = new ArrayList<>();
        List<ImmutableRawAPINews> retryable = new ArrayList<>();
        List<ImmutableRawAPINews> nonRetryable = new ArrayList<>();
        SQLException current = e;
        for (int i = 0; i < results.length; i++){
                 if (results[i] > 0 || results[i] == Statement.SUCCESS_NO_INFO) {
                    succeededTransactions.add(transactions.get(i));
                 }
                 else if (results[i] == 0) {
                    skippedTransactions.add(transactions.get(i));
                 }
                 else{
                     if (QueryHelper.isRetryable(current)) {
                         retryable.add(transactions.get(i));
                     } else {
                         nonRetryable.add(transactions.get(i));
                     }
                     current = current.getNextException();
                 }
        }
        return new TransactionResults<ImmutableRawAPINews>(succeededTransactions,skippedTransactions, new FailedTransactions<ImmutableRawAPINews>(retryable,nonRetryable));
    }
}
