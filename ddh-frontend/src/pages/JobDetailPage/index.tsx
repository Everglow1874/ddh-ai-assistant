import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Card, Button, Space, Typography, Tag, message, Tabs } from 'antd'
import { ExportOutlined, ArrowLeftOutlined, EditOutlined, SaveOutlined, CheckOutlined } from '@ant-design/icons'
import { getJob, getJobSteps, updateJobStep, type EtlJob, type EtlJobStep } from '../../api/job'
import { SqlEditor, SqlPreview } from '../../components/SqlEditor'

const { Title, Text, Paragraph } = Typography

const STEP_TYPE_COLOR: Record<string, string> = {
  EXTRACT: 'blue',
  TRANSFORM: 'orange',
  LOAD: 'green',
  FULL: 'purple',
  UNKNOWN: 'default',
}

function JobDetailPage() {
  const { projectId, jobId } = useParams<{ projectId: string; jobId: string }>()
  const pid = projectId ? Number(projectId) : 0
  const jid = jobId ? Number(jobId) : 0

  const [job, setJob] = useState<EtlJob | null>(null)
  const [steps, setSteps] = useState<EtlJobStep[]>([])
  const [selectedStep, setSelectedStep] = useState<EtlJobStep | null>(null)
  const [editingDdl, setEditingDdl] = useState(false)
  const [editingDml, setEditingDml] = useState(false)
  const [draftDdl, setDraftDdl] = useState('')
  const [draftDml, setDraftDml] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!jid) return
    loadData()
  }, [jid])

  useEffect(() => {
    if (selectedStep) {
      setDraftDdl(selectedStep.ddlSql || '')
      setDraftDml(selectedStep.dmlSql || '')
      setEditingDdl(false)
      setEditingDml(false)
    }
  }, [selectedStep?.id])

  const loadData = async () => {
    if (!jid) return
    try {
      const [jobData, stepsData] = await Promise.all([getJob(jid), getJobSteps(jid)])
      setJob(jobData)
      setSteps(stepsData)
      if (stepsData.length > 0 && !selectedStep) {
        setSelectedStep(stepsData[0])
      }
    } catch {
      message.error('加载作业详情失败')
    }
  }

  const handleExport = async () => {
    if (!jid) return
    try {
      const response = await fetch(`/api/jobs/${jid}/export`, { credentials: 'include' })
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${job?.jobName}.zip`
      a.click()
      window.URL.revokeObjectURL(url)
      message.success('导出成功')
    } catch {
      message.error('导出失败')
    }
  }

  const handleSaveStep = async () => {
    if (!selectedStep) return
    setSaving(true)
    try {
      const updated = await updateJobStep(selectedStep.jobId, selectedStep.id, {
        ...selectedStep,
        ddlSql: draftDdl || undefined,
        dmlSql: draftDml || undefined,
      })
      setSteps(prev => prev.map(s => s.id === updated.id ? updated : s))
      setSelectedStep(updated)
      setEditingDdl(false)
      setEditingDml(false)
      message.success('保存成功')
    } catch {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const statusColor = (status: string) => {
    if (status === 'COMPLETED') return 'green'
    if (status === 'DRAFT') return 'orange'
    return 'default'
  }

  const hasUnsaved = selectedStep && (draftDdl !== (selectedStep.ddlSql || '') || draftDml !== (selectedStep.dmlSql || ''))

  return (
    <div style={{ padding: 24 }}>
      {/* 头部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Space>
          <Link to={`/projects/${pid}/jobs`}>
            <Button icon={<ArrowLeftOutlined />}>返回</Button>
          </Link>
          <Title level={3} style={{ margin: 0 }}>{job?.jobName}</Title>
          {job && <Tag color={statusColor(job.status)}>{job.status}</Tag>}
        </Space>
        <Space>
          {hasUnsaved && (
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSaveStep}
              loading={saving}
            >
              保存修改
            </Button>
          )}
          <Button icon={<ExportOutlined />} onClick={handleExport}>
            导出 ZIP
          </Button>
        </Space>
      </div>

      {job?.description && (
        <Paragraph type="secondary" style={{ marginBottom: 16 }}>{job.description}</Paragraph>
      )}

      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        {/* 左侧：步骤列表 */}
        <div style={{ width: 220, flexShrink: 0 }}>
          <Card size="small" title="执行步骤" bodyStyle={{ padding: 0 }}>
            {steps.length === 0 ? (
              <div style={{ padding: 16, textAlign: 'center' }}>
                <Text type="secondary" style={{ fontSize: 12 }}>暂无步骤</Text>
              </div>
            ) : (
              steps.map((step, idx) => (
                <div
                  key={step.id}
                  onClick={() => setSelectedStep(step)}
                  style={{
                    padding: '10px 12px',
                    cursor: 'pointer',
                    borderBottom: '1px solid #f0f0f0',
                    background: selectedStep?.id === step.id ? '#f0f5ff' : 'transparent',
                    borderLeft: selectedStep?.id === step.id ? '3px solid #1677ff' : '3px solid transparent',
                    transition: 'all 0.15s',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                    <Text style={{ fontSize: 11, color: '#999' }}>步骤 {idx + 1}</Text>
                    <Tag
                      color={STEP_TYPE_COLOR[step.stepType] || 'default'}
                      style={{ fontSize: 10, padding: '0 4px', lineHeight: '16px', margin: 0 }}
                    >
                      {step.stepType}
                    </Tag>
                  </div>
                  <div style={{ fontSize: 13, fontWeight: selectedStep?.id === step.id ? 600 : 400, color: '#1a1a1a' }}>
                    {step.stepName}
                  </div>
                  {step.targetTable && (
                    <Text type="secondary" style={{ fontSize: 11 }}>→ {step.targetTable}</Text>
                  )}
                </div>
              ))
            )}
          </Card>
        </div>

        {/* 右侧：步骤详情 */}
        <div style={{ flex: 1, minWidth: 0 }}>
          {selectedStep ? (
            <Card
              size="small"
              title={
                <Space>
                  <Text strong>步骤 {steps.findIndex(s => s.id === selectedStep.id) + 1}：{selectedStep.stepName}</Text>
                  <Tag color={STEP_TYPE_COLOR[selectedStep.stepType] || 'default'}>{selectedStep.stepType}</Tag>
                  {hasUnsaved && <Tag color="orange">有未保存修改</Tag>}
                </Space>
              }
            >
              {/* 基本信息 */}
              {(selectedStep.description || selectedStep.targetTable) && (
                <div style={{ marginBottom: 12, display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                  {selectedStep.description && (
                    <div><Text type="secondary">描述：</Text><Text>{selectedStep.description}</Text></div>
                  )}
                  {selectedStep.targetTable && (
                    <div><Text type="secondary">目标表：</Text><Text code>{selectedStep.targetTable}</Text></div>
                  )}
                </div>
              )}

              <Tabs
                size="small"
                items={[
                  {
                    key: 'ddl',
                    label: 'DDL（建表语句）',
                    children: (
                      <div>
                        <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'flex-end' }}>
                          <Button
                            size="small"
                            icon={editingDdl ? <CheckOutlined /> : <EditOutlined />}
                            onClick={() => setEditingDdl(!editingDdl)}
                            type={editingDdl ? 'primary' : 'default'}
                          >
                            {editingDdl ? '预览' : '编辑'}
                          </Button>
                        </div>
                        {editingDdl ? (
                          <SqlEditor
                            value={draftDdl}
                            onChange={v => setDraftDdl(v || '')}
                            height={280}
                          />
                        ) : (
                          draftDdl ? (
                            <SqlPreview sql={draftDdl} height={280} />
                          ) : (
                            <div style={{ padding: 20, textAlign: 'center', background: '#fafafa', borderRadius: 4 }}>
                              <Text type="secondary">暂无 DDL 语句</Text>
                            </div>
                          )
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'dml',
                    label: 'DML（数据操作）',
                    children: (
                      <div>
                        <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'flex-end' }}>
                          <Button
                            size="small"
                            icon={editingDml ? <CheckOutlined /> : <EditOutlined />}
                            onClick={() => setEditingDml(!editingDml)}
                            type={editingDml ? 'primary' : 'default'}
                          >
                            {editingDml ? '预览' : '编辑'}
                          </Button>
                        </div>
                        {editingDml ? (
                          <SqlEditor
                            value={draftDml}
                            onChange={v => setDraftDml(v || '')}
                            height={280}
                          />
                        ) : (
                          draftDml ? (
                            <SqlPreview sql={draftDml} height={280} />
                          ) : (
                            <div style={{ padding: 20, textAlign: 'center', background: '#fafafa', borderRadius: 4 }}>
                              <Text type="secondary">暂无 DML 语句</Text>
                            </div>
                          )
                        )}
                      </div>
                    ),
                  },
                ]}
              />

              {hasUnsaved && (
                <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                  <Space>
                    <Button onClick={() => {
                      setDraftDdl(selectedStep.ddlSql || '')
                      setDraftDml(selectedStep.dmlSql || '')
                      setEditingDdl(false)
                      setEditingDml(false)
                    }}>
                      撤销修改
                    </Button>
                    <Button type="primary" icon={<SaveOutlined />} onClick={handleSaveStep} loading={saving}>
                      保存此步骤
                    </Button>
                  </Space>
                </div>
              )}
            </Card>
          ) : (
            <Card>
              <div style={{ textAlign: 'center', padding: 40 }}>
                <Text type="secondary">请在左侧选择步骤查看详情</Text>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}

export default JobDetailPage
