package com.xufeng.blog.mapper;

import com.xufeng.blog.entity.Comment;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface CommentMapper {

    @Select("SELECT id, article_id, author_name, content, status, created_at " +
            "FROM comment WHERE article_id = #{articleId} ORDER BY id DESC")
    List<Comment> listByArticle(Long articleId);

    @Insert("INSERT INTO comment(article_id, author_name, content, status, created_at) " +
            "VALUES(#{articleId}, #{authorName}, #{content}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Select("SELECT COUNT(*) FROM comment")
    long count();

    @Delete("DELETE FROM comment WHERE article_id = #{articleId}")
    int deleteByArticle(Long articleId);

    @Delete("DELETE FROM comment")
    int clear();
}
