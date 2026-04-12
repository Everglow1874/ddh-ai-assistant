import { useEffect, useState, useRef, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Typography, Button, Space, Input, message, Card, List, Modal, Form } from 'antd'
import { SendOutlined, PlusOutlined, SaveOutlined, UnorderedListOutlined, LoadingOutlined } from '@ant-design/icons'
import { getAllTables, type TableMetadata } from '../../api/metadata'
import { createSession, getMessages, getGeneratedSql, sendMessageStream, type ChatSession, type ChatMessage } from '../../api/chat'
import { createJobFromSession } from '../../api/job'
import { SqlPreview } from '../../components/SqlEditor'
import { MarkdownBubble } from '../../components/MarkdownBubble'

const { Title, Text } = Typography
const { TextArea } = Input

// 临时流式消息（AI 正在输出）
interface StreamingMessage {
  role: 'assistant'
  content: string
  streaming: boolean
}

function WorkbenchPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const pid = Number(projectId)

  const [session, setSession] = useState<ChatSession | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [streamingMsg, setStreamingMsg] = useState<StreamingMessage | null>(null)
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [tables, setTables] = useState<TableMetadata[]>([])
  const [generatedSql, setGeneratedSql] = useState<string>('')
  const [saveModalOpen, setSaveModalOpen] = useState(false)
  const [saveForm] = Form.useForm()
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    getAllTables(pid).then(setTables).catch(console.error)
  }, [pid])

  useEffect(() => {
    if (session) {
      getMessages(session.id).then(setMessages).catch(console.error)
    }
  }, [session])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streamingMsg?.content])

  /** 从后端拉取已生成的 SQL */
  const refreshSql = useCallback(async () => {
    if (!session) return
    try {
      const sql = await getGeneratedSql(session.id)
      if (sql) setGeneratedSql(sql)
    } catch {
      // ignore
    }
  }, [session])

  const handleNewSession = async () => {
    // 中止当前流
    abortRef.current?.abort()
    setStreamingMsg(null)
    setGeneratedSql('')
    try {
      const newSession = await createSession(pid, '新会话')
      setSession(newSession)
      setMessages([])
      message.success('已创建新会话')
    } catch {
      message.error('创建会话失败')
    }
  }

  const handleSend = useCallback(async () => {
    if (!input.trim() || !session || loading) return

    const userText = input.trim()
    setInput('')
    setLoading(true)

    // 立即追加用户消息到列表（乐观更新）
    const tempUserMsg: ChatMessage = {
      id: Date.now(),
      sessionId: session.id,
      role: 'user',
      content: userText,
      messageType: 'TEXT',
      createdAt: new Date().toISOString(),
    }
    setMessages(prev => [...prev, tempUserMsg])

    // 初始化流式消息占位
    setStreamingMsg({ role: 'assistant', content: '', streaming: true })

    abortRef.current = sendMessageStream(
      session.id,
      userText,
      // onChunk: 每收到一段内容追加
      (chunk) => {
        setStreamingMsg(prev =>
          prev ? { ...prev, content: prev.content + chunk } : null
        )
      },
      // onDone: 完成，从后端重新拉取完整消息列表和 SQL
      async () => {
        setStreamingMsg(null)
        setLoading(false)
        try {
          const msgs = await getMessages(session.id)
          setMessages(msgs)
        } catch {
          // ignore
        }
        // 每次对话完成后刷新 SQL 预览
        await refreshSql()
      },
      // onError
      (err) => {
        setStreamingMsg(null)
        setLoading(false)
        message.error(`发送失败: ${err}`)
      },
    )
  }, [input, session, loading, refreshSql])

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleSaveAsJob = async () => {
    if (!session?.id) {
      message.warning('请先创建会话')
      return
    }
    setSaveModalOpen(true)
  }

  const handleSaveConfirm = async () => {
    if (!session?.id) return
    try {
      const values = await saveForm.validateFields()
      await createJobFromSession(pid, session.id, values.jobName, values.description)
      message.success('作业保存成功')
      setSaveModalOpen(false)
      saveForm.resetFields()
    } catch {
      message.error('保存失败')
    }
  }

  // 渲染单条消息气泡
  const renderBubble = (role: string, content: string, key: string | number, streaming = false) => (
    <div key={key} style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: role === 'user' ? 'flex-end' : 'flex-start' }}>
        <div
          style={{
            maxWidth: role === 'user' ? '75%' : '85%',
            padding: role === 'user' ? '8px 12px' : '12px 16px',
            borderRadius: 8,
            background: role === 'user' ? '#1677ff' : '#f7f7f8',
            color: role === 'user' ? '#fff' : '#333',
            position: 'relative',
          }}
        >
          {role === 'user' ? (
            <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontFamily: 'inherit', fontSize: 14 }}>
              {content}
            </pre>
          ) : (
            <MarkdownBubble content={content} streaming={streaming} />
          )}
        </div>
      </div>
    </div>
  )

  return (
    <div style={{ display: 'flex', height: '100%', gap: 16 }}>
      {/* 左侧：表浏览器 */}
      <div style={{ width: 220, flexShrink: 0, overflow: 'auto' }}>
        <Card size="small" title="源表列表" extra={<Text type="secondary">{tables.length}张</Text>}>
          <List
            size="small"
            dataSource={tables}
            renderItem={(table: TableMetadata) => (
              <List.Item>
                <div>
                  <div style={{ fontWeight: 500, fontSize: 13 }}>{table.tableName}</div>
                  {table.tableComment && (
                    <Text type="secondary" style={{ fontSize: 11 }}>{table.tableComment}</Text>
                  )}
                </div>
              </List.Item>
            )}
          />
        </Card>
      </div>

      {/* 中间：对话区域 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: '#fff', borderRadius: 6, minWidth: 0 }}>
        {/* 头部 */}
        <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>AI 开发助手</Title>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {session ? `当前阶段：${session.currentState}` : '通过对话开发 ETL 作业'}
            </Text>
          </div>
          <Space>
            <Link to={`/projects/${pid}/jobs`}>
              <Button size="small" icon={<UnorderedListOutlined />}>作业列表</Button>
            </Link>
            <Button size="small" icon={<PlusOutlined />} onClick={handleNewSession}>新建对话</Button>
          </Space>
        </div>

        {/* 消息列表 */}
        <div style={{ flex: 1, overflow: 'auto', padding: '16px 20px' }}>
          {!session ? (
            <div style={{ textAlign: 'center', marginTop: 100 }}>
              <Text type="secondary">点击「新建对话」开始开发 ETL 作业</Text>
            </div>
          ) : messages.length === 0 && !streamingMsg ? (
            <div style={{ textAlign: 'center', marginTop: 100 }}>
              <Text type="secondary">请描述您想要实现的统计需求</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 12 }}>例如：「统计每个部门本月的销售额，按金额从高到低排名」</Text>
            </div>
          ) : (
            <div>
              {messages.map(msg => renderBubble(msg.role, msg.content, msg.id))}
              {/* 流式输出中的 AI 消息 */}
              {streamingMsg && renderBubble('assistant', streamingMsg.content || '...', 'streaming', true)}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* 输入区域 */}
        {session && (
          <div style={{ padding: 12, borderTop: '1px solid #f0f0f0' }}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <TextArea
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder="输入您的需求，按 Enter 发送，Shift+Enter 换行..."
                autoSize={{ minRows: 2, maxRows: 5 }}
                disabled={loading}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Space>
                  <Text type="secondary" style={{ fontSize: 12 }}>Enter 发送 · Shift+Enter 换行</Text>
                  <Button
                    size="small"
                    icon={<SaveOutlined />}
                    onClick={handleSaveAsJob}
                    disabled={messages.length === 0}
                  >
                    保存为作业
                  </Button>
                </Space>
                <Button
                  type="primary"
                  icon={loading ? <LoadingOutlined /> : <SendOutlined />}
                  onClick={handleSend}
                  disabled={!input.trim() || loading}
                >
                  {loading ? 'AI 思考中...' : '发送'}
                </Button>
              </div>
            </Space>
          </div>
        )}
      </div>

      {/* 右侧：SQL 预览 */}
      <div style={{ width: 320, flexShrink: 0, background: '#fff', borderRadius: 6, padding: 12, display: 'flex', flexDirection: 'column' }}>
        <Card size="small" title="SQL 预览" style={{ flex: 1 }}>
          {generatedSql ? (
            <SqlPreview sql={generatedSql} height={400} />
          ) : (
            <div style={{ textAlign: 'center', padding: 40 }}>
              <Text type="secondary">完成 SQL 生成阶段后将在此显示</Text>
            </div>
          )}
        </Card>
      </div>

      {/* 保存作业弹窗 */}
      <Modal
        title="保存为作业"
        open={saveModalOpen}
        onOk={handleSaveConfirm}
        onCancel={() => setSaveModalOpen(false)}
        okText="保存"
        cancelText="取消"
      >
        <Form form={saveForm} layout="vertical">
          <Form.Item name="jobName" label="作业名称" rules={[{ required: true, message: '请输入作业名称' }]}>
            <Input placeholder="请输入作业名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="作业描述（可选）" rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <style>{`
        @keyframes blink {
          0%, 100% { opacity: 1; }
          50% { opacity: 0; }
        }
      `}</style>
    </div>
  )
}

export default WorkbenchPage
