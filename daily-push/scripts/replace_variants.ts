import fs from 'fs';
const file = 'src/App.tsx';
let txt = fs.readFileSync(file, 'utf-8');

txt = txt.replace(
    /variants=\{\{ hidden: \{ opacity: 0, y: 20 \}, show: \{ opacity: 1, y: 0, transition: \{ type: "spring", stiffness: 300, damping: 24 \} \} \}\}/g,
    'initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.05 }} variants={{ hidden: { opacity: 0, y: 40, scale: 0.96 }, show: { opacity: 1, y: 0, scale: 1, transition: { type: "spring", stiffness: 320, damping: 26 } } }}'
);

// We need to remove the stagger from <main> because whileInView doesn't work out-of-the-box perfectly with staggerChildren if the children are offscreen. So removing stagger and letting each trigger individually is better for scrolling.

// Removing the stagger from the parent <motion.div> around the layout gaps:
txt = txt.replace(
'<motion.div \\n          initial="hidden"\\n          animate="show"\\n          variants={{\\n            hidden: { opacity: 0 },\\n            show: {\\n              opacity: 1,\\n              transition: { staggerChildren: 0.08, delayChildren: 0.1 }\\n            }\\n          }}\\n          className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"\\n        >',
'<div className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8">'
);

txt = txt.replace(
'className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8"\\n        >',
'<div className="grid grid-cols-1 xl:grid-cols-12 gap-6 xl:gap-8">'
);

// We also need to change the closing tag from </motion.div> down to </div>
txt = txt.replace(
  '</motion.div>\\n        </motion.div>\\n      </main>',
  '</motion.div>\\n        </div>\\n      </main>'
);

fs.writeFileSync(file, txt, 'utf-8');
