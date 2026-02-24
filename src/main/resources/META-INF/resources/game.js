const { createApp } = Vue;

    createApp({
      data() {
        return {
          ws: null,
          connected: false,
          gameState: {
            puckX: 400,
            puckY: 300,
            puckVX: 40,
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
            speedMultiplier: 5.0
          },
          predictedPuckX: 400,
          predictedPuckY: 300,
          speed: 1.0,
          lineWidth: 5,
          isDrawing: false,
          lastFrameTime: 0,
          fps: 0,
          fpsUpdateTime: 0,
          frameCount: 0,
          activeSessions: 0,
          renderId: null,
          lastRenderTime: 0,
          lastPaddleUpdate: 0,
          lastUpdateTime: 0,
          interpolationAlpha: 1.0,
          canvasScale: 1,
          devicePixelRatio: window.devicePixelRatio || 1,
          lastPuckX: 400,
          lastPuckY: 300,
          smoothPuckX: 400,
          smoothPuckY: 300
        };
      },

      mounted() {
        this.setupCanvas();
        this.connectWebSocket();
        this.startRenderLoop();
        this.fetchStatistics();
        this.setupEventListeners();
        window.addEventListener('resize', this.handleResize.bind(this));
        this.handleResize();
      },

      beforeUnmount() {
        if (this.ws) {
          this.ws.close();
        }
        if (this.renderId) {
          cancelAnimationFrame(this.renderId);
        }
        window.removeEventListener('resize', this.handleResize.bind(this));
      },

      methods: {
        setupCanvas() {
          const canvas = this.$refs.canvas;
          canvas.style.width = '800px';
          canvas.style.height = '600px';
          canvas.width = 800 * this.devicePixelRatio;
          canvas.height = 600 * this.devicePixelRatio;
          const ctx = canvas.getContext('2d');
          ctx.scale(this.devicePixelRatio, this.devicePixelRatio);
          canvas.style.imageRendering = 'crisp-edges';
          canvas.style.willChange = 'transform';
        },

        handleResize() {
          const canvas = this.$refs.canvas;
          const container = canvas.parentElement;
          const containerWidth = container.clientWidth;
          const containerHeight = container.clientHeight;
          const scaleX = containerWidth / 800;
          const scaleY = containerHeight / 600;
          this.canvasScale = Math.min(scaleX, scaleY);
          canvas.style.transform = `scale(${this.canvasScale})`;
          canvas.style.transformOrigin = 'top left';
        },

        connectWebSocket() {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${window.location.host}/api/v1/game/ws`;
            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                this.connected = true;
                this.sendCommand('SET_SPEED', { speed: this.speed });
            };

            this.ws.onmessage = (event) => {
                const startTime = performance.now();

                try {
                    const data = JSON.parse(event.data);

                    if (data.type === 'ERROR') {
                        console.error('Server error:', data.message);
                    } else {
                        this.lastPuckX = this.gameState.puckX;
                        this.lastPuckY = this.gameState.puckY;

                        Object.assign(this.gameState, {
                            puckX: data.puckX ?? this.gameState.puckX,
                            puckY: data.puckY ?? this.gameState.puckY,
                            puckVX: data.puckVX ?? 0,
                            puckVY: data.puckVY ?? 0,
                            paddle1Y: data.paddle1Y ?? 250,
                            paddle2Y: data.paddle2Y ?? 250,
                            paddleHeight: data.paddleHeight ?? 100,
                            canvasWidth: data.canvasWidth ?? 800,
                            canvasHeight: data.canvasHeight ?? 600,
                            lines: data.lines ?? [],
                            powerUps: data.powerUps ?? [],
                            activePowerUps: data.activePowerUps ?? [],
                            additionalPucks: data.additionalPucks ?? [],
                            lifeGridCells: data.lifeGridCells ?? [],
                            paused: data.paused ?? false,
                            speedMultiplier: data.speedMultiplier ?? 1.0,
                        });

                        this.smoothPuckX = this.gameState.puckX;
                        this.smoothPuckY = this.gameState.puckY;
                        this.speed = data.speedMultiplier || this.speed;
                    }
                } catch (e) {
                    console.error('Failed to parse message:', e);
                }

                const processingTime = performance.now() - startTime;
                if (processingTime > 5) {
                    console.warn(`Slow message processing: ${processingTime.toFixed(2)}ms`);
                }
            };

            this.ws.onclose = () => {
                this.connected = false;
                setTimeout(() => this.connectWebSocket(), 2000);
            };

            this.ws.onerror = (error) => {
                console.error('WebSocket error:', error);
            };
        },

        sendCommand(type, data = {}) {
          if (this.ws && this.connected && this.ws.readyState === WebSocket.OPEN) {
            try {
              this.ws.send(JSON.stringify({ type, data }));
            } catch (e) {
              console.error('Failed to send command:', e);
            }
          }
        },

        async fetchStatistics() {
          try {
            const response = await fetch('/api/v1/game/statistics');
            const stats = await response.json();
            this.activeSessions = stats.activeSessions || 0;
          } catch (e) {
            console.error('Failed to fetch statistics:', e);
          }
          setTimeout(() => this.fetchStatistics(), 5000);
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

              this.smoothPuckX = Math.max(10, Math.min(this.gameState.canvasWidth - 10, this.smoothPuckX));
              this.smoothPuckY = Math.max(10, Math.min(this.gameState.canvasHeight - 10, this.smoothPuckY));
            }

            this.lastRenderTime = timestamp;

            this.renderGame();

            this.renderId = requestAnimationFrame(render);
          };

          this.renderId = requestAnimationFrame(render);
        },

        renderGame() {
          const canvas = this.$refs.canvas;
          const ctx = canvas.getContext('2d');

          ctx.fillStyle = '#1a1a2e';
          ctx.fillRect(0, 0, 800, 600);

          ctx.strokeStyle = '#ffffff';
          ctx.lineWidth = 3;
          ctx.strokeRect(0, 0, 800, 600);

          this.renderLifeGrid(ctx);
          this.renderLines(ctx);
          this.renderCenterLine(ctx);
          this.renderPaddles(ctx);
          this.renderPowerUps(ctx);
          this.renderAdditionalPucks(ctx);
          this.renderPuck(ctx);
          this.renderStats(ctx);
        },

        renderStats(ctx) {
          ctx.fillStyle = '#00ff00';
          ctx.font = '12px Arial';
          ctx.textAlign = 'left';
          ctx.textBaseline = 'top';
          ctx.fillText(`FPS: ${this.fps}`, 10, 10);
          ctx.fillText(`Puck: (${Math.round(this.gameState.puckX)}, ${Math.round(this.gameState.puckY)})`, 10, 25);
          ctx.fillText(`Velocity: (${this.gameState.puckVX.toFixed(1)}, ${this.gameState.puckVY.toFixed(1)})`, 10, 40);
          ctx.fillText(`Speed: ${this.speed.toFixed(1)}x`, 10, 55);
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

          for (let i = 0; i < cells.length; i++) {
            const cell = cells[i];
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

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
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

          // Thresholds scaled to new velocity range (puck starts at ~250-300 px/s)
          if (speed > 150) {
            ctx.globalAlpha = 0.25;
            ctx.fillStyle = '#ff5555';
            for (let i = 1; i <= 3; i++) {
              const trailX = x - (vx / speed) * i * 8;
              const trailY = y - (vy / speed) * i * 8;
              const radius = Math.max(2, 10 - i * 2);
              ctx.beginPath();
              ctx.arc(trailX, trailY, radius, 0, Math.PI * 2);
              ctx.fill();
            }
            ctx.globalAlpha = 1.0;
          }

          if (speed > 400) {
            ctx.shadowBlur = 20;
            ctx.shadowColor = 'rgba(255, 0, 0, 0.8)';
          } else if (speed > 200) {
            ctx.shadowBlur = 10;
            ctx.shadowColor = 'rgba(255, 255, 0, 0.6)';
          } else {
            ctx.shadowBlur = 0;
          }

          ctx.fillStyle = '#ffffff';
          ctx.beginPath();
          ctx.arc(x, y, 10, 0, Math.PI * 2);
          ctx.fill();

          ctx.shadowBlur = 0;
          ctx.globalAlpha = 1.0;
        },

        renderAdditionalPucks(ctx) {
          const pucks = this.gameState.additionalPucks;
          if (!pucks || pucks.length === 0) return;

          ctx.fillStyle = '#ff6b6b';
          for (let i = 0; i < pucks.length; i++) {
            const puck = pucks[i];
            ctx.beginPath();
            ctx.arc(puck.x, puck.y, 10, 0, Math.PI * 2);
            ctx.fill();
          }
        },

        renderPowerUps(ctx) {
          const powerups = this.gameState.powerUps;
          if (!powerups || powerups.length === 0) return;

          for (let i = 0; i < powerups.length; i++) {
            const powerup = powerups[i];
            const x = powerup.x;
            const y = powerup.y;
            const radius = powerup.radius || 15;

            ctx.fillStyle = powerup.color || '#ffff00';
            ctx.globalAlpha = 0.8;
            ctx.beginPath();
            ctx.arc(x, y, radius, 0, Math.PI * 2);
            ctx.fill();

            ctx.globalAlpha = 1.0;
            ctx.font = 'bold 20px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(powerup.emoji || '?', x, y);
          }

          ctx.globalAlpha = 1.0;
        },

        setupEventListeners() {
          const canvas = this.$refs.canvas;
          canvas.addEventListener('mousemove', this.handleMouseMove.bind(this));
          canvas.addEventListener('mousedown', this.handleMouseDown.bind(this));
          canvas.addEventListener('mouseup', this.handleMouseUp.bind(this));
          canvas.addEventListener('contextmenu', (e) => e.preventDefault());
          canvas.addEventListener('touchmove', this.handleTouchMove.bind(this));
          canvas.addEventListener('touchstart', this.handleTouchStart.bind(this));
          canvas.addEventListener('touchend', this.handleTouchEnd.bind(this));
        },

        handleMouseMove(event) {
          this.handlePointerMove(event.clientX, event.clientY);
        },

        handleTouchMove(event) {
          event.preventDefault();
          if (event.touches.length > 0) {
            const touch = event.touches[0];
            this.handlePointerMove(touch.clientX, touch.clientY);
          }
        },

        handlePointerMove(clientX, clientY) {
          const canvas = this.$refs.canvas;
          const rect = canvas.getBoundingClientRect();
          const x = clientX - rect.left;
          const y = clientY - rect.top;
          const scaledX = (x / rect.width) * this.gameState.canvasWidth;
          const scaledY = (y / rect.height) * this.gameState.canvasHeight;

          const now = Date.now();
          if (now - this.lastPaddleUpdate > 20) {
            this.sendCommand('MOVE_PADDLE', {
              y: scaledY - this.gameState.paddleHeight / 2
            });
            this.lastPaddleUpdate = now;
          }

          if (this.isDrawing) {
            this.sendCommand('UPDATE_LINE', { x: scaledX, y: scaledY });
          }
        },

        handleMouseDown(event) {
          if (event.button === 2) {
            this.startDrawing(event.clientX, event.clientY);
          }
        },

        handleTouchStart(event) {
          event.preventDefault();
          if (event.touches.length > 0) {
            const touch = event.touches[0];
            this.startDrawing(touch.clientX, touch.clientY);
          }
        },

        startDrawing(clientX, clientY) {
          const canvas = this.$refs.canvas;
          const rect = canvas.getBoundingClientRect();
          const x = clientX - rect.left;
          const y = clientY - rect.top;
          const scaledX = (x / rect.width) * this.gameState.canvasWidth;
          const scaledY = (y / rect.height) * this.gameState.canvasHeight;
          this.isDrawing = true;
          this.sendCommand('START_LINE', { x: scaledX, y: scaledY });
        },

        handleMouseUp(event) {
          if (event.button === 2 && this.isDrawing) {
            this.finishDrawing();
          }
        },

        handleTouchEnd(event) {
          if (this.isDrawing) {
            this.finishDrawing();
          }
        },

        finishDrawing() {
          this.isDrawing = false;
          this.sendCommand('FINISH_LINE');
        },

        togglePause() {
          this.sendCommand('TOGGLE_PAUSE');
        },

        resetGame() {
          this.sendCommand('RESET');
        },

        clearLines() {
          this.sendCommand('CLEAR_LINES');
        },

        setSpeed() {
          this.sendCommand('SET_SPEED', { speed: this.speed });
        },

        spawnPowerUp(type) {
          this.sendCommand('SPAWN_POWERUP', { type });
        }
      }
    }).mount('#app');
