package com.onlinejudge.assessmentservice.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

interface SandboxExecutionClient {
    DockerSandboxClient.Result evaluate(String language, byte[] source, String input, int timeLimitMs, int memoryLimitKb);
}

/**
 * Creates an untrusted LAB execution container through the narrowly scoped
 * Docker socket proxy.  The worker never gets the Docker Unix socket itself.
 */
final class DockerSandboxClient implements SandboxExecutionClient {
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int COMPILE_FAILURE_EXIT_CODE = 66;
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final int MAX_SOURCE_BYTES = 512 * 1024;
    private static final int MAX_INPUT_BYTES = 128 * 1024;
    private final URI baseUri;
    private final RuntimeImage python;
    private final RuntimeImage java;
    private final RuntimeImage cpp;
    private final ObjectMapper json = new ObjectMapper();

    DockerSandboxClient(String apiUri, String image) {
        this(apiUri, image, "eclipse-temurin:21-jdk-alpine", "gcc:14.2.0");
    }

    DockerSandboxClient(String apiUri, String pythonImage, String javaImage, String cppImage) {
        this.baseUri = apiUri == null || apiUri.isBlank() ? null : URI.create(apiUri.endsWith("/") ? apiUri : apiUri + "/");
        this.python = new RuntimeImage(pythonImage == null || pythonImage.isBlank() ? "python:3.12-alpine" : pythonImage,
                "printf %s \"$OJ_SOURCE_B64\" | base64 -d > /workspace/Main.py; printf %s \"$OJ_INPUT_B64\" | base64 -d > /workspace/input.txt; python3 -m py_compile /workspace/Main.py || exit " + COMPILE_FAILURE_EXIT_CODE + "; exec python3 /workspace/Main.py < /workspace/input.txt");
        this.java = new RuntimeImage(javaImage == null || javaImage.isBlank() ? "eclipse-temurin:21-jdk-alpine" : javaImage,
                "printf %s \"$OJ_SOURCE_B64\" | base64 -d > /workspace/Main.java; printf %s \"$OJ_INPUT_B64\" | base64 -d > /workspace/input.txt; mkdir -p /workspace/classes; javac -d /workspace/classes /workspace/Main.java || exit " + COMPILE_FAILURE_EXIT_CODE + "; exec java -cp /workspace/classes Main < /workspace/input.txt");
        this.cpp = new RuntimeImage(cppImage == null || cppImage.isBlank() ? "gcc:14.2.0" : cppImage,
                "printf %s \"$OJ_SOURCE_B64\" | base64 -d > /workspace/Main.cpp; printf %s \"$OJ_INPUT_B64\" | base64 -d > /workspace/input.txt; g++ -O2 -std=c++20 /workspace/Main.cpp -o /workspace/Main || exit " + COMPILE_FAILURE_EXIT_CODE + "; exec /workspace/Main < /workspace/input.txt");
    }

    boolean configured() {
        return baseUri != null;
    }

    public Result evaluate(String language, byte[] source, String input, int timeLimitMs, int memoryLimitKb) {
        RuntimeImage runtime = runtime(language);
        if (!configured()) return new Result(null, "SYSTEM_ERROR");
        if (runtime == null) {
            return new Result(null, "COMPILE_ERROR");
        }
        if (source.length > MAX_SOURCE_BYTES || (input != null && input.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)) {
            return new Result(null, "SYSTEM_ERROR");
        }
        String containerId = null;
        int timeoutMs = Math.max(1, timeLimitMs);
        try {
            containerId = createContainer(runtime, source, input, timeoutMs, memoryLimitKb);
            require(request("POST", "/containers/" + containerId + "/start", new byte[0], null, CONNECT_TIMEOUT_MS), 204);
            Response waited = request("POST", "/containers/" + containerId + "/wait", new byte[0], null, timeoutMs);
            require(waited, 200);
            int exitCode = json.readTree(waited.body()).path("StatusCode").asInt(Integer.MIN_VALUE);
            String output = readLogs(containerId, timeoutMs);
            return exitCode == 0 ? new Result(output, null)
                    : new Result(output, exitCode == COMPILE_FAILURE_EXIT_CODE ? "COMPILE_ERROR" : "RUNTIME_ERROR");
        } catch (SocketTimeoutException timedOut) {
            kill(containerId);
            return new Result(null, "TIME_LIMIT_EXCEEDED");
        } catch (Exception rejected) {
            return new Result(null, "SYSTEM_ERROR");
        } finally {
            remove(containerId);
        }
    }

