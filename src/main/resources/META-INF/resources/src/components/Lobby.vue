<template>
  <section class="lobby">
    <span class="room-label">Room</span>
    <input v-model="draftRoomId" class="room-input" maxlength="32" @keydown.enter="join" />
    <button class="room-join" @click="join">Join</button>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{ roomId: string }>();
const emit = defineEmits<{ join: [roomId: string] }>();

const draftRoomId = ref(props.roomId);

watch(
  () => props.roomId,
  value => {
    draftRoomId.value = value;
  },
);

function join(): void {
  emit('join', draftRoomId.value);
}
</script>

<style scoped>
.lobby {
  display: flex;
  align-items: center;
  gap: 8px;
}

.room-label {
  font-size: 0.72rem;
  color: rgba(255,255,255,0.45);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.room-input {
  width: 150px;
  padding: 7px 9px;
  border-radius: 4px;
  border: 1px solid rgba(255,255,255,0.18);
  background: rgba(255,255,255,0.06);
  color: #fff;
  font-family: monospace;
}

.room-join {
  padding: 7px 12px;
}
</style>
