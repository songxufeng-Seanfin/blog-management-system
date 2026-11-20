#!/usr/bin/env python3
"""端到端接口回归：直接打真实 HTTP 接口，验证项目 5 的完整闭环。

零第三方依赖（只用标准库），且显式绕开环境代理，避免 127.0.0.1 被转发出去。

用法：
    python tests/e2e_api.py                      # 默认 http://127.0.0.1:8003
    BLOG_BASE_URL=http://127.0.0.1:9000 python tests/e2e_api.py
"""
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

BASE = os.environ.get("BLOG_BASE_URL", "http://127.0.0.1:8003").rstrip("/")
ROOT = Path(__file__).resolve().parent.parent
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

RESULTS = []


def call(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with OPENER.open(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            payload = json.loads(raw)
        except Exception:
            payload = {"code": -1, "message": raw[:120]}
        return e.code, payload


def check(name, condition, detail=""):
    RESULTS.append({"name": name, "ok": bool(condition), "detail": str(detail)[:150]})
    print(("  PASS  " if condition else "  FAIL  ") + name +
          (("  | " + str(detail)[:110]) if detail else ""))


def main():
    print("=" * 72)
    print("在线博客管理系统 · 端到端接口回归   " + BASE)
    print("=" * 72)

    # 1. 健康检查
    st, r = call("GET", "/api/health/")
    check("健康检查返回 UP", st == 200 and r.get("code") == 0 and r["data"]["status"] == "UP", r.get("message"))

    # 2. 登录与 JWT
    st, r = call("POST", "/api/auth/login/", {"username": "alice", "password": "123456"})
    ok_login = st == 200 and r.get("code") == 0 and bool(r.get("data", {}).get("token"))
    token = r["data"]["token"] if ok_login else ""
    check("正确账号登录拿到 JWT", ok_login, r.get("message"))

    st, r = call("POST", "/api/auth/login/", {"username": "alice", "password": "wrong"})
    check("错误密码被拒绝", r.get("code") == 401, r.get("message"))

    st, r = call("GET", "/api/auth/me/")
    check("无 token 访问 /me 被安检口拦下", st == 401, "HTTP %s / %s" % (st, r.get("message")))

    st, r = call("GET", "/api/auth/me/", token=token)
    check("带 token 访问 /me 成功", r.get("code") == 0 and r["data"]["username"] == "alice", r.get("message"))

    tampered = token[:-3] + ("aaa" if not token.endswith("aaa") else "bbb")
    st, r = call("GET", "/api/auth/me/", token=tampered)
    check("篡改签名后的 token 被拒绝", st == 401, "HTTP %s" % st)

    # 3. 缓存：第一次 MISS，第二次 HIT
    st, before = call("GET", "/api/demo/state/")
    hits_before = before["data"]["cache"]["hits"]
    call("GET", "/api/categories/")
    call("GET", "/api/categories/")
    st, after = call("GET", "/api/demo/state/")
    hits_after = after["data"]["cache"]["hits"]
    check("同一缓存键第二次读取命中 HIT", hits_after > hits_before,
          "命中数 %s → %s" % (hits_before, hits_after))

    # 4. 列表：分页 / 筛选 / 搜索
    st, r = call("GET", "/api/articles/?page=1&size=3")
    page = r.get("data", {})
    check("分页返回 3 条且总数正确", r.get("code") == 0 and len(page.get("items", [])) == 3
          and page.get("total", 0) >= 8, "total=%s pages=%s" % (page.get("total"), page.get("totalPages")))

    st, r = call("GET", "/api/articles/?status=draft")
    check("状态筛选只返回草稿", r.get("code") == 0
          and all(a["status"] == "draft" for a in r["data"]["items"]),
          "%s 条" % r["data"]["total"])

    st, r = call("GET", "/api/articles/?kw=JWT")
    check("关键词搜索命中标题含 JWT 的文章", r.get("code") == 0
          and any("JWT" in a["title"] for a in r["data"]["items"]),
          "%s 条" % r["data"]["total"])

    # 5. 写操作：未授权 -> 创建 -> 详情 -> 发布 -> 评论 -> 转草稿 -> 删除
    st, r = call("POST", "/api/articles/", {"title": "不该被创建"})
    check("无 token 写文章被拦下（401）", st == 401, "HTTP %s" % st)

    st, r = call("POST", "/api/articles/", {
        "title": "E2E 回归测试文章",
        "summary": "由 tests/e2e_api.py 创建，结束时删除",
        "content": "这是一条真实写进 H2 数据库的记录。",
        "status": "draft"
    }, token=token)
    created = r.get("data", {}).get("id") if r.get("code") == 0 else None
    check("带 token 创建草稿文章", bool(created) and r["data"]["status"] == "draft", "id=%s" % created)

    if created:
        st, r = call("GET", "/api/articles/%s/" % created)
        check("详情接口返回刚创建的文章", r.get("code") == 0
              and r["data"]["title"] == "E2E 回归测试文章", r.get("message"))

        st, r = call("POST", "/api/articles/%s/publish/" % created, token=token)
        check("草稿发布后状态变 published", r.get("code") == 0
              and r["data"]["status"] == "published", r.get("message"))

        st, r = call("GET", "/api/articles/?status=published&kw=E2E")
        check("写操作后缓存失效，列表能看到新文章", r.get("code") == 0
              and any(a["id"] == created for a in r["data"]["items"]),
              "%s 条" % r.get("data", {}).get("total"))

        st, r = call("POST", "/api/articles/%s/comments/" % created,
                     {"authorName": "回归脚本", "content": "评论链路验证"}, token=token)
        check("给文章新增评论", r.get("code") == 0, r.get("message"))

        st, r = call("GET", "/api/articles/%s/comments/" % created)
        check("评论列表包含刚写的评论", r.get("code") == 0
              and any(c["content"] == "评论链路验证" for c in r["data"]),
              "%s 条" % len(r.get("data", [])))

        st, r = call("POST", "/api/articles/%s/unpublish/" % created, token=token)
        check("已发布文章可转回草稿", r.get("code") == 0 and r["data"]["status"] == "draft", r.get("message"))

        st, r = call("DELETE", "/api/articles/%s/" % created, token=token)
        check("删除文章成功", r.get("code") == 0, r.get("message"))

        st, r = call("GET", "/api/articles/%s/" % created)
        check("删除后再查返回业务 404", r.get("code") == 404, r.get("message"))

    # 6. 缓存后端切换（本机无 Redis，应安全回退）
    st, r = call("POST", "/api/cache/switch/", {"backend": "redis"})
    check("无 Redis 时切换请求安全回退内存", r.get("code") == 0
          and r["data"]["backend"] == "memory", r.get("data", {}).get("message"))
    call("POST", "/api/cache/switch/", {"backend": "memory"})

    # 7. 统计 + 重置
    st, r = call("GET", "/api/stats/overview/")
    check("统计接口返回文章与缓存数据", r.get("code") == 0
          and "articles" in r["data"] and "cache" in r["data"], r.get("message"))

    st, r = call("POST", "/api/demo/reset/", {})
    check("重置演示数据回到 8 篇文章", r.get("code") == 0
          and r["data"]["articles"] == 8, r.get("data"))

    passed = sum(1 for x in RESULTS if x["ok"])
    failed = len(RESULTS) - passed
    print("-" * 72)
    print("结果：%s 条 / %s 通过 / %s 失败" % (len(RESULTS), passed, failed))

    out = ROOT / "reports" / "e2e_result.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "base_url": BASE,
        "run_at": datetime.now().isoformat(timespec="seconds"),
        "total": len(RESULTS), "passed": passed, "failed": failed,
        "cases": RESULTS
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print("结果已写入 " + str(out.relative_to(ROOT)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
