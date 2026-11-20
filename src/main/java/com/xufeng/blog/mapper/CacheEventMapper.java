package com.xufeng.blog.mapper;

import com.xufeng.blog.entity.CacheEvent;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface CacheEventMapper {

    @Insert("INSERT INTO cache_event(kind, cache_key, result, detail, created_at) " +
            "VALUES(#{kind}, #{cacheKey}, #{result}, #{detail}, #{createdAt})")
    int insert(CacheEvent event);

    @Select("SELECT id, kind, cache_key, result, detail, created_at " +
            "FROM cache_event ORDER BY id DESC LIMIT #{limit}")
    List<CacheEvent> recent(int limit);

    @Select("SELECT COUNT(*) FROM cache_event")
    long count();

    @Select("SELECT COUNT(*) FROM cache_event WHERE result = 'HIT'")
    long hitCount();

    @Select("SELECT COUNT(*) FROM cache_event WHERE result = 'MISS'")
    long missCount();

    @Select("SELECT COUNT(*) FROM cache_event WHERE result = 'WRITE'")
    long writeCount();

    @Delete("DELETE FROM cache_event")
    int clear();
}
