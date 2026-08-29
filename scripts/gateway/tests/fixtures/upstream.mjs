import http from "node:http";

const service = process.env.SERVICE ?? "unknown";
let uploadCount = 0;

const server = http.createServer((request, response) => {
  const chunks = [];
  request.on("data", (chunk) => chunks.push(chunk));
  request.on("end", () => {
    const path = new URL(request.url, "http://upstream").pathname;

    if (path.endsWith("/unavailable")) {
      request.socket.destroy();
      return;
    }
    if (path.endsWith("/slow")) {
      setTimeout(() => respond(response, request, path, 200, Buffer.concat(chunks)), 2500);
      return;
    }

    let status = 200;
    if (path.endsWith("/unauthorized")) status = 401;
    if (path.endsWith("/forbidden")) status = 403;
    if (path.endsWith("/999999")) status = 404;
    if (request.method === "POST" && path.endsWith("/upload")) uploadCount += 1;
    respond(response, request, path, status, Buffer.concat(chunks));
  });
});

function respond(response, request, path, status, body) {
  const stripped = [
    "x-user-id",
    "x-username",
    "x-user-role",
    "x-permissions",
    "x-course-ids",
    "x-manageable-course-ids",
  ].every((name) => request.headers[name] === undefined);
  const payload = JSON.stringify({
    service,
    path,
    status,
    authorization: request.headers.authorization ?? "",
    stripped,
    requestId: request.headers["x-request-id"] ?? "",
    bytes: body.length,
    uploadCount,
  });
  response.writeHead(status, { "content-type": "application/json" });
  response.end(payload);
}

server.listen(8080, "0.0.0.0");
