const { createApp } = Vue;

createApp({
  data() {
    return {
      ws: null,
      connected: false,
      gameState: {
        puckX: 400,
        puckY: 300,
        puckVX: 0,
        puckVY: 0,
        paddle1Y: 250,
        paddle2Y: 250,
        paddleHeight: 100,
        canvasWidth: 800,
        canvasHeight: 600,
        lines: [],
        powerUps: [],
        activePowerUps: [],
        additionalPucks: [],
        lifeGridCells: [],
        paused: false,
        speedMultiplier: 1.0,
        scoreA: 0,
        scoreB: 0,
      },
      smoothPuckX: 400,
      smoothPuckY: 300,
      speed: 1.0,
      lineWidth: 5,
      isDrawing: false,
      fps: 0,
      fpsUpdateTime: 0,
      frameCount: 0,
      activeSessions: 0,
      renderId: null,
      lastRenderTime: 0,
      lastPaddleUpdate: 0,
      ctx: null,
    };
  },

  mounted() {
    this.setupCanvas();
    this.connectWebSocket();
    this.startRenderLoop();
    this.fetchStatistics();
    this.setupEventListeners();
    window.addEventListener('resize', this.handleResize.bind(this));
  },

  beforeUnmount() {
    if (this.ws) this.ws.close();
    if (this.renderId) cancelAnimationFrame(this.renderId);
    window.removeEventListener('resize', this.handleResize.bind(this));
  },

  methods: {

    setupCanvas() {
      const canvas = this.$refs.canvas;
      const dpr = window.devicePixelRatio || 1;

      canvas.width  = 800 * dpr;
      canvas.height = 600 * dpr;

      canvas.style.width  = '800px';
      canvas.style.height = '600px';

      const ctx = canvas.getContext('2d');
      ctx.scale(dpr, dpr);
      this.ctx = ctx;
    },

    handleResize() {
      const canvas = this.$refs.canvas;
      const container = canvas.parentElement;
      const scaleX = container.clientWidth  / 800;
      const scaleY = container.clientHeight / 600;
      const s = Math.min(scaleX, scaleY, 1);
      canvas.style.transform       = `scale(${s})`;
      canvas.style.transformOrigin = 'top left';
    },


    connectWebSocket() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${protocol}//${window.location.host}/api/v1/game/ws`;
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        this.connected = true;
        console.log('[WS] connected');
        this.sendCommand('SET_SPEED', { speed: this.speed });
      };

      let msgCount = 0;

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'ERROR') {
            console.error('[WS] server error:', data.message);
            return;
          }

          Object.assign(this.gameState, {
            puckX:           data.puckX           ?? this.gameState.puckX,
            puckY:           data.puckY            ?? this.gameState.puckY,
            puckVX:          data.puckVX           ?? 0,
            puckVY:          data.puckVY           ?? 0,
            paddle1Y:        data.paddle1Y          ?? this.gameState.paddle1Y,
            paddle2Y:        data.paddle2Y          ?? this.gameState.paddle2Y,
            paddleHeight:    data.paddleHeight       ?? this.gameState.paddleHeight,
            canvasWidth:     data.canvasWidth        ?? this.gameState.canvasWidth,
            canvasHeight:    data.canvasHeight       ?? this.gameState.canvasHeight,
            lines:           data.lines             ?? [],
            powerUps:        data.powerUps           ?? [],
            activePowerUps:  data.activePowerUps     ?? [],
            additionalPucks: data.additionalPucks    ?? [],
            lifeGridCells:   data.lifeGridCells      ?? [],
            paused:          data.paused             ?? false,
            speedMultiplier: data.speedMultiplier    ?? 1.0,
            scoreA:          data.scoreA             ?? data.score?.playerA ?? this.gameState.scoreA,
            scoreB:          data.scoreB             ?? data.score?.playerB ?? this.gameState.scoreB,
          });

          this.speed = this.gameState.speedMultiplier;

          const driftX = Math.abs(this.smoothPuckX - this.gameState.puckX);
          const driftY = Math.abs(this.smoothPuckY - this.gameState.puckY);
          if (driftX > 30 || driftY > 30) {
            this.smoothPuckX = this.gameState.puckX;
            this.smoothPuckY = this.gameState.puckY;
          }

          console.log(`[WS] Message #${++msgCount} received:`, data);

        } catch (e) {
          console.error('[WS] parse error:', e);
        }
      };

      this.ws.onclose = () => {
        this.connected = false;
        console.log('[WS] closed — reconnecting in 1s');
        setTimeout(() => this.connectWebSocket(), 1000);
      };

      this.ws.onerror = (e) => console.error('[WS] error', e);
    },

    sendCommand(type, data = {}) {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type, data }));
      }
    },

    startRenderLoop() {
      const render = (timestamp) => {
        this.frameCount++;
        if (timestamp - this.fpsUpdateTime >= 1000) {
          this.fps = this.frameCount;
          this.frameCount = 0;
          this.fpsUpdateTime = timestamp;
        }

        if (!this.gameState.paused) {
          const deltaSec = this.lastRenderTime > 0
            ? Math.min((timestamp - this.lastRenderTime) / 1000, 0.05)
            : 0;
          const speed = this.gameState.speedMultiplier || 1.0;

          this.smoothPuckX += this.gameState.puckVX * speed * deltaSec;
          this.smoothPuckY += this.gameState.puckVY * speed * deltaSec;

          const r = 10;
          this.smoothPuckX = Math.max(r, Math.min(this.gameState.canvasWidth  - r, this.smoothPuckX));
          this.smoothPuckY = Math.max(r, Math.min(this.gameState.canvasHeight - r, this.smoothPuckY));

          this.smoothPuckX += (this.gameState.puckX - this.smoothPuckX) * 0.3;
          this.smoothPuckY += (this.gameState.puckY - this.smoothPuckY) * 0.3;
        } else {
          this.smoothPuckX = this.gameState.puckX;
          this.smoothPuckY = this.gameState.puckY;
        }

        this.lastRenderTime = timestamp;
        this.renderGame();
        this.renderId = requestAnimationFrame(render);
      };

      this.renderId = requestAnimationFrame(render);
    },

    renderGame() {
      const ctx = this.ctx;
      if (!ctx) return;

      const W = 800;
      const H = 600;

      ctx.fillStyle = '#1a1a2e';
      ctx.fillRect(0, 0, W, H);

      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 3;
      ctx.strokeRect(0, 0, W, H);

      this.renderLifeGrid(ctx);
      this.renderLines(ctx);
      this.renderCenterLine(ctx);
      this.renderPaddles(ctx);
      this.renderPowerUps(ctx);
      this.renderAdditionalPucks(ctx);
      this.renderPuck(ctx);
      this.renderScore(ctx);
      this.renderStats(ctx);

      if (this.gameState.paused) {
        ctx.fillStyle = 'rgba(0,0,0,0.55)';
        ctx.fillRect(0, 0, W, H);
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 48px monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('PAUSED', W / 2, H / 2);
        ctx.textBaseline = 'alphabetic';
      }

      ctx.globalAlpha = 1.0;
    },

    renderScore(ctx) {
      ctx.fillStyle = 'rgba(255,255,255,0.7)';
      ctx.font = 'bold 32px monospace';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'top';
      ctx.fillText(String(this.gameState.scoreA), 200, 20);
      ctx.fillText(String(this.gameState.scoreB), 600, 20);
      ctx.textBaseline = 'alphabetic';
    },

    renderStats(ctx) {
      ctx.fillStyle = '#00ff00';
      ctx.font = '12px Arial';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'top';
      ctx.fillText(`FPS: ${this.fps}`, 10, 10);
      ctx.fillText(`Puck: (${Math.round(this.smoothPuckX)}, ${Math.round(this.smoothPuckY)})`, 10, 25);
      ctx.fillText(`Vel: (${(this.gameState.puckVX || 0).toFixed(1)}, ${(this.gameState.puckVY || 0).toFixed(1)})`, 10, 40);
      ctx.fillText(`Speed: ${this.speed.toFixed(1)}x`, 10, 55);
      ctx.fillText(this.connected ? '● WS' : '○ WS', 10, 70);
      ctx.textBaseline = 'alphabetic';
    },

    renderCenterLine(ctx) {
      ctx.strokeStyle = '#333333';
      ctx.lineWidth = 1;
      ctx.setLineDash([10, 10]);
      ctx.beginPath();
      ctx.moveTo(400, 0);
      ctx.lineTo(400, 600);
      ctx.stroke();
      ctx.setLineDash([]);
    },

    renderLifeGrid(ctx) {
      const cells = this.gameState.lifeGridCells;
      if (!cells || cells.length === 0) return;
      ctx.fillStyle = '#00ff88';
      ctx.globalAlpha = 0.5;
      const cellSize = cells[0]?.size || 15;
      for (const cell of cells) {
        ctx.fillRect(cell.x, cell.y, cellSize, cellSize);
      }
      ctx.globalAlpha = 1.0;
    },

    renderLines(ctx) {
      const lines = this.gameState.lines;
      if (!lines || lines.length === 0) return;
      ctx.strokeStyle = '#aaaaaa';
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      for (const line of lines) {
        const points = line.points;
        if (!points || points.length < 2) continue;
        const visiblePoints = line.isAnimating
          ? Math.max(2, Math.floor(points.length * (line.animationProgress || 0)))
          : points.length;
        if (visiblePoints < 2) continue;
        ctx.lineWidth = line.width || 5;
        ctx.beginPath();
        ctx.moveTo(points[0].x, points[0].y);
        for (let j = 1; j < visiblePoints; j++) {
          ctx.lineTo(points[j].x, points[j].y);
        }
        ctx.stroke();
      }
    },

    renderPaddles(ctx) {
      const paddleWidth = 20;
      ctx.fillStyle = '#ff6b6b';
      ctx.fillRect(0, this.gameState.paddle1Y, paddleWidth, this.gameState.paddleHeight);
      ctx.fillStyle = '#4ecdc4';
      ctx.fillRect(800 - paddleWidth, this.gameState.paddle2Y, paddleWidth, this.gameState.paddleHeight);
    },

    renderPuck(ctx) {
      const x = this.smoothPuckX;
      const y = this.smoothPuckY;
      const vx = this.gameState.puckVX || 0;
      const vy = this.gameState.puckVY || 0;
      const speed = Math.sqrt(vx * vx + vy * vy);

      if (speed > 150) {
        ctx.globalAlpha = 0.25;
        ctx.fillStyle = '#ff5555';
        for (let i = 1; i <= 3; i++) {
          const trailX = x - (vx / speed) * i * 8;
          const trailY = y - (vy / speed) * i * 8;
          ctx.beginPath();
          ctx.arc(trailX, trailY, Math.max(2, 10 - i * 2), 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.globalAlpha = 1.0;
      }

      const blur = speed > 400 ? 20 : speed > 200 ? 10 : 0;
      if (blur > 0) {
        ctx.shadowBlur  = blur;
        ctx.shadowColor = speed > 400 ? 'rgba(255,0,0,0.8)' : 'rgba(255,255,0,0.6)';
      }

      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.arc(x, y, 10, 0, Math.PI * 2);
      ctx.fill();

      if (blur > 0) ctx.shadowBlur = 0;
    },

    renderAdditionalPucks(ctx) {
      const pucks = this.gameState.additionalPucks;
      if (!pucks || pucks.length === 0) return;
      ctx.fillStyle = '#ff6b6b';
      for (const puck of pucks) {
        ctx.beginPath();
        ctx.arc(puck.x, puck.y, 10, 0, Math.PI * 2);
        ctx.fill();
      }
    },

    renderPowerUps(ctx) {
      const powerups = this.gameState.powerUps;
      if (!powerups || powerups.length === 0) return;
      for (const pu of powerups) {
        ctx.fillStyle = pu.color || '#ffff00';
        ctx.globalAlpha = 0.8;
        ctx.beginPath();
        ctx.arc(pu.x, pu.y, pu.radius || 15, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha = 1.0;
        ctx.font = 'bold 20px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(pu.emoji || '?', pu.x, pu.y);
      }
      ctx.textBaseline = 'alphabetic';
      ctx.globalAlpha = 1.0;
    },


    setupEventListeners() {
      const canvas = this.$refs.canvas;
      canvas.addEventListener('mousemove',    this.handleMouseMove.bind(this));
      canvas.addEventListener('mousedown',    this.handleMouseDown.bind(this));
      canvas.addEventListener('mouseup',      this.handleMouseUp.bind(this));
      canvas.addEventListener('contextmenu',  (e) => e.preventDefault());
      canvas.addEventListener('touchmove',    this.handleTouchMove.bind(this), { passive: false });
      canvas.addEventListener('touchstart',   this.handleTouchStart.bind(this), { passive: false });
      canvas.addEventListener('touchend',     this.handleTouchEnd.bind(this));
    },

    handleMouseMove(e)  { this.handlePointerMove(e.clientX, e.clientY); },
    handleTouchMove(e)  {
      e.preventDefault();
      if (e.touches.length > 0) this.handlePointerMove(e.touches[0].clientX, e.touches[0].clientY);
    },

    handlePointerMove(clientX, clientY) {
      const canvas = this.$refs.canvas;
      const rect   = canvas.getBoundingClientRect();
      const scaledY = ((clientY - rect.top)  / rect.height) * this.gameState.canvasHeight;
      const scaledX = ((clientX - rect.left) / rect.width)  * this.gameState.canvasWidth;

      const now = Date.now();
      if (now - this.lastPaddleUpdate > 16) {
        this.sendCommand('MOVE_PADDLE', { y: scaledY - this.gameState.paddleHeight / 2 });
        this.lastPaddleUpdate = now;
      }

      if (this.isDrawing) {
        this.sendCommand('UPDATE_LINE', { x: scaledX, y: scaledY });
      }
    },

    handleMouseDown(e) {
      if (e.button === 2) this.startDrawing(e.clientX, e.clientY);
    },

    handleTouchStart(e) {
      e.preventDefault();
      if (e.touches.length > 0) this.startDrawing(e.touches[0].clientX, e.touches[0].clientY);
    },

    startDrawing(clientX, clientY) {
      const canvas = this.$refs.canvas;
      const rect   = canvas.getBoundingClientRect();
      const scaledX = ((clientX - rect.left) / rect.width)  * this.gameState.canvasWidth;
      const scaledY = ((clientY - rect.top)  / rect.height) * this.gameState.canvasHeight;
      this.isDrawing = true;
      this.sendCommand('START_LINE', { x: scaledX, y: scaledY });
    },

    handleMouseUp(e) { if (e.button === 2 && this.isDrawing) this.finishDrawing(); },
    handleTouchEnd()  { if (this.isDrawing) this.finishDrawing(); },
    finishDrawing()   { this.isDrawing = false; this.sendCommand('FINISH_LINE'); },

    togglePause()    { this.sendCommand('TOGGLE_PAUSE'); },
    resetGame()      { this.sendCommand('RESET'); },
    clearLines()     { this.sendCommand('CLEAR_LINES'); },
    setSpeed()       { this.sendCommand('SET_SPEED', { speed: this.speed }); },
    spawnPowerUp(t)  { this.sendCommand('SPAWN_POWERUP', { type: t }); },

    async fetchStatistics() {
      try {
        const r = await fetch('/api/v1/game/statistics');
        const s = await r.json();
        this.activeSessions = s.activeSessions || 0;
      } catch (e) {
        console.error('Failed to fetch stats:', e);
      }
      setTimeout(() => this.fetchStatistics(), 5000);
    },
  },
}).mount('#app');

