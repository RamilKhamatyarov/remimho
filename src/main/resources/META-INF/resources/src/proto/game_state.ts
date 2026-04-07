/**
 * src/proto/game_state.ts
 *
 * Hand-written Protobuf binary decoder for GameStateDelta.
 * Replaces the pbjs stub — no protobufjs npm package required.
 *
 * Proto schema reference (game_state.proto):
 *   message GameStateDelta {
 *     optional double puck_x    = 1;   wire type 1 (64-bit LE double)
 *     optional double puck_y    = 2;
 *     optional double puck_vx   = 3;
 *     optional double puck_vy   = 4;
 *     optional double paddle1_y = 5;
 *     optional double paddle2_y = 6;
 *     optional int32  score_a   = 7;   wire type 0 (varint)
 *     optional int32  score_b   = 8;
 *     optional bool   paused    = 9;
 *     repeated Line            lines             = 10;  wire type 2 (length-delimited)
 *     repeated PowerUp         power_ups         = 11;
 *     repeated ActivePowerUp   active_power_ups  = 12;
 *   }
 */

// ── Binary reader ─────────────────────────────────────────────────────────────

class ProtoReader {
  private pos = 0;
  private view: DataView;

  constructor(private bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }

  get done(): boolean { return this.pos >= this.bytes.length; }

  varint(): number {
    let r = 0, shift = 0, b: number;
    do { b = this.bytes[this.pos++]; r |= (b & 0x7f) << shift; shift += 7; } while (b & 0x80);
    return r;
  }

  f64(): number { const v = this.view.getFloat64(this.pos, true); this.pos += 8; return v; }

  bytes_field(): Uint8Array {
    const len = this.varint();
    return this.bytes.slice(this.pos, (this.pos += len));
  }

  str(): string { return new TextDecoder().decode(this.bytes_field()); }

  skip(wire: number): void {
    switch (wire) {
      case 0: this.varint();                break;
      case 1: this.pos += 8;               break;
      case 2: this.pos += this.varint();   break;
      case 5: this.pos += 4;               break;
    }
  }
}

// ── Sub-message decoders ──────────────────────────────────────────────────────

function readPoint(buf: Uint8Array): { x: number; y: number } {
  const r = new ProtoReader(buf);
  let x = 0, y = 0;
  while (!r.done) {
    const tag = r.varint(), f = tag >>> 3, w = tag & 7;
    if (f === 1 && w === 1) x = r.f64();
    else if (f === 2 && w === 1) y = r.f64();
    else r.skip(w);
  }
  return { x, y };
}

function readLine(buf: Uint8Array) {
  const r = new ProtoReader(buf);
  const points: { x: number; y: number }[] = [];
  let width = 3, animationProgress = 0, isAnimating = false;
  while (!r.done) {
    const tag = r.varint(), f = tag >>> 3, w = tag & 7;
    if      (f === 1 && w === 2) points.push(readPoint(r.bytes_field()));
    else if (f === 2 && w === 1) width = r.f64();
    else if (f === 3 && w === 1) animationProgress = r.f64();
    else if (f === 4 && w === 0) isAnimating = r.varint() !== 0;
    else r.skip(w);
  }
  // Map proto points to the Line shape GameCanvas expects
  return { controlPoints: points, flattenedPoints: points.length > 1 ? points : null, width, animationProgress, isAnimating };
}

function readPowerUp(buf: Uint8Array) {
  const r = new ProtoReader(buf);
  let x = 0, y = 0, type = '', emoji = '', color = '#fff', radius = 15;
  while (!r.done) {
    const tag = r.varint(), f = tag >>> 3, w = tag & 7;
    if      (f === 1 && w === 1) x      = r.f64();
    else if (f === 2 && w === 1) y      = r.f64();
    else if (f === 3 && w === 2) type   = r.str();
    else if (f === 4 && w === 2) emoji  = r.str();
    else if (f === 5 && w === 2) color  = r.str();
    else if (f === 6 && w === 1) radius = r.f64();
    else r.skip(w);
  }
  return { x, y, type, emoji, color, radius };
}

function readActivePowerUp(buf: Uint8Array) {
  const r = new ProtoReader(buf);
  let type = '', emoji = '', remainingSeconds = 0;
  while (!r.done) {
    const tag = r.varint(), f = tag >>> 3, w = tag & 7;
    if      (f === 1 && w === 2) type             = r.str();
    else if (f === 2 && w === 2) emoji            = r.str();
    else if (f === 3 && w === 0) remainingSeconds = r.varint();
    else r.skip(w);
  }
  return { type, emoji, remainingMs: remainingSeconds * 1000 };
}

// ── Top-level decoder ─────────────────────────────────────────────────────────

function decode(buf: Uint8Array): Record<string, unknown> {
  const r = new ProtoReader(buf);
  const d: Record<string, unknown> = {};

  while (!r.done) {
    const tag = r.varint(), f = tag >>> 3, w = tag & 7;
    switch (f) {
      case 1:  d['puckX']    = r.f64();           break;
      case 2:  d['puckY']    = r.f64();           break;
      case 3:  d['puckVx']   = r.f64();           break;
      case 4:  d['puckVy']   = r.f64();           break;
      case 5:  d['paddle1Y'] = r.f64();           break;
      case 6:  d['paddle2Y'] = r.f64();           break;
      case 7:  d['scoreA']   = r.varint();        break;
      case 8:  d['scoreB']   = r.varint();        break;
      case 9:  d['paused']   = r.varint() !== 0; break;
      case 10: ((d['lines'] as unknown[]) ??= []).push(readLine(r.bytes_field()));           break;
      case 11: ((d['powerUps'] as unknown[]) ??= []).push(readPowerUp(r.bytes_field()));     break;
      case 12: ((d['activePowerUps'] as unknown[]) ??= []).push(readActivePowerUp(r.bytes_field())); break;
      default: r.skip(w);
    }
  }
  return d;
}

// ── Export ────────────────────────────────────────────────────────────────────

export const GameStateDelta = { decode };
