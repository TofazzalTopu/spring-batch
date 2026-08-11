package com.batch.employee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EmployeeApplication {

  public static void main(String[] args) {
    SpringApplication.run(EmployeeApplication.class, args);
  }
}
