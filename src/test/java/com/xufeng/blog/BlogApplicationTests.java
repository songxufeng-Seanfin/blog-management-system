package com.xufeng.blog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufeng.blog.mapper.CacheEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SpringBoot 侧的接口测试：用 MockMvc 模拟 HTTP，不启动真实端口、不连运行期数据库。
 * 与 tests/e2e_api.py 互为补充：一个在进程内，一个走真实网络。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BlogApplicationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CacheEventMapper events;

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    @Test
    void healthUp() throws Exception {
        mvc.perform(get("/api/health/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void loginOkAndWrongPasswordRejected() throws Exception {
        assertTrue(login().length() > 30);
        mvc.perform(post("/api/auth/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"bad\"}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meRequiresValidToken() throws Exception {
        String token = login();
        mvc.perform(get("/api/auth/me/"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me/").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void tamperedTokenRejected() throws Exception {
        String token = login();
        String tampered = token.substring(0, token.length() - 3) + "xyz";
        mvc.perform(get("/api/auth/me/").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void articlePageAndDraftFilter() throws Exception {
        mvc.perform(get("/api/articles/").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
        mvc.perform(get("/api/articles/").param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("draft"));
    }

    @Test
    void writeWithoutTokenBlocked() throws Exception {
        mvc.perform(post("/api/articles/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"不该被创建\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullArticleLifecycle() throws Exception {
        String token = login();
        String body = mvc.perform(post("/api/articles/")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"JUnit 生命周期测试\",\"summary\":\"测试用\","
                                + "\"content\":\"正文\",\"status\":\"draft\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).path("data").path("id").asLong();

        mvc.perform(post("/api/articles/" + id + "/publish/")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("published"));

        mvc.perform(post("/api/articles/" + id + "/comments/")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorName\":\"测试\",\"content\":\"评论一条\"}"))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/articles/" + id + "/comments/"))
                .andExpect(jsonPath("$.data[0].content").value("评论一条"));

        mvc.perform(delete("/api/articles/" + id + "/")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/articles/" + id + "/"))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void categoryCacheProducesHit() throws Exception {
        long before = events.hitCount();
        mvc.perform(get("/api/categories/")).andExpect(status().isOk());
        mvc.perform(get("/api/categories/")).andExpect(status().isOk());
        assertTrue(events.hitCount() > before, "第二次读分类应当命中缓存");
    }

    @Test
    void keywordSearchWorks() throws Exception {
        mvc.perform(get("/api/articles/").param("kw", "SpringBoot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").exists());
    }

    @Test
    void demoStateContainsAllPanels() throws Exception {
        mvc.perform(get("/api/demo/state/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.articles").exists())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.cache.backend").exists())
                .andExpect(jsonPath("$.data.stack").exists());
    }

    @Test
    void cacheSwitchFallsBackWhenRedisAbsent() throws Exception {
        mvc.perform(post("/api/cache/switch/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backend\":\"redis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backend").exists());
    }
}
