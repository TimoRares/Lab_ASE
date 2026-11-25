<template>
  <section class="analytics">
    <div class="analytics-head">
      <h2>Analytics</h2>
      <button @click="load" :disabled="loading">{{ loading ? 'Se încarcă...' : 'Reîncarcă' }}</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="data">
      <div class="stat-grid">
        <div class="card stat">
          <small>Raport like-uri / comentarii</small>
          <strong>{{ data.likeCommentRatio.ratio === null ? 'n/a' : data.likeCommentRatio.ratio.toFixed(2) }}</strong>
          <span>{{ data.likeCommentRatio.likes }} like-uri · {{ data.likeCommentRatio.comments }} comentarii</span>
        </div>
        <div class="card stat">
          <small>Poziția ta (engagement mediu / postare)</small>
          <template v-if="data.ranking">
            <strong>Top {{ data.ranking.topPercent }}%</strong>
            <span>locul {{ data.ranking.rank }} din {{ data.ranking.totalUsers }} utilizatori · medie {{ data.ranking.averageEngagement }}</span>
          </template>
          <template v-else>
            <strong>n/a</strong>
            <span>Nu ai încă postări</span>
          </template>
        </div>
      </div>

      <div class="card">
        <h3>Pe săptămână</h3>
        <p v-if="!data.weekly.length" class="muted">Nu există date încă.</p>
        <table v-else>
          <thead>
            <tr><th>Săptămâna (de luni)</th><th>Postări</th><th>Like-uri</th><th>Comentarii</th></tr>
          </thead>
          <tbody>
            <tr v-for="w in data.weekly" :key="w.weekStart">
              <td>{{ w.weekStart }}</td><td>{{ w.posts }}</td><td>{{ w.likes }}</td><td>{{ w.comments }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <h3>Top 5 postări după engagement</h3>
        <p v-if="!data.topPosts.length" class="muted">Nu ai încă postări.</p>
        <table v-else>
          <thead>
            <tr><th>Postare</th><th>Media</th><th>Like-uri</th><th>Comentarii</th><th>Total</th></tr>
          </thead>
          <tbody>
            <tr v-for="p in data.topPosts" :key="p.id">
              <td class="truncate">{{ p.content }}</td>
              <td>{{ p.mediaType || '—' }}</td>
              <td>{{ p.likes }}</td><td>{{ p.comments }}</td><td><b>{{ p.engagement }}</b></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <h3>Media like-uri / postare: poze vs. videoclipuri</h3>
        <p v-if="!data.averageLikesByMedia.length" class="muted">Nu ai postări cu poze sau videoclipuri.</p>
        <div v-else class="bars">
          <div v-for="m in data.averageLikesByMedia" :key="m.mediaType" class="bar-row">
            <span class="bar-label">{{ m.mediaType === 'video' ? 'Videoclipuri' : 'Poze' }} ({{ m.posts }})</span>
            <div class="bar-track"><div class="bar-fill" :style="{ width: barWidth(m.averageLikes) + '%' }"></div></div>
            <span class="bar-value">{{ m.averageLikes.toFixed(2) }}</span>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchAnalytics } from './api'

const data = ref(null)
const loading = ref(false)
const error = ref('')

const maxAverage = computed(() =>
  Math.max(1, ...(data.value?.averageLikesByMedia ?? []).map(m => m.averageLikes)))

function barWidth(value) {
  return Math.round((value / maxAverage.value) * 100)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchAnalytics()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Nu s-au putut încărca statisticile'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.analytics { display: flex; flex-direction: column; gap: 1rem; }
.analytics-head { display: flex; justify-content: space-between; align-items: center; }
.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; }
.stat { display: flex; flex-direction: column; gap: 0.25rem; }
.stat strong { font-size: 1.8rem; }
.stat span, .muted { color: #6b7280; font-size: 0.9rem; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 0.4rem 0.5rem; border-bottom: 1px solid #e5e7eb; }
.truncate { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bars { display: flex; flex-direction: column; gap: 0.6rem; }
.bar-row { display: grid; grid-template-columns: 150px 1fr 60px; align-items: center; gap: 0.6rem; }
.bar-track { background: #e5e7eb; border-radius: 999px; height: 12px; overflow: hidden; }
.bar-fill { background: #2563eb; height: 100%; border-radius: 999px; }
.bar-value { text-align: right; font-variant-numeric: tabular-nums; }
</style>
