package com.huvo.payroll.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.audit.AuditClient;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Builds the shared {@link AuditClient} (Huvo_Backend_Context.md Sections 3.4, 7).
 *
 * <p>The library stays framework-free, so each service supplies its own wiring. Credentials come
 * from the default AWS provider chain - the instance role on EC2, the developer's {@code
 * AWS_PROFILE} locally - and no key is ever configured in code.
 *
 * <p>Returns null when no table is configured, so a build agent running the unit tests (Section 9)
 * needs no AWS credentials. The guaranteed-audit paths treat a missing client as a hard failure,
 * because an unauditable leave approval must not succeed.
 */
@Configuration
public class AuditConfig {

  /**
   * @param tableName the audit table, e.g. {@code huvo-dev-audit-log} locally
   * @param region the AWS region
   * @return the client, or null when auditing is not configured
   */
  @Bean
  AuditClient auditClient(
      @Value("${huvo.audit.table:}") String tableName,
      @Value("${AWS_REGION:ap-south-1}") String region) {
    if (tableName.isBlank()) {
      return null;
    }
    return new AuditClient(DynamoDbClient.builder().region(Region.of(region)).build(), tableName);
  }
}
