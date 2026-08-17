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
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
      {/* Metric 1: Tracked Inventory */}
      <div className="industrial-panel p-5 relative overflow-hidden group hover:border-[#444] transition-colors">
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-[#888] uppercase tracking-widest">
            Total Analyzed
          </span>
          <div className="text-[#666] group-hover:text-emerald-500 transition-colors">
            <Layers className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-[#e2e8f0] tech-text">{total}</span>
          <span className="text-[9px] font-bold text-[#666] uppercase tracking-widest">units</span>
        </div>
        <div className="mt-4 flex items-center gap-2 text-[10px] uppercase tracking-widest font-bold text-[#888] relative z-10">
          <span className="text-emerald-500 tech-text">{filteredCount}</span>
          <span>Matching active filters</span>
        </div>
      </div>

      {/* Metric 2: High-Yield Alert Deals */}
      <div className="industrial-panel p-5 relative overflow-hidden group border-[#1a3a2a] hover:border-emerald-500/50 transition-colors laser-accent">
        <div className="absolute top-0 right-0 w-32 h-32 bg-emerald-500/5 rounded-full blur-2xl -mr-10 -mt-10 pointer-events-none group-hover:bg-emerald-500/10 transition-all" />
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-emerald-500 uppercase tracking-widest">
            High-Yield Deals
          </span>
          <div className="text-emerald-500">
            <Flame className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-emerald-400 tech-text">{highYield}</span>
          <span className="text-[9px] font-bold text-emerald-500/60 uppercase tracking-widest">opps</span>
        </div>
        <div className="mt-4 text-[10px] uppercase tracking-widest font-bold text-[#888] relative z-10">
          Score <span className="text-emerald-500 tech-text">&ge; 8.5</span> &amp; Price <span className="text-emerald-500 tech-text">&le; $750</span>
        </div>
      </div>

      {/* Metric 3: Avg Arbitrage Margin */}
      <div className="industrial-panel p-5 relative overflow-hidden group hover:border-[#444] transition-colors">
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-[#888] uppercase tracking-widest">
            Avg Profit Margin
          </span>
          <div className="text-blue-500">
            <TrendingUp className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-white tech-text">+{avgMargin}%</span>
          <span className="text-[10px] font-bold text-blue-500 tech-text">(+${avgProfit})</span>
        </div>
        <div className="mt-4 text-[10px] uppercase tracking-widest font-bold text-[#888] relative z-10">
          Spread over Fair Market Value
        </div>
      </div>

      {/* Metric 4: Peak AI Deal Score */}
      <div className="industrial-panel p-5 relative overflow-hidden group hover:border-[#444] transition-colors">
        <div className="flex items-center justify-between mb-4 relative z-10">
          <span className="text-[10px] font-bold text-[#888] uppercase tracking-widest">
            Top Deal Score
          </span>
          <div className="text-amber-500">
            <Cpu className="h-4 w-4" />
          </div>
        </div>
        <div className="flex items-baseline gap-2 relative z-10">
          <span className="text-4xl font-black text-amber-500 tech-text">{topScore.toFixed(1)}</span>
          <span className="text-[10px] font-bold text-amber-500/50 tech-text">/ 10.0</span>
        </div>
        <div className="mt-4 text-[10px] uppercase tracking-widest font-bold text-[#888] flex items-center gap-2 relative z-10">
          <span className="w-1.5 h-1.5 bg-amber-500 animate-pulse shadow-[0_0_5px_rgba(251,191,36,0.8)]" />
          <span>Evaluated via Gemini 2.5 Flash</span>
        </div>
      </div>
    </div>
  );
};
