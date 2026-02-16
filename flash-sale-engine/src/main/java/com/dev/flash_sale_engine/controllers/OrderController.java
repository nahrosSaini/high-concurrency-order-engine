package com.dev.flash_sale_engine.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.flash_sale_engine.services.OrderService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService){
        this.orderService=orderService;
    }
    
    @PostMapping("/buy")
    public ResponseEntity<String> createOrder(@RequestParam Long productId,@RequestParam Integer quantity) {
        Boolean isOrderSuccess=orderService.placeOrder(productId, quantity);
        String responseMessage=isOrderSuccess?"Order Accepted! Processing in background...":"Product Sold Out..!!";
        return ResponseEntity.status(isOrderSuccess?HttpStatus.CREATED:HttpStatus.CONFLICT).body(responseMessage);
    }    
    
}
