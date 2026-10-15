package com.meiyetec.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * SPA fallback (R2.0-C).
 *
 * <p>Scenario: User directly accesses SPA routes under backend domain
 * (browser favorites, external links, production environment without Nginx
 * try_files). Backend has no matching controller, previously got 401
 * via JWT interceptor. Now returns 200 HTML so frontend entry point can
 * take over.</p>
 *
 * <p>FIX：移除此前的 @GetMapping("/**")。原实现使用 GET /** 通配符会拦截所有
 * GET 请求（包括后端 API），并且 Spring MVC 在 path 命中但 method 不匹配时
 * 会抛 HttpRequestMethodNotSupportedException，导致同一 URL 的 POST/PUT/DELETE
 * 无法被真正的 @RestController 处理，全部返回 500 "Request method not supported"。
 * 改为：仅当 NoHandlerFoundException（没有任何 controller 命中路径）时才返回
 * SPA HTML，由 SpaFallbackExceptionHandler 接管。</p>
 *
 * <p>Production should still use Nginx try_files / static hosting to serve
 * index.html. This controller is a defensive fallback only.</p>
 */
@RestController
public class SpaFallbackController {

    private static final String SPA_FALLBACK_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <title>美业SaaS系统</title>
              <meta name="viewport" content="width=device-width,initial-scale=1.0">
            </head>
            <body>
              <div style="font-family:-apple-system,Segoe UI,sans-serif;padding:40px;text-align:center;color:#333">
                <h2>美业SaaS系统</h2>
                <p>该地址为 SPA 前端路由，请通过前端入口访问。</p>
              </div>
            </body>
            </html>
            """;

    /**
     * Global exception handler for NoHandlerFoundException.
     * Combined with spring.mvc.throw-exception-if-no-handler-found=true,
     * this catches any unmatched route and returns SPA fallback HTML.
     * Returning 200 lets frontend entry point take over.
     *
     * <p>FIX：删除了原 @GetMapping("/**") 通配 handler，避免 Spring MVC
     * 在 path 匹配但 method 不匹配时抛 HttpRequestMethodNotSupportedException，
     * 导致同 URL 的 POST/PUT/DELETE 请求被错误拒绝。</p>
     */
    @ControllerAdvice
    static class SpaFallbackExceptionHandler {
        @ExceptionHandler(NoHandlerFoundException.class)
        public ResponseEntity<String> handleNoHandler() {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(SPA_FALLBACK_HTML);
        }
    }
}
