package com.saga.inventoryservice.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedEvent {
    private Long orderId;
    private String productId;
    private int quantity;
    private String reason;
}
