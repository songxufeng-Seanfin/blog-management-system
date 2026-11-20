package com.xufeng.blog.mapper;

import com.xufeng.blog.entity.Article;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

public interface ArticleMapper {

    String JOIN = " FROM article a " +
            "LEFT JOIN category c ON a.category_id = c.id " +
            "LEFT JOIN blog_user u ON a.author_id = u.id ";

    String COLUMNS = "SELECT a.id, a.title, a.summary, a.content, a.category_id, a.author_id, " +
            "a.status, a.view_count, a.created_at, a.updated_at, a.published_at, " +
            "c.name AS category_name, u.nickname AS author_name ";

    @Select(COLUMNS + JOIN + "WHERE a.id = #{id}")
    Article findById(Long id);

    /** 动态 SQL：<script> 里的 <if> 按前端传来的条件拼 WHERE，空条件由 Service 转成 null */
    @Select({"<script>",
            COLUMNS, JOIN,
            "<where>",
            "  <if test='status != null'> AND a.status = #{status} </if>",
            "  <if test='categoryId != null'> AND a.category_id = #{categoryId} </if>",
            "  <if test='kw != null'> AND (LOWER(a.title) LIKE LOWER(CONCAT('%', #{kw}, '%')) ",
            "       OR LOWER(COALESCE(a.summary, '')) LIKE LOWER(CONCAT('%', #{kw}, '%'))) </if>",
            "</where>",
            "ORDER BY a.id DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<Article> page(@Param("status") String status,
                       @Param("categoryId") Long categoryId,
                       @Param("kw") String kw,
                       @Param("limit") int limit,
                       @Param("offset") int offset);

    @Select({"<script>",
            "SELECT COUNT(*)", JOIN,
            "<where>",
            "  <if test='status != null'> AND a.status = #{status} </if>",
            "  <if test='categoryId != null'> AND a.category_id = #{categoryId} </if>",
            "  <if test='kw != null'> AND (LOWER(a.title) LIKE LOWER(CONCAT('%', #{kw}, '%')) ",
            "       OR LOWER(COALESCE(a.summary, '')) LIKE LOWER(CONCAT('%', #{kw}, '%'))) </if>",
            "</where>",
            "</script>"})
    long countPage(@Param("status") String status,
                   @Param("categoryId") Long categoryId,
                   @Param("kw") String kw);

    @Insert("INSERT INTO article(title, summary, content, category_id, author_id, status, " +
            "view_count, created_at, updated_at, published_at) " +
            "VALUES(#{title}, #{summary}, #{content}, #{categoryId}, #{authorId}, #{status}, " +
            "0, #{createdAt}, #{updatedAt}, #{publishedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Article article);

    @Update("UPDATE article SET title = #{title}, summary = #{summary}, content = #{content}, " +
            "category_id = #{categoryId}, status = #{status}, updated_at = #{updatedAt}, " +
            "published_at = #{publishedAt} WHERE id = #{id}")
    int update(Article article);

    @Update("UPDATE article SET status = 'published', published_at = #{now}, " +
            "updated_at = #{now} WHERE id = #{id}")
    int publish(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE article SET status = 'draft', updated_at = #{now} WHERE id = #{id}")
    int unpublish(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrView(Long id);

    @Update("UPDATE article SET view_count = #{n} WHERE id = #{id}")
    int setViews(@Param("id") Long id, @Param("n") int n);

    @Delete("DELETE FROM article WHERE id = #{id}")
    int delete(Long id);

    @Delete("DELETE FROM article")
    int clear();

    @Select("SELECT COUNT(*) FROM article")
    long countAll();

    @Select("SELECT COUNT(*) FROM article WHERE status = 'published'")
    long countPublished();

    @Select("SELECT COUNT(*) FROM article WHERE status = 'draft'")
    long countDraft();

    @Select("SELECT COALESCE(SUM(view_count), 0) FROM article")
    long sumViews();
}
