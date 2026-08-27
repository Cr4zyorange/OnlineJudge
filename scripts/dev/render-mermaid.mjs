#!/usr/bin/env node
/**
 * 将 Mermaid 图源（.mmd）渲染为 SVG 静态资产。
 *
 * 用法（每两个参数为一组：mmd 源文件路径 + 输出 svg 路径）：
 *   node scripts/dev/render-mermaid.mjs <mmdPath> <svgPath> [<mmdPath> <svgPath> ...]
 *
 * 依赖（本机开发工具，不进入产物）：
 *   - Chrome/Edge headless（默认读取 CHROME_PATH，或常见安装路径）
 *   - mermaid.min.js（默认读取 MERMAID_JS，或 VS Code Markdown Preview Enhanced 扩展捆绑版本）
 *
 * 生成结果与仓库既有 assets/*.svg 约定保持一致：
 *   - svg id 统一为 my-svg
 *   - 保留 xmlns:xlink
 *   - 背景为白色
 */
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

function firstExisting(candidates) {
  return candidates.find((candidate) => candidate && existsSync(candidate));
}

function vscodeMermaidCandidates() {
  const extensionRoot = join(homedir(), '.vscode', 'extensions');
  if (!existsSync(extensionRoot)) {
    return [];
  }
  return readdirSync(extensionRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name.startsWith('shd101wyy.markdown-preview-enhanced-'))
    .map((entry) => join(
      extensionRoot,
      entry.name,
      'crossnote',
      'dependencies',
      'mermaid',
      'mermaid.min.js'
    ));
}

function executableFromPath(names) {
  const lookup = process.platform === 'win32' ? 'where' : 'which';
  for (const name of names) {
    const result = spawnSync(lookup, [name], { encoding: 'utf8' });
    const candidate = result.status === 0 ? result.stdout.split(/\r?\n/).find(Boolean)?.trim() : undefined;
    if (candidate && existsSync(candidate)) {
      return candidate;
    }
  }
  return undefined;
}

const DEFAULT_MERMAID_JS = firstExisting([
  process.env.MERMAID_JS,
  join(repoRoot, 'frontend', 'node_modules', 'mermaid', 'dist', 'mermaid.min.js'),
  join(repoRoot, 'node_modules', 'mermaid', 'dist', 'mermaid.min.js'),
  ...vscodeMermaidCandidates()
]);

const DEFAULT_CHROME = firstExisting([
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
  '/usr/bin/google-chrome',
  '/usr/bin/google-chrome-stable',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser'
]) || executableFromPath([
  'google-chrome',
  'google-chrome-stable',
  'chromium',
  'chromium-browser',
  'chrome',
  'msedge'
]);

const args = process.argv.slice(2);
if (args.length === 0 || args.length % 2 !== 0) {
  console.error('用法：node scripts/dev/render-mermaid.mjs <mmdPath> <svgPath> [<mmdPath> <svgPath> ...]');
  process.exit(1);
}

const mermaidJs = DEFAULT_MERMAID_JS;
const chromePath = DEFAULT_CHROME;
if (!mermaidJs) {
  console.error('未找到 mermaid.min.js，请通过 MERMAID_JS 环境变量指定');
  process.exit(1);
}
if (!chromePath) {
  console.error('未找到 Chrome/Edge，请通过 CHROME_PATH 环境变量指定');
  process.exit(1);
}

const workDir = join(tmpdir(), `oj-mermaid-render-${process.pid}`);
mkdirSync(workDir, { recursive: true });

function buildHtml(mmdPath) {
  const source = readFileSync(mmdPath, 'utf8');
  const payload = JSON.stringify(source);
  return `<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body>
<script src="${pathToFileURL(mermaidJs).href}"></script>
<script>
window.__MMD = ${payload};
window.addEventListener('DOMContentLoaded', async () => {
  try {
    mermaid.initialize({ startOnLoad: false, theme: 'default', securityLevel: 'loose' });
    const res = await mermaid.render('my-svg', window.__MMD);
    document.body.innerHTML = res.svg;
    const svg = document.querySelector('svg');
    svg.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
    const style = svg.getAttribute('style') || '';
    svg.setAttribute('style', style + '; background-color: white;');
  } catch (error) {
    document.body.innerHTML = '<pre style="color:red">' + error.message + '</pre>';
  }
});
</script>
</body>
</html>`;
}

function extractSvg(dump) {
  const match = dump.match(/<svg[\s\S]*?<\/svg>/);
  if (!match) {
    return null;
  }
  return match[0]
    .replace(/ id="my-svg"/, ' id="my-svg"')
    .replace(/<svg id="my-svg"/, '<svg id="my-svg"');
}

try {
  for (let index = 0; index < args.length; index += 2) {
    const mmdPath = args[index];
    const svgPath = args[index + 1];
    const htmlPath = join(workDir, `fig-${index / 2}.html`);
    writeFileSync(htmlPath, buildHtml(mmdPath), 'utf8');

    const result = spawnSync(
      chromePath,
      [
        '--headless=new',
        '--disable-gpu',
        '--disable-dev-shm-usage',
        '--no-sandbox',
        '--dump-dom',
        '--virtual-time-budget=8000',
        pathToFileURL(htmlPath).href,
      ],
      { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
    );
    if (result.error || result.status !== 0) {
      console.error(`渲染失败：${mmdPath}（Chrome 退出码 ${result.status ?? 'spawn-error'}）`);
      console.error((result.error?.message || result.stderr || '').slice(0, 500));
      process.exitCode = 1;
      continue;
    }
    const svg = extractSvg(result.stdout);
    if (!svg || /<pre style="color:red">/.test(result.stdout)) {
      console.error(`渲染失败：${mmdPath}（未生成 SVG 或 mermaid 抛错）`);
      process.exitCode = 1;
      continue;
    }
    mkdirSync(dirname(svgPath), { recursive: true });
    writeFileSync(svgPath, svg, 'utf8');
    console.log(`OK  ${mmdPath} -> ${svgPath}`);
  }
} finally {
  rmSync(workDir, { recursive: true, force: true });
}
process.exit(process.exitCode ?? 0);
