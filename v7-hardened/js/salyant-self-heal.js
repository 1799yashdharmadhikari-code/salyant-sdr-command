/* Salyant CEO Command Hub — browser self-healing supervisor.
   Safe by design: bounded retries, reloads failed lazy modules once, and enters safe mode instead of loops. */
(function(){
  const KEY='salyant_selfheal_v1';
  let persisted={};try{persisted=JSON.parse(localStorage.getItem(KEY)||'{}')}catch{} const state=Object.assign({bootAt:Date.now(),retries:0,lastRecovery:0,safeMode:false,aiFixRequestedAt:0},persisted); const save=()=>{try{localStorage.setItem(KEY,JSON.stringify(state))}catch{}};
  function banner(msg,kind='info'){
    let el=document.getElementById('salyant-selfheal-banner');
    if(!el){el=document.createElement('div');el.id='salyant-selfheal-banner';el.style.cssText='position:fixed;left:16px;right:16px;bottom:16px;z-index:99999;padding:12px 14px;border-radius:10px;background:#151922;color:#fff;font:13px system-ui;box-shadow:0 8px 30px rgba(0,0,0,.25)';document.body.appendChild(el)}
    el.textContent=msg; el.dataset.kind=kind; if(kind==='ok')setTimeout(()=>el.remove(),3500);
  }
  async function check(){
    try{const r=await fetch(window.SALYANT_API?.health||'/api/salyant-sdr/health',{cache:'no-store'}); if(!r.ok)throw Error('health '+r.status); state.retries=0;state.safeMode=false;save();return true}
    catch(e){return false}
  }
  async function recover(){
    if(state.safeMode)return false;
    const now=Date.now(); if(now-state.lastRecovery<30000)return false;
    state.lastRecovery=now;state.retries++;save();banner('Salyant detected a service problem. Attempting safe recovery…');
    if(state.retries<=2){
      try{if(window.SalyantLive?.health)await window.SalyantLive.health(); if(window.refreshSalyantLiveData)await window.refreshSalyantLiveData(); banner('Salyant recovered.','ok');return true}catch{}
    }
    if(state.retries===3){
      try{if(Date.now()-state.aiFixRequestedAt>3600000&&window.SalyantOpsMonitor?.fix){state.aiFixRequestedAt=Date.now();save();window.SalyantOpsMonitor.fix()}}catch{}
      ['salyant-command-control-loader','salyant-ceo-autopilot-loader'].forEach(id=>document.getElementById(id)?.remove());
      const scripts=['/js/salyant-command-control.js','/js/salyant-ceo-autopilot.js'];
      scripts.forEach((src,i)=>setTimeout(()=>{const s=document.createElement('script');s.src=src+'?recovery='+Date.now();s.async=true;document.head.appendChild(s)},i*500));
      banner('Recovered the dashboard modules. Verifying…');setTimeout(check,2500);return true;
    }
    state.safeMode=true;save();banner('Salyant entered safe mode after repeated failures. No autonomous changes will be attempted.');return false;
  }
  window.SalyantSelfHeal={check,recover,state};
  window.addEventListener('error',e=>{if(/salyant|command|autopilot/i.test(String(e?.filename||'')+String(e?.message||'')))recover()});
  window.addEventListener('unhandledrejection',e=>{if(/network|fetch|salyant|command|autopilot/i.test(String(e?.reason||'')))recover()});
  setTimeout(async()=>{if(!(await check()))recover()},3500);
  setInterval(async()=>{if(!(await check()))recover()},60000);
})();
