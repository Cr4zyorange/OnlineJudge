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
import java.util.Map;
import java.util.UUID;

/**
 * Creates an untrusted LAB execution container through the narrowly scoped
 * Docker socket proxy.  The worker never gets the Docker Unix socket itself.
 */
final class DockerSandboxClient {
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final int MAX_SOURCE_BYTES = 512 * 1024;
    private static final int MAX_INPUT_BYTES = 128 * 1024;
    private final URI baseUri;
    private final String image;
    private final ObjectMapper json = new ObjectMapper();

    DockerSandboxClient(String apiUri, String image) {
        this.baseUri = apiUri == null || apiUri.isBlank() ? null : URI.create(apiUri.endsWith("/") ? apiUri : apiUri + "/");
        this.image = image == null || image.isBlank() ? "python:3.12-alpine" : image;
    }

    boolean configured() {
        return baseUri != null;
    }

    Result evaluate(String language, byte[] source, String input, int timeLimitMs, int memoryLimitKb) {
        if (!configured()) return new Result(null, "SANDBOX_UNCONFIGURED");
        if (!"python".equalsIgnoreCase(language) && !"python3".equalsIgnoreCase(language)) {
            return new Result(null, "COMPILE_ERROR");
        }
        if (source.length > MAX_SOURCE_BYTES || (input != null && input.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)) {
            return new Result(null, "SANDBOX_INPUT_TOO_LARGE");
        }
        String containerId = null;
        int timeoutMs = Math.max(1, timeLimitMs);
        try {
            containerId = createContainer(source, input, timeoutMs, memoryLimitKb);
            require(request("POST", "/containers/" + containerId + "/start", new byte[0], null, CONNECT_TIMEOUT_MS), 204);
            Response waited = request("POST", "/containers/" + containerId + "/wait", new byte[0], null, timeoutMs);
            require(waited, 200);
            int exitCode = json.readTree(waited.body()).path("StatusCode").asInt(Integer.MIN_VALUE);
            String output = readLogs(containerId, timeoutMs);
            return exitCode == 0 ? new Result(output, null) : new Result(output, "RUNTIME_ERROR");
        } catch (SocketTimeoutException timedOut) {
            kill(containerId);
            return new Result(null, "SANDBOX_TIMEOUT");
        } catch (Exception rejected) {
            return new Result(null, "SANDBOX_ERROR");
        } finally {
            remove(containerId);
        }
    }

    private String createContainer(byte[] source, String input, int timeLimitMs, int memoryLimitKb) throws IOException {
        Map<String, Object> hostConfig = new LinkedHashMap<>();
        hostConfig.put("NetworkMode", "none");
        hostConfig.put("ReadonlyRootfs", true);
        hostConfig.put("PidsLimit", 64);
        hostConfig.put("Memory", Math.max(4L * 1024 * 1024, memoryLimitKb * 1024L));
        hostConfig.put("NanoCpus", 1_000_000_000L);
        hostConfig.put("CapDrop", List.of("ALL"));
        hostConfig.put("SecurityOpt", List.of("no-new-privileges:true"));
        hostConfig.put("Tmpfs", Map.of(
                "/workspace", "rw,nosuid,nodev,noexec,size=8m,mode=1777",
                "/tmp", "rw,nosuid,nodev,noexec,size=16m,mode=1777"));
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("Image", image);
        definition.put("User", "65534:65534");
        definition.put("WorkingDir", "/workspace");
        definition.put("Env", List.of(
                "PYTHONDONTWRITEBYTECODE=1", "PYTHONUNBUFFERED=1", "OJ_TIME_LIMIT_MS=" + timeLimitMs,
                "OJ_MEMORY_LIMIT_KB=" + memoryLimitKb,
                "OJ_SOURCE_B64=" + Base64.getEncoder().encodeToString(source),
                "OJ_INPUT_B64=" + Base64.getEncoder().encodeToString(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8))));
        definition.put("Cmd", List.of("sh", "-ec", "printf %s \"$OJ_SOURCE_B64\" | base64 -d > /workspace/Main.py; printf %s \"$OJ_INPUT_B64\" | base64 -d > /workspace/input.txt; exec python3 /workspace/Main.py < /workspace/input.txt"));
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
}
