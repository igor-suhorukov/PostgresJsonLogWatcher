package com.github.isuhorukov.log.watcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class PostgreSqlJson {
    public static final String DURATION = "duration: ";
    public static final String MS = " ms";
    public static final String PLAN = "plan:\n";
    public static final String JSON_SUFFIX = ".json";
    public static final String CURRENT_LOGGER_POSITION = ".current_logger_position";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(PostgreSqlJson.class);
    final Map<String, Long> position = new ConcurrentHashMap<>();


    @SneakyThrows
    public static void main(String[] args) {
        if(args==null || args.length!=1){
            logger.error("Path to PostgreSQL log directory expected");
            return;
        }
        String watchDir = args[0];
        long saveInterval = Long.parseLong(System.getProperty("saveInterval", "10"));
        new PostgreSqlJson().watchPostgreSqlLogs(watchDir, saveInterval);
    }

    public void watchPostgreSqlLogs(String watchDir, long saveInterval) throws IOException, InterruptedException {
        File sourceDirectory = new File(watchDir);
        if(!sourceDirectory.exists()) {
            logger.error("Postgres log directory {} for monitoring not found", watchDir);
            return;
        }
        if(!sourceDirectory.isDirectory()){
            logger.error("Path {} is not directory", watchDir);
            return;
        }
        positionFileTasks(saveInterval);
        initialLogImport(sourceDirectory);
        Path dirToWatch = Paths.get(watchDir);
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            dirToWatch.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    String fileName = event.context().toString();
                    if(!fileName.endsWith(JSON_SUFFIX)){
                        continue;
                    }
                    readJsonLog(new File(watchDir, fileName));
                }
                key.reset();
            }
        }
    }

    void initialLogImport(File sourceDirectory) throws IOException {
        File[] jsonLogs = sourceDirectory.listFiles(pathname -> pathname.getName().endsWith(JSON_SUFFIX));

        if(jsonLogs!=null && jsonLogs.length>0){
            Arrays.sort(jsonLogs, Comparator.comparing(File::getName));
            for(File jsonLog: jsonLogs){
                readJsonLog(jsonLog);
            }
        }
    }

    @SneakyThrows
    void parseLogLine(String line, String logName){
        JsonNode jsonNode = mapper.readTree(line);
        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();

        String message = jsonNode.at("/message").asText();
        LoggingEventBuilder loggingEventBuilder = logger.atLevel(getSeverity(jsonNode.at("/error_severity").asText()));
        if(message.startsWith(DURATION)){
            double duration = Double.parseDouble(message.substring(DURATION.length(), message.indexOf(MS)));
            loggingEventBuilder.addKeyValue("duration", duration);
            int planIdx = message.indexOf(PLAN);
            if(planIdx!=-1){
                String plan = message.substring(planIdx + PLAN.length());
                loggingEventBuilder.addKeyValue("plan", plan);
            }
            loggingEventBuilder.setMessage("");
        } else {
            loggingEventBuilder.setMessage(message);
            if(message.startsWith("statement: ")){
                loggingEventBuilder.addKeyValue("statement", true);
            }
        }
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if("error_severity".equals(entry.getKey()) || "message".equals(entry.getKey()) ||
                    "query_id".equals(entry.getKey())&&"0".equals(entry.getValue().asText()) //skip empty query_id
            ) continue;
            JsonNode entryValue = entry.getValue();
            loggingEventBuilder.addKeyValue(entry.getKey(), getValue(entryValue));
        }
        loggingEventBuilder.addKeyValue("fileName", logName);
        loggingEventBuilder.log();
    }

    private static Object getValue(JsonNode entryValue) {
        Object value;
        switch (entryValue.getNodeType()){
            case BOOLEAN:
                value = entryValue.booleanValue();
                break;
            case NUMBER:
                switch (entryValue.numberType()){
                    case INT:
                        value = entryValue.asInt();
                        break;
                    case LONG:
                        value = entryValue.asLong();
                        break;
                    case FLOAT:
                        value = entryValue.floatValue();
                        break;
                    case DOUBLE:
                        value = entryValue.doubleValue();
                        break;
                    default:
                        value = entryValue.asText();
                        break;
                }
                break;
            default:
                value = entryValue.asText();
                break;
        }
        return value;
    }

    private static Level getSeverity(String severity) {
        switch (severity){
            case "LOG": return Level.INFO;
            case "ERROR": return Level.ERROR;
            case "DEBUG": return Level.DEBUG;
            case "TRACE": return Level.TRACE;
            case "FATAL": return Level.ERROR;
            default:
                throw new IllegalArgumentException(severity);
        }
    }
    private void positionFileTasks(long saveInterval) throws IOException {
        if(new File(CURRENT_LOGGER_POSITION).exists()) {
            position.putAll(mapper.readValue(new File(CURRENT_LOGGER_POSITION),
                    new TypeReference<ConcurrentHashMap<String, Long>>() {}));
        }
        new Timer("LogPositionSaver", true).schedule(new TimerTask() {
            @Override
            public void run() {
                saveLogFilesPosition();
            }
        }, TimeUnit.SECONDS.toMillis(saveInterval), TimeUnit.SECONDS.toMillis(saveInterval));
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveLogFilesPosition));
    }

    private synchronized void saveLogFilesPosition() {
        try {
            if(position.isEmpty()){
                return;
            }
            try (FileOutputStream currentPostitionFile = new FileOutputStream(CURRENT_LOGGER_POSITION)){
                mapper.writeValue(currentPostitionFile,new TreeMap<>(position));
            }
        } catch (Exception e) {
            logger.error("Unable to save current log position", e);
        }
    }

    void readJsonLog(File jsonLog) throws IOException {
        String jsonLogName = jsonLog.getName();
        long from = position.computeIfAbsent(jsonLogName, name -> 0L);
        if(jsonLog.length() ==0){
            return;
        }
        if(jsonLog.length()==0 || jsonLog.length()<=position.computeIfAbsent(jsonLogName, name -> 0L)){
            return;
        }
        try (RandomAccessFile randomAccessJson = new RandomAccessFile(jsonLog, "r")) {
            randomAccessJson.seek(from);
            String line;
            while ((line = randomAccessJson.readLine())!= null) {
                parseLogLine(line, jsonLogName);
            }
        }
        position.put(jsonLogName, jsonLog.length());
    }
}
