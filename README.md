````markdown
# Spring Batch Employee Import

Spring Batch employee import application
covering the configuration, architecture, database tables, batch flow, staging process,
asynchronous API, status API, duplicate protection, job parameters, monitoring,
and production considerations.

A production-oriented Spring Batch application for processing large CSV files into MySQL using a **staging-table architecture**.

The application is designed to process large files efficiently using:

- Spring Boot 3.3.2
- Java 17
- Spring Batch 5.1.2
- Spring JDBC
- MySQL
- HikariCP
- Asynchronous job execution
- Batch metadata tables
- Import tracking
- Staging table
- Chunk-oriented processing
- Job and step execution monitoring
- REST APIs
- Duplicate import protection

---

# 1. Architecture

The application follows this high-level architecture:

```text
                         REST API
                            |
                            |
                   POST /api/imports/execute/{id}
                            |
                            v
                 EmployeeImportService
                            |
                     Claim Import
                            |
                            v
                   status = PROCESSING
                            |
                            v
              EmployeeAsyncJobLauncher
                            |
                      @Async Executor
                            |
                            v
                  EmployeeJobLauncher
                            |
                     JobLauncher
                            |
                            v
                  employeeImportJob
                     /          \
                    /            \
                   v              v
       employeeStagingStep    employeeLoadStep
              |                     |
              v                     v
         CSV Reader           Staging Reader
              |                     |
              v                     v
       Processor              Employee Writer
              |                     |
              v                     v
     employee_staging            employee
````

The application separates the import into two stages:

```text
CSV
 |
 v
employee_staging
 |
 v
employee
```

This provides better validation, recovery, auditing, and operational control than loading the CSV directly into the final table.

---

# 2. Technology Stack

| Technology       | Version             |
| ---------------- | ------------------- |
| Java             | 17                  |
| Spring Boot      | 3.3.2               |
| Spring Batch     | 5.1.2               |
| Spring Framework | 6.1.x               |
| Spring JDBC      | Spring Boot managed |
| MySQL            | Local / Production  |
| HikariCP         | 5.1.x               |
| Maven            | 3.6+                |
| Lombok           | Project configured  |
| Spotless         | 3.1.0               |

---

# 3. Maven Dependencies

Main dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jdbc</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

For this application, JPA is not required for the Batch processing path.

Spring JDBC is preferable because the application performs:

* Bulk inserts
* JDBC cursor reads
* SQL updates
* Batch metadata access
* Staging-table operations

---

# 4. Project Structure

```text
src/main/java/com/batch/employee
│
├── EmployeeApplication.java
│
├── config
│   ├── AsyncConfig.java
│   └── BatchConfig.java
│
├── controller
│   └── EmployeeImportController.java
│
├── dto
│   └── ImportStatusResponse.java
│
├── job
│   ├── EmployeeAsyncJobLauncher.java
│   └── EmployeeJobLauncher.java
│
├── listener
│   └── JobCompletionListener.java
│
├── model
│   ├── Employee.java
│   └── EmployeeStaging.java
│
├── processor
│   ├── EmployeeProcessor.java
│   └── EmployeeStagingProcessor.java
│
├── reader
│   ├── EmployeeItemReader.java
│   └── EmployeeStagingReader.java
│
├── writer
│   └── EmployeeItemWriter.java
│
├── repository
│   └── BatchImportRepository.java
│
└── service
    └── EmployeeImportService.java
```

---

# 5. Application Configuration

Example `application.yml`:

```yaml
spring:
  application:
    name: employee

  datasource:
    url: jdbc:mysql://localhost:3306/test_db?useSSL=false&serverTimezone=Asia/Kuala_Lumpur&allowPublicKeyRetrieval=true
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

    hikari:
      pool-name: EmployeeBatchPool
      maximum-pool-size: 10
      minimum-idle: 2
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-timeout: 30000
      leak-detection-threshold: 2000
      auto-commit: true
      read-only: false

  batch:
    job:
      enabled: false

    jdbc:
      initialize-schema: always

server:
  port: 8080

app:
  import:
    input-directory: data/import
```

---

# 6. Important Spring Batch Configuration

This setting is important:

```yaml
spring:
  batch:
    job:
      enabled: false
