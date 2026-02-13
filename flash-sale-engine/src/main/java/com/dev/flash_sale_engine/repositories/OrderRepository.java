package com.dev.flash_sale_engine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.flash_sale_engine.models.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order,Long> {
    
}
