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
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 mb-6 shadow-sm">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        {/* Search Bar */}
        <div className="relative flex-1 min-w-[240px]">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            id="input-search-deals"
            type="text"
            value={filters.search}
            onChange={(e) => onChange({ ...filters, search: e.target.value })}
            placeholder="Search specs, model (e.g., P16, RTX, 64GB, Ada, i9)..."
            className="w-full pl-10 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500 transition"
          />
          {filters.search && (
            <button
              onClick={() => onChange({ ...filters, search: "" })}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Source Badges */}
        <div className="flex items-center flex-wrap gap-2">
          <span className="text-xs text-slate-400 font-medium mr-1">Sources:</span>
          {[
            { id: "ebay", label: "eBay API", color: "hover:border-blue-500" },
            { id: "reddit", label: "r/hws", color: "hover:border-orange-500" },
            { id: "swappa", label: "Swappa", color: "hover:border-teal-500" },
            { id: "syndicated", label: "RSS Feeds", color: "hover:border-yellow-500" },
            { id: "dell_refurbished", label: "Dell Refurb", color: "hover:border-blue-400" },
            { id: "microcenter", label: "Microcenter", color: "hover:border-rose-500" },
            { id: "bh_photo", label: "B&H", color: "hover:border-green-500" },
            { id: "goodwill", label: "Goodwill", color: "hover:border-indigo-500" },
            { id: "lenovo_outlet", label: "Lenovo", color: "hover:border-red-500" },
          ].map((src) => {
            const active = filters.sources.includes(src.id);
            return (
              <button
                key={src.id}
                onClick={() => toggleSource(src.id)}
                className={`px-3 py-1 rounded-lg text-xs font-semibold border transition ${
                  active
                    ? "bg-slate-800 border-emerald-500/60 text-emerald-400 shadow-sm"
                    : "bg-slate-950/60 border-slate-800 text-slate-500 hover:text-slate-300"
                } ${src.color}`}
              >
                {src.label}
              </button>
            );
          })}
        </div>

        {/* High Yield Switch & View Modes */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => onChange({ ...filters, onlyHighYield: !filters.onlyHighYield })}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition ${
              filters.onlyHighYield
                ? "bg-emerald-950/70 border-emerald-500 text-emerald-300"
                : "bg-slate-950 border-slate-800 text-slate-400 hover:text-slate-200"
            }`}
          >
            <Flame className="h-3.5 w-3.5 text-emerald-400" />
            <span>High-Yield Only (&ge;8.5)</span>
          </button>

          <div className="flex items-center bg-slate-950 border border-slate-800 rounded-lg p-0.5">
            <button
              onClick={() => onChange({ ...filters, viewMode: "grid" })}
              className={`p-1.5 rounded-md transition ${
                filters.viewMode === "grid" ? "bg-slate-800 text-emerald-400" : "text-slate-400 hover:text-slate-200"
              }`}
              title="Grid View"
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
            <button
              onClick={() => onChange({ ...filters, viewMode: "table" })}
              className={`p-1.5 rounded-md transition ${
                filters.viewMode === "table" ? "bg-slate-800 text-emerald-400" : "text-slate-400 hover:text-slate-200"
              }`}
              title="Dense Table View"
            >
              <List className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Sliders Row */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-4 pt-4 border-t border-slate-800/80">
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
