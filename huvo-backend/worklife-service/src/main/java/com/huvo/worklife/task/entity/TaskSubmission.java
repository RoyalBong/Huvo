package com.huvo.worklife.task.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A file handed in against a task (Section 6.2).
 *
 * <p>Object key and metadata only, never the bytes: the client uploaded straight to S3 with a
 * pre-signed URL, so the document never passes through this service. That is the point of the
 * pre-signed design - a 40MB file does not consume an EC2 t3.micro's heap on the way in.
 */
@Entity
@Table(name = "task_submission")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "task_id", nullable = false)
  private Long taskId;

  @Column(name = "employee_id", nullable = false)
  private Long employeeId;

  @Column(name = "object_key", nullable = false, length = 500)
  private String objectKey;

  @Column(name = "original_name", length = 255)
  private String originalName;

  @Column(name = "content_type", length = 120)
  private String contentType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "submitted_at", nullable = false)
  private LocalDateTime submittedAt;
}
