package com.huvo.identity.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "employee")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /**
     * TODO(phase-1): replace with a relation that points at {@code Department.id} (Long)
     * once the org-chart / department-membership model lands - see
     * Huvo Backend_Context.md §4.2 (departmentIds is an array claim on the JWT).
     */
    @Column(name = "department_id")
    private String departmentId;

    private double salary;
}
