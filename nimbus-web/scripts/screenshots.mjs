/**
 * 项目截图生成: 登录后逐页截屏, 输出到 docs/screenshots/
 */
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';
import path from 'node:path';

const BASE = 'http://localhost:5173';
const OUT = path.resolve('../docs/screenshots');
fs.mkdirSync(OUT, { recursive: true });
console.log('output:', OUT);

const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
  args: ['--no-sandbox', '--force-device-scale-factor=1.5'],
});
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const shot = async (name) => {
  await sleep(600);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log('saved:', name);
};

// 1. 登录页
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
await sleep(1500);
await shot('login');

// 登录
await page.click('button[type="submit"]');
await page.waitForFunction(() => location.pathname === '/files', { timeout: 9000 });
await sleep(1800);
await shot('files');

// 2. 预览抽屉
await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.ant-table-tbody tr')];
  const row = rows.find((r) => r.textContent?.includes('.txt') || r.textContent?.includes('.xlsx'));
  row?.querySelector('td:nth-child(2) [class*="ant-space"]')?.click();
});
await sleep(1600);
await shot('preview');
await page.evaluate(() => document.querySelector('.ant-drawer-close')?.click());
await sleep(600);

// 3. 传输管理
await page.evaluate(() => [...document.querySelectorAll('button')].find((b) => b.textContent?.includes('传输列表'))?.click());
await page.waitForFunction(() => location.pathname === '/transfers', { timeout: 6000 });
await sleep(1600);
await shot('transfers');

// 4. 共享协作
await page.goto(`${BASE}/shares`, { waitUntil: 'domcontentloaded' });
await sleep(1800);
await shot('shares');

// 5. 回收站
await page.goto(`${BASE}/trash`, { waitUntil: 'domcontentloaded' });
await sleep(1600);
await shot('trash');

// 6. 设置
await page.goto(`${BASE}/settings`, { waitUntil: 'domcontentloaded' });
await sleep(1600);
await shot('settings');

// 7. 分享访问页(取一个已有分享短码, 没有则跳过)
const shareCode = await page.evaluate(async () => {
  const tk = localStorage.getItem('nimbus_token');
  const resp = await fetch('/api/share/my?pageNum=1&pageSize=1', { headers: { Authorization: tk } });
  const json = await resp.json();
  return json.data?.records?.[0]?.shortCode ?? '';
});
if (shareCode) {
  await page.goto(`${BASE}/s/${shareCode}`, { waitUntil: 'domcontentloaded' });
  await sleep(1800);
  await shot('share-access');
}

await browser.close();
console.log('done');