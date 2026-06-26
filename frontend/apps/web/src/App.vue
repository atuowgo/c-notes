<script setup lang="ts">
import { RouterView } from 'vue-router';
</script>

<template>
  <!-- 根壳:鉴权路由(/login、/register 公开;其余需 token)。主应用见 views/HomeView.vue -->
  <RouterView />
</template>
