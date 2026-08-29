import fs from 'fs';

const filePath = 'src/App.tsx';
let content = fs.readFileSync(filePath, 'utf-8');

// Replace the main tag
content = content.replace(
  /<main className="max-w-5xl mx-auto px-4 mt-8 space-y-6 relative z-10 block">/,
  `<main className="max-w-7xl mx-auto px-4 lg:px-8 mt-8 mb-24 relative z-10 block w-full">
        <motion.div 
          initial="hidden"
          animate="show"
          variants={{
            hidden: { opacity: 0 },
            show: {
              opacity: 1,
              transition: { staggerChildren: 0.08, delayChildren: 0.1 }
            }
          }}
          className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"
        >`
);

// We need to enclose the right items in columns.
// Currently they are direct children of <main>.

// 1. Google Drive Sync
// We'll leave it as a motion.div but give it className xl:col-span-12
// Replace its motion props:
let replaceMotionProps = (str: string, spanClass?: string) => {
  return str.replace(/initial=\{\{\s*opacity:\s*0,\s*y:\s*15\s*\}\}\s*animate=\{\{\s*opacity:\s*1,\s*y:\s*0\s*\}\}\s*transition=\{\{\s*duration:\s*0\.4(?:,\s*delay:\s*[\d\.]+)?\s*\}\}/g, `variants={{ hidden: { opacity: 0, y: 20 }, show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 300, damping: 24 } } }}${spanClass ? " " + spanClass : ""}`);
};

content = replaceMotionProps(content);

// We must manually add the column wrappers.
// But we actually can just add the \`xl:col-span-...\` class into the existing motion.div class string!

// 1. Google Drive 
content = content.replace(
  /\{\/\* Google Drive Balance \/ Cloud Synchronization System \*\/}[\s\S]*?className="bg-white\/70/g,
  (match) => match.replace('className="bg-white/70', 'className="xl:col-span-12 bg-white/70')
);

// 2. Cumulative Stat Panels (it's currently: className="grid grid-cols-2 md:grid-cols-4 gap-4")
content = content.replace(
  /\{\/\* Dynamic Cumulative Stat Panels \*\/}[\s\S]*?className="grid grid-cols-2 md:grid-cols-4/g,
  (match) => match.replace('className="grid grid-cols-2 md:grid-cols-4', 'className="xl:col-span-12 grid grid-cols-2 md:grid-cols-4')
);

// 3. AI Insight Panel -> let's make it span 4
content = content.replace(
  /\{\/\* AI Insight Panel \*\/}[\s\S]*?className="bg-white\/70/g,
  (match) => match.replace('className="bg-white/70', 'className="xl:col-span-5 bg-white/70')
);

// 4. Rep Entry Form Section -> let's make it span 4 
content = content.replace(
  /\{\/\* Rep Entry Form Section \*\/}[\s\S]*?className="bg-white\/70/g,
  (match) => match.replace('className="bg-white/70', 'className="xl:col-span-7 bg-white/70')
);

// 5. Personal Records panel -> let's make it span 12. But wait, we can change its own grid from grid-cols-6 to grid-cols-3 or 6.
content = content.replace(
  /\{\/\* Personal Records panel \*\/}[\s\S]*?className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6/g,
  (match) => match.replace('className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6', 'className="xl:col-span-12 grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6')
);

// 6. Dynamic Recharts Performance Graph -> span 12
content = content.replace(
  /\{\/\* Dynamic Recharts Performance Graph \*\/}[\s\S]*?className="grid grid-cols-1 md:grid-cols-2/g,
  (match) => match.replace('className="grid grid-cols-1 md:grid-cols-2', 'className="xl:col-span-7 grid grid-cols-1 md:grid-cols-2')
);

// 7. WORKOUT DATABASE INDEX -> span 5
content = content.replace(
  /\{\/\* WORKOUT DATABASE INDEX \*\/}[\s\S]*?className="bg-white\/70/g,
  (match) => match.replace('className="bg-white/70', 'className="xl:col-span-5 bg-white/70')
);

// 8. Offline Diagnostic Feed -> span 12
content = content.replace(
  /\{\/\* Offline Diagnostic Feed \*\/}[\s\S]*?className="p-5/g,
  (match) => match.replace('className="p-5', 'className="xl:col-span-12 p-5')
);

// Add the closing tag for the new <motion.div> wrapper before </main>
content = content.replace(
  /        <\/motion\.div>\n      <\/main>/,
  `        </motion.div>\n        </motion.div>\n      </main>`
);

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Transform complete.');
