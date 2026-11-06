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
 * <p>Body 大小上限 256KB（JSON 类请求），避免大文件全量缓存拖死内存。
 * 超过上限或 Content-Length 未知（chunked）的请求<b>不缓存</b>，原样透传，
 * 避免截断缓存导致请求体损坏。</p>
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    /** Body 大小上限 256KB */
    public static final int MAX_BODY_SIZE = 256 * 1024;

    /**
     * 请求体是否可以安全缓存：
     * <ul>
     *   <li>Content-Length 必须已知：chunked 传输时按未知长度读会截断请求体</li>
     *   <li>Content-Length 不得超过 MAX_BODY_SIZE：超限截断同样会损坏请求体</li>
     * </ul>
     */
    public static boolean isCacheable(HttpServletRequest request) {
        int len = request.getContentLength();
        return len >= 0 && len <= MAX_BODY_SIZE;
    }

    private final byte[] body;
    private final Charset charset;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        if (!isCacheable(request)) {
            // 不可缓存的请求必须原样透传，不能截断读取
            throw new IllegalArgumentException("请求体不可缓存（Content-Length 未知或超过 " + MAX_BODY_SIZE + " 字节）");
        }
        // 默认按 UTF-8 读取；getCharacterEncoding() 可能是 null，做兜底
        String enc = request.getCharacterEncoding();
        this.charset = (enc == null || enc.isEmpty()) ? StandardCharsets.UTF_8 : Charset.forName(enc);

        int len = request.getContentLength();
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