package com.saga.inventoryservice.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservedEvent {
    private Long orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private double price;
}
