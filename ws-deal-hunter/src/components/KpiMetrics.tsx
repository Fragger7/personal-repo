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
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      {/* Metric 1: Tracked Inventory */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 relative overflow-hidden">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Total Analyzed
          </span>
          <div className="p-2 rounded-lg bg-slate-800 text-slate-300">
            <Layers className="h-4 w-4" />
          </div>
        </div>
        <div className="mt-2.5 flex items-baseline gap-2">
          <span className="text-2xl font-bold text-white">{total}</span>
          <span className="text-xs text-slate-400">units</span>
        </div>
        <div className="mt-1.5 flex items-center gap-1.5 text-xs text-slate-400">
          <span className="text-emerald-400 font-medium">{filteredCount}</span>
          <span>matching active filters</span>
        </div>
      </div>

      {/* Metric 2: High-Yield Alert Deals */}
      <div className="bg-slate-900 border border-emerald-900/40 rounded-xl p-4 relative overflow-hidden bg-gradient-to-br from-emerald-950/20 to-transparent">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-emerald-400 uppercase tracking-wider">
            High-Yield Deals
          </span>
          <div className="p-2 rounded-lg bg-emerald-950/60 border border-emerald-500/20 text-emerald-400">
            <Flame className="h-4 w-4" />
          </div>
        </div>
        <div className="mt-2.5 flex items-baseline gap-2">
          <span className="text-2xl font-bold text-emerald-400">{highYield}</span>
          <span className="text-xs text-emerald-500/80">opportunities</span>
        </div>
        <div className="mt-1.5 text-xs text-slate-400">
          Score <span className="font-semibold text-emerald-400">&ge; 8.5</span> &amp; Price <span className="font-semibold text-emerald-400">&le; $750</span>
        </div>
      </div>

      {/* Metric 3: Avg Arbitrage Margin */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 relative overflow-hidden">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Avg Profit Margin
          </span>
          <div className="p-2 rounded-lg bg-slate-800 text-blue-400">
            <TrendingUp className="h-4 w-4" />
          </div>
        </div>
        <div className="mt-2.5 flex items-baseline gap-2">
          <span className="text-2xl font-bold text-white">+{avgMargin}%</span>
          <span className="text-xs text-blue-400 font-semibold">(+${avgProfit})</span>
        </div>
        <div className="mt-1.5 text-xs text-slate-400">
          Spread over Fair Market Value
        </div>
      </div>

      {/* Metric 4: Peak AI Deal Score */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 relative overflow-hidden">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Top Deal Score
          </span>
          <div className="p-2 rounded-lg bg-slate-800 text-amber-400">
            <Cpu className="h-4 w-4" />
          </div>
        </div>
        <div className="mt-2.5 flex items-baseline gap-2">
          <span className="text-2xl font-bold text-amber-400">{topScore.toFixed(1)}</span>
          <span className="text-xs text-slate-400">/ 10.0</span>
        </div>
        <div className="mt-1.5 text-xs text-slate-400 flex items-center gap-1.5">
          <span className="text-emerald-400">●</span>
          <span>Evaluated via Gemini 2.5 Flash</span>
        </div>
      </div>
    </div>
  );
};
