import assert from 'node:assert/strict';
import {LiveAuction} from '../client/live.js';

const tick = () => new Promise(resolve => setTimeout(resolve, 0));
const sockets = [];
let response = {auction: {version: 0, currentPrice: 1000}};
let resolveRead;
let delayed = false;
let reads = 0;
const applied = [];
const statuses = [];
const live = new LiveAuction({
  id: 'auction-A', url: 'ws://unit-test',
  snapshot: async () => {
    reads++;
    if (delayed) return new Promise(resolve => {resolveRead = resolve;});
    return structuredClone(response);
  },
  apply: snapshot => applied.push(snapshot.auction), status: text => statuses.push(text),
  socketFactory: () => {
    const socket = {send: message => {socket.sent = JSON.parse(message);}, close() {}};
    sockets.push(socket); return socket;
  }
});
try {
  sockets[0].onopen();
  assert.deepEqual(sockets[0].sent, {action:'subscribe',auctionId:'auction-A'});
  sockets[0].onmessage({data:JSON.stringify({type:'subscribed',auctionId:'auction-A'})});
  await tick();
  assert.equal(applied.at(-1).version, 0);
  response = {auction:{version:3,currentPrice:1500}};
  live.acceptEvent({eventId:'event-3',version:3});
  await tick();
  assert.equal(applied.at(-1).currentPrice,1500);
  assert.ok(statuses.includes('Version gap · syncing'));
  const previousReads=reads;
  live.acceptEvent({eventId:'event-3',version:3});
  live.acceptEvent({eventId:'old-event',version:1});
  await tick();
  assert.equal(reads,previousReads,'Duplicate and old events must not refresh or reduce state');
  response = {auction:{version:2,currentPrice:1200}};
  await live.refresh();
  assert.equal(applied.at(-1).version,3,'An old HTTP response must not roll back a newer snapshot');
  // An event arrives while the subscription-triggered HTTP request is still in flight.
  delayed=true;
  const reading=live.refresh();
  live.acceptEvent({eventId:'event-4',version:4});
  delayed=false;
  response={auction:{version:4,currentPrice:1700}};
  resolveRead({auction:{version:3,currentPrice:1500}});
  await reading;
  assert.equal(applied.at(-1).version,4,'In-flight events must force a second authoritative read');
  live.connect();
  sockets[1].onopen();
  assert.equal(sockets[1].sent.action,'subscribe','Every reconnection must resubscribe');
  response={auction:{version:7,currentPrice:2300}};
  sockets[1].onmessage({data:JSON.stringify({type:'subscribed',auctionId:'auction-A'})});
  await tick();
  assert.equal(applied.at(-1).version,7,'Reconnection must recover the latest state');
  console.log('PASS: subscribe/read race, gaps, duplicate/old events, stale HTTP and reconnect recovery');
} finally {live.close();}
