package com.example.departmentservice.service;

import com.example.departmentservice.entity.Department;
import com.example.departmentservice.exception.ResourceNotFoundException;
import com.example.departmentservice.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DepartmentServiceImpl implements DepartmentService {
    @Autowired
    private DepartmentRepository repository;

    @Override
    public List<Department> getAllDepartments() {
        return repository.findAll();
    }

    @Override
    public Optional<Department> getDepartmentById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Department createDepartment(Department department) {
        return repository.save(department);
    }

    @Override
    public Department updateDepartment(Long id, Department department) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Department " + id + " was not found");
        }
        department.setId(id);
        return repository.save(department);
    }

    @Override
    public void deleteDepartment(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Department " + id + " was not found");
        }
        repository.deleteById(id);
    }
}