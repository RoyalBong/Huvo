package com.example.departmentservice.service;

import com.example.departmentservice.entity.Department;
import java.util.List;
import java.util.Optional;

public interface DepartmentService {

    List<Department> getAllDepartments();

    Optional<Department> getDepartmentById(Long id);

    Department createDepartment(Department department);

    /**
     * @throws ResourceNotFoundException if no department with {@code id} exists
     */
    Department updateDepartment(Long id, Department department);

    /**
     * @throws ResourceNotFoundException if no department with {@code id} exists
     */
    void deleteDepartment(Long id);
}