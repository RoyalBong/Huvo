package com.huvo.attendance.calendar.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * The composite primary key of {@link CompanyWorkingDay}: the company, and the ISO weekday.
 *
 * <p>Separate and public because JPA requires it to be a top-level type. {@code equals}/{@code
 * hashCode} are declared because JPA uses the key when merging or detaching an instance, and a
 * generated identity-based implementation would make every instance of the same row unequal.
 *
 * @param companyId the company
 * @param dayOfWeek the ISO weekday, 1 = Monday
 */
public record CompanyWorkingDayKey(Long companyId, Integer dayOfWeek) implements Serializable {

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CompanyWorkingDayKey key)) {
      return false;
    }
    return Objects.equals(companyId, key.companyId) && Objects.equals(dayOfWeek, key.dayOfWeek);
  }

  @Override
  public int hashCode() {
    return Objects.hash(companyId, dayOfWeek);
  }
}
