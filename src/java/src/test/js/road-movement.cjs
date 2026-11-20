const fs = require('fs'), vm = require('vm'), assert = require('node:assert/strict');
const html = fs.readFileSync('src/java/src/main/resources/static/index.html', 'utf8');
for (const m of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(m[1]);
const movement = html.slice(html.indexOf('        function moveCarAlongRoad('), html.indexOf('        function isRoadRoute('));
const validation = html.slice(html.indexOf('        function isRoadRoute('), html.indexOf('        async function retargetCarOnRoad('));
const context = { calculateDistanceKm: (a,b,c,d) => Math.hypot(a-c,b-d) };
vm.createContext(context); vm.runInContext(movement + validation, context);
const state = { lat:0, lon:0, path:[[0,1],[1,1]] };
context.moveCarAlongRoad(state,1.5);
assert.equal(state.lat,0.5); assert.equal(state.lon,1);
assert.equal(context.isRoadRoute({provider:'ROAD_GRID_FALLBACK',coordinates:[[0,0],[1,1]]}),false);
assert.equal(context.isRoadRoute({provider:'OSRM (test)',coordinates:[[0,0],[1,1]]}),false);
assert.equal(context.isRoadRoute({provider:'Mapbox Directions',coordinates:[[0,0],[1,1]]}),true);
assert.equal(context.isRoadRoute({provider:'Mapbox Directions',coordinates:[[0,0],[NaN,1]]}),false);

(async () => {
    let applied = null, url = null;
    const stats = { innerText: '' };
    Object.assign(context, {
        allDrivers: [], pickupCoords:[52.2,104.3], dropoffCoords:[52.3,104.4],
        currentRouteCoords:[], isTrafficVisible:false, currentTrafficScore:0,
        routeFeature:null, document:{ getElementById:()=>stats }, console:{error:()=>{}},
        applyRouteOnMap: coords => { applied = coords; },
        fetch: async u => { url=u; return {ok:true, json:async()=>({provider:'Mapbox Directions',
            distanceMeters:9950,durationSeconds:120,coordinates:Array.from({length:350},(_,i)=>[52.2+i*.00001,104.3])})}; }
    });
    const preview = html.slice(html.indexOf('        let routeRequestId ='),html.indexOf('        function applyRouteOnMap('));
    vm.runInContext(preview,context);
    context.refreshRoute(); await new Promise(setImmediate);
    assert.equal(applied.length,350); assert.ok(url.startsWith('/api/routes?'));
    context.fetch = async () => ({ok:false});
    context.refreshRoute(); await new Promise(setImmediate);
    assert.equal(context.currentRouteCoords.length,0); assert.ok(stats.innerText.length>0);
    console.log('PASS: script syntax, road movement, Mapbox geometry, preview without drivers, visible routing error');
})().catch(e=>{console.error(e);process.exitCode=1;});
