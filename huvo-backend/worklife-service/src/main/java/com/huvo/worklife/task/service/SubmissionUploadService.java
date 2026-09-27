package com.huvo.worklife.task.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.BusinessRuleException;
import com.huvo.worklife.exception.NotFoundException;
import com.huvo.worklife.task.entity.Task;
import com.huvo.worklife.task.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 pre-signed upload URLs (Section 6.2).
 *
 * <p>The client uploads straight to S3 and this service only stores the resulting key. The bytes
 * never traverse the API, which is the reason for the design: an EC2 t3.micro (Section 8.1) has no
 * business proxying document uploads.
 *
 * <p>Section 8.1 rules out emulating S3 locally, so there is no local fake - the presigner talks to
 * the real dev bucket, and the tests cover the service's own decision-making rather than the SDK.
 */
@Service
@RequiredArgsConstructor
public class SubmissionUploadService {

  private final TaskRepository tasks;
  private final S3Presigner presigner;

  @Value("${huvo.s3.submissions-bucket:}")
  private String bucket;

  @Value("${huvo.task.upload-url-ttl-seconds:900}")
  private long ttlSeconds;

  /**
   * Mints a pre-signed URL for uploading one submission.
   *
   * <p>The key is generated <em>server-side</em> and namespaced by task. A client-supplied key
   * would let one employee overwrite another's document, or write anywhere in the bucket.
   *
   * @param principal the assignee
   * @param taskId the task being submitted against
   * @param fileName the client's filename, used only as a label in the key
   * @return the pre-signed URL and the key to record once the upload completes
   */
  public PresignedUpload presign(HuvoPrincipal principal, Long taskId, String fileName) {
    Task task =
        tasks.findById(taskId).orElseThrow(() -> new NotFoundException("No task " + taskId));
    Long employeeId = principal.employeeId();
    if (employeeId == null || !employeeId.equals(task.getEmployeeId())) {
      throw new BusinessRuleException("This task is assigned to someone else");
    }
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException(
          "No S3 bucket is configured (S3_SUBMISSIONS_BUCKET), so a submission cannot be "
              + "uploaded. Refusing rather than handing out a URL that cannot work.");
    }

    String key = "tasks/" + taskId + "/" + java.util.UUID.randomUUID() + "-" + sanitise(fileName);
    PutObjectRequest put =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/octet-stream")
            .build();
    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(ttlSeconds))
            .putObjectRequest(put)
            .build();

    return new PresignedUpload(key, presigner.presignPutObject(presignRequest).url().toString());
  }

  /**
   * The client filename reduced to something safe inside an object key.
   *
   * <p>Path separators and traversal segments are removed rather than encoded: a filename is a
   * label, not a path, and a label containing {@code ../} should not survive into a key at all.
   */
  private static String sanitise(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "upload";
    }
    String cleaned = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    while (cleaned.startsWith(".")) {
      cleaned = cleaned.substring(1);
    }
    return cleaned.isBlank() ? "upload" : cleaned;
  }

  /**
   * What the client needs to complete the upload.
   *
   * @param objectKey the key to record once the upload finishes
   * @param uploadUrl the pre-signed URL
   */
  public record PresignedUpload(String objectKey, String uploadUrl) {}
}
