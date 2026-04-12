import request from './request';

export interface ChatSession {
  id: number;
  projectId: number;
  sessionName: string;
  currentState: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  sessionId: number;
  role: string;
  content: string;
  messageType: string;
  createdAt: string;
}

/** 创建对话会话 */
export function createSession(projectId: number, sessionName?: string): Promise<ChatSession> {
  return request.post(`/projects/${projectId}/chat/sessions`, { sessionName });
}

/** 获取对话列表 */
export function getSessions(projectId: number, current = 1, size = 20): Promise<any> {
  return request.get(`/projects/${projectId}/chat/sessions`, { params: { current, size } });
}

/** 获取对话消息 */
export function getMessages(sessionId: number): Promise<ChatMessage[]> {
  return request.get(`/chat/sessions/${sessionId}/messages`);
}

/** 删除会话 */
export function deleteSession(sessionId: number): Promise<void> {
  return request.delete(`/chat/sessions/${sessionId}`);
}

/** 获取会话中已生成的 SQL */
export function getGeneratedSql(sessionId: number): Promise<string | null> {
  return request.get(`/chat/sessions/${sessionId}/sql`);
}

/**
 * 流式发送消息，通过 fetch + ReadableStream 实现 SSE
 * @param sessionId 会话ID
 * @param userMessage 用户消息
 * @param onChunk 每收到一段文本时回调
 * @param onDone 完成时回调
 * @param onError 出错时回调
 */
export function sendMessageStream(
  sessionId: number,
  userMessage: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: string) => void,
): AbortController {
  const controller = new AbortController();

  fetch(`/api/chat/sessions/${sessionId}/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: userMessage }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        onError(`请求失败: ${response.status}`);
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        onError('无法读取响应流');
        return;
      }

      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        let currentEvent = 'message'; // 默认事件类型

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
            continue;
          }
          if (line.startsWith('data:')) {
            const data = line.slice(5);

            if (currentEvent === 'error') {
              try {
                const errObj = JSON.parse(data);
                onError(errObj.message || '服务端错误');
              } catch {
                onError(data || '服务端错误');
              }
              return;
            }

            if (currentEvent === 'done') {
              // done 事件，不需要处理 data
              onDone();
              return;
            }

            // message 事件：推送 token
            if (data) {
              onChunk(data);
            }
            // 处理完一个 data 后重置事件类型
            currentEvent = 'message';
          }
        }
      }

      // 读取完毕，检查缓冲区剩余
      if (buffer.startsWith('data:')) {
        const data = buffer.slice(5);
        if (data) onChunk(data);
      }

      onDone();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err.message || '网络错误');
      }
    });

  return controller;
}
