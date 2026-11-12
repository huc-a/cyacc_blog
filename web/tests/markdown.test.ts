import { describe, it, expect } from 'vitest'
import { renderMarkdown } from '../app/utils/markdown'
describe('shared safe Markdown renderer', () => {
  it('treats raw HTML and script as text', () => {
    const { html } = renderMarkdown('<script>alert(1)</script>\n<img src=x onerror=alert(1)>')
    expect(html).not.toContain('<script>'); expect(html).not.toContain('<img src=x')
    expect(html).toContain('&lt;script&gt;')
  })
  it('rejects unsafe and protocol relative links in reader and preview', () => {
    for (const preview of [false, true]) {
      const { html } = renderMarkdown('[bad](javascript:alert(1))\n![x](data:image/svg+xml,test)\n[x](//evil.example)', preview)
      expect(html).not.toContain('href="javascript:'); expect(html).not.toContain('src="data:'); expect(html).not.toContain('href="//')
    }
  })
  it('builds stable headings and changes only media paths in admin preview', () => {
    const text = '# 开始\n## 继续\n![图](/api/public/media/12)\n[文档](https://example.com)'
    expect(renderMarkdown(text).headings.map(h => h.id)).toEqual(['开始', '继续'])
    expect(renderMarkdown('## 重复\n## 重复').headings.map(h => h.id)).toEqual(['重复', '重复-2'])
    expect(renderMarkdown(text).html).not.toContain('<h1')
    expect(renderMarkdown(text).html).toContain('src="/api/public/media/12"')
    expect(renderMarkdown(text, true).html).toContain('src="/api/admin/media/12/file"')
    expect(renderMarkdown(text).html).toContain('rel="noopener noreferrer"')
  })
})
