package com.saga.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saga.inventoryservice.dto.InventoryRequest;
import com.saga.inventoryservice.entity.Inventory;
import com.saga.inventoryservice.event.*;
import com.saga.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ── Add / Restock inventory ──────────────────────────────
    @Transactional
    public Inventory addInventory(InventoryRequest request) {
        Optional<Inventory> existing = inventoryRepository.findByProductId(request.getProductId());

        if (existing.isPresent()) {
            Inventory inv = existing.get();
            inv.setAvailableQuantity(inv.getAvailableQuantity() + request.getAvailableQuantity());
            log.info("[INVENTORY SERVICE] Restocked product: {} | New qty: {}", request.getProductId(), inv.getAvailableQuantity());
            return inventoryRepository.save(inv);
        }

        Inventory inventory = Inventory.builder()
                .productId(request.getProductId())
                .productName(request.getProductName())
                .availableQuantity(request.getAvailableQuantity())
                .reservedQuantity(0)
                .build();

        log.info("[INVENTORY SERVICE] Created inventory for product: {}", request.getProductId());
        return inventoryRepository.save(inventory);
    }

    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    public Inventory getInventoryByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product: " + productId));
    }

    // ── Saga Step 2: Handle order.created → reserve stock ────
    @KafkaListener(topics = "order.created", groupId = "inventory-service-group")
    @Transactional
    public void handleOrderCreated(String message) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
            log.info("[INVENTORY SERVICE] Received ← order.created | orderId: {}, productId: {}, qty: {}",
                    event.getOrderId(), event.getProductId(), event.getQuantity());

            Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(event.getProductId());

            if (inventoryOpt.isEmpty()) {
                log.error("[INVENTORY SERVICE] Product not found: {}", event.getProductId());
                publishInventoryFailed(event.getOrderId(), "Product not found: " + event.getProductId());
                return;
            }

            Inventory inventory = inventoryOpt.get();

            if (inventory.getAvailableQuantity() < event.getQuantity()) {
                log.error("[INVENTORY SERVICE] Insufficient stock | product: {} | available: {} | required: {}",
                        event.getProductId(), inventory.getAvailableQuantity(), event.getQuantity());
                publishInventoryFailed(event.getOrderId(),
                        "Insufficient stock. Available: " + inventory.getAvailableQuantity()
                                + ", Required: " + event.getQuantity());
                return;
            }

            // ── Reserve stock ─────────────────────────────────
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - event.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + event.getQuantity());
            inventoryRepository.save(inventory);

            log.info("[INVENTORY SERVICE] Stock reserved | orderId: {} | product: {} | qty: {}",
                    event.getOrderId(), event.getProductId(), event.getQuantity());

            InventoryReservedEvent reservedEvent = InventoryReservedEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .productId(event.getProductId())
                    .quantity(event.getQuantity())
                    .price(event.getPrice())
                    .build();

            kafkaTemplate.send("inventory.reserved", reservedEvent);
            log.info("[INVENTORY SERVICE] Published → inventory.reserved for orderId: {}", event.getOrderId());

        } catch (JsonProcessingException e) {
            log.error("[INVENTORY SERVICE] Failed to deserialize OrderCreatedEvent", e);
        }
    }

    // ── Saga Compensation: payment.failed → release stock ────
    @KafkaListener(topics = "payment.failed", groupId = "inventory-service-group")
    @Transactional
    public void handlePaymentFailed(String message) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);
            log.info("[INVENTORY SERVICE] Received ← payment.failed | orderId: {} | Releasing stock for product: {}",
                    event.getOrderId(), event.getProductId());

            Optional<Inventory> inventoryOpt = inventoryRepository.findByProductId(event.getProductId());

            if (inventoryOpt.isPresent()) {
                Inventory inventory = inventoryOpt.get();
                // Compensating Transaction: release reserved stock back to available
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + event.getQuantity());
                inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - event.getQuantity()));
                inventoryRepository.save(inventory);
                log.info("[INVENTORY SERVICE] Stock released (compensation) | orderId: {} | product: {} | qty: {}",
                        event.getOrderId(), event.getProductId(), event.getQuantity());
            } else {
                log.error("[INVENTORY SERVICE] Cannot release stock — product not found: {}", event.getProductId());
            }
        } catch (JsonProcessingException e) {
            log.error("[INVENTORY SERVICE] Failed to deserialize PaymentFailedEvent", e);
        }
    }

    // ── Helper: publish inventory failure ────────────────────
    private void publishInventoryFailed(Long orderId, String reason) {
        InventoryFailedEvent failedEvent = InventoryFailedEvent.builder()
                .orderId(orderId)
                .reason(reason)
                .build();
        kafkaTemplate.send("inventory.failed", failedEvent);
        log.warn("[INVENTORY SERVICE] Published → inventory.failed for orderId: {}", orderId);
    }
}
