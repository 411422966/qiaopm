/**
 * I121 证据：运行容器镜像 vs 当前源码 HEAD 的漂移量化
 * 不走 cmd shell，避免 % 转义问题
 */
const fs = require('fs')
const { execFileSync } = require('child_process')

function git(args) {
  return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).trim()
}
function docker(args) {
  return execFileSync('docker', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).trim()
}

const out = {}

console.log('=== 1. 容器与镜像 ===')
console.log(docker(['ps', '--format', '{{.Names}}|{{.Image}}|{{.Status}}']))

console.log('\n=== 2. 镜像构建时间 ===')
console.log(docker(['images', '--format', '{{.Repository}}:{{.Tag}}|{{.CreatedAt}}']))

console.log('\n=== 3. 源码 HEAD ===')
console.log(git(['log', '-1', '--format=%H %ad %s', '--date=iso']))
console.log('镜像 api 构建时间对应的源码提交:')
console.log(git(['log', '-1', '--format=%H %ad %s', '--date=iso', '--before=2026-10-20 17:14:10']))
console.log('镜像 web 构建时间对应的源码提交:')
console.log(git(['log', '-1', '--format=%H %ad %s', '--date=iso', '--before=2026-10-10 16:16:56']))

console.log('\n=== 4. api 镜像(2026-10-20 17:14) 之后的提交 ===')
const apiBase = git(['log', '-1', '--format=%H', '--before=2026-10-20 17:14:10'])
const apiAfter = git(['log', '--oneline', `${apiBase}..HEAD`]).split('\n').filter(Boolean)
console.log('提交数:', apiAfter.length)
apiAfter.forEach(l => console.log('   ', l))

console.log('\n=== 5. web 镜像(2026-10-10 16:16) 之后的提交 ===')
const webBase = git(['log', '-1', '--format=%H', '--before=2026-10-10 16:16:56'])
const webAfter = git(['log', '--oneline', `${webBase}..HEAD`]).split('\n').filter(Boolean)
console.log('提交数:', webAfter.length)

console.log('\n=== 6. api 漂移区间的文件变更 ===')
const changed = git(['diff', '--name-only', `${apiBase}..HEAD`]).split('\n').filter(Boolean)
const ctrlChanged = changed.filter(f => f.includes('controller/'))
const feChanged = changed.filter(f => f.startsWith('frontend/'))
const beChanged = changed.filter(f => f.startsWith('backend/') && !f.includes('controller/'))
console.log('总变更文件:', changed.length)
console.log('  后端 controller/:', ctrlChanged.length)
console.log('  后端非 controller/:', beChanged.length)
console.log('  前端 src/:', feChanged.length)

console.log('\n=== 7. 容器内 Controller 注解覆盖 vs 源码 ===')
docker(['exec', 'meiyetec-api', 'sh', '-c', 'cd /tmp && rm -rf jj && mkdir jj && cd jj && unzip -q /app/app.jar "BOOT-INF/classes/com/meiyetec/controller/*.class" && grep -rl RequirePermission BOOT-INF/classes/com/meiyetec/controller/ | wc -l'])
const inImage = parseInt(
  docker(['exec', 'meiyetec-api', 'sh', '-c', 'cd /tmp/jj && grep -rl RequirePermission BOOT-INF/classes/com/meiyetec/controller/ | wc -l']),
  10
)
const inImageTotal = parseInt(
  docker(['exec', 'meiyetec-api', 'sh', '-c', 'cd /tmp/jj && ls BOOT-INF/classes/com/meiyetec/controller/*.class | wc -l']),
  10
)
const CTRL_DIR = 'D:/QiaoPM/backend/src/main/java/com/meiyetec/controller'
const srcFiles = fs.readdirSync(CTRL_DIR).filter(f => f.endsWith('.java'))
const srcWith = srcFiles.filter(f => /@RequirePermission/.test(fs.readFileSync(CTRL_DIR + '/' + f, 'utf8'))).length
console.log(`容器镜像: ${inImage}/${inImageTotal} 个 Controller 类含 RequirePermission`)
console.log(`当前源码: ${srcWith}/${srcFiles.length} 个 Controller 文件含 @RequirePermission`)
console.log(`差值: ${srcWith - inImage} 个文件`)

console.log('\n=== 8. 前端 bundle vs 源码路由码 ===')
console.log(fs.readFileSync('D:/QiaoPM/.audit-tmp/bundle-vs-src.json', 'utf8'))

fs.writeFileSync(
  'D:/QiaoPM/.audit-tmp/image-drift.json',
  JSON.stringify(
    {
      apiImageBuiltAt: '2026-10-20T17:14:10+08:00',
      webImageBuiltAt: '2026-10-10T16:16:56+08:00',
      headCommit: git(['log', '-1', '--format=%H']),
      apiBaseCommit: apiBase,
      webBaseCommit: webBase,
      commitsAfterApiImage: apiAfter.length,
      commitsAfterWebImage: webAfter.length,
      changedFilesAfterApiImage: changed.length,
      changedBackendControllers: ctrlChanged.length,
      changedBackendOther: beChanged.length,
      changedFrontend: feChanged.length,
      imageControllersWithPerm: inImage,
      imageControllersTotal: inImageTotal,
      srcControllersWithPerm: srcWith,
      srcControllersTotal: srcFiles.length,
      drift: srcWith - inImage
    },
    null,
    1
  )
)
console.log('\nwritten .audit-tmp/image-drift.json')
