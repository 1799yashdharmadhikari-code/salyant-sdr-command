export default async function handler(req,res){
 const targetBase=process.env.SALYANT_N8N_BASE_URL; const secret=process.env.SALYANT_N8N_COMMAND_SECRET;
 if(!targetBase||!secret){res.status(503).json({error:'Salyant proxy not configured'});return;}
 const path='/webhook/salyant-sdr/api/mail'; const qs=req.url?.includes('?')?req.url.slice(req.url.indexOf('?')):'';
 try{const body=req.method==='GET'||req.method==='HEAD'?undefined:(typeof req.body==='string'?req.body:JSON.stringify(req.body||{}));
  const r=await fetch(targetBase.replace(/\/$/,'')+path+qs,{method:req.method,headers:{'accept':'application/json','content-type':'application/json','x-salyant-command-secret':secret},body});
  const text=await r.text();let data;try{data=text?JSON.parse(text):{}}catch{data={raw:text}}res.status(r.status).json(data);
 }catch(e){res.status(502).json({error:'Upstream unavailable',message:String(e?.message||e)});}
}
