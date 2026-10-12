// Local build inputs: reuse approved art and the user-approved piano audition.
const fs=require('fs'), path=require('path');
const root=__dirname, target=path.join(root,'app','src','main','assets');
const externalArt=path.join(root,'..','assets');
const source=fs.existsSync(path.join(externalArt,'app-icon.png'))?externalArt:path.join(target,'art');
fs.mkdirSync(path.join(target,'art'),{recursive:true});
for(const name of ['app-icon.png','otter-companion.png','otter-ready.png','otter-asleep.png','otter-room.png']){const input=path.join(source,name),output=path.join(target,'art',name);if(!fs.existsSync(input))throw Error('Missing art: '+name);if(input!==output)fs.copyFileSync(input,output);}
fs.mkdirSync(path.join(root,'app','src','main','res','drawable-nodpi'),{recursive:true});
fs.copyFileSync(path.join(source,'app-icon.png'),path.join(root,'app','src','main','res','drawable-nodpi','app_icon.png'));
fs.mkdirSync(path.join(target,'audio'),{recursive:true});
const preview=path.join(root,'..','audio','C-舒缓钢琴录音.mp3');
const approved=fs.existsSync(preview)?preview:path.join(target,'audio','moonlight.mp3');
const music=fs.readFileSync(approved);
const hash=require('crypto').createHash('sha256').update(music).digest('hex');
if(hash!=='ce282ebbf3a0ff07dc15589a30543d93f9dd009a1c7181d87f710c07ac32bb46')throw Error('Approved piano asset missing or changed');
if(approved!==path.join(target,'audio','moonlight.mp3'))fs.copyFileSync(approved,path.join(target,'audio','moonlight.mp3'));
const obsolete=path.join(target,'audio','moonlight.wav');
if(fs.existsSync(obsolete))fs.unlinkSync(obsolete);
console.log(JSON.stringify({assets:5,audioSeconds:90,music:'Vaporware',sha256:hash,bytes:music.length}));