```

Why?

Spring Boot normally attempts to automatically execute a Batch job when the application starts.

In this application, jobs are launched explicitly through:

```text
REST API
```

or:

```text
Scheduler
```

Therefore automatic startup is disabled.

The application should start without automatically processing an import.

---

# 7. Database

The application uses three categories of tables.

## Application tables

```text
batch_import
employee_staging
employee
```

## Spring Batch metadata tables

Examples:

```text
BATCH_JOB_INSTANCE
BATCH_JOB_EXECUTION
BATCH_JOB_EXECUTION_PARAMS
BATCH_STEP_EXECUTION
BATCH_JOB_EXECUTION_CONTEXT
BATCH_STEP_EXECUTION_CONTEXT
```

These tables are managed by Spring Batch.

---

# 8. batch_import Table

This table represents a business-level import request.

Example:

```sql
CREATE TABLE batch_import (
    import_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    file_name VARCHAR(500) NOT NULL,

    status VARCHAR(30) NOT NULL,

    job_execution_id BIGINT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    started_at TIMESTAMP NULL,

    completed_at TIMESTAMP NULL,

    error_message VARCHAR(2000) NULL
);
```

Typical lifecycle:

```text
RECEIVED
    |
    v
PROCESSING
    |
    +------> COMPLETED
    |
    +------> FAILED
```

---

# 9. employee_staging Table

The staging table temporarily stores processed CSV records.

Example:

```sql
CREATE TABLE employee_staging (
    staging_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    import_id BIGINT NOT NULL,

    row_number BIGINT NOT NULL,

    name VARCHAR(255),

    email VARCHAR(255),

    salary DECIMAL(15,2),

    validation_status VARCHAR(20) DEFAULT 'VALID',

    error_message VARCHAR(1000),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_employee_staging_import_id (import_id),

    INDEX idx_employee_staging_import_status (
        import_id,
        validation_status
    )
);
```

The important index is:

```sql
INDEX idx_employee_staging_import_id (import_id)
```

because the second Batch step reads records using:

```sql
WHERE import_id = ?
```

---

# 10. employee Table

Example:

```sql
CREATE TABLE employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    name VARCHAR(255),

    email VARCHAR(255),

    salary DECIMAL(15,2)
);
```

---

# 11. Why Use a Staging Table?

Instead of:

```text
CSV
 |
 v
employee
```

the application uses:

```text
CSV
 |
 v
employee_staging
 |
 v
employee
```

Advantages:

### Validation

Invalid records can be identified before loading the final table.

### Auditability

Every record is associated with:

```text
import_id
row_number
```

### Recovery

A failed final load does not require reading the original CSV again.

### Reprocessing

A specific import can be investigated and processed independently.

### Operational visibility

The application can determine:

```text
Import 101
50000 records
48000 valid
2000 invalid
```

### Isolation

A partially processed import does not immediately contaminate the final table.

---

# 12. Job Structure

The Batch job is:

```java
@Bean
public Job employeeImportJob(
        JobRepository jobRepository,
        Step employeeStagingStep,
        Step employeeLoadStep,
        JobCompletionListener listener) {

    return new JobBuilder("employeeImportJob", jobRepository)
            .listener(listener)
            .start(employeeStagingStep)
            .next(employeeLoadStep)
            .build();
}
```

The execution flow is:

```text
employeeImportJob
        |
        +--> employeeStagingStep
        |
        +--> employeeLoadStep
```

---

# 13. Step 1 - CSV to Staging

```text
CSV
 |
 v
FlatFileItemReader
 |
 v
Employee
 |
 v
EmployeeStagingProcessor
 |
 v
EmployeeStaging
 |
 v
JdbcBatchItemWriter
 |
 v
