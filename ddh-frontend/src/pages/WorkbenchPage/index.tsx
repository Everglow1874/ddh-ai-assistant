import { useEffect, useState, useRef, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Typography, Button, Space, Input, message, Modal, Form, Tag, Tooltip, Badge, Drawer } from 'antd'
import {
  SendOutlined, PlusOutlined, SaveOutlined, UnorderedListOutlined,
  LoadingOutlined, TableOutlined, KeyOutlined, CheckCircleFilled,
  HistoryOutlined, CopyOutlined
} from '@ant-design/icons'
import { getAllTables, getTableDetail, type TableMetadata, type ColumnMetadata } from '../../api/metadata'
import { createSession, getSessions, getMessages, getGeneratedSql, sendMessageStream, type ChatSession, type ChatMessage } from '../../api/chat'
import { createJobFromSession } from '../../api/job'
import { SqlPreview } from '../../components/SqlEditor'
import { MarkdownBubble } from '../../components/MarkdownBubble'

const { Title, Text } = Typography
const { TextArea } = Input

// 对话阶段定义
const STAGES = [
  { key: 'INIT', label: '需求分析', short: '需求' },
  { key: 'TABLE_RECOMMENDATION', label: '源表推荐', short: '推表' },
  { key: 'STEP_DESIGN', label: '步骤设计', short: '设计' },
  { key: 'SQL_GENERATION', label: 'SQL 生成', short: 'SQL' },
  { key: 'SQL_REVIEW', label: 'SQL 审查', short: '审查' },
  { key: 'DONE', label: '完成', short: '完成' },
]

const STAGE_HINT: Record<string, string> = {
  INIT: '请描述您的数据统计需求，AI 将帮您分析目标指标、维度和过滤条件',
  TABLE_RECOMMENDATION: '确认 AI 推荐的源表是否正确，可补充说明或要求调整',
  STEP_DESIGN: '确认 ETL 作业的执行步骤设计，可要求增减步骤',
  SQL_GENERATION: 'AI 正在生成 GaussDB ETL SQL，完成后可在右侧预览',
  SQL_REVIEW: 'AI 正在审查 SQL 质量，您可以说明特殊要求',
  DONE: 'SQL 已生成完毕，可点击「保存为作业」保存到作业库',
}

interface StreamingMessage {
  role: 'assistant'
  content: string
  streaming: boolean
}

