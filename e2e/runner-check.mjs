// 验证 UI 自测运行器会把浏览器错误转成非零退出码，防止“页面报错但测试仍绿”。
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

for (const mode of ['clean', 'console', 'pageerror']) {
  test(`运行器错误检测：${mode}`, () => {
    const source = `
      import { createRunner } from './lib.mjs'
      const { newPage, step, finish } = await createRunner('运行器校验')
      const page = await newPage('probe')
      await step('注入受控事件', async () => {
        if (${JSON.stringify(mode)} === 'console') {
          await page.evaluate(() => console.error('受控 console.error'))
        }
        if (${JSON.stringify(mode)} === 'pageerror') {
          const observed = new Promise(resolve => page.once('pageerror', resolve))
          await page.evaluate(() => setTimeout(() => { throw new Error('受控 pageerror') }, 0))
          await observed
        }
      })
      await finish()
    `
    const result = spawnSync(process.execPath, ['--input-type=module', '-e', source], {
      cwd: fileURLToPath(new URL('.', import.meta.url)), encoding: 'utf8', timeout: 30_000,
    })
    assert.ifError(result.error)
    assert.equal(result.status, mode === 'clean' ? 0 : 1, result.stdout + result.stderr)
    assert.match(result.stdout, /PASS.*注入受控事件/)
  })
}
