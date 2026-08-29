import fs from 'fs';

const filePath = 'src/App.tsx';
let content = fs.readFileSync(filePath, 'utf-8');

// Replace emerald -> blue
content = content.replace(/emerald-500/g, 'blue-700');
content = content.replace(/emerald-600/g, 'blue-800');
content = content.replace(/emerald-400/g, 'blue-600');
content = content.replace(/emerald-300/g, 'blue-500');
content = content.replace(/emerald-700/g, 'blue-900');
content = content.replace(/emerald-950/g, 'blue-950');

// Replace cyan -> amber (Visa Gold)
content = content.replace(/cyan-500/g, 'amber-500');
content = content.replace(/cyan-400/g, 'amber-400');
content = content.replace(/cyan-700/g, 'amber-700');
content = content.replace(/cyan-300/g, 'amber-300');
content = content.replace(/cyan-50/g, 'amber-50');
content = content.replace(/cyan-200/g, 'amber-200');

// Replace indigo -> blue (Visa Dark Blue/Navy)
content = content.replace(/indigo-500/g, 'blue-800');
content = content.replace(/indigo-400/g, 'blue-700');
content = content.replace(/indigo-700/g, 'blue-950');
content = content.replace(/indigo-600/g, 'blue-900');
content = content.replace(/indigo-950/g, 'slate-950');

// Replace teal -> blue
content = content.replace(/teal-600/g, 'blue-800');
content = content.replace(/teal-400/g, 'blue-500');

// Replace fuchsia -> blue
content = content.replace(/fuchsia-900/g, 'blue-900');

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Colors replaced for Visa branding');
