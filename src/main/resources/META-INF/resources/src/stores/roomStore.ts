import { computed, ref } from 'vue';

const currentRoomId = ref(roomFromPath());

export function useRoomStore() {
  const roomId = computed(() => currentRoomId.value);

  function joinRoom(nextRoomId: string): void {
    const normalized = nextRoomId.trim() || 'default';
    currentRoomId.value = normalized;
    window.history.replaceState({}, '', `/${encodeURIComponent(normalized)}`);
  }

  return { roomId, joinRoom };
}

function roomFromPath(): string {
  return window.location.pathname.split('/').filter(Boolean)[0] ?? 'default';
}