employee_staging
```

Configuration:

```java
@Bean
public Step employeeStagingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        FlatFileItemReader<Employee> employeeReader,
        EmployeeStagingProcessor processor,
        JdbcBatchItemWriter<EmployeeStaging> employeeStagingWriter) {

    return new StepBuilder("employeeStagingStep", jobRepository)
            .<Employee, EmployeeStaging>chunk(1000, transactionManager)
            .reader(employeeReader)
            .processor(processor)
            .writer(employeeStagingWriter)
            .build();
}
```

The chunk size is:

```text
1000
```

Meaning:

```text
Read 1000
Process 1000
Write 1000
Commit
```

Then continue with the next 1000.

---

# 14. Step 2 - Staging to Employee

The second step reads from:

```text
employee_staging
```

and writes to:

```text
employee
```

Example:

```java
@Bean
public Step employeeLoadStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        JdbcCursorItemReader<EmployeeStaging> employeeStagingReader,
        JdbcBatchItemWriter<EmployeeStaging> employeeLoadWriter) {

    return new StepBuilder("employeeLoadStep", jobRepository)
            .<EmployeeStaging, EmployeeStaging>chunk(1000, transactionManager)
            .reader(employeeStagingReader)
            .writer(employeeLoadWriter)
            .build();
}
```

---

# 15. CSV Reader

The CSV reader is `@StepScope`.

This is important because `importId` is a runtime JobParameter.

Example:

```java
@Bean
@StepScope
public FlatFileItemReader<Employee> employeeReader(
        @Value("#{jobParameters['importId']}") Long importId) {

    String fileName =
            batchImportRepository.findFileName(importId);

    return new FlatFileItemReaderBuilder<Employee>()
            .name("employeeReader")
            .resource(new FileSystemResource(fileName))
            .linesToSkip(1)
            .delimited()
            .names("name", "email", "salary")
            .fieldSetMapper(fieldSetMapper)
            .build();
}
```

The important concept is:

```text
JobParameter
    |
    v
importId
    |
    v
batch_import
    |
    v
file_name
    |
    v
CSV Reader
```

The file name does not need to be passed manually from IntelliJ.

---

# 16. Job Parameters

The application uses:

```java
.addLong("importId", importId)
```

Example:

```java
JobParameters parameters =
        new JobParametersBuilder()
                .addLong("importId", importId)
                .toJobParameters();
```

The resulting Spring Batch JobParameter is:

```text
importId = 5
```

The reader can access it using:

```java
@Value("#{jobParameters['importId']}")
Long importId
```

---

# 17. Why importId Is a Job Parameter

An `importId` uniquely identifies a business import.

Example:

```text
import_id = 1
file = employee_5000.csv

import_id = 2
file = employee_50000.csv

import_id = 3
file = employee_100000.csv
```

Spring Batch also uses JobParameters to identify JobInstances.

Therefore:

```text
employeeImportJob + importId=1
```

is different from:

```text
employeeImportJob + importId=2
```

---

# 18. Job Instance Protection

Spring Batch prevents running the same completed JobInstance again with the same identifying parameters.

For example:

```text
employeeImportJob + importId=5
```

has already completed.

Running the same combination again can result in:

```text
JobInstanceAlreadyCompleteException
```

The application therefore treats each business import as a separate import.

If the same physical CSV needs to be processed again, create a new:

```text
import_id
```

---

# 19. Import Claiming

Before launching a job, the application atomically claims the import.

SQL:

```sql
UPDATE batch_import
SET
    status = 'PROCESSING',
    started_at = CURRENT_TIMESTAMP
WHERE import_id = ?
  AND status = 'RECEIVED';
```

Java:

```java
public boolean claimImport(Long importId) {

    int updated = jdbcTemplate.update(
            """
            UPDATE batch_import
            SET status = 'PROCESSING',
                started_at = CURRENT_TIMESTAMP
            WHERE import_id = ?
              AND status = 'RECEIVED'
            """,
            importId
    );

    return updated == 1;
}
```

This prevents duplicate processing.

Example:

```text
Request 1
    |
    +--> RECEIVED -> PROCESSING
    |
    +--> Job starts


Request 2
    |
    +--> PROCESSING
    |
    +--> rejected
```

---

# 20. Duplicate Protection

If an import is already completed:

```text
status = COMPLETED
```

another execution request should not start another job.

The API returns:

```http
409 Conflict
```

Example:

```json
{
    "importId": 5,
    "status": "COMPLETED",
    "message": "Import 5 has already been completed"
}
```

If it is currently processing:

```json
{
    "importId": 5,
    "status": "PROCESSING",
    "message": "Import 5 is already being processed"
}
```

---

# 21. Asynchronous Execution

The Batch job is executed asynchronously.

Executor configuration:

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "batchTaskExecutor")
    public Executor batchTaskExecutor() {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("batch-job-");

        executor.initialize();

        return executor;
    }
}
```

This creates threads such as:

```text
batch-job-1
batch-job-2
```

---

# 22. Why Asynchronous Processing?

