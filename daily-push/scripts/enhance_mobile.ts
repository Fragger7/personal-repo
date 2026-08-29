import fs from 'fs';

const filePath = 'src/App.tsx';
let content = fs.readFileSync(filePath, 'utf-8');

// remove the stagger from the parent
content = content.replace(
  /initial="hidden"[\\s\\S]*?animate="show"[\\s\\S]*?variants=\{\{[\\s\\S]*?hidden: \{ opacity: 0 \},[\\s\\S]*?show: \{[\\s\\S]*?opacity: 1,[\\s\\S]*?transition: \{ staggerChildren: 0.08, delayChildren: 0.1 \}[\\s\\S]*?\}[\\s\\S]*?\}\}/,
  'className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"'
);

content = content.replace(
  /className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"[\\s\\S]*?className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"/,
  'className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"'
);

// update all child variants to use whileInView
content = content.replace(
  /variants=\{\{ hidden: \{ opacity: 0, y: 20 \}, show: \{ opacity: 1, y: 0, transition: \{ type: "spring", stiffness: 300, damping: 24 \} \} \}\}/g,
  'initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.1 }} variants={{ hidden: { opacity: 0, y: 30, scale: 0.98 }, show: { opacity: 1, y: 0, scale: 1, transition: { type: "spring", stiffness: 350, damping: 28 } } }}'
);

// enhance background with animated blobs
content = content.replace(
  /<div className="fixed top-\\[-10%\\] left-\\[-10%\\] w-\\[120%\\] h-\\[600px\\] bg-\\[radial-gradient\\(ellipse_at_top,_var\\(--tw-gradient-stops\\)\\)\\] from-emerald-500\\/10 via-teal-900\\/5 to-transparent pointer-events-none z-0" \\/>[\\s\\S]*?<div className="fixed bottom-\\[-10%\\] right-\\[-10%\\] w-\\[80%\\] h-\\[500px\\] bg-\\[radial-gradient\\(ellipse_at_bottom_right,_var\\(--tw-gradient-stops\\)\\)\\] from-indigo-500\\/5 via-fuchsia-900\\/5 to-transparent pointer-events-none z-0" \\/>/,
  '<div className="fixed top-[-10%] left-[-20%] w-[100vw] h-[100vw] rounded-full bg-emerald-500/20 dark:bg-emerald-600/10 blur-[100px] pointer-events-none z-0 animate-blob" />\\n      <div className="fixed top-[20%] right-[-10%] w-[80vw] h-[80vw] rounded-full bg-cyan-500/20 dark:bg-teal-600/10 blur-[90px] pointer-events-none z-0 animate-blob animation-delay-2000" />\\n      <div className="fixed bottom-[-10%] left-[20%] w-[90vw] h-[90vw] rounded-full bg-indigo-500/20 dark:bg-indigo-600/10 blur-[110px] pointer-events-none z-0 animate-blob animation-delay-4000" />'
);

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Animation and background enhancements complete.');
