package com.example.workfloworchestrator.example;

// Configuration for the example profile
@org.springframework.context.annotation.Configuration
@org.springframework.context.annotation.Profile("example")
class ExampleConfiguration {

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.ApplicationRunner applicationRunner(WorkflowRunner runner) {
        return args -> {
            // Add a small delay to ensure everything is initialized
            Thread.sleep(1000);
            runner.runExample();
        };
    }
}