Without asynchronous execution:

```text
HTTP request
    |
    v
Start Batch
    |
    |
    | 50,000 records
    |
    v
Job completes
    |
    v
HTTP response
```

The HTTP request could remain open for a long time.

With asynchronous processing:

```text
HTTP request
    |
    v
Validate/claim import
    |
    v
Submit Batch
    |
    v
HTTP 202 Accepted
    |
    |
    +--------------------+
                         |
                         v
                    Batch Job
                         |
                         v
                    COMPLETED
```

The API is therefore suitable for long-running imports.

---

# 23. EmployeeAsyncJobLauncher

Because `@Async` uses Spring proxies, the asynchronous method is placed in a separate component.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeAsyncJobLauncher {

    private final EmployeeJobLauncher jobLauncher;
    private final BatchImportRepository batchImportRepository;

    @Async("batchTaskExecutor")
    public void launch(Long importId) {

        try {

            log.info(
                    "Starting batch job asynchronously. importId={}",
                    importId
            );

            jobLauncher.launch(importId);

        } catch (Exception e) {

            log.error(
                    "Failed to launch batch job. importId={}",
                    importId,
                    e
            );

            batchImportRepository.markFailed(
                    importId,
                    e.getMessage()
            );
        }
    }
}
```

---

# 24. EmployeeJobLauncher

```java
@Component
@RequiredArgsConstructor
public class EmployeeJobLauncher {

    private final JobLauncher jobLauncher;
    private final Job employeeImportJob;
    private final BatchImportRepository batchImportRepository;

    public JobExecution launch(Long importId) throws Exception {

        JobParameters parameters =
                new JobParametersBuilder()
                        .addLong("importId", importId)
                        .toJobParameters();

        JobExecution execution =
                jobLauncher.run(
                        employeeImportJob,
                        parameters
                );

        batchImportRepository.updateJobExecutionId(
                importId,
                execution.getId()
        );

        return execution;
    }
}
```

The dependency direction is:

```text
EmployeeImportService
        |
        v
EmployeeAsyncJobLauncher
        |
        v
EmployeeJobLauncher
        |
        v
JobLauncher
```

The JobLauncher does not depend on the Service.

This prevents circular dependencies.

---

# 25. Job Execution ID

`batch_import` contains:

```text
job_execution_id
```

Example:

```text
import_id = 5
job_execution_id = 24
```

This creates a direct relationship:

```text
batch_import
       |
       | job_execution_id
       v
BATCH_JOB_EXECUTION
       |
       v
BATCH_STEP_EXECUTION
```

This is preferable to trying to find the job execution by parsing JobParameters.

---

# 26. Job Completion Listener

The listener synchronizes the application-level import status with Spring Batch.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JobCompletionListener
        implements JobExecutionListener {

    private final BatchImportRepository batchImportRepository;

    @Override
    public void beforeJob(JobExecution jobExecution) {

        Long importId =
                jobExecution.getJobParameters()
                        .getLong("importId");

        log.info(
                "Batch job started. importId={}, executionId={}",
                importId,
                jobExecution.getId()
        );
    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        Long importId =
                jobExecution.getJobParameters()
                        .getLong("importId");

        BatchStatus status =
                jobExecution.getStatus();

        log.info(
                "Batch job finished. importId={}, executionId={}, status={}",
                importId,
                jobExecution.getId(),
                status
        );

        if (status == BatchStatus.COMPLETED) {

            batchImportRepository.markCompleted(importId);

        } else if (status == BatchStatus.FAILED) {

            String errorMessage =
                    jobExecution.getAllFailureExceptions()
                            .stream()
                            .map(Throwable::getMessage)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse("Batch job failed");

            batchImportRepository.markFailed(
                    importId,
                    errorMessage
            );
        }
    }
}
```

The lifecycle becomes:

```text
RECEIVED
   |
   v
PROCESSING
   |
   v
Spring Batch Job
   |
   +--------> COMPLETED
   |
   +--------> FAILED
```

---

# 27. Completion Update

Successful job:

```sql
UPDATE batch_import
SET
    status = 'COMPLETED',
    completed_at = CURRENT_TIMESTAMP
WHERE import_id = ?;
```

Failed job:

```sql
UPDATE batch_import
SET
    status = 'FAILED',
    completed_at = CURRENT_TIMESTAMP,
    error_message = ?
WHERE import_id = ?;
```

