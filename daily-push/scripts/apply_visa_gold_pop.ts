import fs from 'fs';

const filePath = 'src/App.tsx';
let content = fs.readFileSync(filePath, 'utf-8');

// Update chart line colors
content = content.replace(/stroke="#10b981"/g, 'stroke="#F7B600"'); // Pushups to Visa Gold
content = content.replace(/stroke="#6366f1"/g, 'stroke="#1434CB"'); // Crunches to Brand Blue

// Update the dark mode blobs to have gold
content = content.replace(/bg-\[\#F7B600\]\/20 dark:bg-\[\#1A1F71\]\/10 blur-\[90px\]/g, 'bg-[#F7B600]/20 dark:bg-[#F7B600]/15 blur-[90px]');

// Update the main CTA buttons
content = content.replace(
  /className="flex-1 bg-\[\#F0F4FF\] text-slate-950 font-extrabold py-4 rounded-xl text-xs uppercase tracking-widest font-mono hover:bg-slate-300 dark:hover:bg-slate-200 active:bg-slate-350 transition-all cursor-pointer shadow-md"/g,
  'className="flex-1 bg-[#F7B600] text-[#0A1242] font-black py-4 rounded-xl text-xs uppercase tracking-widest font-mono hover:bg-[#CCA200] active:scale-95 transition-all cursor-pointer shadow-lg shadow-[#F7B600]/30 border border-[#F7B600]/50"'
);

content = content.replace(
  /className="w-full bg-\[\#F0F4FF\] text-slate-950 font-extrabold py-4 rounded-xl text-xs uppercase tracking-widest font-mono hover:bg-slate-300 dark:hover:bg-slate-200 active:bg-slate-300 transition-all cursor-pointer shadow-md"/g,
  'className="w-full bg-[#1434CB] text-white font-black py-4 rounded-xl text-xs uppercase tracking-widest font-mono hover:bg-[#1A1F71] active:scale-95 transition-all cursor-pointer shadow-lg shadow-[#1434CB]/30 border border-[#1434CB]/50"'
);

// Add Visa Gold to gradient of the main profile logo
content = content.replace(
  /bg-gradient-to-tr from-\[\#1434CB\] to-blue-500/g,
  'bg-gradient-to-br from-[#1434CB] via-[#1A1F71] to-[#F7B600]'
);

// Add gold to specific badges or icons
content = content.replace(
  /bg-\[\#1434CB\]\/10 border border-\[\#1434CB\]\/40 dark:border-\[\#1434CB\]\/20 text-blue-900 dark:text-\[\#1434CB\]/g,
  'bg-[#F7B600]/10 border border-[#F7B600]/40 dark:border-[#F7B600]/20 text-[#CCA200] dark:text-[#F7B600]'
);


fs.writeFileSync(filePath, content, 'utf-8');
console.log('Visa gold and contrasts applied');
