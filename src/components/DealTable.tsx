import React, { useState, useMemo } from "react";
import { ExternalLink, Bell, Trash2, ArrowUpDown, ArrowUp, ArrowDown, Clock } from "lucide-react";
import { DealRecord } from "../types";

export type TableSortField = "score" | "title" | "cpu" | "ram" | "price" | "fmv" | "spread" | "date";
export type TableSortDirection = "asc" | "desc";

function formatRelativeTime(dateStr?: string): string {
  if (!dateStr) return "Recently";
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return "Recently";
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMinutes = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMinutes < 5) return "Just now";
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays === 1) return "Yesterday";
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

interface DealTableProps {
  deals: DealRecord[];
  onSendPush: (deal: DealRecord) => Promise<void>;
  onDeleteDeal?: (dealId: string) => void;
}

export const DealTable: React.FC<DealTableProps> = ({ deals, onSendPush, onDeleteDeal }) => {
  const [sortField, setSortField] = useState<TableSortField>("score");
  const [sortDirection, setSortDirection] = useState<TableSortDirection>("desc");

  const handleHeaderClick = (field: TableSortField) => {
    if (sortField === field) {
      setSortDirection((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      // Default to ascending for price/title, descending for scores and profits/date
      setSortDirection(field === "price" || field === "title" ? "asc" : "desc");
    }
  };

  const sortedDeals = useMemo(() => {
    return [...deals].sort((a, b) => {
      let comparison = 0;
      switch (sortField) {
        case "score":
          comparison = (a.deal_score || 0) - (b.deal_score || 0);
          break;
        case "title":
          comparison = (a.title || "").localeCompare(b.title || "");
          break;
        case "cpu":
          comparison = (a.specs?.cpu || "").localeCompare(b.specs?.cpu || "");
          break;
        case "ram":
          comparison = (a.specs?.ram_gb || 0) - (b.specs?.ram_gb || 0);
          break;
        case "price":
          comparison = (a.price || 0) - (b.price || 0);
          break;
        case "fmv":
          comparison = (a.fair_market_value || 0) - (b.fair_market_value || 0);
          break;
        case "spread":
          comparison = (a.estimated_profit || 0) - (b.estimated_profit || 0);
          break;
        case "date": {
          const dateA = new Date(a.created_utc || a.evaluated_at || 0).getTime();
          const dateB = new Date(b.created_utc || b.evaluated_at || 0).getTime();
          comparison = dateA - dateB;
          break;
        }
        default:
          comparison = 0;
      }
      return sortDirection === "asc" ? comparison : -comparison;
    });
  }, [deals, sortField, sortDirection]);

  const renderSortIndicator = (field: TableSortField) => {
    if (sortField !== field) {
      return <ArrowUpDown className="h-3 w-3 text-slate-600 opacity-60 group-hover:opacity-100 transition inline-block ml-1" />;
    }
    return sortDirection === "asc" ? (
      <ArrowUp className="h-3 w-3 text-emerald-400 inline-block ml-1" />
    ) : (
      <ArrowDown className="h-3 w-3 text-emerald-400 inline-block ml-1" />
    );
  };

  const getHeaderClass = (field: TableSortField, extra: string = "") => {
    const isActive = sortField === field;
    return `px-4 py-3 cursor-pointer select-none transition group hover:text-emerald-300 ${
      isActive ? "text-emerald-400 font-bold bg-slate-900/60" : "text-slate-400"
    } ${extra}`;
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950/80 uppercase tracking-wider text-[11px] border-b border-slate-800 font-semibold">
            <tr>
              <th
                className={getHeaderClass("score")}
                onClick={() => handleHeaderClick("score")}
                title="Click to sort by Deal Score"
              >
                <div className="flex items-center gap-1">
                  Score {renderSortIndicator("score")}
                </div>
              </th>
              <th
                className={getHeaderClass("title")}
                onClick={() => handleHeaderClick("title")}
                title="Click to sort by Title"
              >
                <div className="flex items-center gap-1">
                  Listing Title &amp; Source {renderSortIndicator("title")}
                </div>
              </th>
              <th
                className={getHeaderClass("date")}
                onClick={() => handleHeaderClick("date")}
                title="Click to sort by Date Found / Added"
              >
                <div className="flex items-center gap-1">
                  Date Found {renderSortIndicator("date")}
                </div>
              </th>
              <th
                className={getHeaderClass("cpu")}
                onClick={() => handleHeaderClick("cpu")}
                title="Click to sort by Processor"
              >
                <div className="flex items-center gap-1">
                  CPU / GPU {renderSortIndicator("cpu")}
                </div>
              </th>
              <th
                className={getHeaderClass("ram")}
                onClick={() => handleHeaderClick("ram")}
                title="Click to sort by Memory capacity"
              >
                <div className="flex items-center gap-1">
                  RAM / SSD {renderSortIndicator("ram")}
                </div>
              </th>
              <th
                className={getHeaderClass("price", "text-right")}
                onClick={() => handleHeaderClick("price")}
                title="Click to sort by Asking Price"
              >
                <div className="flex items-center justify-end gap-1">
                  Price {renderSortIndicator("price")}
                </div>
              </th>
              <th
                className={getHeaderClass("fmv", "text-right")}
                onClick={() => handleHeaderClick("fmv")}
                title="Click to sort by Fair Market Value"
              >
                <div className="flex items-center justify-end gap-1">
                  Est. FMV {renderSortIndicator("fmv")}
                </div>
              </th>
              <th
                className={getHeaderClass("spread", "text-right")}
                onClick={() => handleHeaderClick("spread")}
                title="Click to sort by Arbitrage Spread"
              >
                <div className="flex items-center justify-end gap-1">
                  Arbitrage Spread {renderSortIndicator("spread")}
                </div>
              </th>
              <th className="px-4 py-3 text-center text-slate-400">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {sortedDeals.map((deal) => {
              const isHigh = deal.deal_score >= 8.5;
              const dateFoundStr = deal.created_utc || deal.evaluated_at;
              return (
                <tr
                  key={deal.id}
                  className="hover:bg-slate-800/40 transition duration-150 group"
                >
                  {/* Score */}
                  <td className="px-4 py-3 whitespace-nowrap">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-bold ${
                        isHigh
                          ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30"
                          : "bg-slate-800 text-slate-400"
                      }`}
                    >
                      {deal.deal_score.toFixed(1)}
                    </span>
                  </td>

                  {/* Title & Source */}
                  <td className="px-4 py-3 max-w-[280px]">
                    <div className="font-semibold text-slate-100 truncate group-hover:text-emerald-300 transition">
                      {deal.title}
                    </div>
                    <div className="text-[11px] text-slate-500 flex items-center gap-1.5 mt-0.5">
                      <span className="uppercase font-bold text-slate-400">{deal.source}</span>
                      <span>•</span>
                      <span>{deal.seller}</span>
                    </div>
                  </td>

                  {/* Date Found / Added */}
                  <td className="px-4 py-3 whitespace-nowrap" title={dateFoundStr || ""}>
                    <div className="flex items-center gap-1 text-slate-300 font-medium">
                      <Clock className="h-3 w-3 text-slate-500" />
                      <span>{formatRelativeTime(dateFoundStr)}</span>
                    </div>
                    <div className="text-[10px] text-slate-500">
                      {dateFoundStr ? new Date(dateFoundStr).toLocaleDateString("en-US", { month: "short", day: "numeric" }) : "Today"}
                    </div>
                  </td>

                  {/* CPU / GPU */}
                  <td className="px-4 py-3 max-w-[180px]">
                    <div className="font-medium text-slate-200 truncate">{deal.specs.cpu}</div>
                    <div className="text-[11px] text-slate-400 truncate">{deal.specs.gpu}</div>
                  </td>

                  {/* RAM / SSD */}
                  <td className="px-4 py-3 whitespace-nowrap">
                    <span className="font-medium text-slate-200">{deal.specs.ram_gb} GB</span>
                    <span className="text-slate-500"> / </span>
                    <span className="text-slate-400">{deal.specs.ssd_gb} GB</span>
                  </td>

                  {/* Price */}
                  <td className="px-4 py-3 text-right whitespace-nowrap font-bold text-white">
                    ${deal.price.toFixed(0)}
                  </td>

                  {/* FMV */}
                  <td className="px-4 py-3 text-right whitespace-nowrap font-medium text-slate-400">
                    ${deal.fair_market_value.toFixed(0)}
                  </td>

                  {/* Spread */}
                  <td className="px-4 py-3 text-right whitespace-nowrap font-bold text-emerald-400">
                    +${deal.estimated_profit.toFixed(0)}
                    <span className="text-[10px] text-emerald-500/70 ml-1 font-normal">
                      (+{deal.arbitrage_margin_pct.toFixed(0)}%)
                    </span>
                  </td>

                  {/* Links, Push & Delete */}
                  <td className="px-4 py-3 text-center whitespace-nowrap">
                    <div className="flex items-center justify-center gap-1.5">
                      <a
                        href={deal.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="p-1.5 rounded-md bg-emerald-600/80 hover:bg-emerald-500 text-white transition"
                        title="Open listing URL"
                      >
                        <ExternalLink className="h-3.5 w-3.5" />
                      </a>
                      <button
                        onClick={() => onSendPush(deal)}
                        className="p-1.5 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
                        title="Send Pushover notification"
                      >
                        <Bell className="h-3.5 w-3.5 text-amber-400" />
                      </button>
                      {onDeleteDeal && (
                        <button
                          onClick={() => onDeleteDeal(deal.id)}
                          className="p-1.5 rounded-md bg-rose-950/40 hover:bg-rose-900/80 text-rose-400 hover:text-rose-200 border border-rose-900/50 transition"
                          title="Delete / Dismiss deal from list"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
