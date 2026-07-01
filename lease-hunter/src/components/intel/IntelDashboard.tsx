import React, { useState } from 'react';
import { motion } from 'motion/react';
import { Activity, Database, Zap, Calculator, Car, ChevronRight, CheckCircle2 } from 'lucide-react';

export default function IntelDashboard({ onDealSelect }: { onDealSelect?: (deal: any) => void }) {
  const [isScraping, setIsScraping] = useState(false);
  const [isParsingText, setIsParsingText] = useState(false);
  const [rawText, setRawText] = useState('');
  const [showManualInput, setShowManualInput] = useState(false);
  const [step, setStep] = useState(0); 
  const [baselines, setBaselines] = useState<any>(null);
  const [inventory, setInventory] = useState<any[]>(() => {
    try {
      const saved = localStorage.getItem('lease_inventory');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });
  const [error, setError] = useState('');

  // Search Parameters
  const [searchParams, setSearchParams] = useState({
    make: 'Kia',
    model: 'EV9',
    trim: 'GT-Line',
    year: '2026',
    zipCode: '78665',
    radius: 300,
  });

  React.useEffect(() => {
    localStorage.setItem('lease_inventory', JSON.stringify(inventory));
  }, [inventory]);

  const initializeAggregator = async () => {
    setIsScraping(true);
    setStep(1);
    setError('');
    try {
      // 1. Extract Baselines
      const resBase = await fetch('/api/scrape/extract-baselines', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(searchParams)
      });
      if (!resBase.ok) {
        const errData = await resBase.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to extract baselines');
      }
      const dataBase = await resBase.json();
      setBaselines(dataBase);
      setStep(2);

      // 2. Search Inventory
      const resInv = await fetch('/api/scrape/search-inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...searchParams, useApify: true })
      });
      if (!resInv.ok) {
        const errData = await resInv.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to fetch inventory');
      }
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

  const parseRawTextDump = async () => {
    if (!rawText.trim()) return;
    setIsParsingText(true);
    setStep(1);
    setError('');
    try {
      // 1. Extract Baselines if we don't have them yet
      if (!baselines) {
        const resBase = await fetch('/api/scrape/extract-baselines', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(searchParams)
        });
        if (!resBase.ok) {
          const errData = await resBase.json().catch(() => ({}));
          throw new Error(errData.error || 'Failed to extract baselines');
        }
        const dataBase = await resBase.json();
        setBaselines(dataBase);
      }
      setStep(2);

      // 2. Parse Raw Text for Inventory
      const resInv = await fetch('/api/scrape/parse-raw-text', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ rawText })
      });
      if (!resInv.ok) {
        const errData = await resInv.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to parse raw text');
      }
      const dataInv = await resInv.json();
      
      // Combine with existing inventory if any
      setInventory(prev => [...dataInv.results, ...prev]);
      setStep(3);
      setRawText(''); // clear it
      setShowManualInput(false);
    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Parsing failed');
    } finally {
      setIsParsingText(false);
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
                <p className="font-semibold mb-1 flex items-center gap-2"><Zap className="w-4 h-4" /> Scraper Protocol Error</p>
                <p className="opacity-90">{error}</p>
                <button 
                  onClick={() => { setStep(0); setError(''); }}
                  className="mt-3 px-3 py-1.5 bg-red-500/20 hover:bg-red-500/30 text-red-300 rounded text-xs font-medium transition-colors border border-red-500/30"
                >
                  Reset Engine
                </button>
              </div>
            )}
            
            {step === 0 && !error && (
              <div className="py-8 flex flex-col items-center justify-center">
                <Database className="h-12 w-12 text-slate-700 mb-6" />
                
                <div className="w-full max-w-md bg-slate-950 p-6 rounded-xl border border-white/5 space-y-4 text-left">
                  <h4 className="text-sm font-medium text-slate-300 mb-4 border-b border-white/10 pb-2">Target Vehicle Parameters</h4>
                  
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Make</label>
                      <input 
                        type="text" 
                        value={searchParams.make}
                        onChange={(e) => setSearchParams(prev => ({ ...prev, make: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Model</label>
                      <input 
                        type="text" 
                        value={searchParams.model}
                        onChange={(e) => setSearchParams(prev => ({ ...prev, model: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Target Trim</label>
                      <select 
                        value={searchParams.trim}
                        onChange={(e) => setSearchParams(prev => ({ ...prev, trim: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50 appearance-none"
                      >
                        <option value="GT-Line">GT-Line</option>
                        <option value="Land AWD">Land AWD</option>
                        <option value="Wind AWD">Wind AWD</option>
                        <option value="Light Long Range">Light Long Range</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Search Radius (miles)</label>
                      <select 
                        value={searchParams.radius}
                        onChange={(e) => setSearchParams(prev => ({ ...prev, radius: Number(e.target.value) }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50 appearance-none"
                      >
                        <option value={50}>50 miles</option>
                        <option value={100}>100 miles</option>
                        <option value={300}>300 miles</option>
                        <option value={500}>500 miles</option>
                      </select>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Year</label>
                      <input 
                        type="text" 
                        value={searchParams.year}
                        onChange={(e) => setSearchParams(prev => ({ ...prev, year: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Target ZIP</label>
                      <input 
                        type="text" 
                        value={searchParams.zipCode}
                        onChange={(e) => setSearchParams(prev => ({ ...prev, zipCode: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                  </div>
                </div>

                <div className="flex gap-4 mt-8">
                  <button 
                    onClick={initializeAggregator}
                    disabled={isScraping}
                    className="flex-1 flex justify-center items-center gap-2 px-6 py-3 rounded-lg bg-indigo-500 text-white text-sm font-medium hover:bg-indigo-600 transition-colors disabled:opacity-50"
                  >
                    <Zap className="h-4 w-4" />
                    Initialize Automated Aggregator
                  </button>
                  <button 
                    onClick={() => setShowManualInput(!showManualInput)}
                    className="flex-1 flex justify-center items-center gap-2 px-6 py-3 rounded-lg bg-slate-800 text-slate-200 border border-slate-700 text-sm font-medium hover:bg-slate-700 transition-colors"
                  >
                    <Database className="h-4 w-4" />
                    Manual Intel Dump
                  </button>
                </div>
                
                {showManualInput && (
                  <div className="w-full mt-6 bg-slate-950 p-6 rounded-xl border border-indigo-500/30 text-left animate-in fade-in slide-in-from-top-4">
                    <h4 className="text-sm font-medium text-slate-300 mb-2">Paste CarGurus Search Results</h4>
                    <p className="text-xs text-slate-500 mb-4">
                      Select all text (Ctrl+A) on the CarGurus search results page and paste it here. Gemini will extract VINs, MSRPs, Dealer names, and Days on Lot.
                    </p>
                    <textarea 
                      value={rawText}
                      onChange={e => setRawText(e.target.value)}
                      placeholder="Paste raw text here..."
                      className="w-full h-40 bg-slate-900 border border-slate-800 rounded-lg p-3 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50 resize-y mb-4"
                    />
                    <button 
                      onClick={parseRawTextDump}
                      disabled={isParsingText || !rawText.trim()}
                      className="w-full flex justify-center items-center gap-2 px-6 py-3 rounded-lg bg-indigo-500/20 text-indigo-300 border border-indigo-500/50 text-sm font-medium hover:bg-indigo-500/30 transition-colors disabled:opacity-50"
                    >
                      {isParsingText ? (
                        <>
                          <div className="w-4 h-4 rounded-full border-2 border-indigo-400 border-t-transparent animate-spin" />
                          Parsing Intelligence...
                        </>
                      ) : (
                        <>
                          <CheckCircle2 className="h-4 w-4" />
                          Extract Targets via Gemini
                        </>
                      )}
                    </button>
                  </div>
                )}
              </div>
            )}

            {step > 0 && !error && (
              <div className="space-y-6">
                <div className="flex flex-col gap-3">
                  <div className={`p-4 rounded-xl border flex items-center justify-between ${step >= 1 ? 'border-indigo-500/30 bg-indigo-500/10' : 'border-slate-800 bg-slate-900 opacity-50'}`}>
                    <div className="flex items-center gap-3">
                      {step > 1 ? <CheckCircle2 className="h-5 w-5 text-indigo-400" /> : <div className="w-2 h-2 ml-1.5 rounded-full bg-indigo-400 animate-pulse" />}
                      <div>
                        <p className={`text-sm font-medium ${step >= 1 ? 'text-indigo-300' : 'text-slate-500'}`}>1. Extract Baselines via Search Grounding</p>
                        {step === 1 && <p className="text-xs text-indigo-400/70 mt-1">Prompting Gemini to structure latest Leasehackr Edmunds rates...</p>}
                      </div>
                    </div>
                  </div>

                  <div className={`p-4 rounded-xl border flex items-center justify-between ${step >= 2 ? 'border-blue-500/30 bg-blue-500/10' : 'border-slate-800 bg-slate-900 opacity-50'}`}>
                    <div className="flex items-center gap-3">
                      {step > 2 ? <CheckCircle2 className="h-5 w-5 text-blue-400" /> : step === 2 ? <div className="w-2 h-2 ml-1.5 rounded-full bg-blue-400 animate-pulse" /> : <div className="w-2 h-2 ml-1.5 rounded-full bg-slate-700" />}
                      <div>
                        <p className={`text-sm font-medium ${step >= 2 ? 'text-blue-300' : 'text-slate-500'}`}>2. Regional Inventory Search</p>
                        {step === 2 && <p className="text-xs text-blue-400/70 mt-1">Searching dealer sites in target ZIP boundary...</p>}
                      </div>
                    </div>
                  </div>

                  <div className={`p-4 rounded-xl border flex items-center justify-between ${step >= 3 ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-slate-800 bg-slate-900 opacity-50'}`}>
                    <div className="flex items-center gap-3">
                      {step === 3 ? <CheckCircle2 className="h-5 w-5 text-emerald-400" /> : <div className="w-2 h-2 ml-1.5 rounded-full bg-slate-700" />}
                      <div>
                        <p className={`text-sm font-medium ${step >= 3 ? 'text-emerald-300' : 'text-slate-500'}`}>3. Target Analysis</p>
                        {step === 3 && <p className="text-xs text-emerald-400/70 mt-1">Acquired valid inventory listings.</p>}
                      </div>
                    </div>
                  </div>
                </div>

                {inventory.length > 0 && (
                  <div className="space-y-3 mt-8">
                    <div className="flex items-center justify-between mb-4 border-b border-white/5 pb-2">
                      <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Acquired Targets</h4>
                      <button 
                        onClick={() => setInventory([])}
                        className="text-xs text-red-400 hover:text-red-300 transition-colors"
                      >
                        Clear Inventory
                      </button>
                    </div>
                    {inventory.map((inv, idx) => (
                      <button
                        key={idx}
                        onClick={() => {
                          if (onDealSelect) {
                            onDealSelect({
                              zipCode: searchParams.zipCode,
                              msrp: inv.msrp,
                              discount: baselines?.reasonableDiscountPercent || 6.5,
                              rebates: baselines?.leaseCash || 0,
                              term: 36,
                              moneyFactor: baselines?.moneyFactor || 0.00210,
                              residualPercent: baselines?.residualValue || 64,
                              dealerName: inv.dealerName,
                              vin: inv.vin
                            });
                          }
                        }}
                        className="w-full text-left p-4 rounded-xl bg-slate-950 border border-white/5 hover:border-indigo-500/50 hover:bg-slate-900 transition-all flex items-center justify-between group"
                      >
                        <div className="flex items-center gap-4">
                          <div className="bg-slate-900 p-3 rounded-lg group-hover:bg-indigo-500/20 transition-colors">
                            <Car className="h-5 w-5 text-slate-400 group-hover:text-indigo-400" />
                          </div>
                          <div>
                            <p className="font-medium text-slate-200">{inv.dealerName}</p>
                            <p className="text-xs text-slate-500 font-mono">{inv.color} • {inv.distance} • {inv.daysOnLot} Days on Lot</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-6">
                          <div className="text-right">
                            <p className="font-semibold text-emerald-400">${inv.msrp.toLocaleString()}</p>
                            <p className="text-[10px] text-slate-500 uppercase tracking-wider">MSRP</p>
                          </div>
                          <ChevronRight className="h-5 w-5 text-slate-600 group-hover:text-indigo-400 transition-colors" />
                        </div>
                      </button>
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
              <div className="flex justify-between items-center mb-1">
                <span className="text-xs text-slate-400 font-mono">INCENTIVES</span>
                <span className="text-xs font-semibold text-indigo-400">{baselines?.leaseCash ? `$${baselines.leaseCash.toLocaleString()}` : '---'}</span>
              </div>
            </div>
            
            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center">
                <span className="text-xs text-slate-400 font-mono">TARGET DISCOUNT</span>
                <span className="text-xs font-semibold text-rose-400">{baselines?.reasonableDiscountPercent ? `${baselines.reasonableDiscountPercent}%` : '---'}</span>
              </div>
            </div>
          </div>
          {baselines?.marketMomentum && (
            <div className="mt-6 p-4 rounded-lg bg-indigo-500/10 border border-indigo-500/20 relative overflow-hidden">
              {baselines?.confidenceScore && (
                <div className="absolute top-0 right-0 bg-indigo-500 text-white text-[10px] font-bold px-2 py-1 rounded-bl-lg">
                  {baselines.confidenceScore}% CONFIDENCE
                </div>
              )}
              <p className="text-xs text-indigo-300 font-medium mb-1 mt-2">Market Momentum</p>
              <p className="text-[11px] text-slate-400 leading-relaxed">{baselines.marketMomentum}</p>
              <p className="text-[10px] text-slate-500 mt-2 font-mono border-t border-indigo-500/20 pt-2">{baselines.sourceNotes}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
