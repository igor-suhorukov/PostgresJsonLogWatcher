package com.github.isuhorukov.log.watcher;

import java.io.Closeable;

/**
 * Interface for enriching log entries with additional information from a database.
 *
 * <p>The {@code LogEnricher} interface defines methods for retrieving SQL statements
 * associated with specific query IDs and for obtaining the name of the application
 * using the log enricher.</p>
 */
public interface LogEnricher extends Closeable {
    /**
     * Retrieves the SQL statement associated with the given query ID.
     *
     * <p>The implementation should return the corresponding SQL statement for
     * the provided query ID, or {@code null} if no such statement can be found.</p>
     *
     * @param queryId the query ID for which to retrieve the SQL statement.
     * @return the SQL statement associated with the given query ID, or {@code null} if not found.
     */
    String getStatement(String queryId);

    /**
     * Returns the application name of the log enricher.
     *
     * @return the name of the log enricher application.
     */
    String enricherApplicationName();
}
