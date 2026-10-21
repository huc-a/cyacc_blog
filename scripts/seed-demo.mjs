import { readFile } from 'node:fs/promises'
const base = process.env.API_URL || 'http://127.0.0.1:8080'
if (!['127.0.0.1', 'localhost'].includes(new URL(base).hostname)) throw new Error('Demo seed is restricted to a local development server')
const credentials = JSON.parse((await readFile(new URL('../.local/admin-credentials.json', import.meta.url), 'utf8')).replace(/^\uFEFF/, ''))
const cookies = new Map()
async function api(path, method = 'GET', body) {
  let headers = { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; ') }
  if (method !== 'GET') {
    const csrf = await api('/api/auth/csrf')
    headers = { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; '), [csrf.headerName]: csrf.token }
  }
  const isForm = body instanceof URLSearchParams
  if (body) headers['Content-Type'] = isForm ? 'application/x-www-form-urlencoded' : 'application/json'
  const response = await fetch(base + path, { method, headers, body: body ? (isForm ? body : JSON.stringify(body)) : undefined })
  for (const cookie of response.headers.getSetCookie()) { const pair = cookie.split(';')[0]; const at = pair.indexOf('='); cookies.set(pair.slice(0, at), pair.slice(at + 1)) }
  if (!response.ok) throw new Error(`${path}: ${response.status} ${await response.text()}`)
  const text = await response.text(); return text ? JSON.parse(text) : null
}
await api('/api/auth/login', 'POST', new URLSearchParams(credentials))
const taxonomy = await api('/api/admin/taxonomy')
const categories = {}
for (const [type, name] of [['article', '设计手记'], ['work', '个人项目'], ['tutorial', '建站笔记'], ['website', '开发工具'], ['deal', '免费资源']]) {
  categories[type] = taxonomy.categories.find(c => c.slug === `demo-${type}`) || await api('/api/admin/taxonomy/categories', 'POST', { type, name, slug: `demo-${type}`, sortOrder: 0 })
}
const tags = []
for (const [name, slug] of [['示例内容', 'demo'], ['设计', 'design'], ['编程', 'code'], ['生活', 'life'], ['笔记', 'notes']]) tags.push(taxonomy.tags.find(t => t.slug === slug) || await api('/api/admin/taxonomy/tags', 'POST', { name, slug, sortOrder: 0 }))
const samples = [
  { type: 'article', slug: 'a-quiet-place-for-ideas', title: '给想法一个安静的地方：我的博客设计笔记', summary: '从文字、背景和适当的留白开始，让博客成为一个愿意长久停留的地方。', body: '## 从一篇文章开始\n\n这是一篇示例文章，用来展示博客的阅读排版。正式使用时，可以在后台修改或移入回收站。\n\n很多想法散落在不同的地方：读到一半的书、项目里的小发现，以及走在路上突然想到的一句话。给它们一个不那么匆忙的地方。\n\n> 让风景留在文字背后，让重要的想法有空间被看见。\n\n## 让文字拥有自己的节奏\n\n暖纸色的正文，像一本随手翻开的书。背景里的山脊与湖面安静地留在卡片边缘。\n\n- 标题负责带路。\n- 段落之间留出呼吸的距离。\n- 强调色留给真正重要的入口。\n\n## 把设计变成可以维护的系统\n\n```css\n.glass {\n  background: rgba(250, 247, 235, .66);\n  backdrop-filter: blur(18px);\n}\n```\n\n## 留下一些空白\n\n页面可以慢慢长大，文字始终值得认真对待。' },
  { type: 'article', slug: 'small-notes-on-learning', title: '学习的痕迹，藏在那些小笔记里', summary: '不急着把所有知识整理成系统。先记住今天解决的一个问题，再回头看一看走过的路。' },
  { type: 'article', slug: 'slow-down-and-observe', title: '在忙碌之间，练习停顿', summary: '让注意力回到眼前。一次散步、一本书、一段没有被打断的思考，都是日常里的小小积累。' },
  { type: 'work', slug: 'a-personal-knowledge-garden', title: '知识花园：让零散笔记慢慢连接', summary: '一个关于收集与重新发现的个人项目构想。先整理内容，再让连接自然长出来。', details: { projectStatus: 'planning', techStack: ['Vue', 'TypeScript', 'PostgreSQL'] } },
  { type: 'tutorial', slug: 'build-a-personal-blog', title: '从零搭建自己的博客', summary: '从需求、内容结构和视觉语言开始，一步步走向一个可维护的小站。', details: { difficulty: 'beginner', chapters: [{ chapterKey: crypto.randomUUID(), slug: 'planning', title: '先想清楚，要记录什么', bodyMarkdown: '# 明确内容\n\n示例章节：列出最想分享的三个主题，再决定你的博客结构。' }, { chapterKey: crypto.randomUUID(), slug: 'design', title: '给内容找到合适的样子', bodyMarkdown: '# 建立视觉语言\n\n示例章节：先选定字体、色彩和阅读宽度，再开始组合页面。' }] } },
  { type: 'website', slug: 'mdn-web-docs', title: 'MDN：值得反复打开的 Web 文档', summary: 'HTML、CSS 与 JavaScript 的参考入口。从一个具体问题出发，查阅准确的解释和示例。', details: { websiteUrl: 'https://developer.mozilla.org/zh-CN/' } },
  { type: 'article', slug: 'code-and-everyday-life', title: '代码与生活之间，还有很多值得记录的事', summary: '关于动手做一点东西，也关于认真看见每一天。' }
]
for (const sample of samples) {
  const existing = await api(`/api/admin/contents?q=${encodeURIComponent(sample.title)}&size=50`)
  if (existing.items.some(item => item.slug === sample.slug)) continue
  const content = await api('/api/admin/contents', 'POST', { type: sample.type, slug: sample.slug, title: sample.title, summary: sample.summary, bodyMarkdown: sample.body || `# ${sample.title}\n\n这是一份明确标注的示例内容，用于体验阅读、草稿修改和发布。可以在后台修改或移入回收站。\n\n${sample.summary}`, lockVersion: 0, categoryId: categories[sample.type].id, tagIds: [tags[0].id, tags[1].id], details: sample.details || {} })
  await api(`/api/admin/contents/${content.id}/publish`, 'POST', { lockVersion: content.lockVersion })
}
await api('/api/auth/logout', 'POST')
console.log('Local demonstration content is ready. Every entry is tagged 示例内容. Production is never seeded automatically.')
