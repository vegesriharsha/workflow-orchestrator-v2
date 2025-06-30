package com.example.workfloworchestrator.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Standalone example application demonstrating JSON attribute extraction
 * Run this with: ./gradlew bootRun -PmainClass=com.example.workfloworchestrator.example.JsonAttributeExtractionDemo --args="--spring.profiles.active=example"
 */
@SpringBootApplication(scanBasePackages = "com.example.workfloworchestrator")
public class JsonAttributeExtractionDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Starting JSON Attribute Extraction Demo...");
        
        ConfigurableApplicationContext context = SpringApplication.run(JsonAttributeExtractionDemo.class, args);

        try {
            // Get the example bean and execute
            TaskAttributeExtractionExample example = context.getBean(TaskAttributeExtractionExample.class);
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🎯 JSON ATTRIBUTE EXTRACTION DEMONSTRATION");
            System.out.println("=".repeat(60));
            
            example.runExample();
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("✅ Demo completed successfully!");
            System.out.println("=".repeat(60));
            
        } catch (Exception e) {
            System.err.println("❌ Error running demo: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close the context when done
            context.close();
        }
    }
}
