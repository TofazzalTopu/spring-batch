package com.batch.employee.repository;

import com.batch.employee.dto.BatchImport;
import com.batch.employee.dto.ImportStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.batch.employee.constants.AppConstant.CREATED_AT;
import static com.batch.employee.constants.AppConstant.IMPORT_ID;

@Repository
@RequiredArgsConstructor
public class BatchImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public String findStatus(Long importId) {

        return jdbcTemplate.queryForObject(
                """
                        SELECT status
                        FROM batch_import
                        WHERE import_id = ?
                        """,
                String.class,
                importId
        );
    }

    public Long createImport(String fileName) {

        String sql = """
                INSERT INTO batch_import
                    (file_name, status)
                VALUES
                    (?, 'RECEIVED')
                """;

        jdbcTemplate.update(sql, fileName);

        return jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()",
                Long.class
        );
    }

    public String findFileName(Long importId) {

        return jdbcTemplate.queryForObject(
                """
                        SELECT file_name
                        FROM batch_import
                        WHERE import_id = ?
                        """,
                String.class,
                importId
        );
    }

    public List<Long> findReceivedImports(int limit) {

        return jdbcTemplate.query(
                """
                        SELECT import_id
                        FROM batch_import
                        WHERE status = 'RECEIVED'
                        ORDER BY import_id
                        LIMIT ?
                        """,
                (rs, rowNum) -> rs.getLong(IMPORT_ID),
                limit
        );
    }

    /**
     * Atomic claim.
     * <p>
     * Only one scheduler/API process can change RECEIVED -> PROCESSING.
     */
    public boolean markProcessing(Long importId) {

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

    public int markCompleted(Long importId) {

        String sql = """
        UPDATE batch_import
        SET status = 'COMPLETED',
            completed_at = CURRENT_TIMESTAMP,
            error_message = NULL
        WHERE import_id = ?
          AND status = 'PROCESSING'
        """;

        return jdbcTemplate.update(sql, importId);
    }

    public void markFailed(
            Long importId,
            String errorMessage) {

        jdbcTemplate.update(
                """
                        UPDATE batch_import
                        SET status = 'FAILED',
                            completed_at = CURRENT_TIMESTAMP,
                            error_message = ?
                        WHERE import_id = ?
                        """,
                errorMessage,
                importId
        );
    }

    public ImportStatusResponse findImportStatus(Long importId) {

        return jdbcTemplate.queryForObject(
                """
                        SELECT
                            bi.import_id,
                            bi.file_name,
                            bi.status,
                            bi.created_at,
                            bi.started_at,
                            bi.completed_at,
                            bi.error_message,
                            
                            COALESCE(SUM(bse.read_count), 0) AS read_count,
                            COALESCE(SUM(bse.write_count), 0) AS write_count,
                            COALESCE(SUM(bse.filter_count), 0) AS filter_count,
                            
                            COALESCE(
                                SUM(
                                    bse.read_skip_count
                                    + bse.write_skip_count
                                    + bse.process_skip_count
                                ),
                                0
                            ) AS skip_count,
                            
                            COALESCE(SUM(bse.commit_count), 0) AS commit_count,
                            COALESCE(SUM(bse.rollback_count), 0) AS rollback_count
                            
                        FROM batch_import bi
                            
                        LEFT JOIN BATCH_STEP_EXECUTION bse
                            ON bse.job_execution_id = bi.job_execution_id
                            
                        WHERE bi.import_id = ?
                            
                        GROUP BY
                            bi.import_id,
                            bi.file_name,
                            bi.status,
                            bi.created_at,
                            bi.started_at,
                            bi.completed_at,
                            bi.error_message
                        """,
                (rs, rowNum) -> new ImportStatusResponse(

                        rs.getLong(IMPORT_ID),

                        rs.getString("file_name"),

                        rs.getString("status"),

                        rs.getTimestamp(CREATED_AT) != null
                                ? rs.getTimestamp(CREATED_AT)
                                .toLocalDateTime()
                                : null,

                        rs.getTimestamp("started_at") != null
                                ? rs.getTimestamp("started_at")
                                .toLocalDateTime()
                                : null,

                        rs.getTimestamp("completed_at") != null
                                ? rs.getTimestamp("completed_at")
                                .toLocalDateTime()
                                : null,

                        rs.getString("error_message"),

                        rs.getLong("read_count"),

                        rs.getLong("write_count"),

                        rs.getLong("filter_count"),

                        rs.getLong("skip_count"),

                        rs.getLong("commit_count"),

                        rs.getLong("rollback_count")
                ),
                importId
        );
    }

    public void updateJobExecutionId(Long importId, Long jobExecutionId) {

        jdbcTemplate.update(
                """
                        UPDATE batch_import
                        SET job_execution_id = ?
                        WHERE import_id = ?
                        """,
                jobExecutionId,
                importId
        );
    }

    public Optional<BatchImport> findById(Long importId) {

        String sql = """
                SELECT
                    import_id,
                    file_name,
                    status,
                    created_at,
                    started_at,
                    completed_at,
                    error_message,
                    job_execution_id
                FROM batch_import
                WHERE import_id = ?
                """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setLong(1, importId),
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    BatchImport item = new BatchImport();

                    item.setImportId(rs.getLong(IMPORT_ID));
                    item.setFileName(rs.getString("file_name"));
                    item.setStatus(rs.getString("status"));
                    item.setCreatedAt(
                            rs.getTimestamp(CREATED_AT).toLocalDateTime()
                    );

                    return Optional.of(item);
                }
        );
    }

}