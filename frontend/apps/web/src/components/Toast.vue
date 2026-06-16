<script setup lang="ts">
import { toastState } from '../toast';
</script>

<template>
  <div class="toast" :class="{ show: toastState.visible }">{{ toastState.message }}</div>
</template>
