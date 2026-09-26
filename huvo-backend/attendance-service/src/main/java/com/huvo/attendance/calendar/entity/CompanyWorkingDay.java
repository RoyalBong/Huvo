package com.huvo.attendance.calendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One weekday the company works (Huvo_Backend_Context.md Section 5.1).
 *
 * <p>Stored as an ISO day-of-week (1 = Monday .. 7 = Sunday) so the row is a fact about the
 * calendar rather than about any particular date, and so a Mon-Sat company is six rows.
 *
 * <p>Composite key, hence {@link CompanyWorkingDayKey}. The {@code @IdClass} is not optional: two
 * {@code @Id} fields without one fails when the repository is built, not at compile time.
 */
@Entity
@Table(name = "company_working_days")
@IdClass(CompanyWorkingDayKey.class)
@Data
// No @AllArgsConstructor here: it would duplicate the explicit constructor below, and a composite
// key gains nothing from a generated one.
@NoArgsConstructor
public class CompanyWorkingDay {

  @Id
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Id
  @Column(name = "day_of_week", nullable = false)
  private Integer dayOfWeek;

  public CompanyWorkingDay(Long companyId, Integer dayOfWeek) {
    this.companyId = companyId;
    this.dayOfWeek = dayOfWeek;
  }
}
