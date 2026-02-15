package com.dev.flash_sale_engine.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.dev.flash_sale_engine.models.Product;

import jakarta.persistence.LockModeType;


public interface ProductRepository extends JpaRepository<Product, Long>{
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("Select p from Product p Where p.id = :id")
    Optional<Product> findByIdWithLock(Long id);
    /*
     * 
     * Instead of using findById we are writing our own method with a Lock with
     * pessimistic_write because the findById doesn't have any lock.
     * 
     * Lets learn more about different lockModetypes available
     * 
     * PESSIMISTIC_READ (Shared Lock): Obtains a shared lock that allows other transactions to read the data but prevents them from modifying or deleting it
     * PESSIMISTIC_WRITE (Exclusive Lock): Obtains an exclusive lock that prevents other transactions from reading, modifying, or deleting the data.
     * PESSIMISTIC_FORCE_INCREMENT:Behaves like PESSIMISTIC_WRITE but also forces a version increment on the entity at the end of the transaction, even if you didn't change any fields.
     * OPTIMISTIC : The entity manager verifies that the version hasn't changed at the end of the transaction.
     * OPTIMISTIC_FORCE_INCREMENT: Checks the version and guarantees an increment upon commit, even if no fields were modified
     * 
     * 
     * If your scenario is...
     * Low contention, performance is priority -->	OPTIMISTIC
     * Frequent updates to the same row	--> PESSIMISTIC_WRITE
     * Reading data that must remain consistent -- > PESSIMISTIC_READ
     * Updating a child and needing to flag the parent	--> OPTIMISTIC_FORCE_INCREMENT
     * 
     */
}
