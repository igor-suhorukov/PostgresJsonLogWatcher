package com.github.isuhorukov.log.watcher;

import de.dm.infrastructure.logcapture.LogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static de.dm.infrastructure.logcapture.ExpectedKeyValue.keyValue;
import static de.dm.infrastructure.logcapture.LogExpectation.info;

class PostgreSqlJsonTest {

    private final PostgreSqlJson logWatcher = new PostgreSqlJson();
    @RegisterExtension
    public LogCapture logCapture = LogCapture.forCurrentPackage();

    @Test
    void parseLogLine() {
        logWatcher.parseLogLine("{\"timestamp\":\"2024-08-04 14:05:12.689 UTC\",\"user\":\"postgres\"," +
                "\"dbname\":\"osmworld\",\"pid\":1411,\"remote_host\":\"172.17.0.1\",\"remote_port\":41742," +
                "\"session_id\":\"66af8a98.583\",\"line_num\":14,\"ps\":\"SELECT\"," +
                "\"session_start\":\"2024-08-04 14:05:12 UTC\",\"vxid\":\"4/542\"," +
                "\"txid\":0,\"error_severity\":\"LOG\",\"message\":" +
                "\"execute <unnamed>: select skeys(tags), count(*) from geometry_global_view " +
                        "where id>47 group by 1 order by 2 desc\"," +
                "\"application_name\":\"PostgreSQL JDBC Driver\",\"backend_type\":\"client backend\",\"query_id\":0}\n",
                "postgresql-2024-08-04_072901.json");
        logCapture.assertLogged(info("execute <unnamed>: select skeys\\(tags\\), count\\(\\*\\) " +
                        "from geometry_global_view where id>47 group by 1 order by 2 desc",
                keyValue("timestamp", "2024-08-04 14:05:12.689 UTC"), keyValue("user", "postgres"),
                keyValue("dbname", "osmworld"), keyValue("pid", 1411), keyValue("remote_host", "172.17.0.1"),
                keyValue("remote_port", 41742), keyValue("session_id", "66af8a98.583"), keyValue("line_num", 14),
                keyValue("ps", "SELECT"), keyValue("session_start", "2024-08-04 14:05:12 UTC"),
                keyValue("vxid", "4/542"), keyValue("txid", 0), keyValue("application_name", "PostgreSQL JDBC Driver"),
                keyValue("backend_type", "client backend"), keyValue("fileName", "postgresql-2024-08-04_072901.json")
        ));
    }