---

# 28. REST API

## Execute Import

```http
POST /api/imports/execute/{importId}
```

Example:

```bash
curl -X POST http://localhost:8080/api/imports/execute/5
```

Successful response:

```http
HTTP/1.1 202 Accepted
```

```json
{
    "importId": 5,
    "status": "PROCESSING"
}
```

---

# 29. Already Completed

If:

```text
batch_import.status = COMPLETED
```

then:

```bash
curl -X POST http://localhost:8080/api/imports/execute/5
```

returns:

```http
HTTP/1.1 409 Conflict
```

```json
{
    "importId": 5,
    "status": "COMPLETED",
    "message": "Import 5 has already been completed"
}
```

---

# 30. Already Processing

If:

```text
batch_import.status = PROCESSING
```

the API returns:

```http
HTTP/1.1 409 Conflict
```

```json
{
    "importId": 5,
    "status": "PROCESSING",
    "message": "Import 5 is already being processed"
}
```

---

# 31. Import Status API

```http
GET /api/imports/{importId}
```

Example:

```bash
curl http://localhost:8080/api/imports/5
```

Example response:

```json
{
    "importId": 5,
    "fileName": "employee_5000_unique.csv",
    "status": "COMPLETED",
    "createdAt": "2026-08-11T17:40:10",
    "startedAt": "2026-08-11T17:40:19",
    "completedAt": "2026-08-11T17:42:31",
    "errorMessage": null,
    "readCount": 5000,
    "writeCount": 5000,
    "filterCount": 0,
    "skipCount": 0,
    "commitCount": 5,
    "rollbackCount": 0
}
```

---

# 32. Important: Batch Counts

The application has two steps:

```text
employeeStagingStep
employeeLoadStep
```

Therefore, Spring Batch stores counts for each step.

For a 5,000-row CSV:

```text
employeeStagingStep
    read  = 5000
    write = 5000

employeeLoadStep
    read  = 5000
    write = 5000
```

If an API query uses:

```sql
SUM(read_count)
```

across both steps, the result becomes:

```text
5000 + 5000 = 10000
```

This does NOT mean the CSV had 10,000 records.

It means two processing stages each handled 5,000 records.

For business reporting, use the staging step's read count as the number of source records imported.

---

# 33. Chunk Processing

The application uses:

```java
.chunk(1000, transactionManager)
```

For 5,000 records:

```text
Chunk 1 -> 1000
Chunk 2 -> 1000
Chunk 3 -> 1000
Chunk 4 -> 1000
Chunk 5 -> 1000
```

Approximately:

```text
5 commits
```

For 50,000 records:

```text
50 chunks
```

Approximately:

```text
50 commits
```

Chunk size should be tuned according to:

* Record size
* Database performance
* Available memory
* Transaction duration
* Network latency
* MySQL configuration

A chunk size of `1000` is a reasonable starting point.

---

# 34. JDBC Batch Writer

The staging writer uses JDBC batch operations.

Example:

```java
@Bean
public JdbcBatchItemWriter<EmployeeStaging>
employeeStagingWriter(DataSource dataSource) {

    return new JdbcBatchItemWriterBuilder<EmployeeStaging>()
            .dataSource(dataSource)
            .sql(
                """
                INSERT INTO employee_staging
                    (
                        import_id,
                        row_number,
                        name,
                        email,
                        salary,
                        validation_status
                    )
                VALUES
                    (
                        :importId,
                        :rowNumber,
                        :name,
                        :email,
                        :salary,
                        :validationStatus
                    )
                """
            )
            .beanMapped()
            .build();
}
```

The application does not perform:

```text
INSERT
INSERT
INSERT
INSERT
...
```

as separate application-level operations.

Instead, Spring Batch groups items into chunks and the JDBC writer performs batch operations.

---

# 35. ID Generation

The staging table uses:

```sql
staging_id BIGINT AUTO_INCREMENT
```

The application does NOT do:

```text
SELECT MAX(id)
```

or:

```text
SELECT next ID
```

before every record.

MySQL generates the IDs.

For millions of records this is much more efficient and avoids application-side ID coordination.

---

# 36. Source Row Number

The staging processor maintains the source row number:

