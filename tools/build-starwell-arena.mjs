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
const X=88,Z=88,F=9, b=createBuilder('Starwell • the Fractured Orbit',{x:177,y:34,z:177},'1.20.1');
const put=(x,y,z,s)=>b.set(x+X,y,z+Z,s), at=(x,y,z)=>b.get(x+X,y,z+Z);
const black='polished_blackstone_bricks', edge='polished_deepslate', stone='deepslate_tiles', pale='smooth_quartz', prism='dark_prismarine', lamp='sea_lantern',glass='cyan_stained_glass',gold='smooth_quartz';
const fill=(x,a,z,y,s)=>{for(let h=a;h<=y;h++)put(x,h,z,s)};
const stair=(base,dir)=>`${base}_stairs[facing=${dir},half=bottom,shape=straight,waterlogged=false]`;
const face=(x,z)=>Math.abs(x)>Math.abs(z)?(x>0?'east':'west'):(z>0?'south':'north');
const opp={east:'west',west:'east',north:'south',south:'north'};
const radial=(r,a)=>[Math.round(r*Math.cos(a)),Math.round(r*Math.sin(a))];
const relief=r=>Math.max(0,8-Math.max(0,Math.floor(r-11))) + (r>=29&&r<40 ? Math.min(3,1+Math.floor(r-29),Math.ceil(40-r)) : 0);
for(let x=-63;x<=63;x++)for(let z=-63;z<=63;z++){
 const r=Math.hypot(x,z);if(r>63.4)continue;
 const a=Math.atan2(z,x),axis=Math.min(Math.abs(x),Math.abs(z));
 // A roofed double circuit beneath the altar, linked by four aisles and a central service chamber.
 const room=r<=6 || (r>=24&&r<=29)||(r>=49&&r<=54.7)||axis<=2;
 put(x,0,z,room&&axis===0?prism:stone);
 if(!room)fill(x,1,z,7,black);
 else {put(x,7,z,black);if((Math.abs(x)+Math.abs(z))%8===0)put(x,7,z,lamp)}
 put(x,8,z,edge);
 if(r<49){
  // Basalt iris, two almond-shaped lids, twelve nested pointed petals and copper inlay.
  const eye=Math.abs(Math.abs(z)-11*Math.max(0,1-(x/30)**2))<.58&&Math.abs(x)<=30;
  const petal=Math.abs(r-(26+12*Math.pow(Math.abs(Math.cos(6*a)),.70)))<.62;
  const rings=[7,40,47].some(n=>Math.abs(r-n)<.5);
  const spoke=r>39&&Math.abs(Math.sin(6*a))*r<.6;
  let tile=r<7?'polished_basalt[axis=y]':r<40?(Math.cos(12*a)>0?stone:black):((Math.floor((a+Math.PI)*12/Math.PI)%2)?'gray_concrete':'black_concrete');
  if(eye||petal||rings||spoke){tile=eye||Math.abs(r-40)<.5?gold:glass;}
  const half=relief(r), h=F+Math.floor(half/2); fill(x,8,z,h-1,black);
  if(tile===glass)put(x,h-1,z,lamp);
  put(x,h,z,tile);
  if(half%2)put(x,h+1,z,(tile===gold?'quartz':tile===glass||tile===prism?'dark_prismarine':'polished_blackstone_brick')+'_slab[type=bottom]');
 }else if(r<51){
  fill(x,F,z,F+4,black);put(x,F+2,z,prism);put(x,F+5,z,'polished_blackstone_brick_slab[type=bottom]');
 }else{
  const h=F+Math.min(5,Math.floor((r-51)/2));fill(x,F,z,h,black);
  const dir=face(x,z);const step=Math.floor(r-51)%2===0&&r<61;
  put(x,h,z,axis<3?(step?stair('polished_blackstone_brick',dir):edge):step?stair('quartz',dir):'smooth_quartz');
  if(r>=62){fill(x,h+1,z,h+2,stone);put(x,h+3,z,'deepslate_tile_slab[type=bottom]')}
 }
}
// Cardinal portals are recessed, with a solid lintel and player-operable gates; never leave an open plume path through the arena wall.
const rot=(u,v,k)=>[[u,v],[-v,u],[-u,-v],[v,-u]][k];
for(let k=0;k<4;k++){
 const P=(u,y,v,s)=>{const [x,z]=rot(u,v,k);put(x,y,z,s)};
 for(let v=48;v<=52;v++) for(let u=-1;u<=1;u++){
  P(u,F,v,prism);for(let y=F+1;y<=F+4;y++)P(u,y,v,'air');
  if(v===49)P(u,F+1,v,`dark_oak_fence_gate[facing=${['south','west','north','east'][k]},open=false]`);
 }
 // Lower portals into the crypt at three outer axial walls; southern one's behind grand stair, use others.
 if(k!==0)for(let v=53;v<=63;v++)for(let u=-2;u<=2;u++)for(let y=1;y<=4;y++)P(u,y,v,'air');
 // Walk down an internal stair from the outer concourse to the outer crypt ring.
 for(let i=0;i<=10;i++)for(let v=51;v<=53;v++){
  const y=1+i;for(let h=y+1;h<=17;h++)P(i+8,h,v,'air');P(i+8,y,v,stair('deepslate_tile',['east','south','west','north'][k]));
 }
}
// Monumental column order: fluted quartz shafts, dark bases, copper capitals, pointed flying buttresses.
for(let n=0;n<24;n++){
 const a=(n+.5)*Math.PI/12,[cx,cz]=radial(59,a), base=14;
 for(let u=-3;u<=3;u++)for(let v=-3;v<=3;v++){
  const m=Math.abs(u)+Math.abs(v);if(m>4)continue;
  fill(cx+u,base,cz+v,base+1,edge);put(cx+u,base+2,cz+v,m===4?'quartz_slab[type=bottom]':pale);
  if(m<=2)fill(cx+u,base+3,cz+v,base+15,'quartz_pillar[axis=y]');
  if(m<=3)put(cx+u,base+16,cz+v,'chiseled_quartz_block');
  put(cx+u,base+17,cz+v,m===4?'purpur_slab[type=bottom]':'end_stone_bricks');
 }
 fill(cx,32,cz,35,'polished_blackstone_brick_wall');put(cx,36,cz,'lightning_rod[facing=up]');
 // Arc from pillar to pillar: paired lancet ribs meet at a suspended keystone.
 for(let i=0;i<=32;i++){
  const t=i/32,[x,z]=radial(59,a+t*Math.PI/12);const y=29+Math.round(6*(1-Math.abs(2*t-1))**.62);
  put(x,y,z,black);put(x,y+1,z,'deepslate_tile_slab[type=bottom]');
  if(i===16){put(x,y,z,'chiseled_polished_blackstone');put(x,y-1,z,'chain[axis=y]');put(x,y-2,z,'soul_lantern[hanging=true]')}
 }
 // Hooked, inward-bending 'tentacle' pinnacles at the four cardinal portals.
 if(n%6===0)for(let j=0;j<=9;j++){
  const [x,z]=radial(55-j*.55,a+.035*Math.sin(j*.6));fill(x,14+j,z,15+j,j<6?prism:'polished_blackstone_brick_wall');if(j===9)put(x,24,z,'amethyst_cluster[facing=up]');
 }
}
// Keep each portal axis as a continuous promenade. The crypt now descends from a side bay.
for(let k=0;k<4;k++)for(let v=48;v<=62;v++)for(let u=-1;u<=1;u++){
 const [x,z]=rot(u,v,k),h=F+Math.max(0,Math.min(5,Math.floor((v-51)/2)));
 const rising=v>=53&&v<=61&&v%2===1;
 put(x,h,z,rising?stair('quartz',['south','west','north','east'][k]):pale);
 for(let y=h+1;y<=h+4;y++)if(!(v===49&&y===F+1))put(x,y,z,'air');
}
// Southern processional stair: from the exterior at grade to the high spectator promenade.
for(let z=62;z<=79;z++)for(let x=-5;x<=5;x++){
 const h=Math.max(0,Math.min(14,14-(z-63)));fill(x,0,z,h,black);for(let y=h+1;y<=18;y++)put(x,y,z,'air');
 put(x,h,z,z<=63||z>=78?pale:stair('quartz','north'));
 if(Math.abs(x)===5){put(x,h+1,z,'polished_blackstone_brick_wall');if(z%5===0)put(x,h+2,z,'soul_lantern')}
}
// Recessed stone braziers midway between the portals, alternating two fire colours.
const braziers=[];
for(let n=0;n<12;n++){
 const [x,z]=radial(50,(n+.5)*Math.PI/6); const soul=n%2===1;
 for(let i=-1;i<=1;i++)for(let j=-1;j<=1;j++){
  put(x+i,15,z+j,black);put(x+i,16,z+j,i||j?'polished_blackstone_brick_slab[type=bottom]':soul?'soul_sand':'netherrack');
 }
 put(x,17,z,soul?'soul_fire':'fire[age=0]');braziers.push([x,z,soul]);
}
// The astrolabe is open, not a roof. Connecting diagonal segments with corner blocks keeps its silhouette continuous.
function trace(x0,z0,x1,z1,y,state){let px=Math.round(x0),pz=Math.round(z0);const count=Math.ceil(Math.hypot(x1-x0,z1-z0)*3);
 for(let k=0;k<=count;k++){const t=k/count,x=Math.round(x0+(x1-x0)*t),z=Math.round(z0+(z1-z0)*t);if(px!==x&&pz!==z)put(x,y,pz,state);put(x,y,z,state);px=x;pz=z;}}
