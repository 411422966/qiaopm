import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EchoServer — 简易 HTTP echo 服务，仅用于本地测试 AlertManager webhook 通道连通性。
 *
 * 行为：
 *   GET  /              → 返回自启动以来累计请求数
 *   POST /*             → echo 接收到的 body（JSON 格式：path / bytes / receivedTotal）
 *   GET  /status/200    → 200（健康检查）
 *   GET  /anything      → 200
 *
 * 设计目标：
 *   - 纯 JDK 17（com.sun.net.httpserver.HttpServer），不依赖任何第三方库
 *   - 监听 :80（容器内）
 *   - 一次只服务一个端口，无外部配置
 *
 * 限制：
 *   - 无 TLS（生产 webhook 应在反代后）
 *   - 不持久化日志到磁盘（AlertManager 日志 + amtool 即是验证手段）
 *   - 不鉴权（仅本地 meiyetec-net 内部使用）
 */
public class EchoServer {

    private static final AtomicLong TOTAL = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        int port = 80;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new EchoHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("[EchoServer] started on :" + port);
    }

    static class EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws java.io.IOException {
            long n = TOTAL.incrementAndGet();
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            byte[] body = new byte[0];
            try (InputStream in = ex.getRequestBody()) {
                body = in.readAllBytes();
            }
            String resp = "{"
                    + "\"method\":\"" + method + "\","
                    + "\"path\":\"" + path + "\","
                    + "\"bytes\":" + body.length + ","
                    + "\"receivedTotal\":" + n
                    + "}";
            byte[] respBytes = resp.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, respBytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(respBytes);
            }
            System.out.println("[EchoServer] " + method + " " + path
                    + " body=" + body.length + "B  total=" + n);
        }
    }
}
