package com.example.workfloworchestrator.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Example application demonstrating how to create and run workflows
 * Run this with: ./gradlew bootRun --args="--spring.profiles.active=example"
 */
@SpringBootApplication(scanBasePackages = "com.example.workfloworchestrator")
public class WorkflowRunnerExample {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(WorkflowRunnerExample.class, args);

        // Get the runner bean and execute
        WorkflowRunner runner = context.getBean(WorkflowRunner.class);
        runner.runExample();

        // Close the context when done
        context.close();
    }
}

