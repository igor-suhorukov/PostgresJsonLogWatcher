package com.github.isuhorukov.log.watcher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.SneakyThrows;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * The {@code LogEnricherPostgreSql} class implements the {@link LogEnricher} interface
 * to provide log enrichment from a PostgreSQL database. It uses the pg_stat_statements
 * extension to fetch SQL query statements based on query IDs.
 */
public class LogEnricherPostgreSql implements LogEnricher {
    public static final String LOG_WATCHER_ENRICHER = "log_watcher_enricher";
    private final Connection connection;
    private final PreparedStatement preparedStatement;
    private final Cache<Long, String> cache;

    /**
     * Constructs a new {@code LogEnricherPostgreSql} instance for enriching PostgreSQL logs.
     * Initializes the database connection, prepared statement, and cache for storing SQL queries text.
     *
     * @param host         the hostname of the PostgreSQL server
     * @param port         the port number of the PostgreSQL server
     * @param database     the name of the PostgreSQL database
     * @param user         the username for accessing the PostgreSQL database
     * @param password     the password for accessing the PostgreSQL database
     * @param maximumSize  the maximum size of the cache for storing recently used SQL queries
     */
    @SneakyThrows
    public LogEnricherPostgreSql(String host, int port, String database, String user, String password, int maximumSize) {
        cache = Caffeine.newBuilder().maximumSize(maximumSize).build();
        connection = DriverManager.getConnection("jdbc:postgresql://"+ host +":"+ port +"/" + database +
                (database!=null && database.contains("?")?"&":"?") + "ApplicationName=" + LOG_WATCHER_ENRICHER,
                user, password);
        preparedStatement = connection.prepareStatement("select query from pg_stat_statements where queryid=?");
    }

    /**
     * Retrieves the SQL query corresponding to the provided query ID from the cache or the PostgreSQL database.
     * If the query ID is {@code null}, empty, or not a valid number, this method returns {@code null}.
     *
     * @param queryId the ID of the query to retrieve
     * @return the SQL query associated with the provided query ID, or {@code null} if the query ID is invalid
     */
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

    /**
     * Returns the application name associated with this log enricher.
     *
     * @return the application name as a string
     */
    @Override
    public String enricherApplicationName() {
        return LOG_WATCHER_ENRICHER;
    }

    /**
     * Closes the database connection and releases any resources held by this log enricher.
     *
     * <p>This method is annotated with {@link SneakyThrows} to rethrow any thrown {@link IOException} as a runtime exception.
     * It ensures that the underlying {@link Connection} is closed properly when this enricher is no longer needed.</p>
     *
     * @throws IOException if an I/O error occurs while closing the database connection.
     */
    @Override
    @SneakyThrows
    public void close() throws IOException {
        connection.close();
    }
}
