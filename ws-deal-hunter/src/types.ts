export interface HardwareSpecs {
  cpu: string;
  ram_gb: number;
  ssd_gb: number;
  gpu: string;
  screen: string;
  condition?: string;
}

export interface DealRecord {
  id: string;
  source: string;
  title: string;
  price: number;
  url: string;
  specs: HardwareSpecs;
  fair_market_value: number;
  estimated_profit: number;
  arbitrage_margin_pct: number;
  deal_score: number;
  summary: string;
  actionable_recommendation: string;
  confidence_score: number;
  seller: string;
  location: string;
  created_utc: string;
  evaluated_at: string;
  alerted: boolean;
  is_high_yield: boolean;
  is_auction?: boolean;
  bid_count?: number | null;
  time_left?: string | null;
}

export interface DashboardStats {
  total_deals: number;
  high_yield_deals: number;
  avg_profit: number;
  avg_margin_pct: number;
  top_score: number;
  source_breakdown: Record<string, number>;
}

export interface FilterState {
  minScore: number;
  maxPrice: number;
  sources: string[];
  search: string;
  onlyHighYield: boolean;
  sortBy: "score" | "profit" | "price_low" | "newest";
  viewMode: "grid" | "table";
}
