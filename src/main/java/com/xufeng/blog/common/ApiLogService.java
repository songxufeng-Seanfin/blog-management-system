package com.xufeng.blog.common;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/** 最近 N 条 API 请求的环形流水（放内存里，看板用来展示"请求怎么走了一圈"） */
@Service
public class ApiLogService {

    private static final int MAX = 80;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ConcurrentLinkedDeque<Map<String, Object>> ring = new ConcurrentLinkedDeque<>();
    private final AtomicLong seq = new AtomicLong();
    private final AtomicLong total = new AtomicLong();

    public void add(String method, String path, int status, long costMs, String note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", seq.incrementAndGet());
        row.put("time", LocalDateTime.now().format(FMT));
        row.put("method", method);
        row.put("path", path);
        row.put("status", status);
        row.put("cost_ms", costMs);
        row.put("note", note);
        ring.addLast(row);
        while (ring.size() > MAX) ring.pollFirst();
        total.incrementAndGet();
    }

    /** 最新的排在最前面 */
    public List<Map<String, Object>> recent(int limit) {
        List<Map<String, Object>> all = new ArrayList<>(ring);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            out.add(all.get(i));
        }
        return out;
    }

    public long total() { return total.get(); }

    public void clear() {
        ring.clear();
        total.set(0);
        seq.set(0);
    }
}
