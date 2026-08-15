import React from "react";
import { ExternalLink, Bell, ArrowUpRight } from "lucide-react";
import { DealRecord } from "../types";

interface DealTableProps {
  deals: DealRecord[];
  onSendPush: (deal: DealRecord) => Promise<void>;
}

export const DealTable: React.FC<DealTableProps> = ({ deals, onSendPush }) => {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800 font-semibold">
            <tr>
              <th className="px-4 py-3">Score</th>
              <th className="px-4 py-3">Listing Title &amp; Source</th>
              <th className="px-4 py-3">CPU / GPU</th>
              <th className="px-4 py-3">RAM / SSD</th>
              <th className="px-4 py-3 text-right">Price</th>
              <th className="px-4 py-3 text-right">Est. FMV</th>
              <th className="px-4 py-3 text-right">Arbitrage Spread</th>
              <th className="px-4 py-3 text-center">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {deals.map((deal) => {
              const isHigh = deal.deal_score >= 8.5;
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

                  {/* Links & Push */}
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
