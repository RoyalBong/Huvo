package com.huvo.worklife.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * The S3 presigner used for task submissions (Huvo_Backend_Context.md Section 6.2).
 *
 * <p>Section 8.1 rules out emulating S3 locally, so there is no local stand-in and no in-memory
 * fake: the SDK resolves credentials from the default provider chain (instance role on EC2, {@code
 * AWS_PROFILE} locally) and a dev bucket is a deliberate, documented prerequisite.
 *
 * <p>Separate from the DynamoDB client that {@code huvo-audit-client} builds, because presigning is
 * a local signing operation - it needs a region and credentials but makes no call, so a test can
 * mint a URL without the bucket being reachable.
 */
@Configuration
public class S3Config {

  /**
   * @param region the region the submissions bucket lives in
   * @return the presigner
   */
  @Bean
  S3Presigner s3Presigner(@Value("${huvo.s3.region:ap-south-1}") String region) {
    return S3Presigner.builder().region(Region.of(region)).build();
  }
}
