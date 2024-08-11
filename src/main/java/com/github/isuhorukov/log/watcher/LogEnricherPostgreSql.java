package com.github.isuhorukov.log.watcher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.SneakyThrows;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LogEnricherPostgreSql implements LogEnricher {
    public static final String LOG_WATCHER_ENRICHER = "log_watcher_enricher";
    private final Connection connection;
    private final PreparedStatement preparedStatement;
    private final Cache<Long, String> cache;
    @SneakyThrows
    public LogEnricherPostgreSql(String host, int port, String database, String user, String password, int maximumSize) {
        cache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        connection = DriverManager.getConnection("jdbc:postgresql://"+ host +":"+ port +"/" + database +
                (database!=null && database.contains("?")?"&":"?") + "ApplicationName=" + LOG_WATCHER_ENRICHER,
                user, password);
        preparedStatement = connection.prepareStatement("select query from pg_stat_statements where queryid=?");
    }

    @Override
    @SneakyThrows
    public String getStatement(String queryId) {

        if(queryId==null || queryId.isEmpty()){
            return null;
        }
        try {
            long queryIdLong = Long.parseLong(queryId);
            return cache.get(queryIdLong, this::internalGetStatement);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SneakyThrows
    private String internalGetStatement(Long queryId) {
        preparedStatement.setLong(1, queryId);
        try (ResultSet resultSet = preparedStatement.executeQuery()){
            if(resultSet.next()){
                return resultSet.getString(1);
            } else {
                return null;
            }
        }
    }

    @Override
    public String enricherApplicationName() {
        return LOG_WATCHER_ENRICHER;
    }

    @Override
    @SneakyThrows
    public void close() throws IOException {
        connection.close();
    }
}
