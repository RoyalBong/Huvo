package com.huvo.attendance.calendar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.attendance.calendar.entity.CompanyWorkingDay;

/**
 * The company's working weekdays (Section 5.1, {@code company_working_days}).
 *
 * <p>Configurable, so neither the engine nor the week-reset job hardcodes Monday to Friday - §5.1
 * calls out Mon-Sat as a supported configuration.
 */
public interface CompanyWorkingDayRepository extends JpaRepository<CompanyWorkingDay, Long> {

  /** The configured weekdays for a company, as ISO day-of-week values (1 = Monday). */
  List<CompanyWorkingDay> findByCompanyId(Long companyId);
}
