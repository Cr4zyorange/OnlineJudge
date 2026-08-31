import http from "node:http";

const service = process.env.SERVICE ?? "unknown";
const port = Number(process.env.PORT ?? "8080");
let uploadCount = 0;
const requestCounts = new Map();

const server = http.createServer((request, response) => {
  const chunks = [];
  request.on("data", (chunk) => chunks.push(chunk));
  request.on("end", () => {
    const url = new URL(request.url, "http://upstream");
    const path = url.pathname;

    if (path === "/__fixture/count") {
      const target = url.searchParams.get("target") ?? "";
      response.writeHead(200, { "content-type": "application/json" });
      response.end(JSON.stringify({ target, count: requestCounts.get(target) ?? 0 }));
      return;
    }

    requestCounts.set(path, (requestCounts.get(path) ?? 0) + 1);

    if (path.endsWith("/unavailable")) {
      request.socket.destroy();
      return;
    }
    if (path.endsWith("/slow")) {
      setTimeout(() => respond(response, request, path, 200, Buffer.concat(chunks)), 2500);
      return;
    }

    let status = path.endsWith("/controlled-unavailable") ? 503 : 200;
    if (path.endsWith("/unauthorized")) status = 401;
    if (path.endsWith("/forbidden")) status = 403;
    if (path.endsWith("/999999")) status = 404;
    if (request.method === "POST" && path.endsWith("/upload")) uploadCount += 1;
    respond(response, request, path, status, Buffer.concat(chunks));
  });
});

function respond(response, request, path, status, body) {
  const payload = JSON.stringify({
    service,
    path,
    status,
    authorization: request.headers.authorization ?? "",
    headers: Object.keys(request.headers).sort(),
    requestId: request.headers["x-request-id"] ?? "",
    bytes: body.length,
    uploadCount,
    requestCount: requestCounts.get(path) ?? 0,
  });
  response.writeHead(status, { "content-type": "application/json" });
  response.end(payload);
}

server.listen(port, "0.0.0.0");
