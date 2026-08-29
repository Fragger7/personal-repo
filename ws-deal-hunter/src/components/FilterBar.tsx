import React, { useState } from "react";
import { Search, SlidersHorizontal, LayoutGrid, List, Flame, X, ChevronDown, ChevronUp, RotateCcw, CheckSquare, Square } from "lucide-react";
import { FilterState } from "../types";

interface FilterBarProps {
  filters: FilterState;
  onChange: (newFilters: FilterState) => void;
  onReset: () => void;
}

const ALL_SOURCES = [
  { id: "ebay", label: "eBay", color: "hover:border-blue-500 hover:text-blue-400" },
  { id: "swappa", label: "Swappa", color: "hover:border-teal-500 hover:text-teal-400" },
  { id: "bestbuy", label: "Best Buy", color: "hover:border-yellow-400 hover:text-yellow-300" },
  { id: "bh_photo", label: "B&H Photo", color: "hover:border-green-500 hover:text-green-400" },
  { id: "reddit", label: "r/hws", color: "hover:border-orange-500 hover:text-orange-400" },
  { id: "dell_refurbished", label: "Dell DFS", color: "hover:border-blue-400 hover:text-blue-300" },
  { id: "microcenter", label: "Micro Center", color: "hover:border-rose-500 hover:text-rose-400" },
  { id: "lenovo_outlet", label: "Lenovo", color: "hover:border-red-500 hover:text-red-400" },
  { id: "goodwill", label: "Goodwill", color: "hover:border-indigo-500 hover:text-indigo-400" },
  { id: "syndicated", label: "Syndicated RSS", color: "hover:border-cyan-500 hover:text-cyan-400" },
];

