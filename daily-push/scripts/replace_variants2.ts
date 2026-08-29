import fs from 'fs';
const file = 'src/App.tsx';
let txt = fs.readFileSync(file, 'utf-8');

txt = txt.replace(/<motion\.div\s+initial="hidden"\s+animate="show"\s+variants=\{\{\s+hidden: \{ opacity: 0 \},\s+show: \{\s+opacity: 1,\s+transition: \{ staggerChildren: 0.08, delayChildren: 0.1 \}\s+\}\s+\}\}\s+className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"\s+>/g, '<div className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8">');

txt = txt.replace(/<\/motion\.div>\s*<\/motion\.div>\s*<\/main>/, '</motion.div></div></main>');

fs.writeFileSync(file, txt, 'utf-8');
