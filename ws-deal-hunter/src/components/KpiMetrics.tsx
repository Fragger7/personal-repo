import React from "react";
import { TrendingUp, Flame, Cpu, Layers } from "lucide-react";
import { DashboardStats } from "../types";

interface KpiMetricsProps {
  stats: DashboardStats | null;
  filteredCount: number;
}

export const KpiMetrics: React.FC<KpiMetricsProps> = ({ stats, filteredCount }) => {
  const total = stats?.total_deals ?? 0;
  const highYield = stats?.high_yield_deals ?? 0;
  const avgMargin = stats?.avg_margin_pct ?? 0;
  const avgProfit = stats?.avg_profit ?? 0;
  const topScore = stats?.top_score ?? 0;

  return (
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-8">
      {/* Metric 1: Tracked Inventory */}
      <div className="glass-panel rounded-2xl p-5 relative overflow-hidden group hover:border-slate-700 transition-all">
        <div className="absolute top-0 right-0 w-32 h-32 bg-slate-800/20 rounded-full blur-2xl -mr-10 -mt-10 pointer-events-none" />
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
            Total Analyzed
          </span>
          <div className="p-2.5 rounded-[10px] bg-slate-900 shadow-inner border border-slate-800/80 text-slate-400 group-hover:text-emerald-400 transition-colors">
            <Layers className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-white tracking-tighter">{total}</span>
          <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">units</span>
        </div>
        <div className="mt-3 flex items-center gap-2 text-[11px] font-medium text-slate-400 relative z-10">
          <span className="px-1.5 py-0.5 rounded text-emerald-400 bg-emerald-500/10 font-bold">{filteredCount}</span>
          <span>matching active filters</span>
        </div>
      </div>

      {/* Metric 2: High-Yield Alert Deals */}
      <div className="glass-panel rounded-2xl p-5 relative overflow-hidden group border-emerald-900/40 hover:border-emerald-500/40 transition-all bg-gradient-to-br from-emerald-950/20 to-transparent">
        <div className="absolute top-0 right-0 w-32 h-32 bg-emerald-500/10 rounded-full blur-2xl -mr-10 -mt-10 pointer-events-none group-hover:bg-emerald-500/20 transition-all" />
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-emerald-500 uppercase tracking-widest">
            High-Yield Deals
          </span>
          <div className="p-2.5 rounded-[10px] bg-emerald-950/60 shadow-inner border border-emerald-500/20 text-emerald-400">
            <Flame className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-emerald-400 tracking-tighter">{highYield}</span>
          <span className="text-xs font-bold text-emerald-500/80 uppercase tracking-widest">opps</span>
        </div>
        <div className="mt-3 text-[11px] font-medium text-slate-400 relative z-10">
          Score <span className="font-bold text-emerald-400">&ge; 8.5</span> &amp; Price <span className="font-bold text-emerald-400">&le; $750</span>
        </div>
      </div>

      {/* Metric 3: Avg Arbitrage Margin */}
      <div className="glass-panel rounded-2xl p-5 relative overflow-hidden group hover:border-slate-700 transition-all">
        <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500/10 rounded-full blur-2xl -mr-10 -mt-10 pointer-events-none" />
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
            Avg Profit Margin
          </span>
          <div className="p-2.5 rounded-[10px] bg-slate-900 shadow-inner border border-slate-800/80 text-blue-400">
            <TrendingUp className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-white tracking-tighter">+{avgMargin}%</span>
          <span className="text-xs font-bold text-blue-400 uppercase tracking-widest">(+${avgProfit})</span>
        </div>
        <div className="mt-3 text-[11px] font-medium text-slate-400 relative z-10">
          Spread over Fair Market Value
        </div>
      </div>

      {/* Metric 4: Peak AI Deal Score */}
      <div className="glass-panel rounded-2xl p-5 relative overflow-hidden group hover:border-slate-700 transition-all">
        <div className="absolute top-0 right-0 w-32 h-32 bg-amber-500/10 rounded-full blur-2xl -mr-10 -mt-10 pointer-events-none" />
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
            Top Deal Score
          </span>
          <div className="p-2.5 rounded-[10px] bg-slate-900 shadow-inner border border-slate-800/80 text-amber-400">
            <Cpu className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-amber-400 tracking-tighter">{topScore.toFixed(1)}</span>
          <span className="text-xs font-bold text-amber-500/50 uppercase tracking-widest">/ 10.0</span>
        </div>
        <div className="mt-3 text-[11px] font-medium text-slate-400 flex items-center gap-2 relative z-10">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse shadow-[0_0_5px_rgba(251,191,36,0.8)]" />
          <span>Evaluated via Gemini 2.5 Flash</span>
        </div>
      </div>
    </div>
  );
};
