// nimbus-web 端到端浏览器验证(puppeteer-core + 系统 Chrome)
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';

const BASE = 'http://localhost:5173';
let pass = 0;
let fail = 0;
const ok = (name) => { pass++; console.log(`[PASS] ${name}`); };
const bad = (name, detail) => { fail++; console.log(`[FAIL] ${name} -> ${detail}`); };
const check = (name, cond, detail = '') => (cond ? ok(name) : bad(name, detail));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const browser = await puppeteer.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
  args: ['--no-sandbox'],
});
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
let openTriggered = false;
page.on('console', (m) => {
  if (m.text().includes('nimbus-open')) openTriggered = true;
});
const batchResponses = [];
page.on('response', (resp) => {
  if (resp.url().includes('/download/batch')) {
    batchResponses.push({ status: resp.status(), type: resp.headers()['content-type'] ?? '' });
  }
});

const text = (sel) => page.$eval(sel, (el) => el.textContent ?? '').catch(() => '');
/** 等待表格出现指定名称的行 */
const waitRow = (name, timeoutMs = 9000) =>
  page.waitForFunction(
    (n) => [...document.querySelectorAll('.ant-table-tbody tr')].some((r) => r.textContent?.includes(n)),
    { timeout: timeoutMs },
    name,
  );

/** 点击当前(末尾)弹窗 footer 的确定按钮 */
const clickFooterOk = () =>
  page.evaluate(() => {
    const modal = [...document.querySelectorAll('.ant-modal')].at(-1);
    const footer = modal?.querySelector('.ant-modal-footer');
    const okBtn = [...(footer?.querySelectorAll('button') ?? [])].at(-1);
    okBtn?.click();
  });
/** 当前弹窗数量 */
const modalCount = () => page.evaluate(() => document.querySelectorAll('.ant-modal').length);

/** 按可见文本点按(忽略 antd 双字按钮空格) */
const clickByText = (t) =>
  page.evaluate((target) => {
    const wanted = target.replace(/\s+/g, '');
    const all = [...document.querySelectorAll('button, a, label, .ant-tag, .ant-menu-item, span[class*="ant-btn"]')];
    const el = all.find((e) => e.textContent?.replace(/\s+/g, '').includes(wanted));
    if (!el) throw new Error('not found: ' + target);
    el.click();
  }, t);

/** 表格名称列文本(第 1 列为选择框, 名称在第 3 列) */
const tableNames = () =>
  page.$$eval('.ant-table-tbody tr td:nth-child(2)', (els) => els.map((e) => e.textContent?.trim() ?? ''));

/** 按名称定位行并执行动作(默认点击名称列); fnBody 以 (row, document) 运行 */
const rowAction = (name, fnBody) =>
  page.evaluate(({ name, fnBody }) => {
    const rows = [...document.querySelectorAll('.ant-table-tbody tr')];
    const row = rows.find((r) => r.textContent?.includes(name));
    if (!row) return false;
    new Function('row', 'document', fnBody)(row, document);
    return true;
  }, { name, fnBody });

/** 页内 fetch(诊断/校验用) */
const pageFetch = (url, options) => page.evaluate(async ({ url, options }) => {
  const resp = await fetch(url, options);
  return resp.json();
}, { url, options });

// ---------- 1. 登录 ----------
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await page.click('button[type="submit"]');
await page.waitForFunction(() => location.pathname === '/files', { timeout: 8000 });
check('登录后跳转 /files', page.url().includes('/files'), page.url());
await sleep(1200);

// ---------- 2. 侧边栏/顶栏/配额 ----------
const hasMenu = (name) => page.$$eval('.ant-menu-item', (els, n) => els.some((e) => e.textContent?.includes(n)), name);
check('侧边栏-我的文件', await hasMenu('我的文件'));
check('侧边栏-回收站', await hasMenu('回收站'));
check('侧边栏-存储空间卡片', (await text('.ant-layout-sider')).includes('存储空间'));
check('顶栏-传输列表按钮', (await text('.ant-layout-header')).includes('传输列表'));
check('面包屑-我的文件', (await text('.breadcrumb-bar')).includes('我的文件'));

// ---------- 3. 文件列表渲染 ----------
const beforeNames = await tableNames();
check('文件列表渲染有数据', beforeNames.length > 0, JSON.stringify(beforeNames));

