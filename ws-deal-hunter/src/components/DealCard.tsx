import React, { useState } from "react";
import { ExternalLink, Bell, CheckCircle2, Cpu, HardDrive, Monitor, Shield, Sparkles, Flame } from "lucide-react";
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
      className={`glass-card rounded-2xl p-6 flex flex-col justify-between relative overflow-hidden group ${
        deal.is_high_yield
          ? "border-emerald-500/30 shadow-lg shadow-emerald-950/20 hover:shadow-emerald-950/40"
          : "shadow-lg shadow-black/20"
      }`}
    >
      {/* Decorative gradient orb for high yield deals */}
      {deal.is_high_yield && (
        <div className="absolute -top-24 -right-24 w-48 h-48 bg-emerald-500/10 rounded-full blur-3xl group-hover:bg-emerald-500/20 transition-all duration-500 pointer-events-none" />
      )}

      {/* Top Banner: Source & Score Badge */}
      <div className="relative z-10">
        <div className="flex items-start justify-between gap-3 mb-4">
          <div className="flex items-center gap-2 flex-wrap">
            <span
              className={`px-2.5 py-1 rounded-[8px] text-[10px] font-bold border uppercase tracking-widest ${sourceBadgeColors}`}
            >
              {deal.source.replace("reddit (r/hardwareswap)", "r/hws").substring(0, 15)}
            </span>
            {deal.is_high_yield && (
              <span className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2.5 py-1 rounded-[8px] text-[10px] font-bold uppercase tracking-widest flex items-center gap-1">
                <Flame className="w-3 h-3" />
                Alert Qualified
              </span>
            )}
            <span className="text-[11px] font-medium text-slate-500 truncate max-w-[100px]">
              {deal.seller}
            </span>
          </div>

          <div
            className={`flex items-baseline gap-0.5 px-3 py-1.5 rounded-[10px] border shadow-inner ${scoreBadgeBg}`}
          >
            <span className="text-base font-black tracking-tighter">{deal.deal_score.toFixed(1)}</span>
            <span className="text-[9px] font-bold uppercase opacity-70 tracking-widest">/10</span>
          </div>
        </div>

        {/* Title */}
        <h3 className="text-lg font-bold text-slate-100 line-clamp-2 leading-snug mb-5 group-hover:text-emerald-300 transition-colors duration-300">
          {deal.title}
        </h3>

        {/* Price & Arbitrage Profit Box */}
        <div className="bg-slate-950/50 backdrop-blur-sm border border-slate-800/80 rounded-xl p-4 mb-5 shadow-inner">
          <div className="flex items-end justify-between mb-2">
            <div>
              <span className="text-[10px] font-bold tracking-widest uppercase text-slate-500 block mb-1">
                Asking Price
              </span>
              <span className="text-3xl font-black text-white tracking-tighter">
                ${deal.price.toFixed(0)}
              </span>
            </div>
            <div className="text-right">
              <span className="text-[10px] font-bold tracking-widest uppercase text-slate-500 block mb-1">
                Fair Market Value
              </span>
              <span className="text-base font-bold text-slate-300 line-through decoration-slate-600/50 decoration-2">
                ${deal.fair_market_value.toFixed(0)}
              </span>
            </div>
          </div>

          {/* Arbitrage Spread Bar */}
          <div className="pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs">
            <span className="text-slate-400 font-medium text-[11px] uppercase tracking-wider">Arbitrage Spread</span>
            <span className="font-bold text-emerald-400 flex items-center gap-1.5">
              <span className="bg-emerald-500/20 px-1.5 py-0.5 rounded text-emerald-300">+${deal.estimated_profit.toFixed(0)}</span>
              <span className="text-[10px] font-black tracking-widest uppercase text-emerald-500/80">
                (+{deal.arbitrage_margin_pct.toFixed(0)}% ROI)
              </span>
            </span>
          </div>
        </div>

        {/* Specs Grid */}
        <div className="space-y-2.5 text-xs mb-6 px-1">
          <div className="flex items-center gap-3 text-slate-300">
            <div className="p-1.5 rounded-lg bg-slate-800/50 text-slate-400 shadow-inner">
              <Cpu className="h-3.5 w-3.5" />
            </div>
            <span className="font-medium truncate">{deal.specs.cpu || "N/A"}</span>
          </div>
          <div className="flex items-center gap-3 text-slate-300">
            <div className="p-1.5 rounded-lg bg-slate-800/50 text-slate-400 shadow-inner">
              <HardDrive className="h-3.5 w-3.5" />
            </div>
            <span className="font-medium truncate">
              <strong className="text-white">{deal.specs.ram_gb} GB</strong> RAM <span className="opacity-40 px-1">•</span> <strong className="text-white">{deal.specs.ssd_gb} GB</strong> SSD
            </span>
          </div>
          <div className="flex items-center gap-3 text-slate-300">
            <div className="p-1.5 rounded-lg bg-emerald-950/30 text-emerald-400 shadow-inner">
              <Sparkles className="h-3.5 w-3.5" />
            </div>
            <span className="font-medium truncate">{deal.specs.gpu || "Integrated Graphics"}</span>
          </div>
        </div>

        {/* AI Valuation Recommendation */}
        <div className="text-xs text-slate-400 bg-slate-900/40 p-3 rounded-xl border border-slate-800/60 mb-5 relative overflow-hidden">
          <div className="absolute left-0 top-0 bottom-0 w-1 bg-emerald-500/40" />
          <div className="flex items-center gap-1.5 text-[10px] font-black uppercase tracking-widest text-slate-300 mb-1.5 pl-2">
            <Shield className="h-3 w-3 text-emerald-400" />
            <span>AI Valuation &amp; Action</span>
          </div>
          <p className="pl-2 line-clamp-2 leading-relaxed text-[11px]">
            {deal.actionable_recommendation}
          </p>
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
