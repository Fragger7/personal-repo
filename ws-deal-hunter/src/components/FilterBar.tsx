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
    <div className="py-4">
      <div className="flex flex-wrap items-center gap-4 lg:gap-6 border border-[#222] bg-[#080808]/80 backdrop-blur-sm p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]">
        {/* Search Bar */}
        <div className="relative min-w-[200px] flex-1 lg:flex-none lg:w-64">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[#555]" />
          <input
            id="input-search-deals"
            type="text"
            value={filters.search}
            onChange={(e) => onChange({ ...filters, search: e.target.value })}
            placeholder="Search specs, model..."
            className="w-full pl-11 pr-4 py-2 bg-[#0a0a0a] border border-[#333] text-[12px] text-[#e2e8f0] placeholder-[#555] focus:outline-none focus:border-emerald-500 transition-colors shadow-inner"
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
        <div className="flex items-center flex-wrap gap-1.5 flex-1 min-w-[200px]">
          {[
            { id: "ebay", label: "eBay", color: "hover:border-blue-500/50 hover:text-blue-500" },
            { id: "reddit", label: "r/hws", color: "hover:border-orange-500/50 hover:text-orange-500" },
            { id: "swappa", label: "Swappa", color: "hover:border-teal-500/50 hover:text-teal-500" },
            { id: "syndicated", label: "RSS", color: "hover:border-yellow-500/50 hover:text-yellow-500" },
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
                className={`px-2 py-1 text-[9px] font-bold uppercase tracking-wider transition-all duration-200 border ${
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

        {/* Sliders (Min Score & Max Price) */}
        <div className="flex items-center gap-6 flex-1 min-w-[200px]">
          <div className="flex-1">
            <div className="flex justify-between items-center text-[10px] mb-1">
              <span className="text-[#666] uppercase tracking-widest font-bold">Min Score</span>
              <span className="text-emerald-500 tech-text">{filters.minScore.toFixed(1)}</span>
            </div>
            <input
              id="slider-min-score"
              type="range"
              min="0"
              max="10"
              step="0.1"
              value={filters.minScore}
              onChange={(e) => onChange({ ...filters, minScore: parseFloat(e.target.value) })}
              className="w-full accent-emerald-500 bg-[#333] h-1 appearance-none cursor-pointer"
            />
          </div>
          <div className="flex-1">
            <div className="flex justify-between items-center text-[10px] mb-1">
              <span className="text-[#666] uppercase tracking-widest font-bold">Max Price</span>
              <span className="text-emerald-500 tech-text">${filters.maxPrice}</span>
            </div>
            <input
              id="slider-max-price"
              type="range"
              min="200"
              max="2500"
              step="25"
              value={filters.maxPrice}
              onChange={(e) => onChange({ ...filters, maxPrice: parseInt(e.target.value, 10) })}
              className="w-full accent-emerald-500 bg-[#333] h-1 appearance-none cursor-pointer"
            />
          </div>
        </div>

        {/* Controls block (Sort, Yield, Reset, View) */}
        <div className="flex items-center gap-3">
          <select
            value={filters.sortBy}
            onChange={(e) => onChange({ ...filters, sortBy: e.target.value as any })}
            className="px-2 py-1.5 bg-[#0a0a0a] border border-[#333] text-[10px] uppercase tracking-widest text-[#e2e8f0] focus:outline-none focus:border-emerald-500"
          >
            <option value="score">Sort: Highest Score</option>
            <option value="profit">Sort: Largest Profit</option>
            <option value="price_low">Sort: Lowest Price</option>
            <option value="newest">Sort: Newest</option>
          </select>

          <button
            onClick={() => onChange({ ...filters, onlyHighYield: !filters.onlyHighYield })}
            className={`flex items-center justify-center p-1.5 border transition-all ${
              filters.onlyHighYield
                ? "bg-[#111] border-emerald-500/50 text-emerald-400 laser-accent"
                : "bg-transparent border-[#222] text-[#666] hover:text-[#e2e8f0] hover:border-[#444]"
            }`}
            title="High Yield Only"
          >
            <Flame className={`h-4 w-4 ${filters.onlyHighYield ? 'text-emerald-500' : 'text-[#666]'}`} />
          </button>

          <div className="flex items-center bg-[#0a0a0a] border border-[#222]">
            <button
              onClick={() => onChange({ ...filters, viewMode: "grid" })}
              className={`p-1.5 transition-all ${
                filters.viewMode === "grid" ? "bg-[#111] text-emerald-500 border border-[#333]" : "text-[#555] hover:text-[#e2e8f0] border border-transparent"
              }`}
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
            <button
              onClick={() => onChange({ ...filters, viewMode: "table" })}
              className={`p-1.5 transition-all ${
                filters.viewMode === "table" ? "bg-[#111] text-emerald-500 border border-[#333]" : "text-[#555] hover:text-[#e2e8f0] border border-transparent"
              }`}
            >
              <List className="h-4 w-4" />
            </button>
          </div>

          <button
            onClick={onReset}
            className="p-1.5 text-[#555] hover:text-[#e2e8f0] border border-transparent hover:border-[#444] transition-all bg-[#0a0a0a]"
            title="Reset Filters"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