// Detached arcs: separate rolls, eccentricities and offsets. Leave actual air between the glass fracture lips.
const arcs=[[54,35,5,96,.08,1,-1],[52,38,108,153,-.19,3,0],[54,34,169,259,.07,0,1],[55,37,270,346,-.13,-2,-2],
 [39,45,-24,49,-.15,1,2],[38,42,62,149,.1,-1,0],[40,47,169,212,-.23,3,-2],[37,44,235,319,.11,-2,1]];
for(const [r,y,start,end,tilt,ox,oz] of arcs){
 const point=d=>{const t=d*Math.PI/180;return [Math.round(ox+r*Math.cos(t)),Math.round(y+tilt*r*Math.sin(t-.6)),Math.round(oz+r*.95*Math.sin(t))]};
 let prev=point(start);
 for(let d=start;d<=end;d+=.5){const [x,h,z]=point(d),lip=Math.min(d-start,end-d);
  const block=lip<2?'purple_stained_glass':lip<5?'magenta_stained_glass':Math.floor(d+360)%29<2?lamp:r>45?pale:'purpur_pillar[axis=y]';
  trace(prev[0],prev[2],x,z,h,block);if(prev[1]!==h)fill(x,Math.min(h,prev[1]),z,Math.max(h,prev[1]),block);prev=[x,h,z];}
}
for(let i=0;i<6;i++){
 const a=i*Math.PI/3-Math.PI/2,c=a+2*Math.PI/3;trace(31*Math.cos(a),31*Math.sin(a),31*Math.cos(c),31*Math.sin(c),45,'purpur_block');
 const [x,z]=radial(31,a);put(x,45,z,lamp);put(x,46,z,'amethyst_cluster[facing=up]');put(x,44,z,'chain[axis=y]');put(x,43,z,'soul_lantern[hanging=true]');
}
// Asymmetric, deterministic impact fragments with a tapering train of detached splinters.
const meteors=[[-32,-23,51,4,1,-1],[31,-32,46,3,1,1],[39,18,52,3,-1,1],[-35,28,41,2,-1,-1],[-17,35,55,2,1,1]];
for(const [cx,cz,cy,size,dx,dz] of meteors){
 for(let x=-size-1;x<=size+1;x++)for(let y=-size;y<=size;y++)for(let z=-size;z<=size;z++){
  const h=Math.abs(x*53+y*83+z*31+cx*11)%23;
  if(((x+.25*y)/(size+.7))**2+(y/(size*.8))**2+(z/size)**2>1+(h-11)*.019)continue;
  put(cx+x,cy+y,cz+z,h<=2?'magma_block':h===3?'budding_amethyst':h<10?'basalt[axis=y]':'blackstone');
 }
 let seed=(cx*997+cz*199+cy*97)>>>0;const rnd=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
 for(let n=0;n<9+size;n++){const t=size+2+rnd()*16,side=(rnd()-.5)*(2+t*.64);
  const x=Math.round(cx+dx*t-dz*side),z=Math.round(cz+dz*t*.48+dx*side),y=Math.round(cy+(n%3===0?-.33:.4)*t+(rnd()-.5)*6);
  put(x,y,z,['crying_obsidian','end_stone','blackstone','basalt'][n%4]);if(rnd()>.44)put(x-dx,y+1,z,n%2?'purple_stained_glass':'purpur_block');}
}
// Eight tapered foundation fins occupy the bays between the four axial entrances.
const tips=[];
for(let k=0;k<8;k++){
 const a=(k+.5)*Math.PI/4,c=Math.cos(a),s=Math.sin(a),length=k%2?87:92;
 for(let x=-87;x<=87;x++)for(let z=-87;z<=87;z++){
  const u=x*c+z*s,v=Math.abs(-x*s+z*c);if(u<59||u>length||Math.hypot(x,z)<=63.4)continue;
  const width=19*(length-u)/(length-59);if(v>width+.45)continue;
  const h=Math.max(1,Math.round(9*(length-u)/(length-59)));fill(x,0,z,h-1,'end_stone_bricks');
  put(x,h,z,width-v<1.1?pale:v<.85?'purple_stained_glass':v<2?'purpur_block':v>width*.67?'light_gray_concrete':'black_concrete');if(v<.85)put(x,h-1,z,lamp);
 }
 const [x,z]=radial(length-2,a);put(x,2,z,'chiseled_quartz_block');put(x,3,z,'end_rod[facing=up]');tips.push([x,z]);
}
// Eight of nine base blocks: finish from the southern service aisle at (0,1,1).
for(let x=-1;x<=1;x++)for(let z=-1;z<=1;z++)put(x,1,z,x===0&&z===1?'air':'iron_block');
put(0,2,0,'beacon');for(let y=3;y<=F+4;y++)put(0,y,0,y===F+4?'glass':'air');
const ir=b.build(); const dir='src/main/resources/data/tinkersnewlife/structures';fs.mkdirSync(dir,{recursive:true});
const name=path.join(dir,'starwell_arena.nbt');const bytes=serializeStructure(ir,'nbt');fs.writeFileSync(name,bytes);
const round=parseStructureAuto(new Uint8Array(fs.readFileSync(name)),name),reg=round.regions[0];
const cell=(x,y,z)=>reg.palette[reg.blocks[x+X+(z+Z)*reg.size.x+y*reg.size.x*reg.size.z]].name.split(':').pop();
const check=(v,msg)=>{if(!v)throw Error(msg)};check(cell(0,2,0)==='beacon','beacon');for(let y=3;y<reg.size.y;y++)check(['air','glass'].includes(cell(0,y,0)),'beam');
for(const [x,z,soul] of braziers){check(cell(x,17,z)===(soul?'soul_fire':'fire')&&cell(x,16,z)===(soul?'soul_sand':'netherrack'),'brazier')};for(const [x,z,y] of meteors)check(cell(x,y,z)!=='air','meteor');
// Verify headroom and all neighbouring surface transitions in half-block units from re-imported blocks.
const height=(x,z)=>{for(let y=F+6;y>=F;y--){const s=cell(x,y,z);if(s!=='air')return 2*(y+1)-(s.endsWith('_slab')?1:0)}throw Error('hole')};
for(let x=-48;x<=48;x++)for(let z=-48;z<=48;z++){const r=Math.hypot(x,z);if(r>48)continue;
 const h=height(x,z);check(h===2*(F+1)+relief(r),'surface');const top=Math.ceil(h/2);check(cell(x,top,z)==='air'&&cell(x,top+1,z)==='air','headroom');
 for(const [dx,dz] of [[1,0],[0,1]])if(Math.hypot(x+dx,z+dz)<=48)check(Math.abs(h-height(x+dx,z+dz))<=1,'step too tall');}