// ---------- 4. 新建文件夹并进入 ----------
await clickByText('新建文件夹');
await page.waitForSelector('.ant-modal input');
await page.type('.ant-modal input', 'e2e-测试目录');
await clickByText('创建');
await sleep(1000);
const afterCreate = await tableNames();
check('新建文件夹出现在列表', afterCreate.some((n) => n.includes('e2e-测试目录')), JSON.stringify(afterCreate));

const entered = await rowAction('e2e-测试目录', `row.querySelector('td:nth-child(2) [class*="ant-space"]')?.click()`);
check('定位文件夹行', entered);
await sleep(1000);
check('进入文件夹显示空态', (await text('.ant-empty')).includes('此文件夹为空'));
await page.evaluate(() => [...document.querySelectorAll('.crumb')].find((c) => c.textContent === '我的文件')?.click());
await sleep(900);

// ---------- 5. 上传文件 ----------
fs.writeFileSync('scripts/e2e-upload.txt', 'hello nimbus web e2e upload check');
const fileInput = await page.$('.ant-layout-header input[type=file]');
if (fileInput) {
  await fileInput.uploadFile('scripts/e2e-upload.txt');
  await page.waitForFunction(() => location.pathname === '/transfers', { timeout: 5000 });
  await sleep(1200);
  check('上传后自动跳转传输页', page.url().includes('/transfers'));
  let done = false;
  for (let i = 0; i < 30; i++) {
    if ((await text('.transfer-item')).includes('秒传完成') || includes('已完成')) { done = true; break; }
    await sleep(500);
  }
  check('上传任务完成', done, (await text('.transfer-item')).slice(0, 100));
  await page.goto(`${BASE}/files`, { waitUntil: 'domcontentloaded' });
  await sleep(1200);
  const namesAfterUpload = await tableNames();
  check('上传文件出现在列表', namesAfterUpload.some((n) => n.includes('e2e-upload.txt')), JSON.stringify(namesAfterUpload));
} else {
  bad('顶栏上传 input', 'not found');
}

// ---------- 6. 预览抽屉 ----------
await rowAction('e2e-upload.txt', `row.querySelector('td:nth-child(2) [class*="ant-space"]')?.click()`);
await sleep(1000);
const drawerText = await text('.ant-drawer');
check('预览抽屉打开', drawerText.includes('e2e-upload.txt'));
check('预览抽屉-版本信息', drawerText.includes('1 版'), drawerText.slice(0, 80));
await page.evaluate(() => document.querySelector('.ant-drawer-close')?.click());
await sleep(500);

// ---------- 6.5 批量下载(多选 -> 下载, 回归 405 问题) ----------
await rowAction('e2e-upload.txt', `row.querySelector('td:first-child input')?.click()`);
await sleep(700);
const selBar = await text('.selection-bar');
check('批量选择条出现', selBar.includes('已选 1 项') || selBar.includes('已选'), selBar.slice(0, 60));
await clickByText('下载');
await sleep(1500);
check('批量下载返回 200(zip)', batchResponses.some((r) => r.status === 200), JSON.stringify(batchResponses));
await page.evaluate(() => [...document.querySelectorAll('.selection-bar a')].find((a) => a.textContent?.includes('取消选择'))?.click());
await sleep(300);

