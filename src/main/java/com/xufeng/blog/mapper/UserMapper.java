package com.xufeng.blog.mapper;

import com.xufeng.blog.entity.User;

/** 用户表的 SQL 都写在这里：接口 + 注解，MyBatis 在运行时生成实现类 */
public interface UserMapper {

    @org.apache.ibatis.annotations.Select(
            "SELECT id, username, password_hash, nickname, role, created_at FROM blog_user WHERE username = #{username}")
    User findByUsername(String username);

    @org.apache.ibatis.annotations.Select(
            "SELECT id, username, password_hash, nickname, role, created_at FROM blog_user WHERE id = #{id}")
    User findById(Long id);

    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO blog_user(username, password_hash, nickname, role, created_at) " +
            "VALUES(#{username}, #{passwordHash}, #{nickname}, #{role}, #{createdAt})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM blog_user")
    long count();
}
