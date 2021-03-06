/*
 * Copyright (c) 2018 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.profiling;

import com.uber.profiling.reporters.ConsoleOutputReporter;
import com.uber.profiling.reporters.FileOutputReporter;
import com.uber.profiling.reporters.KafkaOutputReporter;
import com.uber.profiling.util.AgentLogger;
import com.uber.profiling.util.ClassAndMethod;
import com.uber.profiling.util.ClassMethodArgument;
import com.uber.profiling.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Arguments {
    public final static String DEFAULT_APP_ID_REGEX = "application_[\\w_]+";
    public final static long DEFAULT_METRIC_INTERVAL = 60000;
    public final static long DEFAULT_SAMPLE_INTERVAL = 100;

    public final static String ARG_REPORTER = "reporter";
    public final static String ARG_METRIC_INTERVAL = "metricInterval";
    public final static String ARG_SAMPLE_INTERVAL = "sampleInterval";
    public final static String ARG_TAG = "tag";
    public final static String ARG_APP_ID_REGEX = "appIdRegex";
    public final static String ARG_DURATION_PROFILING = "durationProfiling";
    public final static String ARG_ARGUMENT_PROFILING = "argumentProfiling";
    
    public final static String ARG_BROKER_LIST = "brokerList";
    public final static String ARG_SYNC_MODE = "syncMode";
    public final static String ARG_TOPIC_PREFIX = "topicPrefix";

    public final static String ARG_OUTPUT_DIR = "outputDir";
    
    public final static String ARG_IO_PROFILING = "ioProfiling";

    public static final long MIN_INTERVAL_MILLIS = 50;

    private static final AgentLogger logger = AgentLogger.getLogger(Arguments.class.getName());

    private Map<String, List<String>> rawArgValues = new HashMap<>();

    private Constructor<Reporter> reporterConstructor;
    private String appIdRegex = DEFAULT_APP_ID_REGEX;
    private long metricInterval = DEFAULT_METRIC_INTERVAL;
    private long sampleInterval = 0L;
    private String tag;
    private String brokerList;
    private boolean syncMode;
    private String topicPrefix;
    private String outputDir;
    private boolean ioProfiling;

    private List<ClassAndMethod> durationProfiling = new ArrayList<>();
    private List<ClassMethodArgument> argumentProfiling = new ArrayList<>();

    private Arguments(Map<String, List<String>> parsedArgs) {
        rawArgValues.putAll(parsedArgs);

        String argValue = getArgumentSingleValue(parsedArgs, ARG_REPORTER);
        if (argValue != null && !argValue.isEmpty()) {
            reporterConstructor = ReflectionUtils.getConstructor(argValue, Reporter.class);
        }

        argValue = getArgumentSingleValue(parsedArgs, ARG_METRIC_INTERVAL);
        if (argValue != null && !argValue.isEmpty()) {
            metricInterval = Long.parseLong(argValue);
            logger.info("Got argument value for metricInterval: " + metricInterval);
        }

        if (metricInterval < MIN_INTERVAL_MILLIS) {
            throw new RuntimeException("Metric interval too short, must be at least " + Arguments.MIN_INTERVAL_MILLIS);
        }

        argValue = getArgumentSingleValue(parsedArgs, ARG_SAMPLE_INTERVAL);
        if (argValue != null && !argValue.isEmpty()) {
            sampleInterval = Long.parseLong(argValue);
            logger.info("Got argument value for sampleInterval: " + sampleInterval);
        }

        if (sampleInterval != 0 && sampleInterval < MIN_INTERVAL_MILLIS) {
            throw new RuntimeException("Sample interval too short, must be 0 (disable sampling) or at least " + Arguments.MIN_INTERVAL_MILLIS);
        }
        
        tag = getArgumentSingleValue(parsedArgs, ARG_TAG);
        logger.info("Got argument value for tag: " + tag);

        argValue = getArgumentSingleValue(parsedArgs, ARG_APP_ID_REGEX);
        if (argValue != null && !argValue.isEmpty()) {
            appIdRegex = argValue;
            logger.info("Got argument value for appIdRegex: " + appIdRegex);
        }

        List<String> argValues = getArgumentMultiValues(parsedArgs, ARG_DURATION_PROFILING);
        for (String str : argValues) {
            int index = str.lastIndexOf(".");
            if (index <= 0 || index + 1 >= str.length()) {
                throw new IllegalArgumentException("Invalid argument value: " + str);
            }
            String className = str.substring(0, index);
            String methodName = str.substring(index + 1, str.length());
            ClassAndMethod classAndMethod = new ClassAndMethod(className, methodName);
            durationProfiling.add(classAndMethod);
            logger.info("Got argument value for durationProfiling: " + classAndMethod);
        }

        argValues = getArgumentMultiValues(parsedArgs, ARG_ARGUMENT_PROFILING);
        for (String str : argValues) {
            int index = str.lastIndexOf(".");
            if (index <= 0 || index + 1 >= str.length()) {
                throw new IllegalArgumentException("Invalid argument value: " + str);
            }
            String classMethodName = str.substring(0, index);
            int argumentIndex = Integer.parseInt(str.substring(index + 1, str.length()));

            index = classMethodName.lastIndexOf(".");
            if (index <= 0 || index + 1 >= classMethodName.length()) {
                throw new IllegalArgumentException("Invalid argument value: " + str);
            }
            String className = classMethodName.substring(0, index);
            String methodName = str.substring(index + 1, classMethodName.length());

            ClassMethodArgument classMethodArgument = new ClassMethodArgument(className, methodName, argumentIndex);
            argumentProfiling.add(classMethodArgument);
            logger.info("Got argument value for argumentProfiling: " + classMethodArgument);
        }

        brokerList = getArgumentSingleValue(parsedArgs, ARG_BROKER_LIST);
        logger.info("Got argument value for brokerList: " + brokerList);

        argValue = getArgumentSingleValue(parsedArgs, ARG_SYNC_MODE);
        if (argValue != null && !argValue.isEmpty()) {
            syncMode = Boolean.parseBoolean(argValue);
            logger.info("Got argument value for syncMode: " + syncMode);
        }

        topicPrefix = getArgumentSingleValue(parsedArgs, ARG_TOPIC_PREFIX);
        logger.info("Got argument value for topicPrefix: " + topicPrefix);
        
        outputDir = getArgumentSingleValue(parsedArgs, ARG_OUTPUT_DIR);
        logger.info("Got argument value for outputDir: " + outputDir);

        argValue = getArgumentSingleValue(parsedArgs, ARG_IO_PROFILING);
        if (argValue != null && !argValue.isEmpty()) {
            ioProfiling = Boolean.parseBoolean(argValue);
            logger.info("Got argument value for ioProfiling: " + ioProfiling);
        }
    }

    public static Arguments parseArgs(String args) {
        if (args == null) {
            return new Arguments(new HashMap<>());
        }

        args = args.trim();
        if (args.isEmpty()) {
            return new Arguments(new HashMap<>());
        }

        Map<String, List<String>> map = new HashMap<>();
        for (String argPair : args.split(",")) {
            String[] strs = argPair.split("=");
            if (strs.length != 2) {
                throw new IllegalArgumentException("Arguments for the agent should be like: key1=value1,key2=value2");
            }

            String key = strs[0].trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Argument key should not be empty");
            }

            List<String> list = map.get(key);
            if (list == null) {
                list = new ArrayList<>();
                map.put(key, list);
            }
            list.add(strs[1].trim());
        }

        return new Arguments(map);
    }

    public Map<String, List<String>> getRawArgValues() {
        return rawArgValues;
    }

    public Reporter getReporter() {
        if (reporterConstructor == null) {
            return new ConsoleOutputReporter();
        } else {
            try {
                Reporter reporter = reporterConstructor.newInstance();
                if (reporter instanceof KafkaOutputReporter) {
                    KafkaOutputReporter kafkaOutputReporter = (KafkaOutputReporter)reporter;
                    if (brokerList != null && !brokerList.isEmpty()) {
                        kafkaOutputReporter.setBrokerList(brokerList);
                    }
                    kafkaOutputReporter.setSyncMode(syncMode);
                    if (topicPrefix != null && !topicPrefix.isEmpty()) {
                        kafkaOutputReporter.setTopicPrefix(topicPrefix);
                    }
                } else if (reporter instanceof FileOutputReporter) {
                    FileOutputReporter fileOutputReporter = (FileOutputReporter)reporter;
                    fileOutputReporter.setDirectory(outputDir);
                }
                return reporter;
            } catch (Throwable e) {
                throw new RuntimeException(String.format("Failed to create reporter instance %s", reporterConstructor.getDeclaringClass()), e);
            }
        }
    }

    public void setReporter(String className) {
        reporterConstructor = ReflectionUtils.getConstructor(className, Reporter.class);
    }

    public long getMetricInterval() {
        return metricInterval;
    }

    public long getSampleInterval() {
        return sampleInterval;
    }
    
    public String getTag() {
        return tag;
    }

    public String getAppIdRegex() {
        return appIdRegex;
    }

    public List<ClassAndMethod> getDurationProfiling() {
        return durationProfiling;
    }

    public List<ClassMethodArgument> getArgumentProfiling() {
        return argumentProfiling;
    }

    public String getBrokerList() {
        return brokerList;
    }

    public boolean isSyncMode() {
        return syncMode;
    }

    public boolean isIoProfiling() {
        return ioProfiling;
    }

    private String getArgumentSingleValue(Map<String, List<String>> parsedArgs, String argName) {
        List<String> list = parsedArgs.get(argName);
        if (list == null) {
            return null;
        }

        if (list.isEmpty()) {
            return "";
        }

        return list.get(list.size() - 1);
    }

    private List<String> getArgumentMultiValues(Map<String, List<String>> parsedArgs, String argName) {
        List<String> list = parsedArgs.get(argName);
        if (list == null) {
            return new ArrayList<>();
        }
        return list;
    }
}