// ---------- 6.7 下载任务进入传输管理 ----------
await rowAction('e2e-upload.txt', `row.querySelector('.ant-tag').click()`);
await sleep(700);
await page.evaluate(() => [...document.querySelectorAll('.ant-dropdown-menu-item')].find((i) => i.textContent?.includes('下载'))?.click());
await sleep(800);
// 通过顶栏按钮 SPA 导航进入传输页(整页刷新会清空内存中的任务队列)
await clickByText('传输列表');
await page.waitForFunction(() => location.pathname === '/transfers', { timeout: 5000 });
await sleep(1200);
const dlRows = await page.$$eval('.transfer-item', (els) => els.map((e) => e.textContent ?? ''));
const dlTask = dlRows.find((r) => r.includes('e2e-upload.txt'));
check('下载任务出现在传输页', dlTask !== undefined && (dlTask.includes('下载中') || dlTask.includes('已完成')), (dlTask ?? '').slice(0, 120));
let dlDone = false;
for (let i = 0; i < 20; i++) {
  const rows = await page.$$eval('.transfer-item', (els) => els.map((e) => e.textContent ?? ''));
  if (rows.some((r) => r.includes('e2e-upload.txt') && r.includes('已完成'))) {
    dlDone = true;
    break;
  }
  await sleep(500);
}
check('下载任务完成', dlDone);
// 右下角下载完成通知(可能有多条, 找目标文件的)
const notif = await page.evaluate(() => {
  const notices = [...document.querySelectorAll('.ant-notification-notice')];
  const el = notices.find((n) => n.textContent?.includes('e2e-upload.txt')) ?? null;
  if (!el) return null;
  const btns = [...(el.querySelectorAll('button') ?? [])].map((b) => (b.textContent ?? '').replace(/\s+/g, ''));
  return { text: el.textContent ?? '', hasOpenBtn: btns.includes('打开') };
});
check('下载完成右下角通知出现', notif !== null && notif.text.includes('下载完成'), JSON.stringify(notif));
check('通知含「打开」按钮', notif?.hasOpenBtn === true);
// 注: 「打开」点击验证放在用例末尾独立执行, 避免新标签干扰后续弹窗流程

