package com.saga.inventoryservice.controller;

import com.saga.inventoryservice.dto.InventoryRequest;
import com.saga.inventoryservice.entity.Inventory;
import com.saga.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * POST /api/inventory
     * Add or restock product inventory
     */
    @PostMapping
    public ResponseEntity<Inventory> addInventory(@RequestBody InventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.addInventory(request));
    }

    /**
     * GET /api/inventory
     * List all products in inventory
     */
    @GetMapping
    public ResponseEntity<List<Inventory>> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    /**
     * GET /api/inventory/{productId}
     * Get inventory for a specific product
     */
    @GetMapping("/{productId}")
    public ResponseEntity<Inventory> getInventoryByProductId(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getInventoryByProductId(productId));
    }
}
