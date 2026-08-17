import React from "react";
import { Search, SlidersHorizontal, LayoutGrid, List, Flame, X } from "lucide-react";
import { FilterState } from "../types";

interface FilterBarProps {
  filters: FilterState;
  onChange: (newFilters: FilterState) => void;
  onReset: () => void;
}

export const FilterBar: React.FC<FilterBarProps> = ({ filters, onChange, onReset }) => {
  const toggleSource = (src: string) => {
    const next = filters.sources.includes(src)
      ? filters.sources.filter((s) => s !== src)
      : [...filters.sources, src];
    onChange({ ...filters, sources: next });
  };

  return (
    <div className="industrial-panel p-6 mb-8">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        {/* Search Bar */}
        <div className="relative flex-1 min-w-[240px]">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[#555]" />
          <input
            id="input-search-deals"
            type="text"
            value={filters.search}
            onChange={(e) => onChange({ ...filters, search: e.target.value })}
            placeholder="Search specs, model (e.g., P16, RTX, 64GB, Ada, i9)..."
            className="w-full pl-11 pr-4 py-2.5 bg-[#0a0a0a] border border-[#333] text-[13px] text-[#e2e8f0] placeholder-[#555] focus:outline-none focus:border-emerald-500 transition-colors shadow-inner"
          />
          {filters.search && (
            <button
              onClick={() => onChange({ ...filters, search: "" })}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-[#555] hover:text-[#e2e8f0] transition-colors"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Source Badges */}
        <div className="flex items-center flex-wrap gap-2 lg:justify-end">
          <span className="text-[10px] uppercase tracking-widest text-[#555] font-bold mr-2">Sources</span>
          {[
            { id: "ebay", label: "eBay", color: "hover:border-blue-500/50 hover:text-blue-500" },
            { id: "reddit", label: "r/hws", color: "hover:border-orange-500/50 hover:text-orange-500" },
            { id: "swappa", label: "Swappa", color: "hover:border-teal-500/50 hover:text-teal-500" },
            { id: "syndicated", label: "RSS Feeds", color: "hover:border-yellow-500/50 hover:text-yellow-500" },
            { id: "dell_refurbished", label: "Dell", color: "hover:border-blue-400/50 hover:text-blue-400" },
            { id: "microcenter", label: "Microcenter", color: "hover:border-rose-500/50 hover:text-rose-500" },
            { id: "bh_photo", label: "B&H", color: "hover:border-green-500/50 hover:text-green-500" },
            { id: "goodwill", label: "Goodwill", color: "hover:border-indigo-500/50 hover:text-indigo-500" },
            { id: "lenovo_outlet", label: "Lenovo", color: "hover:border-red-500/50 hover:text-red-500" },
          ].map((src) => {
            const active = filters.sources.includes(src.id);
            return (
              <button
                key={src.id}
                onClick={() => toggleSource(src.id)}
                className={`px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider transition-all duration-200 border ${
                  active
                    ? "bg-[#111] border-emerald-500/50 text-emerald-400"
                    : `bg-transparent border-[#222] text-[#666] ${src.color}`
                }`}
              >
                {src.label}
              </button>
            );
          })}
        </div>

        {/* High Yield Switch & View Modes */}
        <div className="flex items-center gap-4">
          <button
            onClick={() => onChange({ ...filters, onlyHighYield: !filters.onlyHighYield })}
            className={`inline-flex items-center gap-2 px-4 py-2 text-[10px] font-bold uppercase tracking-wider border transition-all ${
              filters.onlyHighYield
                ? "bg-[#111] border-emerald-500/50 text-emerald-400 laser-accent"
                : "bg-transparent border-[#222] text-[#666] hover:text-[#e2e8f0] hover:border-[#444]"
            }`}
          >
            <Flame className={`h-3 w-3 ${filters.onlyHighYield ? 'text-emerald-500' : 'text-[#666]'}`} />
            <span>High-Yield</span>
          </button>

          <div className="flex items-center bg-[#0a0a0a] border border-[#222] p-1">
            <button
              onClick={() => onChange({ ...filters, viewMode: "grid" })}
              className={`p-1.5 transition-all ${
                filters.viewMode === "grid" ? "bg-[#111] text-emerald-500 border border-[#333]" : "text-[#555] hover:text-[#e2e8f0] border border-transparent"
              }`}
              title="Grid View"
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
            <button
              onClick={() => onChange({ ...filters, viewMode: "table" })}
              className={`p-1.5 transition-all ${
                filters.viewMode === "table" ? "bg-[#111] text-emerald-500 border border-[#333]" : "text-[#555] hover:text-[#e2e8f0] border border-transparent"
              }`}
              title="Dense Table View"
            >
              <List className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Sliders Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 mt-6 pt-6 border-t border-[#222]">
        {/* Deal Score Slider */}
        <div>
          <div className="flex justify-between items-center text-xs mb-1.5">
            <span className="text-slate-400 font-medium">Min Deal Score:</span>
            <span className="text-emerald-400 font-bold">{filters.minScore.toFixed(1)} / 10.0</span>
          </div>
          <input
            id="slider-min-score"
            type="range"
            min="0"
            max="10"
            step="0.1"
            value={filters.minScore}
            onChange={(e) => onChange({ ...filters, minScore: parseFloat(e.target.value) })}
            className="w-full accent-emerald-500 bg-slate-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* Max Price Slider */}
        <div>
          <div className="flex justify-between items-center text-xs mb-1.5">
            <span className="text-slate-400 font-medium">Max Asking Price:</span>
            <span className="text-emerald-400 font-bold">${filters.maxPrice}</span>
          </div>
          <input
            id="slider-max-price"
            type="range"
            min="200"
            max="2500"
            step="25"
            value={filters.maxPrice}
            onChange={(e) => onChange({ ...filters, maxPrice: parseInt(e.target.value, 10) })}
            className="w-full accent-emerald-500 bg-slate-800 h-1.5 rounded-lg appearance-none cursor-pointer"
          />
        </div>

        {/* Sort Selector */}
        <div className="flex items-center justify-between gap-2">
          <div className="flex-1">
            <span className="text-xs text-slate-400 font-medium block mb-1.5">Sort Order:</span>
            <select
              value={filters.sortBy}
              onChange={(e) => onChange({ ...filters, sortBy: e.target.value as any })}
              className="w-full px-3 py-1.5 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
            >
              <option value="score">Highest Deal Score</option>
              <option value="profit">Largest Arbitrage Profit ($)</option>
              <option value="price_low">Lowest Asking Price</option>
              <option value="newest">Recently Evaluated</option>
            </select>
          </div>

          <button
            onClick={onReset}
            className="mt-5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:text-slate-200 border border-slate-800 hover:bg-slate-800 transition"
          >
            Reset
          </button>
        </div>
      </div>
    </div>
  );
};
