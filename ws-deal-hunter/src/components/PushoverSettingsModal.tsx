import React, { useState } from "react";
import { X, Bell, Send, CheckCircle2, Smartphone, ShieldCheck } from "lucide-react";
import { DealRecord } from "../types";

interface PushoverSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  sampleDeal?: DealRecord;
}

export const PushoverSettingsModal: React.FC<PushoverSettingsModalProps> = ({
  isOpen,
  onClose,
  sampleDeal,
}) => {
  const [isSending, setIsSending] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [testPayload, setTestPayload] = useState<any>(null);

  if (!isOpen) return null;

  const handleTestDispatch = async () => {
    setIsSending(true);
    setStatusMessage(null);
    setTestPayload(null);

    try {
      const res = await fetch("/api/notify/pushover", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          dealId: sampleDeal?.id,
        }),
      });

      const data = await res.json();
      if (data.success) {
        setStatusMessage(data.message || "Alert dispatched successfully!");
        if (data.payload) {
          setTestPayload(data.payload);
        }
      } else {
        setStatusMessage(`Error: ${data.error || "Failed to trigger alert."}`);
      }
    } catch (err: any) {
      setStatusMessage(`Dispatch error: ${err.message}`);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-xl shadow-2xl overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Bell className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Pushover Push Notification Dispatcher</h2>
              <p className="text-xs text-slate-400">
                Autonomous mobile push alerts for high-yield hardware arbitrage
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

        {/* Content */}
        <div className="p-6 space-y-5 text-xs text-slate-300">
          {/* Threshold Criteria Box */}
          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
            <div className="font-bold text-slate-200 flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-emerald-400" />
              <span>Autonomous Trigger Criteria</span>
            </div>
            <p className="text-slate-400 leading-relaxed">
              The daemon automatically evaluates listings and sends priority push alerts when:
            </p>
            <ul className="list-disc list-inside space-y-1 font-mono text-emerald-400">
              <li>Deal Score &ge; 8.5 / 10.0 (Gemini 2.5 Flash)</li>
              <li>Asking Price &le; $750.00</li>
              <li>Calculated Arbitrage Profit &gt; $0</li>
            </ul>
          </div>

          {/* Simulated Mobile Mockup */}
          <div className="p-4 rounded-xl bg-slate-950/80 border border-amber-500/20 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-amber-400 flex items-center gap-1.5">
                <Smartphone className="h-3.5 w-3.5" />
                Mobile Push Preview
              </span>
              <span className="text-[10px] text-slate-400">Priority: High (Sound: Magic)</span>
            </div>
            <div className="bg-slate-900 border border-slate-800 p-3 rounded-lg space-y-1.5">
              <div className="font-bold text-white text-xs">
                🔥 [9.9/10 DEAL] $740 Intel Core i9 13th Gen
              </div>
              <p className="text-[11px] text-slate-300 leading-normal">
                💻 Dell Precision 7680 16" (i9-13950HX, 64GB RAM, 1TB SSD, RTX 4000 Ada 12GB)
                <br />
                • Asking: $740 (Est. FMV: $1814)
                <br />
                • Profit: +$1074 (+145% ROI)
                <br />
                &rarr; INSTANT ARBITRAGE BUY
              </p>
            </div>
          </div>

          {/* Test Dispatch Button */}
          <div className="pt-2 flex flex-col gap-3">
            <button
              onClick={handleTestDispatch}
              disabled={isSending}
              className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs transition duration-150 active:scale-95 disabled:opacity-50 shadow-md shadow-emerald-950"
            >
              <Send className={`h-4 w-4 ${isSending ? "animate-spin" : ""}`} />
              <span>{isSending ? "Dispatching Alert..." : "Send Test Push Alert"}</span>
            </button>

            {statusMessage && (
              <div className="p-3 rounded-lg bg-emerald-950/60 border border-emerald-800/80 text-emerald-300 flex items-start gap-2">
                <CheckCircle2 className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{statusMessage}</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
