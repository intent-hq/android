// npm ci && npx playwright install chromium && npm test
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { chromium } from 'playwright';

const directory = dirname(fileURLToPath(import.meta.url));
const assets = resolve(directory, '../../app/src/main/assets/mermaid');
const origin = 'https://mermaid.intent.invalid/';
const vendor = JSON.parse(await readFile(resolve(assets, 'vendor.json')));
assert.equal(vendor.sha256, createHash('sha256').update(await readFile(resolve(assets, 'pele.bundle.js'))).digest('hex'));
assert.equal(vendor.packageLockSha256, createHash('sha256').update(await readFile(resolve(directory, 'package-lock.json'))).digest('hex'));
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 600 }, isMobile: true });
  const network = [];
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/*', async route => {
    const file = route.request().url().replace(origin, '');
    if (!['index.html', 'renderer.css', 'renderer.js', 'pele.bundle.js'].includes(file)) {
      network.push(route.request().url());
      await route.abort();
      return;
    }
    await route.fulfill({ path: resolve(assets, file) });
  });
  await page.goto(`${origin}index.html`);
  const render = (source, dark = false) => page.evaluate(args => window.renderMermaid(args), { source, dark });
  const fixture = 'flowchart LR\n A[Read note] --> B[Review diagram]';
  for (const dark of [false, true]) {
    for (const source of [fixture, 'sequenceDiagram\n Phone->>Server: Read notes', 'stateDiagram-v2\n [*] --> Draft']) {
      const result = await render(source, dark);
      assert.ok(result.height > 0);
      assert.equal(result.error, undefined);
      assert.equal(await page.locator('#diagram > svg').count(), 1);
      assert.equal(await page.evaluate(() => document.documentElement.classList.contains('dark')), dark);
    }
  }
  for (const source of ['', 'flowchart TD\n A[', 'x'.repeat(50001)]) {
    assert.equal((await render(source)).error, true);
    assert.equal(await page.locator('#diagram > svg').count(), 0);
  }
  const edges = `flowchart LR\n${Array.from({ length: 501 }, (_, i) => `N${i} --> N${i + 1}`).join('\n')}`;
  assert.equal((await render(edges)).error, true);
  assert.equal((await render(`---\nconfig:\n  maxEdges: 10000\n---\n${edges}`)).error, true);
  const hostile = `%%{init: {"securityLevel":"loose", "links":true, "images":true}}%%\nflowchart LR\nA["</script><script>window.compromised=true</script>"] --> B[Safe]\nclick B href "https://example.invalid/leak"`;
  await render(hostile);
  assert.equal(await page.evaluate(() => window.compromised), undefined);
  assert.equal(await page.locator('#diagram script, #diagram [onerror], #diagram [onclick], #diagram a[href], #diagram image, #diagram foreignObject').count(), 0);
  assert.ok((await render(fixture)).height > 0, 'Recovery after a parse error');
  assert.deepEqual(network, [], 'No requests beyond the bundled assets');
  assert.deepEqual(errors, []);
  console.log('PASS: bundled hashes, both themes, diagram fixtures, error recovery, limits, and no external requests');
} finally {
  await browser.close();
}
