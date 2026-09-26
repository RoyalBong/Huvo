/** Matches identity-service EmployeeResponse exactly
 *  (Huvo-backend identity-service DTO). */
export interface Employee {
  id: number;
  name: string;
  departmentId: string | null;
  salary: number;
}

/** Matches EmployeeRequest (write model). */
export interface EmployeeInput {
  name: string;
  departmentId: string | null;
  salary: number;
}

/** Matches identity-service DepartmentResponse exactly. */
export interface Department {
  id: number;
  name: string;
  location: string;
}

/** Matches DepartmentRequest-style writes. */
export interface DepartmentInput {
  name: string;
  location: string;
}
