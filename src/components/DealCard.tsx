import React, { useState } from "react";
import { ExternalLink, Bell, CheckCircle2, Cpu, HardDrive, Monitor, Shield, Sparkles, Flame, Trash2 } from "lucide-react";
import { DealRecord } from "../types";

interface DealCardProps {
  deal: DealRecord;
  onSendPush: (deal: DealRecord) => Promise<void>;
  onDeleteDeal?: (dealId: string) => void;
}

export const DealCard: React.FC<DealCardProps> = ({ deal, onSendPush, onDeleteDeal }) => {
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
      className={`industrial-card p-6 flex flex-col justify-between relative overflow-hidden group ${
        deal.is_high_yield ? "laser-accent border-emerald-500/30" : ""
      }`}
    >
      {/* Top Banner: Source & Score Badge */}
      <div className="relative z-10">
        <div className="flex items-start justify-between gap-3 mb-5">
          <div className="flex items-center gap-2 flex-wrap">
            <span
              className={`px-2 py-1 text-[9px] font-bold border uppercase tracking-widest tech-text ${sourceBadgeColors}`}
            >
              {deal.source.replace("reddit (r/hardwareswap)", "r/hws").substring(0, 15)}
            </span>
            {deal.is_high_yield && (
              <span className="bg-emerald-500/10 text-emerald-500 border border-emerald-500/30 px-2 py-1 text-[9px] font-bold uppercase tracking-widest tech-text flex items-center gap-1">
                <Flame className="w-2.5 h-2.5" />
                Alert
              </span>
            )}
            <span className="text-[10px] uppercase tracking-widest font-bold text-[#666] truncate max-w-[100px]">
              {deal.seller}
            </span>
          </div>

          <div
            className={`flex items-baseline gap-0.5 px-3 py-1.5 border tech-text ${scoreBadgeBg}`}
          >
            <span className="text-lg font-black tracking-tighter">{deal.deal_score.toFixed(1)}</span>
            <span className="text-[9px] font-bold uppercase opacity-70 tracking-widest">/10</span>
          </div>
        </div>

        {/* Title */}
        <h3 className="text-[15px] font-bold text-[#e2e8f0] line-clamp-2 leading-snug mb-5 group-hover:text-emerald-500 transition-colors duration-300">
          {deal.title}
        </h3>

        {/* Price & Arbitrage Profit Box */}
        <div className="bg-[#050505] border border-[#222] p-4 mb-5 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]">
          <div className="flex items-end justify-between mb-2">
            <div>
              <span className="text-[9px] font-bold tracking-widest uppercase text-[#666] block mb-1">
                Asking Price
              </span>
              <span className="text-3xl font-black text-[#e2e8f0] tech-text tracking-tighter">
                ${deal.price.toFixed(0)}
              </span>
            </div>
            <div className="text-right">
              <span className="text-[9px] font-bold tracking-widest uppercase text-[#666] block mb-1">
                Fair Market Value
              </span>
              <span className="text-base font-bold text-[#888] tech-text line-through decoration-[#444] decoration-2">
                ${deal.fair_market_value.toFixed(0)}
              </span>
            </div>
          </div>

          {/* Arbitrage Spread Bar */}
          <div className="pt-3 border-t border-[#222] flex items-center justify-between">
            <span className="text-[#666] font-bold text-[9px] uppercase tracking-widest">Spread</span>
            <span className="font-bold text-emerald-500 flex items-center gap-1.5">
              <span className="bg-emerald-500/10 px-1.5 py-0.5 tech-text">+${deal.estimated_profit.toFixed(0)}</span>
              <span className="text-[9px] font-black tracking-widest uppercase tech-text text-emerald-500/60">
                (+{deal.arbitrage_margin_pct.toFixed(0)}% ROI)
              </span>
            </span>
          </div>
        </div>

        {/* Specs Grid */}
        <div className="space-y-3 mb-6 px-1">
          <div className="flex items-center gap-3 text-[#aaa]">
            <div className="text-[#555]">
              <Cpu className="h-4 w-4" />
            </div>
            <span className="text-[11px] font-bold uppercase tracking-widest truncate">{deal.specs.cpu || "N/A"}</span>
          </div>
          <div className="flex items-center gap-3 text-[#aaa]">
            <div className="text-[#555]">
              <HardDrive className="h-4 w-4" />
            </div>
            <span className="text-[11px] font-bold uppercase tracking-widest truncate">
              <strong className="text-[#e2e8f0] tech-text">{deal.specs.ram_gb} GB</strong> RAM <span className="text-[#444] px-1">/</span> <strong className="text-[#e2e8f0] tech-text">{deal.specs.ssd_gb} GB</strong> SSD
            </span>
          </div>
          <div className="flex items-center gap-3 text-emerald-500">
            <div>
              <Sparkles className="h-4 w-4" />
            </div>
            <span className="text-[11px] font-bold uppercase tracking-widest truncate">{deal.specs.gpu || "Integrated Graphics"}</span>
          </div>
        </div>

        {/* AI Valuation Recommendation */}
        <div className="text-xs text-[#888] bg-[#050505] p-4 border border-[#222] mb-5 relative overflow-hidden">
          <div className="absolute left-0 top-0 bottom-0 w-0.5 bg-emerald-500/50" />
          <div className="flex items-center gap-2 text-[9px] font-black uppercase tracking-widest text-[#aaa] mb-2 pl-2">
            <Shield className="h-3 w-3 text-emerald-500" />
            <span>AI Valuation</span>
          </div>
          <p className="pl-2 line-clamp-2 leading-relaxed text-[11px] font-medium">
            {deal.actionable_recommendation}
          </p>
        </div>
      </div>

      {/* Card Action Buttons */}
      <div className="flex items-center gap-2 pt-4 border-t border-[#222]">
        <a
          href={deal.url}
          target="_blank"
          rel="noopener noreferrer"
          className="flex-1 inline-flex items-center justify-center gap-2 px-3 py-2.5 bg-[#111] hover:bg-[#1a1a1a] text-emerald-500 border border-[#333] hover:border-emerald-500/50 text-[10px] font-bold uppercase tracking-widest transition duration-150 active:scale-95 glitch-hover"
        >
          <span>Open Listing</span>
          <ExternalLink className="h-3.5 w-3.5" />
        </a>

        <button
          onClick={handlePush}
          disabled={isSending}
          className={`inline-flex items-center justify-center p-2.5 text-xs font-semibold border transition duration-150 active:scale-95 glitch-hover ${
            pushSent
              ? "bg-[#111] border-emerald-500/50 text-emerald-500"
              : "bg-[#0a0a0a] hover:bg-[#111] border-[#333] text-[#666]"
          }`}
          title="Send instant mobile push notification via Pushover"
        >
          {pushSent ? (
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          ) : (
            <Bell className={`h-4 w-4 ${isSending ? "animate-pulse text-amber-500" : ""}`} />
          )}
        </button>

        {onDeleteDeal && (
          <button
            onClick={() => onDeleteDeal(deal.id)}
            className="inline-flex items-center justify-center p-2.5 text-xs font-semibold border border-rose-900/40 bg-rose-950/30 hover:bg-rose-900/60 text-rose-400 hover:text-rose-200 transition duration-150 active:scale-95"
            title="Delete / Dismiss deal from list"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        )}
      </div>
    </div>
  );
};
