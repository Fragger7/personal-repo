import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Activity, Database, Zap, Calculator, Car, ChevronRight, CheckCircle2 } from 'lucide-react';

export default function IntelDashboard() {
  const [isScraping, setIsScraping] = useState(false);
  const [step, setStep] = useState(0); 
  const [baselines, setBaselines] = useState<any>(null);
  const [inventory, setInventory] = useState<any[]>([]);
  const [error, setError] = useState('');

  const initializeAggregator = async () => {
    setIsScraping(true);
    setStep(1);
    setError('');
    try {
      // 1. Extract Baselines
      const resBase = await fetch('/api/scrape/extract-baselines', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          make: 'Kia',
          model: 'EV9',
          trim: 'GT-Line OR Land AWD',
          year: '2024',
          zipCode: '78665'
        })
      });
      if (!resBase.ok) throw new Error('Failed to extract baselines');
      const dataBase = await resBase.json();
      setBaselines(dataBase);
      setStep(2);

      // 2. Search Inventory
      const resInv = await fetch('/api/scrape/search-inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
           make: 'Kia',
           model: 'EV9',
           zipCode: '78665',
           radius: 300
        })
      });
      if (!resInv.ok) throw new Error('Failed to fetch inventory');
      const dataInv = await resInv.json();
      setInventory(dataInv.results || []);
      setStep(3);

    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Scraping failed');
    } finally {
      setIsScraping(false);
    }
  };

  return (
    <div className="grid grid-cols-12 gap-6">
      {/* Intelligence Dashboard Setup */}
      <div className="col-span-12 lg:col-span-8 space-y-6">
        <div className="bg-slate-900 border border-white/5 rounded-2xl overflow-hidden">
          <div className="bg-slate-800/50 border-b border-white/5 px-6 py-4 flex items-center justify-between">
            <h3 className="text-sm font-medium text-slate-200 flex items-center gap-2">
              <Activity className="h-4 w-4 text-emerald-400" />
              Live Inventory Feed (North America)
            </h3>
            <span className="text-xs text-slate-500 font-mono">
              {step === 0 ? 'AWAITING SCRAPE PROTOCOL' : step === 3 ? 'SCRAPE COMPLETE' : 'SCRAPING ACTIVE...'}
            </span>
          </div>
          
          <div className="p-6">
            {error && (
              <div className="mb-6 p-4 rounded-xl border border-red-500/30 bg-red-500/10 text-red-400 text-sm">
                <p className="font-semibold mb-1">Scraper Protocol Error</p>
                <p>{error}</p>
                <button 
                  onClick={() => { setStep(0); setError(''); }}
                  className="mt-3 px-3 py-1.5 bg-red-500/20 hover:bg-red-500/30 text-red-300 rounded text-xs font-medium transition-colors"
                >
                  Reset Engine
                </button>
              </div>
            )}
            
            {step === 0 && !error && (
              <div className="py-12 text-center flex flex-col items-center justify-center">
                <Database className="h-12 w-12 text-slate-700 mb-4" />
                <p className="text-slate-400 text-sm max-w-md">
                  The aggregator core is currently configuring. We are defining the scraper logic for Leasehackr, Edmunds, Reddit, and specific captive lender inventory endpoints.
                </p>
                <button 
                  onClick={initializeAggregator}
                  disabled={isScraping}
                  className="mt-6 flex items-center gap-2 px-5 py-2.5 rounded-lg bg-indigo-500 text-white text-sm font-medium hover:bg-indigo-600 transition-colors disabled:opacity-50"
                >
                  <Zap className="h-4 w-4" />
                  Initialize Aggregator Engine
                </button>
              </div>
            )}

            {step > 0 && !error && (
              <div className="space-y-6">
                <div className="flex items-center gap-4 text-sm">
                  <div className={`px-3 py-1.5 rounded-lg border flex items-center gap-2 ${step >= 1 ? 'border-indigo-500/30 bg-indigo-500/10 text-indigo-300' : 'border-slate-800 bg-slate-900 text-slate-500'}`}>
                    {step > 1 ? <CheckCircle2 className="h-4 w-4 text-emerald-400" /> : <div className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-pulse" />}
                    1. Extract Baselines
                  </div>
                  <ChevronRight className="h-4 w-4 text-slate-700" />
                  <div className={`px-3 py-1.5 rounded-lg border flex items-center gap-2 ${step >= 2 ? 'border-blue-500/30 bg-blue-500/10 text-blue-300' : 'border-slate-800 bg-slate-900 text-slate-500'}`}>
                    {step > 2 ? <CheckCircle2 className="h-4 w-4 text-emerald-400" /> : step === 2 ? <div className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-pulse" /> : <div className="w-1.5 h-1.5 rounded-full bg-slate-700" />}
                    2. Query Dealer APIs
                  </div>
                  <ChevronRight className="h-4 w-4 text-slate-700" />
                  <div className={`px-3 py-1.5 rounded-lg border flex items-center gap-2 ${step >= 3 ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300' : 'border-slate-800 bg-slate-900 text-slate-500'}`}>
                    {step === 3 ? <CheckCircle2 className="h-4 w-4 text-emerald-400" /> : <div className="w-1.5 h-1.5 rounded-full bg-slate-700" />}
                    3. Target Analysis
                  </div>
                </div>

                {inventory.length > 0 && (
                  <div className="space-y-3 mt-6">
                    {inventory.map((inv, idx) => (
                      <div key={idx} className="p-4 rounded-xl bg-slate-950 border border-white/5 flex items-center justify-between">
                        <div className="flex items-center gap-4">
                          <div className="bg-slate-900 p-3 rounded-lg">
                            <Car className="h-5 w-5 text-slate-400" />
                          </div>
                          <div>
                            <p className="font-medium text-slate-200">{inv.dealerName}</p>
                            <p className="text-xs text-slate-500 font-mono">{inv.color} • {inv.distance} • {inv.daysOnLot} Days on Lot</p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className="font-semibold text-emerald-400">${inv.msrp.toLocaleString()}</p>
                          <p className="text-[10px] text-slate-500 uppercase tracking-wider">MSRP</p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="col-span-12 lg:col-span-4 space-y-6">
        <div className="bg-slate-900 border border-white/5 rounded-2xl p-6">
          <h3 className="text-sm font-medium text-slate-200 mb-4 flex items-center gap-2">
            <Calculator className="h-4 w-4 text-blue-400" />
            Live Market Base Programs
          </h3>
          <div className="space-y-4">
            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center mb-1">
                <span className="text-xs text-slate-400 font-mono">MONEY FACTOR</span>
                <span className="text-xs font-semibold text-emerald-400">{baselines?.moneyFactor ? baselines.moneyFactor : '---'}</span>
              </div>
            </div>
            
            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center mb-1">
                <span className="text-xs text-slate-400 font-mono">RESIDUAL</span>
                <span className="text-xs font-semibold text-blue-400">{baselines?.residualValue ? `${baselines.residualValue}%` : '---'}</span>
              </div>
            </div>

            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center">
                <span className="text-xs text-slate-400 font-mono">INCENTIVES</span>
                <span className="text-xs font-semibold text-indigo-400">{baselines?.leaseCash ? `$${baselines.leaseCash.toLocaleString()}` : '---'}</span>
              </div>
            </div>
          </div>
          {baselines?.marketMomentum && (
            <div className="mt-6 p-4 rounded-lg bg-indigo-500/10 border border-indigo-500/20">
              <p className="text-xs text-indigo-300 font-medium mb-1">Market Momentum</p>
              <p className="text-[11px] text-slate-400 leading-relaxed">{baselines.marketMomentum}</p>
              <p className="text-[10px] text-slate-500 mt-2 font-mono border-t border-indigo-500/20 pt-2">{baselines.sourceNotes}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
