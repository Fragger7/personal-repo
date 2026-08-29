const {makeHarness}=require("./harness.js");
const fs=require("fs");
(async()=>{
let pass=0,fail=0;
const t=(n,c,d="")=>{c?(pass++,console.log("  PASS  "+n)):(fail++,console.log("  FAIL  "+n+" <- "+d));};

// Phase 1: car exists but is RESERVED -> tracked, no alert
const h=makeHarness({carvanaPlaid:true,carvanaPending:false,carvanaAvailable:false});
await h.sandbox.runCheck();                 // first run seeds
const alerts1=h.state.pushes.filter(p=>/^(New|Possible) .+: \$/.test(p.title)).length;
let seen=JSON.parse(fs.readFileSync(h.dir+"/seen-vins.json","utf8"));
t("reserved car recorded as 'possible'", seen["5YJSA1E44NF500001"]==="possible", JSON.stringify(seen).slice(0,160));
t("no alert while reserved", alerts1===0);

// Phase 2: same car becomes AVAILABLE -> must alert
const h2=makeHarness({carvanaPlaid:true,carvanaPending:false,carvanaAvailable:true});
fs.copyFileSync(h.dir+"/seen-vins.json", h2.dir+"/seen-vins.json");   // carry state forward
fs.copyFileSync(h.dir+"/state.json", h2.dir+"/state.json");
await h2.sandbox.runCheck();
const upgrade=h2.state.pushes.filter(p=>/^(New|Possible) .+: \$/.test(p.title));
t("ALERTS when reserved car becomes available", upgrade.length===1, JSON.stringify(h2.state.pushes.map(p=>p.title)));
t("upgrade alert uses emergency priority", upgrade[0] && upgrade[0].priority==="2");
seen=JSON.parse(fs.readFileSync(h2.dir+"/seen-vins.json","utf8"));
t("now recorded as 'confirmed'", seen["5YJSA1E44NF500001"]==="confirmed");

// Phase 3: still available next cycle -> no repeat alert
await h2.sandbox.runCheck();
t("no duplicate alert on following cycle", h2.state.pushes.filter(p=>/^(New|Possible) .+: \$/.test(p.title)).length===1);

// Phase 4: legacy array format migrates and can still upgrade
const h3=makeHarness({carvanaPlaid:true,carvanaAvailable:true});
fs.writeFileSync(h3.dir+"/seen-vins.json", JSON.stringify(["5YJSA1E44NF500001"]));
await h3.sandbox.runCheck();
t("legacy array format upgrades to an alert", h3.state.pushes.some(p=>/^(New|Possible) .+: \$/.test(p.title)), JSON.stringify(h3.state.pushes.map(p=>p.title)));

console.log(`\n  ${pass} passed, ${fail} failed`);
})();
