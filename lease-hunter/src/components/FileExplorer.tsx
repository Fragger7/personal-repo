import React, { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { 
  FileCode, FileText, CheckCircle, AlertCircle, Save, 
  RefreshCw, ChevronRight, Eye, Edit2, Code
} from 'lucide-react';
import { DeveloperFile } from '../types';

interface FileExplorerProps {
  files: DeveloperFile[];
  onRefresh: () => void;
}

export default function FileExplorer({ files, onRefresh }: FileExplorerProps) {
  const [selectedFile, setSelectedFile] = useState<string>('app.py');
  const [loadingContent, setLoadingContent] = useState(false);
  const [fileContent, setFileContent] = useState<string>('');
  const [isEditing, setIsEditing] = useState(true);
  const [saveStatus, setSaveStatus] = useState<{ type: 'success' | 'error' | null; message: string }>({ type: null, message: '' });
  const [saving, setSaving] = useState(false);

  // Load file content when selectedFile changes
  const fetchFileContent = async (filename: string) => {
    setLoadingContent(true);
    setSaveStatus({ type: null, message: '' });
    try {
      const res = await fetch(`/api/files/content?filename=${encodeURIComponent(filename)}`);
      const data = await res.json();
      if (res.ok) {
        setFileContent(data.content);
      } else {
        setFileContent(`Error loading file: ${data.error}`);
      }
    } catch (err: any) {
      setFileContent(`Error triggering call: ${err.message}`);
    } finally {
      setLoadingContent(false);
    }
  };

  useEffect(() => {
    fetchFileContent(selectedFile);
  }, [selectedFile]);

  const handleSave = async () => {
    setSaving(true);
    setSaveStatus({ type: null, message: '' });
    try {
      const res = await fetch('/api/files/save', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          filename: selectedFile,
          content: fileContent,
        }),
      });
      const data = await res.json();
      if (res.ok) {
        setSaveStatus({ type: 'success', message: `Successfully updated ${selectedFile}!` });
        onRefresh();
      } else {
        setSaveStatus({ type: 'error', message: data.error || 'Failed to save changes.' });
      }
    } catch (err: any) {
      setSaveStatus({ type: 'error', message: `Server error: ${err.message}` });
    } finally {
      setSaving(false);
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const getIcon = (name: string) => {
    if (name.endsWith('.py') || name.endsWith('.cjs') || name.endsWith('.js') || name.endsWith('.ps1')) {
      return <FileCode className="h-4 w-4 text-emerald-400" />;
    }
    return <FileText className="h-4 w-4 text-sky-400" />;
  };

  // Simplistic custom rendering for styling Markdown summaries
  const renderSimpleDoc = (text: string) => {
    return (
      <div className="space-y-4 text-slate-300 font-sans text-sm leading-relaxed max-h-[500px] overflow-y-auto pr-2">
        {text.split('\n').map((line, i) => {
          if (line.startsWith('# ')) {
            return <h1 key={i} className="text-2xl font-bold text-white border-b border-slate-800 pb-2 mt-4 font-display">{line.substring(2)}</h1>;
          }
          if (line.startsWith('## ')) {
            return <h2 key={i} className="text-lg font-semibold text-sky-400 mt-4">{line.substring(3)}</h2>;
          }
          if (line.startsWith('### ')) {
            return <h3 key={i} className="text-md font-medium text-emerald-400 mt-2">{line.substring(4)}</h3>;
          }
          if (line.startsWith('* ') || line.startsWith('- ')) {
            return (
              <ul key={i} className="list-disc pl-5 space-y-1">
                <li>{line.substring(2)}</li>
              </ul>
            );
          }
          if (line.trim() === '---') {
            return <hr key={i} className="border-slate-800 my-4" />;
          }
          return <p key={i} className={`${line.trim() === '' ? 'h-2' : ''}`}>{line}</p>;
        })}
      </div>
    );
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 bg-slate-950/40 p-4 lg:p-6 rounded-2xl border border-slate-800">
      
      {/* File List Navigation (4cols) */}
      <div className="lg:col-span-4 flex flex-col gap-3">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <Code className="h-5 w-5 text-indigo-400" />
            <h3 className="font-semibold text-white tracking-tight">Active Sync Files</h3>
          </div>
          <button 
            onClick={onRefresh}
            className="p-1 rounded-md text-slate-400 hover:text-white hover:bg-slate-900 transition-colors"
            title="Refresh File Status"
          >
            <RefreshCw className="h-4 w-4" />
          </button>
        </div>

        <div className="space-y-2 max-h-[460px] overflow-y-auto pr-1">
          {files.map((file) => (
            <button
              key={file.name}
              onClick={() => setSelectedFile(file.name)}
              className={`w-full flex items-center justify-between p-3 rounded-lg border text-left transition-all ${
                selectedFile === file.name 
                  ? 'bg-slate-900/80 border-indigo-500 text-white shadow-lg shadow-indigo-500/5' 
                  : 'bg-slate-900/30 border-slate-800/60 text-slate-400 hover:bg-slate-900/60 hover:text-slate-200'
              }`}
            >
              <div className="flex items-center gap-2 min-w-0">
                {getIcon(file.name)}
                <span className="font-mono text-xs truncate font-medium">{file.name}</span>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <span className="text-[10px] text-slate-500 font-mono">{formatSize(file.size)}</span>
                {file.exists ? (
                  <div className="h-1.5 w-1.5 rounded-full bg-emerald-400 shadow-sm shadow-emerald-400/50" title="Synchronized" />
                ) : (
                  <div className="h-1.5 w-1.5 rounded-full bg-red-400 shadow-sm shadow-red-400/50" title="Missing" />
                )}
              </div>
            </button>
          ))}
        </div>

        <div className="bg-slate-900/50 rounded-lg border border-slate-800/80 p-3 mt-1">
          <p className="text-[11px] text-slate-400 leading-normal">
            ⚙️ <strong className="text-slate-300">Workspace Live-Sync</strong>: Modifications to code are saved immediately to local disk. Clicking <strong className="text-indigo-400">Git Push Flow</strong> commits and pushes these specific workspace files in isolation.
          </p>
        </div>
      </div>

      {/* Code Editor & File Inspector (8cols) */}
      <div className="lg:col-span-8 flex flex-col gap-3 min-w-0">
        
        {/* Editor Toolbar */}
        <div className="flex items-center justify-between bg-slate-900/50 rounded-lg border border-slate-800 p-3">
          <div className="flex items-center gap-2 min-w-0">
            {getIcon(selectedFile)}
            <span className="font-mono text-sm text-slate-200 font-semibold truncate">{selectedFile}</span>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={() => setIsEditing(false)}
              className={`flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                !isEditing 
                  ? 'bg-indigo-600 text-white' 
                  : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
            >
              <Eye className="h-3 w-3" />
              <span>Inspector</span>
            </button>
            <button
              onClick={() => setIsEditing(true)}
              className={`flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                isEditing 
                  ? 'bg-indigo-600 text-white' 
                  : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
            >
              <Edit2 className="h-3 w-3" />
              <span>Edit Code</span>
            </button>
          </div>
        </div>

        {/* Editor Surface */}
        <div className="relative flex-1 rounded-lg border border-slate-800 bg-slate-950 p-1 flex flex-col shadow-inner">
          {loadingContent ? (
            <div className="h-[430px] flex flex-col items-center justify-center gap-2 text-slate-400">
              <RefreshCw className="h-6 w-6 animate-spin text-indigo-400" />
              <span className="text-xs font-medium font-mono">Loading code matrix...</span>
            </div>
          ) : !isEditing && (selectedFile.endsWith('.md') || selectedFile.endsWith('.txt')) ? (
            <div className="p-4 overflow-y-auto h-[430px]">
              {renderSimpleDoc(fileContent)}
            </div>
          ) : (
            <div className="flex h-[430px]-">
              {/* Line Numbers Bar */}
              <div className="w-10 pr-2 pt-3 text-right font-mono text-slate-600 select-none text-[11px] border-r border-slate-900 h-[430px] overflow-y-hidden bg-slate-950/80">
                {Array.from({ length: Math.max(1, fileContent.split('\n').length) }).map((_, i) => (
                  <div key={i}>{i + 1}</div>
                ))}
              </div>
              {/* Code TextArea */}
              <textarea
                value={fileContent}
                onChange={(e) => setFileContent(e.target.value)}
                className="w-full h-[430px] font-mono text-xs text-slate-300 p-3 bg-slate-950 outline-none border-none resize-none overflow-y-auto leading-relaxed focus:text-white"
                placeholder="// Enter file content..."
                spellCheck={false}
              />
            </div>
          )}
        </div>

        {/* Saving / Feedback Footer */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 p-1">
          {saveStatus.type ? (
            <div className={`flex items-center gap-2 text-xs p-2 rounded-lg ${
              saveStatus.type === 'success' ? 'text-emerald-400 bg-emerald-950/20 border border-emerald-900/30' : 'text-red-400 bg-red-950/20 border border-red-900/30'
            }`}>
              {saveStatus.type === 'success' ? <CheckCircle className="h-4 w-4 shrink-0" /> : <AlertCircle className="h-4 w-4 shrink-0" />}
              <span className="font-medium shrink">{saveStatus.message}</span>
            </div>
          ) : (
            <span className="text-xs text-slate-500 font-mono">Ready to write...</span>
          )}

          <button
            onClick={handleSave}
            disabled={saving || loadingContent}
            className="flex items-center justify-center gap-1.5 px-4 py-2 text-xs font-semibold bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white rounded-lg transition-colors cursor-pointer self-stretch sm:self-auto"
          >
            {saving ? (
              <RefreshCw className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Save className="h-3.5 w-3.5" />
            )}
            <span>Save Updates to Disk</span>
          </button>
        </div>

      </div>

    </div>
  );
}
