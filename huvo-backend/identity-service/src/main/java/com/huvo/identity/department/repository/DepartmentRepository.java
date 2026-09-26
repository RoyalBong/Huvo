package com.huvo.identity.department.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.identity.department.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {}
