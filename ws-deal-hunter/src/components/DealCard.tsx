import React, { useState } from "react";
import { ExternalLink, Bell, CheckCircle2, Cpu, HardDrive, Monitor, Shield, Sparkles } from "lucide-react";
import { DealRecord } from "../types";

interface DealCardProps {
  deal: DealRecord;
  onSendPush: (deal: DealRecord) => Promise<void>;
}

export const DealCard: React.FC<DealCardProps> = ({ deal, onSendPush }) => {
  const [isSending, setIsSending] = useState(false);
  const [pushSent, setPushSent] = useState(deal.alerted);

  const handlePush = async () => {
    setIsSending(true);
    try {
      await onSendPush(deal);
      setPushSent(true);
    } finally {
      setIsSending(false);
    }
  };

  // Score styling logic
  const isElite = deal.deal_score >= 9.0;
  const isHigh = deal.deal_score >= 8.5;
  const isGood = deal.deal_score >= 7.5;

  const scoreBadgeBg = isElite
    ? "bg-emerald-500/20 text-emerald-300 border-emerald-500/40 shadow-emerald-500/10 shadow-lg"
    : isHigh
    ? "bg-teal-500/20 text-teal-300 border-teal-500/40"
    : isGood
    ? "bg-amber-500/20 text-amber-300 border-amber-500/40"
    : "bg-slate-800 text-slate-400 border-slate-700";

  const sourceBadgeColors = {
    ebay: "bg-blue-950/60 text-blue-400 border-blue-800/60",
    reddit: "bg-orange-950/60 text-orange-400 border-orange-800/60",
    swappa: "bg-teal-950/60 text-teal-400 border-teal-800/60",
    manual: "bg-purple-950/60 text-purple-400 border-purple-800/60",
  }[deal.source] || "bg-slate-800 text-slate-400 border-slate-700";

  return (
    <div
      id={`deal-card-${deal.id}`}
      className={`bg-slate-900 border rounded-xl p-5 flex flex-col justify-between transition hover:border-slate-700 relative overflow-hidden group ${
        deal.is_high_yield
          ? "border-emerald-500/30 hover:border-emerald-500/50 shadow-sm shadow-emerald-950/30"
          : "border-slate-800 hover:border-slate-700"
      }`}
    >
      {/* Top Banner: Source & Score Badge */}
      <div>
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex items-center gap-2 flex-wrap">
            <span
              className={`px-2.5 py-0.5 rounded-md text-[11px] font-bold border uppercase tracking-wider ${sourceBadgeColors}`}
            >
              {deal.source === "reddit" ? "r/hardwareswap" : deal.source}
            </span>
            {deal.is_high_yield && (
              <span className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider">
                ⚡ Alert Qualified
              </span>
            )}
            <span className="text-[11px] text-slate-400 truncate max-w-[120px]">
              {deal.seller}
            </span>
          </div>

          <div
            className={`flex items-center gap-1 px-3 py-1 rounded-lg text-sm font-extrabold border ${scoreBadgeBg}`}
          >
            <span>{deal.deal_score.toFixed(1)}</span>
            <span className="text-[10px] opacity-70">/10</span>
          </div>
        </div>

        {/* Title */}
        <h3 className="text-base font-bold text-slate-100 line-clamp-2 leading-snug mb-3 group-hover:text-emerald-300 transition">
          {deal.title}
        </h3>

        {/* Price & Arbitrage Profit Box */}
        <div className="bg-slate-950/80 border border-slate-800/80 rounded-lg p-3 mb-4">
          <div className="flex items-baseline justify-between mb-1.5">
            <div>
              <span className="text-[10px] uppercase font-semibold text-slate-400 block">
                Asking Price
              </span>
              <span className="text-xl font-black text-white">
                ${deal.price.toFixed(0)}
              </span>
            </div>
            <div className="text-right">
              <span className="text-[10px] uppercase font-semibold text-slate-400 block">
                Fair Market Value
              </span>
              <span className="text-sm font-semibold text-slate-300">
                ${deal.fair_market_value.toFixed(0)}
              </span>
            </div>
          </div>

          {/* Arbitrage Spread Bar */}
          <div className="pt-2 border-t border-slate-800 flex items-center justify-between text-xs">
            <span className="text-slate-400 font-medium">Arbitrage Spread:</span>
            <span className="font-bold text-emerald-400 flex items-center gap-1">
              +${deal.estimated_profit.toFixed(0)}
              <span className="text-[10px] text-emerald-500/80">
                (+{deal.arbitrage_margin_pct.toFixed(0)}% ROI)
              </span>
            </span>
          </div>
        </div>

        {/* Specs Grid */}
        <div className="space-y-1.5 text-xs mb-4">
          <div className="flex items-center gap-2 text-slate-300">
            <Cpu className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            <span className="font-medium truncate">{deal.specs.cpu || "N/A"}</span>
          </div>
          <div className="flex items-center gap-2 text-slate-300">
            <HardDrive className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            <span className="font-medium truncate">
              {deal.specs.ram_gb} GB RAM • {deal.specs.ssd_gb} GB SSD NVMe
            </span>
          </div>
          <div className="flex items-center gap-2 text-slate-300">
            <Sparkles className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
            <span className="font-medium truncate">{deal.specs.gpu || "Integrated"}</span>
          </div>
          <div className="flex items-center gap-2 text-slate-400">
            <Monitor className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            <span className="truncate">{deal.specs.screen || "Standard Screen"}</span>
          </div>
        </div>

        {/* AI Valuation Recommendation */}
        <div className="text-xs text-slate-400 bg-slate-950/40 p-2.5 rounded-lg border border-slate-800/60 mb-4">
          <div className="flex items-center gap-1 text-[11px] font-bold text-slate-300 mb-1">
            <Shield className="h-3 w-3 text-emerald-400" />
            <span>AI Valuation &amp; Action:</span>
          </div>
          <p className="line-clamp-2 text-[11px] text-slate-300 leading-relaxed">
            {deal.summary}
          </p>
          <div className="mt-1 text-[11px] font-bold text-emerald-400">
            &rarr; {deal.actionable_recommendation}
          </div>
        </div>
      </div>

      {/* Card Action Buttons */}
      <div className="flex items-center gap-2 pt-2 border-t border-slate-800/80">
        <a
          href={deal.url}
          target="_blank"
          rel="noopener noreferrer"
          className="flex-1 inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold transition duration-150 active:scale-95 shadow-sm"
        >
          <span>Open Listing</span>
          <ExternalLink className="h-3.5 w-3.5" />
        </a>

        <button
          onClick={handlePush}
          disabled={isSending}
          className={`inline-flex items-center justify-center p-2 rounded-lg text-xs font-semibold border transition duration-150 active:scale-95 ${
            pushSent
              ? "bg-slate-800 border-emerald-500/40 text-emerald-400"
              : "bg-slate-800 hover:bg-slate-700 border-slate-700 text-slate-300"
          }`}
          title="Send instant mobile push notification via Pushover"
        >
          {pushSent ? (
            <CheckCircle2 className="h-4 w-4 text-emerald-400" />
          ) : (
            <Bell className={`h-4 w-4 ${isSending ? "animate-pulse text-amber-400" : ""}`} />
          )}
        </button>
      </div>
    </div>
  );
};
