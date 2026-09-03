package com.splitz.expense.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterpartyResponseDTO {

  private Long userId;
  private String username;
  private String firstName;
  private String lastName;
  private String email;
  private BigDecimal balance;
  private List<GroupRefDTO> groups;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GroupRefDTO {
    private Long id;
    private String name;
  }
}
