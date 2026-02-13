package com.dev.flash_sale_engine.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/api/order")
public class OrderController {
    
    @PostMapping("/buy")
    public String createOrder(@RequestParam Long productId,@RequestParam Integer quantity) {
              
        return "Order Created Successfully";
    }    
    
}
