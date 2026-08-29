const {makeHarness}=require("./harness.js"); const fs=require("fs");
(async()=>{
let pass=0,fail=0; const t=(n,c,d="")=>{c?(pass++,console.log("  PASS  "+n)):(fail++,console.log("  FAIL  "+n+" <- "+d));};

// Car is ON HOLD, 14 min remaining
const a=makeHarness({carvanaPlaid:true,carvanaHoldMinutes:14});
await a.sandbox.runCheck();
const st=JSON.parse(fs.readFileSync(a.dir+"/state.json","utf8"));
t("hold detected and hot-watch set", Object.keys(st.hotWatch||{}).length===1, JSON.stringify(st.hotWatch));
const heads=a.state.pushes.filter(p=>/ON HOLD/.test(p.title));
t("heads-up notification sent", heads.length===1, JSON.stringify(a.state.pushes.map(p=>p.title)));
t("heads-up includes expiry time", heads[0]&&/expires in ~1[34] min/.test(heads[0].message), heads[0]&&heads[0].message);
t("heads-up is high priority, not emergency", heads[0]&&heads[0].priority==="1");
t("no 'available' alert while on hold", !a.state.pushes.some(p=>/^New .+: \$/.test(p.title)));

// Second cycle: no duplicate heads-up
await a.sandbox.runCheck();
t("no duplicate heads-up on next cycle", a.state.pushes.filter(p=>/ON HOLD/.test(p.title)).length===1);

// Hold lapses -> car becomes available -> emergency alert
const b=makeHarness({carvanaPlaid:true,carvanaAvailable:true});
fs.copyFileSync(a.dir+"/seen-vins.json", b.dir+"/seen-vins.json");
fs.copyFileSync(a.dir+"/state.json", b.dir+"/state.json");
await b.sandbox.runCheck();
const urgent=b.state.pushes.filter(p=>/^New .+: \$/.test(p.title));
t("EMERGENCY alert when hold lapses and car frees up", urgent.length===1, JSON.stringify(b.state.pushes.map(p=>p.title)));
t("uses emergency priority with retry", urgent[0]&&urgent[0].priority==="2"&&urgent[0].retry==="60");
const st2=JSON.parse(fs.readFileSync(b.dir+"/state.json","utf8"));
t("hot-watch cleared after resolution", Object.keys(st2.hotWatch||{}).length===0, JSON.stringify(st2.hotWatch));

console.log(`\n  ${pass} passed, ${fail} failed`);
})();
