import React from "react";
import { Cpu, RefreshCw, Sparkles, Code2, Bell, GitBranch } from "lucide-react";

interface HeaderProps {
  onSync: () => void;
  isSyncing: boolean;
  onOpenEvaluate: () => void;
  onOpenCode: () => void;
  onOpenNotify: () => void;
  onGitPush?: () => void;
  isPushingGit?: boolean;
  totalDeals: number;
}

export const Header: React.FC<HeaderProps> = ({
  onSync,
  isSyncing,
  onOpenEvaluate,
  onOpenCode,
  onOpenNotify,
  onGitPush,
  isPushingGit,
  totalDeals,
}) => {
  return (
    <header className="glass-panel sticky top-0 z-30">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        {/* Left: Branding & Status */}
        <div className="flex items-center gap-3.5 group cursor-default">
          <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-[1px] flex items-center justify-center shadow-lg shadow-emerald-500/20 group-hover:shadow-emerald-500/40 transition-all duration-300">
            <div className="w-full h-full bg-slate-950 rounded-[11px] flex items-center justify-center">
              <Cpu className="h-5 w-5 text-emerald-400 group-hover:scale-110 transition-transform duration-300" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-bold tracking-tight text-white flex items-center gap-2">
                Workstation Deal Hunter
                <span className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider shadow-inner shadow-emerald-500/10">
                  v2.0
                </span>
              </h1>
            </div>
            <p className="text-xs text-slate-400 flex items-center gap-2 mt-0.5 font-medium">
              <span>Syndicating Global Workstation Inventory</span>
              <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse shadow-[0_0_8px_rgba(52,211,153,0.8)]" />
              <span className="text-slate-300">Live AI Valuation</span>
            </p>
          </div>
        </div>

        {/* Right: Quick Action Controls */}
        <div className="flex items-center flex-wrap gap-2.5">
          {import.meta.env.PROD ? (
            <button
              onClick={() => window.location.reload()}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 active:scale-95 text-emerald-400 text-xs font-bold border border-emerald-500/20 transition-all duration-200 shadow-sm shadow-emerald-500/5"
              title="Refresh dashboard data from GitHub"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              <span>Refresh Data</span>
            </button>
          ) : (
            <>
              <button
                id="btn-sync-endpoints"
                onClick={onSync}
                disabled={isSyncing}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 active:scale-95 text-slate-200 text-xs font-semibold border border-slate-700 transition duration-150 disabled:opacity-50"
                title="Trigger background syndication cycle across eBay, Reddit, and Swappa"
              >
                <RefreshCw className={`h-3.5 w-3.5 text-emerald-400 ${isSyncing ? "animate-spin" : ""}`} />
                <span>{isSyncing ? "Syndicating Feeds..." : "Force Sync"}</span>
              </button>

              <button
                id="btn-open-evaluator"
                onClick={onOpenEvaluate}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 active:scale-95 text-white text-xs font-semibold shadow-sm transition duration-150"
                title="Evaluate raw text with Gemini 2.5 Flash"
              >
                <Sparkles className="h-3.5 w-3.5" />
                <span>AI Evaluate</span>
              </button>

              <button
                id="btn-open-pushover"
                onClick={onOpenNotify}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 active:scale-95 text-slate-200 text-xs font-semibold border border-slate-700 transition duration-150"
                title="Pushover push alert configuration and preview"
              >
                <Bell className="h-3.5 w-3.5 text-amber-400" />
                <span>Alerts</span>
              </button>

              {onGitPush && (
                <button
                  id="btn-git-push"
                  onClick={onGitPush}
                  disabled={isPushingGit}
                  className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 active:scale-95 text-slate-200 text-xs font-semibold border border-slate-700 transition duration-150 disabled:opacity-50"
                  title="Push latest changes to GitHub (Fragger7/personal-repo:ws-deal-hunter)"
                >
                  <GitBranch className={`h-3.5 w-3.5 text-purple-400 ${isPushingGit ? "animate-spin" : ""}`} />
                  <span>{isPushingGit ? "Pushing..." : "Push Code"}</span>
                </button>
              )}

              <button
                id="btn-open-code-explorer"
                onClick={onOpenCode}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 active:scale-95 text-slate-200 text-xs font-semibold border border-slate-700 transition duration-150"
                title="Explore Python modules and run test_system.py"
              >
                <Code2 className="h-3.5 w-3.5 text-blue-400" />
                <span>Architecture</span>
              </button>
            </>
          )}
        </div>
      </div>
    </header>
  );
};
