package com.saga.inventoryservice.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryFailedEvent {
    private Long orderId;
    private String reason;
}
