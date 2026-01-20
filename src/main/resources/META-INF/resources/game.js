const { createApp } = Vue;

createApp({
    data() {
        return {
            ws: null,
            connected: false,
            gameState: {
                puckX: 400,
                puckY: 300,
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
                speedMultiplier: 1.0
            },
            speed: 1.0,
            lineWidth: 5,
            isDrawing: false,
            lastFrameTime: 0,
            fps: 0,
            fpsUpdateTime: 0,
            frameCount: 0,
            activeSessions: 0,
            needsRender: true,
            lastRenderedState: null,
            renderInterval: 33,
            lastRenderTime: 0
        };
    },
    mounted() {
        this.connectWebSocket();
        this.setupCanvas();
        this.startRenderLoop();
        this.fetchStatistics();
    },
    methods: {
        connectWebSocket() {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${window.location.host}/api/v1/game/ws`;

            console.log('Connecting to WebSocket:', wsUrl);
            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                console.log('WebSocket connected to API v1');
                this.connected = true;
            };

            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (data.type === 'ERROR') {
                        console.error('Server error:', data.message);
                    } else {
                        // Only update if state actually changed
                        if (JSON.stringify(data) !== JSON.stringify(this.gameState)) {
                            this.gameState = data;
                            this.needsRender = true; // Mark for render
                        }
                    }
                } catch (e) {
                    console.error('Failed to parse message:', e);
                }
            };

            this.ws.onclose = () => {
                console.log('WebSocket disconnected');
                this.connected = false;
                setTimeout(() => this.connectWebSocket(), 3000);
            };

            this.ws.onerror = (error) => {
                console.error('WebSocket error:', error);
            };
        },

        sendCommand(type, data = {}) {
            if (this.ws && this.connected) {
                this.ws.send(JSON.stringify({ type, data }));
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

            // Fetch again in 5 seconds
            setTimeout(() => this.fetchStatistics(), 5000);
        },

        async togglePause() {
            this.sendCommand('TOGGLE_PAUSE');
        },

        async resetGame() {
            this.sendCommand('RESET');
        },

        async clearLines() {
            this.sendCommand('CLEAR_LINES');
        },

        async setSpeed() {
            this.sendCommand('SET_SPEED', { speed: this.speed });
        },

        setupCanvas() {
            const canvas = this.$refs.canvas;
            canvas.width = 800;
            canvas.height = 600;
        },

        startRenderLoop() {
            const render = (timestamp) => {
                // Calculate FPS
                this.frameCount++;
                if (timestamp - this.fpsUpdateTime >= 1000) {
                    this.fps = this.frameCount;
                    this.frameCount = 0;
                    this.fpsUpdateTime = timestamp;
                }

                // Throttle rendering to 30 FPS
                if (timestamp - this.lastRenderTime > this.renderInterval && this.needsRender) {
                    this.renderGame();
                    this.lastRenderTime = timestamp;
                    this.needsRender = false;
                }

                requestAnimationFrame(render);
            };

            requestAnimationFrame(render);
        },

        renderGame() {
            const canvas = this.$refs.canvas;
            const ctx = canvas.getContext('2d');

            // Only redraw if state changed
            const currentState = JSON.stringify({
                puck: { x: this.gameState.puckX, y: this.gameState.puckY },
                paddles: { p1: this.gameState.paddle1Y, p2: this.gameState.paddle2Y },
                lines: this.gameState.lines,
                powerUps: this.gameState.powerUps,
                activePowerUps: this.gameState.activePowerUps,
                cells: this.gameState.lifeGridCells
            });

            if (currentState === this.lastRenderedState && !this.isDrawing) {
                return; // Skip render if nothing changed
            }

            this.lastRenderedState = currentState;

            // Clear canvas
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.fillStyle = '#1a1a2e';
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            // Draw center line (less frequently updated)
            if (!this.centerLineBuffer) {
                this.centerLineBuffer = this.createCenterLineBuffer();
            }

            // Draw Game of Life cells
            this.renderLifeGrid(ctx);

            // Draw user-drawn lines
            this.renderLines(ctx);

            // Draw paddles
            this.renderPaddles(ctx);

            // Draw puck
            this.renderPuck(ctx);

            // Draw additional pucks
            this.renderAdditionalPucks(ctx);

            // Draw power-ups
            this.renderPowerUps(ctx);

            // Draw center line from buffer
            ctx.drawImage(this.centerLineBuffer, canvas.width/2, 0);
        },

        createCenterLineBuffer() {
            const canvas = document.createElement('canvas');
            canvas.width = 2;
            canvas.height = 600;
            const ctx = canvas.getContext('2d');

            ctx.strokeStyle = '#333';
            ctx.lineWidth = 2;
            ctx.setLineDash([10, 10]);
            ctx.beginPath();
            ctx.moveTo(0, 0);
            ctx.lineTo(0, 600);
            ctx.stroke();

            return canvas;
        },

        renderLifeGrid(ctx) {
            if (this.gameState.lifeGridCells.length === 0) return;

            // Batch all cell draws
            ctx.fillStyle = '#00ff88';
            ctx.globalAlpha = 0.5;

            const cellSize = this.gameState.lifeGridCells[0]?.size || 15;
            ctx.beginPath();
            this.gameState.lifeGridCells.forEach(cell => {
                ctx.rect(Math.round(cell.x), Math.round(cell.y), cellSize, cellSize);
            });
            ctx.fill();

            ctx.globalAlpha = 1.0;
        },

        renderLines(ctx) {
            if (this.gameState.lines.length === 0) return;

            ctx.strokeStyle = '#aaaaaa';
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';

            // Batch similar lines together
            this.gameState.lines.forEach(line => {
                if (!line.points || line.points.length < 2) return;

                const visiblePoints = line.isAnimating
                    ? Math.max(2, Math.floor(line.points.length * line.animationProgress))
                    : line.points.length;

                if (visiblePoints < 2) return;

                ctx.lineWidth = line.width;
                ctx.beginPath();
                ctx.moveTo(line.points[0].x, line.points[0].y);

                // Use integer coordinates for faster rendering
                for (let i = 1; i < visiblePoints; i++) {
                    const point = line.points[i];
                    ctx.lineTo(Math.round(point.x), Math.round(point.y));
                }

                ctx.stroke();
            });
        },

        renderPaddles(ctx) {
            const paddleWidth = 20;

            // AI paddle (left)
            ctx.fillStyle = '#ff6b6b';
            ctx.fillRect(0, Math.round(this.gameState.paddle1Y), paddleWidth, Math.round(this.gameState.paddleHeight));

            // Player paddle (right)
            ctx.fillStyle = '#4ecdc4';
            ctx.fillRect(
                this.gameState.canvasWidth - paddleWidth,
                Math.round(this.gameState.paddle2Y),
                paddleWidth,
                Math.round(this.gameState.paddleHeight)
            );
        },

        renderPuck(ctx) {
            // Draw glow first (only when moving fast)
            const speed = Math.sqrt(
                Math.pow(this.gameState.puckVX || 0, 2) +
                Math.pow(this.gameState.puckVY || 0, 2)
            );

            if (speed > 2) {
                ctx.shadowBlur = Math.min(20, speed * 5);
                ctx.shadowColor = '#ffffff';
            } else {
                ctx.shadowBlur = 0;
            }

            // Draw puck
            ctx.fillStyle = '#ffffff';
            ctx.beginPath();
            ctx.arc(
                Math.round(this.gameState.puckX),
                Math.round(this.gameState.puckY),
                10, 0, Math.PI * 2
            );
            ctx.fill();

            ctx.shadowBlur = 0;
        },

        renderAdditionalPucks(ctx) {
            if (this.gameState.additionalPucks.length === 0) return;

            ctx.fillStyle = '#ff6b6b';
            this.gameState.additionalPucks.forEach(puck => {
                ctx.beginPath();
                ctx.arc(Math.round(puck.x), Math.round(puck.y), 10, 0, Math.PI * 2);
                ctx.fill();
            });
        },

        renderPowerUps(ctx) {
            if (this.gameState.powerUps.length === 0) return;

            const now = Date.now();
            this.gameState.powerUps.forEach(powerup => {
                const x = Math.round(powerup.x);
                const y = Math.round(powerup.y);
                const radius = powerup.radius || 15;

                // Draw outer circle with color
                ctx.fillStyle = powerup.color;
                ctx.globalAlpha = 0.7;
                ctx.beginPath();
                ctx.arc(x, y, radius, 0, Math.PI * 2);
                ctx.fill();

                // Draw pulsing ring (cache animation)
                const pulseSize = radius + Math.sin(now / 200) * 3;
                ctx.strokeStyle = powerup.color;
                ctx.lineWidth = 2;
                ctx.globalAlpha = 0.5;
                ctx.beginPath();
                ctx.arc(x, y, pulseSize, 0, Math.PI * 2);
                ctx.stroke();

                ctx.globalAlpha = 1.0;

                // Draw emoji
                ctx.font = '24px Arial';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillText(powerup.emoji, x, y);
            });

            ctx.globalAlpha = 1.0;
        },

        handleMouseMove(event) {
            const canvas = this.$refs.canvas;
            const rect = canvas.getBoundingClientRect();
            const scaleX = canvas.width / rect.width;
            const scaleY = canvas.height / rect.height;
            const x = (event.clientX - rect.left) * scaleX;
            const y = (event.clientY - rect.top) * scaleY;

            if (Date.now() - this.lastPaddleUpdate > 16) {
                this.sendCommand('MOVE_PADDLE', { y: y - this.gameState.paddleHeight / 2 });
                this.lastPaddleUpdate = Date.now();
            }

            if (this.isDrawing) {
                this.sendCommand('UPDATE_LINE', { x, y });
                this.needsRender = true;
            }
        },

        handleMouseDown(event) {
            if (event.button === 2) { // Right click
                const canvas = this.$refs.canvas;
                const rect = canvas.getBoundingClientRect();
                const scaleX = canvas.width / rect.width;
                const scaleY = canvas.height / rect.height;
                const x = (event.clientX - rect.left) * scaleX;
                const y = (event.clientY - rect.top) * scaleY;

                this.isDrawing = true;
                this.sendCommand('START_LINE', { x, y });
            }
        },

        handleMouseUp(event) {
            if (event.button === 2 && this.isDrawing) {
                this.isDrawing = false;
                this.sendCommand('FINISH_LINE');
            }
        }
    }
}).mount('#app');