/* CEO Autopilot enhancement: keeps the CEO view focused on priorities and attention. */
(function(){'use strict';
 const ready=()=>{ const v=document.getElementById('salyant-ceo-view'); if(!v)return; const h=v.querySelector('.view-head p'); if(h)h.textContent='Set priorities once. The agent uses them to surface useful work and run low-risk operations automatically.'; };
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