```java
private long rowNumber = 0;

@Override
public EmployeeStaging process(Employee employee) {

    rowNumber++;

    EmployeeStaging staging =
            new EmployeeStaging();

    staging.setImportId(importId);
    staging.setRowNumber(rowNumber);

    ...
}
```

This allows records to be traced back to the original CSV row.

---

# 37. Employee Processor

The processor performs normalization.

Example:

```java
@Override
public Employee process(Employee employee) {

    if (employee.getName() != null) {
        employee.setName(
                employee.getName().trim()
        );
    }

    if (employee.getEmail() != null) {
        employee.setEmail(
                employee.getEmail()
                        .trim()
                        .toLowerCase()
        );
    }

    return employee;
}
```

Example:

```text
" John Smith "
```

becomes:

```text
"John Smith"
```

and:

```text
" JOHN@EXAMPLE.COM "
```

becomes:

```text
"john@example.com"
```

---

# 38. HikariCP

The application uses HikariCP for database connection pooling.

Example:

```yaml
spring:
  datasource:
    hikari:
      pool-name: EmployeeBatchPool
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

Important settings:

| Property           | Meaning                       |
| ------------------ | ----------------------------- |
| maximum-pool-size  | Maximum database connections  |
| minimum-idle       | Minimum idle connections      |
| connection-timeout | Maximum wait for a connection |
| idle-timeout       | Idle connection lifetime      |
| max-lifetime       | Maximum connection lifetime   |

---

# 39. Hikari Leak Detection

During development, the application may use:

```yaml
leak-detection-threshold: 2000
```

This means Hikari reports a connection that appears to be held longer than approximately 2 seconds.

Spring Batch's `JdbcCursorItemReader` intentionally keeps a JDBC connection open while processing its cursor.

Therefore, warnings such as:

```text
Apparent connection leak detected
```

do not automatically mean there is a real connection leak.

If the connection is eventually returned:

```text
Previously reported leaked connection
was returned to the pool
```

then it was a long-lived connection rather than an actual leak.

For production, tune or disable leak detection after confirming normal reader behavior.

---

# 40. Hikari Thread Starvation Warning

A message such as:

```text
Thread starvation or clock leap detected
```

usually indicates that the Hikari housekeeper thread was not scheduled for the expected interval.

Possible causes include:

* JVM pause
* Debugger pause
* CPU starvation
* System sleep
* Heavy local resource contention
* Clock adjustment

It is not automatically a database connection failure.

---

# 41. Error Handling

The Batch step can be configured with fault tolerance.

Example:

```java
.faultTolerant()
.retry(SQLException.class)
.retryLimit(3)
.skip(NumberFormatException.class)
.skipLimit(100)
```

Meaning:

### Retry SQL exceptions

```text
SQLException
    |
    +--> retry
    +--> retry
    +--> retry
```

### Skip number format errors

For example:

```text
salary = "ABC"
```

can be skipped according to the configured skip limit.

---

# 42. Production Job Execution Flow

Recommended production flow:

```text
1. Receive CSV
        |
        v
2. Create batch_import
        |
        v
3. status = RECEIVED
        |
        v
4. Generate import_id
        |
        v
5. API/Scheduler triggers import
        |
        v
6. Atomically claim import
        |
        v
7. status = PROCESSING
        |
        v
8. Launch Spring Batch
        |
        v
9. CSV -> employee_staging
        |
        v
10. employee_staging -> employee
        |
        v
11. JobCompletionListener
        |
        +---- COMPLETED
        |
        +---- FAILED
```

---

# 43. Why Not Use IntelliJ Arguments in Production?

During initial development, the job could be started with:

```text
--spring.batch.job.name=employeeImportJob
```

or JobParameters.

That is useful for testing.

For production, the application should not depend on developers manually entering:

```text
importId=5
```

Instead:

```text
REST API
```

or:

```text
Scheduler
```

should determine which import needs processing.

The production system should treat `batch_import` as the source of truth for import requests.

---

# 44. API-Driven Production Execution

Example:

```text
POST /api/imports
```

Create:

```text
import_id = 10
status = RECEIVED
```

Then:

```text
POST /api/imports/execute/10
```

claims and submits the job.

The client gets:

```http
202 Accepted
```

Then it can monitor:

```text
GET /api/imports/10
```

---

# 45. Scheduler-Driven Execution

A scheduler can periodically find:

```sql
SELECT import_id
FROM batch_import
WHERE status = 'RECEIVED'
ORDER BY created_at
LIMIT 10;
```

Then submit each import.

Example architecture:

```text
Scheduler
    |
    v
