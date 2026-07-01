import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Radar, Calculator, MessageSquare, Car, 
  ChevronRight, Activity, Database, Bot, Zap
} from 'lucide-react';
import IntelDashboard from './components/intel/IntelDashboard';
import TaxSimulator from './components/TaxSimulator';
import OutreachCRM from './components/outreach/OutreachCRM';

export default function App() {
  const [activeTab, setActiveTab] = useState<'intel' | 'structuring' | 'outreach'>('intel');
  const [dealConfig, setDealConfig] = useState<any>(null);

  const handleDealSelect = (deal: any) => {
    setDealConfig(deal);
    setActiveTab('structuring');
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans selection:bg-indigo-600/30">
      {/* Top Navigation Bar */}
      <nav className="border-b border-white/5 bg-slate-950/50 backdrop-blur-xl sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="bg-indigo-500/10 p-2 rounded-lg border border-indigo-500/20">
              <Bot className="h-5 w-5 text-indigo-400" />
            </div>
            <div>
              <h1 className="font-display font-semibold text-sm text-slate-200 tracking-tight">Universal Lease Engine</h1>
              <p className="text-[10px] text-indigo-400 font-mono tracking-widest uppercase">Autonomous Broker Protocol</p>
            </div>
          </div>
          
          <div className="flex items-center gap-4 bg-slate-900/50 p-1.5 rounded-full border border-white/5">
            {[
              { id: 'intel', label: 'Market Intel', icon: Radar },
              { id: 'structuring', label: 'Deal Engine', icon: Calculator },
              { id: 'outreach', label: 'Outreach CRM', icon: MessageSquare }
            ].map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as typeof activeTab)}
                  className={`flex items-center gap-2 px-4 py-2 rounded-full text-xs font-medium transition-all ${
                    isActive 
                      ? 'bg-indigo-500/10 text-indigo-300 border border-indigo-500/20' 
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50 border border-transparent'
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {tab.label}
                </button>
              );
            })}
          </div>
          
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-mono">
              <div className="w-1.5 h-1.5 bg-emerald-400 rounded-full animate-pulse" />
              SYSTEM ONLINE
            </div>
          </div>
        </div>
      </nav>

      {/* Main Content Area */}
      <main className="max-w-7xl mx-auto px-6 py-8">
        
        {/* Phase 1 Target Header */}
        <header className="mb-10 flex items-end justify-between">
          <div>
            <div className="flex items-center gap-2 mb-3">
              <span className="px-2.5 py-1 rounded-md bg-indigo-500/10 text-indigo-400 text-[10px] font-mono tracking-wider border border-indigo-500/20 font-bold uppercase">
                PHASE 1: PROOF OF CONCEPT
              </span>
              <span className="px-2.5 py-1 rounded-md bg-slate-800 text-slate-300 text-[10px] font-mono tracking-wider border border-slate-700 font-bold uppercase">
                TARGET VEHICLE
              </span>
            </div>
            <h2 className="text-4xl font-light tracking-tight text-white flex items-center gap-3 mb-2">
              <Car className="h-8 w-8 text-slate-400" />
              Kia EV9 <span className="font-semibold">GT-Line</span>
            </h2>
            <p className="text-sm text-slate-400 max-w-2xl leading-relaxed">
              Analyzing real-time market data to identify the highest lease value scenarios. Evaluating alternative configurations (Land AWD / Wind AWD) against baseline parameters to secure unbeatable North American terms.
            </p>
          </div>
          
          <div className="bg-slate-900 border border-white/5 rounded-xl p-4 flex gap-6 shrink-0">
            <div>
              <p className="text-[10px] text-slate-500 font-mono tracking-wider mb-1">DATA SOURCES</p>
              <p className="text-sm font-semibold text-slate-300 flex items-center gap-1.5">
                <Database className="h-3.5 w-3.5 text-indigo-400" /> 14 Active Nodes
              </p>
            </div>
            <div className="w-px bg-white/5" />
            <div>
              <p className="text-[10px] text-slate-500 font-mono tracking-wider mb-1">MSRP BASELINE</p>
              <p className="text-sm font-semibold text-slate-300">$73,900</p>
            </div>
          </div>
        </header>

        {/* Tab Content Rendering */}
        <AnimatePresence mode="wait">
          {activeTab === 'intel' && (
            <motion.div
              key="intel"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
            >
              <IntelDashboard onDealSelect={handleDealSelect} />
            </motion.div>
          )}

          {activeTab === 'structuring' && (
            <motion.div
               key="structuring"
               initial={{ opacity: 0, y: 10 }}
               animate={{ opacity: 1, y: 0 }}
               exit={{ opacity: 0, y: -10 }}
            >
              <TaxSimulator dealConfig={dealConfig} />
            </motion.div>
          )}
          
          {activeTab === 'outreach' && (
            <motion.div
               key="outreach"
               initial={{ opacity: 0, y: 10 }}
               animate={{ opacity: 1, y: 0 }}
               exit={{ opacity: 0, y: -10 }}
            >
              <OutreachCRM dealConfig={dealConfig} />
            </motion.div>
          )}

        </AnimatePresence>
      </main>
    </div>
  );
}
