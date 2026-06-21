import React, { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { 
  Terminal, ShieldAlert, GitBranch, RefreshCw, FileCode, 
  Map, HelpCircle, Code, Settings, Sparkles, Server, Clock, LogOut
} from 'lucide-react';

import FileExplorer from './components/FileExplorer';
import TaxSimulator from './components/TaxSimulator';
import GitSyncConsole from './components/GitSyncConsole';
import { DeveloperFile, SetupStatus } from './types';

export default function App() {
  const [activeTab, setActiveTab] = useState<'files' | 'tax' | 'git'>('files');
  const [setupStatus, setSetupStatus] = useState<SetupStatus | null>(null);
  const [files, setFiles] = useState<DeveloperFile[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchStatusAndFiles = async () => {
    setLoading(true);
    try {
      const [statusRes, filesRes] = await Promise.all([
        fetch('/api/status'),
        fetch('/api/files')
      ]);

      if (statusRes.ok && filesRes.ok) {
        const statusData = await statusRes.json();
        const filesData = await filesRes.json();
        setSetupStatus(statusData);
        setFiles(filesData);
      }
    } catch (err) {
      console.error('Error contacting workspace server:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatusAndFiles();
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-indigo-600/30 select-none">
      
      {/* Decorative Top Accent Line */}
      <div className="h-1 bg-gradient-to-r from-violet-600 via-indigo-500 to-sky-400 w-full" />

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 md:p-8 flex flex-col gap-6">
        
        {/* Header Block */}
        <header className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 border-b border-slate-900 pb-6">
          <div className="space-y-1">
            <span className="text-xs font-semibold text-sky-400 font-mono tracking-widest uppercase flex items-center gap-1.5 leading-none">
              <Sparkles className="h-3.5 w-3.5 text-indigo-400" />
              Developer Environment Control
            </span>
            <h1 className="text-3xl font-bold font-display tracking-tight text-white flex items-center gap-2">
              Lease Hunter Console
            </h1>
            <p className="text-xs md:text-sm text-slate-400 leading-normal">
              Unified administration deck for managing parameters, verifying statutory multi-state tax logic, and synchronizing workspace folders to GitHub.
            </p>
          </div>

          <div className="flex items-center gap-3 self-start lg:self-auto shrink-0">
            <button
              onClick={fetchStatusAndFiles}
              disabled={loading}
              className="p-2.5 rounded-lg border border-slate-800 text-slate-400 hover:text-white bg-slate-900/40 hover:bg-slate-900 transition-all flex items-center gap-1.5 text-xs font-semibold select-none cursor-pointer"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin text-sky-400' : ''}`} />
              <span>Full Reload</span>
            </button>
            <a
              href="https://github.com/Fragger7/personal-repo"
              target="_blank"
              rel="noreferrer"
              className="px-4 py-2 text-xs font-bold font-sans bg-slate-900 border border-slate-800 hover:bg-slate-800 hover:text-white rounded-lg transition-colors flex items-center gap-1 text-slate-300 shadow-sm"
            >
              <GitBranch className="h-3.5 w-3.5 text-indigo-400" />
              <span>Remote Repository</span>
            </a>
          </div>
        </header>

        {/* Setup Status Row */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          
          <div className="bg-slate-900/30 border border-slate-800 p-4 rounded-xl flex items-center justify-between shadow-sm">
            <div className="space-y-1">
              <span className="text-[10px] text-slate-500 font-bold uppercase tracking-widest font-mono">WORKSPACE SYSTEM</span>
              <span className="text-sm font-semibold text-slate-200 flex items-center gap-1 font-mono">
                Active Node Environment
              </span>
            </div>
            <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-900">
              <Server className="h-5 w-5 text-indigo-400" />
            </div>
          </div>

          <div className="bg-slate-900/30 border border-slate-800 p-4 rounded-xl flex items-center justify-between shadow-sm">
            <div className="space-y-1">
              <span className="text-[10px] text-slate-500 font-bold uppercase tracking-widest font-mono">SYSTEM CLOCK</span>
              <span className="text-sm font-semibold text-slate-200 block font-mono">
                {setupStatus ? new Date(setupStatus.time).toLocaleTimeString() : 'Loading...'}
              </span>
            </div>
            <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-900">
              <Clock className="h-5 w-5 text-indigo-400" />
            </div>
          </div>

          <div className="bg-slate-900/30 border border-slate-800 p-4 rounded-xl flex items-center justify-between shadow-sm5">
            <div className="space-y-1">
              <span className="text-[10px] text-slate-500 font-bold uppercase tracking-widest font-mono">GITHUB INTEGRATION</span>
              <span className={`text-xs font-bold font-mono px-2 py-0.5 rounded-full inline-block mt-0.5 ${
                setupStatus?.gitHubTokenConfigured 
                  ? 'bg-emerald-950/40 text-emerald-400 border border-emerald-900/35' 
                  : 'bg-red-950/40 text-red-400 border border-red-900/35 animate-pulse'
              }`}>
                {setupStatus?.gitHubTokenConfigured ? 'CONNECTED' : 'GITHUB_TOKEN_MISSING'}
              </span>
            </div>
            <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-900">
              <LogOut className={`h-5 w-5 ${setupStatus?.gitHubTokenConfigured ? 'text-emerald-400' : 'text-red-400'}`} />
            </div>
          </div>

          <div className="bg-slate-900/30 border border-slate-800 p-4 rounded-xl flex items-center justify-between shadow-sm">
            <div className="space-y-1">
              <span className="text-[10px] text-slate-500 font-bold uppercase tracking-widest font-mono">AI AUTO SOLVER</span>
              <span className={`text-xs font-bold font-mono px-2 py-0.5 rounded-full inline-block mt-0.5 ${
                setupStatus?.geminiApiKeyConfigured 
                  ? 'bg-emerald-950/40 text-emerald-400 border border-emerald-900/35' 
                  : 'bg-amber-950/40 text-amber-400 border border-amber-900/35'
              }`}>
                {setupStatus?.geminiApiKeyConfigured ? 'GEMINI_READY' : 'GEMINI_KEY_MISSING'}
              </span>
            </div>
            <div className="bg-slate-950 p-2.5 rounded-lg border border-slate-900">
              <Sparkles className={`h-5 w-5 ${setupStatus?.geminiApiKeyConfigured ? 'text-emerald-400' : 'text-amber-400'}`} />
            </div>
          </div>

        </section>

        {/* Tab Controllers */}
        <nav className="flex items-center gap-1.5 border-b border-slate-900 pb-1 mr-auto select-none mt-2">
          
          <button
            onClick={() => setActiveTab('files')}
            className={`px-4 py-2 text-xs font-bold transition-all relative flex items-center gap-2 ${
              activeTab === 'files' 
                ? 'text-white border-b-2 border-indigo-500' 
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <FileCode className="h-4 w-4" />
            <span>Active Sync Files</span>
          </button>

          <button
            onClick={() => setActiveTab('tax')}
            className={`px-4 py-2 text-xs font-bold transition-all relative flex items-center gap-2 ${
              activeTab === 'tax' 
                ? 'text-white border-b-2 border-indigo-500' 
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Map className="h-4 w-4" />
            <span>Tax Core Simulator</span>
          </button>

          <button
            onClick={() => setActiveTab('git')}
            className={`px-4 py-2 text-xs font-bold transition-all relative flex items-center gap-2 ${
              activeTab === 'git' 
                ? 'text-white border-b-2 border-indigo-500' 
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Terminal className="h-4 w-4" />
            <span>DevOps push Flow</span>
          </button>

        </nav>

        {/* Tab Content Rendering with Suspense style */}
        <section className="flex-1 select-none">
          {loading ? (
            <div className="h-96 flex flex-col items-center justify-center gap-3">
              <RefreshCw className="h-8 w-8 animate-spin text-indigo-400" />
              <p className="text-xs font-mono text-slate-500">Retrieving active container states...</p>
            </div>
          ) : (
            <>
              {activeTab === 'files' && (
                <FileExplorer files={files} onRefresh={fetchStatusAndFiles} />
              )}

              {activeTab === 'tax' && (
                <TaxSimulator />
              )}

              {activeTab === 'git' && (
                <GitSyncConsole status={setupStatus} onRefreshStatus={fetchStatusAndFiles} />
              )}
            </>
          )}
        </section>

        {/* Footer */}
        <footer className="border-t border-slate-900 mt-auto pt-6 pb-2 text-center text-xs text-slate-500 font-mono flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <span>🏎️ Universal AI Lease Broker Engine • Production Workspace Shell</span>
          <span>Google AI Studio Build v4.21 &copy; 2026</span>
        </footer>

      </main>
    </div>
  );
}
