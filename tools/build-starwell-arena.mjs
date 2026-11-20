// Run from this repository: node tools/build-starwell-arena.mjs [mcvis checkout]
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const root=path.resolve(process.argv[2] || 'D:/mc-structure-visualizer');
const requireTool=createRequire(path.join(root,'package.json'));
const {createServer}=await import(pathToFileURL(requireTool.resolve('vite/package.json').replace(/package\.json$/,'dist/node/index.js')));
const vite=await createServer({root,configFile:false,logLevel:'silent',optimizeDeps:{noDiscovery:true},resolve:{alias:Object.fromEntries(['core','formats'].map(k=>['@mcvis/'+k,path.join(root,`packages/${k}/src/index.ts`)]))},server:{middlewareMode:true,hmr:false,ws:false},appType:'custom'});
try {
const {createBuilder}=await vite.ssrLoadModule(path.join(root,'packages/core/src/index.ts'));
const {serializeStructure,parseStructureAuto}=await vite.ssrLoadModule(path.join(root,'packages/formats/src/index.ts'));
const X=52,Z=52,F=9, b=createBuilder('Starwell • the Sleepless Eye',{x:105,y:34,z:120},'1.20.1');
const put=(x,y,z,s)=>b.set(x+X,y,z+Z,s), at=(x,y,z)=>b.get(x+X,y,z+Z);
const black='polished_blackstone_bricks', edge='polished_deepslate', stone='deepslate_tiles', pale='smooth_quartz', prism='dark_prismarine', lamp='sea_lantern',glass='cyan_stained_glass',gold='cut_copper';
const fill=(x,a,z,y,s)=>{for(let h=a;h<=y;h++)put(x,h,z,s)};
const stair=(base,dir)=>`${base}_stairs[facing=${dir},half=bottom,shape=straight,waterlogged=false]`;
const face=(x,z)=>Math.abs(x)>Math.abs(z)?(x>0?'east':'west'):(z>0?'south':'north');
const opp={east:'west',west:'east',north:'south',south:'north'};
const radial=(r,a)=>[Math.round(r*Math.cos(a)),Math.round(r*Math.sin(a))];
for(let x=-50;x<=50;x++)for(let z=-50;z<=50;z++){
 const r=Math.hypot(x,z);if(r>50.4)continue;
 const a=Math.atan2(z,x),axis=Math.min(Math.abs(x),Math.abs(z));
 // A roofed double circuit beneath the altar, linked by four aisles and a central service chamber.
 const room=r<=6 || (r>=17&&r<=21.7)||(r>=36&&r<=41.7)||axis<=2;
 put(x,0,z,room&&axis===0?prism:stone);
 if(!room)fill(x,1,z,7,black);
 else {put(x,7,z,black);if((Math.abs(x)+Math.abs(z))%8===0)put(x,7,z,lamp)}
 put(x,8,z,edge);
 if(r<36){
  // Basalt iris, two almond-shaped lids, twelve nested pointed petals and copper inlay.
  const eye=Math.abs(Math.abs(z)-8*Math.max(0,1-(x/22)**2))<.58&&Math.abs(x)<=22;
  const petal=Math.abs(r-(19+9*Math.pow(Math.abs(Math.cos(6*a)),.70)))<.62;
  const rings=[5,30,34].some(n=>Math.abs(r-n)<.5);
  const spoke=r>28&&Math.abs(Math.sin(6*a))*r<.6;
  let tile=r<5?'polished_basalt[axis=y]':r<30?(Math.cos(12*a)>0?stone:black):prism;
  if(eye||petal||rings||spoke){tile=eye||Math.abs(r-30)<.5?gold:glass;if(tile===glass)put(x,8,z,lamp)}
  put(x,F,z,tile);
 }else if(r<38){
  fill(x,F,z,F+4,black);put(x,F+2,z,prism);put(x,F+5,z,'polished_blackstone_brick_slab[type=bottom]');
 }else{
  const h=F+Math.min(5,Math.floor((r-38)/2));fill(x,F,z,h,black);
  const dir=face(x,z);const step=Math.floor(r-38)%2===0&&r<48;
  put(x,h,z,axis<3?(step?stair('polished_blackstone_brick',dir):edge):step?stair('quartz',dir):'smooth_quartz');
  if(r>=49){fill(x,h+1,z,h+2,stone);put(x,h+3,z,'deepslate_tile_slab[type=bottom]')}
 }
}
// Cardinal portals are recessed, with a solid lintel and player-operable gates; never leave an open plume path through the arena wall.
const rot=(u,v,k)=>[[u,v],[-v,u],[-u,-v],[v,-u]][k];
for(let k=0;k<4;k++){
 const P=(u,y,v,s)=>{const [x,z]=rot(u,v,k);put(x,y,z,s)};
 for(let v=35;v<=39;v++) for(let u=-1;u<=1;u++){
  P(u,F,v,prism);for(let y=F+1;y<=F+4;y++)P(u,y,v,'air');
  if(v===36)P(u,F+1,v,`dark_oak_fence_gate[facing=${['south','west','north','east'][k]},open=false]`);
 }
 // Lower portals into the crypt at three outer axial walls; southern one's behind grand stair, use others.
 if(k!==0)for(let v=40;v<=50;v++)for(let u=-2;u<=2;u++)for(let y=1;y<=4;y++)P(u,y,v,'air');
 // Walk down an internal stair from the outer concourse to the outer crypt ring.
 for(let i=0;i<=9;i++)for(let v=38;v<=40;v++){
  const y=1+i;for(let h=y+1;h<=17;h++)P(i-4,h,v,'air');P(i-4,y,v,stair('deepslate_tile',['east','south','west','north'][k]));
 }
}
// Monumental column order: fluted quartz shafts, dark bases, copper capitals, pointed flying buttresses.
for(let n=0;n<16;n++){
 const a=n*Math.PI/8,[cx,cz]=radial(46,a), base=14;
 for(let u=-2;u<=2;u++)for(let v=-2;v<=2;v++){
  const m=Math.abs(u)+Math.abs(v);if(m>3)continue;
  fill(cx+u,base,cz+v,base+1,edge);put(cx+u,base+2,cz+v,m===3?'quartz_slab[type=bottom]':pale);
  if(m<=1)fill(cx+u,base+3,cz+v,base+10,'quartz_pillar[axis=y]');
  if(m<=2)put(cx+u,base+11,cz+v,'chiseled_quartz_block');
  put(cx+u,base+12,cz+v,m===3?'dark_prismarine_slab[type=bottom]':prism);
 }
 fill(cx,27,cz,29,'polished_blackstone_brick_wall');put(cx,30,cz,'lightning_rod[facing=up]');
 // Arc from pillar to pillar: paired lancet ribs meet at a suspended keystone.
 for(let i=0;i<=32;i++){
  const t=i/32,[x,z]=radial(46,a+t*Math.PI/8);const y=23+Math.round(6*(1-Math.abs(2*t-1))**.62);
  put(x,y,z,black);put(x,y+1,z,'deepslate_tile_slab[type=bottom]');
  if(i===16){put(x,y,z,'chiseled_polished_blackstone');put(x,y-1,z,'chain[axis=y]');put(x,y-2,z,'soul_lantern[hanging=true]')}
 }
 // Hooked, inward-bending 'tentacle' pinnacles at the four cardinal portals.
 if(n%4===0)for(let j=0;j<=9;j++){
  const [x,z]=radial(42-j*.55,a+.035*Math.sin(j*.6));fill(x,14+j,z,15+j,j<6?prism:'polished_blackstone_brick_wall');if(j===9)put(x,24,z,'amethyst_cluster[facing=up]');
 }
}
// Southern processional stair: from the exterior at grade to the high spectator promenade.
for(let z=49;z<=66;z++)for(let x=-5;x<=5;x++){
 const h=Math.max(0,14-(z-50));fill(x,0,z,h,black);for(let y=h+1;y<=18;y++)put(x,y,z,'air');
 put(x,h,z,z<=50||z>=65?pale:stair('quartz','north'));
 if(Math.abs(x)===5){put(x,h+1,z,'polished_blackstone_brick_wall');if(z%5===0)put(x,h+2,z,'soul_lantern')}
}
// Eight of nine base blocks: finish from the southern service aisle at (0,1,1).
for(let x=-1;x<=1;x++)for(let z=-1;z<=1;z++)put(x,1,z,x===0&&z===1?'air':'iron_block');
put(0,2,0,'beacon');for(let y=3;y<=F;y++)put(0,y,0,y===F?'glass':'air');
const ir=b.build(); const dir='imagination/src/main/resources/data/tinkersnewlife/structures';fs.mkdirSync(dir,{recursive:true});
const name=path.join(dir,'starwell_arena.nbt');const bytes=serializeStructure(ir,'nbt');fs.writeFileSync(name,bytes);
const round=parseStructureAuto(new Uint8Array(fs.readFileSync(name)),name),reg=round.regions[0];
const cell=(x,y,z)=>reg.palette[reg.blocks[x+X+(z+Z)*reg.size.x+y*reg.size.x*reg.size.z]].name.split(':').pop();
const check=(v,msg)=>{if(!v)throw Error(msg)};check(cell(0,2,0)==='beacon','beacon');for(let y=3;y<reg.size.y;y++)check(['air','glass'].includes(cell(0,y,0)),'beam');
for(let x=-35;x<=35;x++)for(let z=-35;z<=35;z++)if(Math.hypot(x,z)<=35){check(cell(x,F,z)!=='air'&&cell(x,F+1,z)==='air','battle floor')}
// Validate closed enclosure around the floor: no unsupported gap in the wall belt at player height.
for(let x=-38;x<=38;x++)for(let z=-38;z<=38;z++)if(Math.hypot(x,z)>=36&&Math.hypot(x,z)<38&&Math.min(Math.abs(x),Math.abs(z))>1)check(cell(x,F+1,z)!=='air','wall');
for(let k=0;k<4;k++){const [x,z]=rot(0,36,k);check(cell(x,F+1,z)==='dark_oak_fence_gate','gate')}
const walk=new Set(['0,2']);const queue=[[0,2]];for(let i=0;i<queue.length;i++){const [x,z]=queue[i];for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]){const u=x+dx,v=z+dz,key=u+','+v;if(Math.hypot(u,v)>48||walk.has(key)||cell(u,0,v)==='air'||cell(u,1,v)!=='air'||cell(u,2,v)!=='air')continue;walk.add(key);queue.push([u,v]);}}check(walk.has('-38,0')&&walk.has('0,-38')&&walk.has('19,0')&&walk.has('0,38'),'crypt disconnected');
const top=[];for(let z=0;z<reg.size.z;z++)for(let x=0;x<reg.size.x;x++){for(let y=reg.size.y-1;y>=0;y--){const id=cell(x-X,y,z-Z);if(id!=='air'){top.push([x,y,z,id]);break}}}fs.mkdirSync('build/arena',{recursive:true});fs.writeFileSync('build/arena/top.json',JSON.stringify(top));console.log({name,bytes:bytes.length,size:reg.size,palette:reg.palette.length,cryptWalkable:walk.size});
}finally{await vite.close()}
