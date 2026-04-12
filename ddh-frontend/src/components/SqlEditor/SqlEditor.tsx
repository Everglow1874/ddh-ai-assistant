import Editor from '@monaco-editor/react';

interface SqlEditorProps {
  value?: string;
  onChange?: (value: string | undefined) => void;
  readOnly?: boolean;
  height?: number | string;
}

export function SqlEditor({ value = '', onChange, readOnly = false, height = 200 }: SqlEditorProps) {
  return (
    <div style={{ border: '1px solid #d9d9d9', borderRadius: 4 }}>
      <Editor
        height={height}
        defaultLanguage="sql"
        value={value}
        onChange={onChange}
        theme="vs-light"
        options={{
          readOnly,
          minimap: { enabled: false },
          lineNumbers: 'on',
          wordWrap: 'on',
          fontSize: 13,
          scrollBeyondLastLine: false,
          automaticLayout: true,
          padding: { top: 8, bottom: 8 },
        }}
      />
    </div>
  );
}


interface SqlPreviewProps {
  sql: string;
  height?: number | string;
}

export function SqlPreview({ sql, height = 200 }: SqlPreviewProps) {
  return <SqlEditor value={sql} readOnly height={height} />;
}