export const FilterBar: React.FC<FilterBarProps> = ({ filters, onChange, onReset }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const toggleSource = (src: string) => {
    const next = filters.sources.includes(src)
      ? filters.sources.filter((s) => s !== src)
      : [...filters.sources, src];
    onChange({ ...filters, sources: next });
  };

  const selectAllSources = () => {
    onChange({ ...filters, sources: ALL_SOURCES.map((s) => s.id) });
  };

  const clearAllSources = () => {
    onChange({ ...filters, sources: [] });
  };

  // Count active non-default filters
  const activeFilterCount =
    (filters.search ? 1 : 0) +
    (filters.minScore > 0 ? 1 : 0) +
    (filters.maxPrice < 2500 ? 1 : 0) +
    (filters.onlyHighYield ? 1 : 0) +
    (filters.sources.length < ALL_SOURCES.length ? 1 : 0) +
    (filters.sortBy !== "score" ? 1 : 0);

  const hasActiveFilters = activeFilterCount > 0;

  return (
    <div className="py-2 mb-4">
      {/* Top Header / Accordion Trigger Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border border-[#222] bg-[#090909]/90 backdrop-blur-md px-4 py-2.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.03)]">
        
        {/* Left: Search input + Accordion toggle */}
        <div className="flex items-center gap-3 flex-1 min-w-[260px]">
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="flex items-center gap-2 px-2.5 py-1.5 bg-[#141414] hover:bg-[#1c1c1c] border border-[#2a2a2a] hover:border-emerald-500/40 text-[10px] font-bold uppercase tracking-widest text-[#bbb] hover:text-white transition-all rounded-sm group"
            title="Toggle Filter Panel"
          >
            <SlidersHorizontal className="h-3.5 w-3.5 text-emerald-500 group-hover:rotate-90 transition-transform duration-300" />
            <span>Filters</span>
            {hasActiveFilters && (
              <span className="px-1.5 py-0.2 rounded-full bg-emerald-500/20 border border-emerald-500/60 text-emerald-300 text-[9px] font-mono font-bold">
                {activeFilterCount} active
              </span>
            )}
            {isExpanded ? (
              <ChevronUp className="h-3.5 w-3.5 text-[#666]" />
            ) : (
              <ChevronDown className="h-3.5 w-3.5 text-[#666]" />
            )}
          </button>

          {/* Quick Search */}
          <div className="relative flex-1 max-w-xs">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-[#555]" />
            <input
              id="input-search-deals"
              type="text"
              value={filters.search}
              onChange={(e) => onChange({ ...filters, search: e.target.value })}
              placeholder="Search specs, models (e.g., P16, 64GB, M3 Max)..."
              className="w-full pl-9 pr-8 py-1.5 bg-[#0e0e0e] border border-[#2c2c2c] text-[11px] text-[#e2e8f0] placeholder-[#555] focus:outline-none focus:border-emerald-500 transition-colors"
            />
            {filters.search && (
              <button
                onClick={() => onChange({ ...filters, search: "" })}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[#666] hover:text-white transition-colors"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>
        </div>

        {/* Right: Quick View Switchers, High Yield, & Clear All */}
        <div className="flex items-center gap-2">
          
          {/* Quick High-Yield Toggle */}
          <button
            onClick={() => onChange({ ...filters, onlyHighYield: !filters.onlyHighYield })}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider border transition-all ${
              filters.onlyHighYield
                ? "bg-emerald-950/60 border-emerald-500 text-emerald-300 shadow-[0_0_10px_rgba(16,185,129,0.3)] laser-accent"
                : "bg-[#111] border-[#2a2a2a] text-[#888] hover:text-[#e2e8f0] hover:border-[#444]"
            }`}
            title="Toggle High-Yield Only"
          >
            <Flame className={`h-3.5 w-3.5 ${filters.onlyHighYield ? 'text-emerald-400 fill-emerald-400/20' : 'text-[#666]'}`} />
            <span>High Yield</span>
          </button>

          {/* View Mode */}
          <div className="flex items-center bg-[#0e0e0e] border border-[#2a2a2a] p-0.5">
            <button
              onClick={() => onChange({ ...filters, viewMode: "grid" })}
              className={`p-1.5 transition-all ${
                filters.viewMode === "grid"
                  ? "bg-[#1f1f1f] text-emerald-400 border border-emerald-500/30"
                  : "text-[#666] hover:text-[#e2e8f0]"
              }`}
              title="Card Grid View"
            >
              <LayoutGrid className="h-3.5 w-3.5" />
            </button>
            <button
              onClick={() => onChange({ ...filters, viewMode: "table" })}
              className={`p-1.5 transition-all ${
                filters.viewMode === "table"
                  ? "bg-[#1f1f1f] text-emerald-400 border border-emerald-500/30"
                  : "text-[#666] hover:text-[#e2e8f0]"
              }`}
              title="Data Table View"
            >
              <List className="h-3.5 w-3.5" />
            </button>
          </div>

          {/* Prominent Clear All Filters Button */}
          {hasActiveFilters && (
            <button
              onClick={onReset}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-rose-950/40 hover:bg-rose-900/60 text-rose-300 border border-rose-700/60 hover:border-rose-500 text-[10px] font-bold uppercase tracking-wider transition-all animate-in fade-in duration-200"
              title="Clear all active filters"
            >
              <RotateCcw className="h-3 w-3" />
              <span>Clear All</span>
            </button>
          )}
        </div>
      </div>

      {/* Expandable Accordion Body */}
      {isExpanded && (
        <div className="border-x border-b border-[#222] bg-[#070707]/90 backdrop-blur-md p-4 space-y-4 shadow-[0_10px_25px_rgba(0,0,0,0.5)] animate-in fade-in slide-in-from-top-2 duration-200">
          
          {/* Row 1: Source Whitelist Pills with Select All / Clear */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px] uppercase tracking-widest text-[#777] font-bold flex items-center gap-2">
                <span>Inventory Streams</span>
                <span className="text-emerald-400 font-mono">({filters.sources.length}/{ALL_SOURCES.length})</span>
              </span>
              <div className="flex items-center gap-2 text-[9px] font-bold uppercase tracking-widest">
                <button
                  onClick={selectAllSources}
                  className="text-[#666] hover:text-emerald-400 transition-colors"
                >
                  Select All
                </button>
                <span className="text-[#333]">|</span>
                <button
                  onClick={clearAllSources}
                  className="text-[#666] hover:text-rose-400 transition-colors"
                >
                  Clear All
                </button>
              </div>
            </div>

            <div className="flex items-center flex-wrap gap-1.5">
              {ALL_SOURCES.map((src) => {
                const active = filters.sources.includes(src.id);
                return (
                  <button
                    key={src.id}
                    onClick={() => toggleSource(src.id)}
                    className={`px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider transition-all duration-150 border rounded-xs ${
                      active
                        ? "bg-emerald-950/40 border-emerald-500 text-emerald-300 shadow-[0_0_8px_rgba(16,185,129,0.2)]"
                        : `bg-[#0e0e0e] border-[#222] text-[#666] ${src.color}`
                    }`}
                  >
                    <span className="mr-1.5">{active ? "✓" : "+"}</span>
                    {src.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Row 2: Sliders & Sorting Controls */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-3 border-t border-[#1a1a1a]">
            
            {/* Deal Score Slider */}
            <div>
              <div className="flex justify-between items-center text-[10px] mb-1.5">
                <span className="text-[#888] uppercase tracking-widest font-bold">Min Deal Score</span>
                <span className="text-emerald-400 font-mono font-bold">{filters.minScore.toFixed(1)} / 10.0</span>
              </div>
              <input
                id="slider-min-score"
                type="range"
                min="0"
                max="10"
                step="0.1"
                value={filters.minScore}
                onChange={(e) => onChange({ ...filters, minScore: parseFloat(e.target.value) })}
                className="w-full accent-emerald-500 bg-[#222] h-1.5 cursor-pointer appearance-none"
              />
            </div>

            {/* Max Price Slider */}
            <div>
              <div className="flex justify-between items-center text-[10px] mb-1.5">
                <span className="text-[#888] uppercase tracking-widest font-bold">Max Asking Price</span>
                <span className="text-emerald-400 font-mono font-bold">${filters.maxPrice}</span>
              </div>
              <input
                id="slider-max-price"
                type="range"
                min="200"
                max="2500"
                step="25"
                value={filters.maxPrice}
                onChange={(e) => onChange({ ...filters, maxPrice: parseInt(e.target.value, 10) })}
                className="w-full accent-emerald-500 bg-[#222] h-1.5 cursor-pointer appearance-none"
              />
            </div>

            {/* Sort Order Selector */}
            <div>
              <span className="text-[10px] uppercase tracking-widest text-[#888] font-bold block mb-1.5">
                Sort Order
              </span>
              <select
                value={filters.sortBy}
                onChange={(e) => onChange({ ...filters, sortBy: e.target.value as any })}
                className="w-full px-3 py-1.5 bg-[#0e0e0e] border border-[#2c2c2c] text-[11px] uppercase tracking-wider text-[#e2e8f0] focus:outline-none focus:border-emerald-500 transition-colors"
              >
                <option value="score">Score (Highest First)</option>
                <option value="profit">Arbitrage Spread ($ Highest)</option>
                <option value="price_low">Asking Price (Lowest First)</option>
                <option value="newest">Evaluation Date (Newest First)</option>
              </select>
            </div>

          </div>

          {/* Active Chips Summary Row (if filters active) */}
          {hasActiveFilters && (
            <div className="pt-2 border-t border-[#1a1a1a] flex items-center flex-wrap gap-2 text-[10px]">
              <span className="text-[#666] uppercase tracking-widest font-bold text-[9px]">Active Filters:</span>
              
              {filters.search && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-[#151515] border border-[#333] text-[#ccc]">
                  Query: "{filters.search}"
                  <button onClick={() => onChange({ ...filters, search: "" })} className="hover:text-rose-400"><X className="h-3 w-3" /></button>
                </span>
              )}

              {filters.onlyHighYield && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-emerald-950/50 border border-emerald-600/50 text-emerald-300">
                  🔥 High-Yield Only
                  <button onClick={() => onChange({ ...filters, onlyHighYield: false })} className="hover:text-rose-400"><X className="h-3 w-3" /></button>
                </span>
              )}

              {filters.minScore > 0 && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-[#151515] border border-[#333] text-[#ccc]">
                  Score &ge; {filters.minScore.toFixed(1)}
                  <button onClick={() => onChange({ ...filters, minScore: 0 })} className="hover:text-rose-400"><X className="h-3 w-3" /></button>
                </span>
              )}

              {filters.maxPrice < 2500 && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-[#151515] border border-[#333] text-[#ccc]">
                  Price &le; ${filters.maxPrice}
                  <button onClick={() => onChange({ ...filters, maxPrice: 2500 })} className="hover:text-rose-400"><X className="h-3 w-3" /></button>
                </span>
              )}

              {filters.sources.length < ALL_SOURCES.length && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-[#151515] border border-[#333] text-[#ccc]">
                  {filters.sources.length} Sources Selected
                  <button onClick={selectAllSources} className="hover:text-rose-400"><X className="h-3 w-3" /></button>
                </span>
              )}

              <button
                onClick={onReset}
                className="text-[9px] text-rose-400 hover:underline uppercase tracking-wider ml-auto font-bold"
              >
                Reset All Parameters
              </button>
            </div>
          )}

        </div>
      )}
    </div>
  );
};
