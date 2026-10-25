package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redisson 配置。
 * <p>
 * Redisson.create() 在 Redis 不可达时会直接抛异常，导致 Spring 容器启动失败。
 * 这里改为：启动时不强制要求 Redis 在线，暴露一个代理对象；Redis 就绪后由后台线程自动连上，
 * 在此之前调用 Redisson 相关功能会抛出明确的提示异常。
 */
@Slf4j
@Configuration
public class  RedissonConfig {

    private static final long RECONNECT_INTERVAL_MILLIS = 5000L;

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = buildConfig();
        AtomicReference<RedissonClient> clientRef = new AtomicReference<>();

        tryConnect(clientRef, config, true);
        startReconnectTask(clientRef, config);

        InvocationHandler handler = (proxy, method, args) -> {
            if (Object.class.equals(method.getDeclaringClass())) {
                return handleObjectMethod(proxy, method, args);
            }
            RedissonClient client = clientRef.get();
            if (client == null) {
                // Redis 未就绪时，容器销毁等生命周期调用不必报警
                String name = method.getName();
                if ("isShutdown".equals(name)) {
                    return Boolean.TRUE;
                }
                if ("shutdown".equals(name) || "isShuttingDown".equals(name)) {
                    return null;
                }
                throw new IllegalStateException("Redis 暂不可用（" + host + ":" + port + "），请启动 Redis 后重试");
            }
            return method.invoke(client, args);
        };

        return (RedissonClient) Proxy.newProxyInstance(
                RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class},
                handler);
    }

    private Config buildConfig() {
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectTimeout(3000)
                .setRetryAttempts(1)
                .setRetryInterval(1000);
        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }
        return config;
    }

    private void startReconnectTask(AtomicReference<RedissonClient> clientRef, Config config) {
        Thread task = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(RECONNECT_INTERVAL_MILLIS);
                    if (clientRef.get() == null && isReachable()) {
                        tryConnect(clientRef, config, false);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.debug("Redis 重连任务异常：{}", e.getMessage());
                }
            }
        }, "redisson-reconnect");
        task.setDaemon(true);
        task.start();
    }

    private void tryConnect(AtomicReference<RedissonClient> clientRef, Config config, boolean failOnError) {
        if (!isReachable()) {
            if (failOnError) {
                log.error("Redis 连接失败：{}:{}，应用继续启动，后台会持续重试", host, port);
            }
            return;
        }
        try {
            clientRef.set(Redisson.create(config));
            log.info("Redis 连接成功：{}:{}", host, port);
        } catch (Exception e) {
            log.error("Redis 连接失败：{}:{}，原因：{}", host, port, e.getMessage());
        }
    }

    private boolean isReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Object handleObjectMethod(Object proxy, java.lang.reflect.Method method, Object[] args) {
        switch (method.getName()) {
            case "toString":
                return "LazyRedissonClient(" + host + ":" + port + ")";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                return null;
        }
    }
}
