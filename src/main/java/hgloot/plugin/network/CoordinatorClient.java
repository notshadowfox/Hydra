package hgloot.plugin.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class CoordinatorClient {
    private final NetworkNodeConfig config;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final HttpClient client;

    public CoordinatorClient(NetworkNodeConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.requestTimeoutMillis()))
            .build();
    }

    public <T> CompletableFuture<T> get(String path, Class<T> responseType) {
        HttpRequest request = request(path).GET().build();
        return send(request, responseType);
    }

    public <T> CompletableFuture<T> post(String path, Object body, Class<T> responseType) {
        String json = gson.toJson(body == null ? new JsonObject() : body);
        HttpRequest request = request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return send(request, responseType);
    }

    private HttpRequest.Builder request(String path) {
        URI target = config.coordinatorUri().resolve(path.startsWith("/") ? path : "/" + path);
        return HttpRequest.newBuilder(target)
            .timeout(Duration.ofMillis(config.requestTimeoutMillis()))
            .header("X-Hydra-Secret", config.sharedSecret())
            .header("X-Hydra-Node", config.nodeName());
    }

    private <T> CompletableFuture<T> send(HttpRequest request, Class<T> responseType) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new CoordinatorException(response.statusCode(), response.body());
                }
                return gson.fromJson(response.body(), responseType);
            });
    }

    public void logFailure(String action, Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause instanceof IOException ? "coordinator unavailable" : cause.getMessage();
        logger.warning("[Network] " + action + " failed: " + message);
    }

    public static final class CoordinatorException extends RuntimeException {
        private final int status;

        public CoordinatorException(int status, String body) {
            super("HTTP " + status + ": " + body);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
