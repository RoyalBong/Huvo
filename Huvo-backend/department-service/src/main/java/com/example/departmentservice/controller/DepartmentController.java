package com.example.departmentservice.controller;

import com.example.departmentservice.dto.DepartmentRequest;
import com.example.departmentservice.dto.DepartmentResponse;
import com.example.departmentservice.exception.ResourceNotFoundException;
import com.example.departmentservice.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Department API. Works exclusively with DTOs (Huvo Backend_Context.md §10) - JPA
 * entities are never returned to a client, and all failures come back through
 * {@link com.example.departmentservice.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService service;

    @GetMapping
    public List<DepartmentResponse> getAllDepartments() {
        return service.getAllDepartments().stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public DepartmentResponse getDepartmentById(@PathVariable Long id) {
        return service.getDepartmentById(id)
                .map(DepartmentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Department " + id + " was not found"));
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DepartmentResponse.from(service.createDepartment(request.toEntity())));
    }

    @PutMapping("/{id}")
    public DepartmentResponse updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return DepartmentResponse.from(service.updateDepartment(id, request.toEntity()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        service.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
