const fs=require("fs"), path=require("path"), F=require("./fixtures");

function makeHarness(opts={}){
  const dir = fs.mkdtempSync(require("os").tmpdir()+"/plaid-test-");
  const cfg = Object.assign({
    PUSHOVER_USER:"uKEY", PUSHOVER_TOKEN:"tKEY",
    CHECK_INTERVAL_SECONDS:60, ZIP:"03229", NATIONWIDE:true, YEAR_MIN:2026,
    NIGHT_PAUSE:false, NIGHT_START_HOUR:0, NIGHT_END_HOUR:6,
    HEALTHCHECK_PING_URL:"https://hc-ping.com/testuuid",
    LAT:43.5,LNG:-71.4,REGION:"NH",
    USE_REAL_CHROME:false, HEADLESS:true,
    SOURCE_INTERVAL_MINUTES:{tesla:0,carvana:0,carmax:0},
    USE_STEALTH:false, CHROME_CDP_URL:null,
    TESLA_CONDITIONS:["new","used"], TESLATRACKER_FALLBACK:true
  }, opts.config||{});
  fs.writeFileSync(path.join(dir,"config.json"), JSON.stringify(cfg,null,2));

  const state = { pushes:[], pings:0, teslaRequests:0, carmaxRequests:0, carvanaRequests:0,
                  browserLaunches:0, pagesOpened:0, pagesClosed:0, failSources:opts.failSources||[] };

  // Fake page object
  function makePage(){
    state.pagesOpened++;
    let lastUrl="";
    return {
      async goto(url){
        lastUrl=url;
        if(url.includes("tesla.com/inventory/api")){
          state.teslaRequests++;
          if(state.failSources.includes("tesla")) return {status:()=>403};
          return {status:()=>200};
        }
        if(url.includes("tesla.com/inventory/used")) return {status:()=>200};
        if(url.includes("carmax.com")){
          state.carmaxRequests++;
          if(state.failSources.includes("carmax")) return {status:()=>403};
          return {status:()=>200};
        }
        if(url.includes("carvana.com/vehicle/")) return {status:()=>200};
        if(url.includes("carvana.com")){
          state.carvanaRequests++;
          if(state.failSources.includes("carvana")) return {status:()=>403};
          return {status:()=>200};
        }
        if(url.includes("teslatracker.com")) return {status:()=>200};
        return {status:()=>200};
      },
      async evaluate(fn){
        const q=decodeURIComponent((lastUrl.split("query=")[1]||""));
        let cond="used", off=0, trimFiltered=false;
        try{ const j=JSON.parse(q); cond=j.query.condition; off=j.outsideOffset||0;
             trimFiltered=!!(j.query.options&&j.query.options.TRIM); }catch{}
        return F.teslaPage(off,cond,{trimFiltered,includePlaid:opts.teslaPlaid});
      },
      async content(){
        if(lastUrl.includes("carmax.com")) return F.carmaxHtml(opts.carmaxPlaid);
        if(lastUrl.includes("carvana.com/vehicle/")) return F.carvanaDetail(opts.carvanaAvailable!==false, opts.carvanaHoldMinutes);
        if(lastUrl.includes("carvana.com")) return F.carvanaHtml(opts.carvanaPlaid, opts.carvanaPending);
        if(lastUrl.includes("teslatracker.com")) return '<html>\\"vin\\":\\"5YJSA1E55RF600001\\",\\"year\\":2026,\\"trim\\":\\"Plaid\\",\\"source\\":\\"tesla\\",\\"currentPrice\\":11900000,\\"mileage\\":2100</html>';
        return "<html></html>";
      },
      async waitForTimeout(){ },
      async close(){ state.pagesClosed++; }
    };
  }

  const fakeBrowser={ isConnected:()=>true, contexts:()=>[], async newPage(){return makePage();},
                      async newContext(){ return { newPage: async()=>makePage() }; }, async close(){} };

  // Load tracker source, strip auto-start, inject stubs
  let src=fs.readFileSync(require("path").join(__dirname,"..","tracker.js"),"utf8");
  src=src.replace(/\nmain\(\);\s*$/,"\n");
  src=src.replace('const CONFIG_PATH = path.join(__dirname, "config.json");',
                  `const CONFIG_PATH = ${JSON.stringify(path.join(dir,"config.json"))};`);
  src=src.replace('const SEEN_PATH = path.join(__dirname, "seen-vins.json");',
                  `const SEEN_PATH = ${JSON.stringify(path.join(dir,"seen-vins.json"))};`);
  src=src.replace('const STATE_PATH = path.join(__dirname, "state.json");',
                  `const STATE_PATH = ${JSON.stringify(path.join(dir,"state.json"))};`);

  const fakeRequire=(name)=>{
    if(name==="playwright"||name==="playwright-extra"){
      state.browserLaunches++;
      return {chromium:{async launch(){return fakeBrowser;},async connectOverCDP(){return fakeBrowser;},use(){}}};
    }
    if(name==="puppeteer-extra-plugin-stealth") return ()=>({});
    return require(name);
  };
  const sandbox={require: fakeRequire, console, __dirname:dir, module:{exports:{}},
    fetch: async(url,o)=>{
      if(String(url).includes("hc-ping")){ state.pings++; return {ok:true,status:200}; }
      if(String(url).includes("pushover")){ state.pushes.push(Object.fromEntries(new URLSearchParams(o.body))); return {ok:true,status:200,text:async()=>""}; }
      if(String(url).includes("carmax")){ state.carmaxRequests++; if(state.failSources.includes("carmax")) return {ok:false,status:403}; return {ok:true,status:200,text:async()=>F.carmaxHtml(opts.carmaxPlaid)}; }
      return {ok:true,status:200,text:async()=>"<html></html>",json:async()=>({})};
    },
    setTimeout, clearTimeout, setInterval, clearInterval, Date, Math, JSON, Set, Map, Number, String, Object, Array, Promise, RegExp, Error, URLSearchParams, process, Buffer
  };
  sandbox.global=sandbox;

  const vm=require("vm"); vm.createContext(sandbox);
  vm.runInContext(src, sandbox, {filename:"tracker.js"});
  return {sandbox,state,dir};
}
module.exports={makeHarness};
