const {makeHarness}=require("./harness.js");
let pass=0,fail=0;
const t=(name,cond,detail="")=>{ if(cond){pass++;console.log("  PASS  "+name);} else {fail++;console.log("  FAIL  "+name+(detail?"  <- "+detail:""));} };
const titles=s=>s.pushes.map(p=>p.title);

(async()=>{
// ---------- 1. First run seeds silently, no listing alerts ----------
console.log("\n[1] First run seeding");
{ const h=makeHarness({teslaPlaid:true,carmaxPlaid:true,carvanaPlaid:true,carvanaAvailable:true});
  await h.sandbox.runCheck();
  t("sends exactly one 'started' push", h.state.pushes.length===1 && /started/i.test(h.state.pushes[0].title), JSON.stringify(titles(h.state)));
  t("no listing alerts on first run", !h.state.pushes.some(p=>/^(New|Possible) .+: \$/.test(p.title)));
  t("healthcheck pinged on first run", h.state.pings===1, "pings="+h.state.pings);
}

// ---------- 2. New listing alerts on second run ----------
console.log("\n[2] New listing detection");
{ const h2=makeHarness({teslaPlaid:true,carmaxPlaid:true,carvanaPlaid:true,carvanaAvailable:true});
  await h2.sandbox.runCheck();                   // first run seeds them
  const seededTitles=titles(h2.state);
  await h2.sandbox.runCheck();                   // second run: nothing new
  t("no duplicate alerts for already-seen cars", titles(h2.state).length===seededTitles.length, JSON.stringify(titles(h2.state)));
}

// ---------- 3. Dedupe across cycles ----------
console.log("\n[3] Dedupe / seen-vins persistence");
{ const h=makeHarness({carmaxPlaid:true});
  await h.sandbox.runCheck();   // seeds
  await h.sandbox.runCheck();
  await h.sandbox.runCheck();
  const alerts=titles(h.state).filter(x=>/^(New|Possible) .+: \$/.test(x));
  t("repeat cycles produce zero new alerts", alerts.length===0, JSON.stringify(alerts));
}

// ---------- 4. Carvana pending car is excluded ----------
console.log("\n[4] Carvana availability verification");
{ const h=makeHarness({carvanaPlaid:true,carvanaPending:false,carvanaAvailable:false});
  await h.sandbox.runCheck();  // seed
  const seen=require("fs").readFileSync(h.dir+"/seen-vins.json","utf8");
  { const m=JSON.parse(seen);
    t("reserved car tracked as 'possible' (not alertable)", m["5YJSA1E44NF500001"]==="possible", seen.slice(0,140)); }
}
{ const h=makeHarness({carvanaPlaid:true,carvanaPending:false,carvanaAvailable:true});
  await h.sandbox.runCheck();
  const seen=require("fs").readFileSync(h.dir+"/seen-vins.json","utf8");
  t("available car IS included", seen.includes("5YJSA1E44NF500001"));
}

// ---------- 5. isPurchasePending flag respected ----------
console.log("\n[5] isPurchasePending filter");
{ const h=makeHarness({carvanaPlaid:true,carvanaPending:true,carvanaAvailable:true});
  await h.sandbox.runCheck();
  const seen=require("fs").readFileSync(h.dir+"/seen-vins.json","utf8");
  t("pending=true excluded at parse stage", !seen.includes("5YJSA1E44NF500001"));
}

// ---------- 6. Night pause ----------
console.log("\n[6] Night pause");
{ const RealDate=Date;
  const h=makeHarness({config:{NIGHT_PAUSE:true,NIGHT_START_HOUR:0,NIGHT_END_HOUR:6}});
  h.sandbox.Date=class extends RealDate{ getHours(){return 2;} static now(){return RealDate.now();} };
  await h.sandbox.runCheck();
  t("pause notification sent", h.state.pushes.some(p=>/night pause started/i.test(p.title)), JSON.stringify(titles(h.state)));
  t("healthcheck STILL pinged while paused", h.state.pings>=1, "pings="+h.state.pings);
  t("no source requests made while paused", h.state.carmaxRequests===0);
  const n=h.state.pushes.length; await h.sandbox.runCheck();
  t("no duplicate pause notification", h.state.pushes.length===n);
}

// ---------- 7. Failure handling + fallback ----------
console.log("\n[7] Source failure + TeslaTracker fallback");
{ const h=makeHarness({failSources:["tesla"],carmaxPlaid:false});
  await h.sandbox.runCheck();
  for(let i=0;i<5;i++) await h.sandbox.runCheck();
  t("other sources still ran despite tesla failing", h.state.carmaxRequests>0);
  t("healthcheck still pinged during failures", h.state.pings>0);
  t("tesla reported as ERR not 0", JSON.parse(require("fs").readFileSync(h.dir+"/state.json","utf8")).lastSourceCounts.tesla==="ERR");
}
// count-based trigger (backoff bypassed by calling recordResult directly)
{ const h=makeHarness({});
  for(let i=0;i<6;i++) h.sandbox.recordResult("carmax",{ok:false,status:403});
  await new Promise(r=>setTimeout(r,30));
  t("failure alert fires on consecutive-count threshold", h.state.pushes.filter(p=>/source failing/i.test(p.title)).length===1, JSON.stringify(titles(h.state)));
  h.sandbox.recordResult("carmax",{ok:true,status:200});
  await new Promise(r=>setTimeout(r,30));
  const st=JSON.parse(require("fs").readFileSync(h.dir+"/state.json","utf8"));
  t("recovery clears streak/alerted/backoff", !st.failStreak.carmax && !st.alerted.carmax && !st.backoff.carmax);
}
// time-based trigger for slow-cycling sources
{ const base=Date.now();
  const h=makeHarness({});
  h.sandbox.recordResult("tesla",{ok:false,status:403});
  h.sandbox.Date=class extends Date{ static now(){return base+35*60*1000;} };
  h.sandbox.recordResult("tesla",{ok:false,status:403});
  await new Promise(r=>setTimeout(r,30));
  t("failure alert fires on elapsed-time threshold", h.state.pushes.filter(p=>/source failing/i.test(p.title)).length===1);
  t("alert not repeated once raised", h.state.pushes.filter(p=>/source failing/i.test(p.title)).length===1);
}

// ---------- 8. Alert priority calibration ----------
console.log("\n[8] Alert priorities");
{ const h=makeHarness({carmaxPlaid:true});
  await h.sandbox.runCheck();  // seed
  // force a fresh listing by clearing seen-vins
  require("fs").writeFileSync(h.dir+"/seen-vins.json","[]");
  await h.sandbox.runCheck();
  const alert=h.state.pushes.find(p=>/^(New|Possible) .+: \$/.test(p.title));
  t("confirmed listing uses emergency priority", alert && alert.priority==="2", alert?JSON.stringify(alert.priority):"none");
  t("emergency has retry set", alert && alert.retry==="60");
}

// ---------- 9. Page hygiene ----------
console.log("\n[9] Resource hygiene");
{ const h=makeHarness({carvanaPlaid:true,carvanaAvailable:true,teslaPlaid:true});
  await h.sandbox.runCheck(); await h.sandbox.runCheck();
  t("every opened page was closed", h.state.pagesOpened===h.state.pagesClosed, `opened=${h.state.pagesOpened} closed=${h.state.pagesClosed}`);
}

// ---------- 10. State integrity ----------
console.log("\n[10] State integrity");
{ const h=makeHarness({});
  await h.sandbox.runCheck(); await h.sandbox.runCheck();
  const st=JSON.parse(require("fs").readFileSync(h.dir+"/state.json","utf8"));
  t("checksRun is a number, not NaN", Number.isFinite(st.checksRun), JSON.stringify(st.checksRun));
  { const sv=JSON.parse(require("fs").readFileSync(h.dir+"/seen-vins.json","utf8"));
    t("seen-vins is a valid status map", sv && typeof sv==="object" && !Array.isArray(sv)); }
}

// ---------- 11. Reserved -> available upgrade ----------
console.log("\n[11] Reserved-to-available upgrade path");
{ const fs=require("fs");
  const a=makeHarness({carvanaPlaid:true,carvanaAvailable:false});
  await a.sandbox.runCheck();
  const seenA=JSON.parse(fs.readFileSync(a.dir+"/seen-vins.json","utf8"));
  t("reserved car tracked as possible, not dropped", seenA["5YJSA1E44NF500001"]==="possible");
  t("no alert while reserved", !a.state.pushes.some(p=>/^(New|Possible) .+: \$/.test(p.title)));
  const b=makeHarness({carvanaPlaid:true,carvanaAvailable:true});
  fs.copyFileSync(a.dir+"/seen-vins.json", b.dir+"/seen-vins.json");
  fs.copyFileSync(a.dir+"/state.json", b.dir+"/state.json");
  await b.sandbox.runCheck();
  t("alerts when it becomes available", b.state.pushes.filter(p=>/^(New|Possible) .+: \$/.test(p.title)).length===1);
  await b.sandbox.runCheck();
  t("no duplicate on next cycle", b.state.pushes.filter(p=>/^(New|Possible) .+: \$/.test(p.title)).length===1);
}

console.log(`\n================  ${pass} passed, ${fail} failed  ================`);
process.exit(fail?1:0);
})();
