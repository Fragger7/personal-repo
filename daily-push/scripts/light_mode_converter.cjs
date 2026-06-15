const fs = require('fs');

let content = fs.readFileSync('src/App.tsx', 'utf-8');

// Replace standard dark classes with dynamic dark: ones
const replacements = [
  { p: /bg-\[#02040a\]/g, r: 'bg-slate-50 dark:bg-[#02040a]' },
  { p: /text-slate-100/g, r: 'text-slate-900 dark:text-slate-100' },
  { p: /selection:text-white/g, r: 'selection:text-slate-950 dark:selection:text-white' },
  { p: /text-white/g, r: 'text-slate-900 dark:text-white' },
  { p: /bg-slate-950\/60/g, r: 'bg-white/60 dark:bg-slate-950/60' },
  { p: /bg-slate-950\/50/g, r: 'bg-white/50 dark:bg-slate-950/50' },
  { p: /bg-slate-950\/40/g, r: 'bg-white/40 dark:bg-slate-950/40' },
  { p: /bg-slate-950\b/g, r: 'bg-white dark:bg-slate-950' },
  { p: /bg-slate-900\/80/g, r: 'bg-slate-100/80 dark:bg-slate-900/80' },
  { p: /bg-slate-900\b/g, r: 'bg-slate-100 dark:bg-slate-900' },
  { p: /bg-slate-800\b/g, r: 'bg-slate-200 dark:bg-slate-800' },
  { p: /border-white\/5/g, r: 'border-slate-200 dark:border-white/5' },
  { p: /border-white\/10/g, r: 'border-slate-300 dark:border-white/10' },
  { p: /border-white\/20/g, r: 'border-slate-300 dark:border-white/20' },
  { p: /text-slate-350/g, r: 'text-slate-700 dark:text-slate-350' },
  { p: /text-slate-400/g, r: 'text-slate-600 dark:text-slate-400' },
  { p: /text-slate-500/g, r: 'text-slate-500 dark:text-slate-500' }, // maybe leave? text-slate-500 is good for both
  { p: /bg-black\/15/g, r: 'bg-slate-200/50 dark:bg-black/15' },
  { p: /bg-black\/20/g, r: 'bg-slate-200/50 dark:bg-black/20' },
  { p: /bg-slate-950\/90/g, r: 'bg-white/90 dark:bg-slate-950/90' },
  { p: /bg-slate-600\b/g, r: 'bg-slate-300 dark:bg-slate-600' },
  { p: /bg-slate-700\b/g, r: 'bg-slate-300 dark:bg-slate-700' },
  // specific accents
  { p: /text-cyan-400/g, r: 'text-cyan-600 dark:text-cyan-400' },
  { p: /text-emerald-400/g, r: 'text-emerald-600 dark:text-emerald-400' },
  { p: /text-indigo-400/g, r: 'text-indigo-600 dark:text-indigo-400' },
  { p: /text-amber-400/g, r: 'text-amber-600 dark:text-amber-400' },
  { p: /text-cyan-300/g, r: 'text-cyan-700 dark:text-cyan-300' },
  { p: /bg-cyan-500\/15/g, r: 'bg-cyan-500/10 dark:bg-cyan-500/15' },
  { p: /border-cyan-500\/25/g, r: 'border-cyan-500/30 dark:border-cyan-500/25' },
  { p: /bg-emerald-950\/50/g, r: 'bg-emerald-50 dark:bg-emerald-950/50' },
  { p: /border-emerald-500\/20/g, r: 'border-emerald-500/30 dark:border-emerald-500/20' },
  { p: /bg-red-950\/50/g, r: 'bg-red-50 dark:bg-red-950/50' },
  { p: /border-red-500\/20/g, r: 'border-red-500/30 dark:border-red-500/20' },
  { p: /text-emerald-300/g, r: 'text-emerald-700 dark:text-emerald-300' },
  { p: /text-red-300/g, r: 'text-red-700 dark:text-red-300' },
  { p: /text-slate-950/g, r: 'text-white dark:text-slate-950' },
  { p: /bg-slate-50\b/g, r: 'bg-slate-900 dark:bg-slate-50' },
];

replacements.forEach(rep => {
  content = content.replace(rep.p, rep.r);
});

fs.writeFileSync('src/App.tsx', content);
console.log('App.tsx classes updated for light mode!');

