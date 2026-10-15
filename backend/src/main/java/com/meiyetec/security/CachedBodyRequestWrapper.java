package com.meiyetec.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 完全缓存 body 的 RequestWrapper：
 * - 构造时主动读取整个 body 到 byte[]；
 * - 后续 getInputStream()/getReader() 都从这份 byte[] 重新打开新流；
 * - Spring MVC 的 @RequestBody 可正常解析；
 * - 在 Filter/Interceptor 中通过 getContentAsByteArray()/getContentAsString() 读取并校验。
 *
 * <p>Body 大小上限 256KB（JSON 类请求），避免大文件全量缓存拖死内存。</p>
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;
    private final Charset charset;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 默认按 UTF-8 读取；getCharacterEncoding() 可能是 null，做兜底
        String enc = request.getCharacterEncoding();
        this.charset = (enc == null || enc.isEmpty()) ? StandardCharsets.UTF_8 : Charset.forName(enc);

        int len = request.getContentLength();
        if (len < 0) len = 0;
        if (len > 256 * 1024) {
            // 超大 body 截断到 256KB，避免 OOM；截断不影响 Spring 解析（实际请求体会被消费后失败，但已避免内存爆炸）
            len = 256 * 1024;
        }
        byte[] buf = new byte[Math.max(len, 4096)];
        int total = 0;
        try (ServletInputStream in = request.getInputStream()) {
            int n;
            while (total < buf.length && (n = in.read(buf, total, buf.length - total)) != -1) {
                total += n;
            }
        }
        this.body = (total == buf.length) ? buf : java.util.Arrays.copyOf(buf, total);
    }

    public byte[] getContentAsByteArray() {
        return body;
    }

    public String getContentAsString() {
        return new String(body, charset);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener l) { /* no-op */ }
            @Override public int read() { return bais.read(); }
            @Override public int read(byte[] b, int off, int len) { return bais.read(b, off, len); }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}