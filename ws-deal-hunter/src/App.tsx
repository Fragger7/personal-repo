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
      const totalDeals = deals.length;
      const unicornCount = deals.filter(d => d.deal_score >= 9.0).length;
      const highYieldCount = deals.filter(d => d.is_high_yield || (d.deal_score >= 8.5 && d.price <= 750)).length;
      
      const avgScore = totalDeals > 0 
        ? (deals.reduce((acc, d) => acc + d.deal_score, 0) / totalDeals).toFixed(1)
        : "0.0";
        
      setStats({
        totalDeals,
        unicornCount,
        highYieldCount,
        averageScore: parseFloat(avgScore),
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
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans antialiased selection:bg-emerald-500/30 selection:text-emerald-200">
      {/* Top Header */}
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

      {/* Main Layout Container */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {/* KPI Metrics Dashboard */}
        <KpiMetrics stats={stats} filteredCount={filteredDeals.length} />

        {/* Filter Controls Toolbar */}
        <FilterBar
          filters={filters}
          onChange={setFilters}
          onReset={handleResetFilters}
        />

        {/* Content Section */}
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-20 text-slate-400">
            <RefreshCw className="h-8 w-8 text-emerald-400 animate-spin mb-3" />
            <p className="text-sm">Loading workstation arbitrage deals...</p>
          </div>
        ) : filteredDeals.length === 0 ? (
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-12 text-center max-w-lg mx-auto my-12">
            <div className="h-12 w-12 rounded-full bg-slate-800 text-slate-400 flex items-center justify-center mx-auto mb-4">
              <Layers className="h-6 w-6" />
            </div>
            <h3 className="text-base font-bold text-white mb-1">
              No Listings Match Filter Parameters
            </h3>
            <p className="text-xs text-slate-400 mb-6 leading-relaxed">
              Try adjusting the Deal Score slider, increasing the maximum asking price, or clicking Reset to view all tracked workstation inventory.
            </p>
            <button
              onClick={handleResetFilters}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold transition"
            >
              Reset Filters
            </button>
          </div>
        ) : filters.viewMode === "grid" ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
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
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 border border-emerald-500/50 text-emerald-300 px-4 py-3 rounded-xl shadow-2xl flex items-center gap-3 text-xs font-semibold animate-in slide-in-from-bottom-5 duration-200">
          <Sparkles className="h-4 w-4 text-emerald-400 shrink-0" />
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
