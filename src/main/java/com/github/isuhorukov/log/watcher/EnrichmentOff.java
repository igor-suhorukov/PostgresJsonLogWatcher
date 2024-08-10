package com.github.isuhorukov.log.watcher;

import java.io.IOException;

public class EnrichmentOff implements LogEnricher{

    @Override
    public String getStatement(String queryId) {
        return null;
    }

    @Override
    public String enricherApplicationName() {
        return null;
    }

    @Override
    public void close() throws IOException {
    }
}
