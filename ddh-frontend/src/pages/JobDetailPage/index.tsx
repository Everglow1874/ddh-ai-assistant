import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Card, Button, Space, Typography, Steps, Tag, message, Divider } from 'antd'
import { ExportOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { getJob, getJobSteps, type EtlJob, type EtlJobStep } from '../../api/job'
import { SqlPreview } from '../../components/SqlEditor'

const { Title, Text, Paragraph } = Typography

function JobDetailPage() {
  const { projectId, jobId } = useParams<{ projectId: string; jobId: string }>()

  const pid = projectId ? Number(projectId) : 0
  const jid = jobId ? Number(jobId) : 0

  const [job, setJob] = useState<EtlJob | null>(null)
  const [steps, setSteps] = useState<EtlJobStep[]>([])
  const [selectedStep, setSelectedStep] = useState<EtlJobStep | null>(null)

  useEffect(() => {
    if (!jid) return
    loadData()
  }, [jid])

  const loadData = async () => {
    if (!jid) return
    try {
      const [jobData, stepsData] = await Promise.all([
        getJob(jid),
        getJobSteps(jid),
      ])
      setJob(jobData)
      setSteps(stepsData)
      if (stepsData.length > 0) {
        setSelectedStep(stepsData[0])
      }
    } catch (e) {
      message.error('加载作业详情失败')
    }
  }

  const handleExport = async () => {
    if (!jid) return
    try {
      const response = await fetch(`/api/jobs/${jid}/export`, {
        credentials: 'include',
      })
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${job?.jobName}.zip`
      a.click()
      window.URL.revokeObjectURL(url)
      message.success('导出成功')
    } catch (e) {
      message.error('导出失败')
    }
  }

  const statusColor = (status: string) => {
    if (status === 'COMPLETED') return 'green'
    if (status === 'DRAFT') return 'orange'
    return 'default'
  }

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Space>
          <Link to={`/projects/${pid}/jobs`}>
            <Button icon={<ArrowLeftOutlined />}>返回</Button>
          </Link>
          <Title level={3} style={{ margin: 0 }}>{job?.jobName}</Title>
        </Space>
        <Space>
          <Tag color={statusColor(job?.status || '')}>{job?.status}</Tag>
          <Button type="primary" icon={<ExportOutlined />} onClick={handleExport}>
            导出作业
          </Button>
        </Space>
      </div>

      {job?.description && (
        <Paragraph type="secondary">{job.description}</Paragraph>
      )}

      <Divider />

      <Card title="执行步骤">
        <Steps
          current={selectedStep ? steps.findIndex(s => s.id === selectedStep.id) : 0}
          items={steps.map((step) => ({
            title: step.stepName,
            description: step.stepType,
          }))}
        />
      </Card>

      {selectedStep && (
        <Card title={`步骤 ${selectedStep.stepOrder}: ${selectedStep.stepName}`} style={{ marginTop: 16 }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <div>
              <Text type="secondary">类型：</Text>
              <Tag>{selectedStep.stepType}</Tag>
            </div>
            {selectedStep.description && (
              <div>
                <Text type="secondary">描述：</Text>
                <Text>{selectedStep.description}</Text>
              </div>
            )}
            {selectedStep.targetTable && (
              <div>
                <Text type="secondary">目标表：</Text>
                <Text code>{selectedStep.targetTable}</Text>
              </div>
            )}

            {selectedStep.ddlSql && (
              <div>
                <Text type="secondary">DDL：</Text>
                <SqlPreview sql={selectedStep.ddlSql} height={150} />
              </div>
            )}

            {selectedStep.dmlSql && (
              <div>
                <Text type="secondary">DML：</Text>
                <SqlPreview sql={selectedStep.dmlSql} height={200} />
              </div>
            )}
          </Space>
        </Card>
      )}

      <Divider />

      <Card title="SQL 预览">
        {steps.length > 0 ? (
          <SqlPreview
            sql={[...steps.map(s => s.ddlSql || ''), ...steps.map(s => s.dmlSql || '')].join('\n\n')}
            height={300}
          />
        ) : (
          <Text type="secondary">暂无 SQL 内容</Text>
        )}
      </Card>
    </div>
  )
}

export default JobDetailPage