Find RECEIVED imports
    |
    v
Claim import
    |
    v
Submit Batch job
```

The same `claimImport()` mechanism prevents two scheduler instances from processing the same import.

---

# 46. Multi-Instance Deployment

If multiple application instances run:

```text
Application Instance 1
Application Instance 2
Application Instance 3
```

they may all see:

```text
import_id = 10
status = RECEIVED
```

The atomic update:

```sql
UPDATE batch_import
SET status = 'PROCESSING'
WHERE import_id = ?
AND status = 'RECEIVED'
```

ensures only one instance successfully claims it.

The other instances receive:

```text
updated rows = 0
```

and must not launch the job.

This is an important concurrency control mechanism.

---

# 47. Production Considerations

Before production, review:

## Database indexes

Ensure indexes exist on:

```text
batch_import.import_id
batch_import.status

employee_staging.import_id
employee_staging.import_id + validation_status
```

## Connection pool

Tune:

```text
maximum-pool-size
```

based on actual DB capacity.

## Chunk size

Test:

```text
500
1000
2000
5000
```

and measure:

* Throughput
* Memory
* Transaction duration
* Database CPU
* Lock contention

## File storage

For production, avoid relying only on:

```text
src/main/resources
```

for incoming files.

Use controlled external storage such as:

```text
/opt/application/import
```

or object storage.

## Monitoring

Monitor:

```text
Job status
Step status
Read count
Write count
Skip count
Failure count
Processing duration
Database connections
CPU
Memory
```

---

# 48. Troubleshooting

## Job does not start

Check:

```yaml
spring:
  batch:
    job:
      enabled: false
```

If this is false, the job will not automatically start during application startup.

Use the API or scheduler.

---

## JobInstanceAlreadyCompleteException

Example:

```text
A job instance already exists and is complete
```

This means the same identifying JobParameters were already completed.

Use a new `importId` for a new import attempt.

---

## Unable to claim import

Check:

```sql
SELECT
    import_id,
    status
FROM batch_import
WHERE import_id = ?;
```

Expected state before execution:

```text
RECEIVED
```

If it is:

```text
PROCESSING
```

the import is already running.

If it is:

```text
COMPLETED
```

the import has already finished.

---

## Input resource does not exist

Check the exact file path.

Example:

```bash
find data/import -name "employee_50000_unique.csv" -print
```

Be careful not to concatenate the configured directory twice.

Incorrect:

```text
data/import/data/import/file.csv
```

Correct:

```text
data/import/file.csv
```

---

## Bean definition already exists

If you see:

```text
BeanDefinitionOverrideException
```

check for duplicate classes or bean methods:

```bash
find src -name "EmployeeStagingReader.java" -print
```

and:

```bash
grep -R "employeeStagingReader" src/main/java
```

Then perform:

```bash
mvn clean
```

and restart the application.

---

# 49. Testing with 5,000 Records

Example CSV:

```text
employee_5000_unique.csv
```

Expected:

```text
Source records = 5000
```

Step 1:

```text
readCount  = 5000
writeCount = 5000
```

Step 2:

```text
readCount  = 5000
writeCount = 5000
```

Business-level imported records:

```text
5000
```

---

# 50. Testing with 50,000 Records

Example:

```text
employee_50000_unique.csv
```

With:

```java
chunk(1000)
```

approximately:

```text
50 chunks
```

for each step.

The application should process:

```text
CSV
 ↓
50,000 staging records
 ↓
50,000 final records
```

assuming all records are valid and successfully processed.

---

# 51. Example Complete Flow

Suppose the API creates:

```text
import_id = 20
```

Database:

```text
batch_import

import_id | file_name                  | status
----------+----------------------------+---------
20        | employee_50000_unique.csv  | RECEIVED
```

Execute:

```bash
curl -X POST \
  http://localhost:8080/api/imports/execute/20
```

Claim:

```text
RECEIVED
    ↓
PROCESSING
```

Spring Batch:

```text
employeeImportJob
       |
       +--> employeeStagingStep
       |       |
       |       +--> 50,000 records
       |
       +--> employeeLoadStep
               |
               +--> 50,000 records
