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
export function renderMarkdown(source: string, preview = false) {
  const tokens = markdown.parse(source, {})
  const headings: { id: string; text: string; level: number }[] = []
  let count = 0
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]!
    if ((token.type === 'heading_open' || token.type === 'heading_close') && token.tag === 'h1') token.tag = 'h2'
    if (token.type === 'heading_open') {
      const id = `section-${++count}`
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
