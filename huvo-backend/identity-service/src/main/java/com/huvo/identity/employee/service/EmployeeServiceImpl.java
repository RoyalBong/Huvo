package com.huvo.identity.employee.service;

import com.huvo.identity.employee.entity.Employee;
import com.huvo.identity.exception.ResourceNotFoundException;
import com.huvo.identity.employee.messaging.EmployeeEventPublisher;
import com.huvo.identity.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service

public class EmployeeServiceImpl implements EmployeeService {
	@Autowired
    private EmployeeRepository repository;

    @Autowired
    private EmployeeEventPublisher eventPublisher;
    @Override
    public List<Employee> getAllEmployees() {
        return repository.findAll();
    }

    @Override
    public Optional<Employee> getEmployeeById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Employee createEmployee(Employee employee) {
        return repository.save(employee);
    }

    @Override
    public Employee updateEmployee(Long id, Employee employee) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Employee " + id + " was not found");
        }
        employee.setId(id);
        Employee updatedEmployee = repository.save(employee);
        eventPublisher.publishEmployeeUpdated(employee.getId());
        return updatedEmployee;
    }

    @Override
    public void deleteEmployee(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Employee " + id + " was not found");
        }
        repository.deleteById(id);
    }
}