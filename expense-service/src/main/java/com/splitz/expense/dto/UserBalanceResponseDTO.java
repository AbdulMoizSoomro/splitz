package com.splitz.expense.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBalanceResponseDTO {

    private Long userId;
    private BigDecimal totalBalance;
    private List<GroupBalanceDTO> groupBalances;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupBalanceDTO {

        private Long groupId;
        private String groupName;
        private BigDecimal balance;
    }
}
