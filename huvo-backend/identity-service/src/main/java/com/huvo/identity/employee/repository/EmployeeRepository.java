package com.huvo.identity.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.identity.employee.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {}
