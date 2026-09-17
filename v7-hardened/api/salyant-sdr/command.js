export default async function handler(req,res){
 if(req.method!=='POST'){res.status(405).json({error:'Method not allowed'});return}
 const target=process.env.SALYANT_N8N_COMMAND_URL; const secret=process.env.SALYANT_N8N_COMMAND_SECRET;
 if(!target||!secret){res.status(503).json({error:'Command proxy is not configured'});return}
 try{const body=typeof req.body==='string'?JSON.parse(req.body):req.body||{};
  const upstream=await fetch(target,{method:'POST',headers:{'content-type':'application/json','accept':'application/json','x-salyant-command-secret':secret},body:JSON.stringify(body),signal:AbortSignal.timeout(120000)});
  const text=await upstream.text();let data;try{data=text?JSON.parse(text):{}}catch{data={raw:text}}res.status(upstream.status).json(data);
 }catch(e){res.status(502).json({error:'Command upstream failed',message:String(e?.message||e)})}
}
