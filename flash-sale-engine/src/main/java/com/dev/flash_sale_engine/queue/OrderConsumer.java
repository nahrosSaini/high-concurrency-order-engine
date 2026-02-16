package com.dev.flash_sale_engine.queue;

import java.time.LocalDateTime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.flash_sale_engine.models.Order;
import com.dev.flash_sale_engine.repositories.OrderRepository;
import com.dev.flash_sale_engine.repositories.ProductRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderConsumer {
    
    private final ProductRepository productRepository;

    private final OrderRepository orderRepository;

    public OrderConsumer(OrderRepository orderRepository,ProductRepository productRepository){
        this.orderRepository=orderRepository;
        this.productRepository=productRepository;
    }


    @KafkaListener(topics = "flash_sale_orders",groupId = "flash-sale-group")
    @Transactional
    public void processOrder(String message){
        if(message!=null){
            String[] data= message.split(":");
            Long productId=Long.parseLong(data[0]);
            Integer quantity=Integer.parseInt(data[1]);

            // 1. MYSQL Atomic Update (The Fix)
            int rowsUpdated=productRepository.decreaseStock(productId,quantity);

            // 2. Lets handle rare case when stock in DB and Redis is not in sync.
            if(rowsUpdated > 0){           
                Order order=new Order();
                order.setProductId(productId);
                order.setQuantity(quantity);
                order.setOrderTime(LocalDateTime.now());
                orderRepository.save(order);
                
                log.info("Kafka Consumer: Processed order for product {}",productId);
            }else{
                log.info("Kafka Consumer: Failed to update MySQL (Out of stock)");
            }
        }  
    }
}
