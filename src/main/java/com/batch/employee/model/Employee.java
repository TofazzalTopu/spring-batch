package com.batch.employee.model;

import java.util.Objects;

public class Employee {

  private Long id;

  private String name;
  private String email;
  private Double salary;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Employee employee = (Employee) o;
    return id.equals(employee.id)
        && name.equals(employee.name)
        && email.equals(employee.email)
        && salary.equals(employee.salary);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, email, salary);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Double getSalary() {
    return salary;
  }

  public void setSalary(Double salary) {
    this.salary = salary;
  }
}