```

Completion listener:

```text
JobExecution = COMPLETED
```

Application table:

```text
PROCESSING
    ↓
COMPLETED
```

Status API:

```bash
curl http://localhost:8080/api/imports/20
```

Response:

```json
{
    "importId": 20,
    "fileName": "employee_50000_unique.csv",
    "status": "COMPLETED",
    "readCount": 50000,
    "writeCount": 50000,
    "filterCount": 0,
    "skipCount": 0
}
```

---

# 52. Recommended Status Lifecycle

```text
                    +-------------+
                    |   RECEIVED  |
                    +------+------+
                           |
                           | claim
                           v
                    +-------------+
                    | PROCESSING  |
                    +------+------+
                           |
                  +--------+--------+
                  |                 |
                  v                 v
          +---------------+   +-----------+
          |   COMPLETED   |   |  FAILED   |
          +---------------+   +-----------+
```

An import should not normally move backwards:

```text
COMPLETED -> RECEIVED
```

If the same file needs to be processed again, create a new import.

---

# 53. Key Design Principles

This application follows several important Spring Batch principles:

### 1. Chunk-oriented processing

Large files are processed in manageable chunks.

### 2. JDBC batch writing

Records are written efficiently using JDBC batching.

### 3. Staging architecture

Input data is isolated before final loading.

### 4. Database-generated IDs

MySQL generates primary keys using `AUTO_INCREMENT`.

### 5. Job parameters

`importId` identifies the business import and Spring Batch JobInstance.

### 6. Atomic claiming

Database status transition prevents duplicate execution.

### 7. Asynchronous execution

Long-running jobs do not block HTTP requests.

### 8. JobExecutionListener

Application import status is synchronized with actual Batch job status.

### 9. Spring Batch metadata

Execution information is stored in the standard Batch metadata tables.

### 10. REST monitoring

Clients can query import status without waiting for the Batch job.

---

# 54. Current API Summary

| Method | Endpoint                          | Purpose             |
| ------ | --------------------------------- | ------------------- |
| POST   | `/api/imports/execute/{importId}` | Start an import     |
| GET    | `/api/imports/{importId}`         | Check import status |

Example:

```bash
# Start
curl -X POST \
  http://localhost:8080/api/imports/execute/5

# Check status
curl \
  http://localhost:8080/api/imports/5
```

---

# 55. Final Architecture

```text
                       CLIENT
                         |
                         |
                REST API / Scheduler
                         |
                         v
                EmployeeImportService
                         |
                    Claim Import
                         |
                +--------+--------+
                |                 |
             FAILED           CLAIMED
                |                 |
                |                 v
                |        Async Job Launcher
                |                 |
                |                 v
                |          JobLauncher
                |                 |
                |                 v
                |       employeeImportJob
                |                 |
                |        +--------+--------+
                |        |                 |
                |        v                 v
                |  Staging Step       Load Step
                |        |                 |
                |        v                 v
                |   CSV Reader       Staging Reader
                |        |                 |
                |        v                 v
                |   CSV Processor      JDBC Writer
                |        |                 |
                |        v                 v
                | employee_staging       employee
                |        |
                |        |
                +--------+
                         |
                         v
                JobCompletionListener
                         |
                  +------+------+
                  |             |
                  v             v
              COMPLETED       FAILED
                  |
                  v
              batch_import
                  |
                  v
             Status API
```

---

# 56. Summary

The application provides a scalable foundation for large CSV imports:

```text
CSV
 ↓
Import Registration
 ↓
batch_import
 ↓
Atomic Import Claim
 ↓
Asynchronous Spring Batch Job
 ↓
CSV Reader
 ↓
Processor
 ↓
employee_staging
 ↓
Staging Reader
 ↓
employee
 ↓
Job Completion Listener
 ↓
batch_import status
 ↓
REST Status API
```

For a 50,000-record file, the application processes records using chunks rather than loading the entire file into memory.

The `importId` provides the business-level identity of each import, while Spring Batch's `JobExecution` and `StepExecution` provide technical execution tracking.

The combination of:

* `batch_import`
* `employee_staging`
* Spring Batch metadata
* atomic claiming
* asynchronous execution
* chunk processing
* JDBC batch writing
* job completion listener
* REST status API

provides a solid foundation for moving the application from a local proof-of-concept toward a production-grade batch import architecture.

```
```
