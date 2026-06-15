const fs = require('fs');

let content = fs.readFileSync('src/App.tsx.backup', 'utf-8');

// Function to replace classname attributes specifically
content = content.replace(/className=(["`'])(.*?)\1/gs, (match, quote, classNames) => {
    // Process the classes
    let classes = classNames.split(/\s+/);
    
    classes = classes.map(cls => {
        // Only process classes that are not already dark:*
        if (cls.startsWith('dark:')) return cls;
        if (cls.startsWith('hover:dark:')) return cls;
        
        switch (cls) {
            case 'bg-[#02040a]': return 'bg-slate-50 dark:bg-[#02040a]';
            case 'bg-[#02040a]/80': return 'bg-slate-50/80 dark:bg-[#02040a]/80';
            case 'bg-slate-950/40': return 'bg-white/40 dark:bg-slate-950/40';
            case 'bg-slate-950/50': return 'bg-white/60 dark:bg-slate-950/50';
            case 'bg-slate-950/60': return 'bg-white/80 dark:bg-slate-950/60';
            case 'bg-slate-950/90': return 'bg-white/95 dark:bg-slate-950/90';
            case 'bg-slate-950': return 'bg-white dark:bg-slate-950';
            case 'bg-slate-900/80': return 'bg-slate-100/80 dark:bg-slate-900/80';
            case 'bg-slate-900': return 'bg-slate-100 dark:bg-slate-900';
            case 'bg-slate-800': return 'bg-slate-200 dark:bg-slate-800';
            case 'bg-slate-700': return 'bg-slate-300 dark:bg-slate-700';
            case 'bg-black/15': return 'bg-slate-200/50 dark:bg-black/15';
            case 'bg-black/20': return 'bg-slate-200/50 dark:bg-black/20';
            case 'bg-emerald-950/50': return 'bg-emerald-50 dark:bg-emerald-950/50';
            case 'bg-red-950/50': return 'bg-red-50 dark:bg-red-950/50';
            case 'bg-cyan-500/15': return 'bg-cyan-50 border border-cyan-200 dark:border-transparent dark:bg-cyan-500/15';

            case 'border-white/5': return 'border-slate-300 dark:border-white/5';
            case 'border-white/10': return 'border-slate-300 dark:border-white/10';
            case 'border-white/20': return 'border-slate-300 dark:border-white/20';
            case 'border-emerald-500/20': return 'border-emerald-500/40 dark:border-emerald-500/20';
            case 'border-cyan-500/20': return 'border-cyan-500/40 dark:border-cyan-500/20';
            case 'border-cyan-500/25': return 'border-cyan-500/40 dark:border-cyan-500/25';
            case 'border-red-500/20': return 'border-red-500/40 dark:border-red-500/20';

            case 'text-slate-100': return 'text-slate-900 dark:text-slate-100';
            case 'text-white': {
                // If it's a solid bg button (e.g., emerald) we shouldn't change. 
                // Let's assume most text-white are text elements in cards. For solid buttons, if there is a 'bg-slate-900' or similar, we change it.
                // It's safer to just change it to text-slate-900 dark:text-white. 
                return 'text-slate-900 dark:text-white';
            }
            case 'text-slate-400': return 'text-slate-600 dark:text-slate-400';
            case 'text-slate-350': return 'text-slate-700 dark:text-slate-350';
            
            case 'text-emerald-400': return 'text-emerald-700 dark:text-emerald-400';
            case 'text-cyan-400': return 'text-cyan-700 dark:text-cyan-400';
            case 'text-indigo-400': return 'text-indigo-700 dark:text-indigo-400';
            case 'text-amber-400': return 'text-amber-700 dark:text-amber-400';
            case 'text-cyan-300': return 'text-cyan-700 dark:text-cyan-300';
            case 'text-emerald-300': return 'text-emerald-700 dark:text-emerald-300';
            case 'text-red-300': return 'text-red-700 dark:text-red-300';
            case 'text-red-400': return 'text-red-700 dark:text-red-400';
            
            case 'hover:text-slate-200': return 'hover:text-slate-800 dark:hover:text-slate-200';
            case 'hover:bg-slate-200': return 'hover:bg-slate-300 dark:hover:bg-slate-200';
            
            default: return cls;
        }
    });
    
    // Check if there are exceptions where text-white should remain text-white
    // e.g. "bg-emerald-500" + "text-slate-900 dark:text-white" -> "text-white"
    if (classes.includes('text-slate-900') && classes.includes('dark:text-white') && classes.includes('bg-emerald-500')) {
        let idx1 = classes.indexOf('text-slate-900');
        classes[idx1] = 'text-white';
        let idx2 = classes.indexOf('dark:text-white');
        classes.splice(idx2, 1);
    }

    return `className=${quote}${classes.join(' ')}${quote}`;
});

fs.writeFileSync('src/App.tsx', content);
console.log('App.tsx processed with lexer-like replacement!');
