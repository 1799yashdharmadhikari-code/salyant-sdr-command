/* Salyant SDR — shared browser API client. */
window.SALYANT_API=Object.freeze({
  chat:'/api/salyant-sdr/command',
  command:'/api/salyant-sdr/command',
  accounts:'/api/salyant-sdr/accounts',
  mail:'/api/salyant-sdr/mail',
  send:'/api/salyant-sdr/send',
  health:'/api/salyant-sdr/health'
});
async function salyantFetch(url,options={}){
 const r=await fetch(url,{...options,headers:{Accept:'application/json',...(options.body?{'Content-Type':'application/json'}:{}),...(options.headers||{})}});
 const t=await r.text();let d={};try{d=t?JSON.parse(t):{}}catch{d={raw:t}};
 if(!r.ok)throw new Error(d?.message||d?.error||d?.raw||`HTTP ${r.status}`);return d;
}
window.SalyantAPI={
 health(){return salyantFetch(window.SALYANT_API.health)},
 accounts(){return salyantFetch(window.SALYANT_API.accounts)},
 mail(account,limit=25){const p=new URLSearchParams({account,limit:String(limit)});return salyantFetch(`${window.SALYANT_API.mail}?${p}`)},
 send(payload){return salyantFetch(window.SALYANT_API.send,{method:'POST',body:JSON.stringify(payload)})},
 command(payload){return salyantFetch(window.SALYANT_API.command,{method:'POST',body:JSON.stringify(payload)})},
 chat(chatInput,account='acc-main',sessionId='director-session'){return salyantFetch(window.SALYANT_API.command,{method:'POST',body:JSON.stringify({mode:'chat',message:chatInput,chatInput,sessionId,account})})}
};
