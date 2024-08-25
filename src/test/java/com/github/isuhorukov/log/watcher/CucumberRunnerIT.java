package com.github.isuhorukov.log.watcher;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("com/github/isuhorukov/log/watcher")
public class CucumberRunnerIT {
}