// ---------- 7. 创建分享 ----------
await page.goto(`${BASE}/files`, { waitUntil: 'domcontentloaded' });
await sleep(1200);
await rowAction('e2e-upload.txt', `row.querySelector('.ant-tag').click()`);
await sleep(700);
await page.evaluate(() => [...document.querySelectorAll('.ant-dropdown-menu-item')].find((i) => i.textContent?.includes('分享'))?.click());
await sleep(700);
check('分享弹窗打开', (await page.$('.ant-modal')) !== null);
check('分享弹窗仅一个', (await modalCount()) === 1, `count=${await modalCount()}`);
await clickFooterOk();
await sleep(1200);
const shareCode = await page.evaluate(() => {
  const input = [...document.querySelectorAll('.ant-modal input')].find((i) => /\/s\//.test(i.value ?? ''));
  return input?.value?.match(/\/s\/([A-Za-z0-9]+)/)?.[1] ?? '';
});
check('分享创建成功(含短链)', shareCode.length > 0, `code=${shareCode}`);
await clickFooterOk();
await sleep(600);
check('结果弹窗已关闭', (await modalCount()) === 0, `count=${await modalCount()}`);

// ---------- 8. 分享访问页(免登录) ----------
if (shareCode) {
  const sharePage = await browser.newPage();
  await sharePage.goto(`${BASE}/s/${shareCode}`, { waitUntil: 'domcontentloaded' });
  await sleep(1200);
  const shareText = await sharePage.evaluate(() => document.body.innerText);
  check('分享访问页渲染', shareText.includes('Nimbus 云盘 · 分享'));
  check('分享访问页-默认全选权限展示', shareText.includes('可预览') && shareText.includes('可下载') && shareText.includes('可转存'), shareText.slice(0, 120));
  check('分享访问页-条目可见', shareText.includes('e2e-upload.txt'), shareText.slice(0, 160));
  check('分享访问页-下载/转存按钮', shareText.includes('保存到我的网盘'));
  await sharePage.close();
}

// ---------- 8.5 共享协作-新建分享(回归: 弹窗只出现一次, 选文件后切到配置弹窗) ----------
await page.goto(`${BASE}/shares`, { waitUntil: 'domcontentloaded' });
await sleep(1000);
await clickByText('新建分享');
await sleep(600);
const modalCount1 = await page.evaluate(() => document.querySelectorAll('.ant-modal').length);
check('新建分享-仅弹出一个选择器', modalCount1 === 1, `count=${modalCount1}`);
// 等待文件列表加载后点击第一个文件
await page.waitForSelector('.ant-modal [class*="spin"] .ant-spin-spinning', { timeout: 2000 }).catch(() => {});
for (let i = 0; i < 10; i++) {
  const hasRow = await page.evaluate(() => {
    const modal = [...document.querySelectorAll('.ant-modal')].at(-1);
    return modal ? modal.textContent?.includes('分享 →') ?? false : false;
  });
  if (hasRow) break;
  await sleep(400);
}
const picked = await page.evaluate(() => {
  const modal = [...document.querySelectorAll('.ant-modal')].at(-1);
  const span = [...(modal?.querySelectorAll('span') ?? [])].find((d) => d.textContent?.trim() === '分享 →');
  span?.click();
  return span !== undefined;
});
check('新建分享-选择文件', picked);
await sleep(700);
const modalCount2 = await page.evaluate(() => document.querySelectorAll('.ant-modal').length);
check('选择后仍只有一个弹窗', modalCount2 === 1, `count=${modalCount2}`);
const configText = await text('.ant-modal');
check('切换到分享配置弹窗', configText.includes('分享方式') && configText.includes('永久有效'), configText.slice(0, 80));
// 权限应默认全选(可预览/可下载/可转存)
const permState = await page.evaluate(() => {
  const modal = [...document.querySelectorAll('.ant-modal')].at(-1);
  const checks = [...modal.querySelectorAll('.ant-checkbox-wrapper input')].map((i) => i.checked);
  const labels = [...modal.querySelectorAll('.ant-checkbox-wrapper')].map((l) => l.textContent?.trim());
  return { checks, labels };
});
check('分享权限默认全选(3项)', permState.checks.length === 3 && permState.checks.every(Boolean), JSON.stringify(permState));
// 创建并关闭
await clickFooterOk();
await sleep(1200);
check('配置弹窗创建成功', (await text('.ant-modal')).includes('分享已创建'));
await clickFooterOk();
await sleep(600);
check('配置结果弹窗已关闭', (await modalCount()) === 0, `count=${await modalCount()}`);

// ---------- 8.6 加密分享: 提取码自动生成 ----------
await clickByText('新建分享');
await sleep(600);
await page.waitForFunction(
  () => [...document.querySelectorAll('.ant-modal')].at(-1)?.textContent?.includes('分享 →') ?? false,
  { timeout: 8000 },
);
await page.evaluate(() => {
  const modal = [...document.querySelectorAll('.ant-modal')].at(-1);
  const span = [...(modal?.querySelectorAll('span') ?? [])].find((d) => d.textContent?.trim() === '分享 →');
  span?.click();
});
await sleep(700);
// 选择「加密分享」Radio
await page.evaluate(() => {
  const modal = [...document.querySelectorAll('.ant-modal')].at(-1);
  const label = [...(modal?.querySelectorAll('label') ?? [])].find((l) => l.textContent?.includes('加密'));
  label?.click();
});
await sleep(600);
const autoCode = await page.evaluate(() => {
  const modal = [...document.querySelectorAll('.ant-modal')].at(-1);
  const item = [...(modal?.querySelectorAll('.ant-form-item') ?? [])].find((i) =>
    i.querySelector('.ant-form-item-label')?.textContent?.includes('提取码'),
  );
  const input = item?.querySelector('input:not([type="radio"])');
  return input?.value ?? '';
});
check('加密分享-提取码自动生成(6位)', /^[2-9A-Z]{6}$/.test(autoCode), `code=${autoCode}`);
await clickFooterOk();
await sleep(1200);
check('加密分享-创建成功展示提取码', (await text('.ant-modal')).includes('提取码:'));
// 链接应自动附带提取码(?code=)
const encLink = await page.evaluate(() => {
  const input = [...document.querySelectorAll('.ant-modal input')].find((i) => /^https?:\/\/.*\/s\//.test(i.value ?? ''));
  return input?.value ?? '';
});
check('加密分享-链接自带提取码', /\/s\/[A-Za-z0-9]+\?code=/.test(encLink), encLink);
await clickFooterOk();
await sleep(600);
check('加密分享结果弹窗已关闭', (await modalCount()) === 0, `count=${await modalCount()}`);
// 接收方: 粘贴带码链接到浏览器, 自动识别提取码并展示
const encCodeMatch = encLink.match(/\/s\/([A-Za-z0-9]+)\?code=([^&]+)/);
if (encCodeMatch) {
  const encPage = await browser.newPage();
  await encPage.goto(`${BASE}/s/${encCodeMatch[1]}?code=${encCodeMatch[2]}`, { waitUntil: 'domcontentloaded' });
  await encPage.waitForFunction(() => document.body.innerText.includes('分享内容'), { timeout: 9000 }).catch(() => {});
  await sleep(800);
  const encText = await encPage.evaluate(() => document.body.innerText);
  check('带码链接打开-自动识别并显示条目', encText.includes('e2e-upload.txt'), encText.slice(0, 160));
  check('带码链接打开-无需手动输入提取码', !encText.includes('请输入提取码'));
  await encPage.close();
}

// ---------- 8.7 拖拽移动(行 -> 文件夹行 / 行 -> 面包屑根) ----------
// 清理历史运行累积的重复/残留文件(仅保留根目录一个), 避免拖拽移动时同名冲突
await page.evaluate(async () => {
  const token = localStorage.getItem('nimbus_token');
  const resp = await fetch('/api/netdisk/file/page?pageNum=1&pageSize=100', { headers: { Authorization: token } });
  const json = await resp.json();
  const all = (json.data?.records ?? []).filter((f) => f.fileName === 'e2e-upload.txt');
  const rootOnes = all.filter((f) => String(f.folderId) === '0');
  const toDelete = all.filter((f) => String(f.folderId) !== '0' || f.id !== rootOnes[0]?.id);
  for (const file of toDelete) {
    await fetch(`/api/netdisk/file/${file.id}`, { method: 'DELETE', headers: { Authorization: token } });
    await fetch(`/api/recycle/1/${file.id}`, { method: 'DELETE', headers: { Authorization: token } });
  }
});
await page.goto(`${BASE}/files`, { waitUntil: 'domcontentloaded' });
await sleep(1200);
const dragSeq = async (sourceText, targetSel) => {
  await page.evaluate(({ sourceText, targetSel }) => {
    const rows = [...document.querySelectorAll('.ant-table-tbody tr')];
    const source = rows.find((r) => r.textContent?.includes(sourceText));
    const targetRow = rows.find((r) => r.querySelector(targetSel));
    if (!source || !targetRow) return false;
    const dt = new DataTransfer();
    source.dispatchEvent(new DragEvent('dragstart', { dataTransfer: dt, bubbles: true, cancelable: true }));
    targetRow.dispatchEvent(new DragEvent('dragover', { dataTransfer: dt, bubbles: true, cancelable: true }));
    targetRow.dispatchEvent(new DragEvent('drop', { dataTransfer: dt, bubbles: true, cancelable: true }));
    return true;
  }, { sourceText, targetSel });
};
// 拖文件到文件夹行
const draggedToFolder = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.ant-table-tbody tr')];
  const source = rows.find((r) => r.textContent?.includes('e2e-upload.txt'));
  const target = rows.find((r) => r.textContent?.includes('e2e-测试目录'));
  if (!source || !target) return false;
  const dt = new DataTransfer();
  source.dispatchEvent(new DragEvent('dragstart', { dataTransfer: dt, bubbles: true, cancelable: true }));
  target.dispatchEvent(new DragEvent('dragover', { dataTransfer: dt, bubbles: true, cancelable: true }));
  target.dispatchEvent(new DragEvent('drop', { dataTransfer: dt, bubbles: true, cancelable: true }));
  return true;
});
check('拖拽-源与目标行定位', draggedToFolder);
await sleep(1000);
check('拖拽后文件离开根列表', !(await tableNames()).some((n) => n.includes('e2e-upload.txt')));
// 进入文件夹验证文件已移入
await rowAction('e2e-测试目录', `row.querySelector('td:nth-child(2) [class*="ant-space"]')?.click()`);
await sleep(1000);
check('拖拽后文件出现在目标文件夹', (await tableNames()).some((n) => n.includes('e2e-upload.txt')));
// 拖回面包屑「我的文件」(根)
const draggedBack = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.ant-table-tbody tr')];
  const source = rows.find((r) => r.textContent?.includes('e2e-upload.txt'));
  const crumb = [...document.querySelectorAll('.crumb')].find((c) => c.textContent === '我的文件');
  if (!source || !crumb) return { ok: false, why: 'source/crumb missing' };
  const dt = new DataTransfer();
  source.dispatchEvent(new DragEvent('dragstart', { dataTransfer: dt, bubbles: true, cancelable: true }));
  const over = new DragEvent('dragover', { dataTransfer: dt, bubbles: true, cancelable: true });
  crumb.dispatchEvent(over);
  const drop = new DragEvent('drop', { dataTransfer: dt, bubbles: true, cancelable: true });
  crumb.dispatchEvent(drop);
  return { ok: true, overPrevented: over.defaultPrevented, dropPrevented: drop.defaultPrevented, data: dt.getData('application/x-nimbus-row') };
});
check('拖拽-面包屑定位', draggedBack.ok === true, JSON.stringify(draggedBack));
await sleep(1500);
const backState = await pageFetch('/api/netdisk/file/page?pageNum=1&pageSize=100', { headers: { Authorization: await page.evaluate(() => localStorage.getItem('nimbus_token')) } });
const backFiles = (backState?.data?.records ?? []).filter((f) => f.fileName === 'e2e-upload.txt');
check('拖拽-文件已回到根(接口校验)', backFiles.some((f) => String(f.folderId) === '0') && !backFiles.some((f) => String(f.folderId) !== '0'), JSON.stringify(backFiles.map((f) => f.folderId)));

