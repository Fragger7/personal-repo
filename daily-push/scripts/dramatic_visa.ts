import fs from 'fs';

const file = 'src/App.tsx';
let txt = fs.readFileSync(file, 'utf-8');

// Replace the deep slate colors with Visa Navy shades
txt = txt.replace(/#03060f/g, '#0A1242'); // Main background
txt = txt.replace(/#060b14/g, '#0E1750'); // Secondary background
txt = txt.replace(/#080d19/g, '#111B61'); // Card background

// Make the text accents literally Visa Gold (#F7B600) instead of amber-X
txt = txt.replace(/amber-400/g, '[#F7B600]');
txt = txt.replace(/amber-500/g, '[#F7B600]');
txt = txt.replace(/amber-600/g, '[#CCA200]');

// Make the primary blue literally Visa Blue (#1434CB) instead of blue-X
txt = txt.replace(/blue-600/g, '[#1434CB]');
txt = txt.replace(/blue-700/g, '[#1434CB]');
txt = txt.replace(/blue-800/g, '[#1A1F71]');
txt = txt.replace(/blue-950/g, '[#0A0D36]');

txt = txt.replace(/bg-slate-50/g, 'bg-[#F0F4FF]');

fs.writeFileSync(file, txt, 'utf-8');
console.log('Dramatic Visa colors applied!');
