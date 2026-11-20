package com.xufeng.blog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 整个程序的入口：main 方法一跑，SpringBoot 就把 Web 服务器、数据库连接池、
 * MyBatis、各个 @Service / @Controller 全部装配起来，然后开始接请求。
 * @MapperScan 告诉 MyBatis：mapper 包里的接口都要生成实现类。
 */
@SpringBootApplication
@MapperScan("com.xufeng.blog.mapper")
public class BlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
