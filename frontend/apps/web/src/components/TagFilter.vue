<script setup lang="ts">
defineProps<{ tags: string[]; active: string }>();
const emit = defineEmits<{ select: [tag: string] }>();
</script>

<template>
  <div v-if="tags.length" class="filters">
    <div class="filters-inner">
      <button class="chip" :class="{ active: active === 'all' }" @click="emit('select', 'all')">全部</button>
      <button
        v-for="t in tags"
        :key="t"
        class="chip"
        :class="{ active: active === t }"
        @click="emit('select', t)"
      >
        {{ t }}
      </button>
    </div>
  </div>
</template>
