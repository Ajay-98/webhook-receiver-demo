package com.ajay.webhookreceiver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors com.ajay.kafkademo.model.OrderEvent on the sender side.
 * In a real system, this shape would be defined by a shared contract
 * (e.g., a schema in Schema Registry, or a shared client library) rather
 * than two hand-written copies that could silently drift apart - worth
 * keeping in mind when we get to Schema Registry later.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private String orderId;
    private String status;
}
