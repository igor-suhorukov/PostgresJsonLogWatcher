package com.github.isuhorukov.log.watcher;

import java.io.Closeable;

public interface LogEnricher extends Closeable {
    String getStatement(String queryId);
    String enricherApplicationName();
}
