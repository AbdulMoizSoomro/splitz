package com.splitz.expense.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGroupRequest {

  @Size(min = 1, max = 255, message = "name must be between 1 and 255 characters")
  private String name;

  @Size(max = 2000, message = "description must be at most 2000 characters")
  private String description;

  @Size(max = 512, message = "imageUrl must be at most 512 characters")
  private String imageUrl;
}
