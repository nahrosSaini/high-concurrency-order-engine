package com.dev.flash_sale_engine.services;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderService {    
   
    //By using final, you guarantee the dependency isn't null and can't be changed accidentally at runtime.
    private final StringRedisTemplate redisTemplate;

    private final KafkaTemplate<String,String> kafkaTemplate;

    public OrderService(StringRedisTemplate redisTemplate,KafkaTemplate<String,String> kafkaTemplate){     
        this.redisTemplate=redisTemplate;
        this.kafkaTemplate=kafkaTemplate;
    }

    public Boolean placeOrder(Long productId,Integer quantity){

        // 1. Check stock in Redis. (Atomic Operation)
        Long remainingStock=redisTemplate.opsForValue().decrement("product:"+productId+":stock",quantity);

        // 2. Validate the stock
        if(remainingStock!=null && remainingStock < 0){
            redisTemplate.opsForValue().increment("product:"+productId+":stock",quantity);
            return false;
        }

        // 3. Push the details to kafka
        kafkaTemplate.send("flash_sale_orders",productId+":"+quantity);

        return true;

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

/*

STEP - 4 :

Now to resolve our response time issue we need to make some Architecture changes.

Issue : When we recevie 1000 requests each request execute a DB query to fetch the stock quantity. This added some overhead on the DB and delays the request by few milliseconds.

How to solve this : 

1. "REDIS CACHE" :  Redis cache is one part of our solution here. 

Why REDIS : 

In-Memory:  It doesn't touch the slow hard drive.
Atomic Operations: Redis has a command called DECRBY. If 1,000 threads hit it at once, Redis handles them internally one by one at lightning speed (sub-millisecond).
Lua Scripting: You can send a small script to Redis that says: "Check if stock > 0. If yes, subtract 1. If no, return error." 
               This whole logic happens inside Redis in one atomic step.

Now we put our stock quantity in redis and let each request read stock of the product from redis instead of DB.


Let SetUp :

1. Instead of running Redis on my machine. We we do it via docker. Lets install docker and create a container with redis Image.

Docker download link : https://www.docker.com/products/docker-desktop/

If manually you want to dowload the redis image.
Download redis Image : docker run --name flash-sale-redis -p 6379:6379 -d redis

Im doing it via docker-compose.yml file.

few commands : 

    docker-compose up -d: Starts all services (Redis) in the background.

    docker-compose down: Stops and removes all services at once, cleaning up your workspace.

    docker-compose ps: Shows you a neat table of all your containers.


Once Docker and redis is installed.

Set the stock in redis via RedisInsight( You need to download this if you like UI) or commandLine.

-> Get Stock : docker exec -it flash-sale-redis redis-cli GET product:1:stock
-> Set Stock : docker exec -it flash-sale-redis redis-cli SET product:1:stock 10

----

Now only 10 request are going inside the DB of 100 request. we have avoid other 90 requests from go to DB for reading the Stock.

Now we have more impromement to do that is today we have 100  requests and once stock is good we create the order. 
Until we create the order we keep the thread active.

Why not separate this logic via a queue like Kafka.

*/


/*

STEP - 5

Now we have implemented Basic Apache Kafka with Topic and one Partition. 

1. The moment we recevied request we check in redis stock and decrement it.
2. Then we validate new value if its not correct then we increment the stock in redis.
3. If stock looks good then we add the productId and quantity in Kafka topic.

4. Now one Consumer is listening to this topic.
5. Its will pick the data from the queue topic.
6. Using the product id we will update the stock in DB for the product.
7. Now we can save the order in DB.

Validated and looks correct. I can see good response time. 

Now lets scale and play around.


*/