    private String createContainer(RuntimeImage runtime, byte[] source, String input, int timeLimitMs, int memoryLimitKb) throws IOException {
        Map<String, Object> hostConfig = new LinkedHashMap<>();
        hostConfig.put("NetworkMode", "none");
        hostConfig.put("ReadonlyRootfs", true);
        hostConfig.put("PidsLimit", 64);
        hostConfig.put("Memory", Math.max(4L * 1024 * 1024, memoryLimitKb * 1024L));
        hostConfig.put("NanoCpus", 1_000_000_000L);
        hostConfig.put("CapDrop", List.of("ALL"));
        hostConfig.put("SecurityOpt", List.of("no-new-privileges:true"));
        hostConfig.put("Tmpfs", Map.of(
                "/workspace", "rw,nosuid,nodev,exec,size=8m,mode=1777",
                "/tmp", "rw,nosuid,nodev,noexec,size=16m,mode=1777"));
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("Image", runtime.image());
        definition.put("User", "65534:65534");
        definition.put("WorkingDir", "/workspace");
        definition.put("Env", List.of(
                "PYTHONDONTWRITEBYTECODE=1", "PYTHONUNBUFFERED=1", "OJ_TIME_LIMIT_MS=" + timeLimitMs,
                "OJ_MEMORY_LIMIT_KB=" + memoryLimitKb,
                "OJ_SOURCE_B64=" + Base64.getEncoder().encodeToString(source),
                "OJ_INPUT_B64=" + Base64.getEncoder().encodeToString(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8))));
        definition.put("Cmd", List.of("sh", "-ec", runtime.command()));
        definition.put("HostConfig", hostConfig);
        Response created = request("POST", "/containers/create?name=oj-lab-" + UUID.randomUUID(),
                json.writeValueAsBytes(definition), "application/json", CONNECT_TIMEOUT_MS);
        require(created, 201);
        JsonNode body = json.readTree(created.body());
        String id = body.path("Id").asText();
        if (id.isBlank()) throw new IOException("Docker create did not return a container id");
        return id;
    }

    private String readLogs(String containerId, int timeoutMs) throws IOException {
        Response logs = request("GET", "/containers/" + containerId + "/logs?stdout=1&stderr=1&tail=200", null, null, timeoutMs);
        require(logs, 200);
        return new String(unframe(logs.body()), StandardCharsets.UTF_8);
    }

    private void kill(String containerId) {
        if (containerId == null) return;
        try {
            request("POST", "/containers/" + containerId + "/kill", new byte[0], null, CONNECT_TIMEOUT_MS);
        } catch (Exception ignored) {
            // Removal below is forceful and is the final cleanup boundary.
        }
    }

    private void remove(String containerId) {
        if (containerId == null) return;
        try {
            request("DELETE", "/containers/" + containerId + "?force=1&v=1", null, null, CONNECT_TIMEOUT_MS);
        } catch (Exception ignored) {
            // A failed cleanup must not hide the evaluation outcome.
        }
    }

    private Response request(String method, String path, byte[] body, String contentType, int readTimeoutMs) throws IOException {
        URI target = baseUri.resolve(path.startsWith("/") ? path.substring(1) : path);
        HttpURLConnection connection = (HttpURLConnection) target.toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(Math.max(1, readTimeoutMs));
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
            try (var output = connection.getOutputStream()) {
                output.write(body);
            }
        }
        int status = connection.getResponseCode();
        try (InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            return new Response(status, stream == null ? new byte[0] : readLimited(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static void require(Response response, int expectedStatus) throws IOException {
        if (response.status() != expectedStatus) throw new IOException("Docker API returned " + response.status());
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (output.size() < MAX_OUTPUT_BYTES) {
            int max = Math.min(buffer.length, MAX_OUTPUT_BYTES - output.size());
            int read = input.read(buffer, 0, max);
            if (read < 0) break;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static byte[] unframe(byte[] raw) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(raw.length);
        for (int offset = 0; offset < raw.length;) {
            if (offset + 8 > raw.length || raw[offset] < 0 || raw[offset] > 3) return raw;
            int length = ((raw[offset + 4] & 0xff) << 24) | ((raw[offset + 5] & 0xff) << 16)
                    | ((raw[offset + 6] & 0xff) << 8) | (raw[offset + 7] & 0xff);
            if (length < 0 || offset + 8 + length > raw.length) return raw;
            output.write(raw, offset + 8, length);
            offset += 8 + length;
        }
        return output.toByteArray();
    }

    record Result(String output, String status) { }
    private record Response(int status, byte[] body) { }
    private record RuntimeImage(String image, String command) { }

    private RuntimeImage runtime(String language) {
        if (language == null) return null;
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "python", "python3" -> python;
            case "java" -> java;
            case "cpp", "c++", "cc", "cxx" -> cpp;
            default -> null;
        };
    }
}
