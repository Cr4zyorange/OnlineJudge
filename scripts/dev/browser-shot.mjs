#!/usr/bin/env node
/**
 * 无 Playwright 依赖的真实页面截图工具（headless Chrome + CDP）。
 * 用法：
 *   node scripts/dev/browser-shot.mjs --url <登录后页面URL> --token <Bearer Token> \
 *       --user-id <userId> --role <STUDENT|TEACHER|ADMIN> --username <username> --out <png路径>
 *
 * 用途：在无法安装 Playwright 的环境里，为端到端证据提供真实页面截图；
 * 页面通过真实 API 请求渲染，截图前注入与登录态一致的 localStorage。
 */
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = Object.fromEntries(
  process.argv.slice(2).map((value, index, all) =>
    value.startsWith('--') ? [value.slice(2), all[index + 1]] : null
  ).filter(Boolean)
);

const required = ['url', 'token', 'user-id', 'role', 'username', 'out'];
for (const key of required) {
  if (!args[key]) {
    console.error(`缺少参数 --${key}`);
    process.exit(1);
  }
}

const chromePath = process.env.CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const port = 9333 + Math.floor(Math.random() * 1000);
const userDataDir = join(tmpdir(), `oj-shot-${process.pid}`);
const chrome = spawn(chromePath, [
  '--headless=new',
  '--disable-gpu',
  '--no-sandbox',
  '--window-size=1440,900',
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${userDataDir}`,
  'about:blank'
], { stdio: 'ignore' });

async function jsonRequest(path, method = 'GET') {
  const response = await fetch(`http://127.0.0.1:${port}${path}`, { method });
  return response.json();
}

async function waitForChrome() {
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      await fetch(`http://127.0.0.1:${port}/json/version`);
      return;
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
  }
  throw new Error('Chrome CDP 未就绪');
}

let nextId = 1;
function connect(wsUrl) {
  const socket = new WebSocket(wsUrl);
  const pending = new Map();
  const listeners = new Map();
  socket.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id);
      pending.delete(message.id);
      message.error ? reject(new Error(message.error.message)) : resolve(message.result);
      return;
    }
    for (const handler of listeners.get(message.method) ?? []) {
      handler(message.params);
    }
  };
  return new Promise((resolve) => {
    socket.onopen = () => resolve({
      send(method, params = {}) {
        const id = nextId++;
        return new Promise((resolveSend, rejectSend) => {
          pending.set(id, { resolve: resolveSend, reject: rejectSend });
          socket.send(JSON.stringify({ id, method, params }));
        });
      },
      on(method, handler) {
        const handlers = listeners.get(method) ?? [];
        handlers.push(handler);
        listeners.set(method, handlers);
      },
      close() {
        socket.close();
      }
    });
  });
}

try {
  await waitForChrome();
  const tab = await jsonRequest(`/json/new?${encodeURIComponent(args.url)}`, 'PUT');
  const page = await connect(tab.webSocketDebuggerUrl);
  await page.send('Page.enable');
  await page.send('Runtime.enable');

  const storage = {
    'onlinejudge.authToken': args.token,
    'onlinejudge.userId': args['user-id'],
    'onlinejudge.userRole': args.role,
    'onlinejudge.username': args.username
  };
  await page.send('Page.navigate', { url: `http://127.0.0.1:5173/login` });
  await new Promise((resolve) => page.on('Page.loadEventFired', resolve));
  await page.send('Runtime.evaluate', {
    expression: `(() => { const values = ${JSON.stringify(storage)}; for (const [key, value] of Object.entries(values)) { localStorage.setItem(key, value); } return true; })()`
  });
  await page.send('Page.navigate', { url: args.url });
  await new Promise((resolve) => page.on('Page.loadEventFired', resolve));
  await new Promise((resolve) => setTimeout(resolve, 2500));
  const pageState = await page.send('Runtime.evaluate', {
    expression: 'document.body ? document.body.innerText.slice(0, 300).replace(/\\n+/g, " | ") : "NO_BODY"',
    returnByValue: true
  });
  console.log(`页面文本：${pageState.result.value}`);

  const shot = await page.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  mkdirSync(args.out.split(/[\\/]/).slice(0, -1).join('/'), { recursive: true });
  writeFileSync(args.out, Buffer.from(shot.data, 'base64'));
  console.log(`OK ${args.out}`);
  page.close();
} finally {
  chrome.kill();
}
