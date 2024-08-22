package com.github.isuhorukov.log.watcher;

import java.io.IOException;

/**
 * A no-op implementation of {@link LogEnricher} that disables log enrichment.
 *
 * <p>This class is used as a fallback or default implementation when log enrichment is not needed or
 * cannot be configured. It implements all methods of the {@link LogEnricher} interface but provides
 * no operational functionality.</p>
 */
public class EnrichmentOff implements LogEnricher{

    /**
     * Returns {@code null} as no statement enrichment is performed.
     *
     * @param queryId the query ID (which is ignored).
     * @return {@code null}.
     */
    @Override
    public String getStatement(String queryId) {
        return null;
    }

    /**
     * Returns {@code null} as this implementation has no application name.
     *
     * @return {@code null}.
     */
    @Override
    public String enricherApplicationName() {
        return null;
    }

    /**
     * Performs no action as there are no resources to close in this implementation.
     *
     * @throws IOException never thrown in this implementation
     */
    @Override
    public void close() throws IOException {
    }
}
