package com.xufeng.blog.mapper;

import com.xufeng.blog.entity.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface CategoryMapper {

    @Select("SELECT id, name, slug, sort_no FROM category ORDER BY sort_no, id")
    List<Category> list();

    @Select("SELECT id, name, slug, sort_no FROM category WHERE id = #{id}")
    Category findById(Long id);

    @Insert("INSERT INTO category(name, slug, sort_no) VALUES(#{name}, #{slug}, #{sortNo})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Select("SELECT COUNT(*) FROM category")
    long count();
}
