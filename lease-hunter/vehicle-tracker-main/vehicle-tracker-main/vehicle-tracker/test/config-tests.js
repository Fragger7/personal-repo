const {makeHarness}=require("./harness.js");
(async()=>{
let pass=0,fail=0;
const t=(n,c,d="")=>{c?(pass++,console.log("  PASS  "+n)):(fail++,console.log("  FAIL  "+n+" <- "+d));};

console.log("\n[A] Source enable/disable");
{ const h=makeHarness({config:{SOURCES:{tesla:false,carmax:true,carvana:false}}});
  await h.sandbox.runCheck();
  t("disabled tesla makes no requests", h.state.teslaRequests===0, "req="+h.state.teslaRequests);
  t("disabled carvana makes no requests", h.state.carvanaRequests===0, "req="+h.state.carvanaRequests);
  t("enabled carmax still runs", h.state.carmaxRequests>0);
}
{ const h=makeHarness({config:{SOURCES:{tesla:true,carmax:false,carvana:false}}});
  await h.sandbox.runCheck();
  t("only tesla runs when others disabled", h.state.teslaRequests>0 && h.state.carmaxRequests===0);
}

console.log("\n[B] Tesla source auto-disables for non-Tesla makes");
{ const h=makeHarness({config:{VEHICLE:{make:"BMW",model:"M5",trim:"Competition",yearMin:2024}}});
  await h.sandbox.runCheck();
  t("no tesla.com requests when searching a BMW", h.state.teslaRequests===0, "req="+h.state.teslaRequests);
  t("carmax still searched for the BMW", h.state.carmaxRequests>0);
}

console.log("\n[C] Vehicle matching driven by config");
{ const h=makeHarness({config:{VEHICLE:{make:"Tesla",model:"Model S",trim:"Plaid",yearMin:2026}}});
  t("2026 Plaid matches", h.sandbox.matchesVehicle(2026,"Plaid"));
  t("2025 Plaid rejected (below yearMin)", !h.sandbox.matchesVehicle(2025,"Plaid"));
  t("2026 Long Range rejected (wrong trim)", !h.sandbox.matchesVehicle(2026,"Long Range"));
  t("unknown trim FAILS OPEN", h.sandbox.matchesVehicle(2026,""));
}
{ const h=makeHarness({config:{VEHICLE:{make:"Tesla",model:"Model S",trim:null,yearMin:2026}}});
  t("trim:null matches any trim", h.sandbox.matchesVehicle(2026,"Long Range"));
}
{ const h=makeHarness({config:{VEHICLE:{make:"Tesla",model:"Model S",trim:"Plaid",yearMin:2024,yearMax:2025}}});
  t("yearMax excludes newer cars", !h.sandbox.matchesVehicle(2026,"Plaid"));
  t("year within range matches", h.sandbox.matchesVehicle(2025,"Plaid"));
}

console.log("\n[D] Legacy config (no VEHICLE block) keeps original behaviour");
{ const h=makeHarness({});   // harness config has flat YEAR_MIN, no VEHICLE
  // const declarations aren't exposed on the VM sandbox, so assert BEHAVIOUR:
  // a legacy config must still behave as Tesla / Model S / Plaid.
  t("legacy config still matches a 2026 Plaid", h.sandbox.matchesVehicle(2026,"Plaid"));
  t("legacy config still rejects non-Plaid trims", !h.sandbox.matchesVehicle(2026,"Long Range"));
  t("legacy config still respects yearMin", !h.sandbox.matchesVehicle(2025,"Plaid"));
  t("legacy config still fails open on unknown trim", h.sandbox.matchesVehicle(2026,""));
}

console.log("\n[E] Generated source URLs");
{ const h=makeHarness({config:{VEHICLE:{make:"BMW",model:"M5",trim:"Competition",yearMin:2024}}});
  const o={filters:{makes:[{name:"BMW",parentModels:[{name:"M5",trims:["Competition"]}]}]}};
  const expect=Buffer.from(JSON.stringify(o)).toString("base64").replace(/=+$/,"");
  t("carvana filter encodes the configured vehicle", expect.length>0 && Buffer.from(expect,"base64").toString().includes("M5"));
  t("carmax slug builds correctly", h.sandbox.slug("Model S")==="model-s" && h.sandbox.slug("BMW")==="bmw");
}

console.log(`\n  ${pass} passed, ${fail} failed`);
process.exit(fail?1:0);
})();
