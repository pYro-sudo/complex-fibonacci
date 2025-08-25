package org.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import org.apache.commons.math3.complex.Complex;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Fibonacci extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(Fibonacci.class);
    static private final Complex INV_SQRT_5 = new Complex(1 / Math.sqrt(5), 0);
    static private final Complex LOG_PHI = new Complex((1 + Math.sqrt(5)) / 2, 0).log();
    static private final Complex LOG_PSI = new Complex((1 - Math.sqrt(5)) / 2, 0).log();

    private RedisAPI redisAPI;
    private ExecutorService executorService;
    private HttpServer server;

    @Override
    public void start(Promise<Void> startPromise) {
        logger.info("Starting Fibonacci verticle");
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        logger.debug("Created executor service with {} threads", Runtime.getRuntime().availableProcessors() * 2);

        RedisOptions redisOptions = new RedisOptions()
                .setConnectionString(System.getenv().getOrDefault("REDIS_URL", "redis://redis:6379"))
                .setMaxPoolSize(10)
                .setMaxWaitingHandlers(100);

        Redis.createClient(vertx, redisOptions)
                .connect()
                .onSuccess(conn -> {
                    redisAPI = RedisAPI.api(conn);
                    logger.info("Redis connected successfully");
                    setupHttpServer(startPromise);
                })
                .onFailure(err -> {
                    logger.warn("Redis connection failed: {}", err.getMessage());
                    logger.info("Continuing without cache...");
                    redisAPI = null;
                    setupHttpServer(startPromise);
                });
    }

    private void setupHttpServer(Promise<Void> startPromise) {
        logger.info("Setting up HTTP server on port 8080");
        server = vertx.createHttpServer(new HttpServerOptions().setPort(8080));

        server.requestHandler(request -> {
            logger.debug("Received {} request to {}", request.method(), request.path());

            if (request.method().name().equals("POST") && request.path().equals("/fibonacci")) {
                request.bodyHandler(body -> {
                    try {
                        JsonObject json = body.toJsonObject();
                        String input = json.getString("number");
                        logger.info("Processing POST request with input: {}", input);

                        process(request, input);
                    } catch (Exception e) {
                        logger.warn("Invalid JSON format in POST request: {}", e.getMessage());
                        request.response()
                                .setStatusCode(400)
                                .end(new JsonObject()
                                        .put("error", "Invalid JSON format")
                                        .encode());
                    }
                });
            } else if (request.method().name().equals("GET") && request.path().equals("/fibonacci")) {
                String input = request.getParam("number");
                if (input != null) {
                    logger.info("Processing GET request with input: {}", input);
                    process(request, input);
                } else {
                    logger.warn("Missing 'number' parameter in GET request");
                    request.response()
                            .setStatusCode(400)
                            .end(new JsonObject()
                                    .put("error", "Missing 'number' parameter")
                                    .encode());
                }
            } else {
                logger.warn("Invalid request path: {}", request.path());
                request.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put("error", "Not found")
                                .encode());
            }
        });

        server.listen(8080, result -> {
            if (result.succeeded()) {
                logger.info("HTTP server started successfully on port 8080");
                logger.info("Send POST requests to /fibonacci with JSON: {\"number\": \"a b\"}");
                logger.info("Or GET requests to /fibonacci?number=a+b");
                startPromise.complete();
            } else {
                logger.error("HTTP server startup failed: {}", result.cause().getMessage());
                startPromise.fail(result.cause());
            }
        });
    }

    private void process(HttpServerRequest request, String input) {
        logger.debug("Processing input: {}", input);
        processRequest(input)
                .thenAccept(result -> {
                    JsonObject response = new JsonObject()
                            .put("input", input)
                            .put("result", formatComplex(result));

                    logger.info("Successfully processed input: {}, result: {}", input, formatComplex(result));
                    request.response()
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                })
                .exceptionally(ex -> {
                    logger.error("Error processing input {}: {}", input, ex.getMessage());
                    request.response()
                            .setStatusCode(400)
                            .end(new JsonObject()
                                    .put("error", ex.getMessage())
                                    .encode());
                    return null;
                });
    }

    @Contract("_ -> new")
    private @NotNull CompletableFuture<Complex> processRequest(String input) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Complex z = parseComplex(input);
                logger.debug("Parsed complex number: {}", formatComplex(z));
                return execute(z).join();
            } catch (NumberFormatException e) {
                logger.error("Number format error for input {}: {}", input, e.getMessage());
                throw new RuntimeException(e.getMessage());
            }
        }, executorService);
    }

    private CompletableFuture<Complex> execute(Complex z) {
        if (redisAPI == null) {
            logger.debug("Cache not available, computing directly for: {}", formatComplex(z));
            return computeFibonacci(z);
        }

        String key = "fib:" + formatComplexForCache(z);
        logger.debug("Checking cache for key: {}", key);

        return getFromCacheAsync(key)
                .thenCompose(cachedResult -> {
                    if (cachedResult != null) {
                        logger.info("Cache hit for key: {}", key);
                        return CompletableFuture.completedFuture(cachedResult);
                    }

                    logger.debug("Cache miss for key: {}", key);
                    return computeFibonacci(z)
                            .thenCompose(result -> saveToCacheAsync(key, result)
                                    .thenApply(__ -> {
                                        logger.debug("Successfully cached result for key: {}", key);
                                        return result;
                                    })
                                    .exceptionally(ex -> {
                                        logger.warn("Failed to save to cache for key {}: {}", key, ex.getMessage());
                                        return result;
                                    }));
                })
                .exceptionally(ex -> {
                    logger.warn("Cache error for key {}, computing directly: {}", key, ex.getMessage());
                    return computeFibonacci(z).join();
                });
    }

    @Contract("_ -> new")
    private @NotNull CompletableFuture<Complex> computeFibonacci(Complex z) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Computing Fibonacci for: {}", formatComplex(z));
                Complex result = z.multiply(LOG_PHI).exp()
                        .subtract(z.multiply(LOG_PSI).exp())
                        .multiply(INV_SQRT_5);

                if (result.isNaN() || result.isInfinite()) {
                    logger.error("Numerical instability in Fibonacci computation for: {}", formatComplex(z));
                    throw new ArithmeticException("Numerical instability in Fibonacci computation");
                }

                logger.debug("Computation completed for: {}, result: {}", formatComplex(z), formatComplex(result));
                return result;
            } catch (Exception e) {
                logger.error("Fibonacci computation failed for {}: {}", formatComplex(z), e.getMessage());
                throw new RuntimeException("Fibonacci computation via logarithms failed", e);
            }
        }, executorService);
    }

    private @NotNull CompletableFuture<Complex> getFromCacheAsync(String key) {
        logger.trace("Getting from cache: {}", key);
        CompletableFuture<Complex> future = new CompletableFuture<>();

        vertx.executeBlocking(promise ->
                redisAPI.get(key)
                        .onSuccess(response -> {
                            if (response != null) {
                                try {
                                    Complex result = parseComplexFromCache(response.toString());
                                    logger.trace("Cache get successful for key: {}", key);
                                    promise.complete(result);
                                } catch (Exception e) {
                                    logger.warn("Failed to parse cached value for key {}: {}", key, e.getMessage());
                                    promise.fail(e);
                                }
                            } else {
                                logger.trace("Cache miss for key: {}", key);
                                promise.complete(null);
                            }
                        })
                        .onFailure(promise::fail), false, result -> {
            if (result.succeeded()) {
                future.complete((Complex) result.result());
            } else {
                logger.warn("Cache get failed for key {}: {}", key, result.cause().getMessage());
                future.completeExceptionally(result.cause());
            }
        });

        return future;
    }

    private @NotNull CompletableFuture<Void> saveToCacheAsync(String key, Complex result) {
        logger.trace("Saving to cache: {} -> {}", key, formatComplex(result));
        CompletableFuture<Void> future = new CompletableFuture<>();

        vertx.executeBlocking(promise -> {
            String value = formatComplexForCache(result);
            redisAPI.setex(key, "3600", value)
                    .onSuccess(__ -> {
                        logger.trace("Cache save successful for key: {}", key);
                        promise.complete();
                    })
                    .onFailure(promise::fail);
        }, false, asyncResult -> {
            if (asyncResult.succeeded()) {
                future.complete(null);
            } else {
                logger.warn("Cache save failed for key {}: {}", key, asyncResult.cause().getMessage());
                future.completeExceptionally(asyncResult.cause());
            }
        });

        return future;
    }

    private Complex parseComplex(@NotNull String input) {
        try {
            double[] values = Arrays.stream(
                            input.replace(',', '.')
                                    .replace('+', ' ')
                                    .split(" "))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .mapToDouble(Double::parseDouble)
                    .toArray();

            Complex result = switch (values.length) {
                case 1 -> new Complex(values[0], 0);
                case 2 -> new Complex(values[0], values[1]);
                default -> throw new NumberFormatException("Not supported amount of variables");
            };

            logger.debug("Successfully parsed input '{}' to complex number: {}", input, formatComplex(result));
            return result;
        } catch (Exception e) {
            logger.error("Failed to parse input '{}': {}", input, e.getMessage());
            throw e;
        }
    }

    private @NotNull String formatComplex(@NotNull Complex z) {
        double re = z.getReal();
        double im = z.getImaginary();

        return (Double.compare(re, -0.0) == 0 ? "-0.0000000000000000" :
                String.format(java.util.Locale.US, "%.16f", re)) +
                (Double.compare(im, 0.0) >= 0 ? "+" : "-") +
                (Double.compare(Math.abs(im), -0.0) == 0 ? "0.0000000000000000" :
                        String.format(java.util.Locale.US, "%.16f", Math.abs(im))) +
                "i";
    }

    private @NotNull String formatComplexForCache(@NotNull Complex z) {
        double re = z.getReal();
        double im = z.getImaginary();

        return (Double.compare(re, -0.0) == 0 ? "-0.0000000000000000" :
                String.format(java.util.Locale.US, "%.16f", re)) +
                " " +
                (Double.compare(im, -0.0) == 0 ? "-0.0000000000000000" :
                        String.format(java.util.Locale.US, "%.16f", im));
    }

    @Contract("_ -> new")
    private @NotNull Complex parseComplexFromCache(@NotNull String cached) {
        try {
            String[] parts = cached.split(" ");
            if (parts.length != 2) {
                throw new RuntimeException("Invalid cache format: " + cached);
            }

            Complex result = new Complex(
                    parts[0].equals("-0.0000000000000000") ? -0.0 : Double.parseDouble(parts[0]),
                    parts[1].equals("-0.0000000000000000") ? -0.0 : Double.parseDouble(parts[1])
            );

            logger.trace("Successfully parsed cached value: {} -> {}", cached, formatComplex(result));
            return result;
        } catch (Exception e) {
            logger.error("Failed to parse cached value '{}': {}", cached, e.getMessage());
            throw e;
        }
    }

    @Override
    public void stop() {
        logger.info("Stopping Fibonacci verticle");
        if (server != null) {
            server.close();
            logger.info("HTTP server stopped");
        }
        if (executorService != null) {
            executorService.shutdown();
            logger.info("Executor service shutdown");
        }
    }

    public static void main(String[] args) {
        logger.info("Starting Fibonacci application");
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(Fibonacci.class.getName(), new DeploymentOptions().setHa(true).setInstances(10))
                .onSuccess(id -> logger.info("Verticle deployed successfully: {}", id))
                .onFailure(err -> logger.error("Deployment failed: {}", err.getMessage()));
    }
}