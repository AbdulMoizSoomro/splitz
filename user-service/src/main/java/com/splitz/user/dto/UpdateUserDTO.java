package com.splitz.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for partial user updates. Unlike UserDTO, fields are optional to support partial updates
 * where only specific fields are changed.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserDTO {

  @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
  private String firstName;

  @Size(max = 100, message = "Last name must not exceed 100 characters")
  private String lastName;

  @Email(message = "Email should be valid")
  private String email;

  @Size(min = 8, message = "Password must be at least 8 characters")
  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
  private String password;
}
