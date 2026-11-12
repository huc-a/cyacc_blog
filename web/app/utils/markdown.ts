import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({ html: false, linkify: true, typographer: true })
markdown.validateLink = (url: string) => /^(https?:|mailto:|\/|#)/i.test(url) && !url.startsWith('//') && !url.startsWith('/\\') && !/[\u0000-\u001f]/.test(url)
const baseLink = markdown.renderer.rules.link_open || ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
const baseFence = markdown.renderer.rules.fence!
markdown.renderer.rules.fence = (tokens, idx, options, env, self) => `<div class="code-block"><button type="button" data-copy-code aria-label="复制代码">复制</button>${baseFence(tokens, idx, options, env, self)}</div>`
markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx]!.attrSet('rel', 'noopener noreferrer')
  return baseLink(tokens, idx, options, env, self)
}
// Heading anchors are derived from the heading text (not their position),
// so inserting or removing a section does not break existing #anchor links.
// Repeated headings get a numeric suffix.
function headingId(text: string, seen: Map<string, number>) {
  const base = text.toLowerCase().trim().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '') || 'section'
  const n = (seen.get(base) || 0) + 1
  seen.set(base, n)
  return n === 1 ? base : `${base}-${n}`
}
export function renderMarkdown(source: string, preview = false) {
  const tokens = markdown.parse(source, {})
  const headings: { id: string; text: string; level: number }[] = []
  const seen = new Map<string, number>()
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]!
    if ((token.type === 'heading_open' || token.type === 'heading_close') && token.tag === 'h1') token.tag = 'h2'
    if (token.type === 'heading_open') {
      const id = headingId(tokens[i + 1]?.content || '', seen)
      token.attrSet('id', id)
      headings.push({ id, text: tokens[i + 1]?.content || '', level: Number(token.tag.slice(1)) })
    }
    if (preview) for (const child of token.children || []) {
      if (child.type === 'image') {
        const src = String(child.attrGet('src') || '')
        if (/^\/api\/public\/media\/\d+$/.test(src)) child.attrSet('src', src.replace('/public/', '/admin/') + '/file')
      }
    }
  }
  return { html: markdown.renderer.render(tokens, markdown.options, {}), headings }
}
