import React, { useState, useEffect } from "react";
import { X, Play, Code2, CheckCircle2, AlertCircle, FileCode, Copy, Check } from "lucide-react";

interface CodeExplorerModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const CodeExplorerModal: React.FC<CodeExplorerModalProps> = ({
  isOpen,
  onClose,
}) => {
  const [files, setFiles] = useState<Record<string, string>>({});
  const [activeFile, setActiveFile] = useState<string>("daemon.py");
  const [copied, setCopied] = useState(false);
  const [testOutput, setTestOutput] = useState<string | null>(null);
  const [isRunningTests, setIsRunningTests] = useState(false);

  useEffect(() => {
    if (isOpen) {
      fetch("/api/python/files")
        .then((res) => res.json())
        .then((data) => {
          if (data.success && data.files) {
            setFiles(data.files);
          }
        })
        .catch((err) => console.error("Error loading python files:", err));
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleCopy = () => {
    if (files[activeFile]) {
      navigator.clipboard.writeText(files[activeFile]);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleRunTests = async () => {
    setIsRunningTests(true);
    setTestOutput("Executing `python3 test_system.py` across all test suites...\n");
    try {
      const res = await fetch("/api/python/run-tests");
      const data = await res.json();
      setTestOutput(data.stdout || "Tests completed with no output.");
    } catch (err: any) {
      setTestOutput(`Execution error: ${err.message}`);
    } finally {
      setIsRunningTests(false);
    }
  };

  const fileList = [
    { name: "daemon.py", desc: "Background Scheduler & Pipeline Loop" },
    { name: "collector.py", desc: "eBay, Reddit & Swappa Syndicators" },
    { name: "evaluator.py", desc: "Gemini 2.5 Flash Hardware AI Engine" },
    { name: "notifier.py", desc: "Pushover Push Notification Dispatcher" },
    { name: "storage.py", desc: "Thread-Safe Atomic JSON Store (deals.json)" },
    { name: "app.py", desc: "Streamlit Dashboard App" },
    { name: "test_system.py", desc: "Comprehensive Unittest Suite" },
    { name: "git_sync.py", desc: "GitHub Auto Push / Pull Pipeline" },
    { name: "README.md", desc: "Project Documentation & Setup Guide" },
    { name: "requirements.txt", desc: "Python Dependencies Specification" },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-6xl h-[88vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-950/60">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-400 border border-blue-500/20">
              <Code2 className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Python System Architecture &amp; Codebase</h2>
              <p className="text-xs text-slate-400">
                Modular Python 3.11+ implementation of the Workstation Deal Hunter
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={handleRunTests}
              disabled={isRunningTests}
              className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold transition disabled:opacity-50 shadow-sm"
            >
              <Play className={`h-3.5 w-3.5 ${isRunningTests ? "animate-spin" : ""}`} />
              <span>{isRunningTests ? "Running Tests..." : "Run test_system.py"}</span>
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Main Split Layout */}
        <div className="flex-1 flex overflow-hidden">
          {/* Sidebar File Picker */}
          <div className="w-72 border-r border-slate-800 bg-slate-950/40 p-3 overflow-y-auto space-y-1">
            <div className="text-[10px] font-bold text-slate-400 uppercase px-2 py-1 tracking-wider">
              Python Modules
            </div>
            {fileList.map((f) => {
              const active = activeFile === f.name;
              return (
                <button
                  key={f.name}
                  onClick={() => setActiveFile(f.name)}
                  className={`w-full text-left px-3 py-2 rounded-lg text-xs transition flex flex-col gap-0.5 ${
                    active
                      ? "bg-slate-800 text-emerald-400 font-semibold border border-slate-700"
                      : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
                  }`}
                >
                  <div className="flex items-center gap-1.5">
                    <FileCode className={`h-3.5 w-3.5 ${active ? "text-emerald-400" : "text-slate-400"}`} />
                    <span>{f.name}</span>
                  </div>
                  <span className="text-[10px] text-slate-400 truncate pl-5">
                    {f.desc}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Code Viewer / Terminal */}
          <div className="flex-1 flex flex-col bg-slate-950 overflow-hidden">
            {/* Tab Bar */}
            <div className="flex items-center justify-between px-4 py-2 bg-slate-900/80 border-b border-slate-800 text-xs text-slate-300">
              <span className="font-mono font-semibold text-emerald-400">
                /{activeFile}
              </span>
              <button
                onClick={handleCopy}
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-[11px] text-slate-300 transition"
              >
                {copied ? <Check className="h-3 w-3 text-emerald-400" /> : <Copy className="h-3 w-3" />}
                <span>{copied ? "Copied" : "Copy Code"}</span>
              </button>
            </div>

            {/* Code Content */}
            <div className="flex-1 overflow-auto p-4 font-mono text-xs text-slate-300 leading-relaxed">
              <pre className="whitespace-pre">
                {files[activeFile] || "# Loading module contents..."}
              </pre>
            </div>

            {/* Test Output Terminal Drawer if test was run */}
            {testOutput && (
              <div className="h-44 border-t border-slate-800 bg-black/90 p-3 flex flex-col font-mono text-xs">
                <div className="flex items-center justify-between pb-1.5 text-[11px] text-slate-400 border-b border-slate-800 mb-1.5">
                  <span className="text-emerald-400 font-bold">● test_system.py Output Terminal</span>
                  <button
                    onClick={() => setTestOutput(null)}
                    className="text-slate-400 hover:text-slate-200"
                  >
                    Clear
                  </button>
                </div>
                <div className="flex-1 overflow-y-auto text-emerald-300 text-[11px] whitespace-pre-wrap">
                  {testOutput}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
