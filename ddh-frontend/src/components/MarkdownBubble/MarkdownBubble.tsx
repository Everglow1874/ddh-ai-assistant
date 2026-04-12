import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'

interface MarkdownBubbleProps {
  content: string
  streaming?: boolean
}

/**
 * 从 AI 响应内容中移除 ```json:context ... ``` 块（仅供系统内部使用的结构化数据）
 */
function stripJsonContext(text: string): string {
  return text.replace(/```json:context\s*\n[\s\S]*?\n```/g, '').trim()
}

/**
 * Markdown 消息气泡组件 - 用于渲染 AI 回复
 * 支持 GFM 表格、代码高亮、列表等
 */
export function MarkdownBubble({ content, streaming = false }: MarkdownBubbleProps) {
  const displayContent = stripJsonContext(content)

  const components: Components = {
    // 代码块渲染
    code({ className, children, ...props }) {
      const match = /language-(\w+)/.exec(className || '')
      const lang = match ? match[1] : ''
      const codeStr = String(children).replace(/\n$/, '')

      // 多行代码块
      if (lang || codeStr.includes('\n')) {
        return (
          <div style={{ position: 'relative', margin: '8px 0' }}>
            {lang && (
              <div style={{
                fontSize: 11,
                color: '#999',
                padding: '2px 8px',
                background: '#f6f6f6',
                borderRadius: '4px 4px 0 0',
                borderBottom: '1px solid #e8e8e8',
              }}>
                {lang.toUpperCase()}
              </div>
            )}
            <pre style={{
              background: '#f6f8fa',
              padding: '12px',
              borderRadius: lang ? '0 0 4px 4px' : '4px',
              overflow: 'auto',
              fontSize: 13,
              lineHeight: 1.5,
              margin: 0,
            }}>
              <code className={className} {...props}>{codeStr}</code>
            </pre>
          </div>
        )
      }

      // 行内代码
      return (
        <code style={{
          background: '#f0f0f0',
          padding: '1px 4px',
          borderRadius: 3,
          fontSize: '0.9em',
        }} {...props}>
          {children}
        </code>
      )
    },
    // 表格样式
    table({ children }) {
      return (
        <div style={{ overflowX: 'auto', margin: '8px 0' }}>
          <table style={{
            borderCollapse: 'collapse',
            width: '100%',
            fontSize: 13,
          }}>
            {children}
          </table>
        </div>
      )
    },
    th({ children }) {
      return (
        <th style={{
          border: '1px solid #d9d9d9',
          padding: '6px 10px',
          background: '#fafafa',
          fontWeight: 600,
          textAlign: 'left',
        }}>
          {children}
        </th>
      )
    },
    td({ children }) {
      return (
        <td style={{
          border: '1px solid #d9d9d9',
          padding: '6px 10px',
        }}>
          {children}
        </td>
      )
    },
    // 标题
    h3({ children }) {
      return <h3 style={{ fontSize: 15, fontWeight: 600, margin: '12px 0 6px' }}>{children}</h3>
    },
    h4({ children }) {
      return <h4 style={{ fontSize: 14, fontWeight: 600, margin: '10px 0 4px' }}>{children}</h4>
    },
    // 段落
    p({ children }) {
      return <p style={{ margin: '6px 0', lineHeight: 1.6 }}>{children}</p>
    },
    // 列表
    ul({ children }) {
      return <ul style={{ margin: '4px 0', paddingLeft: 20 }}>{children}</ul>
    },
    ol({ children }) {
      return <ol style={{ margin: '4px 0', paddingLeft: 20 }}>{children}</ol>
    },
    li({ children }) {
      return <li style={{ margin: '2px 0', lineHeight: 1.5 }}>{children}</li>
    },
    // 分割线
    hr() {
      return <hr style={{ border: 'none', borderTop: '1px solid #e8e8e8', margin: '12px 0' }} />
    },
  }

  return (
    <div style={{ fontSize: 14, lineHeight: 1.6, wordBreak: 'break-word' }}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {displayContent}
      </ReactMarkdown>
      {streaming && (
        <span style={{
          display: 'inline-block',
          width: 2,
          height: '1em',
          background: '#666',
          marginLeft: 2,
          animation: 'blink 1s step-end infinite',
          verticalAlign: 'text-bottom',
        }} />
      )}
    </div>
  )
}