// ---------- 9. 收藏 ----------
await page.goto(`${BASE}/files`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await waitRow('e2e-upload.txt');
// 记录收藏前的列表顺序
const orderBeforeStar = await tableNames();
await rowAction('e2e-upload.txt', `{
  const star = [...row.querySelectorAll('td:last-child [aria-label]')].find((s) => (s.getAttribute('aria-label') ?? '').includes('star'));
  if (!star) return false;
  star.click();
  return true;
}`);
await sleep(1000);
// 收藏后列表顺序不变(不重载、不改 updateTime)
const orderAfterStar = await tableNames();
check('收藏后列表顺序不变', JSON.stringify(orderAfterStar) === JSON.stringify(orderBeforeStar),
  `${JSON.stringify(orderBeforeStar)} vs ${JSON.stringify(orderAfterStar)}`);
const starState = await pageFetch('/api/netdisk/file/page?pageNum=1&pageSize=50', { headers: { Authorization: await page.evaluate(() => localStorage.getItem('nimbus_token')) } });
const starredAny = (starState?.data?.records ?? []).some((f) => f.fileName === 'e2e-upload.txt' && f.isStarred === 1);
check('收藏接口生效(isStarred=1)', starredAny === true, `starred=${starredAny}`);
await page.goto(`${BASE}/starred`, { waitUntil: 'domcontentloaded' });
await sleep(1000);
check('收藏页出现该文件', (await text('.ant-table-tbody')).includes('e2e-upload.txt'));

// ---------- 10. 回收站: 删除 -> 列表 -> 恢复 ----------
await page.goto(`${BASE}/files`, { waitUntil: 'domcontentloaded' });
await sleep(1000);
await rowAction('e2e-upload.txt', `row.querySelector('.ant-tag').click()`);
await sleep(600);
await page.evaluate(() => [...document.querySelectorAll('.ant-dropdown-menu-item')].find((i) => i.textContent?.includes('移入回收站'))?.click());
await sleep(600);
await clickByText('移入回收站');
await sleep(1000);
await page.goto(`${BASE}/trash`, { waitUntil: 'domcontentloaded' });
await sleep(1000);
const trashText = await text('.ant-table-tbody');
check('回收站列表含该文件', trashText.includes('e2e-upload.txt'));
await rowAction('e2e-upload.txt', `{
  const btn = [...row.querySelectorAll('button')].find((b) => b.textContent?.includes('恢复'));
  btn?.click();
}`);
await sleep(1000);
check('恢复后回收站无该文件', !(await text('.ant-table-tbody')).includes('e2e-upload.txt'));

// ---------- 11. 搜索 ----------
await page.goto(`${BASE}/search?keyword=e2e`, { waitUntil: 'domcontentloaded' });
await sleep(1200);
check('搜索命中文件', (await text('.ant-table-tbody')).includes('e2e-upload.txt'));
check('搜索-重置条件按钮出现', (await text('.page-card')).includes('重置条件'));
// 追加类型条件重新搜索
await page.evaluate(() => {
  document.querySelectorAll('.ant-select')?.item(document.querySelectorAll('.ant-select').length - 1)?.click();
});
await sleep(600);
await page.evaluate(() => {
  const opts = [...document.querySelectorAll('.ant-select-item-option')];
  opts.find((o) => o.textContent?.includes('文档'))?.click();
});
await sleep(1000);
check('搜索-类型过滤生效', (await text('.ant-table-tbody')).includes('e2e-upload.txt') || (await text('.ant-table-tbody')).includes('物联网'));
// 重置条件: 直接返回我的文件页
await clickByText('重置条件');
await page.waitForFunction(() => location.pathname === '/files', { timeout: 6000 });
await sleep(1000);
const resetState = await page.evaluate(() => ({
  url: location.href,
  headerSearchValue: document.querySelector('.ant-layout-header input')?.value ?? 'NOT_FOUND',
  hasFilesBreadcrumb: document.body.innerText.includes('我的文件'),
}));
check('搜索-重置后返回我的文件页', resetState.url.includes('/files'), resetState.url);
check('搜索-重置后顶栏搜索框被清空', resetState.headerSearchValue === '', `value=${resetState.headerSearchValue}`);
check('搜索-重置后我的文件页正常渲染', resetState.hasFilesBreadcrumb === true);

// ---------- 12. 设置/配额 ----------
await page.goto(`${BASE}/settings`, { waitUntil: 'domcontentloaded' });
await sleep(1000);
const settingsText = await text('body');
check('设置页-账户信息', settingsText.includes('账户信息'));
check('设置页-总容量', settingsText.includes('总容量'));

// ---------- 13. 通知「打开」(末尾独立验证, 避免新标签干扰) ----------
await page.goto(`${BASE}/files`, { waitUntil: 'domcontentloaded' });
await sleep(800);
await waitRow('dl.txt');
await rowAction('dl.txt', `row.querySelector('.ant-tag').click()`);
await sleep(700);
await page.evaluate(() => [...document.querySelectorAll('.ant-dropdown-menu-item')].find((i) => i.textContent?.includes('下载'))?.click());
await page.waitForFunction(
  () => [...document.querySelectorAll('.ant-notification-notice')].some((n) => n.textContent?.includes('dl.txt') && n.textContent.includes('下载完成')),
  { timeout: 10000 },
);
await sleep(400);
await page.evaluate(() => {
  const notices = [...document.querySelectorAll('.ant-notification-notice')];
  const btn = notices.at(-1)?.querySelector('button');
  btn?.click();
});
await sleep(1000);
check('通知「打开」-打开动作已触发', openTriggered === true);

// ---------- 14. 传输记录持久化: 刷新后历史仍在 ----------
await page.goto(`${BASE}/transfers`, { waitUntil: 'domcontentloaded' });
await sleep(1500);
const afterReload = await page.$$eval('.transfer-item', (els) => els.map((e) => e.textContent ?? ''));
check('刷新后传输历史仍在(下载记录)', afterReload.some((r) => r.includes('dl.txt') && r.includes('已完成')), JSON.stringify(afterReload.slice(0, 3)));
check('刷新后传输历史仍在(上传记录)', afterReload.some((r) => r.includes('e2e-upload.txt') && (r.includes('已完成') || r.includes('秒传完成'))));
// 统计卡与分段筛选
const transferPageText = await text('body');
check('传输页-统计卡(上传速度/下载速度/今日已完成)', transferPageText.includes('上传速度') && transferPageText.includes('下载速度') && transferPageText.includes('今日已完成'));
check('传输页-分段筛选(全部/进行中/已完成/失败)', transferPageText.includes('进行中') && transferPageText.includes('已完成') && /失败 \d+/.test(transferPageText.replace(/\s+/g, ' ')));
check('传输页-断点续传提示', transferPageText.includes('支持断点续传'));

// ---------- 15. 「打开所在位置」定位云盘目录(?folderId=) ----------
const treeInfo = await pageFetch('/api/netdisk/folder/tree', { headers: { Authorization: await page.evaluate(() => localStorage.getItem('nimbus_token')) } });
const targetDir = (treeInfo?.data ?? []).find((f) => f.folderName === 'e2e-测试目录');
check('定位目标目录存在', targetDir !== undefined);
if (targetDir) {
  await page.goto(`${BASE}/files?folderId=${targetDir.id}`, { waitUntil: 'domcontentloaded' });
  await sleep(1200);
  const crumbText = await text('.breadcrumb-bar');
  check('打开所在位置-定位到云盘目录', crumbText.includes('e2e-测试目录'), crumbText.slice(0, 80));
}

console.log(`\ne2e result: PASS=${pass} FAIL=${fail}`);
if (errors.length) {
  console.log('page errors:', errors.slice(0, 5));
}
await browser.close();
process.exit(fail > 0 ? 1 : 0);