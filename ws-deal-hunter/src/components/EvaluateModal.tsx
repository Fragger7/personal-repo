import React, { useState } from "react";
import { X, Sparkles, Cpu, HardDrive, ShieldCheck, ArrowRight, CheckCircle2 } from "lucide-react";
import { DealRecord } from "../types";

interface EvaluateModalProps {
  isOpen: boolean;
  onClose: () => void;
  onDealEvaluated: (deal: DealRecord) => void;
}

export const EvaluateModal: React.FC<EvaluateModalProps> = ({
  isOpen,
  onClose,
  onDealEvaluated,
}) => {
  const [title, setTitle] = useState(
    "[H] Lenovo ThinkPad P16 Gen 1 (Core i9-12950HX, 64GB DDR5 ECC, 2TB SSD, RTX A4500 16GB, 4K UHD+) [W] $680 PayPal"
  );
  const [price, setPrice] = useState("680");
  const [source, setSource] = useState("reddit");
  const [url, setUrl] = useState("https://reddit.com/r/hardwareswap");
  const [description, setDescription] = useState(
    "Selling my mobile workstation in pristine condition. Used exclusively in a docked workstation environment. 1600p IPS / 4K UHD+ screen, clean keyboard, battery health 96%."
  );
  const [saveToStore, setSaveToStore] = useState(true);

  const [isLoading, setIsLoading] = useState(false);
  const [result, setResult] = useState<DealRecord | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleEvaluate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);
    setResult(null);

    try {
      const res = await fetch("/api/deals/evaluate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title,
          price: parseFloat(price),
          source,
          url,
          description,
          saveToStore,
        }),
      });

      const data = await res.json();
      if (data.success && data.deal) {
        setResult(data.deal);
        if (saveToStore) {
          onDealEvaluated(data.deal);
        }
      } else {
        setError(data.error || "Evaluation failed.");
      }
    } catch (err: any) {
      setError(err.message || "Failed to contact evaluation server.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-2xl flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <Sparkles className="h-4 w-4" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Live AI Hardware Evaluator</h2>
              <p className="text-xs text-slate-400">
                Gemini 2.5 Flash structured spec extraction &amp; arbitrage score
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 space-y-5">
          <form onSubmit={handleEvaluate} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Listing Title
              </label>
              <input
                type="text"
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Dell Precision 7780 (i9-13950HX, 64GB DDR5, RTX 3500 Ada)"
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Asking Price ($)
                </label>
                <input
                  type="number"
                  required
                  step="0.01"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Source Platform
                </label>
                <select
                  value={source}
                  onChange={(e) => setSource(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
                >
                  <option value="reddit">Reddit r/hardwareswap</option>
                  <option value="ebay">eBay API</option>
                  <option value="swappa">Swappa RSS</option>
                  <option value="manual">Manual Listing</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Listing URL
                </label>
                <input
                  type="url"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="https://..."
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">
                Body / Specification Description
              </label>
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Paste body text or extra hardware details..."
                className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div className="flex items-center justify-between pt-1">
              <label className="flex items-center gap-2 cursor-pointer text-xs text-slate-400 select-none">
                <input
                  type="checkbox"
                  checked={saveToStore}
                  onChange={(e) => setSaveToStore(e.target.checked)}
                  className="accent-emerald-500 rounded"
                />
                <span>Persist evaluated record to <code className="text-emerald-400">deals.json</code></span>
              </label>

              <button
                type="submit"
                disabled={isLoading}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 active:scale-95 text-white text-xs font-bold transition duration-150 disabled:opacity-50 shadow-md shadow-emerald-950"
              >
                {isLoading ? (
                  <>
                    <Sparkles className="h-4 w-4 animate-spin" />
                    <span>Evaluating with Gemini...</span>
                  </>
                ) : (
                  <>
                    <span>Run AI Valuation</span>
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </button>
            </div>
          </form>

          {/* Error Banner */}
          {error && (
            <div className="p-3 rounded-lg bg-red-950/60 border border-red-800/80 text-xs text-red-300">
              {error}
            </div>
          )}

          {/* Evaluation Result View */}
          {result && (
            <div className="mt-4 p-4 rounded-xl bg-slate-950 border border-emerald-500/30 space-y-3 animate-in fade-in duration-200">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                  Evaluation Successful
                </span>
                <span className="px-2.5 py-1 rounded-md text-xs font-extrabold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                  Deal Score: {result.deal_score.toFixed(1)} / 10.0
                </span>
              </div>

              {/* Financial Box */}
              <div className="grid grid-cols-3 gap-2 bg-slate-900/80 p-3 rounded-lg border border-slate-800 text-center text-xs">
                <div>
                  <span className="text-slate-400 block text-[10px]">Asking Price</span>
                  <span className="font-bold text-white text-sm">${result.price}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">Est. FMV</span>
                  <span className="font-bold text-slate-200 text-sm">${result.fair_market_value.toFixed(0)}</span>
                </div>
                <div>
                  <span className="text-slate-400 block text-[10px]">Profit Margin</span>
                  <span className="font-bold text-emerald-400 text-sm">+{result.arbitrage_margin_pct.toFixed(0)}%</span>
                </div>
              </div>

              {/* Specs Breakdown */}
              <div className="text-xs space-y-1 text-slate-300 bg-slate-900/50 p-3 rounded-lg border border-slate-800/80">
                <div>• <strong className="text-slate-200">CPU:</strong> {result.specs.cpu}</div>
                <div>• <strong className="text-slate-200">RAM:</strong> {result.specs.ram_gb} GB DDR</div>
                <div>• <strong className="text-slate-200">SSD:</strong> {result.specs.ssd_gb} GB NVMe</div>
                <div>• <strong className="text-slate-200">GPU:</strong> {result.specs.gpu}</div>
                <div>• <strong className="text-slate-200">Screen:</strong> {result.specs.screen}</div>
              </div>

              <div className="text-xs text-slate-400">
                <span className="font-bold text-slate-300">AI Summary: </span>
                {result.summary}
              </div>

              <div className="text-xs font-bold text-emerald-400 pt-1">
                &rarr; {result.actionable_recommendation}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
