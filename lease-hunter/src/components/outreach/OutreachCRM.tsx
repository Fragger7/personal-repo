import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Mail, Send, Copy, Navigation, Sparkles, Building2, AlignLeft } from 'lucide-react';

export default function OutreachCRM() {
  const [generating, setGenerating] = useState(false);
  const [outreachData, setOutreachData] = useState<any>(null);

  const generateEmail = async () => {
    setGenerating(true);
    try {
      const response = await fetch('/api/scrape/score-targets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          targets: [{
            vin: "KNDCE3LXXXXXXXXX1",
            dealerName: "Round Rock Kia",
            msrp: 75395,
            daysOnLot: 45
          }],
          baselines: {
            moneyFactor: 0.0021,
            residualValue: 64,
            leaseCash: 7500
          }
        })
      });
      const data = await response.json();
      setOutreachData(data);
    } catch (e) {
      console.error(e);
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div className="grid grid-cols-12 gap-6">
      
      {/* CRM Left Panel */}
      <div className="col-span-12 lg:col-span-4 space-y-6">
        <div className="bg-slate-900 border border-white/5 rounded-2xl overflow-hidden p-6">
           <h3 className="text-sm font-medium text-slate-200 flex items-center gap-2 mb-4">
              <Building2 className="h-4 w-4 text-emerald-400" />
              Active Leads Tracker
            </h3>

            <div className="space-y-4">
              {/* Dummy Active Lead */}
              <div className="p-4 rounded-xl bg-slate-800/50 border border-emerald-500/30">
                 <p className="font-semibold text-emerald-400 mb-1">Round Rock Kia</p>
                 <p className="text-xs text-slate-400 font-mono mb-2">VIN: KNDCE3LXXXXXXXXX1</p>
                 <span className="px-2 py-1 text-[10px] font-bold tracking-widest uppercase bg-indigo-500/20 text-indigo-300 rounded border border-indigo-500/30">Target Identified</span>
                 
                 <div className="mt-4">
                   <button 
                     onClick={generateEmail}
                     disabled={generating}
                     className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
                   >
                     {generating ? <Sparkles className="h-4 w-4 animate-pulse" /> : <Mail className="h-4 w-4" />}
                     {generating ? 'Drafting Neural Template...' : 'Generate AI Outreach'}
                   </button>
                 </div>
              </div>

               <div className="p-4 rounded-xl bg-slate-950 border border-white/5 opacity-50">
                 <p className="font-semibold text-slate-400 mb-1">Capitol Auto</p>
                 <p className="text-xs text-slate-500 font-mono mb-2">VIN: KNDCE3LXXXXXXXXX2</p>
                 <span className="px-2 py-1 text-[10px] font-bold tracking-widest uppercase bg-slate-800 text-slate-400 rounded border border-slate-700">Evaluating</span>
              </div>
            </div>
        </div>
      </div>

      {/* Right Panel: Outreach Drafting */}
      <div className="col-span-12 lg:col-span-8">
        <div className="bg-slate-900 border border-white/5 rounded-2xl overflow-hidden h-full">
           <div className="bg-slate-800/50 border-b border-white/5 px-6 py-4 flex items-center justify-between">
              <h3 className="text-sm font-medium text-slate-200 flex items-center gap-2">
                <AlignLeft className="h-4 w-4 text-emerald-400" />
                AI Negotiation Broker
              </h3>
              {outreachData?.leasehackrScore && (
                 <span className="text-xs font-mono font-semibold bg-emerald-500/20 text-emerald-400 px-3 py-1 rounded-full border border-emerald-500/30">
                   Leasehackr Score: {outreachData.leasehackrScore}/10
                 </span>
              )}
            </div>
            
            <div className="p-6 h-full min-h-[400px]">
              {!outreachData && !generating && (
                <div className="h-full flex flex-col items-center justify-center text-center opacity-50 py-12">
                   <Navigation className="h-12 w-12 text-slate-500 mb-4" />
                   <p className="text-sm text-slate-400">Select a target lead to generate an assertive, data-backed first-contact email.</p>
                </div>
              )}

              {generating && (
                <div className="h-full flex flex-col items-center justify-center py-12">
                   <div className="w-8 h-8 rounded-full border-2 border-indigo-500 border-t-transparent animate-spin mb-4" />
                   <p className="text-sm text-indigo-400 font-mono animate-pulse">Structuring aggressive offer parameters...</p>
                </div>
              )}

              {outreachData && !generating && (
                <div className="space-y-6">
                  <div className="p-4 rounded-xl border border-blue-500/30 bg-blue-500/10 mb-6">
                    <p className="text-xs text-blue-400 uppercase tracking-widest font-semibold mb-2">Broker Analysis</p>
                    <p className="text-sm text-slate-200 leading-relaxed">{outreachData.dealEvaluation}</p>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-2">
                       <p className="text-sm text-slate-400 font-medium ml-1">Proposed Email Draft :</p>
                       <button className="text-xs text-indigo-400 hover:text-indigo-300 flex items-center gap-1 bg-indigo-500/10 px-2 py-1 rounded">
                         <Copy className="h-3 w-3" /> Copy
                       </button>
                    </div>
                    <textarea 
                      readOnly 
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 text-sm text-slate-300 font-mono leading-relaxed resize-none focus:outline-none"
                      rows={14}
                      value={outreachData.outreachEmail}
                    />
                  </div>
                </div>
              )}
            </div>
        </div>
      </div>
    </div>
  )
}
