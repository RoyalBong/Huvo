package com.example.employeeservice.controller;

import com.example.employeeservice.dto.EmployeeRequest;
import com.example.employeeservice.dto.EmployeeResponse;
import com.example.employeeservice.exception.ResourceNotFoundException;
import com.example.employeeservice.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Employee API. Works exclusively with DTOs (Huvo Backend_Context.md §10) - JPA
 * entities are never returned to a client, and all failures come back through
 * {@link com.example.employeeservice.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService service;

    @GetMapping
    public List<EmployeeResponse> getAllEmployees() {
        return service.getAllEmployees().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable Long id) {
        return service.getEmployeeById(id)
                .map(EmployeeResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " was not found"));
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EmployeeResponse.from(service.createEmployee(request.toEntity())));
    }

    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return EmployeeResponse.from(service.updateEmployee(id, request.toEntity()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        service.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
