const fs = require('fs')
const src = fs.readFileSync('frontend/meiyetec-web/src/router/index.ts', 'utf8')
const bundle = fs.readFileSync('.audit-tmp/router-bundle.js', 'utf8')

// 源码：单引号
const re1 = /\{\s*path:\s*'([^']*)'\s*,\s*name:\s*'([^']*)'([\s\S]*?)\}/g
let m
const srcCodes = new Set()
while ((m = re1.exec(src))) {
  const b = m[3]
  const i = b.indexOf('meta:')
  if (i < 0) continue
  const mm = b.slice(i + 5).match(/permission:\s*'([^']*)'/)
  if (mm) srcCodes.add(mm[1])
}

// bundle：反引号（构建后保留模板字符串）
const bundleCodes = new Set()
const re2 = /permission:\s*[`"']([a-zA-Z0-9:._-]+)[`"']/g
while ((m = re2.exec(bundle))) bundleCodes.add(m[1])

const onlySrc = [...srcCodes].filter(c => !bundleCodes.has(c)).sort()
const onlyBundle = [...bundleCodes].filter(c => !srcCodes.has(c)).sort()

console.log('源码路由权限码数:', srcCodes.size)
console.log('容器 bundle 权限码数:', bundleCodes.size)
console.log('交集:', [...srcCodes].filter(c => bundleCodes.has(c)).length)
console.log('仅源码有 (' + onlySrc.length + '):', onlySrc.join(', '))
console.log('仅容器有 (' + onlyBundle.length + '):', onlyBundle.join(', '))

// 同时比对 path+permission 组合
const srcPairs = new Set()
const re3 = /\{\s*path:\s*'([^']*)'\s*,\s*name:\s*'([^']*)'([\s\S]*?)\}/g
while ((m = re3.exec(src))) {
  const b = m[3]
  const i = b.indexOf('meta:')
  if (i < 0) continue
  const mm = b.slice(i + 5).match(/permission:\s*'([^']*)'/)
  if (mm) srcPairs.add(m[1] + ' => ' + mm[1])
}
const bundlePairs = new Set()
const re4 = /path:\s*[`"']([^`"']+)[`"'],\s*name:\s*[`"']([^`"']+)[`"'][\s\S]{0,400}?permission:\s*[`"']([a-zA-Z0-9:._-]+)[`"']/g
while ((m = re4.exec(bundle))) bundlePairs.add(m[1] + ' => ' + m[3])

const onlySrcPairs = [...srcPairs].filter(p => !bundlePairs.has(p)).sort()
const onlyBundlePairs = [...bundlePairs].filter(p => !srcPairs.has(p)).sort()

console.log('\n--- path => permission 组合 ---')
console.log('源码组合数:', srcPairs.size, ' bundle 组合数:', bundlePairs.size, ' 交集:', [...srcPairs].filter(p => bundlePairs.has(p)).length)
console.log('仅源码有 (' + onlySrcPairs.length + '):')
onlySrcPairs.forEach(p => console.log('   ', p))
console.log('仅容器有 (' + onlyBundlePairs.length + '):')
onlyBundlePairs.forEach(p => console.log('   ', p))

fs.writeFileSync(
  '.audit-tmp/bundle-vs-src.json',
  JSON.stringify(
    { srcCount: srcCodes.size, bundleCount: bundleCodes.size, onlySrc, onlyBundle, onlySrcPairs, onlyBundlePairs },
    null,
    1
  )
)