// 表浏览器节点（支持展开字段）
function TableBrowser({
  tables, pid
}: { tables: TableMetadata[], pid: number }) {
  const [expandedTable, setExpandedTable] = useState<number | null>(null)
  const [columnsMap, setColumnsMap] = useState<Record<number, ColumnMetadata[]>>({})
  const [loadingTable, setLoadingTable] = useState<number | null>(null)

  const handleExpand = async (table: TableMetadata) => {
    if (expandedTable === table.id) {
      setExpandedTable(null)
      return
    }
    setExpandedTable(table.id)
    if (!columnsMap[table.id]) {
      setLoadingTable(table.id)
      try {
        const detail = await getTableDetail(pid, table.id)
        setColumnsMap(prev => ({ ...prev, [table.id]: detail.columns }))
      } catch {
        message.error('获取字段失败')
      } finally {
        setLoadingTable(null)
      }
    }
  }

  return (
    <div style={{ overflowY: 'auto', flex: 1 }}>
      {tables.map(table => (
        <div key={table.id} style={{ borderBottom: '1px solid #f0f0f0' }}>
          <div
            onClick={() => handleExpand(table)}
            style={{
              padding: '8px 10px',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 6,
              background: expandedTable === table.id ? '#f0f5ff' : 'transparent',
              transition: 'background 0.15s',
            }}
          >
            <TableOutlined style={{ fontSize: 12, color: '#1677ff', marginTop: 2, flexShrink: 0 }} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 12, fontWeight: 500, color: '#1a1a1a', lineHeight: 1.4, wordBreak: 'break-all' }}>
                {table.tableName}
              </div>
              {table.tableComment && (
                <div style={{ fontSize: 11, color: '#999', lineHeight: 1.3, marginTop: 2 }}>
                  {table.tableComment}
                </div>
              )}
            </div>
          </div>

          {expandedTable === table.id && (
            <div style={{ background: '#fafafa', borderTop: '1px solid #f0f0f0' }}>
              {loadingTable === table.id ? (
                <div style={{ padding: '6px 10px', fontSize: 11, color: '#999' }}>加载中…</div>
              ) : (columnsMap[table.id] || []).map(col => (
                <div
                  key={col.id}
                  style={{
                    padding: '4px 10px 4px 26px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    fontSize: 11,
                  }}
                >
                  {col.isPrimaryKey === 1 && (
                    <KeyOutlined style={{ fontSize: 10, color: '#faad14', flexShrink: 0 }} />
                  )}
                  <span style={{ color: '#333', fontWeight: col.isPrimaryKey === 1 ? 600 : 400 }}>
                    {col.columnName}
                  </span>
                  <span style={{ color: '#aaa', fontFamily: 'monospace', fontSize: 10 }}>
                    {col.columnType}
                  </span>
                  {col.columnComment && (
                    <Tooltip title={col.columnComment}>
                      <span style={{ color: '#bbb', maxWidth: 60, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {col.columnComment}
                      </span>
                    </Tooltip>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

// 阶段进度指示器
function StageProgress({ currentState }: { currentState: string }) {
  const currentIdx = STAGES.findIndex(s => s.key === currentState)

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 0, padding: '8px 0' }}>
      {STAGES.map((stage, idx) => {
        const done = idx < currentIdx
        const active = idx === currentIdx
        return (
          <div key={stage.key} style={{ display: 'flex', alignItems: 'center', flex: idx < STAGES.length - 1 ? 1 : 'none' }}>
            <Tooltip title={stage.label}>
              <div style={{
                width: 26, height: 26, borderRadius: '50%',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 11, fontWeight: active ? 600 : 400,
                background: done ? '#52c41a' : active ? '#1677ff' : '#f0f0f0',
                color: done || active ? '#fff' : '#999',
                border: active ? '2px solid #1677ff' : 'none',
                flexShrink: 0,
                transition: 'all 0.3s',
              }}>
                {done ? <CheckCircleFilled style={{ fontSize: 12 }} /> : idx + 1}
              </div>
            </Tooltip>
            {idx < STAGES.length - 1 && (
              <div style={{
                flex: 1, height: 2,
                background: done ? '#52c41a' : '#f0f0f0',
                transition: 'background 0.3s',
                minWidth: 4,
              }} />
            )}
          </div>
        )
      })}
    </div>
  )
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
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false)
  const [sessionList, setSessionList] = useState<ChatSession[]>([])
  const [thinkingMap, setThinkingMap] = useState<Record<number, string>>({})
  const [saveForm] = Form.useForm()
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  const fetchSessionList = useCallback(async () => {
    try {
      const page = await getSessions(pid, 1, 50)
      setSessionList(page?.records || [])
    } catch { /* ignore */ }
  }, [pid])

  useEffect(() => {
    let mounted = true
    getAllTables(pid).then(data => {
      if(mounted) setTables(data)
    }).catch(console.error)

    getSessions(pid, 1, 50).then(page => {
      if(mounted) setSessionList(page?.records || [])
    }).catch(() => {})

    return () => { mounted = false }
  }, [pid])

  const handleSwitchSession = async (s: ChatSession) => {
    abortRef.current?.abort()
    setStreamingMsg(null)
    setGeneratedSql('')
    setThinkingMap({})
    setSession(s)
    setHistoryDrawerOpen(false)
    try {
      const msgs = await getMessages(s.id)
      setMessages(msgs)
      const sql = await getGeneratedSql(s.id)
      if (sql) setGeneratedSql(sql)
    } catch { /* ignore */ }
  }

  useEffect(() => {
    if (session) {
      getMessages(session.id).then(setMessages).catch(console.error)
    }
  }, [session])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streamingMsg?.content])

  const refreshSql = useCallback(async () => {
    if (!session) return
    try {
      const sql = await getGeneratedSql(session.id)
      if (sql) setGeneratedSql(sql)
    } catch { /* ignore */ }
  }, [session])

  const handleNewSession = async () => {
    abortRef.current?.abort()
    setStreamingMsg(null)
    setGeneratedSql('')
    setThinkingMap({})
    try {
      const newSession = await createSession(pid, '新会话')
      setSession(newSession)
      setMessages([])
      message.success('已创建新会话')
      fetchSessionList()
    } catch {
      message.error('创建会话失败')
    }
  }

  const handleSend = useCallback(async () => {
    if (!input.trim() || !session || loading) return

    const userText = input.trim()
    setInput('')
    setLoading(true)

    // 追加用户消息
    const userMsg: ChatMessage = { id: Date.now(), sessionId: session.id, role: 'user', content: userText }
    setMessages(prev => [...prev, userMsg])

    // 占位 assistant 消息
    const astMsgId = Date.now() + 1
    const astMsg: ChatMessage = { id: astMsgId, sessionId: session.id, role: 'assistant', content: '' }
    setMessages(prev => [...prev, astMsg])

    // 滚动到底部
    setTimeout(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }) }, 100)

    // 发起 SSE 流式请求
    abortRef.current = sendMessageStream(
      session.id,
      userText,
      (text) => {
        setMessages(prev => prev.map(m => m.id === astMsgId ? { ...m, content: m.content + text } : m))
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
      },
      (text) => {
        setThinkingMap(prev => ({ ...prev, [astMsgId]: (prev[astMsgId] || '') + text }))
      },
      () => {
        setLoading(false)
        fetchSessionList() // 重新获取状态
        refreshSql()
      },
      (err) => {
        setLoading(false)
        message.error(`请求失败: ${err}`)
      }
    )
  }, [input, session, loading, refreshSql, pid, fetchSessionList])

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() }
  }

  const handleSaveConfirm = async () => {
    if (!session?.id) return
    try {
      const values = await saveForm.validateFields()
      await createJobFromSession(pid, session.id, values.jobName, values.description)
      message.success('作业保存成功，可在作业列表查看步骤详情')
      setSaveModalOpen(false)
      saveForm.resetFields()
    } catch {
      message.error('保存失败')
    }
  }

  const renderBubble = (role: string, content: string, key: string | number, streaming = false, thinking?: string) => (
    <div key={key} style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: role === 'user' ? 'flex-end' : 'flex-start' }}>
        <div style={{
          maxWidth: role === 'user' ? '75%' : '88%',
          padding: role === 'user' ? '8px 12px' : '12px 16px',
          borderRadius: 8,
          background: role === 'user' ? '#1677ff' : '#f7f7f8',
          color: role === 'user' ? '#fff' : '#333',
        }}>
          {role === 'user' ? (
            <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontFamily: 'inherit', fontSize: 14 }}>{content}</pre>
          ) : (
            <MarkdownBubble content={content} thinking={thinking} streaming={streaming} />
          )}
        </div>
      </div>
    </div>
  )

  const currentState = session?.currentState || 'INIT'
  const isDone = currentState === 'DONE'
  const stageHint = session ? STAGE_HINT[currentState] : '点击「新建对话」开始开发 ETL 作业'

  return (
    <div style={{ display: 'flex', height: '100%', gap: 12 }}>
      {/* 左侧：表浏览器 */}
      <div style={{ width: 220, flexShrink: 0, display: 'flex', flexDirection: 'column', background: '#fff', borderRadius: 6, overflow: 'hidden', border: '1px solid #f0f0f0' }}>
        <div style={{ padding: '10px 12px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text strong style={{ fontSize: 13 }}>源表列表</Text>
          <Badge count={tables.length} color="#1677ff" style={{ fontSize: 10 }} />
        </div>
        <TableBrowser tables={tables} pid={pid} />
      </div>

      {/* 中间：对话区域 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: '#fff', borderRadius: 6, minWidth: 0, border: '1px solid #f0f0f0', overflow: 'hidden' }}>
        {/* 头部 */}
        <div style={{ padding: '10px 16px', borderBottom: '1px solid #f0f0f0' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
            <Title level={5} style={{ margin: 0 }}>AI 开发助手</Title>
            <Space>
              <Button size="small" icon={<HistoryOutlined />} onClick={() => { fetchSessionList(); setHistoryDrawerOpen(true) }}>
                历史会话
              </Button>
              <Link to={`/projects/${pid}/jobs`}>
                <Button size="small" icon={<UnorderedListOutlined />}>作业列表</Button>
              </Link>
              <Button size="small" type="primary" icon={<PlusOutlined />} onClick={handleNewSession}>新建对话</Button>
            </Space>
          </div>
          {session && <StageProgress currentState={currentState} />}
          <Text type="secondary" style={{ fontSize: 11 }}>{stageHint}</Text>
        </div>

        {/* 消息列表 */}
        <div style={{ flex: 1, overflow: 'auto', padding: '16px 20px' }}>
          {!session ? (
            <div style={{ textAlign: 'center', marginTop: 80 }}>
              <Text type="secondary" style={{ fontSize: 15 }}>点击「新建对话」开始开发 ETL 作业</Text>
            </div>
          ) : messages.length === 0 && !streamingMsg ? (
            <div style={{ textAlign: 'center', marginTop: 80 }}>
              <Text type="secondary">请描述您想要实现的统计需求</Text><br />
              <Text type="secondary" style={{ fontSize: 12 }}>例如：「统计每个部门本月的销售额，按金额从高到低排名」</Text>
            </div>
          ) : (
            <div>
              {messages.map(msg => renderBubble(msg.role, msg.content, msg.id, false, thinkingMap[msg.id]))}
              {streamingMsg && renderBubble('assistant', streamingMsg.content || '▋', 'streaming', true, thinkingMap['streaming'])}
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
                placeholder={isDone ? '作业已完成，可继续追问或修改 SQL…' : '输入消息，按 Enter 发送，Shift+Enter 换行…'}
                autoSize={{ minRows: 2, maxRows: 5 }}
                disabled={loading}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Space>
                  {isDone && (
                    <Button
                      size="small"
                      type="primary"
                      ghost
                      icon={<SaveOutlined />}
                      onClick={() => setSaveModalOpen(true)}
                    >
                      保存为作业
                    </Button>
                  )}
                  {!isDone && messages.length > 0 && (
                    <Tag color="blue">{STAGES.find(s => s.key === currentState)?.label}</Tag>
                  )}
                </Space>
                <Button
                  type="primary"
                  icon={loading ? <LoadingOutlined /> : <SendOutlined />}
                  onClick={handleSend}
                  disabled={!input.trim() || loading}
                >
                  {loading ? 'AI 思考中…' : '发送'}
                </Button>
              </div>
            </Space>
          </div>
        )}
      </div>

      {/* 右侧：SQL 预览 */}
      <div style={{ width: 300, flexShrink: 0, display: 'flex', flexDirection: 'column', background: '#fff', borderRadius: 6, border: '1px solid #f0f0f0', overflow: 'hidden' }}>
        <div style={{ padding: '10px 12px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text strong style={{ fontSize: 13 }}>SQL 预览</Text>
          <Space size={4}>
            {generatedSql && <Tag color="green" style={{ fontSize: 10 }}>已生成</Tag>}
            {generatedSql && (
              <Tooltip title="复制 SQL">
                <Button
                  size="small"
                  type="text"
                  icon={<CopyOutlined />}
                  onClick={() => {
                    navigator.clipboard.writeText(generatedSql)
                      .then(() => message.success('SQL 已复制'))
                  }}
                />
              </Tooltip>
            )}
          </Space>
        </div>
        <div style={{ flex: 1, overflow: 'hidden' }}>
          {generatedSql ? (
            <SqlPreview sql={generatedSql} height="100%" />
          ) : (
            <div style={{ textAlign: 'center', paddingTop: 60 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>完成 SQL 生成阶段后显示</Text>
            </div>
          )}
        </div>
      </div>

      {/* 历史会话抽屉 */}
      <Drawer
        title="历史会话"
        placement="right"
        width={320}
        open={historyDrawerOpen}
        onClose={() => setHistoryDrawerOpen(false)}
      >
        {sessionList.length === 0 ? (
          <Text type="secondary">暂无历史会话</Text>
        ) : (
          sessionList.map(s => (
            <div
              key={s.id}
              onClick={() => handleSwitchSession(s)}
              style={{
                padding: '10px 12px',
                borderRadius: 6,
                cursor: 'pointer',
                background: session?.id === s.id ? '#e6f4ff' : 'transparent',
                border: session?.id === s.id ? '1px solid #91caff' : '1px solid transparent',
                marginBottom: 8,
                transition: 'all 0.15s',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Text strong style={{ fontSize: 13 }}>{s.sessionName || `会话 ${s.id}`}</Text>
                <Tag
                  color={s.currentState === 'DONE' ? 'green' : 'blue'}
                  style={{ fontSize: 10 }}
                >
                  {STAGES.find(st => st.key === s.currentState)?.label || s.currentState}
                </Tag>
              </div>
              <Text type="secondary" style={{ fontSize: 11 }}>
                {s.updatedAt ? new Date(s.updatedAt).toLocaleString() : ''}
              </Text>
            </div>
          ))
        )}
      </Drawer>

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
    </div>
  )
}

export default WorkbenchPage
