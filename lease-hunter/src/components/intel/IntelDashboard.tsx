import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Activity, Database, Zap, Calculator, Car, ChevronRight, CheckCircle2, AlertTriangle, ExternalLink, Copy, Check, Send, Link as LinkIcon, RefreshCw, X } from 'lucide-react';

export default function IntelDashboard({ onDealSelect }: { onDealSelect?: (deal: any) => void }) {
  const [isScraping, setIsScraping] = useState(false);
  const [crawlStep, setCrawlStep] = useState<number>(0); 
  const [step, setStep] = useState(0); 
  const [baselines, setBaselines] = useState<any>(() => {
    try {
      const saved = localStorage.getItem('lease_baselines');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });
  const [showVerificationModal, setShowVerificationModal] = useState(false);
  const [copiedInquiry, setCopiedInquiry] = useState(false);
  const [isSendingTelegram, setIsSendingTelegram] = useState(false);
  const [telegramStatus, setTelegramStatus] = useState<'idle' | 'success' | 'failed'>('idle');
  const [rateFindrUrl, setRateFindrUrl] = useState('');
  const [isParsingRateFindr, setIsParsingRateFindr] = useState(false);
  const [rateFindrSuccess, setRateFindrSuccess] = useState(false);
  const [inventory, setInventory] = useState<any[]>(() => {
    try {
      const saved = localStorage.getItem('lease_inventory');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });
  const [sortOption, setSortOption] = useState<'none' | 'days-desc' | 'price-asc' | 'discount-desc' | 'distance-asc'>('none');
  const [sourceFilter, setSourceFilter] = useState<'all' | 'dealer' | 'caredge' | 'cargurus'>('all');
  const [error, setError] = useState('');

  // Search Parameters (Persisted in localStorage)
  const [searchParams, setSearchParams] = useState(() => {
    try {
      const saved = localStorage.getItem('lease_search_params');
      return saved ? JSON.parse(saved) : {
        make: 'Kia',
        model: 'EV9',
        trim: 'all',
        year: '2026',
        zipCode: '78665',
        radius: 50,
      };
    } catch {
      return {
        make: 'Kia',
        model: 'EV9',
        trim: 'all',
        year: '2026',
        zipCode: '78665',
        radius: 50,
      };
    }
  });

  React.useEffect(() => {
    localStorage.setItem('lease_inventory', JSON.stringify(inventory));
  }, [inventory]);

  React.useEffect(() => {
    localStorage.setItem('lease_search_params', JSON.stringify(searchParams));
  }, [searchParams]);

  React.useEffect(() => {
    if (baselines) {
      localStorage.setItem('lease_baselines', JSON.stringify(baselines));
    }
  }, [baselines]);

  const handleCopyInquiry = () => {
    if (!baselines?.inquiryText) return;
    navigator.clipboard.writeText(baselines.inquiryText);
    setCopiedInquiry(true);
    setTimeout(() => setCopiedInquiry(false), 2500);
  };

  const handleSendTelegramNotice = async () => {
    setIsSendingTelegram(true);
    setTelegramStatus('idle');
    try {
      const res = await fetch('/api/scrape/send-baseline-alert', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...searchParams,
          inquiryText: baselines?.inquiryText,
          edmundsUrl: baselines?.edmundsUrl
        })
      });
      const data = await res.json();
      if (data.success) {
        setTelegramStatus('success');
      } else {
        setTelegramStatus('failed');
      }
    } catch {
      setTelegramStatus('failed');
    } finally {
      setIsSendingTelegram(false);
      setTimeout(() => setTelegramStatus('idle'), 4000);
    }
  };

  const handleImportRateFindr = async () => {
    if (!rateFindrUrl.trim()) return;
    setIsParsingRateFindr(true);
    setRateFindrSuccess(false);
    try {
      const res = await fetch('/api/scrape/parse-ratefindr', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: rateFindrUrl.trim() })
      });
      if (!res.ok) throw new Error('Invalid Rate Findr or Leasehackr URL');
      const data = await res.json();
      if (data.success) {
        setBaselines((prev: any) => ({
          ...prev,
          moneyFactor: data.data.moneyFactor,
          residualValue: data.data.residualPercent,
          leaseCash: data.data.leaseCash,
          reasonableDiscountPercent: data.data.discountPercent,
          confidenceScore: 99,
          sourceNotes: `Direct Leasehackr Rate Findr Ingestion • Verified baseline`,
          needsVerification: false,
          isRegionalApproximation: false
        }));
        setRateFindrSuccess(true);
        setRateFindrUrl('');
        setTimeout(() => setRateFindrSuccess(false), 3000);
      }
    } catch (err: any) {
      alert(err.message || 'Failed to parse Rate Findr URL');
    } finally {
      setIsParsingRateFindr(false);
    }
  };

  const initializeAggregator = async () => {
    setIsScraping(true);
    setCrawlStep(1);
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
      setCrawlStep(2);
      setStep(2);

      // 2. Search Inventory
      const resInv = await fetch('/api/scrape/search-inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(searchParams)
      });
      if (!resInv.ok) {
        const errData = await resInv.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to fetch inventory');
      }
      const dataInv = await resInv.json();
      setInventory(dataInv.results || []);
      setCrawlStep(3);
      setStep(3);

    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Scraping failed');
    } finally {
      setIsScraping(false);
    }
  };

  const triggerLocalCrawl = async () => {
    setIsScraping(true);
    setCrawlStep(1);
    setStep(1);
    setError('');
    try {
      // 1. Extract Baselines
      const resBase = await fetch('/api/scrape/extract-baselines', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(searchParams)
      });
      if (resBase.ok) {
        const dataBase = await resBase.json();
        setBaselines(dataBase);
      }
      
      setCrawlStep(2);
      setStep(2);

      // 2. Trigger Playwright 3-Node Network Crawl with live parameters
      const resCrawl = await fetch('/api/scrape/trigger-crawl', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          zip: searchParams.zipCode,
          distance: searchParams.radius,
          make: searchParams.make,
          model: searchParams.model,
          trim: searchParams.trim,
          year: searchParams.year
        })
      });
      
      if (!resCrawl.ok) {
        const errData = await resCrawl.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to trigger local crawler');
      }

      setCrawlStep(3);
      const crawlData = await resCrawl.json();
      setInventory(crawlData.inventory || []);
      setStep(3);
    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Local crawler scan failed');
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
              Live Local Inventory Feed (50-Mile Radius)
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
                        onChange={(e) => setSearchParams((prev: any) => ({ ...prev, make: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Model</label>
                      <input 
                        type="text" 
                        value={searchParams.model}
                        onChange={(e) => setSearchParams((prev: any) => ({ ...prev, model: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Target Trim</label>
                      <select 
                        value={searchParams.trim}
                        onChange={(e) => setSearchParams((prev: any) => ({ ...prev, trim: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50 appearance-none"
                      >
                        <option value="all">All Trims</option>
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
                        onChange={(e) => setSearchParams((prev: any) => ({ ...prev, radius: Number(e.target.value) }))}
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
                        onChange={(e) => setSearchParams((prev: any) => ({ ...prev, year: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-500 mb-1">Target ZIP</label>
                      <input 
                        type="text" 
                        value={searchParams.zipCode}
                        onChange={(e) => setSearchParams((prev: any) => ({ ...prev, zipCode: e.target.value }))}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-indigo-500/50"
                      />
                    </div>
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row gap-3 mt-8 w-full">
                  <button 
                    onClick={triggerLocalCrawl}
                    disabled={isScraping}
                    className="flex-1 flex justify-center items-center gap-2 px-5 py-3 rounded-lg bg-emerald-500 text-white text-sm font-medium hover:bg-emerald-600 transition-colors disabled:opacity-50 shadow-lg shadow-emerald-500/10"
                  >
                    <Zap className="h-4 w-4" />
                    {isScraping ? 'Triangulating Multi-Node Network...' : `🔄 Scan 3-Node Network (${searchParams.radius}mi)`}
                  </button>
                  <button 
                    onClick={initializeAggregator}
                    disabled={isScraping}
                    className="flex-1 flex justify-center items-center gap-2 px-5 py-3 rounded-lg bg-indigo-500 text-white text-sm font-medium hover:bg-indigo-600 transition-colors disabled:opacity-50"
                  >
                    <Activity className="h-4 w-4" />
                    Deep Aggregator Scan
                  </button>
                </div>
              </div>
            )}

            {isScraping && (
              <div className="mb-6 p-4 rounded-xl border border-indigo-500/30 bg-indigo-950/40 text-left animate-in fade-in space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <div className="w-4 h-4 rounded-full border-2 border-indigo-400 border-t-transparent animate-spin" />
                    <span className="text-xs font-semibold text-indigo-300">
                      Multi-Node Sourcing in Progress for {searchParams.year} {searchParams.make} {searchParams.model} (ZIP: {searchParams.zipCode})
                    </span>
                  </div>
                  <span className="text-[10px] font-mono text-indigo-400">Step {crawlStep} of 3</span>
                </div>
                <div className="grid grid-cols-3 gap-2 pt-1 text-[11px] font-mono">
                  <div className={`p-2 rounded border flex items-center gap-1.5 ${crawlStep >= 1 ? 'bg-indigo-500/10 border-indigo-500/30 text-indigo-300' : 'bg-slate-900/50 border-white/5 text-slate-500'}`}>
                    <span className="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
                    1. CarEdge REST API
                  </div>
                  <div className={`p-2 rounded border flex items-center gap-1.5 ${crawlStep >= 2 ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300' : 'bg-slate-900/50 border-white/5 text-slate-500'}`}>
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                    2. Headless Dealer Nodes
                  </div>
                  <div className={`p-2 rounded border flex items-center gap-1.5 ${crawlStep >= 3 ? 'bg-amber-500/10 border-amber-500/30 text-amber-300' : 'bg-slate-900/50 border-white/5 text-slate-500'}`}>
                    <span className="w-1.5 h-1.5 rounded-full bg-amber-400"></span>
                    3. CarGurus CDP Interceptor
                  </div>
                </div>
              </div>
            )}

            {step > 0 && !error && (
              <div className="space-y-6">
                <div className="flex flex-col gap-3">
                  <div className={`p-4 rounded-xl border flex items-center justify-between ${step >= 1 ? 'border-indigo-500/30 bg-indigo-500/10' : 'border-slate-800 bg-slate-900 opacity-50'}`}>
                    <div className="flex items-center gap-3">
                      {step > 1 ? <CheckCircle2 className="h-5 w-5 text-indigo-400" /> : <div className="w-2 h-2 ml-1.5 rounded-full bg-indigo-400 animate-pulse" />}
                      <div>
                        <p className={`text-sm font-medium ${step >= 1 ? 'text-indigo-300' : 'text-slate-500'}`}>1. Extract Baselines & Captive Matrices</p>
                        {step === 1 && <p className="text-xs text-indigo-400/70 mt-1">Retrieving verified KFA Tier 1 money factors, residuals, and lease cash...</p>}
                      </div>
                    </div>
                  </div>

                  <div className={`p-4 rounded-xl border flex items-center justify-between ${step >= 2 ? 'border-blue-500/30 bg-blue-500/10' : 'border-slate-800 bg-slate-900 opacity-50'}`}>
                    <div className="flex items-center gap-3">
                      {step > 2 ? <CheckCircle2 className="h-5 w-5 text-blue-400" /> : step === 2 ? <div className="w-2 h-2 ml-1.5 rounded-full bg-blue-400 animate-pulse" /> : <div className="w-2 h-2 ml-1.5 rounded-full bg-slate-700" />}
                      <div>
                        <p className={`text-sm font-medium ${step >= 2 ? 'text-blue-300' : 'text-slate-500'}`}>2. Triangulated Multi-Node Inventory Sweep</p>
                        {step === 2 && <p className="text-xs text-blue-400/70 mt-1">Scanning CarEdge API, Dealer-Direct showrooms, and CarGurus CDP stream...</p>}
                      </div>
                    </div>
                  </div>

                  <div className={`p-4 rounded-xl border flex items-center justify-between ${step >= 3 ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-slate-800 bg-slate-900 opacity-50'}`}>
                    <div className="flex items-center gap-3">
                      {step === 3 ? <CheckCircle2 className="h-5 w-5 text-emerald-400" /> : <div className="w-2 h-2 ml-1.5 rounded-full bg-slate-700" />}
                      <div>
                        <p className={`text-sm font-medium ${step >= 3 ? 'text-emerald-300' : 'text-slate-500'}`}>3. Target Analysis & Deduplication</p>
                        {step === 3 && <p className="text-xs text-emerald-400/70 mt-1">Successfully merged and verified {inventory.length} unique leasable targets.</p>}
                      </div>
                    </div>
                  </div>
                </div>

                {inventory.length > 0 && (
                  <div className="space-y-3 mt-8">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 border-b border-white/5 pb-3">
                      <div className="flex flex-wrap items-center gap-3">
                        <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                          Acquired Targets <span className="ml-2 px-2 py-0.5 bg-indigo-500/20 text-indigo-400 rounded-full text-[10px]">{inventory.length}</span>
                        </h4>
                        <div className="flex items-center gap-2">
                          <select
                            value={sourceFilter}
                            onChange={(e) => setSourceFilter(e.target.value as any)}
                            className="text-xs bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-slate-300 outline-none focus:border-indigo-500/50"
                          >
                            <option value="all">Source: All ({inventory.length})</option>
                            <option value="dealer">Dealer Direct ({inventory.filter(i => (i.source || '').includes('Dealer')).length})</option>
                            <option value="cargurus">CarGurus ({inventory.filter(i => (i.source || '').includes('CarGurus')).length})</option>
                            <option value="caredge">CarEdge ({inventory.filter(i => !(i.source || '').includes('Dealer') && !(i.source || '').includes('CarGurus')).length})</option>
                          </select>
                          <select
                            value={sortOption}
                            onChange={(e) => setSortOption(e.target.value as any)}
                            className="text-xs bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-slate-300 outline-none focus:border-indigo-500/50"
                          >
                            <option value="none">Sort: Default</option>
                            <option value="distance-asc">Distance (Closest First)</option>
                            <option value="discount-desc">Highest Discount ($ Off)</option>
                            <option value="price-asc">Site Price (Low to High)</option>
                            <option value="days-desc">Days on Lot (High to Low)</option>
                          </select>
                        </div>
                      </div>
                      <button 
                        onClick={() => setInventory([])}
                        className="text-xs text-red-400 hover:text-red-300 transition-colors self-end sm:self-auto"
                      >
                        Clear Inventory
                      </button>
                    </div>
                    {([...inventory]
                      .filter((item) => {
                        if (sourceFilter === 'dealer') return (item.source || '').includes('Dealer');
                        if (sourceFilter === 'cargurus') return (item.source || '').includes('CarGurus');
                        if (sourceFilter === 'caredge') return !(item.source || '').includes('Dealer') && !(item.source || '').includes('CarGurus');
                        return true;
                      })
                      .sort((a, b) => {
                        if (sortOption === 'distance-asc') return (a.distanceMiles || 999) - (b.distanceMiles || 999);
                        if (sortOption === 'discount-desc') return (b.discount || 0) - (a.discount || 0);
                        if (sortOption === 'days-desc') return (b.daysOnLot || 0) - (a.daysOnLot || 0);
                        if (sortOption === 'price-asc') return ((a.listingPrice || a.msrp) || 0) - ((b.listingPrice || b.msrp) || 0);
                        return 0;
                      })).map((inv, idx) => (
                      <button
                        key={idx}
                        onClick={() => {
                          if (onDealSelect) {
                            onDealSelect({
                              zipCode: searchParams.zipCode,
                              msrp: inv.msrp,
                              sellingPrice: inv.listingPrice || inv.msrp,
                              discount: inv.discountPercent ? parseFloat(inv.discountPercent) : (baselines?.reasonableDiscountPercent || 6.5),
                              rebates: baselines?.leaseCash || 0,
                              term: 36,
                              moneyFactor: baselines?.moneyFactor || 0.00210,
                              residualPercent: baselines?.residualValue || 64,
                              dealerName: inv.dealerName,
                              vin: inv.vin
                            });
                          }
                        }}
                        className="w-full text-left p-4 rounded-xl bg-slate-950 border border-white/5 hover:border-indigo-500/50 hover:bg-slate-900 transition-all flex flex-col md:flex-row md:items-center justify-between gap-4 group"
                      >
                        <div className="flex items-start gap-4">
                          <div className="bg-slate-900 p-3 rounded-lg group-hover:bg-indigo-500/20 transition-colors shrink-0 mt-0.5">
                            <Car className="h-5 w-5 text-slate-400 group-hover:text-indigo-400" />
                          </div>
                          <div>
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="font-semibold text-slate-200">{inv.dealerName}</p>
                              {inv.source && (
                                <span className={`text-[9px] px-2 py-0.5 rounded font-mono ${
                                  inv.source.includes('Dealer') 
                                    ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                                    : inv.source.includes('CarGurus')
                                    ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                                    : 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20'
                                }`}>
                                  {inv.source}
                                </span>
                              )}
                              {inv.discount > 0 && (
                                <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                                  -${inv.discount.toLocaleString()} ({inv.discountPercent}% Off)
                                </span>
                              )}
                            </div>
                            <p className="text-xs text-slate-400 font-mono mt-1">
                              {inv.color || 'Dark Metallic'} • 📍 {inv.distance || `${inv.distanceMiles || 15} miles`} • ⏱️ {inv.daysOnLot} Days on Lot
                            </p>
                            <p className="text-[10px] text-slate-500 font-mono mt-0.5">VIN: {inv.vin}</p>
                          </div>
                        </div>
                        <div className="flex items-center justify-between md:justify-end gap-6 shrink-0 pt-2 md:pt-0 border-t md:border-t-0 border-white/5">
                          <div className="text-left md:text-right">
                            <div className="flex items-baseline md:justify-end gap-2">
                              <p className="text-base font-bold text-emerald-400">${(inv.listingPrice || inv.msrp).toLocaleString()}</p>
                              {inv.discount > 0 && (
                                <p className="text-xs text-slate-500 line-through">${inv.msrp.toLocaleString()}</p>
                              )}
                            </div>
                            <p className="text-[10px] text-slate-500 uppercase tracking-wider">
                              {inv.discount > 0 ? 'Dealer Sale Price' : 'MSRP'}
                            </p>
                          </div>
                          {inv.listingUrl || inv.url || inv.link ? (
                            <a 
                              href={inv.listingUrl || inv.url || inv.link} 
                              target="_blank" 
                              rel="noopener noreferrer"
                              onClick={(e) => e.stopPropagation()}
                              className="text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-500 px-4 py-2 rounded-lg transition-colors whitespace-nowrap shadow-lg shadow-indigo-500/30 flex items-center gap-1.5"
                            >
                              <span>🌐 View Deal</span>
                            </a>
                          ) : (
                            <span className="text-[10px] text-slate-500 border border-slate-700 px-2 py-1 rounded">
                              Direct VDP
                            </span>
                          )}
                          <ChevronRight className="h-5 w-5 text-slate-600 group-hover:text-indigo-400 transition-colors hidden md:block" />
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
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium text-slate-200 flex items-center gap-2">
              <Calculator className="h-4 w-4 text-blue-400" />
              Live Market Base Programs
            </h3>
            {baselines?.programMonth && (
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
                {baselines.programMonth}
              </span>
            )}
          </div>

          {baselines?.regionalZone && (
            <div className="mb-4 text-[11px] font-mono text-slate-400 flex items-center gap-1.5 bg-slate-950 p-2 rounded-lg border border-white/5">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
              {baselines.regionalZone}
            </div>
          )}

          {baselines && (
            <div className={`mb-4 p-3 rounded-xl border text-xs flex items-center justify-between ${
              baselines.needsVerification 
                ? 'bg-amber-500/10 border-amber-500/30 text-amber-300' 
                : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
            }`}>
              <div className="flex items-center gap-2">
                {baselines.needsVerification ? <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" /> : <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />}
                <span>
                  {baselines.needsVerification ? 'Estimated / Regional Approximation' : 'Active Captive Program Verified'}
                </span>
              </div>
              <button
                onClick={() => setShowVerificationModal(true)}
                className="px-2 py-1 bg-white/10 hover:bg-white/20 rounded font-medium text-[10px] text-white transition-colors"
              >
                {baselines.needsVerification ? 'Post Inquiry' : 'View Inquiry'}
              </button>
            </div>
          )}

          <div className="space-y-4">
            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center mb-1">
                <span className="text-xs text-slate-400 font-mono">MONEY FACTOR (BUY RATE)</span>
                <span className="text-xs font-semibold text-emerald-400">{baselines?.moneyFactor ? baselines.moneyFactor : '---'}</span>
              </div>
            </div>
            
            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center mb-1">
                <span className="text-xs text-slate-400 font-mono">RESIDUAL VALUE</span>
                <span className="text-xs font-semibold text-blue-400">{baselines?.residualValue ? `${baselines.residualValue}%` : '---'}</span>
              </div>
            </div>

            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center mb-1">
                <span className="text-xs text-slate-400 font-mono">LEASE CASH / REBATES</span>
                <span className="text-xs font-semibold text-indigo-400">{baselines?.leaseCash ? `$${baselines.leaseCash.toLocaleString()}` : '---'}</span>
              </div>
            </div>
            
            <div className="bg-slate-950 rounded-lg p-3 border border-white/5">
              <div className="flex justify-between items-center">
                <span className="text-xs text-slate-400 font-mono">TARGET PRE-INCENTIVE %</span>
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

          {/* Quick Rate Findr Ingestion */}
          <div className="mt-6 pt-4 border-t border-white/5">
            <label className="block text-xs font-medium text-slate-400 mb-2 flex items-center gap-1.5">
              <LinkIcon className="w-3.5 h-3.5 text-indigo-400" />
              Import Leasehackr / Rate Findr Link
            </label>
            <div className="flex gap-2">
              <input
                type="text"
                value={rateFindrUrl}
                onChange={(e) => setRateFindrUrl(e.target.value)}
                placeholder="https://leasehackr.com/calculator?..."
                className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-indigo-500/50"
              />
              <button
                onClick={handleImportRateFindr}
                disabled={isParsingRateFindr || !rateFindrUrl.trim()}
                className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white rounded-lg text-xs font-medium transition-colors whitespace-nowrap flex items-center gap-1"
              >
                {isParsingRateFindr ? <RefreshCw className="w-3 h-3 animate-spin" /> : rateFindrSuccess ? <Check className="w-3 h-3 text-emerald-300" /> : 'Import'}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Edmunds Forum Inquiry & Verification Modal */}
      <AnimatePresence>
        {showVerificationModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              className="bg-slate-900 border border-slate-700 rounded-2xl max-w-xl w-full p-6 shadow-2xl relative text-left"
            >
              <button
                onClick={() => setShowVerificationModal(false)}
                className="absolute top-4 right-4 text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>

              <div className="flex items-center gap-3 mb-4">
                <div className="p-2.5 rounded-xl bg-amber-500/20 text-amber-400 border border-amber-500/30">
                  <AlertTriangle className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-100">Live Captive Rate Verification</h3>
                  <p className="text-xs text-slate-400 font-mono">
                    Target: {searchParams.year} {searchParams.make} {searchParams.model} {searchParams.trim} (ZIP {searchParams.zipCode})
                  </p>
                </div>
              </div>

              <p className="text-xs text-slate-300 leading-relaxed mb-4">
                To guarantee you are negotiating with the exact bank-level <b>Buy Rate Money Factor</b> and capture any unadvertised manufacturer bonus cash, post this exact question in the active Edmunds community thread. Moderators typically answer within 1-2 hours.
              </p>

              <div className="bg-slate-950 p-4 rounded-xl border border-white/10 mb-4 relative">
                <label className="block text-[10px] font-mono text-slate-500 uppercase mb-1">Pre-Formatted Forum Inquiry</label>
                <p className="text-xs text-emerald-300 font-mono whitespace-pre-wrap leading-relaxed select-all">
                  {baselines?.inquiryText || `Hi moderators, could you please provide the current Buy Rate MF, RV%, and total Lease Cash for a ${searchParams.year} ${searchParams.make} ${searchParams.model} ${searchParams.trim} in ZIP ${searchParams.zipCode} for 36mo/10k and 24mo/10k? Thank you!`}
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
                <button
                  onClick={handleCopyInquiry}
                  className="flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-lg shadow-indigo-600/30 transition-all"
                >
                  {copiedInquiry ? (
                    <>
                      <Check className="w-4 h-4 text-emerald-300" />
                      <span>Copied to Clipboard!</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-4 h-4" />
                      <span>Copy Inquiry Text</span>
                    </>
                  )}
                </button>

                <a
                  href={baselines?.edmundsUrl || 'https://forums.edmunds.com/discussion/70165/kia/ev9/2026-kia-ev9-lease-deals-incentives-rebates-and-prices'}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-600 text-xs font-semibold transition-all"
                >
                  <ExternalLink className="w-4 h-4 text-blue-400" />
                  <span>Open Edmunds Thread ↗</span>
                </a>
              </div>

              <div className="border-t border-white/10 pt-4 flex items-center justify-between">
                <span className="text-[11px] text-slate-400">Want an alert on your phone?</span>
                <button
                  onClick={handleSendTelegramNotice}
                  disabled={isSendingTelegram}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-500/20 hover:bg-blue-500/30 border border-blue-500/30 text-blue-300 rounded-lg text-xs font-medium transition-colors disabled:opacity-50"
                >
                  {isSendingTelegram ? (
                    <RefreshCw className="w-3 h-3 animate-spin" />
                  ) : telegramStatus === 'success' ? (
                    <>
                      <Check className="w-3 h-3 text-emerald-400" />
                      <span>Sent to Telegram!</span>
                    </>
                  ) : telegramStatus === 'failed' ? (
                    <>
                      <AlertTriangle className="w-3 h-3 text-red-400" />
                      <span>Check Bot Config</span>
                    </>
                  ) : (
                    <>
                      <Send className="w-3 h-3 text-blue-400" />
                      <span>Push Prompt to Telegram</span>
                    </>
                  )}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
