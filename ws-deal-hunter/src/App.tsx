import React, { useState, useEffect, useCallback } from "react";
import { Header } from "./components/Header";
import { KpiMetrics } from "./components/KpiMetrics";
import { FilterBar } from "./components/FilterBar";
import { DealCard } from "./components/DealCard";
import { DealTable } from "./components/DealTable";
import { EvaluateModal } from "./components/EvaluateModal";
import { CodeExplorerModal } from "./components/CodeExplorerModal";
import { PushoverSettingsModal } from "./components/PushoverSettingsModal";
import { DealRecord, DashboardStats, FilterState } from "./types";
import { Sparkles, Layers, RefreshCw, Info, AlertTriangle } from "lucide-react";

export function App() {
  const [deals, setDeals] = useState<DealRecord[]>([]);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSyncing, setIsSyncing] = useState(false);
  const [isPushingGit, setIsPushingGit] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Modals state
  const [isEvaluateOpen, setIsEvaluateOpen] = useState(false);
  const [isCodeOpen, setIsCodeOpen] = useState(false);
  const [isNotifyOpen, setIsNotifyOpen] = useState(false);

  // Filter state
  const [filters, setFilters] = useState<FilterState>({
    minScore: 0.0,
    maxPrice: 2500,
    sources: ["ebay", "reddit", "swappa", "syndicated", "dell_refurbished", "microcenter", "bh_photo", "goodwill", "lenovo_outlet"],
    search: "",
    onlyHighYield: false,
    sortBy: "newest",
    viewMode: "grid",
  });

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((curr) => (curr === msg ? null : curr));
    }, 4000);
  };

  const fetchDeals = useCallback(async () => {
    try {
      // In production (Vercel), fetch directly from the raw GitHub JSON
      // In development (npm run dev), fetch from the local Express server
      const url = import.meta.env.PROD 
        ? "https://raw.githubusercontent.com/Fragger7/personal-repo/main/ws-deal-hunter/deals.json"
        : "/api/deals";

      const res = await fetch(url);
      const data = await res.json();
      
      // Handle both formats (raw array vs express object)
      if (Array.isArray(data)) {
        setDeals(data);
      } else if (data.success && Array.isArray(data.deals)) {
        setDeals(data.deals);
      }
    } catch (err) {
      console.error("Failed to fetch deals:", err);
    }
  }, []);

  const fetchStats = useCallback(async () => {
    try {
      if (import.meta.env.PROD) {
        // Vercel static fallback: calculate stats dynamically since Express isn't running
        // This will be updated by a separate effect based on the raw deals array
        return; 
      }
      
      const res = await fetch("/api/stats");
      const data = await res.json();
      if (data.success) {
        setStats(data);
      }
    } catch (err) {
      console.error("Failed to fetch stats:", err);
    }
  }, []);

  useEffect(() => {
    Promise.all([fetchDeals(), fetchStats()]).finally(() => {
      setIsLoading(false);
    });
  }, [fetchDeals, fetchStats]);

  // Vercel Dynamic Stats Calculator
  useEffect(() => {
    if (import.meta.env.PROD && deals.length > 0) {
      const total_deals = deals.length;
      const high_yield_deals = deals.filter(d => d.is_high_yield || (d.deal_score >= 8.5 && d.price <= 750)).length;
      
      const avg_profit = total_deals > 0 
        ? Math.round(deals.reduce((acc, d) => acc + (d.estimated_profit || 0), 0) / total_deals)
        : 0;
        
      const avg_margin_pct = total_deals > 0 
        ? Math.round(deals.reduce((acc, d) => acc + (d.arbitrage_margin_pct || 0), 0) / total_deals)
        : 0;
        
      const top_score = total_deals > 0 
        ? Math.max(...deals.map(d => d.deal_score || 0))
        : 0;

      const source_breakdown = deals.reduce((acc: Record<string, number>, d) => {
        acc[d.source] = (acc[d.source] || 0) + 1;
        return acc;
      }, {});
        
      setStats({
        total_deals,
        high_yield_deals,
        avg_profit,
        avg_margin_pct,
        top_score,
        source_breakdown,
        lastSync: new Date().toISOString()
      });
    }
  }, [deals]);

  const handleSyncEndpoints = async () => {
    if (import.meta.env.PROD) {
      showToast("⚡ Automated background daemon is running 24/7 on GitHub Actions. Manual sync is disabled on Vercel.");
      return;
    }
    
    setIsSyncing(true);
    try {
      const res = await fetch("/api/collect", { method: "POST" });
      const data = await res.json();
      if (data.success) {
        showToast("⚡ Syndication cycle complete! Updated active listings.");
        await Promise.all([fetchDeals(), fetchStats()]);
      } else {
        showToast(`Sync error: ${data.error}`);
      }
    } catch (err: any) {
      showToast(`Sync error: ${err.message}`);
    } finally {
      setIsSyncing(false);
    }
  };

  const handleGitPush = async () => {
    if (import.meta.env.PROD) {
      showToast("🎉 You are on the live site! Commits happen automatically via GitHub Actions.");
      return;
    }

    setIsPushingGit(true);
    try {
      const res = await fetch("/api/git/push", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: `feat(ws-deal-hunter): update workstation deal hunter at ${new Date().toLocaleTimeString()}`,
        }),
      });
      const data = await res.json();
      if (data.success) {
        showToast("🎉 Pushed to GitHub repo (Fragger7/personal-repo:ws-deal-hunter)!");
      } else {
        showToast(`Git push failed: ${data.error || data.stdout}`);
      }
    } catch (err: any) {
      showToast(`Git push failed: ${err.message}`);
    } finally {
      setIsPushingGit(false);
    }
  };

  const handleSendPush = async (deal: DealRecord) => {
    if (import.meta.env.PROD) {
      showToast("📱 Automated alerts are handled securely by the backend daemon.");
      return;
    }

    try {
      const res = await fetch("/api/notify/pushover", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dealId: deal.id }),
      });
      const data = await res.json();
      if (data.success) {
        showToast(`📱 Pushover alert dispatched for ${deal.title.slice(0, 30)}...`);
      } else {
        showToast(`Pushover error: ${data.error}`);
      }
    } catch (err: any) {
      showToast(`Notification failed: ${err.message}`);
    }
  };

  const handleDealEvaluated = (newDeal: DealRecord) => {
    setDeals((prev) => [newDeal, ...prev.filter((d) => d.id !== newDeal.id)]);
    fetchStats();
    showToast(`Evaluated: ${newDeal.title.slice(0, 30)}... (Score: ${newDeal.deal_score}/10)`);
  };

  const handleResetFilters = () => {
    setFilters({
      minScore: 0.0,
      maxPrice: 2500,
      sources: ["ebay", "reddit", "swappa"],
      search: "",
      onlyHighYield: false,
      sortBy: "score",
      viewMode: filters.viewMode,
    });
  };

  // Client-side filtering & sorting
  const filteredDeals = deals.filter((d) => {
    if (d.deal_score < filters.minScore) return false;
    if (d.price > filters.maxPrice) return false;
    
    // Fuzzy source matching to handle 'reddit (r/hardwareswap)' matching 'reddit'
    const matchesSource = filters.sources.length === 0 || filters.sources.some(s => 
      d.source.toLowerCase().includes(s.toLowerCase()) || 
      (s === 'swappa' && d.source === 'syndicated')
    );
    if (!matchesSource) return false;

    if (filters.onlyHighYield && !d.is_high_yield && !(d.deal_score >= 8.5 && d.price <= 750)) return false;
    if (filters.search) {
      const query = filters.search.toLowerCase();
      const searchable = `${d.title} ${d.specs?.cpu || ""} ${d.specs?.gpu || ""} ${d.specs?.ram_gb || ""}gb ${d.summary || ""} ${d.source}`.toLowerCase();
      if (!searchable.includes(query)) return false;
    }
    return true;
  });

  filteredDeals.sort((a, b) => {
    if (filters.sortBy === "score") return b.deal_score - a.deal_score;
    if (filters.sortBy === "profit") return b.estimated_profit - a.estimated_profit;
    if (filters.sortBy === "price_low") return a.price - b.price;
    if (filters.sortBy === "newest") return new Date(b.evaluated_at || 0).getTime() - new Date(a.evaluated_at || 0).getTime();
    return 0;
  });

  return (
    <div className="min-h-screen w-full bg-[#050505] text-[#e2e8f0] font-sans antialiased selection:bg-emerald-500/30 selection:text-emerald-200">
      
      {/* Subtle background tech grid and CRT scanline */}
      <div className="fixed inset-0 pointer-events-none opacity-[0.03] z-0" style={{ backgroundImage: 'linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)', backgroundSize: '50px 50px' }} />
      <div className="crt-scanline fixed" />

      {/* Top Header */}
      <div className="relative z-20 border-b border-[#222] bg-[#0a0a0abf] backdrop-blur-md sticky top-0">
        <Header
          onSync={handleSyncEndpoints}
          isSyncing={isSyncing}
          onOpenEvaluate={() => setIsEvaluateOpen(true)}
          onOpenCode={() => setIsCodeOpen(true)}
          onOpenNotify={() => setIsNotifyOpen(true)}
          onGitPush={handleGitPush}
          isPushingGit={isPushingGit}
          totalDeals={deals.length}
        />
        
        {/* Horizontal Ribbon Filter Bar */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <FilterBar
            filters={filters}
            onChange={setFilters}
            onReset={handleResetFilters}
          />
        </div>
      </div>

      {/* Main Layout Container */}
      <main className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        
        {/* KPI Metrics Dashboard */}
        <KpiMetrics stats={stats} filteredCount={filteredDeals.length} />

        <div className="flex items-center justify-between mb-8 mt-4 pb-2 border-b border-[#222]">
          <h2 className="text-[11px] font-bold uppercase tracking-widest text-[#666] auto-glitch">
            <span className="text-emerald-500 tech-text">{filteredDeals.length}</span> Active Opportunities
          </h2>
          <div className="flex gap-2">
            <span className="inline-block w-2 h-2 bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.8)] animate-pulse" />
          </div>
        </div>

        {/* Content Section */}
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-32 text-[#555]">
            <RefreshCw className="h-10 w-10 text-emerald-500 animate-spin mb-4" />
            <p className="text-[10px] font-bold uppercase tracking-widest">Intercepting Global Streams...</p>
          </div>
        ) : filteredDeals.length === 0 ? (
          <div className="industrial-panel p-12 text-center max-w-lg mx-auto my-20">
            <div className="h-12 w-12 border border-[#333] bg-[#111] text-[#666] flex items-center justify-center mx-auto mb-6">
              <Layers className="h-5 w-5" />
            </div>
            <h3 className="text-[13px] font-bold text-[#aaa] uppercase tracking-widest mb-3">
              No Signals Detected
            </h3>
            <p className="text-[11px] text-[#666] mb-8 leading-relaxed font-medium">
              Adjust parameters to broaden the search matrix.
            </p>
            <button
              onClick={handleResetFilters}
              className="px-5 py-2.5 bg-[#111] hover:bg-[#1a1a1a] text-emerald-500 border border-[#333] hover:border-emerald-500/50 text-[10px] font-bold tracking-widest uppercase transition-all"
            >
              Reset Matrix Filters
            </button>
          </div>
        ) : filters.viewMode === "grid" ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {filteredDeals.map((deal) => (
              <DealCard
                key={deal.id}
                deal={deal}
                onSendPush={handleSendPush}
              />
            ))}
          </div>
        ) : (
          <DealTable
            deals={filteredDeals}
            onSendPush={handleSendPush}
          />
        )}
      </main>

      {/* Floating Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-[#111] border border-emerald-500/50 text-emerald-500 px-5 py-4 shadow-2xl flex items-center gap-3 text-[11px] uppercase tracking-widest font-bold animate-in slide-in-from-bottom-5 duration-200">
          <Sparkles className="h-4 w-4 text-emerald-500 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Modals */}
      <EvaluateModal
        isOpen={isEvaluateOpen}
        onClose={() => setIsEvaluateOpen(false)}
        onDealEvaluated={handleDealEvaluated}
      />

      <CodeExplorerModal
        isOpen={isCodeOpen}
        onClose={() => setIsCodeOpen(false)}
      />

      <PushoverSettingsModal
        isOpen={isNotifyOpen}
        onClose={() => setIsNotifyOpen(false)}
        sampleDeal={filteredDeals[0] || deals[0]}
      />
    </div>
  );
}

export default App;
