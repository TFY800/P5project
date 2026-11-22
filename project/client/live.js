// Events are hints. Only an HTTP snapshot may change authoritative auction state.
export class LiveAuction {
  constructor({id, url, snapshot, apply, status, socketFactory = url => new WebSocket(url)}) {
    Object.assign(this, {id, url, snapshot, apply, status, socketFactory});
    this.version = -1;
    this.eventIds = new Set();
    this.generation = 0;
    this.closed = false;
    this.retry = 500;
    this.poll = setInterval(() => this.refresh().then(() => { if (this.subscribed && !this.closed) this.status('Live'); }).catch(() => this.status('Sync failed · retrying')), 10000);
    this.connect();
  }
  connect() {
    if (this.closed) return;
    const generation = ++this.generation;
    this.subscribed = false;
    this.status('Connecting…');
    this.socket = this.socketFactory(this.url);
    this.socket.onopen = () => {
      if (this.closed || generation !== this.generation) return;
      this.socket.send(JSON.stringify({action: 'subscribe', auctionId: this.id}));
    };
    this.socket.onmessage = event => {
      if (generation !== this.generation || this.closed) return;
      try {
        const data = JSON.parse(event.data);
        if (data.auctionId !== this.id) return;
        if (data.type === 'subscribed') {
          this.retry = 500; this.subscribed = true;
          // Subscribe acknowledgement precedes this read. In-flight hints trigger another read.
          this.status('Live · syncing');
          this.refresh().then(() => { if (!this.closed && generation === this.generation) this.status('Live'); }).catch(() => this.status('Sync failed · retrying'));
        } else if (['price_updated', 'auction_ended'].includes(data.type)) this.acceptEvent(data);
      } catch { this.status('Invalid update · resyncing'); this.refresh().catch(() => {}); }
    };
    this.socket.onerror = () => { if (!this.closed && generation === this.generation) this.status('Connection interrupted'); };
    this.socket.onclose = () => {
      if (this.closed || generation !== this.generation) return;
      this.subscribed = false;
      this.status('Offline · reconnecting');
      this.refresh().catch(() => {});
      this.reconnect = setTimeout(() => this.connect(), this.retry + Math.random() * 200);
      this.retry = Math.min(15000, this.retry * 2);
    };
  }
  acceptEvent(event) {
    if (!Number.isSafeInteger(event.version) || event.version <= this.version || this.eventIds.has(event.eventId)) return;
    this.eventIds.add(event.eventId);
    if (this.eventIds.size > 500) this.eventIds.delete(this.eventIds.values().next().value);
    if (event.version > this.version + 1) this.status('Version gap · syncing');
    this.refresh().then(() => { if (this.subscribed && !this.closed) this.status('Live'); }).catch(() => this.status('Sync failed · retrying'));
  }
  async refresh() {
    if (this.closed) return;
    if (this.reading) { this.dirty = true; return this.reading; }
    this.reading = (async () => {
      do {
        this.dirty = false;
        const result = await this.snapshot();
        if (!this.closed && result.auction.version >= this.version) {
          this.version = result.auction.version;
          this.apply(result);
        }
      } while (this.dirty && !this.closed);
    })();
    try { await this.reading; } finally { this.reading = null; }
  }
  close() {
    this.closed = true;
    clearInterval(this.poll); clearTimeout(this.reconnect);
    this.socket?.close();
  }
}