    @Test
    void parseStatement(){
        logWatcher.parseLogLine("{\"timestamp\":\"2024-08-01 13:31:52.169 UTC\",\"user\":\"postgres\"," +
                "\"dbname\":\"osmworld\",\"pid\":162,\"remote_host\":\"172.17.0.1\",\"remote_port\":60944," +
                "\"session_id\":\"66ab31f2.a2\",\"line_num\":41,\"ps\":\"idle\"," +
                "\"session_start\":\"2024-08-01 06:57:54 UTC\",\"vxid\":\"3/150\",\"txid\":0," +
                "\"error_severity\":\"LOG\",\"message\":\"statement: SELECT 1;\",\"application_name\":" +
                "\"psql\",\"backend_type\":\"client backend\",\"query_id\":0}\n",
                "postgresql-2024-08-04_072901.json");
        logCapture.assertLogged(info("statement: SELECT 1;", keyValue("statement",true)));
    }
    @Test
    void parseLogPlan(){
        logWatcher.parseLogLine("{\"timestamp\":\"2024-08-04 14:05:12.850 UTC\",\"user\":\"postgres\"," +
                "\"dbname\":\"osmworld\",\"pid\":1411,\"remote_host\":\"172.17.0.1\",\"remote_port\":41742," +
                "\"session_id\":\"66af8a98.583\",\"line_num\":16,\"ps\":\"SELECT\"," +
                "\"session_start\":\"2024-08-04 14:05:12 UTC\",\"vxid\":\"4/542\",\"txid\":0,\"error_severity\":\"LOG\"," +
                "\"message\":\"duration: 160.933 ms  plan:\\n" +
                "Query Text: select skeys(tags), count(*) from geometry_global_view " +
                "where id>47 group by 1 order by 2 desc\\n" +
                "Sort  (cost=306516.93..306832.63 rows=126279 width=40)\\n  " +
                "Sort Key: (count(*)) DESC\\n" +
                "  ->  HashAggregate  (cost=290488.84..293793.80 rows=126279 width=40)\\n" +
                "        Group Key: (skeys(ways.tags))\\n" +
                "        Planned Partitions: 4\\n" +
                "        ->  Gather  (cost=1000.00..285432.75 rows=126279 width=32)\\n" +
                "              Workers Planned: 2\\n" +
                "              ->  ProjectSet  (cost=0.00..271804.85 rows=52616000 width=32)\\n" +
                "                    ->  Parallel Append  (cost=0.00..8330.23 rows=52616 width=65)\\n" +
                "                          ->  Parallel Seq Scan on ways_000 ways  (cost=0.00..7551.98 rows=46714 width=62)\\n" +
                "                                Filter: (id > 47)\\n" +
                "                          ->  Parallel Seq Scan on nodes_000 nodes  (cost=0.00..421.97 rows=7997 width=91)\\n" +
                "                                Filter: (id > 47)\\n" +
                "                          ->  Parallel Seq Scan on multipolygon_000 multipolygon  (cost=0.00..58.30 rows=184 width=73)\\n" +
                "                                Filter: (id > 47)\\n" +
                "                          ->  Parallel Seq Scan on ways_32767 ways_1  (cost=0.00..33.83 rows=146 width=89)\\n" +
                "                                Filter: (id > 47)\\n" +
                "                          ->  Parallel Seq Scan on multipolygon_32767 multipolygon_1  (cost=0.00..1.07 rows=5 width=34)\\n" +
                "                                Filter: (id > 47)\\n" +
                "JIT:\\n" +
                "  Functions: 26\\n" +
                "  Options: Inlining false, Optimization false, Expressions true, Deforming true\"," +
                "\"application_name\":\"PostgreSQL JDBC Driver\",\"backend_type\":\"client backend\",\"query_id\":0}\n",
                "postgresql-2024-08-04_072901.json");
        logCapture.assertLogged(info(keyValue("duration",160.933), keyValue("plan","Query Text: " +
                    "select skeys(tags), count(*) from geometry_global_view where id>47 group by 1 order by 2 desc\n" +
                "Sort  (cost=306516.93..306832.63 rows=126279 width=40)\n" +
                "  Sort Key: (count(*)) DESC\n" +
                "  ->  HashAggregate  (cost=290488.84..293793.80 rows=126279 width=40)\n" +
                "        Group Key: (skeys(ways.tags))\n" +
                "        Planned Partitions: 4\n" +
                "        ->  Gather  (cost=1000.00..285432.75 rows=126279 width=32)\n" +
                "              Workers Planned: 2\n" +
                "              ->  ProjectSet  (cost=0.00..271804.85 rows=52616000 width=32)\n" +
                "                    ->  Parallel Append  (cost=0.00..8330.23 rows=52616 width=65)\n" +
                "                          ->  Parallel Seq Scan on ways_000 ways  (cost=0.00..7551.98 rows=46714 width=62)\n" +
                "                                Filter: (id > 47)\n" +
                "                          ->  Parallel Seq Scan on nodes_000 nodes  (cost=0.00..421.97 rows=7997 width=91)\n" +
                "                                Filter: (id > 47)\n" +
                "                          ->  Parallel Seq Scan on multipolygon_000 multipolygon  (cost=0.00..58.30 rows=184 width=73)\n" +
                "                                Filter: (id > 47)\n" +
                "                          ->  Parallel Seq Scan on ways_32767 ways_1  (cost=0.00..33.83 rows=146 width=89)\n" +
                "                                Filter: (id > 47)\n" +
                "                          ->  Parallel Seq Scan on multipolygon_32767 multipolygon_1  (cost=0.00..1.07 rows=5 width=34)\n" +
                "                                Filter: (id > 47)\n" +
                "JIT:\n" +
                "  Functions: 26\n" +
                "  Options: Inlining false, Optimization false, Expressions true, Deforming true")));
    }
}