// Validate closed enclosure around the floor: no unsupported gap in the wall belt at player height.
for(let x=-51;x<=51;x++)for(let z=-51;z<=51;z++)if(Math.hypot(x,z)>=49&&Math.hypot(x,z)<51&&Math.min(Math.abs(x),Math.abs(z))>1)check(cell(x,F+1,z)!=='air','wall');
for(let k=0;k<4;k++){const [x,z]=rot(0,49,k);check(cell(x,F+1,z)==='dark_oak_fence_gate','gate')}
for(const [x,z] of tips){check(cell(x,3,z)==='end_rod' && cell(x,0,z)!=='air','star tip')}
for(let k=0;k<4;k++)for(let v=48;v<=(k===0?79:62);v++)for(let u=-1;u<=1;u++){
 const [x,z]=rot(u,v,k),h=v>=62&&k===0?Math.max(0,Math.min(14,14-(v-63))):F+Math.max(0,Math.min(5,Math.floor((v-51)/2)));
 // The southern exterior landing meets the interior promenade at the same elevation.
 const y=h;
 check(cell(x,y,z)!=='air','missing entrance floor');for(let dy=1;dy<=3;dy++)check(cell(x,y+dy,z)==='air'||(v===49&&dy===1),'blocked entrance '+[x,y+dy,z]);
}
const walk=new Set(['0,2']);const queue=[[0,2]];for(let i=0;i<queue.length;i++){const [x,z]=queue[i];for(const [dx,dz] of [[1,0],[-1,0],[0,1],[0,-1]]){const u=x+dx,v=z+dz,key=u+','+v;if(Math.hypot(u,v)>61||walk.has(key)||cell(u,0,v)==='air'||cell(u,1,v)!=='air'||cell(u,2,v)!=='air')continue;walk.add(key);queue.push([u,v]);}}check(walk.has('-51,0')&&walk.has('0,-51')&&walk.has('26,0')&&walk.has('0,51'),'crypt disconnected');
fs.mkdirSync('build/arena',{recursive:true});fs.writeFileSync('build/arena/starwell_arena.schem',serializeStructure(ir,'schem'));
const top=[];for(let z=0;z<reg.size.z;z++)for(let x=0;x<reg.size.x;x++){for(let y=reg.size.y-1;y>=0;y--){const id=cell(x-X,y,z-Z);if(id!=='air'){top.push([x,y,z,id]);break}}}fs.mkdirSync('build/arena',{recursive:true});fs.writeFileSync('build/arena/top.json',JSON.stringify(top));console.log({name,bytes:bytes.length,size:reg.size,palette:reg.palette.length,cryptWalkable:walk.size});
}finally{await vite.close()}
