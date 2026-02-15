package com.dev.flash_sale_engine.services;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Order placeOrder(Long productId,Integer quantity){

        // 1. Find Product using standard findById (No Lock)
        //Product product= productRepository.findById(productId).get(); 
        Product product= productRepository.findByIdWithLock(productId).get();

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

STEP - 1 : 

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

/*

STEP - 2 : 

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


/*

STEP - 3 : 

In Order to resolve our "Retry Strom" problem we  thinked of using Pessimistic locking strategy instead of Optimisitc.

As pessimistic (Write ) Locking will block everyone else from both reading and writing that specific row.

After implementing i observed that we have successfully resolve our issues of "Retry Strom" and "OverSelling of Product" as well.

But Now we are another challenge that is "PERFORMANCE" of response time: 

In the locking strategy an internal queue is maintained and all the request are put into that queue in first come first server order.
Consider we have 1000 request trying to access this row then when first request has achived the lock then all other 999 will move to the queue.
now if we compare the response time of the first and the last request then we will observe a delay of few milliseconds.

We need to resolve this performance issue.

*/