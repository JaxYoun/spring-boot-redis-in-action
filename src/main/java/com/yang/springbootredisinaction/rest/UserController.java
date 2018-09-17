package com.yang.springbootredisinaction.rest;

import ch.qos.logback.core.util.TimeUtil;
import com.yang.springbootredisinaction.entity.User;
import io.netty.util.internal.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @Auther: Yang
 * @Date: 2018/8/18 21:19
 * @Description:
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 1.先到缓存中取，如果去到了直接返回
     * 2.如果缓存中没有，就到DB中取，如果顺利从DB中拿到了就将数据存入缓存，同时返回
     * 3.如果未能从DB中取得，此时就要防止缓存穿透攻击，这里采用最简单的一种方法，顺势将此key存入缓存，value未空，但是同时要加上一个不大于5min的国企时间
     * 4.利用Bloom过滤器也可以防止缓存穿透攻击（Gava有提供），但是需要额外编码
     * @return
     */
    @GetMapping("/getFromCacheElseDB")
    public Object getUserFromCacheElseDB(String hashName, Integer userId) {
        HashOperations<String, String, User> hashOperations = this.redisTemplate.opsForHash();
        this.redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        this.redisTemplate.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(User.class));

        User user = hashOperations.get(hashName, userId.toString());
        if (null == user) {
            user = this.getUserFromDB();
            if (null == user) {  //当从数据库也找不到这个值时，往缓存放入一个带有效期的null，防止缓存穿透攻击
                ValueOperations valueOperations = redisTemplate.opsForValue();
                valueOperations.set(hashName, null, 3L, TimeUnit.MINUTES);
            } else {
                hashOperations.putIfAbsent(hashName, userId.toString(), user);
            }
        }
        return user;
    }

    /**
     * 如果大量缓存数据集中在一小段时间集体失效，极易引起缓存雪崩，给系统造成过重压力，
     * 所以需要在数据存入缓存是给失效时间加上一个随机值，达到时间上的分散
     *
     * @param userId
     * @return
     */
    @GetMapping("/deleteCacheAfterUpdate")
    public Object deleteCacheAfterUpdate(Integer userId) {
        ValueOperations<String, String> valueOperations = this.stringRedisTemplate.opsForValue();
        valueOperations.set(userId.toString(), "yang",this.getTTL(5), TimeUnit.MINUTES);
        return "";
    }




    private User getUserFromDB() {
        return new User(48, "luo");
    }

    private long getTTL(int seed) {
        Random random = new Random(seed);
        long ttl = 10L + random.nextLong();
        return ttl;
    }

}
