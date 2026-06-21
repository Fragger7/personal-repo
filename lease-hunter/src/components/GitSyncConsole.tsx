import React, { useState, useRef, useEffect } from 'react';
import { motion } from 'motion/react';
import { 
  Terminal, ShieldAlert, GitBranch, Play, AlertCircle, 
  CheckCircle, Copy, Info, Check, RefreshCw
} from 'lucide-react';
import { SetupStatus } from '../types';

interface GitSyncConsoleProps {
  status: SetupStatus | null;
  onRefreshStatus: () => void;
}

export default function GitSyncConsole({ status, onRefreshStatus }: GitSyncConsoleProps) {
  const [commitMsg, setCommitMsg] = useState('Update Lease Hunter via AI Studio Control Panel');
  const [pushing, setPushing] = useState(false);
  const [terminalOutput, setTerminalOutput] = useState<string>('');
  const [completed, setCompleted] = useState(false);
  const [errorText, setErrorText] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);

  const terminalEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll terminal
  useEffect(() => {
    if (terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [terminalOutput]);

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopied(label);
    setTimeout(() => setCopied(null), 2000);
  };

  const triggerGitPush = async () => {
    setPushing(true);
    setCompleted(false);
    setErrorText(null);
    setTerminalOutput('');

    try {
      const response = await fetch('/api/git-push', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ commitMsg }),
      });

      if (!response.ok) {
        // Try to query error JSON or fallback
        try {
          const errData = await response.json();
          setErrorText(errData.error || 'Server error triggering sync.');
          setTerminalOutput(`[SYSTEM ERROR] Failed: ${errData.error || 'Unknown error'}`);
        } catch {
          const rawErr = await response.text();
          setErrorText(rawErr || 'Failed to initialize backend stream.');
          setTerminalOutput(`[SYSTEM ERROR] Failed raw init:\n${rawErr}`);
        }
        setPushing(false);
        return;
      }

      // Stream output chunks in real-time
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        setTerminalOutput('Error: Unable to open response stream reader.');
        setPushing(false);
        return;
      }

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        setTerminalOutput(prev => prev + chunk);
      }

      setCompleted(true);
      onRefreshStatus();
    } catch (err: any) {
      setErrorText(`Network fault: ${err.message}`);
      setTerminalOutput(prev => prev + `\n[NETWORK FAULT] Connection closed unexpectedly. ${err.message}`);
    } finally {
      setPushing(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 bg-slate-950/40 p-4 lg:p-6 rounded-2xl border border-slate-800">
      
      {/* Setup Guide Panel (5cols) */}
      <div className="lg:col-span-5 flex flex-col gap-4">
        <div className="flex items-center gap-2 border-b border-slate-800 pb-3">
          <ShieldAlert className="h-5 w-5 text-indigo-400" />
          <h3 className="font-semibold text-white tracking-tight font-display">DevOps Setup Guide</h3>
        </div>

        <div className="space-y-4 text-xs font-sans text-slate-300 leading-normal">
          <div className="p-3 bg-slate-900/40 border border-slate-800 rounded-lg flex items-start gap-2">
            <span className="h-5 w-5 rounded-full bg-indigo-950 text-indigo-400 font-bold border border-indigo-900 shrink-0 flex items-center justify-center font-mono">1</span>
            <div>
              <strong className="text-white">Generate GitHub PAT</strong>
              <p className="mt-1 text-slate-400">
                Generate a write-permission Personal Access Token (PAT) for your GitHub repository:
                <code className="block mt-1 bg-slate-950 text-slate-300 p-1 font-mono text-[10px] rounded border border-slate-800 break-all select-all">
                  https://github.com/settings/tokens
                </code>
              </p>
            </div>
          </div>

          <div className="p-3 bg-slate-900/40 border border-slate-800 rounded-lg flex items-start gap-2">
            <span className="h-5 w-5 rounded-full bg-indigo-950 text-indigo-400 font-bold border border-indigo-900 shrink-0 flex items-center justify-center font-mono">2</span>
            <div>
              <strong className="text-white">Configure AI Studio Secret</strong>
              <p className="mt-1 text-slate-400">
                1. Look at Google AI Studio sidebar config panel.<br/>
                2. Add the variable: <strong className="text-indigo-400">GITHUB_TOKEN</strong><br/>
                3. Paste your generated PAT as the secret value. Click Save.
              </p>
            </div>
          </div>

          <div className="p-3 bg-slate-900/40 border border-slate-800 rounded-lg flex items-start gap-2">
            <span className="h-5 w-5 rounded-full bg-indigo-950 text-indigo-400 font-bold border border-indigo-900 shrink-0 flex items-center justify-center font-mono">3</span>
            <div>
              <strong className="text-white">Terminal Command execution</strong>
              <p className="mt-1 text-slate-400">
                Our backend runs <code className="text-sky-300 font-mono">git_push.cjs</code> securely with your secret, pushing to:
                <code className="block mt-1 bg-slate-950 text-sky-400 p-1 font-mono text-[10px] rounded border border-slate-800 select-all truncate">
                  https://github.com/Fragger7/personal-repo.git
                </code>
              </p>
            </div>
          </div>
        </div>

        <div className="bg-amber-950/20 border border-amber-900/30 p-3.5 rounded-lg text-xs text-amber-300 mt-1 flex gap-2">
          <Info className="h-5 w-5 text-amber-500 shrink-0" />
          <div>
            <strong className="text-white block mb-0.5 font-semibold">Secret Isolation Protection</strong>
            Your token never leaves the server-side container, never leaks in client code, and git temporary directories are completely deleted on completion. This follows strict production engineering standards.
          </div>
        </div>

      </div>

      {/* Terminal Console (7cols) */}
      <div className="lg:col-span-7 flex flex-col gap-4">
        
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <Terminal className="h-5 w-5 text-sky-400" />
            <h3 className="font-semibold text-white tracking-tight font-display">Sync Command Console</h3>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-[10px] uppercase font-bold tracking-widest text-slate-500 font-mono">Branch:</span>
            <span className="bg-slate-900 border border-slate-800 text-slate-300 font-mono text-[10px] px-2 py-0.5 rounded-full flex items-center gap-1">
              <GitBranch className="h-3 w-3 text-indigo-400" />
              main
            </span>
          </div>
        </div>

        {/* Input Form for Commit Message */}
        <div className="bg-slate-900/40 border border-slate-800 p-4 rounded-xl space-y-3.5">
          <div>
            <label className="block text-xs text-slate-400 font-semibold mb-1 uppercase tracking-wider">
              Commit Description Message
            </label>
            <input
              type="text"
              value={commitMsg}
              onChange={(e) => setCommitMsg(e.target.value)}
              disabled={pushing}
              className="w-full p-2.5 text-xs font-mono text-white bg-slate-950 border border-slate-800 rounded focus:border-indigo-500 outline-none disabled:opacity-50"
              placeholder="e.g. Update Lease Hunter parameters"
            />
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-400">Token Status:</span>
              {status?.gitHubTokenConfigured ? (
                <span className="text-emerald-400 font-semibold font-mono text-xs flex items-center gap-1">
                  <CheckCircle className="h-3.5 w-3.5" /> Set
                </span>
              ) : (
                <span className="text-red-400 font-semibold font-mono text-xs flex items-center gap-1">
                  <AlertCircle className="h-3.5 w-3.5 text-red-500 animate-pulse" /> Unset In Secrets
                </span>
              )}
            </div>

            <button
              onClick={triggerGitPush}
              disabled={pushing || !status?.gitHubTokenConfigured}
              className="flex items-center justify-center gap-1.5 px-5 py-2 text-xs font-bold bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 disabled:hover:bg-indigo-600 font-sans text-white rounded-lg transition-colors cursor-pointer"
            >
              {pushing ? (
                <>
                  <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                  <span>Syncing...</span>
                </>
              ) : (
                <>
                  <Play className="h-3.5 w-3.5 fill-current" />
                  <span>Execute Push Flow</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Console Box Output */}
        <div className="bg-slate-950 rounded-xl border border-slate-800/80 p-4 font-mono text-xs shadow-inner flex flex-col justify-between">
          <div className="text-[10px] text-slate-500 font-semibold border-b border-slate-900 pb-1.5 mb-2.5 flex items-center justify-between">
            <span>UNIX TERMINAL STDOUT</span>
            <button 
              onClick={() => copyToClipboard(terminalOutput, 'stdout')}
              className="text-slate-500 hover:text-slate-300 flex items-center gap-1"
              title="Copy Output"
            >
              {copied === 'stdout' ? <Check className="h-2.5 w-2.5 text-emerald-400" /> : <Copy className="h-2.5 w-2.5" />}
              <span>{copied === 'stdout' ? 'Copied' : 'Copy log'}</span>
            </button>
          </div>

          <div className="h-44 overflow-y-auto font-mono text-[11px] text-slate-300 leading-normal whitespace-pre-wrap select-text selection:bg-indigo-900/55 scrollbar-thin scrollbar-thumb-slate-800">
            {terminalOutput || (
              <span className="text-slate-600 italic">No command in progress. Awaiting push action to display active console log stream...</span>
            )}
            <div ref={terminalEndRef} />
          </div>

          {errorText && (
            <div className="mt-3 p-2 bg-red-950/20 border border-red-900/30 text-red-400 rounded-lg flex items-start gap-1.5 leading-normal">
              <AlertCircle className="h-4 w-4 shrink-0 mt-0.5 text-red-500" />
              <span>{errorText}</span>
            </div>
          )}

          {completed && !errorText && (
            <div className="mt-3 p-2 bg-emerald-950/20 border border-emerald-900/30 text-emerald-400 rounded-lg flex items-center gap-1.5 font-sans">
              <CheckCircle className="h-4 w-4 shrink-0 text-emerald-400" />
              <span className="font-medium text-xs">Pushed successfully! Streamlit Cloud Community pipeline updated.</span>
            </div>
          )}
        </div>

      </div>

    </div>
  );
}
