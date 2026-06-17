import fs from 'fs';

const filePath = 'src/App.tsx';
let content = fs.readFileSync(filePath, 'utf-8');

content = content.replace(/bg-amber-50 border border-amber-200 dark:border-transparent dark:bg-\[\#F7B600\]\/15 text-amber-700 dark:text-amber-300/g, 'bg-[#F7B600]/10 border border-[#F7B600]/20 dark:border-transparent dark:bg-[#F7B600]/15 text-[#CCA200] dark:text-[#F7B600]');

content = content.replace(/text-amber-700 dark:text-\[\#F7B600\]/g, 'text-[#CCA200] dark:text-[#F7B600]');


fs.writeFileSync(filePath, content, 'utf-8');
console.log('Amber cleanup completed');
