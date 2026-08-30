package com.researchspace.conversion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConversionSidecarApplication {

  public static void main(String[] args) {
    SpringApplication.run(ConversionSidecarApplication.class, args);
  }
}
