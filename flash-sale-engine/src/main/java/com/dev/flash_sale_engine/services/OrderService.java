package com.dev.flash_sale_engine.services;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.dev.flash_sale_engine.models.Order;
import com.dev.flash_sale_engine.models.Product;
import com.dev.flash_sale_engine.repositories.OrderRepository;
import com.dev.flash_sale_engine.repositories.ProductRepository;

@Service
public class OrderService {    
   
    //By using final, you guarantee the dependency isn't null and can't be changed accidentally at runtime.
    private final OrderRepository orderRepository;

    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository){
        this.orderRepository=orderRepository;
        this.productRepository=productRepository;
    }

    public Order placeOrder(Long productId,Integer quantity){

        // 1. Find Product using standard findById (No Lock)
        Product product= productRepository.findById(productId).get();

        // 2. If product is null then throw the error
        if(product==null){
            throw new RuntimeException("Product not found !!");
        }

        // 3. Check the stock
        if(product.getStock() < quantity){
            throw new RuntimeException("Insufficient stock for product : "+productId);
        }

        // 4. Decrement the stock
        product.setStock(product.getStock()-quantity);
        productRepository.save(product);

        // 5. Create Order
        Order order=new Order();
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setOrderTime(LocalDateTime.now());

        // 6. save and return Order
        return orderRepository.save(order);

    }
}

/*

Choosing the constructer based Injection because :

Immutability ->	By using final, you guarantee the dependency isn't null and can't be changed accidentally at runtime.
Easy Testing ->	You can test this class in a plain Java unit test by simply calling new OrderService(mockRepo, mockRedis). No Spring "magic" required.
Fail-Fast -> If a dependency is missing, the application will crash immediately on startup rather than throwing a NullPointerException later when you try to use it.
No Reflection -> It uses standard Java instantiation rather than expensive reflection to "force" values into private fields.

--------
Scenarios : 
1. 100 requests at the same time
2. In product database there are only 10 quantity for the product

Issues with this approach : 

 Each Request called database 3 times to : 
 1. Fetch the product.
 2. Update the product.
 3. Save the Order.

 if 100 users send the request at the same time.
 we observed that although the quantity in stock was 10 for the product 
 but 50 orders were placed causing OVER SELLING of products.

 why this happened is because 

 ->  At $T_0$, 50 different threads all execute findById(productId). They all see the same stock value of 10.
 ->  All 50 threads reach your if(product.getStock() < quantity) check. Since 10 < 1 is false for all of them, they all pass the check simultaneously.
    -> Thread 1 calculates 10 - 1 = 9 and saves it.
    -> Thread 2 (which already read 10 earlier) also calculates 10 - 1 = 9 and saves it.
    -> This continues until all 50 threads have saved a "new" stock value (mostly 9 or 8) and created 50 order records.

Result: You have 50 orders in your order table, but your product stock might only show 9 or 0 because the updates were overwriting each other instead of subtracting sequentially.

*/
