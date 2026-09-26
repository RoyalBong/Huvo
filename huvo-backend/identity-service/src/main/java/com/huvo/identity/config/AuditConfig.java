package com.huvo.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.audit.AuditClient;
import com.huvo.identity.audit.AuditRecorder;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Builds the shared {@link AuditClient} for this service.
 *
 * <p>The library stays framework-free (Section 10): each service supplies its own wiring, so
 * huvo-audit-client carries no Spring dependency and a non-Spring service can still use it.
 *
 * <p>Credentials come from the default AWS provider chain - on EC2 that is the instance role
 * (Section 9), locally it is the developer's {@code AWS_PROFILE}. No key is ever configured in
 * code, and there is deliberately no DynamoDB Local: Section 8.2 rules it out to avoid behavioural
 * drift, so the dev table is a real, low-cost on-demand table instead.
 *
 * <p>Disabled when no table name is set, so a build agent running the unit tests (Section 9) does
 * not need AWS credentials. The mutation paths treat a missing client as "audit unavailable" and
 * log it rather than failing the operation.
 */
@Configuration
public class AuditConfig {

  /**
   * @param tableName the audit table, e.g. {@code huvo-dev-audit-log} locally and {@code
   *     huvo_audit_log} in prod
   * @param region the AWS region, defaulting to the instance's own region
   * @return the client, or null when auditing is not configured for this environment
   */
  /**
   * Every employee and department mutation in this service is recorded best-effort, on purpose. The
   * database write has already committed by the time the audit row is appended, so a DynamoDB
   * failure is logged and swallowed: returning an error would tell the client its create was
   * rejected when the row is actually there.
   *
   * <p>Use {@link AuditRecorder#recordRequired} where the audit row <em>is</em> the operation, not
   * an observation of it — §5.2's manual admin attendance override, and any future admin or
   * financial action.
   *
   * <p>The consequence worth knowing before reading this trail: a mutation can succeed without
   * leaving a row, so the log is near-complete, not authoritative. Nothing else here claims
   * otherwise.
   */
  @Bean
  AuditClient auditClient(
      @Value("${huvo.audit.table:}") String tableName,
      @Value("${AWS_REGION:ap-south-1}") String region) {
    if (tableName.isBlank()) {
      return null;
    }
    DynamoDbClient dynamo = DynamoDbClient.builder().region(Region.of(region)).build();
    return new AuditClient(dynamo, tableName);
  }
}
