package com.huvo.attendance.shift.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.attendance.shift.entity.Shift;

/** Shift definitions. Managed by an administrator, never by the engine. */
public interface ShiftRepository extends JpaRepository<Shift, Long> {

  Optional<Shift> findByName(String name);
}
