package com.dev.flash_sale_engine.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    private Integer stock; // This is the "Hot Spot"

    private Double price;

    @Version
    private Long version;
}

/*

InOrder to resolve the Overselling we thought of using OptimisticLocking using @Version.

This resovled our Overselling problem but there is a issue now.

Optimistic Locking (the @Version approach) becomes a performance bottleneck 
because of the high failure rate.

Issue with this approach : 

1. The "First Wins, Others Fail" Problem
    -> If 100 users try to buy an item at the exact same microsecond, 
       1 user succeeds and 99 users get an exception.
       To make it "work" for the user, we have to implement a retry loop.

2. If we implement Retry then its will cause "Retry Storm"

    -> If you have 1,000 users and 10 items, and you tell the 999 "losers" to retry:

    Those 999 users hit the database again.1 succeeds, 998 fail.
    They retry again.
    This creates an exponential increase in database load. 
    Our DB spends all its CPU power rejecting failed version checks rather than doing actual work.

3. Database "Write Contention"
   -> Even though the application doesn't "lock" the row, the Database Engine (MySQL) still has to handle the physical write to the disk. 
      1,000 simultaneous updates to the exact same row (the product) will cause "Row Latch Contention," slowing down your entire database.

*/