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
    <header className="border-b border-[#222] bg-[#090909]/95 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-3 sm:px-6 lg:px-8 py-2.5 sm:py-3 flex items-center justify-between gap-3">
        {/* Left: Branding & Status */}
        <div className="flex items-center gap-2.5 sm:gap-3 group cursor-default">
          <div className="h-8 w-8 sm:h-9 sm:w-9 flex items-center justify-center border border-[#333] bg-[#0a0a0a] shadow-[inset_0_0_10px_rgba(0,0,0,1)] shrink-0">
            <Cpu className="h-4 w-4 text-emerald-500 group-hover:text-emerald-400 transition-colors" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm sm:text-base font-bold tracking-tight text-[#e2e8f0] uppercase flex items-center gap-1.5">
                Workstation Deal Hunter
                <span className="hidden sm:inline bg-[#111] text-emerald-500 border border-[#333] text-[9px] font-bold px-1.5 py-0.5 uppercase tracking-widest tech-text">
                  v3.0
                </span>
              </h1>
            </div>
            <p className="hidden sm:flex text-[10px] text-[#888] items-center gap-2 uppercase tracking-wider font-semibold">
              <span>Syndicating Hardware</span>
              <span className="inline-block w-1 h-1 bg-emerald-500 animate-pulse" />
              <span className="text-emerald-400">Live AI Valuation</span>
            </p>
          </div>
        </div>

        {/* Right: Quick Action Controls */}
        <div className="flex items-center gap-2">
          {import.meta.env.PROD ? (
            <button
              onClick={() => window.location.reload()}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#111] hover:bg-[#1a1a1a] active:scale-95 text-emerald-400 text-[10px] sm:text-[11px] font-bold uppercase tracking-wider border border-[#333] hover:border-emerald-500/50 transition-all"
              title="Refresh dashboard data from GitHub"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              <span className="hidden xs:inline">Refresh</span>
            </button>
          ) : (
            <>
              <button
                id="btn-sync-endpoints"
                onClick={onSync}
                disabled={isSyncing}
                className="inline-flex items-center gap-2 px-3 py-1.5 bg-[#111] hover:bg-[#1a1a1a] active:scale-95 text-[#aaa] text-[10px] font-bold uppercase tracking-widest border border-[#333] transition duration-150 disabled:opacity-50"
                title="Trigger background syndication cycle across eBay, Reddit, and Swappa"
              >
                <RefreshCw className={`h-3 w-3 text-emerald-500 ${isSyncing ? "animate-spin" : ""}`} />
                <span>{isSyncing ? "Syndicating..." : "Force Sync"}</span>
              </button>

              <button
                id="btn-open-evaluator"
                onClick={onOpenEvaluate}
                className="inline-flex items-center gap-2 px-3 py-1.5 bg-emerald-900/20 hover:bg-emerald-900/40 active:scale-95 text-emerald-500 text-[10px] font-bold uppercase tracking-widest border border-emerald-500/30 transition duration-150"
                title="Evaluate raw text with Gemini 2.5 Flash"
              >
                <Sparkles className="h-3 w-3" />
                <span>AI Evaluate</span>
              </button>

              <button
                id="btn-open-pushover"
                onClick={onOpenNotify}
                className="inline-flex items-center gap-2 px-3 py-1.5 bg-[#111] hover:bg-[#1a1a1a] active:scale-95 text-[#aaa] text-[10px] font-bold uppercase tracking-widest border border-[#333] transition duration-150"
                title="Pushover push alert configuration and preview"
              >
                <Bell className="h-3 w-3 text-amber-500" />
                <span>Alerts</span>
              </button>

              {onGitPush && (
                <button
                  id="btn-git-push"
                  onClick={onGitPush}
                  disabled={isPushingGit}
                  className="inline-flex items-center gap-2 px-3 py-1.5 bg-[#111] hover:bg-[#1a1a1a] active:scale-95 text-[#aaa] text-[10px] font-bold uppercase tracking-widest border border-[#333] transition duration-150 disabled:opacity-50"
                  title="Push latest changes to GitHub"
                >
                  <GitBranch className={`h-3 w-3 text-purple-500 ${isPushingGit ? "animate-spin" : ""}`} />
                  <span>{isPushingGit ? "Pushing..." : "Push Code"}</span>
                </button>
              )}

              <button
                id="btn-open-code-explorer"
                onClick={onOpenCode}
                className="inline-flex items-center gap-2 px-3 py-1.5 bg-[#111] hover:bg-[#1a1a1a] active:scale-95 text-[#aaa] text-[10px] font-bold uppercase tracking-widest border border-[#333] transition duration-150"
                title="Explore Python modules"
              >
                <Code2 className="h-3 w-3 text-blue-500" />
                <span>Architecture</span>
              </button>
            </>
          )}
        </div>
      </div>
    </header>
  );
};
