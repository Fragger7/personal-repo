import React, { useState, useEffect } from 'react';
import { motion } from 'motion/react';
import { 
  Calculator, Building2, HelpCircle, DollarSign, 
  MapPin, ClipboardCheck, Percent, Info, AlertTriangle
} from 'lucide-react';
import { TaxRuleResult } from '../types';

export default function TaxSimulator({ dealConfig }: { dealConfig?: any }) {
  // Inputs
  const [zipCode, setZipCode] = useState(dealConfig?.zipCode || '78664');
  const [msrp, setMsrp] = useState(dealConfig?.msrp || 62500);
  const [discount, setDiscount] = useState(dealConfig?.discount || 6.5);
  const [rebates, setRebates] = useState(dealConfig?.rebates || 7500);
  const [term, setTerm] = useState(dealConfig?.term || 36);
  const [moneyFactor, setMoneyFactor] = useState(dealConfig?.moneyFactor || 0.00210);
  const [residualPercent, setResidualPercent] = useState(dealConfig?.residualPercent || 64);
  const [taxCreditActive, setTaxCreditActive] = useState(true);

  useEffect(() => {
    if (dealConfig) {
      if (dealConfig.zipCode) setZipCode(dealConfig.zipCode);
      if (dealConfig.msrp) setMsrp(dealConfig.msrp);
      if (dealConfig.discount) setDiscount(dealConfig.discount);
      if (dealConfig.rebates) setRebates(dealConfig.rebates);
      if (dealConfig.term) setTerm(dealConfig.term);
      if (dealConfig.moneyFactor) setMoneyFactor(dealConfig.moneyFactor);
      if (dealConfig.residualPercent) setResidualPercent(dealConfig.residualPercent);
    }
  }, [dealConfig]);

  // Simulated results of Tax Rules fetch
  const [taxDetails, setTaxDetails] = useState<TaxRuleResult | null>(null);
  const [loadingTax, setLoadingTax] = useState(false);

  // Fetch zip guidelines from backend
  const fetchTaxRules = async (zip: string) => {
    setLoadingTax(true);
    try {
      const res = await fetch('/api/tax-simulate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ zipCode: zip })
      });
      const data = await res.json();
      if (res.ok) {
        setTaxDetails(data);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingTax(false);
    }
  };

  useEffect(() => {
    // Debounce zip input to avoid hammering api
    const timer = setTimeout(() => {
      if (zipCode.length >= 2) {
        fetchTaxRules(zipCode);
      }
    }, 400);
    return () => clearTimeout(timer);
  }, [zipCode]);

  // Derived Calculations
  const sellPrice = msrp * (1 - (discount / 100));
  const grossCap = sellPrice + 650; // Add 650 standard acquisition fee
  const netCap = grossCap - rebates;
  const residualAmt = msrp * (residualPercent / 100);

  // Avoid dividing by zero or infinite loop
  const safeTerm = term || 1;
  const monthlyDepreciation = Math.max(0, (netCap - residualAmt) / safeTerm);
  const monthlyRent = Math.max(0, (netCap + residualAmt) * moneyFactor);
  const basePmt = monthlyDepreciation + monthlyRent;

  // Compute multi-state tax rules
  let calculatedTaxRate = taxDetails?.defaultRate || 0.0775;
  let totalTaxDue = 0;
  let monthlyTax = 0;

  if (taxDetails) {
    if (taxDetails.taxType === 'TAX_ON_FULL_PRICE') {
      // upfront sales tax on selling price of car
      const rate = taxCreditActive ? 0.0125 : calculatedTaxRate;
      totalTaxDue = sellPrice * rate;
      monthlyTax = totalTaxDue / safeTerm;
    } else if (taxDetails.taxType === 'TAX_ON_TOTAL_PAYMENTS') {
      // upfront sales tax on sum of base payments
      const totalPaymentsEst = basePmt * safeTerm;
      totalTaxDue = totalPaymentsEst * calculatedTaxRate;
      monthlyTax = totalTaxDue / safeTerm;
    } else {
      // monthly tax on TOP of base payment
      monthlyTax = basePmt * calculatedTaxRate;
    }
  }

  const finalPayment = basePmt + monthlyTax;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 bg-slate-950/40 p-4 lg:p-6 rounded-2xl border border-slate-800">
      
      {/* Parameters Panel (7cols) */}
      <div className="lg:col-span-7 flex flex-col gap-4">
        <div className="flex items-center gap-2 border-b border-slate-800 pb-3">
          <Calculator className="h-5 w-5 text-emerald-400" />
          <h3 className="font-semibold text-white tracking-tight">Parametric Deal Contract</h3>
        </div>

        {dealConfig?.dealerName && (
          <div className="bg-indigo-500/10 border border-indigo-500/20 rounded-xl p-3 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-indigo-500/20 rounded-lg">
                <Building2 className="h-4 w-4 text-indigo-400" />
              </div>
              <div>
                <p className="text-xs text-indigo-300 font-medium">Target Dealership</p>
                <p className="text-sm text-white font-semibold">{dealConfig.dealerName}</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-[10px] text-slate-500 font-mono tracking-wider">VIN IDENTIFIER</p>
              <p className="text-xs font-mono text-slate-300">{dealConfig.vin || 'N/A'}</p>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          
          {/* Geographics & Zip */}
          <div className="col-span-1 sm:col-span-2 bg-slate-900/40 p-3.5 rounded-xl border border-slate-800/80">
            <label className="block text-xs text-slate-400 font-semibold mb-1.5 flex items-center gap-1.5 uppercase tracking-wider">
              <MapPin className="h-3 w-3 text-sky-400" />
              Contract Zip & Tax Rules
            </label>
            <div className="flex items-center gap-3">
              <input
                type="text"
                value={zipCode}
                onChange={(e) => setZipCode(e.target.value.substring(0, 5))}
                className="w-1/3 p-2 text-sm font-mono text-white bg-slate-950 rounded border border-slate-800 focus:border-sky-500 outline-none"
                placeholder="78664"
              />
              <div className="flex-1 min-w-0">
                {loadingTax ? (
                  <span className="text-xs font-mono text-slate-500 animate-pulse">Running tax schema query...</span>
                ) : taxDetails ? (
                  <div className="flex flex-col">
                    <span className="text-xs font-bold font-mono text-sky-400 flex items-center gap-1 leading-none mb-1">
                      {taxDetails.taxType} 
                      <span className="text-[10px] text-slate-500 font-normal">({(taxDetails.defaultRate * 100).toFixed(2)}%)</span>
                    </span>
                    <span className="text-[11px] text-slate-400 truncate leading-none">{taxDetails.description}</span>
                  </div>
                ) : (
                  <span className="text-xs text-slate-500">Enter ZIP to resolve tax classification</span>
                )}
              </div>
            </div>

            {taxDetails?.showsTaxCredits && (
              <div className="mt-3 pt-2.5 border-t border-slate-800/60 flex items-center justify-between">
                <span className="text-xs text-slate-300 font-medium flex items-center gap-1.5">
                  <Info className="h-3.5 w-3.5 text-amber-500 shrink-0" />
                  Apply Texas Lender Tax Credit (1.25% upfront rate)
                </span>
                <input
                  type="checkbox"
                  checked={taxCreditActive}
                  onChange={(e) => setTaxCreditActive(e.target.checked)}
                  className="h-4 w-4 accent-indigo-500 cursor-pointer"
                />
              </div>
            )}
          </div>

          {/* MSRP & Discount */}
          <div>
            <label className="block text-xs text-slate-400 font-semibold mb-1 uppercase tracking-wider">
              Vehicle MSRP ($)
            </label>
            <input
              type="number"
              value={msrp}
              onChange={(e) => setMsrp(Math.max(0, parseInt(e.target.value) || 0))}
              className="w-full p-2 text-sm font-mono text-white bg-slate-900 border border-slate-800 rounded focus:border-indigo-500 outline-none"
            />
          </div>

          <div>
            <label className="block text-xs text-slate-400 font-semibold mb-1 uppercase tracking-wider">
              Pre-Incentive Discount ({discount}%)
            </label>
            <input
              type="range"
              min="0"
              max="20"
              step="0.5"
              value={discount}
              onChange={(e) => setDiscount(parseFloat(e.target.value))}
              className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-indigo-500 my-2"
            />
          </div>

          {/* Incentives */}
          <div>
            <label className="block text-xs text-slate-400 font-semibold mb-1 uppercase tracking-wider">
              Total Rebates / EV Cash ($)
            </label>
            <input
              type="number"
              value={rebates}
              onChange={(e) => setRebates(Math.max(0, parseInt(e.target.value) || 0))}
              className="w-full p-2 text-sm font-mono text-white bg-slate-900 border border-slate-800 rounded focus:border-indigo-500 outline-none"
            />
          </div>

          {/* Term Selection */}
          <div>
            <label className="block text-xs text-slate-400 font-semibold mb-1 uppercase tracking-wider">
              Lease Term Target (Months)
            </label>
            <select
              value={term}
              onChange={(e) => setTerm(parseInt(e.target.value))}
              className="w-full p-2 text-sm font-mono text-white bg-slate-900 border border-slate-800 rounded focus:border-indigo-500 outline-none"
            >
              <option value="24">24 Months</option>
              <option value="36">36 Months</option>
              <option value="39">39 Months</option>
              <option value="48">48 Months</option>
            </select>
          </div>

          {/* Money Factor */}
          <div>
            <label className="block text-xs text-slate-400 font-semibold mb-1 uppercase tracking-wider">
              Money Factor (MF)
            </label>
            <input
              type="number"
              step="0.0001"
              value={moneyFactor}
              onChange={(e) => setMoneyFactor(parseFloat(e.target.value) || 0)}
              className="w-full p-2 text-sm font-mono text-white bg-slate-900 border border-slate-800 rounded focus:border-indigo-500 outline-none"
            />
            <span className="text-[10px] text-slate-500 mt-0.5 block font-mono">
              Equivalent to {(moneyFactor * 2400).toFixed(2)}% APR
            </span>
          </div>

          {/* Residual Percentage */}
          <div>
            <label className="block text-xs text-slate-400 font-semibold mb-1 uppercase tracking-wider">
              Residual Locks ({residualPercent}%)
            </label>
            <div className="flex items-center gap-2">
              <input
                type="range"
                min="45"
                max="75"
                value={residualPercent}
                onChange={(e) => setResidualPercent(parseInt(e.target.value))}
                className="flex-1 h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-indigo-500 my-2"
              />
              <span className="text-xs text-slate-400 font-mono w-8 text-right shrink-0">{residualPercent}%</span>
            </div>
          </div>

        </div>

        <div className="bg-slate-900/10 p-3 rounded-lg border border-slate-800/60 mt-2">
          <p className="text-[11px] text-slate-500 leading-normal flex gap-1.5 items-start">
            <Info className="h-3.5 w-3.5 text-indigo-400 shrink-0 mt-0.5" />
            <span>
              This calculator mimics the full contract arithmetic of the <strong className="text-slate-300">Universal AI Lease Broker Engine</strong>. It processes deprecation values, standard acquisition fees ($650), subvented lender factor ratios, and regional multi-tiered, upfront, and payment tax rules.
            </span>
          </p>
        </div>

      </div>

      {/* outputs Panel (5cols) */}
      <div className="lg:col-span-5 bg-slate-900/40 border border-slate-800 p-5 rounded-xl flex flex-col justify-between">
        
        <div className="space-y-4">
          <div className="flex items-center gap-2 border-b border-slate-800/80 pb-3">
            <ClipboardCheck className="h-5 w-5 text-sky-400" />
            <h3 className="font-semibold text-white tracking-tight">Real-Time Pay Desk</h3>
          </div>

          <div className="text-center p-4 bg-slate-950/80 rounded-xl border border-slate-800/80 shadow-md">
            <span className="text-[10px] text-slate-500 font-bold uppercase tracking-widest block mb-1">
              ESTIMATED FINAL PAYMENT
            </span>
            <div className="text-3xl font-extrabold text-white font-mono flex items-center justify-center tracking-tight">
              <span className="text-lg text-emerald-400 mr-0.5 font-normal">$</span>
              {finalPayment.toFixed(2)}
              <span className="text-xs text-slate-400 font-light ml-1 font-sans">/mo</span>
            </div>
            <span className="text-[11px] text-slate-500 block mt-2 font-mono">
              Includes pre-incentive discount & subvented rates
            </span>
          </div>

          <div className="space-y-2 pt-2 text-xs">
            
            <div className="flex items-center justify-between py-1.5 border-b border-slate-900 text-slate-400">
              <span>Pre-Incentive Selling Price:</span>
              <span className="font-mono text-slate-200 font-semibold">${sellPrice.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
            </div>

            <div className="flex items-center justify-between py-1.5 border-b border-slate-900 text-slate-400">
              <span className="flex items-center gap-1">
                Net Adj. Cap Cost 
                <span className="text-[9px] text-slate-500" title="Includes $650 standard bank acq fee and subtracts Incentives">(*Acq. incl)</span>
              </span>
              <span className="font-mono text-slate-200 font-semibold">${netCap.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
            </div>

            <div className="flex items-center justify-between py-1.5 border-b border-slate-900 text-slate-400">
              <span>Residual Value Lock:</span>
              <span className="font-mono text-slate-200 font-semibold">${residualAmt.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
            </div>

            <div className="flex items-center justify-between py-1.5 border-b border-slate-900 text-slate-400">
              <span>Monthly Base Depreciation:</span>
              <span className="font-mono text-slate-200 font-semibold">${monthlyDepreciation.toFixed(2)}/mo</span>
            </div>

            <div className="flex items-center justify-between py-1.5 border-b border-slate-900 text-slate-400">
              <span className="flex items-center gap-1">
                Monthly rent (Interest Fee):
                <span className="text-[9px] text-slate-500" title="(Net Cap + Residual) * MF">Math</span>
              </span>
              <span className="font-mono text-slate-200 font-semibold">${monthlyRent.toFixed(2)}/mo</span>
            </div>

            <div className="flex items-center justify-between py-1.5 border-b border-slate-900 text-slate-400">
              <span className="flex items-center gap-1">
                Upfront Sales Tax Assessed:
                {taxDetails?.taxType === 'TAX_ON_PAYMENT' && <span className="text-[9px] text-slate-500">(Monthly assessed)</span>}
              </span>
              <span className="font-mono text-sky-400 font-semibold">
                {taxDetails?.taxType === 'TAX_ON_PAYMENT' ? '$0.00' : `$${totalTaxDue.toFixed(2)}`}
              </span>
            </div>

            <div className="flex items-center justify-between py-1.5 text-slate-400 border-b border-slate-900">
              <span>Monthly Combined Tax Portion:</span>
              <span className="font-mono text-sky-400 font-semibold">${monthlyTax.toFixed(2)}/mo</span>
            </div>

          </div>

          {/* Mathematical Breakdown */}
          <div className="bg-slate-950 p-3 rounded-lg border border-slate-800 text-[10px] text-slate-500 font-mono space-y-2 mt-4">
            <h4 className="text-slate-400 font-sans font-semibold uppercase tracking-wider mb-2 text-xs">Mathematical Breakdown</h4>
            <div className="grid grid-cols-[1fr_auto] gap-x-2 gap-y-1">
              <span>Net Cap Cost = (MSRP * (1 - Discount)) + $650 Acq - Rebates</span>
              <span className="text-slate-300">${netCap.toFixed(2)}</span>
              
              <span>Depreciation = (Net Cap - Residual) / Term</span>
              <span className="text-slate-300">${monthlyDepreciation.toFixed(2)}/mo</span>
              
              <span>Rent Charge = (Net Cap + Residual) * MF</span>
              <span className="text-slate-300">${monthlyRent.toFixed(2)}/mo</span>

              <span>Base Payment = Depreciation + Rent Charge</span>
              <span className="text-slate-300">${basePmt.toFixed(2)}/mo</span>
            </div>
          </div>
        </div>

        {/* Warning rules helper */}
        <div className="bg-sky-950/20 border border-sky-900/30 p-3 rounded-lg text-xs text-sky-300 mt-4">
          <div className="flex items-start gap-1.5">
            <Info className="h-4 w-4 shrink-0 text-sky-400 mt-0.5" />
            <div className="leading-normal">
              <strong className="text-white block mb-0.5">Statutory Rules</strong>
              Texas taxes the whole selling price upfront. NY taxes total sum of payments upfront. Standard states load tax onto the base payment month by month.
            </div>
          </div>
        </div>

      </div>

    </div>
  );
}
