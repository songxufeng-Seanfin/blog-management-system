/* 在线博客管理系统 · 前端只做三件事：发请求、拿数据、把数据画出来 */
const { createApp, ref, reactive, computed, onMounted } = Vue;

createApp({
  setup() {
    const state = ref({});
    const articlePage = ref({ items: [], total: 0, totalPages: 1 });
    const token = ref('');
    const user = ref(null);
    const loginForm = reactive({ username: 'alice', password: '123456' });
    const form = reactive({ title: '', summary: '', content: '', categoryId: null, status: 'draft' });
    const commentForm = reactive({ authorName: '我', content: '' });
    const filters = reactive({ status: 'all', categoryId: '', kw: '', page: 1, size: 6 });
    const current = ref(null);
    const comments = ref([]);
    const steps = ref([]);
    const running = ref(false);
    const toast = reactive({ show: false, text: '', type: 'info' });
    let toastTimer = null;

    const ov = computed(() => state.value.overview || {});
    const cache = computed(() => state.value.cache || {});
    const cacheBackend = computed(() => cache.value.backend || 'memory');
    const cacheLabel = computed(() =>
      cacheBackend.value === 'redis' ? 'Redis' : 'JVM 内存（自动回退）');

    function showToast(text, type = 'info') {
      toast.text = text;
      toast.type = type;
      toast.show = true;
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => { toast.show = false; }, 3600);
    }

    /** 统一请求封装：自动带 JWT，统一解析 { code, message, data } */
    async function request(path, opts = {}) {
      const { method = 'GET', body = null, withToken = true } = opts;
      const headers = { 'Content-Type': 'application/json' };
      if (withToken && token.value) headers['Authorization'] = 'Bearer ' + token.value;
      let res;
      try {
        res = await fetch(path, {
          method,
          headers,
          body: body === null ? undefined : JSON.stringify(body)
        });
      } catch (e) {
        return { status: 0, code: -1, data: null, message: '网络错误：' + e.message };
      }
      let json = {};
      try { json = await res.json(); } catch (e) { /* 非 JSON 响应 */ }
      return {
        status: res.status,
        code: json.code,
        data: json.data,
        message: json.message || ('HTTP ' + res.status)
      };
    }

    async function loadState() {
      const r = await request('/api/demo/state/');
      if (r.code === 0) state.value = r.data;
      else showToast('状态加载失败：' + r.message, 'error');
    }

    async function loadArticles() {
      const p = new URLSearchParams();
      if (filters.status && filters.status !== 'all') p.set('status', filters.status);
      if (filters.categoryId !== '' && filters.categoryId != null) p.set('categoryId', filters.categoryId);
      if (filters.kw) p.set('kw', filters.kw);
      p.set('page', filters.page);
      p.set('size', filters.size);
      const r = await request('/api/articles/?' + p.toString());
      if (r.code === 0) articlePage.value = r.data;
      else showToast('列表加载失败：' + r.message, 'error');
    }

    function loadAll() { loadState(); loadArticles(); }

    function fmt(v) {
      if (!v) return '—';
      return String(v).replace('T', ' ').slice(0, 16);
    }

    function pillClass(result) {
      const map = {
        HIT: 'pill-green', MISS: 'pill-amber', WRITE: 'pill-blue',
        EVICT: 'pill-purple', BACKEND: 'pill-purple'
      };
      return map[result] || 'pill-muted';
    }

    async function doLogin() {
      const r = await request('/api/auth/login/', {
        method: 'POST',
        body: { username: loginForm.username, password: loginForm.password },
        withToken: false
      });
      if (r.code === 0) {
        token.value = r.data.token;
        user.value = r.data.user;
        showToast('登录成功，已拿到 JWT 通行证', 'ok');
        await loadState();
      } else {
        showToast('登录失败：' + r.message, 'error');
      }
    }

    function addStep(name, method, path, status, note) {
      steps.value.push({ step: steps.value.length + 1, name, method, path, status, note });
    }

    /** 一键演示：每一步都是真的 fetch，不是预先写死的动画 */
    async function runDemo() {
      if (running.value) return;
      running.value = true;
      steps.value = [];
      const stamp = new Date().toLocaleTimeString('zh-CN', { hour12: false });
      let createdId = null;
      try {
        let r = await request('/api/auth/login/', {
          method: 'POST',
          body: { username: 'alice', password: '123456' },
          withToken: false
        });
        if (r.code === 0) { token.value = r.data.token; user.value = r.data.user; }
        addStep('登录，拿一张 JWT 通行证', 'POST', '/api/auth/login/', r.status,
          r.code === 0 ? 'HMAC-SHA256 签发成功，token 只存在前端' : '登录失败');

        r = await request('/api/articles/', {
          method: 'POST', body: { title: '不该被创建的文章' }, withToken: false
        });
        addStep('故意不带 token 写文章', 'POST', '/api/articles/', r.status,
          '安检口拦下 401：写操作必须带通行证');

        r = await request('/api/categories/');
        addStep('读分类（第一次）', 'GET', '/api/categories/', r.status,
          '缓存 MISS → 回源查库 → 结果写入缓存');
        r = await request('/api/categories/');
        addStep('读分类（第二次）', 'GET', '/api/categories/', r.status,
          '同一个 key 命中 HIT，不再查库');

        r = await request('/api/articles/?page=1&size=6');
        addStep('读文章列表（第一次）', 'GET', '/api/articles/', r.status, '列表缓存 MISS → 回源');
        r = await request('/api/articles/?page=1&size=6');
        addStep('读文章列表（第二次）', 'GET', '/api/articles/', r.status, '列表缓存 HIT');

        const firstCat = state.value.categories && state.value.categories.length
          ? state.value.categories[0].id : null;
        r = await request('/api/articles/', {
          method: 'POST',
          body: {
            title: '一键演示创建的文章 · ' + stamp,
            summary: '演示用，结束时会自动删除',
            content: '这篇由「一键跑完整流程」真实调用接口创建，结束后会被清理。',
            categoryId: firstCat,
            status: 'draft'
          }
        });
        createdId = r.data && r.data.id ? r.data.id : null;
        addStep('新建一篇草稿', 'POST', '/api/articles/', r.status,
          '写操作成功 → 列表 / 详情缓存自动失效');

        if (createdId) {
          r = await request('/api/articles/' + createdId + '/publish/', { method: 'POST' });
          addStep('把草稿发布', 'POST', '/api/articles/{id}/publish/', r.status,
            'status: draft → published，published_at 被写入');

          r = await request('/api/articles/?page=1&size=6');
          addStep('发布后再读列表', 'GET', '/api/articles/', r.status,
            '缓存已失效，重新回源，新文章立刻可见');

          r = await request('/api/articles/' + createdId + '/comments/', {
            method: 'POST',
            body: { authorName: '演示脚本', content: '这条评论也是真实写进数据库的' }
          });
          addStep('给新文章写评论', 'POST', '/api/articles/{id}/comments/', r.status,
            '评论写入成功，该文章的评论缓存失效');

          r = await request('/api/articles/' + createdId + '/comments/');
          addStep('读评论（两次）', 'GET', '/api/articles/{id}/comments/', r.status,
            '第一次 MISS 写缓存，第二次 HIT');
          await request('/api/articles/' + createdId + '/comments/');
        }

        r = await request('/api/stats/overview/');
        addStep('读统计', 'GET', '/api/stats/overview/', r.status,
          '数字来自几张表的 COUNT 与缓存事件统计');

        if (createdId) {
          r = await request('/api/articles/' + createdId + '/', { method: 'DELETE' });
          addStep('清理演示文章', 'DELETE', '/api/articles/{id}/', r.status,
            '把演示产生的数据还原，可反复点击');
        }

        await loadAll();
        showToast('演示完成：' + steps.value.length + ' 步全部走通', 'ok');
      } catch (e) {
        showToast('演示中断：' + e.message, 'error');
      } finally {
        running.value = false;
      }
    }

    async function saveArticle() {
      if (!form.title.trim()) { showToast('请先写标题', 'error'); return; }
      const r = await request('/api/articles/', {
        method: 'POST',
        body: {
          title: form.title, summary: form.summary, content: form.content,
          categoryId: form.categoryId || null, status: form.status
        }
      });
      if (r.code === 0) {
        showToast('文章已保存，ID = ' + r.data.id, 'ok');
        Object.assign(form, { title: '', summary: '', content: '', categoryId: null, status: 'draft' });
        await loadAll();
      } else {
        showToast('保存失败：' + r.message + '（写操作需要先登录）', 'error');
      }
    }

    async function openDetail(a) {
      const r = await request('/api/articles/' + a.id + '/');
      if (r.code !== 0) { showToast('详情加载失败：' + r.message, 'error'); return; }
      current.value = r.data;
      const c = await request('/api/articles/' + a.id + '/comments/');
      comments.value = c.code === 0 ? c.data : [];
      await loadState();
    }

    async function addComment() {
      if (!current.value || !commentForm.content.trim()) {
        showToast('请先选一篇文章并写点内容', 'error'); return;
      }
      const r = await request('/api/articles/' + current.value.id + '/comments/', {
        method: 'POST',
        body: { authorName: commentForm.authorName, content: commentForm.content }
      });
      if (r.code === 0) {
        commentForm.content = '';
        const c = await request('/api/articles/' + current.value.id + '/comments/');
        comments.value = c.code === 0 ? c.data : [];
        await loadState();
        showToast('评论已发表', 'ok');
      } else {
        showToast('发表失败：' + r.message + '（需要先登录）', 'error');
      }
    }

    async function publish(a) {
      const r = await request('/api/articles/' + a.id + '/publish/', { method: 'POST' });
      if (r.code === 0) { current.value = r.data; await loadAll(); showToast('已发布', 'ok'); }
      else showToast('操作失败：' + r.message + '（需要先登录）', 'error');
    }

    async function unpublish(a) {
      const r = await request('/api/articles/' + a.id + '/unpublish/', { method: 'POST' });
      if (r.code === 0) { current.value = r.data; await loadAll(); showToast('已转为草稿', 'ok'); }
      else showToast('操作失败：' + r.message + '（需要先登录）', 'error');
    }

    async function removeArticle(a) {
      const r = await request('/api/articles/' + a.id + '/', { method: 'DELETE' });
      if (r.code === 0) {
        current.value = null; comments.value = [];
        await loadAll(); showToast('文章已删除', 'ok');
      } else showToast('删除失败：' + r.message + '（需要先登录）', 'error');
    }

    async function switchBackend(name) {
      const r = await request('/api/cache/switch/', { method: 'POST', body: { backend: name } });
      if (r.code === 0) {
        showToast(r.data.message, 'ok');
        await loadState();
      } else showToast('切换失败：' + r.message, 'error');
    }

    async function resetDemo() {
      const r = await request('/api/demo/reset/', { method: 'POST' });
      if (r.code === 0) {
        steps.value = []; current.value = null; comments.value = []; filters.page = 1;
        await loadAll();
        showToast('演示数据已重置（保留账号与分类）', 'ok');
      } else showToast('重置失败：' + r.message, 'error');
    }

    function onFilter() { filters.page = 1; loadArticles(); }
    function changePage(delta) { filters.page += delta; loadArticles(); }

    onMounted(() => {
      loadAll();
      // 便于自动化验证 / 直接分享：带 ?demo=1 打开页面会自动跑一遍完整流程
      if (new URLSearchParams(location.search).get('demo') === '1') {
        setTimeout(() => { runDemo(); }, 1200);
      }
    });

    return {
      state, articlePage, token, user, loginForm, form, commentForm, filters,
      current, comments, steps, running, toast, ov, cache, cacheBackend, cacheLabel,
      doLogin, runDemo, saveArticle, openDetail, addComment, publish, unpublish,
      removeArticle, switchBackend, resetDemo, onFilter, changePage, loadAll,
      fmt, pillClass
    };
  }
}).mount('#app');
