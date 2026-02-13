package com.dev.flash_sale_engine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.flash_sale_engine.models.Product;

public interface ProductRepository extends JpaRepository<Product, Long>{
    
}
