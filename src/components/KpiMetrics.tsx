import React, { useState } from "react";
import { TrendingUp, Flame, Cpu, Layers, ChevronDown, ChevronUp, Filter } from "lucide-react";
import { DashboardStats, FilterState } from "../types";

interface KpiMetricsProps {
  stats: DashboardStats | null;
  filteredCount: number;
  filters: FilterState;
  onUpdateFilters: (updater: (prev: FilterState) => FilterState) => void;
  onResetFilters: () => void;
}

export const KpiMetrics: React.FC<KpiMetricsProps> = ({
  stats,
  filteredCount,
  filters,
  onUpdateFilters,
  onResetFilters,
}) => {
  const [isCollapsed, setIsCollapsed] = useState(false);

  const total = stats?.total_deals ?? 0;
  const highYield = stats?.high_yield_deals ?? 0;
  const avgMargin = stats?.avg_margin_pct ?? 0;
  const avgProfit = stats?.avg_profit ?? 0;
  const topScore = stats?.top_score ?? 0;

  const isHighYieldActive = filters.onlyHighYield;
  const isProfitSortActive = filters.sortBy === "profit";
  const isScoreSortActive = filters.sortBy === "score" && filters.minScore >= 8.0;

  return (
    <div className="mb-6">
      {/* Header with Accordion Toggle */}
      <div className="flex items-center justify-between mb-3">
        <button
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-[#888] hover:text-[#e2e8f0] transition-colors group"
        >
          <span className="text-emerald-500 tech-text">//</span>
          <span>Market Intelligence Dashboard</span>
          <span className="text-[9px] px-1.5 py-0.5 rounded bg-[#151515] border border-[#222] text-[#666] group-hover:border-emerald-500/40 group-hover:text-emerald-400 transition-colors">
            {isCollapsed ? "Click to Expand" : "Click to Minimize"}
          </span>
          {isCollapsed ? (
            <ChevronDown className="h-3.5 w-3.5 text-emerald-500" />
          ) : (
            <ChevronUp className="h-3.5 w-3.5 text-[#666] group-hover:text-emerald-500" />
          )}
        </button>

        {isCollapsed && (
          <div className="flex items-center gap-3 text-[10px] uppercase font-mono text-[#888]">
            <span>Total: <strong className="text-white">{total}</strong></span>
            <span>•</span>
            <span>High-Yield: <strong className="text-emerald-400">{highYield}</strong></span>
            <span>•</span>
            <span>Top Score: <strong className="text-amber-400">{topScore.toFixed(1)}</strong></span>
          </div>
        )}
      </div>

      {!isCollapsed && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 animate-in fade-in duration-200">
          
          {/* Widget 1: Tracked Inventory (Click -> Reset/View All) */}
          <div
            onClick={onResetFilters}
            className="industrial-panel p-4 relative overflow-hidden group cursor-pointer hover:border-emerald-500/40 transition-all duration-200 active:scale-[0.98]"
            title="Click to reset filters and view all active inventory"
          >
            <div className="flex items-center justify-between mb-3 relative z-10">
              <span className="text-[10px] font-bold text-[#888] uppercase tracking-widest group-hover:text-[#aaa]">
                Total Analyzed
              </span>
              <div className="text-[#666] group-hover:text-emerald-400 transition-colors">
                <Layers className="h-4 w-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 relative z-10">
              <span className="text-3xl font-black text-[#e2e8f0] tech-text">{total}</span>
              <span className="text-[9px] font-bold text-[#666] uppercase tracking-widest">units</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[9px] uppercase tracking-widest font-bold text-[#888] relative z-10">
              <span>Showing: <strong className="text-emerald-400">{filteredCount}</strong></span>
              <span className="text-emerald-500/80 group-hover:underline">View All &rarr;</span>
            </div>
          </div>

          {/* Widget 2: High-Yield Alert Deals (Click -> Toggle High-Yield Filter) */}
          <div
            onClick={() => onUpdateFilters((f) => ({ ...f, onlyHighYield: !f.onlyHighYield }))}
            className={`industrial-panel p-4 relative overflow-hidden group cursor-pointer transition-all duration-200 active:scale-[0.98] ${
              isHighYieldActive
                ? "bg-[#0c1f14] border-emerald-500 shadow-[0_0_15px_rgba(16,185,129,0.2)]"
                : "border-[#1a3a2a] hover:border-emerald-500/50"
            }`}
            title="Click to toggle High-Yield deals only"
          >
            <div className="absolute top-0 right-0 w-28 h-28 bg-emerald-500/5 rounded-full blur-xl -mr-8 -mt-8 pointer-events-none group-hover:bg-emerald-500/10 transition-all" />
            <div className="flex items-center justify-between mb-3 relative z-10">
              <span className="text-[10px] font-bold text-emerald-400 uppercase tracking-widest flex items-center gap-1.5">
                <span>High-Yield Deals</span>
                {isHighYieldActive && (
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-ping" />
                )}
              </span>
              <div className="text-emerald-400">
                <Flame className="h-4 w-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 relative z-10">
              <span className="text-3xl font-black text-emerald-300 tech-text">{highYield}</span>
              <span className="text-[9px] font-bold text-emerald-500/70 uppercase tracking-widest">deals</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[9px] uppercase tracking-widest font-bold relative z-10">
              <span className="text-[#888]">Score &ge; 8.5 &amp; &le; $750</span>
              <span className={isHighYieldActive ? "text-emerald-300 font-bold" : "text-[#666] group-hover:text-emerald-400"}>
                {isHighYieldActive ? "Active [ON]" : "Filter &rarr;"}
              </span>
            </div>
          </div>

          {/* Widget 3: Avg Arbitrage Margin (Click -> Sort by Profit) */}
          <div
            onClick={() => onUpdateFilters((f) => ({ ...f, sortBy: "profit" }))}
            className={`industrial-panel p-4 relative overflow-hidden group cursor-pointer transition-all duration-200 active:scale-[0.98] ${
              isProfitSortActive
                ? "bg-[#09152a] border-blue-500 shadow-[0_0_15px_rgba(59,130,246,0.2)]"
                : "hover:border-blue-500/40"
            }`}
            title="Click to sort listings by largest dollar profit spread"
          >
            <div className="flex items-center justify-between mb-3 relative z-10">
              <span className="text-[10px] font-bold text-[#888] group-hover:text-blue-400 uppercase tracking-widest transition-colors">
                Avg Profit Spread
              </span>
              <div className="text-blue-400">
                <TrendingUp className="h-4 w-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 relative z-10">
              <span className="text-3xl font-black text-white tech-text">+{avgMargin}%</span>
              <span className="text-[10px] font-bold text-blue-400 tech-text">(+${avgProfit})</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[9px] uppercase tracking-widest font-bold text-[#888] relative z-10">
              <span>Spread vs FMV</span>
              <span className={isProfitSortActive ? "text-blue-300 font-bold" : "text-[#666] group-hover:text-blue-400"}>
                {isProfitSortActive ? "Sorted by Profit" : "Sort High &rarr;"}
              </span>
            </div>
          </div>

          {/* Widget 4: Peak AI Deal Score (Click -> Filter Score >= 8.5) */}
          <div
            onClick={() => onUpdateFilters((f) => ({ ...f, sortBy: "score", minScore: 8.5 }))}
            className={`industrial-panel p-4 relative overflow-hidden group cursor-pointer transition-all duration-200 active:scale-[0.98] ${
              isScoreSortActive
                ? "bg-[#231705] border-amber-500 shadow-[0_0_15px_rgba(245,158,11,0.2)]"
                : "hover:border-amber-500/40"
            }`}
            title="Click to filter for top-tier deals (Score >= 8.5) sorted by score"
          >
            <div className="flex items-center justify-between mb-3 relative z-10">
              <span className="text-[10px] font-bold text-[#888] group-hover:text-amber-400 uppercase tracking-widest transition-colors">
                Top Deal Score
              </span>
              <div className="text-amber-400">
                <Cpu className="h-4 w-4" />
              </div>
            </div>
            <div className="flex items-baseline gap-2 relative z-10">
              <span className="text-3xl font-black text-amber-400 tech-text">{topScore.toFixed(1)}</span>
              <span className="text-[10px] font-bold text-amber-500/60 tech-text">/ 10.0</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[9px] uppercase tracking-widest font-bold text-[#888] relative z-10">
              <span>Gemini Valuated</span>
              <span className={isScoreSortActive ? "text-amber-300 font-bold" : "text-[#666] group-hover:text-amber-400"}>
                {isScoreSortActive ? "Top Deals Filtered" : "Filter Elite &rarr;"}
              </span>
            </div>
          </div>

        </div>
      )}
    </div>
  );
};
